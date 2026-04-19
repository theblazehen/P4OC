package dev.blazelight.p4oc.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `bare host defaults to http and 4096`() {
        assertEquals("http://example.com:4096", ServerUrl.normalize("example.com"))
    }

    @Test
    fun `explicit port is preserved`() {
        assertEquals("http://example.com:1234", ServerUrl.normalize("example.com:1234"))
    }

    @Test
    fun `https missing port defaults to 4096`() {
        assertEquals("https://example.com:4096", ServerUrl.normalize("https://example.com"))
    }

    @Test
    fun `path query and fragment are stripped`() {
        assertEquals(
            "http://example.com:4096",
            ServerUrl.normalize("example.com/foo/bar?x=1#frag"),
        )
    }

    @Test
    fun `host is lowercased`() {
        assertEquals("https://example.com:4096", ServerUrl.normalize("https://EXAMPLE.COM"))
    }

    @Test
    fun `unsupported scheme is rejected`() {
        assertNull(ServerUrl.normalize("ftp://example.com"))
    }

    @Test
    fun `blank is rejected`() {
        assertNull(ServerUrl.normalize("   "))
    }

    @Test
    fun `ipv6 is bracketed and zone id is stripped`() {
        assertEquals(
            "http://[2001:db8::1]:4096",
            ServerUrl.normalize("http://[2001:db8::1%wlan0]/foo"),
        )
    }

    @Test
    fun `equivalent forms share endpoint key`() {
        val bare = ServerUrl.endpointKey("Example.com")
        val full = ServerUrl.endpointKey("http://example.com:4096/path?x=1#frag")

        assertEquals(full, bare)
    }
}
