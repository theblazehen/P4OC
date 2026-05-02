package dev.blazelight.p4oc.ui.workspace

import androidx.lifecycle.ViewModel
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.workspace.Workspace

class WorkspaceViewModel(
    val tabId: String,
    val workspace: Workspace,
    val generation: ServerGeneration,
    activeServerApiProvider: ActiveServerApiProvider,
) : ViewModel() {
    val workspaceClient: WorkspaceClient = WorkspaceClient(workspace, generation, activeServerApiProvider)
    val sessionRepository: SessionRepositoryImpl = SessionRepositoryImpl(workspaceClient)

    val identityHash: Int = System.identityHashCode(this)

    init {
        AppLog.i(TAG, logPrefix("init"))
    }

    fun touch(destinationRoute: String?) {
        AppLog.d(TAG) { "${logPrefix("touch")} destination=$destinationRoute" }
    }

    override fun onCleared() {
        AppLog.i(TAG, logPrefix("onCleared"))
        super.onCleared()
    }

    private fun logPrefix(event: String): String =
        "WorkspaceViewModel.$event tabId=$tabId workspaceKey=${workspace.key} server=${workspace.server.endpointKey} generation=${generation.value} identity=$identityHash"

    private companion object {
        const val TAG = "WorkspaceViewModel"
    }
}
