package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.tts.TtsEngineType
import com.example.ui.tts.TtsPlaybackState
import com.example.ui.tts.TtsUiState
import com.example.ui.tts.VoiceGender

@Composable
fun PdfReadAloudBar(
    state: TtsUiState,
    currentPage: Int,
    totalPages: Int,
    isExtracting: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNextSentence: () -> Unit,
    onPrevSentence: () -> Unit,
    onOpenLanguageSelector: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSpeedMenuOpen by remember { mutableStateOf(false) }
    var isMinimized by remember { mutableStateOf(false) }
    val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    val isPlaying = state.playbackState == TtsPlaybackState.PLAYING
    val isMale = state.selectedNaturalVoice.gender == VoiceGender.MALE

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .animateContentSize(animationSpec = tween(250)),
        color = DarkSurfaceContainer,
        shape = RoundedCornerShape(if (isMinimized) 24.dp else 18.dp),
        border = BorderStroke(1.dp, if (isPlaying) VioletPrimary.copy(alpha = 0.5f) else DarkOutline),
        shadowElevation = 8.dp
    ) {
        AnimatedContent(
            targetState = isMinimized,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "read_aloud_bar_content"
        ) { minimized ->
            if (minimized) {
                // Compact / Minimized Floating Pill Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Indicator (clickable to change voice/language)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clickable(onClick = onOpenLanguageSelector)
                            .testTag("tts_minimized_voice_chip")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (isMale) Color(0xFF3B82F6).copy(alpha = 0.25f) else Color(0xFFEC4899).copy(alpha = 0.25f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isMale) "♂" else "♀",
                                color = if (isMale) Color(0xFF60A5FA) else Color(0xFFF472B6),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Column {
                            Text(
                                text = state.selectedNaturalVoice.speakerName.split(" ").first(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 12.sp
                                )
                            )
                            val sentenceProgress = if (state.sentences.isNotEmpty()) {
                                "${state.currentSentenceIndex + 1}/${state.sentences.size}"
                            } else "..."
                            Text(
                                text = sentenceProgress,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = VioletPrimary,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    // Playback Controls in Mini Mode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Previous
                        IconButton(
                            onClick = onPrevSentence,
                            enabled = state.sentences.isNotEmpty() && state.currentSentenceIndex > 0,
                            modifier = Modifier.size(32.dp).testTag("tts_mini_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous sentence",
                                tint = if (state.currentSentenceIndex > 0) TextPrimary else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Play/Pause / Buffering
                        if (isExtracting || state.isBufferingSentence) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(VioletPrimary.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = VioletPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(VioletPrimary, CircleShape)
                                    .clickable { if (isPlaying) onPause() else onPlay() }
                                    .testTag("tts_mini_play_pause_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Next
                        IconButton(
                            onClick = onNextSentence,
                            enabled = state.sentences.isNotEmpty() && state.currentSentenceIndex < state.sentences.size - 1,
                            modifier = Modifier.size(32.dp).testTag("tts_mini_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next sentence",
                                tint = if (state.currentSentenceIndex < state.sentences.size - 1) TextPrimary else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Expand Button (Slide Up)
                        IconButton(
                            onClick = { isMinimized = false },
                            modifier = Modifier.size(32.dp).testTag("tts_expand_bar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Expand controls",
                                tint = VioletPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp).testTag("tts_mini_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Read Aloud",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                // Full Expanded Mode
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Header Row: Title, Page indicator, Minimize & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        if (state.isTranslated) Color(0xFF10B981).copy(alpha = 0.2f) else VioletPrimary.copy(alpha = 0.2f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (state.engineType == TtsEngineType.NATURAL_NEURAL) Icons.Default.RecordVoiceOver else Icons.Default.VolumeUp,
                                    contentDescription = if (state.isTranslated) "Translated Read Aloud" else "Read Aloud",
                                    tint = if (state.isTranslated) Color(0xFF34D399) else VioletPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = state.customLabel ?: "Read Aloud (Page ${currentPage + 1})",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    if (state.engineType == TtsEngineType.NATURAL_NEURAL) {
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "✨ Natural",
                                                color = Color(0xFF34D399),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                val statusText = when {
                                    isExtracting -> "Extracting page text..."
                                    state.isBufferingSentence -> "Preparing natural voice..."
                                    state.playbackState == TtsPlaybackState.PLAYING -> "Reading sentence ${state.currentSentenceIndex + 1} of ${state.sentences.size}"
                                    state.playbackState == TtsPlaybackState.PAUSED -> "Paused at sentence ${state.currentSentenceIndex + 1} of ${state.sentences.size}"
                                    state.playbackState == TtsPlaybackState.COMPLETED -> "Page readout finished"
                                    state.isScannedPage -> "Scanned / Image page"
                                    state.isTranslated -> "Speaking in ${state.selectedNaturalVoice.speakerName} (${state.selectedLanguage.displayName})"
                                    state.engineType == TtsEngineType.NATURAL_NEURAL -> "Voice: ${state.selectedNaturalVoice.speakerName} (${state.selectedLanguage.displayName})"
                                    else -> "Device Offline TTS (${state.selectedLanguage.displayName})"
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (state.isScannedPage) Color(0xFFFBBF24) else TextSecondary,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Voice / Language Selector Chip Button
                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, DarkOutline),
                                modifier = Modifier
                                    .clickable(onClick = onOpenLanguageSelector)
                                    .testTag("tts_language_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isMale) "♂" else "♀",
                                        color = if (isMale) Color(0xFF60A5FA) else Color(0xFFF472B6),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    val label = if (state.engineType == TtsEngineType.NATURAL_NEURAL) {
                                        state.selectedNaturalVoice.speakerName.split(" ").first()
                                    } else {
                                        state.selectedLanguage.displayName.take(8)
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Speed Chip Button
                            Box {
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, DarkOutline),
                                    modifier = Modifier
                                        .clickable { isSpeedMenuOpen = true }
                                        .testTag("tts_speed_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${state.speechRate}x",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = isSpeedMenuOpen,
                                    onDismissRequest = { isSpeedMenuOpen = false },
                                    modifier = Modifier.background(DarkSurfaceContainer)
                                ) {
                                    speedOptions.forEach { spd ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${spd}x",
                                                    color = if (state.speechRate == spd) VioletPrimary else TextPrimary,
                                                    fontWeight = if (state.speechRate == spd) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                onSpeedChanged(spd)
                                                isSpeedMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Slide Down / Minimize Button
                            IconButton(
                                onClick = { isMinimized = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("tts_minimize_bar_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Slide down / Minimize to pill",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Close Bar Button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("tts_close_bar_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Read Aloud",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Warning Banner for Scanned PDFs
                    if (state.isScannedPage) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF332A15),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "This page contains scanned images or non-selectable text. Read Aloud works with digital text-based pages.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFFDE68A),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }

                    // Warning Banner for Error
                    if (!state.errorMessage.isNullOrBlank() && !state.isScannedPage) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = state.errorMessage ?: "Voice error occurred.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.testTag("tts_fix_voice_button")
                                ) {
                                    Text("Settings", color = VioletPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Extracted text preview snippet (showing active sentence)
                    if (state.sentences.isNotEmpty() && !state.isScannedPage) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val currentText = state.sentences.getOrNull(state.currentSentenceIndex) ?: ""
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DarkOutline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "\"$currentText\"",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextPrimary,
                                    lineHeight = 16.sp,
                                    fontSize = 12.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        // Progress Bar
                        Spacer(modifier = Modifier.height(6.dp))
                        val progress = if (state.sentences.isNotEmpty()) {
                            (state.currentSentenceIndex + 1).toFloat() / state.sentences.size.toFloat()
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = VioletPrimary,
                            trackColor = DarkOutline
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Main Playback Controls: Prev | Play/Pause | Next | Stop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Sentence
                        IconButton(
                            onClick = onPrevSentence,
                            enabled = state.sentences.isNotEmpty() && state.currentSentenceIndex > 0,
                            modifier = Modifier.testTag("tts_prev_sentence_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous sentence",
                                tint = if (state.currentSentenceIndex > 0) TextPrimary else TextMuted
                            )
                        }

                        // Play / Pause / Loading
                        if (isExtracting || state.isBufferingSentence) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(VioletPrimary.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = VioletPrimary,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(VioletPrimary, CircleShape)
                                    .clickable {
                                        if (isPlaying) onPause() else onPlay()
                                    }
                                    .testTag("tts_play_pause_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Next Sentence
                        IconButton(
                            onClick = onNextSentence,
                            enabled = state.sentences.isNotEmpty() && state.currentSentenceIndex < state.sentences.size - 1,
                            modifier = Modifier.testTag("tts_next_sentence_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next sentence",
                                tint = if (state.currentSentenceIndex < state.sentences.size - 1) TextPrimary else TextMuted
                            )
                        }

                        // Stop Button
                        IconButton(
                            onClick = onStop,
                            enabled = state.playbackState == TtsPlaybackState.PLAYING || state.playbackState == TtsPlaybackState.PAUSED,
                            modifier = Modifier.testTag("tts_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = if (state.playbackState == TtsPlaybackState.PLAYING || state.playbackState == TtsPlaybackState.PAUSED) Color(0xFFEF4444) else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
