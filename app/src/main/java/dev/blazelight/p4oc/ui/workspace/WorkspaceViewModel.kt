package dev.blazelight.p4oc.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.workspace.Workspace
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    val tabId: String,
    val workspace: Workspace,
    val generation: ServerGeneration,
    activeServerApiProvider: ActiveServerApiProvider,
    messageMapper: MessageMapper,
    connectionManager: ConnectionManager,
) : ViewModel() {
    val workspaceClient: WorkspaceClient = WorkspaceClient(workspace, generation, activeServerApiProvider)
    val sessionRepository: SessionRepositoryImpl = SessionRepositoryImpl(workspaceClient, messageMapper)

    val identityHash: Int = System.identityHashCode(this)

    init {
        AppLog.i(TAG, logPrefix("init"))
        viewModelScope.launch {
            connectionManager.scopedEvents.collect { scopedEvent ->
                if (scopedEvent.serverRef == workspace.server &&
                    scopedEvent.generation == generation &&
                    scopedEvent.workspaceKey == workspace.key
                ) {
                    sessionRepository.acceptEvent(scopedEvent.event)
                }
            }
        }
    }

    fun touch(destinationRoute: String?) {
        AppLog.d(TAG) { "${logPrefix("touch")} destination=$destinationRoute" }
    }

    override fun onCleared() {
        sessionRepository.close()
        AppLog.i(TAG, logPrefix("onCleared"))
        super.onCleared()
    }

    private fun logPrefix(event: String): String =
        "WorkspaceViewModel.$event tabId=$tabId workspaceKey=${workspace.key} server=${workspace.server.endpointKey} generation=${generation.value} identity=$identityHash"

    private companion object {
        const val TAG = "WorkspaceViewModel"
    }
}
