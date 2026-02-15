package com.example.blitzread.ui.pdf

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.blitzread.data.local.UriCache
import com.example.blitzread.ui.theme.BlitzReadTheme
import java.io.File
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
class PdfViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriString = intent.getStringExtra(EXTRA_URI) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "PDF"
        val docId = intent.getStringExtra(EXTRA_DOC_ID)
            ?: error("Missing docId")

        val file = UriCache.copyToCache(this, Uri.parse(uriString), ".pdf")

        setContent {
            BlitzReadTheme {
                PdfViewer(file = file, title = title,docId = docId, uriString = uriString)
            }
        }
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DOC_ID = "extra_doc_id"

    }
}

@Composable
private fun PdfViewer(file: File, title: String, docId: String, uriString: String) {
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }

    DisposableEffect(file) {
        val desc = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(desc)
        renderer = r
        onDispose {
            r.close()
            desc.close()
        }
    }

    val r = renderer
    if (r == null) {
        Text("Loading…")
        return
    }

    val pageCount = r.pageCount
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            com.example.blitzread.ui.nav.BottomReaderBar(
                onBack = { (context as? Activity)?.finish() },
                onHome = {
                    val i = Intent(context, com.example.blitzread.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(i)
                },
                onSettings = { /* later */ },
                onStartRsvp = {
                    val intent = Intent(context, com.example.blitzread.ui.rsvp.RsvpActivity::class.java).apply {
                        putExtra(com.example.blitzread.ui.rsvp.RsvpActivity.EXTRA_DOC_ID, docId)
                        putExtra(com.example.blitzread.ui.rsvp.RsvpActivity.EXTRA_URI, uriString)
                        putExtra(com.example.blitzread.ui.rsvp.RsvpActivity.EXTRA_PAGE_INDEX, pagerState.currentPage)
                        putExtra(com.example.blitzread.ui.rsvp.RsvpActivity.EXTRA_PAGE_COUNT, pageCount)
                    }
                    Log.d("RSVP", "Sending pageIndex=${pagerState.currentPage}, pageCount=$pageCount, docId=$docId")
                    context.startActivity(intent)
                },
                onSetStartPoint = { /* not supported for PDF yet */ },
                isInSelectionMode = false,
                selectedWordPreview = null
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF151515))
                .padding(padding)
                .padding(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                val bmp = remember(index) { renderPage(r, index) }

                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF page ${index + 1}",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap {
    renderer.openPage(index).use { page ->
        val width = page.width * 2
        val height = page.height * 3
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }
}
private fun renderPageDark(renderer: PdfRenderer, index: Int): Bitmap {
    renderer.openPage(index).use { page ->
        val scale = 2.5f
        val width = (page.width * scale).toInt()
        val height = (page.height * scale).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Fill background dark before rendering (helps with transparent PDFs)
        bitmap.eraseColor(0xFF151515.toInt())

        // Render the page
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        // Invert colors for "dark mode"
        invertBitmapInPlace(bitmap)

        return bitmap
    }
}

private fun invertBitmapInPlace(bmp: Bitmap) {
    val w = bmp.width
    val h = bmp.height
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)

    for (i in pixels.indices) {
        val c = pixels[i]
        val a = (c ushr 24) and 0xFF
        val r = (c ushr 16) and 0xFF
        val g = (c ushr 8) and 0xFF
        val b = c and 0xFF

        // keep alpha, invert RGB
        pixels[i] = (a shl 24) or ((0xFF - r) shl 16) or ((0xFF - g) shl 8) or (0xFF - b)
    }

    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
}
