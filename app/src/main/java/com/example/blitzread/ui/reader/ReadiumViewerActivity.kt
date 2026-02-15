package com.example.blitzread.ui.reader

import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Locator
import androidx.fragment.app.commit
import com.example.blitzread.data.local.ReaderProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.view.Gravity
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.example.blitzread.MainActivity
import com.example.blitzread.ui.nav.BottomReaderBar
import com.example.blitzread.ui.rsvp.RsvpActivity
import com.example.blitzread.ui.theme.BlitzReadTheme
import kotlinx.coroutines.delay
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.navigator.preferences.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color as ComposeColor

import androidx.compose.ui.unit.dp

class ReadiumViewerActivity : AppCompatActivity() {

    private lateinit var vm: ReadiumViewerViewModel
    private val containerId = 0xBEEF
    private lateinit var progress: ReaderProgressStore
    private lateinit var docId: String
    private var epubPageCount: Int = 0

    // NEW: Selection mode state
    private var isInSelectionMode = mutableStateOf(false)
    private var selectedLocator: Locator? = null
    private var selectedWordPreview = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progress = ReaderProgressStore(this)

        val uriString = intent.getStringExtra(EXTRA_URI) ?: return finish()
        docId = intent.getStringExtra(EXTRA_DOC_ID) ?: return finish()
        val file = copyUriToCacheFile(Uri.parse(uriString))

        vm = ViewModelProvider(this)[ReadiumViewerViewModel::class.java]
        vm.openFile(file)

        val root = FrameLayout(this)

        val readerHost = FrameLayout(this).apply { id = containerId }
        root.addView(
            readerHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // NEW: Selection mode overlay
        val selectionOverlay = ComposeView(this).apply {
            setContent {
                BlitzReadTheme {
                    SelectionModeOverlay(
                        isVisible = isInSelectionMode.value
                    )
                }
            }
        }
        root.addView(
            selectionOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val composeBar = ComposeView(this).apply {
            setContent {
                BlitzReadTheme {
                    BottomReaderBar(
                        onBack = { finish() },
                        onHome = {
                            val i = Intent(this@ReadiumViewerActivity, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(i)
                        },
                        onSettings = { /* later */ },
                        onStartRsvp = { startRsvpFromLocator(uriString) },
                        onSetStartPoint = { toggleSelectionMode() },
                        isInSelectionMode = isInSelectionMode.value,
                        selectedWordPreview = selectedWordPreview.value
                    )
                }
            }
        }

        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }

        root.addView(composeBar, barParams)
        setContentView(root)

        lifecycleScope.launch {
            vm.publication.collectLatest { pub ->
                if (pub != null) {
                    title = pub.metadata.title ?: "Book"
                    val epubFactory = EpubNavigatorFactory(pub)

                    val savedLocator: Locator? = withContext(Dispatchers.IO) {
                        progress.loadLocator(docId)
                    }
                    val prefs = EpubPreferences(
                        backgroundColor = Color(0xFF151515.toInt()),
                        textColor = Color(0xFFFFFFFF.toInt())
                    )

                    if (supportFragmentManager.findFragmentById(containerId) == null) {
                        val fragmentFactory = epubFactory.createFragmentFactory(
                            initialLocator = savedLocator,
                            initialPreferences = prefs,
                        )

                        supportFragmentManager.fragmentFactory = fragmentFactory

                        val frag = supportFragmentManager.fragmentFactory.instantiate(
                            classLoader,
                            "org.readium.r2.navigator.epub.EpubNavigatorFragment"
                        )

                        supportFragmentManager.commit {
                            replace(containerId, frag)
                        }

                        // NEW: Set up selection listener
                        setupSelectionListener()
                    }
                    epubPageCount = pub.positions().size
                }
            }
        }
    }

    // NEW: Toggle selection mode
    private fun toggleSelectionMode() {
        isInSelectionMode.value = !isInSelectionMode.value

        if (isInSelectionMode.value) {
            selectedLocator = null
            selectedWordPreview.value = null // Clear preview
            Toast.makeText(this, "Long-press any word to set starting point", Toast.LENGTH_SHORT).show()
        } else {
            val frag = supportFragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
            frag?.clearSelection()
        }
    }

    // NEW: Listen for text selection
    private fun setupSelectionListener() {
        lifecycleScope.launch {
            while (true) {
                delay(200)

                if (isInSelectionMode.value) {
                    val frag = supportFragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
                    val selection = frag?.currentSelection()

                    if (selection != null) {
                        selectedLocator = selection.locator

                        // Extract first word from selected text
                        val selectedText = selection.locator.text?.highlight ?: ""
                        val firstWord = selectedText.trim().split(Regex("\\s+")).firstOrNull() ?: selectedText.take(15)
                        selectedWordPreview.value = if (firstWord.length > 15) firstWord.take(12) + "..." else firstWord

                        isInSelectionMode.value = false
                        Toast.makeText(
                            this@ReadiumViewerActivity,
                            "Starting point set!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // MODIFIED: Start RSVP from selected or current locator
    private fun startRsvpFromLocator(uriString: String) {
        val frag = supportFragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
            ?: return

        // Use selected locator if available, otherwise current position
        val locator = selectedLocator ?: frag.currentLocator.value
        selectedLocator = null // Clear after use
        selectedWordPreview.value = null // Clear preview after starting
        val locatorJson = locator.toJSON().toString()
        val pageIndex = (locator.locations.position?.toInt()?.minus(1)) ?: -1

        val intent = Intent(this, RsvpActivity::class.java).apply {
            putExtra(RsvpActivity.EXTRA_DOC_ID, docId)
            putExtra(RsvpActivity.EXTRA_URI, uriString)
            putExtra(RsvpActivity.EXTRA_LOCATOR_JSON, locatorJson)
            putExtra(RsvpActivity.EXTRA_PAGE_INDEX, pageIndex)
            putExtra(RsvpActivity.EXTRA_PAGE_COUNT, epubPageCount)
        }
        startActivity(intent)
    }

    override fun onPause() {
        super.onPause()

        val frag = supportFragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
            ?: return

        val locator = frag.currentLocator.value

        lifecycleScope.launch(Dispatchers.IO) {
            progress.saveLocator(docId, locator)
        }
    }

    private fun copyUriToCacheFile(uri: Uri): File {
        val outFile = File(cacheDir, "book-${System.currentTimeMillis()}.epub")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        return outFile
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_DOC_ID = "extra_doc_id"
    }
}

// NEW: Selection mode overlay composable
@Composable
fun SelectionModeOverlay(isVisible: Boolean) {
    if (isVisible) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Dim background - but DON'T intercept touches
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black.copy(alpha = 0.3f))
            )

            // Instruction card - positioned but doesn't block touches
            Surface(
                modifier = Modifier
                    .padding(top = 60.dp)
                    .padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Long-press any word to set starting point",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
