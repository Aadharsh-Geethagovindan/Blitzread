package com.example.blitzread.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

private val Context.dataStore by preferencesDataStore(name = "reader_progress")

class ReaderProgressStore(private val context: Context) {

    private fun key(docId: String) = stringPreferencesKey("locator_$docId")

    suspend fun saveLocator(docId: String, locator: Locator) {
        // In your build, Locator likely has toJSON() returning JSONObject
        val jsonString = locator.toJSON().toString()
        context.dataStore.edit { prefs -> prefs[key(docId)] = jsonString }
    }

    suspend fun loadLocator(docId: String): Locator? {
        val prefs = context.dataStore.data.first()
        val jsonString = prefs[key(docId)] ?: return null
        val jsonObj = JSONObject(jsonString)
        return Locator.fromJSON(jsonObj)
    }
}
