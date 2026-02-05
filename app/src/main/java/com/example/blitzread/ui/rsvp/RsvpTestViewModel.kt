package com.example.blitzread.ui.rsvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blitzread.domain.model.DocumentContent
import com.example.blitzread.domain.model.Paragraph
import com.example.blitzread.domain.model.Sentence
import com.example.blitzread.domain.model.Token
import com.example.blitzread.engine.rsvp.RsvpPlaybackController
import kotlinx.coroutines.flow.StateFlow

class RsvpTestViewModel : ViewModel() {

    private val controller = RsvpPlaybackController(viewModelScope)

    val currentTokenText: StateFlow<String> = controller.currentTokenText
    val sessionState = controller.sessionState

    init {
        controller.loadContent(sampleContent())
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun wpmUp() = controller.increaseWpm()
    fun wpmDown() = controller.decreaseWpm()

    private fun sampleContent(): DocumentContent {
        // Simple sample: one paragraph, two sentences
        val s1Tokens = listOf("This", "is", "a", "quick", "RSVP", "test.").mapIndexed { i, t ->
            Token(id = "s1_t$i", text = t.trimEnd('.'))
        }
        val s2Tokens = listOf("You", "can", "change", "WPM", "while", "it", "plays.").mapIndexed { i, t ->
            Token(id = "s2_t$i", text = t.trimEnd('.'))
        }

        return DocumentContent(
            documentId = "sample",
            paragraphs = listOf(
                Paragraph(
                    id = "p1",
                    sentences = listOf(
                        Sentence(id = "s1", tokens = s1Tokens),
                        Sentence(id = "s2", tokens = s2Tokens)
                    )
                )
            )
        )
    }
}