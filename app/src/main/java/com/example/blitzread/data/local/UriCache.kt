package com.example.blitzread.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object UriCache {
    fun copyToCache(context: Context, uri: Uri, suffix: String): File {
        val outFile = File(context.cacheDir, "doc-${System.currentTimeMillis()}$suffix")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        return outFile
    }
}
