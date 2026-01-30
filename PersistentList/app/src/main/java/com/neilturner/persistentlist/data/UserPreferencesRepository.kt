package com.neilturner.persistentlist.data

import android.content.Context
import android.content.SharedPreferences

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HIGHLIGHTED_INDEX = "highlighted_index"
    }

    fun saveHighlightedIndex(index: Int) {
        prefs.edit().putInt(KEY_HIGHLIGHTED_INDEX, index).apply()
    }

    fun getHighlightedIndex(): Int {
        return prefs.getInt(KEY_HIGHLIGHTED_INDEX, -1)
    }
}
