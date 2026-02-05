package com.example.blitzread.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.blitzread.data.repo.LibraryRepository

@Composable
fun ReaderScreen(docId: String, vm: ReaderViewModel = viewModel()) {
    val doc = LibraryRepository.getById(docId)

    val token by vm.currentTokenText.collectAsState()
    val state by vm.sessionState.collectAsState()

    LaunchedEffect(docId) {
        if (doc != null) vm.loadFromDoc(docId, doc.uriString)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        Text("Reader (RSVP)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        if (doc == null) {
            Text("Document not found.")
            return
        }

        Text("Reading: ${doc.displayName}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(token.ifBlank { "—" }, style = MaterialTheme.typography.displaySmall)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.wpmDown() }) { Text("- WPM") }
            Button(onClick = { vm.togglePlayPause() }) {
                Text(if (state.isPlaying) "Pause" else "Play")
            }
            Button(onClick = { vm.wpmUp() }) { Text("+ WPM") }
        }
    }
}
