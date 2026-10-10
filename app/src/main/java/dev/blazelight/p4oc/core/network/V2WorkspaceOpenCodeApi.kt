package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.data.remote.dto.AddMcpServerRequest
import dev.blazelight.p4oc.data.remote.dto.AgentDto
import dev.blazelight.p4oc.data.remote.dto.CacheCostDto
import dev.blazelight.p4oc.data.remote.dto.CommandDto
import dev.blazelight.p4oc.data.remote.dto.ConfigDto
import dev.blazelight.p4oc.data.remote.dto.ConfigProvidersDto
import dev.blazelight.p4oc.data.remote.dto.CreatePtyRequest
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
import dev.blazelight.p4oc.data.remote.dto.FileContentDto
import dev.blazelight.p4oc.data.remote.dto.FileNodeDto
import dev.blazelight.p4oc.data.remote.dto.FileStatusDto
import dev.blazelight.p4oc.data.remote.dto.McpConfigDto
import dev.blazelight.p4oc.data.remote.dto.McpStatusDto
import dev.blazelight.p4oc.data.remote.dto.ModalitiesDto
import dev.blazelight.p4oc.data.remote.dto.ModelCapabilitiesDto
import dev.blazelight.p4oc.data.remote.dto.ModelCostDto
import dev.blazelight.p4oc.data.remote.dto.ModelCostTierDto
import dev.blazelight.p4oc.data.remote.dto.ModelCostTierRuleDto
import dev.blazelight.p4oc.data.remote.dto.ModelDto
import dev.blazelight.p4oc.data.remote.dto.ModelLimitDto
import dev.blazelight.p4oc.data.remote.dto.ModelRefDto
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.ProjectTimeDto
import dev.blazelight.p4oc.data.remote.dto.ProviderDto
import dev.blazelight.p4oc.data.remote.dto.ProvidersResponseDto
import dev.blazelight.p4oc.data.remote.dto.PtyDto
import dev.blazelight.p4oc.data.remote.dto.UpdatePtyRequest
import dev.blazelight.p4oc.data.remote.dto.VcsInfoDto
import dev.blazelight.p4oc.data.remote.dto.WorkspaceVcsInfoDto
import dev.blazelight.p4oc.data.vcs.VcsDiffMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Maps the documented OpenCode v2 workspace routes onto the app's existing v1-facing API. */
internal class V2WorkspaceOpenCodeApi(
    private val next: OpenCodeApi,
    http: V2Http,
    private val json: Json,
) : V2WorkspaceApiBase(next, http) {
    internal val forms = V2Forms(http, json)

    private val dtoMapper = V2WorkspaceDtoMapper(v2Json)

    private suspend fun fetchVcsInfo(directory: String?, workspace: String?): V2VcsInfo {
        val response = http.request("GET", "api/vcs", locationQuery(directory, workspace))
        return decodeData(response)
    }

    internal suspend fun executeShellForOutput(sessionId: String, command: String): String =
        (next as? V2SessionOpenCodeApi)?.executeShellForOutput(sessionId, command)
            ?: throw IOException("OpenCode v2 shell execution is unavailable in this API chain")

    internal suspend fun dispatchCommand(
        sessionId: String,
        request: ExecuteCommandRequest,
    ) {
        (next as? V2SessionOpenCodeApi)?.dispatchCommand(sessionId, request)
            ?: throw IOException("OpenCode v2 command execution is unavailable in this API chain")
    }

    override suspend fun listProjects(directory: String?, workspace: String?): List<ProjectDto> {
        requireNoWorkspaceId(workspace)
        val response = http.request("GET", "api/project")
        return v2Json.decodeFromJsonElement<List<V2Project>>(response).map { project ->
            dtoMapper.run { project.toProjectDto() }
        }
    }

    override suspend fun getCurrentProject(directory: String?, workspace: String?): ProjectDto {
        val query = locationQuery(directory, workspace)
        val current = v2Json.decodeFromJsonElement<V2LocationInfo>(http.request("GET", "api/location", query))
        return listProjects(directory, workspace).firstOrNull { it.id == current.project.id }
            ?: throw IOException("OpenCode v2 did not return project ${current.project.id} in its project list")
    }

    override suspend fun getVcsInfo(directory: String?, workspace: String?): VcsInfoDto {
        val info = fetchVcsInfo(directory, workspace)
        return dtoMapper.run { info.toLegacyInfo() }
    }

    override suspend fun getVcsInfoRaw(
        directory: String?,
        workspace: String?,
    ): Response<ResponseBody> {
        val info = fetchVcsInfo(directory, workspace)
        val workspaceInfo = dtoMapper.run { info.toWorkspaceInfo() }
        return jsonResponse(json.encodeToString(WorkspaceVcsInfoDto.serializer(), workspaceInfo))
    }

    override suspend fun getAgents(directory: String?, workspace: String?): List<AgentDto> {
        val response = http.request("GET", "api/agent", locationQuery(directory, workspace))
        return decodeData<List<V2AgentInfo>>(response).map { agent ->
            dtoMapper.run { agent.toAgentDto() }
        }
    }

    override suspend fun getProviders(directory: String?, workspace: String?): ProvidersResponseDto {
        val location = locationQuery(directory, workspace)
        val providerResponse = http.request("GET", "api/provider", location)
        val modelResponse = http.request("GET", "api/model", location)
        val defaultResponse = http.request("GET", "api/model/default", location)
        val providers = decodeData<List<V2ProviderInfo>>(providerResponse)
        val models = decodeData<List<V2ModelInfo>>(modelResponse)
        val defaultModel = data(defaultResponse).takeUnless { it == JsonNull }
            ?.let { v2Json.decodeFromJsonElement<V2ModelInfo>(it) }
        val modelDtosByProvider = models.filter(V2ModelInfo::enabled).groupBy(V2ModelInfo::providerID)
        val providerDtos = providers.map { provider ->
            dtoMapper.run { provider.toProviderDto(modelDtosByProvider[provider.id].orEmpty()) }
        }
        val connected = providers.asSequence()
            .filter { provider -> modelDtosByProvider[provider.id].orEmpty().isNotEmpty() }
            .map(V2ProviderInfo::id)
            .toList()
        val defaults = defaultModel?.let { mapOf(it.providerID to it.modelID) }.orEmpty()
        return ProvidersResponseDto(all = providerDtos, default = defaults, connected = connected)
    }

    override suspend fun getConfig(directory: String?, workspace: String?): ConfigDto {
        val location = locationQuery(directory, workspace)
        val configResponse = http.request("GET", "api/config", location)
        val entries = v2Json.decodeFromJsonElement<List<V2ConfigEntry>>(configResponse)
        var resolved = ConfigDto()
        entries.forEach { entry ->
            val info = entry.info ?: return@forEach
            val configuredModel = info.model?.let(::modelConfigValue)
            val autoupdate = configAutoupdate(info.update, resolved.autoupdate)
            resolved = resolved.copy(
                model = configuredModel ?: resolved.model,
                share = info.share ?: resolved.share,
                username = info.username ?: resolved.username,
                autoupdate = autoupdate,
                instructions = info.instructions ?: resolved.instructions,
            )
        }

        val defaultResponse = http.request("GET", "api/model/default", location)
        val defaultModel = data(defaultResponse).takeUnless { it == JsonNull }
            ?.let { v2Json.decodeFromJsonElement<V2ModelInfo>(it) }
        return resolved.copy(
            model = resolved.model ?: defaultModel?.let { model ->
                dtoMapper.run { model.legacyModelId() }
            },
        )
    }

    override suspend fun getMcpStatus(directory: String?, workspace: String?): Map<String, McpStatusDto> {
        val response = http.request("GET", "api/mcp", locationQuery(directory, workspace))
        return decodeData<List<V2McpServer>>(response).associate { server ->
            server.name to McpStatusDto(
                status = server.status.status,
                error = server.status.error,
            )
        }
    }

    override suspend fun addMcpServer(
        request: AddMcpServerRequest,
        directory: String?,
        workspace: String?,
    ): Map<String, McpStatusDto> {
        val query = locationQuery(directory, workspace)
        val body = buildJsonObject { put("config", request.config.toV2Config()) }
        http.request("PUT", "api/experimental/mcp/${request.name}", query, body)
        return getMcpStatus(directory, workspace)
    }

    override suspend fun listCommands(directory: String?, workspace: String?): List<CommandDto> {
        val response = http.request("GET", "api/command", locationQuery(directory, workspace))
        return decodeData<List<V2CommandInfo>>(response).map { command ->
            CommandDto(name = command.name, description = command.description)
        }
    }

    override suspend fun listFiles(
        path: String,
        directory: String?,
        workspace: String?,
    ): List<FileNodeDto> {
        val query = locationQuery(directory, workspace) + mapOf("path" to path)
        val response = http.request("GET", "api/fs/list", query)
        val locationDirectory = v2Json
            .decodeFromJsonElement<V2LocationRef>(requiredField(response, "location"))
            .directory
        return decodeData<List<V2FileEntry>>(response).map { entry ->
            if (entry.path.startsWith('/')) {
                throw SerializationException("OpenCode v2 filesystem entries must be relative to their location")
            }
            FileNodeDto(
                name = entry.path.trimEnd('/').substringAfterLast('/'),
                path = entry.path,
                absolute = joinLocationPath(locationDirectory, entry.path),
                type = entry.type,
            )
        }
    }

    override suspend fun readFileRaw(
        path: String,
        directory: String?,
        workspace: String?,
    ): Response<ResponseBody> {
        // V2Http appends path segments through HttpUrl.Builder, which percent-encodes each segment.
        return http.raw("api/fs/read/$path", locationQuery(directory, workspace))
    }

    override suspend fun readFile(path: String, directory: String?, workspace: String?): FileContentDto =
        http.readBody("api/fs/read/$path", locationQuery(directory, workspace)) { body ->
            val bytes = body.bytes()
            val mimeType = body.contentType()?.toString()
            val text = bytes.decodeUtf8OrNull()?.takeIf { '\u0000' !in it }
            if (text != null && mimeType.isTextualMimeType()) {
                FileContentDto(type = "text", content = text, mimeType = mimeType)
            } else {
                FileContentDto(
                    type = "binary",
                    content = java.util.Base64.getEncoder().encodeToString(bytes),
                    encoding = "base64",
                    mimeType = mimeType,
                )
            }
        }

    override suspend fun getFileStatus(directory: String?, workspace: String?): List<FileStatusDto> {
        val response = http.request("GET", "api/vcs/status", locationQuery(directory, workspace))
        return decodeData<List<V2VcsFileStatus>>(response).map { status ->
            FileStatusDto(
                path = status.file,
                status = status.status,
                added = status.additions,
                removed = status.deletions,
            )
        }
    }

    override suspend fun searchFiles(
        query: String,
        directory: String?,
        workspace: String?,
        dirs: String?,
        type: String?,
        limit: Int?,
    ): List<String> {
        val includeDirectories = dirs?.toBooleanStrictOrNull()
        if (dirs != null && includeDirectories == null) {
            throw IOException("OpenCode v2 filesystem search requires dirs to be true or false")
        }
        val v2Type = when {
            type == "directory" -> "directory"
            type == null && includeDirectories == false -> "file"
            type == "file" && includeDirectories != true -> "file"
            type == null || (type == "file" && includeDirectories == true) -> null
            else -> throw IOException("OpenCode v2 filesystem search does not support type '$type'")
        }
        val searchQuery = locationQuery(directory, workspace) + mapOf(
            "query" to query,
            "type" to v2Type,
            "limit" to limit?.toString(),
        )
        val response = http.request("GET", "api/fs/find", searchQuery)
        return decodeData<List<V2FileEntry>>(response).map(V2FileEntry::path)
    }

    override suspend fun listPtySessions(directory: String?, workspace: String?): List<PtyDto> {
        val response = http.request("GET", "api/pty", locationQuery(directory, workspace))
        return decodeData<List<V2PtyInfo>>(response).map { pty -> dtoMapper.run { pty.toPtyDto() } }
    }

    override suspend fun createPtySession(
        directory: String?,
        workspace: String?,
        request: CreatePtyRequest,
    ): PtyDto {
        val body = buildJsonObject {
            request.command?.let { put("command", it) }
            put("args", buildJsonArray { request.args.forEach { add(JsonPrimitive(it)) } })
            request.cwd?.let { put("cwd", it) }
            request.title?.let { put("title", it) }
            putJsonObject("env") { request.env.forEach { (key, value) -> put(key, value) } }
        }
        val response = http.request("POST", "api/pty", locationQuery(directory, workspace), body)
        val pty = decodeData<V2PtyInfo>(response)
        return dtoMapper.run { pty.toPtyDto() }
    }

    override suspend fun getPtySession(
        id: String,
        directory: String?,
        workspace: String?,
    ): PtyDto {
        val response = http.request("GET", "api/pty/$id", locationQuery(directory, workspace))
        val pty = decodeData<V2PtyInfo>(response)
        return dtoMapper.run { pty.toPtyDto() }
    }

    override suspend fun deletePtySession(id: String, directory: String?, workspace: String?): Boolean {
        http.request("DELETE", "api/pty/$id", locationQuery(directory, workspace))
        return true
    }

    override suspend fun updatePtySession(
        id: String,
        directory: String?,
        workspace: String?,
        request: UpdatePtyRequest,
    ): PtyDto {
        val body = buildJsonObject {
            request.title?.let { put("title", it) }
            request.size?.let { size ->
                putJsonObject("size") {
                    put("rows", size.rows)
                    put("cols", size.cols)
                }
            }
        }
        val response = http.request("PUT", "api/pty/$id", locationQuery(directory, workspace), body)
        val pty = decodeData<V2PtyInfo>(response)
        return dtoMapper.run { pty.toPtyDto() }
    }
}

internal abstract class V2WorkspaceApiBase(
    next: OpenCodeApi,
    protected val http: V2Http,
) : OpenCodeApi by next {
    @PublishedApi
    internal val v2Json = Json { ignoreUnknownKeys = true }

    override suspend fun getConfigProviders(directory: String?, workspace: String?): ConfigProvidersDto {
        val providers = getProviders(directory, workspace)
        return ConfigProvidersDto(providers = providers.all, default = providers.default)
    }

    override suspend fun getVcsStatusRaw(
        directory: String?,
        workspace: String?,
    ): Response<ResponseBody> {
        val response = http.request("GET", "api/vcs/status", locationQuery(directory, workspace))
        return jsonResponse(data(response).toString())
    }

    override suspend fun getVcsDiffRaw(
        mode: VcsDiffMode,
        context: Int,
        directory: String?,
        workspace: String?,
    ): Response<ResponseBody> {
        require(context >= 0) { "context must be non-negative" }
        val v2Mode = when (mode) {
            VcsDiffMode.Git -> "working"
            VcsDiffMode.Branch -> "branch"
        }
        val query = locationQuery(directory, workspace) + mapOf(
            "mode" to v2Mode,
            "context" to context.toString(),
        )
        val response = http.request("GET", "api/vcs/diff", query)
        return jsonResponse(data(response).toString())
    }

    protected fun configAutoupdate(update: String?, current: JsonElement?): JsonElement? = when (update) {
        null -> current
        "disable" -> JsonPrimitive(false)
        "notify" -> JsonPrimitive("notify")
        "auto" -> JsonPrimitive(true)
        else -> throw SerializationException("OpenCode v2 config update mode '$update' is unsupported")
    }

    protected fun requireNoWorkspaceId(workspace: String?) {
        if (workspace != null) {
            throw IOException("OpenCode v2 scopes workspace APIs by directory; workspace IDs cannot be mapped")
        }
    }

    protected fun locationQuery(directory: String?, workspace: String?): Map<String, String?> {
        requireNoWorkspaceId(workspace)
        return mapOf("location[directory]" to directory)
    }

    @PublishedApi
    internal fun data(response: JsonElement): JsonElement = requiredField(response, "data")

    @PublishedApi
    internal inline fun <reified T> decodeData(response: JsonElement): T =
        v2Json.decodeFromJsonElement(data(response))

    protected fun requiredField(response: JsonElement, name: String): JsonElement =
        (response as? JsonObject)?.get(name)
            ?: throw SerializationException("OpenCode v2 response is missing '$name'")

    protected fun jsonResponse(content: String): Response<ResponseBody> =
        Response.success(content.toResponseBody(JSON_MEDIA_TYPE))

    protected fun joinLocationPath(directory: String, relativePath: String): String =
        "${directory.trimEnd('/')}/${relativePath.trimStart('/')}"

    protected fun ByteArray.decodeUtf8OrNull(): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
        null
    }

    protected fun String?.isTextualMimeType(): Boolean {
        if (this == null) return true
        val mediaType = substringBefore(';').trim().lowercase()
        return mediaType.startsWith("text/") ||
            mediaType in TEXTUAL_APPLICATION_TYPES ||
            mediaType.endsWith("+json") ||
            mediaType.endsWith("+xml")
    }

    protected fun modelConfigValue(value: JsonElement): String = when (value) {
        is JsonPrimitive -> if (value.isString) {
            value.content
        } else {
            throw SerializationException("OpenCode v2 config model must be a string or model reference")
        }
        is JsonObject -> {
            val model = v2Json.decodeFromJsonElement<V2ConfigModelRef>(value)
            "${model.providerID}/${model.model}${model.variant?.let { "#$it" }.orEmpty()}"
        }
        else -> throw SerializationException("OpenCode v2 config model must be a string or model reference")
    }

    protected fun McpConfigDto.toV2Config(): JsonObject {
        if (timeout != null) {
            throw IOException(
                "OpenCode v2 MCP config uses separate startup, catalog, and execution timeouts; " +
                    "the v1 scalar timeout cannot be mapped",
            )
        }
        if (type != "local" && type != "remote") {
            throw IOException("OpenCode v2 MCP config does not support type '$type'")
        }
        return buildJsonObject {
            put("type", type)
            command?.let { values -> putJsonArray("command") { values.forEach { add(JsonPrimitive(it)) } } }
            cwd?.let { put("cwd", it) }
            environment?.let { values ->
                putJsonObject("environment") {
                    values.forEach { (key, value) -> put(key, value) }
                }
            }
            url?.let { put("url", it) }
            headers?.let { values -> putJsonObject("headers") { values.forEach { (key, value) -> put(key, value) } } }
            oauth?.let { put("oauth", it) }
            enabled?.let { put("disabled", !it) }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val TEXTUAL_APPLICATION_TYPES = setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/ecmascript",
            "application/x-javascript",
            "application/x-www-form-urlencoded",
            "application/sql",
            "application/graphql",
            "application/yaml",
            "application/x-yaml",
            "application/toml",
            "application/csv",
        )
    }
}

@Serializable
private data class V2Project(
    val id: String,
    val canonical: String,
    val vcs: String? = null,
    val name: String? = null,
    val icon: JsonObject? = null,
    val commands: JsonObject? = null,
    val time: V2ProjectTime,
    val sandboxes: List<String>,
)

@Serializable
private data class V2ProjectTime(
    val created: Long,
    val updated: Long,
    val active: Long? = null,
)

@Serializable
private data class V2LocationInfo(
    val directory: String,
    val project: V2LocationProject,
)

@Serializable
private data class V2LocationProject(
    val id: String,
    val directory: String,
    val canonical: String,
)

@Serializable
private data class V2LocationRef(val directory: String)

@Serializable
private data class V2VcsInfo(
    val provider: String? = null,
    val branch: V2VcsBranch? = null,
)

@Serializable
private data class V2VcsBranch(
    val current: String? = null,
    val default: String? = null,
)

@Serializable
private data class V2VcsFileStatus(
    val file: String,
    val additions: Int,
    val deletions: Int,
    val status: String,
)

@Serializable
private data class V2AgentInfo(
    val id: String,
    val name: String,
    val model: V2ModelRef? = null,
    val system: String? = null,
    val description: String? = null,
    val mode: String,
    val hidden: Boolean,
    val color: String? = null,
    val steps: Int? = null,
    val permissions: JsonElement? = null,
)

@Serializable
private data class V2ModelRef(
    val id: String,
    @SerialName("providerID") val providerID: String,
    val variant: String? = null,
)

@Serializable
private data class V2ProviderInfo(
    val id: String,
    val name: String,
)

@Serializable
private data class V2ModelInfo(
    val modelID: String,
    val providerID: String,
    val family: String? = null,
    val name: String,
    val compatibility: V2ModelCompatibility? = null,
    val headers: Map<String, String>? = null,
    val capabilities: V2ModelCapabilities,
    val variants: List<V2ModelVariant>,
    val cost: List<V2ModelCost>,
    val status: String,
    val enabled: Boolean,
    val limit: V2ModelLimit,
)

@Serializable
private data class V2ModelCompatibility(
    val reasoningField: String? = null,
    val requireReasoning: Boolean? = null,
)

@Serializable
private data class V2ModelCapabilities(
    val tools: Boolean,
    val input: List<String>,
    val output: List<String>,
)

@Serializable
private data class V2ModelVariant(
    val id: String,
    val settings: JsonObject? = null,
    val headers: Map<String, String>? = null,
    val body: JsonObject? = null,
)

@Serializable
private data class V2ModelCost(
    val tier: V2ModelCostTier? = null,
    val input: Double,
    val output: Double,
    val cache: V2ModelCache,
)

@Serializable
private data class V2ModelCostTier(
    val type: String,
    val size: Int,
)

@Serializable
private data class V2ModelCache(val read: Double, val write: Double)

@Serializable
private data class V2ModelLimit(
    val context: Int,
    val input: Int? = null,
    val output: Int,
)

@Serializable
private data class V2ConfigEntry(val info: V2ConfigInfo? = null)

@Serializable
private data class V2ConfigInfo(
    val model: JsonElement? = null,
    val share: String? = null,
    val username: String? = null,
    val update: String? = null,
    val instructions: List<String>? = null,
)

@Serializable
private data class V2ConfigModelRef(
    val providerID: String,
    val model: String,
    val variant: String? = null,
)

@Serializable
private data class V2McpServer(
    val name: String,
    val status: V2McpStatus,
    val integrationID: String? = null,
)

@Serializable
private data class V2McpStatus(
    val status: String,
    val error: String? = null,
)

@Serializable
private data class V2CommandInfo(
    val name: String,
    val description: String? = null,
)

@Serializable
private data class V2FileEntry(
    val path: String,
    val type: String,
)

@Serializable
private data class V2PtyInfo(
    val id: String,
    val title: String,
    val command: String,
    val args: List<String>,
    val cwd: String,
    val status: String,
    val pid: Int? = null,
    val exitCode: Int? = null,
)

private class V2WorkspaceDtoMapper(private val v2Json: Json) {
    fun V2VcsInfo.toLegacyInfo(): VcsInfoDto = VcsInfoDto(
        branch = branch?.current,
        defaultBranch = branch?.default,
    )

    fun V2VcsInfo.toWorkspaceInfo(): WorkspaceVcsInfoDto = WorkspaceVcsInfoDto(
        branch = branch?.current,
        defaultBranch = branch?.default,
    )

    fun V2ModelInfo.legacyModelId(): String = "$providerID/$modelID"

    fun V2Project.toProjectDto(): ProjectDto = ProjectDto(
        id = id,
        worktree = canonical,
        vcs = vcs,
        time = ProjectTimeDto(created = time.created, updated = time.updated),
        sandboxes = sandboxes,
        name = name,
        icon = icon,
        commands = commands,
    )

    fun V2AgentInfo.toAgentDto(): AgentDto = AgentDto(
        name = name,
        description = description,
        mode = mode,
        hidden = hidden,
        color = color,
        steps = steps?.toDouble(),
        permission = permissions,
        model = model?.let { ModelRefDto(providerID = it.providerID, modelID = it.id) },
        variant = model?.variant,
        systemPrompt = system,
    )

    fun V2ProviderInfo.toProviderDto(models: List<V2ModelInfo>): ProviderDto =
        ProviderDto(
            id = id,
            name = name,
            // V2 Provider.Info does not expose the legacy source classification.
            source = null,
            options = null,
            models = models.associate { model -> model.modelID to model.toModelDto() },
        )

    fun V2ModelInfo.toModelDto(): ModelDto {
        val baseCost = cost.firstOrNull { it.tier == null }
        val tiers = if (baseCost == null) {
            emptyList()
        } else {
            cost.mapNotNull { item ->
                item.tier?.let { tier ->
                    ModelCostTierDto(
                        input = item.input,
                        output = item.output,
                        cache = item.cache.toLegacyCache(),
                        tier = ModelCostTierRuleDto(type = tier.type, size = tier.size.toDouble()),
                    )
                }
            }
        }
        val inputModalities = capabilities.input
        val outputModalities = capabilities.output
        val reasoning = compatibility?.reasoningField != null || compatibility?.requireReasoning == true
        val variantsJson = variants.takeIf { it.isNotEmpty() }?.let { entries ->
            JsonObject(entries.associate { it.id to v2Json.encodeToJsonElement(it) })
        }
        return ModelDto(
            id = modelID,
            providerId = providerID,
            name = name,
            family = family,
            capabilities = ModelCapabilitiesDto(
                reasoning = reasoning,
                attachment = inputModalities.any { it != "text" },
                toolcall = capabilities.tools,
                input = inputModalities.toModalities(),
                output = outputModalities.toModalities(),
            ),
            cost = baseCost?.let { base ->
                ModelCostDto(
                    input = base.input,
                    output = base.output,
                    cache = base.cache.toLegacyCache(),
                    tiers = tiers.takeIf { it.isNotEmpty() },
                )
            },
            limit = ModelLimitDto(context = limit.context, output = limit.output),
            status = status,
            variants = variantsJson,
            headers = headers,
            contextLength = limit.context,
            supportsTools = capabilities.tools,
            supportsReasoning = reasoning,
        )
    }

    fun V2ModelCache.toLegacyCache() =
        CacheCostDto(read = read, write = write)

    fun List<String>.toModalities() = ModalitiesDto(
        text = "text" in this,
        audio = "audio" in this,
        image = "image" in this,
        video = "video" in this,
        pdf = "pdf" in this,
    )

    fun V2PtyInfo.toPtyDto() = PtyDto(
        id = id,
        title = title,
        command = command,
        args = args,
        cwd = cwd,
        status = status,
        pid = pid,
        exitCode = exitCode,
    )
}
