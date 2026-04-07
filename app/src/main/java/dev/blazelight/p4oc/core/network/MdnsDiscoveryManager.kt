package dev.blazelight.p4oc.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.net.wifi.WifiManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dev.blazelight.p4oc.core.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "MdnsDiscovery"
private const val SERVICE_TYPE = "_http._tcp."
private const val SERVICE_NAME_PREFIX = "opencode-"
private val PROBE_PORTS = intArrayOf(4096, 443, 8080, 80, 9000)
// Common hostnames for MagicDNS/local discovery
private val COMMON_HOSTNAMES = arrayOf(
    "opencode", "code-server", "vscode", "coder", "code",
    "dev", "devserver", "workspace", "ide", "server"
)

/**
 * A discovered OpenCode server on the local network.
 */
data class DiscoveredServer(
    val serviceName: String,
    val host: String,
    val port: Int,
    val url: String
)

/**
 * State of mDNS discovery scanning.
 */
enum class DiscoveryState {
    IDLE,
    SCANNING,
    ERROR
}

/**
 * Manages mDNS/NSD discovery of OpenCode servers on the local network.
 *
 * Uses Android's [NsdManager] to browse for `_http._tcp.` services whose name
 * starts with `opencode-`. Resolves each discovered service sequentially
 * (NSD limitation: only one resolve at a time) and exposes results via
 * [discoveredServers] and [discoveryState] StateFlows.
 */
class MdnsDiscoveryManager(private val context: Context) {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private fun addInterfaceTargets(out: MutableSet<String>) {
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            ifaces.forEach { nif ->
                val name = nif.name?.lowercase() ?: ""
                val looksVpn = name.contains("tailscale") || name.contains("ts") || name.contains("tun")
                if (!looksVpn) return@forEach
                nif.inetAddresses.toList().forEach { ia ->
                    val v4 = ia as? Inet4Address ?: return@forEach
                    if (!isPrivateIpv4(v4)) return@forEach
                    out.add(v4.hostAddress)
                    // Aggressive sweep for VPN: full /24 (254 hosts) for CGNAT
                    val isCgnat = isCgnatIp(v4)
                    addIpv4CidrTargets(v4, 24, out, maxHosts = if (isCgnat) 254 else 512)
                }
            }
        }.onFailure {
            // ignore — not critical, some devices restrict enumeration
        }
    }

    private fun addMagicDnsTargets(out: MutableSet<String>) {
        // Attempt to resolve common hostnames via MagicDNS or local DNS
        COMMON_HOSTNAMES.forEach { hostname ->
            runCatching {
                val addrs = InetAddress.getAllByName(hostname)
                addrs.forEach { inet ->
                    val v4 = inet as? Inet4Address ?: return@forEach
                    if (!isPrivateIpv4(v4)) return@forEach
                    out.add(v4.hostAddress)
                    AppLog.d(TAG, "MagicDNS resolved: $hostname -> ${v4.hostAddress}")
                }
            }.onFailure {
                // hostname not found — normal, continue
            }
        }
    }

    private fun addDnsServerTargets(lp: LinkProperties, out: MutableSet<String>) {
        // DNS servers in Tailscale/VPN often near peers; probe them
        runCatching {
            lp.dnsServers.forEach { dns ->
                val v4 = dns as? Inet4Address ?: return@forEach
                if (!isPrivateIpv4(v4)) return@forEach
                out.add(v4.hostAddress)
                // Sample /28 around DNS server (16 IPs)
                addIpv4CidrTargets(v4, 28, out, maxHosts = 16)
            }
        }.onFailure { }
    }

    private fun addSeedTargets(urlStr: String, out: MutableSet<String>) {
        runCatching {
            val uri = URI(urlStr)
            val host = uri.host ?: return
            val port = if (uri.port != -1) uri.port else 4096
            // Resolve host (MagicDNS through VPN DNS if active)
            val addrs = InetAddress.getAllByName(host)
            addrs.forEach { inet ->
                val v4 = inet as? Inet4Address ?: return@forEach
                out.add(v4.hostAddress)
                // Aggressive /24 for CGNAT, normal for others
                val isCgnat = isCgnatIp(v4)
                addIpv4CidrTargets(v4, 24, out, maxHosts = if (isCgnat) 254 else 512)
            }
            // also remember port in case it differs (handled during probe)
            if (port != 4096 && !PROBE_PORTS.contains(port)) {
                // no-op: we keep static ports for now; future: make configurable
            }
        }.onFailure {
            // ignore malformed seed
        }
    }

    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.asStateFlow()

    private val _discoveryState = MutableStateFlow(DiscoveryState.IDLE)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    /** Queue for sequential resolution (NSD can only resolve one at a time). */
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    @Volatile
    private var isResolving = false

    private var activeListener: NsdManager.DiscoveryListener? = null

    // Extended sweep state (for VPN/Ethernet/Wi‑Fi subnets)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var sweepActive = false
    private var sweepJob: Job? = null
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(800, TimeUnit.MILLISECONDS)
            .readTimeout(800, TimeUnit.MILLISECONDS)
            .callTimeout(1600, TimeUnit.MILLISECONDS)
            .build()
    }

    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Start browsing for OpenCode servers on the local network.
     * If already scanning, stops the previous scan first.
     */
    fun startDiscovery(seeds: List<String> = emptyList()) {
        // Stop any existing discovery first (handles rapid stop/start)
        if (activeListener != null) {
            AppLog.d(TAG, "Stopping previous discovery before restarting")
            stopDiscovery()
        }

        // Clear previous results
        _discoveredServers.value = emptyList()
        _discoveryState.value = DiscoveryState.SCANNING

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                AppLog.d(TAG, "Discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName
                AppLog.d(TAG, "Service found: $name")
                if (name.startsWith(SERVICE_NAME_PREFIX, ignoreCase = true)) {
                    AppLog.d(TAG, "OpenCode service matched: $name, queuing resolve")
                    enqueueResolve(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName
                AppLog.d(TAG, "Service lost: $name")
                _discoveredServers.update { servers ->
                    servers.filter { it.serviceName != name }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                AppLog.d(TAG, "Discovery stopped for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                AppLog.e(TAG, "Start discovery failed: errorCode=$errorCode")
                activeListener = null
                _discoveryState.value = DiscoveryState.ERROR
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                AppLog.w(TAG, "Stop discovery failed: errorCode=$errorCode")
            }
        }

        activeListener = listener

        try {
            // Acquire multicast lock to improve mDNS reception on some Wi‑Fi APs
            if (multicastLock == null) {
                val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wm?.createMulticastLock("p4oc-mdns").apply { this?.setReferenceCounted(true); this?.acquire() }
                AppLog.d(TAG, "Acquired Wi‑Fi MulticastLock for mDNS")
            }
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to start discovery", e)
            activeListener = null
            _discoveryState.value = DiscoveryState.ERROR
        }

        // Start extended sweep in parallel (useful over VPN/Ethernet as mDNS may not traverse)
        startExtendedSweep(seeds)
    }

    /**
     * Stop browsing for servers.
     */
    fun stopDiscovery() {
        val listener = activeListener ?: return
        activeListener = null

        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            // IllegalArgumentException if listener not registered — safe to ignore
            AppLog.w(TAG, "Error stopping discovery: ${e.message}")
        }

        resolveQueue.clear()
        isResolving = false
        // Stop any extended sweep
        sweepActive = false
        sweepJob?.cancel()
        sweepJob = null
        // Release Wi‑Fi multicast lock if held
        try {
            multicastLock?.let { lock -> if (lock.isHeld) lock.release() }
            multicastLock = null
            AppLog.d(TAG, "Released Wi‑Fi MulticastLock")
        } catch (_: Exception) { }
        _discoveryState.value = DiscoveryState.IDLE
    }

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        resolveQueue.add(serviceInfo)
        processResolveQueue()
    }

    @Synchronized
    private fun processResolveQueue() {
        if (isResolving) return
        val next = resolveQueue.poll() ?: return
        isResolving = true
        resolveService(next)
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        try {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    AppLog.w(TAG, "Resolve failed for ${info.serviceName}: errorCode=$errorCode")
                    isResolving = false
                    processResolveQueue()
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host
                    val port = info.port
                    val hostAddress = host?.hostAddress
                    if (hostAddress == null) {
                        AppLog.w(TAG, "Resolved ${info.serviceName} but hostAddress is null, skipping")
                        isResolving = false
                        processResolveQueue()
                        return
                    }

                    // Format IPv6 addresses with brackets for URL construction
                    val formattedHost = if (host is Inet6Address) {
                        "[${hostAddress.split("%").first()}]"
                    } else {
                        hostAddress
                    }

                    val url = "http://$formattedHost:$port"
                    val server = DiscoveredServer(
                        serviceName = info.serviceName,
                        host = formattedHost,
                        port = port,
                        url = url
                    )

                    AppLog.d(TAG, "Resolved: ${server.serviceName} → $url")

                    _discoveredServers.update { servers ->
                        // Replace if same service name already present, otherwise add
                        val existing = servers.indexOfFirst { it.serviceName == server.serviceName }
                        if (existing >= 0) {
                            servers.toMutableList().apply { this[existing] = server }
                        } else {
                            servers + server
                        }
                    }

                    isResolving = false
                    processResolveQueue()
                }
            })
        } catch (e: Exception) {
            AppLog.e(TAG, "Error resolving service: ${e.message}", e)
            isResolving = false
            processResolveQueue()
        }
    }

    private fun addGatewayTarget(route: RouteInfo, out: MutableSet<String>) {
        val gw = route.gateway as? Inet4Address ?: return
        if (!isPrivateIpv4(gw)) return
        out.add(gw.hostAddress)
    }

    // -------------------------------------------------------------------------
    // Extended discovery over VPN/Ethernet/Wi‑Fi: fast HTTP health probes
    // -------------------------------------------------------------------------
    private fun startExtendedSweep(seeds: List<String>) {
        if (sweepActive) return
        sweepActive = true

        sweepJob = scope.launch {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val targets = mutableSetOf<String>()

            // 0) Add IPv4s from local interfaces (tailscale/tun) if visible to JVM
            addInterfaceTargets(targets)

            // 0b) Try common hostnames via MagicDNS / local DNS
            addMagicDnsTargets(targets)

            // 1) Add seed hosts (recent servers) — resolves MagicDNS if VPN active
            if (seeds.isNotEmpty()) {
                AppLog.d(TAG, "Extended sweep: seeds=${seeds.size}")
                seeds.forEach { url ->
                    addSeedTargets(url, targets)
                }
            }

            val networks = cm.allNetworks
            AppLog.d(TAG, "Extended sweep: networks=${networks.size}")
            networks.forEach { network ->
                val caps = cm.getNetworkCapabilities(network)
                val lp: LinkProperties = cm.getLinkProperties(network) ?: return@forEach

                val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                val isEth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                if (isVpn || isEth || isWifi) {
                    AppLog.d(
                        TAG,
                        "net transports: vpn=${isVpn} eth=${isEth} wifi=${isWifi}; addrs=${lp.linkAddresses.size} routes=${lp.routes.size}"
                    )
                    // Add DNS server targets (near peers in Tailscale)
                    addDnsServerTargets(lp, targets)
                    
                    lp.linkAddresses.forEach { la ->
                        addIpv4PrefixTargets(la, targets)
                    }
                    lp.routes.forEach { r ->
                        addIpv4RouteTargets(r, targets)
                        addGatewayTarget(r, targets)
                    }
                }
            }

            if (targets.isEmpty()) {
                AppLog.d(TAG, "Extended sweep: no IPv4 targets derived")
                sweepActive = false
                return@launch
            }

            // Cap total hosts to avoid heavy scans
            val capped = targets.take(512)
            val semaphore = Semaphore(32)

            AppLog.d(TAG, "Extended sweep: probing ${capped.size} hosts on ports=${PROBE_PORTS.joinToString()}")

            val jobs = capped.map { ip ->
                async {
                    semaphore.acquire()
                    try {
                        probeIp(ip)
                    } finally {
                        semaphore.release()
                    }
                }
            }

            jobs.awaitAll()
            sweepActive = false
            AppLog.d(TAG, "Extended sweep: completed")
        }
    }

    private fun addIpv4PrefixTargets(la: LinkAddress, out: MutableSet<String>) {
        val addr = la.address as? Inet4Address ?: return
        val prefix = la.prefixLength
        if (!isPrivateIpv4(addr)) return
        // Accept larger prefixes too; sample up to 512 hosts evenly (/16.. /30)
        if (prefix < 16 || prefix > 30) return

        val isCgnat = isCgnatIp(addr)
        addIpv4CidrTargets(addr, prefix, out, maxHosts = if (isCgnat) 254 else 512)
    }

    private fun addIpv4RouteTargets(route: RouteInfo, out: MutableSet<String>) {
        val dst = route.destination ?: return
        val addr = dst.address as? Inet4Address ?: return
        val prefix = dst.prefixLength
        if (!isPrivateIpv4(addr)) return
        if (prefix < 16 || prefix > 30) return

        val isCgnat = isCgnatIp(addr)
        addIpv4CidrTargets(addr, prefix, out, maxHosts = if (isCgnat) 254 else 512)
    }

    private fun addIpv4CidrTargets(addr: Inet4Address, prefix: Int, out: MutableSet<String>, maxHosts: Int = 512) {
        val base = ipv4ToInt(addr) and subnetMask(prefix)
        val low = base + 1
        val high = (base or invMask(prefix)) - 1

        val span = (high - low + 1).coerceAtLeast(1)
        val step = (span / maxHosts).coerceAtLeast(1)

        var added = 0
        var ipInt = low
        while (ipInt <= high && added < maxHosts) {
            out.add(intToIpv4(ipInt))
            ipInt += step
            added += 1
        }
    }

    private suspend fun probeIp(ip: String) {
        // Pre-filter with fast TCP connect check on primary port (longer timeout for VPN)
        val tcpOk = tcpConnectCheck(ip, 4096, timeoutMs = 800)
        if (!tcpOk) {
            return // No server listening on primary port, skip HTTP probes
        }
        AppLog.d(TAG, "TCP check passed for $ip:4096, trying HTTP probes")
        // Try each port with both HTTP and HTTPS
        for (port in PROBE_PORTS) {
            val found = probeHealth(ip, port, "http") || probeHealth(ip, port, "https")
            if (found) return
        }
    }

    private suspend fun probeHealth(ip: String, port: Int, scheme: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$scheme://$ip:$port/global/health"
            val req = Request.Builder().url(url).build()
            probeClient.newCall(req).execute().use { resp ->
                val code = resp.code
                val body = resp.body?.string() ?: ""
                val headers = resp.headers
                
                
                // Enhanced detection: multiple strategies
                val is200Healthy = code == 200 && body.contains("healthy", ignoreCase = true)
                val is401Json = code == 401 && (body.startsWith("{") || body.startsWith("["))
                // Accept 401 with 'Unauthorized' text (common OpenCode response)
                val is401Unauthorized = code == 401 && body.trim().lowercase() == "unauthorized"
                // Also accept 401/403 if headers suggest code-server or similar
                val serverHeader = headers["Server"]?.lowercase() ?: ""
                val poweredBy = headers["X-Powered-By"]?.lowercase() ?: ""
                val hasCodeServerHeaders = serverHeader.contains("code-server") || 
                                          serverHeader.contains("opencode") ||
                                          poweredBy.contains("code-server") ||
                                          poweredBy.contains("opencode")
                val is401Auth = (code == 401 || code == 403) && hasCodeServerHeaders
                // Also check 404 with relevant headers (some servers return 404 on /global/health)
                val is404CodeServer = code == 404 && hasCodeServerHeaders
                
                if (is200Healthy || is401Json || is401Unauthorized || is401Auth || is404CodeServer) {
                    // Reverse DNS lookup for hostname validation
                    val hostname = reverseResolveHostname(ip)
                    val serviceName = if (hostname != null && hostname != ip) {
                        "opencode-$hostname"
                    } else {
                        "opencode-$ip"
                    }
                    val finalUrl = "$scheme://$ip:$port"
                    val server = DiscoveredServer(
                        serviceName = serviceName,
                        host = ip,
                        port = port,
                        url = finalUrl
                    )
                    AppLog.d(TAG, "Sweep found: $finalUrl (HTTP $code) hostname=$hostname")
                    _discoveredServers.update { servers ->
                        val existing = servers.indexOfFirst { it.serviceName == serviceName }
                        if (existing >= 0) {
                            servers.toMutableList().apply { this[existing] = server }
                        } else {
                            servers + server
                        }
                    }
                    return@withContext true
                }
            }
        }.onFailure {
            // connection failed — normal, host not listening
        }
        false
    }

    private fun reverseResolveHostname(ip: String): String? {
        return runCatching {
            val addr = InetAddress.getByName(ip)
            val hostname = addr.canonicalHostName
            if (hostname != ip) hostname else null
        }.getOrNull()
    }

    private fun tcpConnectCheck(ip: String, port: Int, timeoutMs: Int): Boolean {
        return runCatching {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
                true
            }
        }.onFailure {
            // Log only for debugging critical IPs
            // TCP connect failed — normal for most IPs
        }.getOrDefault(false)
    }

    // IPv4 helpers
    private fun isPrivateIpv4(addr: Inet4Address): Boolean {
        val bytes = addr.address
        // 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 100.64.0.0/10 (CGNAT)
        return (bytes[0] == 10.toByte()) ||
               (bytes[0] == 172.toByte() && (bytes[1].toInt() and 0xF0) == 16) ||
               (bytes[0] == 192.toByte() && bytes[1] == 168.toByte()) ||
               (bytes[0] == 100.toByte() && (bytes[1].toInt() and 0xC0) == 64)
    }

    private fun isCgnatIp(addr: Inet4Address): Boolean {
        val bytes = addr.address
        // 100.64.0.0/10 (CGNAT used by Tailscale)
        return bytes[0] == 100.toByte() && (bytes[1].toInt() and 0xC0) == 64
    }

    private fun ipv4ToInt(addr: Inet4Address): Int {
        val b = addr.address
        return ((b[0].toInt() and 0xFF) shl 24) or
               ((b[1].toInt() and 0xFF) shl 16) or
               ((b[2].toInt() and 0xFF) shl 8) or
               (b[3].toInt() and 0xFF)
    }

    private fun intToIpv4(v: Int): String {
        val b0 = (v ushr 24) and 0xFF
        val b1 = (v ushr 16) and 0xFF
        val b2 = (v ushr 8) and 0xFF
        val b3 = v and 0xFF
        return "$b0.$b1.$b2.$b3"
    }

    private fun subnetMask(prefix: Int): Int = if (prefix == 0) 0 else -1 shl (32 - prefix)
    private fun invMask(prefix: Int): Int = subnetMask(prefix).inv()
}
