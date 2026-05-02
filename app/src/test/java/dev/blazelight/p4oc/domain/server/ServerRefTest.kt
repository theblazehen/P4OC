package dev.blazelight.p4oc.domain.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerRefTest {
    @Test
    fun `equivalent endpoints are equal after normalization`() {
        val bare = ServerRef.fromEndpoint("Example.com")
        val explicit = ServerRef.fromEndpoint("http://example.com:4096/?ignored=true#fragment")

        assertEquals(bare, explicit)
        assertEquals(bare.hashCode(), explicit.hashCode())
    }

    @Test
    fun `display name does not affect equality or hash code`() {
        val named = ServerRef.fromEndpoint("example.com", displayName = "Work")
        val differentlyNamed = ServerRef.fromEndpoint("http://example.com:4096", displayName = "Personal")

        assertEquals(named, differentlyNamed)
        assertEquals(named.hashCode(), differentlyNamed.hashCode())
        assertNotEquals(named.displayName, differentlyNamed.displayName)
    }

    @Test
    fun `different endpoints are not equal`() {
        val first = ServerRef.fromEndpoint("example.com")
        val second = ServerRef.fromEndpoint("example.org")

        assertNotEquals(first, second)
    }

    @Test
    fun `invalid endpoint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerRef.fromEndpoint("ftp://example.com")
        }
    }

    @Test
    fun `blank endpoint key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerRef.fromEndpointKey("   ")
        }
    }
}
