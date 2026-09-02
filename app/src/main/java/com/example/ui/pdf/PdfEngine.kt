package com.example.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.api.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class PdfEngine(private val context: Context) {

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    var currentPdfFile: File? = null
        private set

    var totalPages: Int = 0
        private set

    suspend fun loadPdf(bookId: Long, pdfUrl: String, onProgress: (Float) -> Unit = {}): Result<Int> = withContext(Dispatchers.IO) {
        try {
            close()
            val pdfDir = File(context.cacheDir, "books_pdf")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val localFile = File(pdfDir, "book_${bookId}.pdf")
            currentPdfFile = localFile

            // Download if not already cached
            if (!localFile.exists() || localFile.length() == 0L) {
                val request = Request.Builder().url(pdfUrl).build()
                val response = NetworkClient.fileDownloadClient.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    val code = response.code
                    val msg = response.message
                    return@withContext Result.failure(Exception("Failed to download PDF (HTTP $code: $msg)"))
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(localFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        onProgress(totalBytesRead.toFloat() / contentLength)
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()
            }

            // Open with native PdfRenderer
            parcelFileDescriptor = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
            totalPages = pdfRenderer?.pageCount ?: 0

            if (totalPages <= 0) {
                return@withContext Result.failure(Exception("The PDF file contains 0 pages."))
            }

            Result.success(totalPages)
        } catch (e: Exception) {
            Log.e("PdfEngine", "Error loading PDF", e)
            Result.failure(e)
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= totalPages) return@withContext null

        var page: PdfRenderer.Page? = null
        try {
            page = renderer.openPage(pageIndex)
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            Log.e("PdfEngine", "Failed rendering page $pageIndex", e)
            null
        } finally {
            try {
                page?.close()
            } catch (_: Exception) {}
        }
    }

    suspend fun extractPageText(pageIndex: Int): Result<String> = withContext(Dispatchers.IO) {
        val file = currentPdfFile ?: return@withContext Result.failure(Exception("No PDF file currently opened"))
        PdfTextExtractor.extractPageText(context, file, pageIndex, totalPages)
    }

    fun close() {
        try {
            pdfRenderer?.close()
        } catch (_: Exception) {}
        try {
            parcelFileDescriptor?.close()
        } catch (_: Exception) {}
        pdfRenderer = null
        parcelFileDescriptor = null
        currentPdfFile = null
        totalPages = 0
    }
}
