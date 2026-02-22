package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// File Types
// ============================================================================

@Serializable
data class FileNodeDto(
    val name: String,
    val path: String,
    val absolute: String,
    val type: String, // "file" | "directory"
    val ignored: Boolean = false
)

@Serializable
data class FileContentDto(
    val type: String, // "text"
    val content: String,
    val diff: String? = null,
    val patch: PatchDto? = null,
    val encoding: String? = null, // "base64"
    val mimeType: String? = null
)

@Serializable
data class PatchDto(
    val oldFileName: String,
    val newFileName: String,
    val oldHeader: String? = null,
    val newHeader: String? = null,
    val hunks: List<HunkDto>,
    val index: String? = null
)

@Serializable
data class HunkDto(
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<String>
)

@Serializable
data class FileStatusDto(
    val path: String,
    val status: String, // "added" | "deleted" | "modified"
    val added: Int = 0,
    val removed: Int = 0
)

@Serializable
data class SearchResultDto(
    val path: String,
    val lines: List<SearchLineDto>? = null,
    @SerialName("line_number") val lineNumber: Int? = null,
    @SerialName("absolute_offset") val absoluteOffset: Int? = null,
    val submatches: List<SubmatchDto>? = null
)

@Serializable
data class SearchLineDto(
    val text: String
)

@Serializable
data class SubmatchDto(
    val match: String,
    val start: Int,
    val end: Int
)

@Serializable
data class SymbolDto(
    val name: String,
    val kind: Int,
    val location: SymbolLocationDto
)

@Serializable
data class SymbolLocationDto(
    val uri: String,
    val range: RangeDto
)

@Serializable
data class RangeDto(
    val start: PositionDto,
    val end: PositionDto
)

@Serializable
data class PositionDto(
    val line: Int,
    val character: Int
)
