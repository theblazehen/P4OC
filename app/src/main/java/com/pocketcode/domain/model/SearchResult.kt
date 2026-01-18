package com.pocketcode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val path: String,
    val lineNumber: Int?,
    val lines: String?,
    val absoluteOffset: Int?,
    val submatches: List<Submatch>?
)

@Serializable
data class Submatch(
    val match: String,
    val start: Int,
    val end: Int
)
