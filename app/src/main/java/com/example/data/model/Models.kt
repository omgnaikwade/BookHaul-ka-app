package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CategoryDto(
    val id: Long,
    val name: String,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class BookDto(
    val id: Long,
    val title: String,
    val author: String,
    val description: String? = null,
    @Json(name = "cover_path") val coverPath: String? = null,
    @Json(name = "pdf_path") val pdfPath: String? = null,
    @Json(name = "category_id") val categoryId: Long? = null,
    val category: CategoryDto? = null,
    val status: String = "approved",
    @Json(name = "uploaded_by") val uploadedBy: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ProfileDto(
    val id: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class FavoriteDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "book_id") val bookId: Long,
    @Json(name = "created_at") val createdAt: String? = null,
    val book: BookDto? = null
)

@JsonClass(generateAdapter = true)
data class ReadingProgressDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "book_id") val bookId: Long,
    val progress: Int = 0,
    @Json(name = "updated_at") val updatedAt: String? = null,
    val book: BookDto? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponseDto(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    val user: AuthUserDto? = null
)

@JsonClass(generateAdapter = true)
data class AuthUserDto(
    val id: String,
    val aud: String? = null,
    val role: String? = null,
    @Json(name = "is_anonymous") val isAnonymous: Boolean? = null,
    @Json(name = "created_at") val createdAt: String? = null
)
