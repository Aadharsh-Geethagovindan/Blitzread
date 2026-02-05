package com.example.blitzread.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.blitzread.domain.model.LibraryDocument
import com.example.blitzread.ui.reader.ReadiumViewerActivity

private val Bg = Color(0xFF151515)
private val BorderGreen = Color(0xFF006715)
private val ButtonGreen = Color(0xFF016347)
private val CardShape = RoundedCornerShape(14.dp)

@Composable
fun LibraryScreen(
    onOpenDocument: (String) -> Unit,
    vm: LibraryViewModel = viewModel()
) {
    val docs by vm.docs.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.addPickedDocument(uri)
    }

    // v1: “resume reading” = last doc in list (swap later to true persistence)
    val resumeDoc = docs.lastOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        Text(
            "Resume Reading",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(Modifier.height(10.dp))

        ResumeCard(
            doc = resumeDoc,
            onClick = {
                if (resumeDoc != null) openDoc(context, resumeDoc, onOpenDocument)
            }
        )

        Spacer(Modifier.height(18.dp))

        Text(
            "My Library",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(docs) { doc ->
                LibraryTile(
                    doc = doc,
                    onClick = { openDoc(context, doc, onOpenDocument) }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { picker.launch(arrayOf("application/pdf", "application/epub+zip")) },
            colors = ButtonDefaults.buttonColors(containerColor = ButtonGreen),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Import PDF/EPUB", color = Color.White)
        }
    }
}

@Composable
private fun ResumeCard(
    doc: LibraryDocument?,
    onClick: () -> Unit
) {
    val h = 260.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(h)
            .border(3.dp, BorderGreen, CardShape)
            .clickable(enabled = doc != null) { onClick() }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (doc == null) {
            Text("No documents yet", color = Color.White)
            return
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Thumb(
                thumbPath = doc.thumbPath,
                modifier = Modifier
                    .width(160.dp)
                    .height(210.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                doc.displayName,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryTile(
    doc: LibraryDocument,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .border(3.dp, BorderGreen, CardShape)
                .padding(10.dp)
        ) {
            Thumb(
                thumbPath = doc.thumbPath,
                modifier = Modifier
                    .width(88.dp)
                    .height(120.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = doc.displayName.removeSuffix(".pdf").removeSuffix(".epub"),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Thumb(thumbPath: String?, modifier: Modifier) {
    if (thumbPath.isNullOrBlank()) {
        // placeholder
        Box(
            modifier = modifier.background(Color(0xFF222222), CardShape),
            contentAlignment = Alignment.Center
        ) {
            Text("No\nCover", color = Color.White)
        }
    } else {
        AsyncImage(
            model = thumbPath, // file path string is fine for Coil
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

private fun openDoc(
    context: android.content.Context,
    doc: LibraryDocument,
    onOpenDocument: (String) -> Unit
) {

        onOpenDocument(doc.id)

}