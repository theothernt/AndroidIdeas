package com.neilturner.videothumbnails.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.videoSelectionDataStore: DataStore<Preferences> by preferencesDataStore(name = "video_selection")

interface VideoSelectionStore {
    val hiddenVideoIds: Flow<Set<String>>

    suspend fun setHidden(
        videoId: String,
        hidden: Boolean,
    )

    suspend fun toggle(videoId: String): Boolean

    suspend fun clearAll()
}

class DataStoreVideoSelectionStore(
    private val context: Context,
) : VideoSelectionStore {
    private val hiddenIdsKey = stringSetPreferencesKey("hidden_video_ids")

    override val hiddenVideoIds: Flow<Set<String>> =
        context.videoSelectionDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }.map { preferences -> preferences[hiddenIdsKey].orEmpty() }

    override suspend fun setHidden(
        videoId: String,
        hidden: Boolean,
    ) {
        context.videoSelectionDataStore.edit { preferences ->
            val current = preferences[hiddenIdsKey].orEmpty()
            preferences[hiddenIdsKey] = if (hidden) current + videoId else current - videoId
        }
    }

    override suspend fun toggle(videoId: String): Boolean {
        var hidden = false
        context.videoSelectionDataStore.edit { preferences ->
            val current = preferences[hiddenIdsKey].orEmpty()
            hidden = videoId !in current
            preferences[hiddenIdsKey] = if (hidden) current + videoId else current - videoId
        }
        return hidden
    }

    override suspend fun clearAll() {
        context.videoSelectionDataStore.edit { preferences ->
            preferences[hiddenIdsKey] = emptySet()
        }
    }
}
