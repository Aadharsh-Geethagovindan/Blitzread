package com.example.blitzread.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

class PdfTextExtractor(
    private val context: Context
) {
    suspend fun extractWholeText(uri: Uri): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open PDF stream")

        input.use {
            val doc = PDDocument.load(it)
            doc.use { pd ->
                val stripper = PDFTextStripper()
                stripper.getText(pd)
            }
        }
    }
    suspend fun extractPageText(uri: Uri, pageIndex0: Int): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open PDF stream")
        input.use {
            PDDocument.load(it).use { pd ->
                val stripper = PDFTextStripper().apply {
                    val page1 = pageIndex0 + 1
                    setStartPage(page1)
                    setEndPage(page1)
                }
                stripper.getText(pd)
            }
        }
    }

}
