package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.domain.server.ServerGeneration
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerFallbackTest {
    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var manager: ConnectionManager

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        manager = ConnectionManager(
            json = json,
            eventMapper = EventMapper(json, MessageMapper()),
            settingsDataStore = mockk<SettingsDataStore>(),
            generationIssuer = { ServerGeneration(1L) },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `explicit opencode port only tries the primary url`() {
        val primary = ServerConfig(url = "http://192.168.24.25:4096")

        val candidates = manager.connectionCandidates(primary)

        assertTrue(manager.hasExplicitPort(primary.url))
        assertEquals(listOf(primary), candidates)
    }

    @Test
    fun `implicit http url probes opencode port before preserved no-port url`() {
        val primary = ServerConfig(url = "http://192.168.24.25")

        val candidates = manager.connectionCandidates(primary)

        assertFalse(manager.hasExplicitPort(primary.url))
        assertEquals(2, candidates.size)
        assertEquals(primary.copy(url = "http://192.168.24.25:4096"), candidates[0])
        assertEquals(primary.copy(url = "http://192.168.24.25"), candidates[1])
        assertEquals(80, candidates[1].url.toHttpUrl().port)
    }
}
