package com.example.blitzread.ui.rsvp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RsvpTestScreen(vm: RsvpTestViewModel = viewModel()) {
    val token by vm.currentTokenText.collectAsState()
    val state by vm.sessionState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = token.ifBlank { "—" }, fontSize = 48.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "WPM: ${state.effectiveWpm}")
        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = { vm.togglePlayPause() }) {
            Text(if (state.isPlaying) "Pause" else "Play")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { vm.wpmDown() }) { Text("WPM -") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { vm.wpmUp() }) { Text("WPM +") }
        }
    }
}


