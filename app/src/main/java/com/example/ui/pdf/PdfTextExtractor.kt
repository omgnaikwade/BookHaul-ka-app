package com.example.ui.pdf

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfTextExtractor {
    private const val TAG = "PdfTextExtractor"
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PDFBox", e)
            }
        }
    }

    /**
     * Extracts text from ONLY the specified 0-indexed page of the PDF file.
     */
    suspend fun extractPageText(
        context: Context,
        pdfFile: File,
        pageIndex: Int,
        totalPages: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            init(context)
            if (!pdfFile.exists() || pdfFile.length() == 0L) {
                return@withContext Result.failure(Exception("PDF file does not exist or is empty"))
            }

            PDDocument.load(pdfFile).use { document ->
                val documentPages = document.numberOfPages
                val oneBasedPage = pageIndex + 1
                if (oneBasedPage < 1 || oneBasedPage > documentPages) {
                    return@withContext Result.failure(Exception("Page index $pageIndex is out of range (1..$documentPages)"))
                }

                val stripper = PDFTextStripper().apply {
                    startPage = oneBasedPage
                    endPage = oneBasedPage
                    sortByPosition = true
                }

                val rawText = stripper.getText(document) ?: ""
                val cleanedText = cleanPdfText(rawText, oneBasedPage, totalPages)
                Result.success(cleanedText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from page $pageIndex", e)
            Result.failure(e)
        }
    }

    /**
     * Cleans extracted PDF text:
     * - Removes orphan page numbers and running headers/footers
     * - Fixes broken hyphenated line breaks (e.g. "connec-\ntion" -> "connection")
     * - Joins unwrapped lines while preserving paragraph breaks
     * - Strips invisible control characters & soft hyphens
     */
    fun cleanPdfText(rawText: String, pageNumber: Int, totalPages: Int): String {
        if (rawText.isBlank()) return ""

        // 1. Remove soft hyphens & non-standard whitespace
        var text = rawText
            .replace("\u00AD", "") // soft hyphen
            .replace("\uFEFF", "") // byte order mark
            .replace("\u00A0", " ") // non-breaking space
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val lines = text.lines()
        val cleanedLines = mutableListOf<String>()

        val pageNumberRegex = Regex("^\\s*(page\\s*)?(\\d+|[ivxlcdm]+)(\\s*of\\s*\\d+)?\\s*$", RegexOption.IGNORE_CASE)
        val standaloneDashesPage = Regex("^\\s*-\\s*\\d+\\s*-\\s*$")

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()

            // Skip empty lines
            if (trimmed.isEmpty()) {
                cleanedLines.add("")
                continue
            }

            // Remove standalone page numbers at top or bottom
            if ((index < 3 || index > lines.size - 4) &&
                (pageNumberRegex.matches(trimmed) || standaloneDashesPage.matches(trimmed) || trimmed == pageNumber.toString())
            ) {
                continue
            }

            cleanedLines.add(trimmed)
        }

        // 2. Re-assemble lines, fixing hyphenation across line breaks
        val joined = cleanedLines.joinToString("\n")

        // Replace hyphen at end of line followed by word on next line (e.g. "infor-\nmation" -> "information")
        val unhyphenated = joined.replace(Regex("(\\b[\\p{L}\\d]{2,})-\\n\\s*([\\p{L}\\d]{2,}\\b)"), "$1$2")

        // 3. Connect lines within paragraphs
        // Multiple consecutive newlines represent paragraph breaks; single newlines within sentences are joined with a space
        val paragraphs = unhyphenated.split(Regex("\\n{2,}"))
        val normalizedParagraphs = paragraphs.map { para ->
            para.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        }.filter { it.isNotBlank() }

        return normalizedParagraphs.joinToString("\n\n").trim()
    }
}
