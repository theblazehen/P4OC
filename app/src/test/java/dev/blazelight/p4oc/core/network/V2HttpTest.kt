package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class V2HttpTest {
    @Test
    fun `v2 http failures keep their status code for shared callers`() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("""{"error":"missing"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val http = V2Http("http://v2.test", client, Json)

        val request = safeApiCall { http.request("GET", "api/session/ses_gone") }
        val raw = safeApiCall { http.raw("api/fs/read/gone.txt") }

        assertEquals(404, (request as ApiResult.Error).code)
        assertEquals(404, (raw as ApiResult.Error).code)
    }
}
