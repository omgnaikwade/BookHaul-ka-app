package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.SupabaseConfig
import com.example.data.model.BookDto
import com.example.ui.components.EmptyState
import com.example.ui.components.PdfReadAloudBar
import com.example.ui.components.PdfTranslationPanel
import com.example.ui.components.TtsLanguageSelectorDialog
import com.example.ui.pdf.PdfEngine
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.translation.SupportedTranslationLanguage
import com.example.ui.translation.TranslationManager
import com.example.ui.tts.TtsLanguage
import com.example.ui.tts.TtsManager
import com.example.ui.tts.TtsPlaybackState
import kotlinx.coroutines.launch

@Composable
fun PdfReaderScreen(
    book: BookDto,
    initialProgress: Int,
    onProgressUpdated: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pdfEngine = remember { PdfEngine(context) }
    val ttsManager = remember { TtsManager(context) }
    val ttsState by ttsManager.state.collectAsStateWithLifecycle()

    var isLoadingPdf by remember { mutableStateOf(true) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPage by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(false) }

    // Read aloud state
    var isReadAloudBarVisible by remember { mutableStateOf(false) }
    var isExtractingText by remember { mutableStateOf(false) }
    var isLanguageSelectorOpen by remember { mutableStateOf(false) }

    // Translation state
    var isTranslationPanelOpen by remember { mutableStateOf(false) }
    var selectedTranslationLanguage by remember {
        mutableStateOf(
            TranslationManager.SUPPORTED_LANGUAGES.firstOrNull { it.code == "hi" }
                ?: TranslationManager.SUPPORTED_LANGUAGES.first()
        )
    }
    var isTranslating by remember { mutableStateOf(false) }
    var translationStatusText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var isTranslatedOffline by remember { mutableStateOf(true) }
    var translationErrorMessage by remember { mutableStateOf<String?>(null) }
    var isScannedPageForTranslation by remember { mutableStateOf(false) }
    var lastTranslatedPageIndex by remember { mutableIntStateOf(-1) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val pdfUrl = SupabaseConfig.getPdfUrl(book.pdfPath)

    fun startReadAloudForPage(pageIndex: Int) {
        coroutineScope.launch {
            isExtractingText = true
            val textResult = pdfEngine.extractPageText(pageIndex)
            isExtractingText = false
            if (textResult.isSuccess) {
                val text = textResult.getOrNull() ?: ""
                ttsManager.startReadingPage(pageIndex, text)
            } else {
                ttsManager.startReadingPage(pageIndex, "")
            }
        }
    }

    fun executeTranslation(pageIndex: Int, autoReadAloud: Boolean = false) {
        coroutineScope.launch {
            isTranslating = true
            translationErrorMessage = null
            isScannedPageForTranslation = false
            translationStatusText = "Extracting text from page ${pageIndex + 1}..."

            val textResult = pdfEngine.extractPageText(pageIndex)
            val extractedPageText = textResult.getOrNull()

            if (textResult.isFailure || extractedPageText.isNullOrBlank()) {
                isTranslating = false
                isScannedPageForTranslation = true
                translationErrorMessage = "This page appears to be a scanned image or contains no selectable digital text."
                return@launch
            }

            translationStatusText = "Translating into ${selectedTranslationLanguage.displayName}..."

            val translationResult = TranslationManager.translateText(
                text = extractedPageText,
                targetLang = selectedTranslationLanguage,
                sourceLangCode = "en"
            )

            if (translationResult.isSuccess) {
                val result = translationResult.getOrThrow()
                translatedText = result.translatedText
                isTranslatedOffline = result.isOfflineModel
                lastTranslatedPageIndex = pageIndex
                isTranslating = false

                if (autoReadAloud) {
                    val matchingTtsLanguage = TranslationManager.findTtsLanguage(
                        selectedTranslationLanguage,
                        ttsState.availableLanguages
                    )
                    isReadAloudBarVisible = true
                    ttsManager.startReadingTranslated(pageIndex, result.translatedText, matchingTtsLanguage)
                }
            } else {
                isTranslating = false
                translationErrorMessage = translationResult.exceptionOrNull()?.localizedMessage
                    ?: "Translation failed for ${selectedTranslationLanguage.displayName}"
            }
        }
    }

    fun loadCurrentPage(pageIndex: Int, autoReadAloud: Boolean = false) {
        if (totalPages <= 0) return
        val clampedIndex = pageIndex.coerceIn(0, totalPages - 1)
        val wasDifferentPage = currentPageIndex != clampedIndex
        currentPageIndex = clampedIndex
        scale = 1f
        offsetX = 0f
        offsetY = 0f

        if (wasDifferentPage) {
            if (lastTranslatedPageIndex != clampedIndex) {
                translatedText = null
                translationErrorMessage = null
                isScannedPageForTranslation = false
            }

            if (isReadAloudBarVisible) {
                ttsManager.stop()
                if (autoReadAloud) {
                    startReadAloudForPage(clampedIndex)
                }
            }
        }

        coroutineScope.launch {
            isRenderingPage = true
            val bitmap = pdfEngine.renderPage(clampedIndex)
            currentPageBitmap = bitmap
            isRenderingPage = false

            // Calculate progress percentage
            val progressPercent = (((clampedIndex + 1).toFloat() / totalPages) * 100).toInt().coerceIn(1, 100)
            onProgressUpdated(progressPercent)
        }
    }

    DisposableEffect(Unit) {
        ttsManager.onPageCompleted = { completedPageIndex ->
            if (completedPageIndex + 1 < totalPages) {
                loadCurrentPage(completedPageIndex + 1, autoReadAloud = true)
            }
        }
        onDispose {
            ttsManager.release()
            pdfEngine.close()
        }
    }

    LaunchedEffect(pdfUrl) {
        if (pdfUrl.isNullOrBlank()) {
            isLoadingPdf = false
            errorMessage = "No PDF file is associated with this book in the Supabase database."
            return@LaunchedEffect
        }

        isLoadingPdf = true
        errorMessage = null
        val result = pdfEngine.loadPdf(book.id, pdfUrl) { prog ->
            downloadProgress = prog
        }

        if (result.isSuccess) {
            totalPages = result.getOrNull() ?: 0
            isLoadingPdf = false
            // Calculate start page from initial progress
            val startPage = if (initialProgress > 0 && initialProgress < 100 && totalPages > 1) {
                ((initialProgress / 100f) * totalPages).toInt().coerceIn(0, totalPages - 1)
            } else 0
            loadCurrentPage(startPage)
        } else {
            isLoadingPdf = false
            errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to open PDF from Supabase Storage"
        }
    }

    // Invert matrix for dark reading mode
    val nightModeMatrix = remember {
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isNightMode) Color(0xFF0F0F14) else DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Reader App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = {
                        ttsManager.stop()
                        onBack()
                    },
                    modifier = Modifier.testTag("pdf_reader_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (totalPages > 0) {
                        Text(
                            text = "Page ${currentPageIndex + 1} of $totalPages",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = VioletPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Translate Page Button
                if (totalPages > 0) {
                    IconButton(
                        onClick = {
                            isTranslationPanelOpen = true
                        },
                        modifier = Modifier.testTag("pdf_translate_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Translate Page",
                            tint = if (isTranslationPanelOpen || translatedText != null) Color(0xFF60A5FA) else TextSecondary
                        )
                    }
                }

                // Read Aloud Button
                if (totalPages > 0) {
                    val isPlaying = ttsState.playbackState == TtsPlaybackState.PLAYING
                    IconButton(
                        onClick = {
                            if (!isReadAloudBarVisible) {
                                isReadAloudBarVisible = true
                                startReadAloudForPage(currentPageIndex)
                            } else {
                                if (isPlaying) {
                                    ttsManager.pause()
                                } else {
                                    if (ttsState.sentences.isNotEmpty()) {
                                        ttsManager.play()
                                    } else {
                                        startReadAloudForPage(currentPageIndex)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("pdf_read_aloud_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = if (isReadAloudBarVisible || isPlaying) VioletPrimary else TextSecondary
                        )
                    }
                }

                // Night mode toggle
                IconButton(
                    onClick = { isNightMode = !isNightMode },
                    modifier = Modifier.testTag("pdf_night_mode_toggle")
                ) {
                    Icon(
                        imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle night mode",
                        tint = if (isNightMode) Color(0xFFFBBF24) else TextSecondary
                    )
                }

                // Mark Completed CTA
                if (totalPages > 0) {
                    IconButton(
                        onClick = {
                            ttsManager.stop()
                            onProgressUpdated(100)
                            loadCurrentPage(totalPages - 1)
                        },
                        modifier = Modifier.testTag("pdf_mark_completed_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Complete book",
                            tint = Color(0xFF34D399)
                        )
                    }
                }
            }
        }

        // PDF Content Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (isNightMode) Color(0xFF14141E) else Color(0xFF181528)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoadingPdf -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            color = VioletPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Opening \"${book.title}\"...",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (downloadProgress > 0f) "Downloading reader copy (${(downloadProgress * 100).toInt()}%)" else "Preparing your book...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        )
                        if (downloadProgress > 0f) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = VioletPrimary,
                                trackColor = DarkOutline
                            )
                        }
                    }
                }
                !errorMessage.isNullOrBlank() -> {
                    EmptyState(
                        title = "Unable to load PDF",
                        message = errorMessage ?: "Unknown error loading file",
                        actionButtonText = "Try Again",
                        onActionClick = {
                            isLoadingPdf = true
                            errorMessage = null
                            coroutineScope.launch {
                                val res = pdfEngine.loadPdf(book.id, pdfUrl ?: "")
                                if (res.isSuccess) {
                                    totalPages = res.getOrNull() ?: 0
                                    isLoadingPdf = false
                                    loadCurrentPage(0)
                                } else {
                                    isLoadingPdf = false
                                    errorMessage = res.exceptionOrNull()?.localizedMessage
                                }
                            }
                        }
                    )
                }
                currentPageBitmap != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 3.5f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = currentPageBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPageIndex + 1}",
                            colorFilter = if (isNightMode) ColorFilter.colorMatrix(nightModeMatrix) else null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )

                        if (isRenderingPage) {
                            CircularProgressIndicator(
                                color = VioletPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        // Read Aloud Control Bar (when active)
        if (isReadAloudBarVisible && totalPages > 0) {
            PdfReadAloudBar(
                state = ttsState,
                currentPage = currentPageIndex,
                totalPages = totalPages,
                isExtracting = isExtractingText,
                onPlay = {
                    if (ttsState.sentences.isEmpty()) {
                        startReadAloudForPage(currentPageIndex)
                    } else {
                        ttsManager.play()
                    }
                },
                onPause = { ttsManager.pause() },
                onStop = { ttsManager.stop() },
                onNextSentence = {
                    val nextIdx = ttsState.currentSentenceIndex + 1
                    if (nextIdx < ttsState.sentences.size) {
                        ttsManager.seekSentence(nextIdx)
                    }
                },
                onPrevSentence = {
                    val prevIdx = ttsState.currentSentenceIndex - 1
                    if (prevIdx >= 0) {
                        ttsManager.seekSentence(prevIdx)
                    }
                },
                onOpenLanguageSelector = { isLanguageSelectorOpen = true },
                onSpeedChanged = { spd -> ttsManager.setSpeechRate(spd) },
                onOpenSettings = { ttsManager.openTtsSettings() },
                onDismiss = {
                    ttsManager.stop()
                    isReadAloudBarVisible = false
                }
            )
        }

        // Bottom Controls Bar
        if (totalPages > 0) {
            Surface(
                color = DarkSurfaceContainer,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Page Scrubber Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${currentPageIndex + 1}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = VioletPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Slider(
                            value = currentPageIndex.toFloat(),
                            onValueChange = { newPage ->
                                loadCurrentPage(newPage.toInt(), autoReadAloud = true)
                            },
                            valueRange = 0f..(totalPages - 1).toFloat(),
                            steps = (totalPages - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = VioletPrimary,
                                activeTrackColor = VioletPrimary,
                                inactiveTrackColor = DarkOutline
                            ),
                            modifier = Modifier.weight(1f).testTag("pdf_page_slider")
                        )

                        Text(
                            text = "$totalPages",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    // Navigation Step Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { loadCurrentPage(currentPageIndex - 1, autoReadAloud = true) },
                            enabled = currentPageIndex > 0,
                            modifier = Modifier.testTag("pdf_prev_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous")
                        }

                        // Reset zoom button
                        if (scale > 1f) {
                            TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) {
                                Text("Reset Zoom (${(scale * 100).toInt()}%)", color = VioletPrimary)
                            }
                        }

                        TextButton(
                            onClick = { loadCurrentPage(currentPageIndex + 1, autoReadAloud = true) },
                            enabled = currentPageIndex < totalPages - 1,
                            modifier = Modifier.testTag("pdf_next_page_button")
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Language and Natural Voice Selector Sheet
    if (isLanguageSelectorOpen) {
        TtsLanguageSelectorDialog(
            currentLanguage = ttsState.selectedLanguage,
            currentVoice = ttsState.selectedNaturalVoice,
            currentEngineType = ttsState.engineType,
            availableLanguages = ttsState.availableLanguages,
            availableVoicesForLanguage = ttsState.availableVoicesForSelectedLanguage,
            onLanguageSelected = { lang ->
                ttsManager.setLanguage(lang)
            },
            onVoiceSelected = { voice ->
                ttsManager.setNaturalVoice(voice)
                isLanguageSelectorOpen = false
            },
            onEngineTypeSelected = { engine ->
                ttsManager.setEngineType(engine)
            },
            onOpenTtsSettings = {
                ttsManager.openTtsSettings()
            },
            onDismiss = { isLanguageSelectorOpen = false }
        )
    }

    // Translation Sheet / Overlay
    if (isTranslationPanelOpen && totalPages > 0) {
        PdfTranslationPanel(
            currentPageIndex = currentPageIndex,
            totalPages = totalPages,
            selectedTargetLanguage = selectedTranslationLanguage,
            onLanguageSelected = { newLang ->
                selectedTranslationLanguage = newLang
                // If text was already translated on this page, re-translate to the newly selected language
                if (translatedText != null && !isTranslating) {
                    executeTranslation(currentPageIndex, autoReadAloud = false)
                }
            },
            isTranslating = isTranslating,
            translationStatusText = translationStatusText,
            translatedText = translatedText,
            isOfflineModel = isTranslatedOffline,
            errorMessage = translationErrorMessage,
            isScannedPage = isScannedPageForTranslation,
            onTranslatePage = {
                executeTranslation(currentPageIndex, autoReadAloud = false)
            },
            onTranslateAndReadAloud = {
                executeTranslation(currentPageIndex, autoReadAloud = true)
            },
            onDismiss = {
                isTranslationPanelOpen = false
            }
        )
    }
}

