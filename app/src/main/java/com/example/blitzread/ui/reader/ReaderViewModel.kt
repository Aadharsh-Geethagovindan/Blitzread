package com.example.blitzread.ui.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blitzread.data.local.PdfTextExtractor
import com.example.blitzread.domain.model.*
import com.example.blitzread.domain.text.TextToDocumentContent
import com.example.blitzread.engine.rsvp.RsvpPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = RsvpPlaybackController(viewModelScope)
    private val pdfExtractor = PdfTextExtractor(app.applicationContext)

    val currentTokenText: StateFlow<String> = controller.currentTokenText
    val sessionState = controller.sessionState

    fun togglePlayPause() = controller.togglePlayPause()
    fun wpmUp() = controller.increaseWpm()
    fun wpmDown() = controller.decreaseWpm()

    fun loadFromDoc(docId: String, uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            val mime = getApplication<Application>().contentResolver.getType(uri).orEmpty()

            val text = if (mime == "application/pdf" || uriString.endsWith(".pdf", true)) {
                pdfExtractor.extractWholeText(uri)
            } else {
                // v1: EPUB extraction not wired in this path yet
                "EPUB text loading not wired yet."
            }

            val content = TextToDocumentContent.fromPlainText(docId, text)

            withContext(Dispatchers.Main) {
                controller.loadContent(content, ReaderLocation(documentId = docId))
            }
        }
    }
}
