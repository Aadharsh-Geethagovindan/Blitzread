package com.example.blitzread

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class BlitzReadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
    }
}