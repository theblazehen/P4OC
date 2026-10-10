package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class V2HttpCancelTest {
    @Test
    fun `cancelling a v2 request caller cancels the in-flight http call`() = runBlocking {
        val server = BlockingServer()
        val http = V2Http("http://v2.test", server.client, Json)

        val caller = launch(Dispatchers.Default) { http.request("GET", "api/session") }
        assertTrue("request never reached the network", server.entered.await(5, TimeUnit.SECONDS))
        withTimeout(5_000) { caller.cancelAndJoin() }

        assertTrue("the OkHttp call was not cancelled", server.cancelled.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `cancelling a v2 raw caller cancels the in-flight http call`() = runBlocking {
        val server = BlockingServer()
        val http = V2Http("http://v2.test", server.client, Json)

        val caller = launch(Dispatchers.Default) { http.raw("api/fs/read/file.txt").body()?.close() }
        assertTrue("request never reached the network", server.entered.await(5, TimeUnit.SECONDS))
        withTimeout(5_000) { caller.cancelAndJoin() }

        assertTrue("the OkHttp call was not cancelled", server.cancelled.await(5, TimeUnit.SECONDS))
    }

    /** A transport that holds every call open until OkHttp reports the call as cancelled. */
    private class BlockingServer {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val client: OkHttpClient = OkHttpClient.Builder()
            .eventListener(
                object : EventListener() {
                    override fun canceled(call: Call) = cancelled.countDown()
                },
            )
            .addInterceptor { _ ->
                entered.countDown()
                cancelled.await(TRANSPORT_HOLD_SECONDS, TimeUnit.SECONDS)
                throw IOException("Canceled")
            }
            .build()
    }

    private companion object {
        const val TRANSPORT_HOLD_SECONDS = 10L
    }
}
