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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "MdnsDiscovery"
private const val SERVICE_TYPE = "_http._tcp."
private const val SERVICE_NAME_PREFIX = "opencode-"

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
    fun startDiscovery() {
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
        startExtendedSweep()
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
    private fun startExtendedSweep() {
        if (sweepActive) return
        sweepActive = true

        sweepJob = scope.launch {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val targets = mutableSetOf<String>()

            cm.allNetworks.forEach { network ->
                val caps = cm.getNetworkCapabilities(network)
                val lp: LinkProperties = cm.getLinkProperties(network) ?: return@forEach

                val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                val isEth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

                if (isVpn || isEth || isWifi) {
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

            AppLog.d(TAG, "Extended sweep: probing ${capped.size} hosts")

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

        val base = ipv4ToInt(addr) and subnetMask(prefix)
        val low = base + 1
        val high = (base or invMask(prefix)) - 1

        val span = (high - low + 1).coerceAtLeast(1)
        val maxHosts = 512
        val step = (span / maxHosts).coerceAtLeast(1)

        var added = 0
        var ipInt = low
        while (ipInt <= high && added < maxHosts) {
            out.add(intToIpv4(ipInt))
            ipInt += step
            added += 1
        }
    }

    private fun addIpv4RouteTargets(route: RouteInfo, out: MutableSet<String>) {
        val dst = route.destination ?: return
        val addr = dst.address as? Inet4Address ?: return
        val prefix = dst.prefixLength
        if (!isPrivateIpv4(addr)) return
        if (prefix < 16 || prefix > 30) return

        val base = ipv4ToInt(addr) and subnetMask(prefix)
        val low = base + 1
        val high = (base or invMask(prefix)) - 1

        val span = (high - low + 1).coerceAtLeast(1)
        val maxHosts = 512
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
        val url = "http://$ip:4096/global/health"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        withContext(Dispatchers.IO) {
            runCatching {
                probeClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    if (body.contains("\"healthy\":true")) {
                        val server = DiscoveredServer(
                            serviceName = "opencode-$ip",
                            host = ip,
                            port = 4096,
                            url = "http://$ip:4096"
                        )
                        _discoveredServers.update { servers ->
                            if (servers.any { it.url == server.url }) servers else servers + server
                        }
                        AppLog.d(TAG, "Extended sweep: $url healthy")
                    }
                }
            }.onFailure {
                // Ignore failures – host not an OpenCode server or unreachable
            }
        }
    }

    // IPv4 helpers
    private fun isPrivateIpv4(addr: Inet4Address): Boolean {
        val b = addr.address
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        return when (b0) {
            10 -> true // 10.0.0.0/8
            172 -> b1 in 16..31 // 172.16.0.0/12
            192 -> b1 == 168 // 192.168.0.0/16
            100 -> b1 in 64..127 // 100.64.0.0/10 (CGNAT) — used by Tailscale
            else -> false
        }
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
