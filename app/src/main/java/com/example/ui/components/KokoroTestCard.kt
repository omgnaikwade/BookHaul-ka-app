package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.tts.kokoro.DownloadStatus
import com.example.ui.tts.kokoro.KokoroEngine
import com.example.ui.tts.kokoro.KokoroModelManager
import com.example.ui.tts.kokoro.KokoroVoiceCatalog
import kotlinx.coroutines.launch

@Composable
fun KokoroTestCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val modelManager = remember { KokoroModelManager(context) }
    val engine = remember { KokoroEngine(context, modelManager) }

    val modelState by modelManager.state.collectAsState()

    var selectedVoiceId by remember { mutableStateOf("hf_alpha") }
    var testText by remember {
        mutableStateOf("नमस्ते! कोकोरो ऑन-डिवाइस न्यूरल टीटीएस में आपका स्वागत है।")
    }
    var isSynthesizing by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var synthesisStatusMessage by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("kokoro_test_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainer),
        border = BorderStroke(1.dp, VioletPrimary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(VioletPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = VioletPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Kokoro-82M Neural TTS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "100% On-Device Neural Engine (Quantized ONNX)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (modelState.isModelDownloaded) Color(0xFF064E3B) else Color(0xFF3B1E08),
                    border = BorderStroke(
                        1.dp,
                        if (modelState.isModelDownloaded) Color(0xFF059669) else Color(0xFFD97706)
                    )
                ) {
                    Text(
                        text = if (modelState.isModelDownloaded) "Ready" else "Download Needed",
                        color = if (modelState.isModelDownloaded) Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Download Status / Progress
            when (val status = modelState.downloadStatus) {
                is DownloadStatus.Downloading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = status.item,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                            )
                            Text(
                                text = "${(status.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = VioletPrimary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = VioletPrimary,
                            trackColor = DarkOutline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${status.bytesDownloaded / (1024 * 1024)} MB / ${(status.totalBytes / (1024 * 1024)).coerceAtLeast(1)} MB",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                is DownloadStatus.Error -> {
                    Text(
                        text = status.message,
                        color = Color(0xFFF87171),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                is DownloadStatus.Completed -> {
                    Text(
                        text = status.message,
                        color = Color(0xFF34D399),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                else -> Unit
            }

            if (!modelState.isModelDownloaded) {
                // Download Prompt Section
                Text(
                    text = "Kokoro-82M provides state-of-the-art offline neural speech for Hindi & English. Download once (~88 MB) to use offline.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = {
                        scope.launch {
                            modelManager.downloadModelAndInitialVoices(listOf("hf_alpha", "hm_omega"))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_kokoro_model_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Kokoro Model (88 MB)")
                }
            } else {
                // Model is Downloaded - Test & Control Panel
                Text(
                    text = "Select Voice for Testing:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Voice Chips (Hindi & English)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KokoroVoiceCatalog.HINDI_VOICES.forEach { voice ->
                        FilterChip(
                            selected = selectedVoiceId == voice.id,
                            onClick = {
                                selectedVoiceId = voice.id
                                testText = "नमस्ते! कोकोरो ऑन-डिवाइस न्यूरल टीटीएस में आपका स्वागत है।"
                                scope.launch {
                                    if (!modelManager.isVoiceDownloaded(voice.id)) {
                                        modelManager.downloadVoice(voice.id)
                                    }
                                }
                            },
                            label = { Text(voice.id.replace("hf_", "").replace("hm_", "").replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VioletPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Test Text Input
                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    label = { Text("Sample Text to Synthesize", color = TextMuted, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kokoro_sample_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = VioletPrimary,
                        unfocusedBorderColor = DarkOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons (Generate Sample, Stop, Delete)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isSynthesizing = true
                                synthesisStatusMessage = "Synthesizing on-device..."
                                try {
                                    // Ensure voice is cached
                                    if (!modelManager.isVoiceDownloaded(selectedVoiceId)) {
                                        modelManager.downloadVoice(selectedVoiceId)
                                    }
                                    val wavFile = engine.synthesizeToWav(testText, selectedVoiceId)
                                    if (wavFile != null && wavFile.exists()) {
                                        synthesisStatusMessage = "Playing audio sample (${wavFile.length() / 1024} KB)"
                                        isPlaying = true
                                        engine.playAudio(wavFile) {
                                            isPlaying = false
                                            synthesisStatusMessage = "Playback completed"
                                        }
                                    } else {
                                        synthesisStatusMessage = "Synthesis failed"
                                    }
                                } catch (e: Exception) {
                                    synthesisStatusMessage = "Error: ${e.message}"
                                } finally {
                                    isSynthesizing = false
                                }
                            }
                        },
                        enabled = !isSynthesizing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("generate_sample_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSynthesizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synthesizing...")
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate & Play")
                        }
                    }

                    if (isPlaying) {
                        OutlinedButton(
                            onClick = {
                                engine.stopAudio()
                                isPlaying = false
                                synthesisStatusMessage = "Playback stopped"
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFF43F5E))
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop", tint = Color(0xFFF43F5E))
                        }
                    }

                    IconButton(
                        onClick = {
                            modelManager.deleteLocalModel()
                            synthesisStatusMessage = "Local model removed"
                        },
                        modifier = Modifier.testTag("delete_kokoro_model_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Model",
                            tint = TextMuted
                        )
                    }
                }

                synthesisStatusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall.copy(color = VioletPrimary, fontSize = 11.sp)
                    )
                }
            }
        }
    }
}
