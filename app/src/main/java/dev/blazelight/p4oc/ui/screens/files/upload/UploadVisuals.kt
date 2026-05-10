package dev.blazelight.p4oc.ui.screens.files.upload

import java.util.Locale

/**
 * Pure helpers for upload UI presentation. Kept JVM-only so they can be
 * unit-tested without Android instrumentation.
 *
 * Compose visual helpers belong in UploadProgressSheet.kt to keep this file
 * under 100 LOC and easy to test.
 */

/** Format a byte size into a short human-readable string (B/KB/MB/GB). */
fun formatFileSize(bytes: Long): String = when {
    bytes < 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L ->
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else ->
        String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

/** Single-glyph TUI symbol for a filename, by extension. */
fun getFileSymbol(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
    return when (ext) {
        "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "bmp" -> "▣"
        "mp4", "avi", "mov", "mkv", "webm" -> "▶"
        "mp3", "wav", "ogg", "flac", "m4a" -> "♪"
        "zip", "tar", "gz", "rar", "7z" -> "▤"
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h",
        "rs", "go", "rb", "php", "swift", "m", "sh", "bash" -> "{}"
        "md", "txt", "rst", "log" -> "≡"
        "json", "yaml", "yml", "xml", "toml", "ini", "conf", "config" -> "⚙"
        "pdf", "doc", "docx" -> "▭"
        else -> "◆"
    }
}

/** Short uppercase label for a MIME type (e.g. "image/png" -> "PNG"). */
fun getMimeTypeLabel(mimeType: String?): String {
    if (mimeType.isNullOrBlank()) return "BIN"
    val sub = mimeType.substringAfter('/', mimeType).substringBefore(';').trim()
    if (sub.isBlank()) return "BIN"
    val cleaned = sub.removePrefix("x-").removePrefix("vnd.")
    val token = cleaned.substringBefore('+')
    return token.take(MIME_LABEL_MAX).uppercase(Locale.US)
}

/**
 * Sanitize a SAF display name into a safe filename:
 * - strip path separators and control characters
 * - drop leading dots (no hidden files via picker)
 * - trim and collapse whitespace
 * - fall back to a timestamped placeholder when empty.
 */
fun sanitizeUploadName(rawName: String?, fallbackTimestamp: Long): String {
    val cleaned = (rawName ?: "")
        .replace('\\', '_')
        .replace('/', '_')
        .replace('\u0000', '_')
        .filter { it.code >= 0x20 }
        .trim()
        .trimStart('.')
    if (cleaned.isBlank()) return "upload-$fallbackTimestamp.bin"
    val withoutTransientSuffix = cleaned.removeSuffix(".part").ifBlank { cleaned }
    return withoutTransientSuffix.take(MAX_NAME_LEN)
}

/** Join the explorer's current path with a sanitized name. */
fun joinDestinationPath(currentPath: String?, sanitizedName: String): String {
    val base = currentPath.orEmpty().trim().trim('/')
    return if (base.isEmpty()) sanitizedName else "$base/$sanitizedName"
}

private const val MIME_LABEL_MAX = 6
private const val MAX_NAME_LEN = 200
