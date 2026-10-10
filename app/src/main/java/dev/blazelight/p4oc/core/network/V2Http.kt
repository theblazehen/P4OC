package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * The v2 API uses JSON envelopes and has no common Retrofit DTO shape with the legacy API.
 * HTTP failures throw Retrofit's [HttpException], exactly as the v1 client does, so shared callers
 * (missing-session handling, VCS support detection, form settlement) can read the status code.
 */
internal class V2Http(
    baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val base = (baseUrl.trimEnd('/') + "/").toHttpUrl()

    /**
     * The whole exchange, including reading the body, runs inside one cancellable call: cancelling
     * the calling coroutine cancels the OkHttp call, so a retired tab or generation stops its I/O.
     */
    suspend fun request(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: JsonElement? = null,
    ): JsonElement {
        val url = url(path, query)
        val requestBody = body?.let {
            json.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON)
        } ?: when (method) {
            "POST", "PUT", "PATCH" -> ByteArray(0).toRequestBody()
            else -> null
        }
        val request = Request.Builder().url(url).method(method, requestBody).build()
        return client.newCall(request).await(onDiscard = {}) { response ->
            response.use {
                if (!response.isSuccessful) throw httpFailure(response)
                parseJson(response.body.string(), method, path)
            }
        }
    }

    /**
     * Caller owns and must close the returned body, including on a bounded-read failure. Cancelling
     * before the response is handed over cancels the call and closes any response it produced.
     */
    suspend fun raw(path: String, query: Map<String, String?> = emptyMap()): Response<ResponseBody> =
        client.newCall(Request.Builder().url(url(path, query)).get().build())
            .await(onDiscard = { it.body()?.close() }) { response ->
                if (!response.isSuccessful) throw response.use(::httpFailure)
                Response.success(response.body)
            }

    private fun parseJson(text: String, method: String, path: String): JsonElement =
        if (text.isBlank()) {
            JsonNull
        } else {
            try {
                json.parseToJsonElement(text)
            } catch (e: IllegalArgumentException) {
                throw IOException("OpenCode v2 returned a non-JSON response for $method $path", e)
            }
        }

    /**
     * Enqueues the call and runs [handle] on OkHttp's callback thread, where cancelling the call
     * still aborts a body read in progress. A result produced after the coroutine was cancelled is
     * passed to [onDiscard] so owned resources are released instead of leaked.
     */
    private suspend fun <T> Call.await(onDiscard: (T) -> Unit, handle: (okhttp3.Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { this@await.cancel() }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    val result = runCatching { handle(response) }
                    result.fold(
                        onSuccess = { value ->
                            continuation.resume(value) { _, discarded, _ -> onDiscard(discarded) }
                        },
                        onFailure = { continuation.resumeWith(Result.failure(it)) },
                    )
                }
            })
        }

    private fun url(path: String, query: Map<String, String?>): HttpUrl =
        base.newBuilder().addPathSegments(path.trimStart('/')).apply {
            query.forEach { (name, value) -> if (value != null) addQueryParameter(name, value) }
        }.build()

    private fun httpFailure(response: okhttp3.Response): HttpException {
        val body = response.body.bytes().toResponseBody(response.body.contentType())
        return HttpException(Response.error<Any>(body, response))
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

internal const val HTTP_NOT_FOUND = 404
