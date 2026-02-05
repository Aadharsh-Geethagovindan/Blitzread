package com.example.blitzread.domain.model

data class Token(
    val id: String,
    val text: String
)

data class Sentence(
    val id: String,
    val tokens: List<Token>
)

data class Paragraph(
    val id: String,
    val sentences: List<Sentence>
)

data class DocumentContent(
    val documentId: String,
    val paragraphs: List<Paragraph>
)