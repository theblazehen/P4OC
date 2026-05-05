package dev.blazelight.p4oc.ui.screens.files.upload

/**
 * Abstraction over reading an upload payload from a SAF Uri. The Android
 * implementation lives in [ContentResolverUploadSource]; tests can stub this
 * with in-memory bytes to avoid Robolectric.
 */
interface UploadSource {
    /** Return lightweight metadata for the source (name, size, mime). */
    suspend fun probe(sourceId: String): UploadSourceMetadata

    /** Read the full payload bytes. Implementations must respect [maxBytes]. */
    suspend fun readBytes(sourceId: String, maxBytes: Long): ByteArray
}

data class UploadSourceMetadata(
    val displayName: String?,
    val sizeBytes: Long,
    val mimeType: String?,
)

/** Thrown when a source exceeds [UploadOrchestrator.maxBytes]. */
class UploadTooLargeException(
    val sizeBytes: Long,
    val maxBytes: Long,
) : Exception("Upload too large: $sizeBytes bytes (max $maxBytes)")
