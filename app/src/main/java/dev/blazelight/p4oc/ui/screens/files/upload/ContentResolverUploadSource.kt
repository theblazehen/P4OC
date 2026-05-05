package dev.blazelight.p4oc.ui.screens.files.upload

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * [UploadSource] backed by Android's [ContentResolver]. Always reads on
 * [Dispatchers.IO]. Determines MIME via [ContentResolver.getType] with an
 * extension-based fallback through [MimeTypeMap].
 */
class ContentResolverUploadSource(
    private val resolver: ContentResolver,
) : UploadSource {

    override suspend fun probe(sourceId: String): UploadSourceMetadata = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceId)
        var name: String? = null
        var size: Long = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
        }
        val fallbackName = name ?: uri.lastPathSegment?.substringAfterLast('/')
        val mime = resolver.getType(uri) ?: mimeFromName(fallbackName)
        UploadSourceMetadata(displayName = fallbackName, sizeBytes = size, mimeType = mime)
    }

    override suspend fun readBytes(sourceId: String, maxBytes: Long): ByteArray = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceId)
        resolver.openInputStream(uri).use { stream ->
            stream ?: throw IOException("Unable to open $sourceId")
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw UploadTooLargeException(total, maxBytes)
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun mimeFromName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    private companion object {
        const val BUFFER_SIZE = 32 * 1024
    }
}
