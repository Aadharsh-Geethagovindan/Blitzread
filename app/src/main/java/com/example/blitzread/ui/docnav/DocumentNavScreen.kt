package com.example.blitzread.ui.docnav

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.example.blitzread.data.repo.LibraryRepository
import com.example.blitzread.ui.pdf.PdfViewerActivity
import com.example.blitzread.ui.reader.ReadiumViewerActivity
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
@Composable
fun DocumentNavScreen(
    docId: String,
    onStartReading: () -> Unit,
    onDelete: () -> Unit
) {
    val doc = LibraryRepository.getById(docId)
    val ctx = LocalContext.current

    val bg = Color(0xFF151515)
    val greenBorder = Color(0xFF006715)
    val greenBtn = Color(0xFF016347)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        if (doc == null) {
            Text("Document not found for id=$docId", color = Color.White)
            return
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top info
            Text("Title: ${doc.displayName}", color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text("Type: ${doc.mimeType}", color = Color.White)

            Spacer(Modifier.height(24.dp))

            // Cover centered
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .border(10.dp, greenBorder)
                        .padding(10.dp)
                ) {
                    // Use whatever you already use in Library for thumbnails.
                    // For now: if doc.thumbPath exists, load it; otherwise show placeholder.
                    val thumbPath = doc.thumbPath // if you added this; if not, replace with null
                    if (!thumbPath.isNullOrBlank()) {
                        Image(
                            bitmap = BitmapFactory.decodeFile(thumbPath).asImageBitmap(),
                            contentDescription = "Cover",
                            modifier = Modifier
                                .width(200.dp)
                                .height(280.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .height(280.dp)
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No cover", color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        when (doc.mimeType) {
                            "application/epub+zip" -> {
                                val i = Intent(ctx, ReadiumViewerActivity::class.java).apply {
                                    putExtra(ReadiumViewerActivity.EXTRA_URI, doc.uriString)
                                    putExtra(ReadiumViewerActivity.EXTRA_DOC_ID, doc.id)
                                }
                                ctx.startActivity(i)
                            }
                            "application/pdf" -> {
                                val i = Intent(ctx, PdfViewerActivity::class.java).apply {
                                    putExtra(PdfViewerActivity.EXTRA_URI, doc.uriString)
                                    putExtra(PdfViewerActivity.EXTRA_TITLE, doc.displayName)
                                    putExtra(PdfViewerActivity.EXTRA_DOC_ID, docId)
                                }
                                ctx.startActivity(i)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = greenBtn),
                    modifier = Modifier
                        .width(220.dp)
                        .height(52.dp)
                ) {
                    Text("Read", color = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Delete centered (placeholder action)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    onClick = onDelete
,
                    colors = ButtonDefaults.buttonColors(containerColor = greenBtn),
                    modifier = Modifier
                        .width(220.dp)
                        .height(48.dp)
                ) {
                    Text("Delete From Library", color = Color.White)
                }
            }
        }
    }
}

