package com.example.blitzread.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blitzread.engine.readium.ReadiumReaderOpener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import java.io.File

class ReadiumViewerViewModel(app: Application) : AndroidViewModel(app) {

    private val opener = ReadiumReaderOpener(app)

    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication.asStateFlow()

    fun openFile(file: File) {
        viewModelScope.launch {
            val result = opener.openLocalFile(file)
            when (result) {
                is Try.Success -> _publication.value = result.value
                is Try.Failure -> _publication.value = null
            }
        }
    }
}
