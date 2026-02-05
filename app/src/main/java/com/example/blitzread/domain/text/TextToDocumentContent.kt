package com.example.blitzread.domain.text

import com.example.blitzread.domain.model.*

object TextToDocumentContent {

    fun fromPlainText(
        docId: String,
        text: String
    ): DocumentContent {
        // v1 simple: one paragraph, one sentence, tokens split by whitespace.
        // Later we can add real sentence/paragraph detection.
        val tokens = text
            .replace("\r\n", "\n")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .mapIndexed { idx, raw ->
                Token(id = "t$idx", text = raw)
            }

        val sentence = Sentence(id = "s1", tokens = tokens)
        val paragraph = Paragraph(id = "p1", sentences = listOf(sentence))

        return DocumentContent(
            documentId = docId,
            paragraphs = listOf(paragraph)
        )
    }
}
