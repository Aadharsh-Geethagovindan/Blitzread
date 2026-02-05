package com.example.blitzread.data.repo

import com.example.blitzread.domain.model.LibraryDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LibraryRepository {
    private val _docs = MutableStateFlow<List<LibraryDocument>>(emptyList())
    val docs: StateFlow<List<LibraryDocument>> = _docs.asStateFlow()

    fun add(doc: LibraryDocument) {
        _docs.value = _docs.value + doc
    }
    fun remove(id: String) {
        _docs.value = _docs.value.filterNot { it.id == id }
    }
    fun getById(id: String): LibraryDocument? {
        return _docs.value.firstOrNull { it.id == id }
    }
}
