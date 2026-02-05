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
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.setPadding
import org.json.JSONObject
import org.readium.r2.navigator.NavigatorFragment
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Locator
import androidx.fragment.app.commit
import com.example.blitzread.data.local.ReaderProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
//import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.AbsoluteUrl
import android.content.Intent
import android.view.Gravity

import androidx.compose.ui.platform.ComposeView
import com.example.blitzread.MainActivity
import com.example.blitzread.ui.nav.BottomReaderBar
import com.example.blitzread.ui.rsvp.RsvpActivity
import com.example.blitzread.ui.theme.BlitzReadTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.navigator.preferences.Color

class ReadiumViewerActivity : AppCompatActivity() {

    private lateinit var vm: ReadiumViewerViewModel
    private val containerId = 0xBEEF  // any int
    private lateinit var progress: ReaderProgressStore
    private lateinit var docId: String
    private var epubPageCount: Int = 0

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
                        onStartRsvp = { startRsvpFromCurrentLocator(uriString) }
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
                    // next step: attach navigator fragment here
                    val epubFactory = EpubNavigatorFactory(pub)

                    // v1: start at beginning (null locator)
                    val savedLocator: Locator? = withContext(Dispatchers.IO) {
                        progress.loadLocator(docId)
                    }
                    val prefs = EpubPreferences(
                        backgroundColor = Color(0xFF151515.toInt()), // #151515
                        textColor = Color(0xFFFFFFFF.toInt())        // white
                    )

                    if (supportFragmentManager.findFragmentById(containerId) == null) {
                        val fragmentFactory = epubFactory.createFragmentFactory(
                            initialLocator = savedLocator,
                            initialPreferences = prefs,

                        )

                        // IMPORTANT: tell FragmentManager to use this factory
                        supportFragmentManager.fragmentFactory = fragmentFactory

                        // Now create and attach the navigator fragment.
                        // The class you’re creating is EpubNavigatorFragment, but you don’t call its constructor yourself.
                        // You ask FragmentManager to instantiate it using the installed factory.
                        val frag = supportFragmentManager.fragmentFactory.instantiate(
                            classLoader,
                            "org.readium.r2.navigator.epub.EpubNavigatorFragment"
                        )

                        supportFragmentManager.commit {
                            replace(containerId, frag)
                        }
                    }
                    epubPageCount = pub.positions().size
                }

            }
        }
    }

    override fun onPause() {
        super.onPause()

        val frag = supportFragmentManager.findFragmentById(containerId)
                as? EpubNavigatorFragment
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
    private fun startRsvpFromCurrentLocator(uriString: String) {
        val frag = supportFragmentManager.findFragmentById(containerId)
                as? EpubNavigatorFragment
            ?: return

        val locator = frag.currentLocator.value
        val locatorJson = locator.toJSON().toString()

        // Readium locator may include a 1-based position
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

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_DOC_ID = "extra_doc_id"
    }
}
