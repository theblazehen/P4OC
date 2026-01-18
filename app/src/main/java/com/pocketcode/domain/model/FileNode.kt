package com.pocketcode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FileNode(
    val name: String,
    val path: String,
    val absolute: String = "",
    val type: String = "file",
    val ignored: Boolean = false
) {
    val isDirectory: Boolean get() = type == "directory"
}

@Serializable
data class FileContent(
    val type: String = "text",
    val content: String,
    val diff: String? = null,
    val mimeType: String? = null
)

@Serializable
data class FileStatus(
    val path: String,
    val status: String,
    val added: Int = 0,
    val removed: Int = 0
)
