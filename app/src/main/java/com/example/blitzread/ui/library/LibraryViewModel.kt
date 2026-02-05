package com.example.blitzread.ui.library

import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import com.example.blitzread.data.local.EpubThumbs
import com.example.blitzread.data.local.PdfThumbs
import com.example.blitzread.domain.model.LibraryDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import com.example.blitzread.data.repo.LibraryRepository
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    val docs = LibraryRepository.docs
    fun addPickedDocument(uri: Uri) {
        val cr = getApplication<Application>().contentResolver

        // Persist permission so we can read later (after app restart)
        try {
            cr.takePersistableUriPermission(
                uri,
                IntentFlags.READ
            )
        } catch (_: SecurityException) {
            // Some providers don't allow persist; v1 will still work during session.
        }

        val name = queryDisplayName(cr, uri) ?: "Imported Document"
        val mime = cr.getType(uri) ?: "application/octet-stream"

        val docId = UUID.randomUUID().toString()

        val thumbPath: String? = runCatching {
            when {
                mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> {
                    PdfThumbs.makeFirstPageThumb(
                        context = getApplication<Application>(),
                        uri = uri,
                        docId = docId
                    )
                }
                mime == "application/epub+zip" || name.endsWith(".epub", ignoreCase = true) -> {
                    EpubThumbs.makeCoverThumb(
                        context = getApplication<Application>(),
                        uri = uri,
                        docId = docId
                    )
                }
                else -> null
            }
        }.getOrNull()

        val doc = LibraryDocument(
            id = docId,
            displayName = name,
            uriString = uri.toString(),
            mimeType = mime,
            thumbPath = thumbPath
        )

        LibraryRepository.add(doc)
    }

    private fun queryDisplayName(cr: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        var cursor: Cursor? = null
        return try {
            cursor = cr.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } finally {
            cursor?.close()
        }
    }

    private object IntentFlags {
        const val READ = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
}
