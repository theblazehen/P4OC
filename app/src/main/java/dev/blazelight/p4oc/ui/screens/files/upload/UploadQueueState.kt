package dev.blazelight.p4oc.ui.screens.files.upload

/**
 * UI state for the SAF upload batch.
 *
 * The orchestration is whole-file (not chunked): each item progresses
 * Pending -> Reading -> Uploading -> (Done | Failed). Per-chunk progress is
 * not exposed by FileRepository.uploadFile, so we report only phase + size.
 */
data class UploadQueueState(
    val items: List<UploadItem> = emptyList(),
    val currentIndex: Int = 0,
    val isActive: Boolean = false,
    val cancelled: Boolean = false,
) {
    val total: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()
    val isTerminal: Boolean get() = !isActive && items.isNotEmpty()
    val current: UploadItem? get() = items.getOrNull(currentIndex)
    val successes: List<UploadItem> get() = items.filter { it.phase is UploadPhase.Done }
    val failures: List<UploadItem> get() = items.filter { it.phase is UploadPhase.Failed }
    val anySuccess: Boolean get() = items.any { it.phase is UploadPhase.Done }
}

data class UploadItem(
    /** Stable id derived from source URI string for retry/dedup. */
    val sourceId: String,
    val displayName: String,
    val destinationPath: String,
    val mimeType: String,
    val bytesTotal: Long,
    val phase: UploadPhase = UploadPhase.Pending,
    val attempts: Int = 0,
)

sealed interface UploadPhase {
    data object Pending : UploadPhase
    data object Reading : UploadPhase
    data object Uploading : UploadPhase
    data object Done : UploadPhase
    data class Failed(val message: String) : UploadPhase
}
