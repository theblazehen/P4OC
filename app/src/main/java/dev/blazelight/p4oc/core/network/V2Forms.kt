package dev.blazelight.p4oc.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.io.IOException

/** Form endpoints are v2-only and remain bound to the owning connection's V2Http instance. */
internal class V2Forms(
    private val http: V2Http,
    private val json: Json,
) {
    private val formJson = Json(from = json) { ignoreUnknownKeys = true }

    suspend fun listSessionForms(sessionID: String, directory: String?): List<V2FormInfo> {
        val path = "api/session/$sessionID/form"
        val data = http.request("GET", path, location(directory)).formData(path)
        val forms = data as? JsonArray
            ?: throw IOException("OpenCode v2 returned an invalid pending-forms response")
        return forms.map { it.decodeForm(path) }
    }

    suspend fun getSessionForm(sessionID: String, formID: String, directory: String?): V2FormInfo {
        val path = "api/session/$sessionID/form/$formID"
        return http.request("GET", path, location(directory)).formData(path).decodeForm(path)
    }

    suspend fun reply(
        sessionID: String,
        formID: String,
        answer: JsonObject,
        directory: String?,
    ) {
        val path = "api/session/$sessionID/form/$formID/reply"
        val body = buildJsonObject { put("answer", answer) }
        http.request("POST", path, location(directory), body)
    }

    suspend fun reject(sessionID: String, formID: String, directory: String?) {
        val path = "api/session/$sessionID/form/$formID"
        http.request("DELETE", path, location(directory))
    }

    private fun location(directory: String?): Map<String, String?> =
        if (directory == null) emptyMap() else mapOf("location[directory]" to directory)

    private fun JsonElement.formData(path: String): JsonElement =
        (this as? JsonObject)?.get("data")
            ?: throw IOException("OpenCode v2 returned no form data for $path")

    private fun JsonElement.decodeForm(path: String): V2FormInfo = try {
        formJson.decodeFromJsonElement(V2FormInfo.serializer(), this)
    } catch (e: SerializationException) {
        throw IOException("OpenCode v2 returned an invalid form for $path", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("OpenCode v2 returned an invalid form for $path", e)
    }
}

@Serializable
data class V2FormInfo(
    val id: String,
    @SerialName("sessionID") val sessionID: String,
    val title: String,
    val metadata: JsonElement? = null,
    val fields: List<V2FormField>,
    val state: V2FormState? = null,
)

@Serializable
data class V2FormField(
    val key: String,
    val type: String,
    val title: String? = null,
    val description: String? = null,
    val required: Boolean? = null,
    val hidden: Boolean? = null,
    @SerialName("when") val conditions: List<V2FormCondition> = emptyList(),
    val format: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val placeholder: String? = null,
    val minimum: JsonElement? = null,
    val maximum: JsonElement? = null,
    val default: JsonElement? = null,
    val options: List<V2FormOption>? = null,
    val custom: Boolean? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val url: String? = null,
)

@Serializable
data class V2FormOption(
    val value: String,
    val label: String,
    val description: String? = null,
)

@Serializable
data class V2FormCondition(
    val key: String,
    val op: String,
    val value: JsonElement,
)

@Serializable
data class V2FormState(
    val status: String,
    val answer: JsonObject? = null,
)
