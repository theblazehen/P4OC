package dev.blazelight.p4oc.ui.workspace

import androidx.lifecycle.ViewModel
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesRepository
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.workspace.Workspace

class WorkspaceViewModel(
    private val owner: WorkspaceRepositoryOwner,
) : ViewModel() {
    val tabId: String = owner.tabId
    val workspace: Workspace = owner.workspace
    val generation: ServerGeneration = owner.generation
    val workspaceClient: WorkspaceClient = owner.workspaceClient
    val sessionRepository = owner.sessionRepository
    val fileRepository = owner.fileRepository
    val workspaceChangesRepository: WorkspaceChangesRepository = owner.workspaceChangesRepository
    val uploadCoordinator = owner.uploadCoordinator

    val identityHash: Int = owner.identityHash

    fun touch(destinationRoute: String?) {
        owner.touch(destinationRoute)
    }
}
