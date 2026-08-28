package dev.blazelight.p4oc.di

import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.data.server.StaleWorkspaceClientException
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ActiveServerApiProviderWiringTest {
    @Test
    fun `stale workspace cannot receive replacement api between generation and current lookup`() {
        val registry = mockk<ServerConnectionRegistry>()
        val serverRef = ServerRef.fromEndpoint("http://api-race.example.com")
        val oldGeneration = ServerGeneration(7L)
        val replacementApi = mockk<OpenCodeApi>()
        every { registry.generation(serverRef) } returns oldGeneration
        every { registry.api(serverRef) } returns replacementApi
        every { registry.api(serverRef, oldGeneration) } returns null
        val provider = activeServerApiProvider(registry)

        assertThrows(StaleWorkspaceClientException::class.java) {
            provider.apiFor(serverRef, oldGeneration)
        }

        verify(exactly = 1) { registry.api(serverRef, oldGeneration) }
        verify(exactly = 0) { registry.generation(serverRef) }
        verify(exactly = 0) { registry.api(serverRef) }
    }

    @Test
    fun `current workspace receives only exact generation api`() {
        val registry = mockk<ServerConnectionRegistry>()
        val serverRef = ServerRef.fromEndpoint("http://exact-api.example.com")
        val generation = ServerGeneration(8L)
        val api = mockk<OpenCodeApi>()
        every { registry.api(serverRef, generation) } returns api

        val resolved = activeServerApiProvider(registry).apiFor(serverRef, generation)

        assertSame(api, resolved)
        verify(exactly = 1) { registry.api(serverRef, generation) }
    }
}
