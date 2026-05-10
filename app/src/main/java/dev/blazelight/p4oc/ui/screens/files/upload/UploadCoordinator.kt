package dev.blazelight.p4oc.ui.screens.files.upload

import dev.blazelight.p4oc.data.files.FileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UploadCoordinator(
    private val scope: CoroutineScope,
    private val repositoryFactory: () -> FileRepository,
    private val destinationPath: () -> String?,
    private val onComplete: suspend (List<UploadItem>) -> Unit = {},
) {
    private val _state = MutableStateFlow(UploadQueueState())
    val state: StateFlow<UploadQueueState> = _state

    private var uploadJob: Job? = null
    private var orchestrator: UploadOrchestrator? = null

    fun upload(source: UploadSource, sourceIds: List<String>) {
        if (sourceIds.isEmpty()) return
        if (_state.value.isActive) {
            _state.value = _state.value.copy(
                notice = "Upload already in progress; wait for it to finish before adding more files",
            )
            return
        }

        val currentOrchestrator = UploadOrchestrator(
            fileRepository = repositoryFactory(),
            source = source,
        ).also { orchestrator = it }

        uploadJob?.cancel()
        uploadJob = scope.launch(Dispatchers.IO) {
            val mirrorJob = launch {
                currentOrchestrator.state.collect { _state.value = it }
            }
            try {
                val plans = sourceIds.map { id ->
                    val metaResult = runCatching { source.probe(id) }
                    val meta = metaResult.getOrNull()
                    UploadOrchestrator.Plan(
                        sourceId = id,
                        displayName = meta?.displayName,
                        sizeBytes = meta?.sizeBytes ?: -1L,
                        mimeType = meta?.mimeType,
                        probeFailure = metaResult.exceptionOrNull()?.message,
                    )
                }
                val finalState = currentOrchestrator.run(destinationPath(), plans)
                _state.value = finalState
                onComplete(finalState.successes)
            } finally {
                mirrorJob.cancel()
            }
        }
    }

    fun retryFailed() {
        val currentOrchestrator = orchestrator ?: return
        if (_state.value.failures.isEmpty() || _state.value.isActive) return
        uploadJob?.cancel()
        uploadJob = scope.launch(Dispatchers.IO) {
            val mirrorJob = launch {
                currentOrchestrator.state.collect { _state.value = it }
            }
            try {
                currentOrchestrator.retryFailed()
                val finalState = currentOrchestrator.state.value
                _state.value = finalState
                onComplete(finalState.successes)
            } finally {
                mirrorJob.cancel()
            }
        }
    }

    fun cancel() {
        val currentJob = uploadJob ?: return
        val currentOrchestrator = orchestrator ?: return
        currentOrchestrator.markCancelled()
        _state.value = currentOrchestrator.state.value
        uploadJob = null
        scope.launch {
            currentJob.cancelAndJoin()
            _state.value = currentOrchestrator.state.value
        }
    }

    fun dismiss() {
        if (_state.value.isActive) return
        _state.value = UploadQueueState()
        orchestrator = null
    }
}
