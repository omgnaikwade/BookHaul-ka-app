package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.api.NetworkClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("bookhaul_session_prefs", Context.MODE_PRIVATE)

    private val _userIdFlow = MutableStateFlow<String?>(getUserId())
    val userIdFlow: StateFlow<String?> = _userIdFlow.asStateFlow()

    private val _displayNameFlow = MutableStateFlow(getDisplayName())
    val displayNameFlow: StateFlow<String> = _displayNameFlow.asStateFlow()

    init {
        val token = getAccessToken()
        if (!token.isNullOrBlank()) {
            NetworkClient.setUserToken(token)
        }
    }

    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getDisplayName(): String {
        return prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
    }

    fun hasCompletedOnboarding(): Boolean {
        return !getUserId().isNullOrBlank() && getDisplayName().isNotBlank()
    }

    fun saveSession(userId: String, accessToken: String?, refreshToken: String?, displayName: String) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()

        if (!accessToken.isNullOrBlank()) {
            NetworkClient.setUserToken(accessToken)
        }
        _userIdFlow.value = userId
        _displayNameFlow.value = displayName
    }

    fun updateTokens(accessToken: String?, refreshToken: String?) {
        val editor = prefs.edit()
        if (accessToken != null) {
            editor.putString(KEY_ACCESS_TOKEN, accessToken)
            NetworkClient.setUserToken(accessToken)
        }
        if (refreshToken != null) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        editor.apply()
    }

    fun updateDisplayName(displayName: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, displayName).apply()
        _displayNameFlow.value = displayName
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        NetworkClient.setUserToken(null)
        _userIdFlow.value = null
        _displayNameFlow.value = ""
    }

    fun getRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(DELIMITER).filter { it.isNotBlank() }
    }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = getRecentSearches().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val trimmedList = current.take(10)
        prefs.edit().putString(KEY_RECENT_SEARCHES, trimmedList.joinToString(DELIMITER)).apply()
    }

    fun removeRecentSearch(query: String) {
        val current = getRecentSearches().toMutableList()
        current.remove(query.trim())
        prefs.edit().putString(KEY_RECENT_SEARCHES, current.joinToString(DELIMITER)).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    companion object {
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_ACCESS_TOKEN = "key_access_token"
        private const val KEY_REFRESH_TOKEN = "key_refresh_token"
        private const val KEY_DISPLAY_NAME = "key_display_name"
        private const val KEY_RECENT_SEARCHES = "key_recent_searches"
        private const val DELIMITER = "|||||"
    }
}
