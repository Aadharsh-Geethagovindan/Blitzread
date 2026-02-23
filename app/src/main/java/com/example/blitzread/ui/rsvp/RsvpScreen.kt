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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString

@Composable
fun RsvpScreen(
    docId: String,
    uriString: String,
    locatorJson: String?,
    pageIndex: Int?,
    pageCount: Int?,
    vm: RsvpViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    LaunchedEffect(docId, uriString, locatorJson, pageIndex) {
        vm.load(docId, uriString, locatorJson, pageIndex)
    }

    val tokenDisplay by vm.currentTokenDisplay.collectAsState() // Changed from currentTokenText
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
        Text(text = "Current page: $currentPage of $totalPages")
        Spacer(Modifier.height(18.dp))

        // Token area with ORP alignment and highlighting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(.6f),
            contentAlignment = Alignment.Center
        ) {
            OrpWordDisplay(word = tokenDisplay.fullText, orpIndex = tokenDisplay.orpIndex)
        }

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
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    Spacer(Modifier.height(8.dp))
}

@Composable
fun OrpWordDisplay(
    word: String,
    orpIndex: Int,
    modifier: Modifier = Modifier
) {
    if (word.isBlank()) {
        Text(
            text = "—",
            style = MaterialTheme.typography.displayLarge,
            modifier = modifier
        )
        return
    }

    val beforeOrp = word.take(orpIndex)
    val orpChar = word.getOrNull(orpIndex)?.toString() ?: ""
    val afterOrp = word.drop(orpIndex + 1)

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.displayLarge

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val screenWidthPx = with(density) { this@BoxWithConstraints.maxWidth.roundToPx() }
        val fixationX = (screenWidthPx * 0.4f).toInt()

        // Pre-measure all text parts
        val beforeLayout = textMeasurer.measure(
            text = AnnotatedString(beforeOrp),
            style = textStyle
        )
        val orpLayout = textMeasurer.measure(
            text = AnnotatedString(orpChar),
            style = textStyle.copy(fontWeight = FontWeight.Bold)
        )
        val afterLayout = textMeasurer.measure(
            text = AnnotatedString(afterOrp),
            style = textStyle
        )

        val beforeWidth = beforeLayout.size.width
        val totalWidth = beforeWidth + orpLayout.size.width + afterLayout.size.width

        // Calculate scale if needed (minimum 0.5 scale to prevent too much shrinking)
        val scale = if (totalWidth > screenWidthPx - 40) {
            maxOf(0.5f, (screenWidthPx - 40f) / totalWidth)
        } else {
            1f
        }

        // Calculate position with scale applied
        val scaledBeforeWidth = (beforeWidth * scale).toInt()
        val targetX = fixationX - scaledBeforeWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.TopStart)
        ) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .offset { IntOffset(targetX, 0) }
                    .wrapContentWidth(unbounded = true) // NEW: Allow content to extend beyond bounds
            ) {
                Text(
                    text = beforeOrp,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1, // NEW: Force single line
                    softWrap = false // NEW: No wrapping
                )

                Text(
                    text = orpChar,
                    style = textStyle,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )

                Text(
                    text = afterOrp,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
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