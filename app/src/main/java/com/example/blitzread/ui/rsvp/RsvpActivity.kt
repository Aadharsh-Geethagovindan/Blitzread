package com.example.blitzread.ui.rsvp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.blitzread.ui.theme.BlitzReadTheme

class RsvpActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val docId = intent.getStringExtra(EXTRA_DOC_ID) ?: ""
        val uriString = intent.getStringExtra(EXTRA_URI) ?: ""
        val locatorJson = intent.getStringExtra(EXTRA_LOCATOR_JSON)
        val pageIndex = intent.getIntExtra(EXTRA_PAGE_INDEX, -1)
        val pageCount = intent.getIntExtra(EXTRA_PAGE_COUNT, 0).takeIf { it > 0 }
        Log.d("RSVP", "Received pageIndex=$pageIndex rawPageCount=${intent.getIntExtra(EXTRA_PAGE_COUNT, -999)}")
        Log.d("RSVP", "Keys=${intent.extras?.keySet()?.joinToString()}")
        setContent {
            BlitzReadTheme(darkTheme = true, dynamicColor = false) {

                    RsvpScaffoldedScreen(
                        docId = docId,
                        uriString = uriString,
                        locatorJson = locatorJson,
                        pageIndex = pageIndex.takeIf { it >= 0 },
                        pageCount = intent.getIntExtra(EXTRA_PAGE_COUNT, 0).takeIf { it > 0 }
                    )

            }
        }
    }

    companion object {
        const val EXTRA_DOC_ID = "extra_doc_id"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_LOCATOR_JSON = "extra_locator_json"
        const val EXTRA_PAGE_INDEX = "extra_page_index"
        const val EXTRA_PAGE_COUNT = "extra_page_count"
    }
}
