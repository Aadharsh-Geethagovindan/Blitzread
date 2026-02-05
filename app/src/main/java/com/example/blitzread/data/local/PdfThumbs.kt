package com.example.blitzread.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

object PdfThumbs {

    fun makeFirstPageThumb(context: Context, uri: Uri, docId: String): String? {
        val cr = context.contentResolver
        val pfd = cr.openFileDescriptor(uri, "r") ?: return null

        pfd.use { desc ->
            PdfRenderer(desc).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val width = (page.width * 2).coerceAtLeast(1)
                    val height = (page.height * 2).coerceAtLeast(1)

                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
                    val outFile = File(dir, "$docId.png")

                    FileOutputStream(outFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    return outFile.absolutePath
                }
            }
        }
    }
}
