package com.example.data.api

import com.example.data.model.AuthResponseDto
import com.example.data.model.BookDto
import com.example.data.model.CategoryDto
import com.example.data.model.FavoriteDto
import com.example.data.model.ProfileDto
import com.example.data.model.ReadingProgressDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseApiService {

    // --- AUTH ---
    @POST("auth/v1/signup")
    suspend fun signupAnonymous(
        @Body body: Map<String, String> = emptyMap()
    ): Response<AuthResponseDto>

    // --- PROFILES ---
    @GET("rest/v1/profiles")
    suspend fun getProfile(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<ProfileDto>>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Query("id") idFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, String>
    ): Response<List<ProfileDto>>

    // --- BOOKS ---
    @GET("rest/v1/books")
    suspend fun getApprovedBooks(
        @Query("status") status: String = "eq.approved",
        @Query("select") select: String = "*,category:categories(*)",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<BookDto>>

    @GET("rest/v1/books")
    suspend fun getBooksByCategory(
        @Query("status") status: String = "eq.approved",
        @Query("category_id") categoryFilter: String,
        @Query("select") select: String = "*,category:categories(*)",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<BookDto>>

    @GET("rest/v1/books")
    suspend fun searchBooks(
        @Query("status") status: String = "eq.approved",
        @Query("or") orFilter: String,
        @Query("select") select: String = "*,category:categories(*)"
    ): Response<List<BookDto>>

    @GET("rest/v1/books")
    suspend fun getBookById(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*,category:categories(*)"
    ): Response<List<BookDto>>

    // --- CATEGORIES ---
    @GET("rest/v1/categories")
    suspend fun getCategories(
        @Query("select") select: String = "*",
        @Query("order") order: String = "name.asc"
    ): Response<List<CategoryDto>>

    // --- FAVORITES ---
    @GET("rest/v1/favorites")
    suspend fun getFavorites(
        @Query("user_id") userFilter: String,
        @Query("select") select: String = "*,book:books(*,category:categories(*))"
    ): Response<List<FavoriteDto>>

    @POST("rest/v1/favorites")
    suspend fun addFavorite(
        @Header("Prefer") prefer: String = "return=representation",
        @Body favorite: FavoriteDto
    ): Response<List<FavoriteDto>>

    @DELETE("rest/v1/favorites")
    suspend fun removeFavorite(
        @Query("user_id") userFilter: String,
        @Query("book_id") bookFilter: String
    ): Response<Unit>

    // --- READING PROGRESS ---
    @GET("rest/v1/reading_progress")
    suspend fun getReadingProgress(
        @Query("user_id") userFilter: String,
        @Query("select") select: String = "*,book:books(*,category:categories(*))",
        @Query("order") order: String = "updated_at.desc"
    ): Response<List<ReadingProgressDto>>

    @POST("rest/v1/reading_progress")
    suspend fun saveReadingProgress(
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Body progress: ReadingProgressDto
    ): Response<List<ReadingProgressDto>>

    @DELETE("rest/v1/reading_progress")
    suspend fun deleteReadingProgress(
        @Query("user_id") userFilter: String,
        @Query("book_id") bookFilter: String
    ): Response<Unit>
}
