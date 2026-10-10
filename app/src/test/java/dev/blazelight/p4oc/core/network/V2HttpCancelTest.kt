package dev.blazelight.p4oc.core.network

import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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

    @Test
    fun `a v2 file read stalled after headers runs off the caller thread and cancels with it`() = runBlocking {
        val cancelled = CountDownLatch(1)
        val bodyReadStarted = CountDownLatch(1)
        var readerThread: Thread? = null
        val client = OkHttpClient.Builder()
            .eventListener(
                object : EventListener() {
                    override fun canceled(call: Call) = cancelled.countDown()
                },
            )
            .addInterceptor { chain ->
                // Headers arrive immediately; the body never finishes until the call is cancelled.
                val stalled = object : okio.Source {
                    override fun read(sink: okio.Buffer, byteCount: Long): Long {
                        readerThread = Thread.currentThread()
                        bodyReadStarted.countDown()
                        cancelled.await(TRANSPORT_HOLD_SECONDS, TimeUnit.SECONDS)
                        throw IOException("Canceled")
                    }
                    override fun timeout() = okio.Timeout.NONE
                    override fun close() = Unit
                }
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(stalled.buffer().asResponseBody(null, -1))
                    .build()
            }
            .build()
        val api = V2WorkspaceOpenCodeApi(mockk(relaxed = true), V2Http("http://v2.test", client, Json), Json)
        val callerDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        var callerThread: Thread? = null

        try {
            val caller = launch(callerDispatcher) {
                callerThread = Thread.currentThread()
                api.readFile("big.bin", directory = null, workspace = null)
            }
            assertTrue("body read never started", bodyReadStarted.await(5, TimeUnit.SECONDS))
            assertNotSame("body was read on the caller's thread", callerThread, readerThread)
            withTimeout(5_000) { caller.cancelAndJoin() }
        } finally {
            callerDispatcher.close()
        }

        assertTrue("the OkHttp call was not cancelled", cancelled.await(5, TimeUnit.SECONDS))
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
