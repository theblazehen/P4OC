package dev.blazelight.p4oc.domain.workspace

import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.server.WorkspaceKey

data class Workspace(
    val server: ServerRef,
    val directory: String?,
) {
    init {
        require(directory == null || directory.isNotBlank()) { "Workspace directory must not be blank" }
    }

    val key: WorkspaceKey = directory?.let(WorkspaceKey::Directory) ?: WorkspaceKey.Global
}
