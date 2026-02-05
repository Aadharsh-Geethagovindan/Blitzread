import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blitzread.data.local.PdfTextExtractor
import com.example.blitzread.domain.model.ReaderLocation
import com.example.blitzread.domain.text.TextToDocumentContent
import com.example.blitzread.engine.rsvp.RsvpPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Try
import com.example.blitzread.data.local.UriCache
import com.example.blitzread.engine.readium.ReadiumReaderOpener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.shared.publication.Publication
import java.io.File
class RsvpViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = RsvpPlaybackController(viewModelScope)
    private val pdfExtractor = PdfTextExtractor(app.applicationContext)

    private val _epubLocation = MutableStateFlow<Pair<Int, Int>?>(null) // (current, total)
    val epubLocation: StateFlow<Pair<Int, Int>?> = _epubLocation
    val currentTokenText = controller.currentTokenText
    val sessionState = controller.sessionState

    fun load(docId: String, uriString: String, locatorJson: String?, pageIndex: Int?) {
        // v1
        viewModelScope.launch(Dispatchers.IO) {
            val uri = Uri.parse(uriString)

            val mime = getApplication<Application>()
                .contentResolver
                .getType(uri)
                .orEmpty()

            if (mime == "application/epub+zip" || uriString.endsWith(".epub", ignoreCase = true)) {
                val app = getApplication<Application>()
                val file: File = UriCache.copyToCache(app, Uri.parse(uriString), ".epub")

                val locator = locatorJson
                    ?.let { Locator.fromJSON(JSONObject(it)) }
                    ?: null

                // Open publication
                val opener = ReadiumReaderOpener(app)
                val pubTry = opener.openLocalFile(file)
                val publication = when (pubTry) {
                    is org.readium.r2.shared.util.Try.Success -> pubTry.value
                    is org.readium.r2.shared.util.Try.Failure -> {
                        val content = TextToDocumentContent.fromPlainText(docId, "Failed to open EPUB.")
                        withContext(Dispatchers.Main) { controller.loadContent(content, ReaderLocation(documentId = docId)) }
                        return@launch
                    }
                }

                fun normHref(s: String?): String =
                    (s ?: "")
                        .substringBefore('#')
                        .substringBefore('?')
                        .trim()
                        .trimStart('/')

// Find the spine item (chapter) matching the locator href (tolerant match)
                val locatorHref = normHref(locator?.href?.toString())

                val link: Link? =
                    publication.readingOrder.firstOrNull { normHref(it.href.toString()) == locatorHref }
                        ?: publication.readingOrder.firstOrNull()

                val chapterText = if (link != null) extractLinkText(publication, link) else ""

                val content = TextToDocumentContent.fromPlainText(docId, chapterText.ifBlank { "No text extracted." })

                val prog = locator?.locations?.progression ?: 0.0
                val totalTokens = content.paragraphs.firstOrNull()
                    ?.sentences?.firstOrNull()
                    ?.tokens?.size ?: 0

                val startIndex =
                    (prog * totalTokens).toInt().coerceIn(0, (totalTokens - 1).coerceAtLeast(0))

                withContext(Dispatchers.Main) {
                    controller.loadContent(
                        content,
                        ReaderLocation(documentId = docId),
                        startTokenIndex = startIndex
                    )
                }

                return@launch
            }

            if (mime == "application/pdf" || uriString.endsWith(".pdf", true)) {
                val text = if (pageIndex != null) {
                    pdfExtractor.extractPageText(uri, pageIndex)
                } else {
                    pdfExtractor.extractWholeText(uri) // fallback
                }

                val content = TextToDocumentContent.fromPlainText(docId, text)

                withContext(Dispatchers.Main) {
                    controller.loadContent(
                        content,
                        ReaderLocation(documentId = docId),
                        startTokenIndex = 0 // page text starts at top
                    )
                }
                return@launch
            }
        }
    }
    private suspend fun extractLinkText(publication: Publication, link: Link): String {
        val res = publication.get(link) ?: return ""

        val bytesTry = res.read()

        val bytes = when (bytesTry) {
            is Try.Success -> bytesTry.value
            is Try.Failure -> return ""
        }

        val html = bytes.toString(Charsets.UTF_8)
        return stripHtml(html)
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")

    }
    fun togglePlayPause() = controller.togglePlayPause()
    fun wpmUp() = controller.increaseWpm()
    fun wpmDown() = controller.decreaseWpm()
}
