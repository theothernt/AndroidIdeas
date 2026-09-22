package com.neilturner.playerexp.data.plex

import android.util.Log

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/** Keeps credentials and all server-derived values in one encrypted store. */
class PlexAccountStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "plex_account",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun accountToken(): String? = preferences.getString(KEY_ACCOUNT_TOKEN, null).also { Log.d("PlexApi", "AccountStore: accountToken present: ${it != null}") }

    fun clientIdentifier(): String = preferences.getString(KEY_CLIENT_IDENTIFIER, null)
        ?.also { Log.d("PlexApi", "AccountStore: clientIdentifier: $it") }
        ?: UUID.randomUUID().toString().also { identifier ->
            Log.d("PlexApi", "AccountStore: generated new clientIdentifier: $identifier")
            preferences.edit().putString(KEY_CLIENT_IDENTIFIER, identifier).apply()
        }

    fun saveLinkedAccount(accountToken: String, serverName: String?, serverUrl: String? = null) {
        Log.d("PlexApi", "AccountStore: saveLinkedAccount serverName=$serverName serverUrl=$serverUrl")
        preferences.edit()
            .putString(KEY_ACCOUNT_TOKEN, accountToken)
            .putString(KEY_SERVER_NAME, serverName)
            .putString(KEY_SERVER_URL, serverUrl)
            .apply()
    }

    fun serverName(): String? = preferences.getString(KEY_SERVER_NAME, null).also { Log.d("PlexApi", "AccountStore: serverName: $it") }

    fun serverUrl(): String? = preferences.getString(KEY_SERVER_URL, null).also { Log.d("PlexApi", "AccountStore: serverUrl: $it") }

    /** A reset intentionally removes every persisted Plex credential and selection. */
    fun clearPlexData() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCOUNT_TOKEN = "account_token"
        const val KEY_CLIENT_IDENTIFIER = "client_identifier"
        const val KEY_SERVER_NAME = "server_name"
        const val KEY_SERVER_URL = "server_url"
    }
}
