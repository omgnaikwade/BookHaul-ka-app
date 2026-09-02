package com.example.data.api

import android.util.Log
import com.example.data.auth.SupabaseAuthManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val TAG = "BookHaul Auth"

    @Volatile
    private var customUserToken: String? = null

    fun setUserToken(token: String?) {
        customUserToken = token
    }

    fun getUserToken(): String? {
        return customUserToken
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .header("Content-Type", "application/json")

        // Use custom user token if available and request doesn't already specify Authorization
        if (original.header("Authorization") == null) {
            val tokenToUse = customUserToken ?: SupabaseConfig.SUPABASE_ANON_KEY
            builder.header("Authorization", "Bearer $tokenToUse")
        }

        val request = builder.build()
        val response = chain.proceed(request)

        // Detect if response returned 401 Unauthorized or PGRST303 / JWT expired
        if (response.code == 401 || isAuthExpiredResponse(response)) {
            val isAlreadyRetrying = original.header("X-Bookhaul-Retry") != null
            if (!isAlreadyRetrying) {
                Log.w(TAG, "[BookHaul Auth] Detected auth/token expiry (code ${response.code}). Attempting token refresh...")
                response.close()

                val refreshSuccess = runBlocking {
                    SupabaseAuthManager.refreshSession()
                }

                if (refreshSuccess) {
                    Log.d(TAG, "[BookHaul Auth] Retrying request after session refresh")
                    val freshToken = customUserToken ?: SupabaseConfig.SUPABASE_ANON_KEY
                    val retryRequest = original.newBuilder()
                        .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer $freshToken")
                        .header("X-Bookhaul-Retry", "true")
                        .build()
                    return@Interceptor chain.proceed(retryRequest)
                } else {
                    Log.w(TAG, "[BookHaul Auth] Session refresh failed")
                    
                    // Fallback for public resources (books and categories)
                    val path = original.url.encodedPath
                    val isPublicData = original.method == "GET" && (path.contains("rest/v1/books") || path.contains("rest/v1/categories"))
                    if (isPublicData && customUserToken != null) {
                        Log.d(TAG, "[BookHaul Auth] Falling back to anon key for public library request")
                        val fallbackRequest = original.newBuilder()
                            .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
                            .header("X-Bookhaul-Retry", "true")
                            .build()
                        return@Interceptor chain.proceed(fallbackRequest)
                    }
                }
            }
        }

        response
    }

    private fun isAuthExpiredResponse(response: Response): Boolean {
        if (response.code == 401 || response.code == 403) return true
        return false
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val fileDownloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val req = original.newBuilder()
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${customUserToken ?: SupabaseConfig.SUPABASE_ANON_KEY}")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val apiService: SupabaseApiService by lazy {
        Retrofit.Builder()
            .baseUrl(SupabaseConfig.SUPABASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseApiService::class.java)
    }
}
