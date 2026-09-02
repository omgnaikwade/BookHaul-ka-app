package com.example.data.auth

import android.util.Log
import com.example.data.api.SupabaseConfig
import com.example.data.local.SessionManager
import com.example.data.model.AuthResponseDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class SupabaseAuthEvent {
    INITIAL_SESSION,
    SIGNED_IN,
    TOKEN_REFRESHED,
    SIGNED_OUT,
    USER_UPDATED
}

object SupabaseAuthManager {
    private const val TAG = "BookHaul Auth"

    private val _authEvents = MutableSharedFlow<SupabaseAuthEvent>(extraBufferCapacity = 10)
    val authEvents: SharedFlow<SupabaseAuthEvent> = _authEvents.asSharedFlow()

    private val refreshMutex = Mutex()
    private var sessionManager: SessionManager? = null

    // Standalone raw OkHttpClient for auth operations (avoids circular interceptor calls)
    private val authHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val authAdapter by lazy {
        moshi.adapter(AuthResponseDto::class.java)
    }

    fun init(sessionManager: SessionManager) {
        this.sessionManager = sessionManager
    }

    suspend fun restoreSession(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "[BookHaul Auth] Restoring session...")
        val sm = sessionManager
        if (sm == null) {
            Log.w(TAG, "[BookHaul Auth] SessionManager not initialized during restore")
            return@withContext false
        }

        val userId = sm.getUserId()
        val refreshToken = sm.getRefreshToken()
        val accessToken = sm.getAccessToken()

        if (userId.isNullOrBlank()) {
            Log.d(TAG, "[BookHaul Auth] No existing user session found")
            return@withContext false
        }

        // If we have a refresh token, perform a proactive session refresh to guarantee token validity
        if (!refreshToken.isNullOrBlank()) {
            val refreshed = refreshSession()
            if (refreshed) {
                Log.d(TAG, "[BookHaul Auth] Session restored")
                _authEvents.emit(SupabaseAuthEvent.INITIAL_SESSION)
                return@withContext true
            }
        }

        // If refresh token wasn't available or refresh couldn't complete (e.g. offline), use cached token
        if (!accessToken.isNullOrBlank()) {
            Log.d(TAG, "[BookHaul Auth] Session restored with cached credentials")
            _authEvents.emit(SupabaseAuthEvent.INITIAL_SESSION)
            return@withContext true
        }

        Log.w(TAG, "[BookHaul Auth] Session restoration completed with unauthenticated state")
        return@withContext false
    }

    suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val sm = sessionManager
            if (sm == null) {
                Log.w(TAG, "[BookHaul Auth] Session refresh failed: SessionManager is null")
                return@withLock false
            }

            val refreshToken = sm.getRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                Log.w(TAG, "[BookHaul Auth] Session refresh failed: No refresh token available")
                return@withLock false
            }

            try {
                val url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token"
                val jsonPayload = JSONObject().apply {
                    put("refresh_token", refreshToken)
                }.toString()

                val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                authHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string()
                    if (response.isSuccessful && !bodyString.isNullOrBlank()) {
                        val authResponse = authAdapter.fromJson(bodyString)
                        val newAccessToken = authResponse?.accessToken
                        val newRefreshToken = authResponse?.refreshToken ?: refreshToken

                        if (!newAccessToken.isNullOrBlank()) {
                            sm.updateTokens(newAccessToken, newRefreshToken)
                            Log.d(TAG, "[BookHaul Auth] Token refreshed")
                            _authEvents.emit(SupabaseAuthEvent.TOKEN_REFRESHED)
                            return@withLock true
                        }
                    }

                    Log.w(TAG, "[BookHaul Auth] Session refresh failed with status: ${response.code}")
                    return@withLock false
                }
            } catch (e: Exception) {
                Log.e(TAG, "[BookHaul Auth] Session refresh failed with exception: ${e.localizedMessage}")
                return@withLock false
            }
        }
    }

    suspend fun notifySignedIn() {
        Log.d(TAG, "[BookHaul Auth] User signed in")
        _authEvents.emit(SupabaseAuthEvent.SIGNED_IN)
    }

    suspend fun notifyUserUpdated() {
        Log.d(TAG, "[BookHaul Auth] User profile updated")
        _authEvents.emit(SupabaseAuthEvent.USER_UPDATED)
    }

    suspend fun notifySignedOut() {
        Log.d(TAG, "[BookHaul Auth] User signed out")
        _authEvents.emit(SupabaseAuthEvent.SIGNED_OUT)
    }
}
