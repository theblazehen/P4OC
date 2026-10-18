package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * OpenCode v2 models carry two names: `id` (the server's routing key, which session model refs
 * and prompts must use) and `modelID` (the upstream provider's model name). They differ for
 * proxied providers, e.g. id `glm-5.3-flash` vs modelID `ollama-cloud/glm-5.3-flash`.
 * Keying the catalog by `modelID` made every prompt on such a model fail server-side with
 * `provider.no-route`.
 */
class V2ModelCatalogTest {
    private val proxiedModel = """
        {
          "id": "glm-5.3-flash",
          "modelID": "ollama-cloud/glm-5.3-flash",
          "providerID": "sohoai",
          "name": "GLM 5.3 Flash",
          "capabilities": {"tools": true, "input": ["text"], "output": ["text"]},
          "variants": [],
          "cost": [],
          "status": "active",
          "enabled": true,
          "limit": {"context": 1000000, "output": 32000}
        }
    """.trimIndent()

    // Serves the three catalog endpoints plus an empty config, based on the request path.
    private val api = run {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = when (chain.request().url.encodedPath) {
                "/api/provider" -> """{"data":[{"id":"sohoai","name":"SoHoAI"}]}"""
                "/api/model" -> """{"data":[$proxiedModel]}"""
                "/api/model/default" -> """{"data":$proxiedModel}"""
                "/api/config" -> "[]"
                else -> error("unexpected request ${chain.request().url}")
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        V2WorkspaceOpenCodeApi(unsupportedV2Api(), V2Http("http://v2.test", client, Json), Json)
    }

    @Test
    fun `provider catalog keys models by routing id not upstream modelID`() = runTest {
        val providers = api.getProviders(directory = "/workspace", workspace = null)
        val models = providers.all.single().models

        assertEquals(setOf("glm-5.3-flash"), models.keys)
        assertEquals("glm-5.3-flash", models.getValue("glm-5.3-flash").id)
        assertFalse(models.containsKey("ollama-cloud/glm-5.3-flash"))
    }

    @Test
    fun `default model uses the routing id`() = runTest {
        val providers = api.getProviders(directory = "/workspace", workspace = null)

        assertEquals(mapOf("sohoai" to "glm-5.3-flash"), providers.default)
    }

    @Test
    fun `config falls back to the default model by routing id`() = runTest {
        val config = api.getConfig(directory = "/workspace", workspace = null)

        assertEquals("sohoai/glm-5.3-flash", config.model)
    }
}
