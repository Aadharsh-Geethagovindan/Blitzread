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
import com.example.blitzread.data.local.ReaderProgressStore
import kotlinx.coroutines.GlobalScope
import org.readium.r2.shared.publication.Publication
import java.io.File
class RsvpViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = RsvpPlaybackController(viewModelScope)
    private val pdfExtractor = PdfTextExtractor(app.applicationContext)
    private val progress = ReaderProgressStore(app.applicationContext)
    //private val _epubLocation = MutableStateFlow<Pair<Int, Int>?>(null) // (current, total)
    //val epubLocation: StateFlow<Pair<Int, Int>?> = _epubLocation
    val currentTokenText = controller.currentTokenText
    val sessionState = controller.sessionState
    val currentTokenDisplay = controller.currentTokenDisplay
    fun load(docId: String, uriString: String, locatorJson: String?, pageIndex: Int?) {
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



                val opener = ReadiumReaderOpener(app)
                val pubTry = opener.openLocalFile(file)
                val publication = when (pubTry) {
                    is org.readium.r2.shared.util.Try.Success -> pubTry.value
                    is org.readium.r2.shared.util.Try.Failure -> {
                        val content = TextToDocumentContent.fromPlainText(docId, "Failed to open EPUB.")
                        withContext(Dispatchers.Main) {
                            controller.loadContent(content, ReaderLocation(documentId = docId))
                        }
                        return@launch
                    }
                }

                fun normHref(s: String?): String =
                    (s ?: "")
                        .substringBefore('#')
                        .substringBefore('?')
                        .trim()
                        .trimStart('/')

                val locatorHref = normHref(locator?.href?.toString())
                val link: Link? =
                    publication.readingOrder.firstOrNull { normHref(it.href.toString()) == locatorHref }
                        ?: publication.readingOrder.firstOrNull()

                val chapterText = if (link != null) extractLinkText(publication, link) else ""
                val content = TextToDocumentContent.fromPlainText(docId, chapterText.ifBlank { "No text extracted." })

                // Calculate start index from selection or load saved position
                val startIndex = if (locator?.text?.highlight != null) {
                    // User selected a specific word - start there (keep exact position)
                    Log.d("RsvpViewModel", "User selected a word - using calculateStartIndex")
                    calculateStartIndex(locator, content, chapterText)
                } else {
                    // No word selection - check for saved RSVP position
                    val saved = progress.loadRsvpPosition(docId)
                    Log.d("RsvpViewModel", "No word selection. Loaded saved position: $saved for docId: $docId")

                    if (saved != null && saved > 0) {
                        // Intelligent resume: find last sentence boundary (period) before saved position
                        val tokens = content.paragraphs.firstOrNull()?.sentences?.firstOrNull()?.tokens ?: emptyList()

                        // Search backwards from saved position for a word ending with period (max 50 words back)
                        var sentenceStartIndex: Int? = null
                        for (i in (saved - 1) downTo maxOf(0, saved - 50)) {
                            val token = tokens.getOrNull(i)?.text ?: continue
                            // Check if token ends with sentence-ending punctuation
                            if (token.endsWith(".") || token.endsWith("!") || token.endsWith("?")) {
                                sentenceStartIndex = i + 1 // Start at word after the period
                                break
                            }
                        }

                        val resumeIndex = if (sentenceStartIndex != null) {
                            Log.d("RsvpViewModel", "Found sentence start at $sentenceStartIndex (was at $saved)")
                            sentenceStartIndex
                        } else {
                            // No period found nearby - use saved position as-is
                            Log.d("RsvpViewModel", "No sentence boundary found within 50 words, using saved position $saved")
                            saved
                        }

                        resumeIndex.coerceIn(0, tokens.size - 1)
                    } else {
                        // No saved position - use progression from locator (top of current page)
                        val prog = locator?.locations?.progression ?: 0.0
                        val totalTokens = content.paragraphs.firstOrNull()
                            ?.sentences?.firstOrNull()
                            ?.tokens?.size ?: 0
                        val progIndex = (prog * totalTokens).toInt().coerceIn(0, (totalTokens - 1).coerceAtLeast(0))
                        Log.d("RsvpViewModel", "No saved position, using progression: $progIndex")
                        progIndex
                    }
                }

                Log.d("RsvpViewModel", "Starting RSVP at token index: $startIndex")
                //Log.d("RsvpViewModel", "Final startIndex: $startIndex")

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
                    pdfExtractor.extractWholeText(uri)
                }

                val content = TextToDocumentContent.fromPlainText(docId, text)

                // NEW: Load saved position for PDF too
                val savedPosition = progress.loadRsvpPosition(docId) ?: 0

                withContext(Dispatchers.Main) {
                    controller.loadContent(
                        content,
                        ReaderLocation(documentId = docId),
                        startTokenIndex = savedPosition
                    )
                }
                return@launch
            }
        }
    }

    fun saveCurrentPosition(docId: String) {
        val currentState = controller.sessionState.value
        val tokenIndex = currentState.location.tokenIndex
        val currentWord = controller.currentTokenText.value
        Log.d("RsvpViewModel", "saveCurrentPosition called for docId: $docId, tokenIndex: $tokenIndex")

        if (tokenIndex > 0) {
            // Use GlobalScope because viewModelScope gets cancelled on activity exit
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    progress.saveRsvpPosition(docId, tokenIndex, currentWord)
                    Log.d("RsvpViewModel", "Save succeeded: $tokenIndex for doc $docId")

                    // Verify it was saved by reading it back
                    val verify = progress.loadRsvpPosition(docId)
                    Log.d("RsvpViewModel", "Verification read: $verify")
                } catch (e: Exception) {
                    Log.e("RsvpViewModel", "Error saving position", e)
                }
            }
        } else {
            Log.d("RsvpViewModel", "Skipping save - position is at start (0)")
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
