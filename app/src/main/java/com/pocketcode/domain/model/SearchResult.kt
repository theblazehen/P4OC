package com.pocketcode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val path: String,
    val lineNumber: Int?,
    val lines: List<SearchLine>,
    val absoluteOffset: Int?,
    val submatches: List<Submatch>?
)

@Serializable
data class SearchLine(
    val text: String
)

@Serializable
data class Submatch(
    val match: String,
    val start: Int,
    val end: Int
)
