package dev.blazelight.p4oc.ui.screens.files

import dev.blazelight.p4oc.data.vcs.WorkspaceChangesRepository
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesResult
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesSnapshot
import dev.blazelight.p4oc.data.vcs.WorkspacePatch

internal object NoOpWorkspaceChangesRepository : WorkspaceChangesRepository {
    override suspend fun loadSnapshot(): WorkspaceChangesResult<WorkspaceChangesSnapshot> =
        WorkspaceChangesResult.Unsupported

    override suspend fun loadDiff(): WorkspaceChangesResult<Map<String, WorkspacePatch>> =
        WorkspaceChangesResult.Unsupported
}
