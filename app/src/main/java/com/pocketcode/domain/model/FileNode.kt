package com.pocketcode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val children: List<FileNode>? = null
)

@Serializable
data class FileContent(
    val path: String,
    val content: String,
    val mimeType: String? = null,
    val size: Long? = null
)

@Serializable
data class FileStatus(
    val path: String,
    val status: String, // "modified", "added", "deleted", "renamed"
    val staged: Boolean = false
)
