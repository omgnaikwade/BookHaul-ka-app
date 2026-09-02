package com.example.data.api

object SupabaseConfig {
    const val SUPABASE_URL = "https://fzglhyqchhgbfksfitnc.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_hAF8rPFo_webaAjovQ78iQ_BFnP8ZoX"

    const val BUCKET_COVERS = "book-covers"
    const val BUCKET_PDFS = "Books-pdf"

    fun getCoverUrl(coverPath: String?): String? {
        if (coverPath.isNullOrBlank()) return null
        if (coverPath.startsWith("http://") || coverPath.startsWith("https://")) {
            return coverPath
        }
        val cleanPath = coverPath.trimStart('/')
        return "$SUPABASE_URL/storage/v1/object/public/$BUCKET_COVERS/$cleanPath"
    }

    fun getPdfUrl(pdfPath: String?): String? {
        if (pdfPath.isNullOrBlank()) return null
        if (pdfPath.startsWith("http://") || pdfPath.startsWith("https://")) {
            return pdfPath
        }
        val cleanPath = pdfPath.trimStart('/')
        return "$SUPABASE_URL/storage/v1/object/public/$BUCKET_PDFS/$cleanPath"
    }
}
