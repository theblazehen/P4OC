package dev.blazelight.p4oc.domain.workspace

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun WorkspacePath.Relative.toAttachmentUrl(): String = path.value.encodePathSegments()

object WorkspacePathAttachmentCodec {
    fun parseFromServer(value: String): WorkspacePath.Relative = WorkspacePath.Relative(
        RelativePath(value.decodePathSegments()),
    )
}

private fun String.encodePathSegments(): String = split("/")
    .joinToString("/") { segment ->
        URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

private fun String.decodePathSegments(): String = split("/")
    .joinToString("/") { segment ->
        URLDecoder.decode(segment, StandardCharsets.UTF_8.name())
    }
