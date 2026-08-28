package dev.blazelight.p4oc.data.media

import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.network.TerminalTransport
import dev.blazelight.p4oc.data.remote.dto.FileContentDto
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.Part
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.concurrent.TimeUnit

fun interface ChatMediaLoader {
    suspend fun load(part: Part.File): ChatMediaLoadResult
}

sealed interface ChatMediaLoadResult {
    data class Loaded(
        val bytes: ByteArray,
        val mimeType: String,
    ) : ChatMediaLoadResult

    data object Unavailable : ChatMediaLoadResult
}

class WorkspaceChatMediaLoader internal constructor(
    private val workspaceDirectory: String?,
    private val workspaceFileReader: suspend (path: String, maxResponseBytes: Long) -> FileContentDto,
    private val terminalTransportLookup: () -> TerminalTransport?,
    private val httpCallFactory: (client: OkHttpClient, request: Request) -> Call = { client, request ->
        client.newCall(request)
    },
) : ChatMediaLoader {
    constructor(
        workspaceClient: WorkspaceClient,
        serverConnectionRegistry: ServerConnectionRegistry?,
    ) : this(
        workspaceDirectory = workspaceClient.workspace.directory,
        workspaceFileReader = workspaceClient::readFileBounded,
        terminalTransportLookup = {
            serverConnectionRegistry
                ?.terminalTransport(workspaceClient.workspace.server, workspaceClient.generation)
        },
    )

    override suspend fun load(part: Part.File): ChatMediaLoadResult {
        if (imageMimeType(part.mime) == null || part.url != part.url.trim()) {
            return ChatMediaLoadResult.Unavailable
        }

        return try {
            when {
                part.url.startsWith(DATA_SCHEME, ignoreCase = true) -> loadDataUrl(part.url)
                part.url.startsWith(FILE_SCHEME, ignoreCase = true) -> loadWorkspaceFile(part.url)
                else -> loadHttpUrl(part.url)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ChatMediaLoadResult.Unavailable
        }
    }

    private fun loadDataUrl(url: String): ChatMediaLoadResult {
        val commaIndex = url.indexOf(',')
        var loaded: ChatMediaLoadResult.Loaded? = null
        if (commaIndex > DATA_SCHEME.length) {
            val descriptor = url.substring(DATA_SCHEME.length, commaIndex)
            val base64Marker = descriptor.lastIndexOf(';')
            if (base64Marker > 0 && descriptor.hasBase64EncodingAt(base64Marker)) {
                val mimeType = imageMimeType(descriptor.substring(0, base64Marker))
                val bytes = decodeBoundedBase64(url.substring(commaIndex + 1))
                if (mimeType != null && bytes != null) {
                    loaded = ChatMediaLoadResult.Loaded(bytes, mimeType)
                }
            }
        }
        return loaded ?: ChatMediaLoadResult.Unavailable
    }

    private suspend fun loadWorkspaceFile(url: String): ChatMediaLoadResult {
        val loaded = workspaceRelativePath(url)?.let { path ->
            val content = workspaceFileReader(path, MAX_CHAT_MEDIA_FILE_RESPONSE_BYTES)
            val mimeType = imageMimeType(content.mimeType)
            val bytes = if (content.hasBase64Encoding()) decodeBoundedBase64(content.content) else null
            if (mimeType != null && bytes != null) {
                ChatMediaLoadResult.Loaded(bytes, mimeType)
            } else {
                null
            }
        }
        return loaded ?: ChatMediaLoadResult.Unavailable
    }

    private fun workspaceRelativePath(url: String): String? {
        val uri = URI(url)
        val decodedPath = uri.path
        val root = workspaceDirectory?.let(Paths::get)?.normalize()
        val candidate = decodedPath
            ?.takeIf { it.isSafeFilePath() }
            ?.let(Paths::get)
            ?.normalize()
        return if (uri.hasSupportedFileShape()) {
            containedWorkspaceRelativePath(root, candidate)?.toApiPath()
        } else {
            null
        }
    }

    private suspend fun loadHttpUrl(url: String): ChatMediaLoadResult {
        val parsed = url.toHttpUrlOrNull()
            ?.takeIf { it.isSafeHttpUrl() }
            ?: return ChatMediaLoadResult.Unavailable
        val transport = terminalTransportLookup()
        val serverOrigin = transport
            ?.connection
            ?.config
            ?.url
            ?.toHttpUrlOrNull()
            ?.takeIf { it.isSafeHttpUrl() }
        val loaded = if (transport != null && serverOrigin != null && parsed.isSafeFor(serverOrigin)) {
            val redirectSafeClient = transport.authClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .readTimeout(MEDIA_HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(MEDIA_HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(parsed).get().build()
            redirectSafeClient.fetchBoundedImage(request)
        } else {
            null
        }
        return loaded ?: ChatMediaLoadResult.Unavailable
    }

    private suspend fun OkHttpClient.fetchBoundedImage(request: Request): ChatMediaLoadResult =
        suspendCancellableCoroutine { continuation ->
            val call = httpCallFactory(this, request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(ChatMediaLoadResult.Unavailable) { _, _, _ -> }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = try {
                            response.use(::decodeImageResponse)
                        } catch (_: Exception) {
                            ChatMediaLoadResult.Unavailable
                        }
                        continuation.resume(result) { _, _, _ -> }
                    }
                }
            )
        }

    private fun decodeImageResponse(response: Response): ChatMediaLoadResult {
        val loaded = if (response.isSuccessful && !response.isRedirect) {
            val body = response.body
            val mimeType = imageMimeType(body.contentType()?.toString())
            val bytes = mimeType?.let { body.byteStream().readBounded(body.contentLength()) }
            if (mimeType != null && bytes != null) {
                ChatMediaLoadResult.Loaded(bytes, mimeType)
            } else {
                null
            }
        } else {
            null
        }
        return loaded ?: ChatMediaLoadResult.Unavailable
    }

    private fun InputStream.readBounded(contentLength: Long): ByteArray? {
        val hasInvalidDeclaredLength = contentLength > MAX_CHAT_MEDIA_BYTES || contentLength > Int.MAX_VALUE
        return if (hasInvalidDeclaredLength) {
            null
        } else {
            val initialSize = if (contentLength < 0L) STREAM_BUFFER_SIZE else contentLength.toInt()
            val output = ByteArrayOutputStream(initialSize)
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            output.takeIf { readIntoBounded(output, buffer) }?.toByteArray()
        }
    }

    private fun InputStream.readIntoBounded(output: ByteArrayOutputStream, buffer: ByteArray): Boolean {
        var total = 0L
        var read = read(buffer)
        while (read >= 0 && total <= MAX_CHAT_MEDIA_BYTES) {
            if (read > 0) {
                total += read
                output.writeIfWithinLimit(buffer, read, total)
            }
            read = if (total <= MAX_CHAT_MEDIA_BYTES) read(buffer) else -1
        }
        return total <= MAX_CHAT_MEDIA_BYTES
    }

    private fun ByteArrayOutputStream.writeIfWithinLimit(buffer: ByteArray, count: Int, total: Long) {
        if (total <= MAX_CHAT_MEDIA_BYTES) write(buffer, 0, count)
    }

    private fun decodeBoundedBase64(encoded: String): ByteArray? =
        if (encoded.length > MAX_CHAT_MEDIA_BASE64_CHARS) {
            null
        } else {
            try {
                Base64.getDecoder().decode(encoded).takeIf { it.size <= MAX_CHAT_MEDIA_BYTES }
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    private fun imageMimeType(value: String?): String? = value
        ?.toMediaTypeOrNull()
        ?.takeIf { it.isSupportedImage() }
        ?.let { "${it.type}/${it.subtype}" }

    private fun String.hasBase64EncodingAt(separatorIndex: Int): Boolean =
        substring(separatorIndex + 1).equals(BASE64_ENCODING, ignoreCase = true)

    private fun FileContentDto.hasBase64Encoding(): Boolean =
        encoding.equals(BASE64_ENCODING, ignoreCase = true)

    private fun String.isSafeFilePath(): Boolean = '\u0000' !in this && '\\' !in this

    private fun URI.hasSupportedFileShape(): Boolean =
        hasSupportedFileScheme() && hasNoAuthority() && hasNoQueryOrFragment()

    private fun URI.hasSupportedFileScheme(): Boolean =
        scheme.equals(FILE_SCHEME_NAME, ignoreCase = true) && !isOpaque

    private fun URI.hasNoAuthority(): Boolean = rawAuthority.isNullOrEmpty()

    private fun URI.hasNoQueryOrFragment(): Boolean = rawQuery == null && rawFragment == null

    private fun HttpUrl.isSafeFor(origin: HttpUrl): Boolean =
        isSafeHttpUrl() && hasSameOrigin(origin)

    private fun HttpUrl.isSafeHttpUrl(): Boolean = hasSupportedHttpScheme() && hasNoUserInfo()

    private fun HttpUrl.hasSupportedHttpScheme(): Boolean = scheme == HTTP_SCHEME || scheme == HTTPS_SCHEME

    private fun HttpUrl.hasNoUserInfo(): Boolean = username.isEmpty() && password.isEmpty()

    private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port

    private companion object {
        const val DATA_SCHEME = "data:"
        const val FILE_SCHEME = "file:"
        const val FILE_SCHEME_NAME = "file"
        const val HTTP_SCHEME = "http"
        const val HTTPS_SCHEME = "https"
        const val BASE64_ENCODING = "base64"
        const val IMAGE_TYPE = "image"
        const val WILDCARD_SUBTYPE = "*"
        const val STREAM_BUFFER_SIZE = 8 * 1024
        const val MEDIA_HTTP_TIMEOUT_SECONDS = 60L
    }
}

private fun containedWorkspaceRelativePath(root: Path?, candidate: Path?): Path? {
    if (root == null || candidate == null) return null
    return if (areAbsolute(root, candidate) && candidate.isDescendantOf(root)) {
        root.relativize(candidate)
    } else {
        null
    }
}

private fun areAbsolute(root: Path, candidate: Path): Boolean = root.isAbsolute && candidate.isAbsolute

private fun Path.isDescendantOf(root: Path): Boolean = this != root && startsWith(root)

private fun Path.toApiPath(): String = iterator().asSequence().joinToString("/") { it.toString() }

private fun okhttp3.MediaType.isSupportedImage(): Boolean =
    type == "image" && subtype.isNotBlank() && subtype != "*"

internal const val MAX_CHAT_MEDIA_BYTES: Int = 8 * 1024 * 1024
internal const val MAX_CHAT_MEDIA_BASE64_CHARS: Int = ((MAX_CHAT_MEDIA_BYTES + 2) / 3) * 4
internal const val MAX_CHAT_MEDIA_FILE_RESPONSE_BYTES: Long = 11_184_812L + 64L * 1024L
