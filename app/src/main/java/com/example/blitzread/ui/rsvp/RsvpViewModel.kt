import android.app.Application
import android.net.Uri
import android.util.Log
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


import org.readium.r2.shared.publication.Publication
import java.io.File
class RsvpViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = RsvpPlaybackController(viewModelScope)
    private val pdfExtractor = PdfTextExtractor(app.applicationContext)

    //private val _epubLocation = MutableStateFlow<Pair<Int, Int>?>(null) // (current, total)
    //val epubLocation: StateFlow<Pair<Int, Int>?> = _epubLocation
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


                // DEBUG: Log what's in the locator
                locator?.let {
                    Log.d("RsvpViewModel", "=== LOCATOR DEBUG ===")
                    Log.d("RsvpViewModel", "href: ${it.href}")
                    //Log.d("RsvpViewModel", "type: ${it.type}")
                    Log.d("RsvpViewModel", "title: ${it.title}")
                    Log.d("RsvpViewModel", "locations.position: ${it.locations.position}")
                    Log.d("RsvpViewModel", "locations.progression: ${it.locations.progression}")
                    Log.d("RsvpViewModel", "locations.totalProgression: ${it.locations.totalProgression}")
                    Log.d("RsvpViewModel", "text.before: ${it.text.before}")
                    Log.d("RsvpViewModel", "text.highlight: ${it.text.highlight}")
                    Log.d("RsvpViewModel", "text.after: ${it.text.after}")
                    Log.d("RsvpViewModel", "JSON: $locatorJson")
                    Log.d("RsvpViewModel", "===================")
                }

                // Open publication
                val opener = ReadiumReaderOpener(app)
                val pubTry = opener.openLocalFile(file)
                val publication = when (pubTry) {
                    is Try.Success -> pubTry.value
                    is Try.Failure -> {
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

                // NEW: Calculate start index based on selected text or progression
                val startIndex = calculateStartIndex(locator, content, chapterText)

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
    private fun calculateStartIndex(
        locator: Locator?,
        content: com.example.blitzread.domain.model.DocumentContent,
        chapterText: String
    ): Int {
        if (locator == null) return 0

        val selectedText = locator.text.highlight?.trim()
        val beforeContext = locator.text.before?.trim()
        val afterContext = locator.text.after?.trim()

        Log.d("RsvpViewModel", "calculateStartIndex - selectedText: '$selectedText'")

        if (!selectedText.isNullOrBlank()) {
            val tokens = content.paragraphs.firstOrNull()?.sentences?.firstOrNull()?.tokens ?: emptyList()

            if (!beforeContext.isNullOrBlank() && !afterContext.isNullOrBlank()) {
                // Helper function to normalize words (remove punctuation, lowercase)
                fun normalize(word: String): String {
                    return word.lowercase().replace(Regex("[^a-z0-9]"), "")
                }

                // Extract and normalize words
                val beforeWords = beforeContext.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .map { normalize(it) }
                    .filter { it.isNotEmpty() } // Remove pure punctuation tokens

                val afterWords = afterContext.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .map { normalize(it) }
                    .filter { it.isNotEmpty() }

                // Take last 5 words before and first 5 words after
                val contextBefore = beforeWords.takeLast(5)
                val contextAfter = afterWords.take(5)

                Log.d("RsvpViewModel", "contextBefore (normalized): $contextBefore")
                Log.d("RsvpViewModel", "contextAfter (normalized): $contextAfter")

                val selectedNorm = normalize(selectedText)

                // Search through tokens for best match
                var bestMatchIndex = -1
                var bestMatchScore = 0

                for (i in 0 until tokens.size) {
                    if (normalize(tokens[i].text) != selectedNorm) continue

                    // Calculate match score for this position
                    var score = 0

                    // Check words before (up to 5)
                    for (j in contextBefore.indices) {
                        val tokenIndex = i - contextBefore.size + j
                        if (tokenIndex >= 0 && tokenIndex < tokens.size) {
                            if (normalize(tokens[tokenIndex].text) == contextBefore[j]) {
                                score++
                            }
                        }
                    }

                    // Check words after (up to 5)
                    for (j in contextAfter.indices) {
                        val tokenIndex = i + 1 + j
                        if (tokenIndex < tokens.size) {
                            if (normalize(tokens[tokenIndex].text) == contextAfter[j]) {
                                score++
                            }
                        }
                    }

                    Log.d("RsvpViewModel", "Token $i: '${tokens[i].text}' scored $score/${contextBefore.size + contextAfter.size}")

                    if (score > bestMatchScore) {
                        bestMatchScore = score
                        bestMatchIndex = i

                        //Early exit on perfect match
                        val totalContext = contextBefore.size + contextAfter.size
                        if (score == totalContext) {
                            Log.d("RsvpViewModel", "Perfect match found at token $i - stopping search")
                            break
                        }

                    }
                }

                // Use best match if we got at least 50% match
                val totalContext = contextBefore.size + contextAfter.size
                val matchPercentage = if (totalContext > 0) (bestMatchScore * 100) / totalContext else 0

                Log.d("RsvpViewModel", "Best match: index $bestMatchIndex with $matchPercentage% ($bestMatchScore/$totalContext)")

                if (matchPercentage >= 50 && bestMatchIndex >= 0) {
                    Log.d("RsvpViewModel", "SUCCESS: Using best match at token index $bestMatchIndex")
                    return bestMatchIndex
                }

                Log.d("RsvpViewModel", "FAILED: No match above 50% threshold")
            }

            // Fallback: Find first occurrence
            val selectedWords = selectedText.split(Regex("\\s+"))
            val firstWord = selectedWords.firstOrNull()?.lowercase() ?: return 0

            val matchIndex = tokens.indexOfFirst { token ->
                token.text.lowercase() == firstWord
            }

            if (matchIndex >= 0) {
                Log.d("RsvpViewModel", "Fallback: using first occurrence at token index $matchIndex")
                return matchIndex
            }
        }

        // Fallback to progression-based
        val prog = locator.locations.progression ?: 0.0
        val totalTokens = content.paragraphs.firstOrNull()
            ?.sentences?.firstOrNull()
            ?.tokens?.size ?: 0

        Log.d("RsvpViewModel", "Fallback: using progression $prog")
        return (prog * totalTokens).toInt().coerceIn(0, (totalTokens - 1).coerceAtLeast(0))
    }
    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")

    }
    fun togglePlayPause() = controller.togglePlayPause()
    fun wpmUp() = controller.increaseWpm()
    fun wpmDown() = controller.decreaseWpm()
}
