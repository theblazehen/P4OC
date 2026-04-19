package dev.blazelight.p4oc.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MdnsDiscoveryManagerSeedTest {

    @Test
    fun `normalizeSeed http host defaults 4096`() {
        val normalized = normalizeSeed(DiscoverySeed("example.com"))

        requireNotNull(normalized)
        assertEquals("http://example.com:4096", normalized.canonicalUrl)
        assertEquals("example.com", normalized.host)
        assertEquals(4096, normalized.port)
        assertEquals("http", normalized.scheme)
    }

    @Test
    fun `normalizeSeed https host defaults 4096`() {
        val normalized = normalizeSeed(DiscoverySeed("https://example.com"))

        requireNotNull(normalized)
        assertEquals("https://example.com:4096", normalized.canonicalUrl)
        assertEquals("example.com", normalized.host)
        assertEquals(4096, normalized.port)
        assertEquals("https", normalized.scheme)
    }

    @Test
    fun `normalizeSeed strips path query fragment`() {
        val normalized = normalizeSeed(DiscoverySeed("https://example.com/foo/bar?x=1#frag"))

        requireNotNull(normalized)
        assertEquals("https://example.com:4096", normalized.canonicalUrl)
    }

    @Test
    fun `normalizeSeed rejects blank and unsupported schemes`() {
        assertNull(normalizeSeed(DiscoverySeed("   ")))
        assertNull(normalizeSeed(DiscoverySeed("ftp://example.com")))
    }

    @Test
    fun `normalizeSeed preserves ipv6 brackets`() {
        val normalized = normalizeSeed(DiscoverySeed("http://[2001:db8::1]/foo"))

        requireNotNull(normalized)
        assertEquals("http://[2001:db8::1]:4096", normalized.canonicalUrl)
        assertEquals("[2001:db8::1]", normalized.host)
    }

    @Test
    fun `normalizeSeed carries allowInsecure from seed`() {
        val normalized = normalizeSeed(DiscoverySeed("https://example.com", allowInsecure = true))

        requireNotNull(normalized)
        assertTrue(normalized.allowInsecure)
    }

    @Test
    fun `mergeDiscoveredServer adds when absent`() {
        val incoming = DiscoveredServer(
            serviceName = "seed:example.com:4096",
            host = "example.com",
            port = 4096,
            url = "http://example.com:4096",
            source = DiscoverySource.SEED,
        )

        val merged = mergeDiscoveredServer(emptyList(), incoming)

        assertEquals(listOf(incoming), merged)
    }

    @Test
    fun `mergeDiscoveredServer keeps mdns over seed for same url`() {
        val existing = listOf(
            DiscoveredServer(
                serviceName = "opencode-local",
                host = "example.com",
                port = 4096,
                url = "http://example.com:4096",
                source = DiscoverySource.MDNS,
            )
        )
        val incoming = DiscoveredServer(
            serviceName = "seed:example.com:4096",
            host = "example.com",
            port = 4096,
            url = "http://example.com:4096",
            source = DiscoverySource.SEED,
            allowInsecure = true,
        )

        val merged = mergeDiscoveredServer(existing, incoming)

        assertEquals(existing.first().copy(allowInsecure = true), merged.single())
    }

    @Test
    fun `mergeDiscoveredServer replaces seed with mdns for same url and preserves allowInsecure`() {
        val existing = listOf(
            DiscoveredServer(
                serviceName = "seed:example.com:4096",
                host = "example.com",
                port = 4096,
                url = "http://example.com:4096",
                source = DiscoverySource.SEED,
                allowInsecure = true,
            )
        )
        val incoming = DiscoveredServer(
            serviceName = "opencode-local",
            host = "example.com",
            port = 4096,
            url = "http://example.com:4096",
            source = DiscoverySource.MDNS,
            allowInsecure = false,
        )

        val merged = mergeDiscoveredServer(existing, incoming)

        assertEquals(listOf(incoming.copy(allowInsecure = true)), merged)
    }

    @Test
    fun `mergeDiscoveredServer replaces same-source duplicate`() {
        val existing = listOf(
            DiscoveredServer(
                serviceName = "seed:example.com:4096",
                host = "example.com",
                port = 4096,
                url = "http://example.com:4096",
                source = DiscoverySource.SEED,
            )
        )
        val incoming = DiscoveredServer(
            serviceName = "seed:example.com:4096-updated",
            host = "example.com",
            port = 4096,
            url = "http://example.com:4096",
            source = DiscoverySource.SEED,
        )

        val merged = mergeDiscoveredServer(existing, incoming)

        assertEquals(listOf(incoming), merged)
    }

    @Test
    fun `endpointKey dedupes equivalent urls`() {
        assertEquals(
            endpointKey("example.com"),
            endpointKey("http://example.com:4096/path?x=1#frag"),
        )
    }

    @Test
    fun `mdns discovered servers default to strict tls metadata`() {
        val server = DiscoveredServer(
            serviceName = "opencode-local",
            host = "example.com",
            port = 4096,
            url = "http://example.com:4096",
            source = DiscoverySource.MDNS,
        )

        assertFalse(server.allowInsecure)
    }
}
