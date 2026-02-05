package com.example.blitzread.ui.rsvp

import RsvpViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.blitzread.domain.model.*
import com.example.blitzread.ui.components.BottomBar
import com.example.blitzread.ui.components.BottomBarItem
import android.app.Activity
import android.content.Intent
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.*
import com.example.blitzread.MainActivity

@Composable
fun RsvpScreen(
    docId: String,
    uriString: String,
    locatorJson: String?,
    pageIndex: Int?,
    pageCount: Int?, // add this param
    vm: RsvpViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    LaunchedEffect(docId, uriString, locatorJson, pageIndex) {
        vm.load(docId, uriString, locatorJson, pageIndex)
    }

    val token by vm.currentTokenText.collectAsState()
    val state by vm.sessionState.collectAsState()

    val currentPage = ((pageIndex ?: 0) + 1).coerceAtLeast(1)
    val totalPages = (pageCount ?: 0).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 72.dp)
    ) {
        // Top info
        Text(text = "Current page: $currentPage of $totalPages")

        Spacer(Modifier.height(18.dp))

        // Token area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(.6f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = token.ifBlank { "—" },
                style = MaterialTheme.typography.displayLarge
            )
        }

        // Controls (pulled up)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { vm.wpmDown() }) { Text("-") }
            Button(onClick = { vm.togglePlayPause() }) {
                Text(if (state.isPlaying) "Pause" else "Play")
            }
            Button(onClick = { vm.wpmUp() }) { Text("+") }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Current Wpm: ${state.effectiveWpm}",
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }

    // No BottomBar here. Scaffold in AppNavHost owns it.
    Spacer(Modifier.height(8.dp))
}
@Composable
fun RsvpScaffoldedScreen(
    docId: String,
    uriString: String,
    locatorJson: String?,
    pageIndex: Int?,
    pageCount: Int?,
    vm: RsvpViewModel = viewModel()
) {
    val ctx = LocalContext.current

    val items = listOf(
        BottomBarItem(
            icon = { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) },
            onClick = { (ctx as? Activity)?.finish() }
        ),
        BottomBarItem(
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White) },
            onClick = { /* TODO */ }
        ),
        BottomBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home", tint = Color.White) },
            onClick = {
                val i = Intent(ctx, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                ctx.startActivity(i)
                (ctx as? Activity)?.finish()
            }
        )
    )

    Scaffold(
        bottomBar = { BottomBar(items = items) }
    ) { innerPadding ->
        RsvpScreen(
            docId = docId,
            uriString = uriString,
            locatorJson = locatorJson,
            pageIndex = pageIndex,
            pageCount = pageCount,
            vm = vm,
            modifier = Modifier.padding(innerPadding)
        )
    }
}