package com.example.blitzread.domain.model

data class ReaderLocation(
    val documentId: String,
    val paragraphId: String? = null,
    val sentenceId: String? = null,
    val tokenIndex: Int = 0,
    val fallbackRef: String? = null
)