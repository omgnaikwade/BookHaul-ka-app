package com.example.data.repository

import android.util.Log
import com.example.data.api.NetworkClient
import com.example.data.api.SupabaseApiService
import com.example.data.local.SessionManager
import com.example.data.model.BookDto
import com.example.data.model.CategoryDto
import com.example.data.model.FavoriteDto
import com.example.data.model.ProfileDto
import com.example.data.model.ReadingProgressDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}

class BookHaulRepository(
    private val sessionManager: SessionManager,
    private val apiService: SupabaseApiService = NetworkClient.apiService
) {

    val currentUserId: String?
        get() = sessionManager.getUserId()

    val currentDisplayName: String
        get() = sessionManager.getDisplayName()

    fun hasCompletedOnboarding(): Boolean = sessionManager.hasCompletedOnboarding()

    suspend fun signupAnonymousAndCreateProfile(name: String): Resource<ProfileDto> = withContext(Dispatchers.IO) {
        try {
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) {
                return@withContext Resource.Error("Please enter your name to continue.")
            }

            // 1. Sign up anonymously on Supabase Auth
            val authResp = apiService.signupAnonymous(mapOf())
            if (!authResp.isSuccessful || authResp.body()?.user == null) {
                val errorMsg = authResp.errorBody()?.string() ?: "Failed to initialize anonymous session"
                Log.e("BookHaulRepo", "Anonymous signup error: $errorMsg (code ${authResp.code()})")
                return@withContext Resource.Error("Authentication failed: $errorMsg")
            }

            val authData = authResp.body()!!
            val userId = authData.user!!.id
            val accessToken = authData.accessToken
            val refreshToken = authData.refreshToken

            // Set token for subsequent API calls
            if (!accessToken.isNullOrBlank()) {
                NetworkClient.setUserToken(accessToken)
            }

            // 2. Update display_name on the profile created by Supabase trigger
            val patchResp = apiService.updateProfile(
                idFilter = "eq.$userId",
                body = mapOf("display_name" to trimmedName)
            )

            if (!patchResp.isSuccessful) {
                val patchError = patchResp.errorBody()?.string() ?: "Failed to save profile name"
                Log.e("BookHaulRepo", "Update profile error: $patchError (code ${patchResp.code()})")
                return@withContext Resource.Error("Failed to save your name to Supabase: $patchError")
            }

            val savedProfileList = patchResp.body()
            val profile = savedProfileList?.firstOrNull() ?: ProfileDto(id = userId, displayName = trimmedName)

            // 3. Save locally in SessionManager
            sessionManager.saveSession(
                userId = userId,
                accessToken = accessToken,
                refreshToken = refreshToken,
                displayName = trimmedName
            )

            com.example.data.auth.SupabaseAuthManager.notifySignedIn()

            Resource.Success(profile)
        } catch (e: Exception) {
            Log.e("BookHaulRepo", "signupAnonymousAndCreateProfile exception", e)
            Resource.Error(e.localizedMessage ?: "Network error connecting to Supabase", e)
        }
    }

    suspend fun restoreProfile(): Resource<ProfileDto> = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId()
        if (userId.isNullOrBlank()) {
            return@withContext Resource.Error("No active session found")
        }

        try {
            val resp = apiService.getProfile(idFilter = "eq.$userId")
            if (resp.isSuccessful) {
                val profile = resp.body()?.firstOrNull()
                if (profile != null) {
                    sessionManager.updateDisplayName(profile.displayName)
                    return@withContext Resource.Success(profile)
                }
            }
            // Fallback to local session if network profile query is empty but session exists
            val localName = sessionManager.getDisplayName()
            if (localName.isNotBlank()) {
                Resource.Success(ProfileDto(id = userId, displayName = localName))
            } else {
                Resource.Error("Profile not found on Supabase")
            }
        } catch (e: Exception) {
            Log.e("BookHaulRepo", "restoreProfile error", e)
            val localName = sessionManager.getDisplayName()
            if (localName.isNotBlank()) {
                Resource.Success(ProfileDto(id = userId, displayName = localName))
            } else {
                Resource.Error(e.localizedMessage ?: "Failed to load profile", e)
            }
        }
    }

    suspend fun updateProfileName(newName: String): Resource<ProfileDto> = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext Resource.Error("User not logged in")
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return@withContext Resource.Error("Name cannot be empty")

        try {
            val resp = apiService.updateProfile(
                idFilter = "eq.$userId",
                body = mapOf("display_name" to trimmed)
            )
            if (resp.isSuccessful) {
                val profile = resp.body()?.firstOrNull() ?: ProfileDto(id = userId, displayName = trimmed)
                sessionManager.updateDisplayName(trimmed)
                com.example.data.auth.SupabaseAuthManager.notifyUserUpdated()
                Resource.Success(profile)
            } else {
                val err = resp.errorBody()?.string() ?: "Failed to update profile name"
                Resource.Error(err)
            }
        } catch (e: Exception) {
            Log.e("BookHaulRepo", "updateProfileName error", e)
            Resource.Error(e.localizedMessage ?: "Network error updating name", e)
        }
    }

    suspend fun getApprovedBooks(): Resource<List<BookDto>> = withContext(Dispatchers.IO) {
        try {
            val resp = apiService.getApprovedBooks()
            if (resp.isSuccessful) {
                val books = resp.body() ?: emptyList()
                Resource.Success(books)
            } else {
                val err = resp.errorBody()?.string() ?: "Failed to load books from Supabase"
                Log.e("BookHaulRepo", "getApprovedBooks error: $err (code ${resp.code()})")
                Resource.Error(err)
            }
        } catch (e: Exception) {
            Log.e("BookHaulRepo", "getApprovedBooks exception", e)
            Resource.Error(e.localizedMessage ?: "Network connection error", e)
        }
    }

    suspend fun getCategories(): Resource<List<CategoryDto>> = withContext(Dispatchers.IO) {
        try {
            val resp = apiService.getCategories()
            if (resp.isSuccessful) {
                val categories = resp.body() ?: emptyList()
                Resource.Success(categories)
            } else {
                val err = resp.errorBody()?.string() ?: "Failed to load categories"
                Resource.Error(err)
            }
        } catch (e: Exception) {
            Log.e("BookHaulRepo", "getCategories exception", e)
            Resource.Error(e.localizedMessage ?: "Network connection error", e)
        }
    }

    suspend fun getBooksByCategory(categoryId: Long): Resource<List<BookDto>> = withContext(Dispatchers.IO) {
        try {
            val resp = apiService.getBooksByCategory(categoryFilter = "eq.$categoryId")
            if (resp.isSuccessful) {
                Resource.Success(resp.body() ?: emptyList())
            } else {
                Resource.Error(resp.errorBody()?.string() ?: "Failed to load category books")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error", e)
        }
    }

    suspend fun searchBooks(query: String): Resource<List<BookDto>> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext Resource.Success(emptyList())
        sessionManager.addRecentSearch(clean)

        try {
            val orFilter = "(title.ilike.*$clean*,author.ilike.*$clean*,description.ilike.*$clean*)"
            val resp = apiService.searchBooks(orFilter = orFilter)
            if (resp.isSuccessful) {
                Resource.Success(resp.body() ?: emptyList())
            } else {
                Resource.Error(resp.errorBody()?.string() ?: "Search failed")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error searching books", e)
        }
    }

    suspend fun getBookById(bookId: Long): Resource<BookDto> = withContext(Dispatchers.IO) {
        try {
            val resp = apiService.getBookById(idFilter = "eq.$bookId")
            if (resp.isSuccessful) {
                val book = resp.body()?.firstOrNull()
                if (book != null) {
                    Resource.Success(book)
                } else {
                    Resource.Error("Book not found")
                }
            } else {
                Resource.Error(resp.errorBody()?.string() ?: "Failed to load book details")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error loading book", e)
        }
    }

    suspend fun getFavorites(): Resource<List<FavoriteDto>> = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext Resource.Success(emptyList())
        try {
            val resp = apiService.getFavorites(userFilter = "eq.$userId")
            if (resp.isSuccessful) {
                Resource.Success(resp.body() ?: emptyList())
            } else {
                Resource.Error(resp.errorBody()?.string() ?: "Failed to load favorites")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error loading favorites", e)
        }
    }

    suspend fun isBookFavorited(bookId: Long): Boolean = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext false
        try {
            val resp = apiService.getFavorites(userFilter = "eq.$userId")
            if (resp.isSuccessful) {
                return@withContext resp.body()?.any { it.bookId == bookId } ?: false
            }
        } catch (_: Exception) {}
        false
    }

    suspend fun toggleFavorite(bookId: Long, currentlyFavorited: Boolean): Resource<Boolean> = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext Resource.Error("User not logged in")
        try {
            if (currentlyFavorited) {
                val resp = apiService.removeFavorite(userFilter = "eq.$userId", bookFilter = "eq.$bookId")
                if (resp.isSuccessful) {
                    Resource.Success(false)
                } else {
                    Resource.Error(resp.errorBody()?.string() ?: "Failed to remove favorite")
                }
            } else {
                val resp = apiService.addFavorite(favorite = FavoriteDto(userId = userId, bookId = bookId))
                if (resp.isSuccessful) {
                    Resource.Success(true)
                } else {
                    Resource.Error(resp.errorBody()?.string() ?: "Failed to add favorite")
                }
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error updating favorite", e)
        }
    }

    suspend fun getReadingProgressList(): Resource<List<ReadingProgressDto>> = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext Resource.Success(emptyList())
        try {
            val resp = apiService.getReadingProgress(userFilter = "eq.$userId")
            if (resp.isSuccessful) {
                Resource.Success(resp.body() ?: emptyList())
            } else {
                Resource.Error(resp.errorBody()?.string() ?: "Failed to load reading progress")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error loading reading progress", e)
        }
    }

    suspend fun saveReadingProgress(bookId: Long, progress: Int): Resource<Unit> = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext Resource.Error("User not logged in")
        try {
            val clamped = progress.coerceIn(0, 100)
            val resp = apiService.saveReadingProgress(
                progress = ReadingProgressDto(userId = userId, bookId = bookId, progress = clamped)
            )
            if (resp.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error(resp.errorBody()?.string() ?: "Failed to save reading progress")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error saving progress", e)
        }
    }

    suspend fun deleteReadingProgress(bookId: Long): Resource<Unit> = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext Resource.Error("User not logged in")
        try {
            val resp = apiService.deleteReadingProgress(userFilter = "eq.$userId", bookFilter = "eq.$bookId")
            if (resp.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error(resp.errorBody()?.string() ?: "Failed to delete progress")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error deleting progress", e)
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }
}
