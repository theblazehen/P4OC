package com.pocketcode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val file: String,
    val line: Int,
    val column: Int,
    val match: String,
    val context: String
)
