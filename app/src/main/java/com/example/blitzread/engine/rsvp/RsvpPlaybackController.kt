
package com.example.blitzread.engine.rsvp

import com.example.blitzread.domain.model.DocumentContent
import com.example.blitzread.domain.model.ReaderLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RsvpPlaybackController(
    private val scope: CoroutineScope,
    initialSettings: RsvpSettings = RsvpSettings()
) {
    private var job: Job? = null

    private var content: DocumentContent? = null
    private var settings: RsvpSettings = initialSettings
    private var location: ReaderLocation = ReaderLocation(documentId = "")

    private val _currentTokenDisplay = MutableStateFlow(TokenDisplay("", 0))
    val currentTokenDisplay: StateFlow<TokenDisplay> = _currentTokenDisplay.asStateFlow()

    private val _currentTokenText = MutableStateFlow("")
    val currentTokenText: StateFlow<String> = _currentTokenText.asStateFlow()
    private val _sessionState = MutableStateFlow(
        RsvpSessionState(location = location, effectiveWpm = settings.wpm, isPlaying = false)
    )
    val sessionState: StateFlow<RsvpSessionState> = _sessionState.asStateFlow()

    fun loadContent(document: DocumentContent,
                    startLocation: ReaderLocation = ReaderLocation(documentId = document.documentId),
                    startTokenIndex: Int = 0) {
        content = document
        location = location.copy(tokenIndex = startTokenIndex.coerceAtLeast(0))
        emitCurrentToken()
        publishState(isPlaying = false)
    }

    fun play() {
        if (job?.isActive == true) return
        if (content == null) return

        publishState(isPlaying = true)

        job = scope.launch {
            while (isActive) {
                emitCurrentToken()
                publishState(isPlaying = true) // Publish state AFTER emitting token (so tokenIndex matches displayed word)

                // Delay BEFORE advancing (so saved position matches displayed word)
                delay(msPerToken(settings.wpm))

                // Advance location by 1 token
                if (!advanceOneToken()) {
                    // End of content -> stop
                    pause()
                    return@launch
                }
            }
        }
    }

    fun pause() {
        job?.cancel()
        job = null
        publishState(isPlaying = false)
    }

    fun togglePlayPause() {
        if (_sessionState.value.isPlaying) pause() else play()
    }

    fun setWpm(newWpm: Int) {
        val clamped = newWpm.coerceIn(60, 1200)
        settings = settings.copy(wpm = clamped)
        publishState(isPlaying = _sessionState.value.isPlaying)
    }

    fun increaseWpm(step: Int = 25) = setWpm(settings.wpm + step)
    fun decreaseWpm(step: Int = 25) = setWpm(settings.wpm - step)

    fun seek(newLocation: ReaderLocation) {
        location = newLocation.copy(documentId = location.documentId)
        emitCurrentToken()
        publishState(isPlaying = _sessionState.value.isPlaying)
    }

    private fun publishState(isPlaying: Boolean) {
        _sessionState.value = RsvpSessionState(
            location = location,
            effectiveWpm = settings.wpm,
            isPlaying = isPlaying
        )
    }

    private fun msPerToken(wpm: Int): Long {
        // 60,000 ms per minute / words per minute.
        // v1 treats 1 token ~= 1 word for timing.
        return (60_000.0 / wpm.toDouble()).toLong().coerceAtLeast(10L)
    }

    private fun emitCurrentToken() {
        val doc = content ?: return
        val token = tokenAt(doc, location) ?: ""
        val orpIndex = calculateOrpIndex(token)
        _currentTokenDisplay.value = TokenDisplay(token, orpIndex)
        _currentTokenText.value = token
    }
    private fun calculateOrpIndex(word: String): Int {
        if (!settings.orpEnabled) {
            return word.length / 2 // Center alignment if ORP disabled
        }

        val cleanLength = word.trim().length
        return when (cleanLength) {
            0, 1 -> 0
            in 2..5 -> 1
            in 6..9 -> 2
            in 10..13 -> 3
            else -> 4
        }.coerceAtMost(cleanLength - 1) // Don't exceed word length
    }
    private fun advanceOneToken(): Boolean {
        val doc = content ?: return false

        val (pIndex, sIndex, tIndex) = resolveIndexes(doc, location) ?: return false

        val sentence = doc.paragraphs[pIndex].sentences[sIndex]
        val nextT = tIndex + 1
        if (nextT < sentence.tokens.size) {
            location = location.copy(
                paragraphId = doc.paragraphs[pIndex].id,
                sentenceId = sentence.id,
                tokenIndex = nextT
            )
            publishState(isPlaying = _sessionState.value.isPlaying)
            return true
        }

        // Next sentence
        val nextS = sIndex + 1
        if (nextS < doc.paragraphs[pIndex].sentences.size) {
            val nextSentence = doc.paragraphs[pIndex].sentences[nextS]
            location = location.copy(
                paragraphId = doc.paragraphs[pIndex].id,
                sentenceId = nextSentence.id,
                tokenIndex = 0
            )
            publishState(isPlaying = _sessionState.value.isPlaying)
            return true
        }

        // Next paragraph
        val nextP = pIndex + 1
        if (nextP < doc.paragraphs.size) {
            val nextParagraph = doc.paragraphs[nextP]
            val firstSentence = nextParagraph.sentences.firstOrNull() ?: return false
            location = location.copy(
                paragraphId = nextParagraph.id,
                sentenceId = firstSentence.id,
                tokenIndex = 0
            )
            publishState(isPlaying = _sessionState.value.isPlaying)
            return true
        }

        return false
    }

    private fun tokenAt(doc: DocumentContent, loc: ReaderLocation): String? {
        val (pIndex, sIndex, tIndex) = resolveIndexes(doc, loc) ?: return null
        val tokens = doc.paragraphs[pIndex].sentences[sIndex].tokens
        return tokens.getOrNull(tIndex)?.text
    }

    private fun resolveIndexes(doc: DocumentContent, loc: ReaderLocation): Triple<Int, Int, Int>? {
        val pIndex = loc.paragraphId?.let { pid -> doc.paragraphs.indexOfFirst { it.id == pid } } ?: 0
        if (pIndex !in doc.paragraphs.indices) return null

        val paragraph = doc.paragraphs[pIndex]
        val sIndex = loc.sentenceId?.let { sid -> paragraph.sentences.indexOfFirst { it.id == sid } } ?: 0
        if (sIndex !in paragraph.sentences.indices) return null

        val tIndex = loc.tokenIndex
        if (tIndex < 0) return null

        return Triple(pIndex, sIndex, tIndex)
    }
}

data class TokenDisplay(
    val fullText: String,
    val orpIndex: Int // 0-based index of the ORP character
)