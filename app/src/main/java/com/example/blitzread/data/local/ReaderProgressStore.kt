package com.example.blitzread.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

private val Context.dataStore by preferencesDataStore(name = "reader_progress")

class ReaderProgressStore(private val context: Context) {

    private fun locatorKey(docId: String) = stringPreferencesKey("locator_$docId")
    private fun rsvpPositionKey(docId: String) = intPreferencesKey("rsvp_token_$docId")
    private fun rsvpWordKey(docId: String) = stringPreferencesKey("rsvp_word_$docId")
    suspend fun saveLocator(docId: String, locator: Locator) {
        val jsonString = locator.toJSON().toString()
        context.dataStore.edit { prefs -> prefs[locatorKey(docId)] = jsonString }
    }

    suspend fun loadLocator(docId: String): Locator? {
        val prefs = context.dataStore.data.first()
        val jsonString = prefs[locatorKey(docId)] ?: return null
        val jsonObj = JSONObject(jsonString)
        return Locator.fromJSON(jsonObj)
    }

    suspend fun saveRsvpPosition(docId: String, tokenIndex: Int, word: String = "") {
        Log.d("ReaderProgressStore", "Saving RSVP: docId=$docId, tokenIndex=$tokenIndex, word=$word")
        context.dataStore.edit { prefs ->
            prefs[rsvpPositionKey(docId)] = tokenIndex
            if (word.isNotEmpty()) {
                prefs[rsvpWordKey(docId)] = word
            }
        }
        Log.d("ReaderProgressStore", "Save complete")
    }

    suspend fun loadRsvpWord(docId: String): String? {
        val prefs = context.dataStore.data.first()
        val result = prefs[rsvpWordKey(docId)]
        Log.d("ReaderProgressStore", "Loading RSVP word: docId=$docId, result='$result'")
        return result
    }
    suspend fun loadRsvpPosition(docId: String): Int? {
        val prefs = context.dataStore.data.first()
        val result = prefs[rsvpPositionKey(docId)]
        Log.d("ReaderProgressStore", "Loading RSVP position: docId=$docId, result=$result")
        return result
    }
}