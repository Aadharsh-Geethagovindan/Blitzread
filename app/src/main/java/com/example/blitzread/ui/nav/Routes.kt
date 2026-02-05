package com.example.blitzread.ui.nav

sealed class Route(val path: String) {
    data object Library : Route("library")
    data object DocumentNav : Route("docNav/{docId}") {
        fun create(docId: String) = "docNav/$docId"
    }
    data object Reader : Route("reader/{docId}") {
        fun create(docId: String) = "reader/$docId"
    }
}