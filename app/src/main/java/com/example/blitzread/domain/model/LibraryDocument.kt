package com.example.blitzread.domain.model

data class LibraryDocument(
    val id: String,
    val displayName: String,
    val uriString: String,
    val mimeType: String,
    val thumbPath: String? = null
)