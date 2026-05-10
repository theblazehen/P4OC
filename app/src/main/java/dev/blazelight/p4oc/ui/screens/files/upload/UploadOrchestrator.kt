package dev.blazelight.p4oc.ui.screens.files.upload

import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileUploadRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Drives a serial whole-file upload batch and exposes per-item progress as
 * [UploadQueueState].
 *
 * Retry policy: only [FileOperationResult.Failed] outcomes are retried (up to
 * [maxAttempts] total attempts). [FileOperationResult.Conflict] is treated as
 * a terminal user-actionable failure for that item — we do not blindly retry
 * past hash conflicts. This is whole-file retry; the OFISH layer aborts its
 * temp file on every failed chunk, so there is no remote partial state to
 * preserve.
 */
class UploadOrchestrator(
    private val fileRepository: FileRepository,
    private val source: UploadSource,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val retryDelayMillis: (attempt: Int) -> Long = ::defaultBackoff,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(UploadQueueState())
    val state: StateFlow<UploadQueueState> = _state.asStateFlow()

    data class Plan(
        val sourceId: String,
        val displayName: String?,
        val sizeBytes: Long,
        val mimeType: String?,
        val probeFailure: String? = null,
    )

    /**
     * Run the batch. Returns when finished or cancelled. Suspends per item;
     * caller controls the scope/dispatcher. Safe to cancel via the calling
     * coroutine.
     */
    suspend fun run(currentPath: String?, plans: List<Plan>): UploadQueueState {
        val items = plans.map { plan ->
            val sanitized = sanitizeUploadName(plan.displayName, now())
            UploadItem(
                sourceId = plan.sourceId,
                displayName = sanitized,
                destinationPath = joinDestinationPath(currentPath, sanitized),
                mimeType = plan.mimeType ?: DEFAULT_MIME,
                bytesTotal = plan.sizeBytes.coerceAtLeast(0L),
                probeFailure = plan.probeFailure,
            )
        }
        _state.value = UploadQueueState(items = items, currentIndex = 0, isActive = items.isNotEmpty())

        items.forEachIndexed { index, _ ->
            mutate { it.copy(currentIndex = index) }
            uploadOne(index)
        }

        mutate { it.copy(isActive = false) }
        return _state.value
    }

    /**
     * Re-run only the items currently in [UploadPhase.Failed], preserving
     * already-completed entries in the queue so the UI keeps showing
     * successful uploads.
     */
    suspend fun retryFailed() {
        val snapshot = _state.value
        val failedIndices = snapshot.items.mapIndexedNotNull { i, item ->
            if (item.phase is UploadPhase.Failed) i else null
        }
        if (failedIndices.isEmpty()) return
        // Reset failed items to Pending and mark active; preserve done items.
        _state.update { state ->
            val items = state.items.toMutableList()
            failedIndices.forEach { i ->
                items[i] = items[i].copy(phase = UploadPhase.Pending, attempts = 0)
            }
            state.copy(items = items, isActive = true, cancelled = false)
        }
        for (idx in failedIndices) {
            mutate { it.copy(currentIndex = idx) }
            uploadOne(idx)
        }
        mutate { it.copy(isActive = false) }
    }

    /**
     * Mark the queue cancelled and convert any in-flight item (Reading /
     * Uploading / Pending) into a terminal Failed("cancelled") so the UI
     * doesn't keep displaying it as active forever after the job is killed.
     */
    fun markCancelled() {
        _state.update { state ->
            val items = state.items.map { item ->
                when (item.phase) {
                    UploadPhase.Pending,
                    UploadPhase.Reading,
                    UploadPhase.Uploading -> item.copy(phase = UploadPhase.Failed(CANCELLED_MESSAGE))
                    else -> item
                }
            }
            state.copy(items = items, isActive = false, cancelled = true)
        }
    }

    private suspend fun uploadOne(index: Int) {
        val item = _state.value.items.getOrNull(index) ?: return
        updateItem(index) { it.copy(phase = UploadPhase.Reading, attempts = 0) }

        val bytes = try {
            source.readBytes(item.sourceId, maxBytes)
        } catch (cancellation: CancellationException) {
            // Never swallow coroutine cancellation as a normal read failure;
            // markCancelled() will set the phase appropriately.
            throw cancellation
        } catch (tooBig: UploadTooLargeException) {
            // The 25 MiB cap is enforced because FileRepository.uploadFile
            // takes a ByteArray; we read the whole payload into memory.
            updateItem(index) {
                it.copy(phase = UploadPhase.Failed(
                    "File exceeds ${formatFileSize(tooBig.maxBytes)} upload limit"
                ))
            }
            return
        } catch (t: Throwable) {
            updateItem(index) {
                it.copy(phase = UploadPhase.Failed(t.message ?: "Failed to read file"))
            }
            return
        }

        updateItem(index) { it.copy(phase = UploadPhase.Uploading, bytesTotal = bytes.size.toLong(), bytesUploaded = 0L) }

        var lastFailure: String? = null
        for (attempt in 1..maxAttempts) {
            updateItem(index) { it.copy(attempts = attempt) }
            val request = FileUploadRequest(
                path = item.destinationPath,
                bytes = bytes,
                expectedHash = null,
                onBytesUploaded = { uploaded ->
                    updateItem(index) { current ->
                        current.copy(bytesUploaded = uploaded.coerceIn(0L, current.bytesTotal))
                    }
                },
            )
            when (val result = fileRepository.uploadFile(request)) {
                is FileOperationResult.Ok -> {
                    updateItem(index) { it.copy(phase = UploadPhase.Done, bytesUploaded = it.bytesTotal) }
                    return
                }
                is FileOperationResult.Conflict -> {
                    updateItem(index) {
                        it.copy(phase = UploadPhase.Failed(result.message))
                    }
                    return
                }
                is FileOperationResult.Failed -> {
                    lastFailure = result.message
                    if (attempt < maxAttempts) delay(retryDelayMillis(attempt))
                }
            }
        }
        updateItem(index) {
            it.copy(phase = UploadPhase.Failed(lastFailure ?: "Upload failed"))
        }
    }

    private fun updateItem(index: Int, transform: (UploadItem) -> UploadItem) {
        _state.update { state ->
            val current = state.items.getOrNull(index) ?: return@update state
            state.copy(items = state.items.toMutableList().also { it[index] = transform(current) })
        }
    }

    private inline fun mutate(crossinline transform: (UploadQueueState) -> UploadQueueState) {
        _state.update(transform)
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3

        /**
         * Per-file size cap (25 MiB). Picked because [FileRepository.uploadFile]
         * accepts a `ByteArray`, so the full payload is buffered in memory
         * before being chunked by the OFISH layer. Raising this requires a
         * streaming upload API.
         */
        const val DEFAULT_MAX_BYTES: Long = 25L * 1024L * 1024L
        const val DEFAULT_MIME = "application/octet-stream"
        const val CANCELLED_MESSAGE = "cancelled"

        private fun defaultBackoff(attempt: Int): Long = when (attempt) {
            1 -> 200L
            2 -> 600L
            else -> 1_000L
        }
    }
}
