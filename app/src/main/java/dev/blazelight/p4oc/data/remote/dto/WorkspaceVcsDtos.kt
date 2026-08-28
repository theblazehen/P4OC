package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class WorkspaceVcsInfoDto(
    val branch: String? = null,
    @SerialName("default_branch") val defaultBranch: String? = null,
)

@Serializable
internal data class WorkspaceVcsStatusDto(
    val file: String,
    val status: String,
    val additions: Long,
    val deletions: Long,
)

@Serializable
internal data class WorkspaceVcsDiffDto(
    val file: String,
    val patch: String? = null,
    val status: String? = null,
    val additions: Long,
    val deletions: Long,
)
