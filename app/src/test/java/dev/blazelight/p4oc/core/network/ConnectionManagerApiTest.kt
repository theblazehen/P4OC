package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.domain.server.ServerGeneration
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ConnectionManagerApiTest {
    @Test
    fun `replacement connection snapshot never serves its api to old generation`() {
        val config = ServerConfig(url = "http://same-manager.example.com")
        val oldGeneration = ServerGeneration(7L)
        val replacementGeneration = ServerGeneration(8L)
        val oldApi = mockk<OpenCodeApi>()
        val replacementApi = mockk<OpenCodeApi>()
        val eventSource = mockk<OpenCodeEventSource>()
        val oldConnection = Connection(config, oldGeneration, oldApi, eventSource)
        val replacementConnection = Connection(config, replacementGeneration, replacementApi, eventSource)

        assertSame(oldApi, oldConnection.apiForGeneration(oldGeneration))
        assertNull(replacementConnection.apiForGeneration(oldGeneration))
        assertSame(replacementApi, replacementConnection.apiForGeneration(replacementGeneration))
    }

    @Test
    fun `missing connection snapshot cannot resolve any generation`() {
        val connection: Connection? = null

        assertNull(connection.apiForGeneration(ServerGeneration(1L)))
    }
}
