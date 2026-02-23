package com.example.blitzread.ui.rsvp

import RsvpViewModel
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.blitzread.ui.theme.BlitzReadTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue

class RsvpActivity : ComponentActivity() {

    private var currentDocId: String = ""

    private var viewModel: RsvpViewModel? = null // Change to nullable instead of lateinit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("RsvpActivity", "onCreate called")

        val docId = intent.getStringExtra(EXTRA_DOC_ID) ?: ""
        currentDocId = docId

        val uriString = intent.getStringExtra(EXTRA_URI) ?: ""
        val locatorJson = intent.getStringExtra(EXTRA_LOCATOR_JSON)
        val pageIndex = intent.getIntExtra(EXTRA_PAGE_INDEX, -1)
        val pageCount = intent.getIntExtra(EXTRA_PAGE_COUNT, 0).takeIf { it > 0 }

        setContent {
            BlitzReadTheme(darkTheme = true, dynamicColor = false) {
                val vm: RsvpViewModel = viewModel()
                viewModel = vm

                val currentWord by vm.currentTokenText.collectAsState()



                RsvpScaffoldedScreen(
                    docId = docId,
                    uriString = uriString,
                    locatorJson = locatorJson,
                    pageIndex = pageIndex.takeIf { it >= 0 },
                    pageCount = pageCount,
                    vm = vm
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (currentDocId.isNotEmpty()) {
            viewModel?.saveCurrentPosition(currentDocId)
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