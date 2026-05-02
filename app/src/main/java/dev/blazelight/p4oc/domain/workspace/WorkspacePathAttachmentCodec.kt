package dev.blazelight.p4oc.domain.workspace

import java.net.URLDecoder
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun WorkspacePath.Relative.toAttachmentUrl(): String = path.value.encodePathSegments()

object WorkspacePathAttachmentCodec {
    fun parseFromServer(value: String): WorkspacePath.Relative = WorkspacePath.Relative(
        RelativePath(value.asServerPathValue().decodePathSegments()),
    )

    fun parseSymbolUri(uri: String): WorkspacePath.Relative = parseFromServer(uri)
}

object WorkspacePathParser {
    fun parseFromServer(value: String): WorkspacePath.Relative =
        WorkspacePathAttachmentCodec.parseFromServer(value)
}

private fun String.encodePathSegments(): String = split("/")
    .joinToString("/") { segment ->
        URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

private fun String.decodePathSegments(): String = split("/")
    .joinToString("/") { segment ->
        URLDecoder.decode(segment, StandardCharsets.UTF_8.name())
    }

private fun String.asServerPathValue(): String {
    if (!startsWith("file://", ignoreCase = true)) return this

    val uri = URI(this)
    val rawAuthority = uri.rawAuthority?.let { "$it/" }.orEmpty()
    val rawPath = uri.rawPath?.removePrefix("/").orEmpty()
    val rawQuery = uri.rawQuery?.let { "?$it" }.orEmpty()
    val rawFragment = uri.rawFragment?.let { "#$it" }.orEmpty()
    return rawAuthority + rawPath + rawQuery + rawFragment
}
