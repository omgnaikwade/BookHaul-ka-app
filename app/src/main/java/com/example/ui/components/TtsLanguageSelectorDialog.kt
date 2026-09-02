package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.tts.NaturalVoice
import com.example.ui.tts.NaturalVoiceCatalog
import com.example.ui.tts.TtsEngineType
import com.example.ui.tts.TtsLanguage
import com.example.ui.tts.VoiceGender
import com.example.ui.tts.VoiceProvider
import com.example.ui.tts.kokoro.DownloadStatus
import com.example.ui.tts.kokoro.KokoroModelManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsLanguageSelectorDialog(
    currentLanguage: TtsLanguage,
    currentVoice: NaturalVoice,
    currentEngineType: TtsEngineType,
    availableLanguages: List<TtsLanguage>,
    availableVoicesForLanguage: List<NaturalVoice>,
    onLanguageSelected: (TtsLanguage) -> Unit,
    onVoiceSelected: (NaturalVoice) -> Unit,
    onEngineTypeSelected: (TtsEngineType) -> Unit,
    onOpenTtsSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelManager = remember { KokoroModelManager.getInstance(context) }
    val modelState by modelManager.state.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Voices, 1: All Languages
    var selectedVoiceFilter by remember { mutableStateOf("ALL") } // "ALL", "NEURAL", "SYSTEM", "MALE", "FEMALE"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header Row
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
                            .size(36.dp)
                            .background(VioletPrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = VioletPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Text-to-Speech & Voice Settings",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Hybrid: Offline Neural (Hindi/English) + System (All Languages)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF34D399),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Slide Down Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("tts_lang_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Slide down",
                            tint = TextSecondary
                        )
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("tts_lang_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Engine Mode Selector
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DarkOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Hybrid Neural Voice Option (Hindi / English Offline)
                    val isNeural = currentEngineType == TtsEngineType.NATURAL_NEURAL
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEngineTypeSelected(TtsEngineType.NATURAL_NEURAL) }
                            .testTag("tts_engine_neural_button"),
                        color = if (isNeural) VioletPrimary.copy(alpha = 0.25f) else Color.Transparent,
                        border = if (isNeural) BorderStroke(1.dp, VioletPrimary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (isNeural) VioletPrimary else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🧠 Hybrid Neural",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isNeural) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isNeural) Color.White else TextSecondary
                                )
                            )
                        }
                    }

                    // System Offline Option (All Languages)
                    val isSystem = currentEngineType == TtsEngineType.SYSTEM_OFFLINE
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEngineTypeSelected(TtsEngineType.SYSTEM_OFFLINE) }
                            .testTag("tts_engine_system_button"),
                        color = if (isSystem) DarkSurfaceVariant else Color.Transparent,
                        border = if (isSystem) BorderStroke(1.dp, DarkOutline) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                tint = if (isSystem) TextPrimary else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "📱 Device System TTS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSystem) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSystem) TextPrimary else TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Offline Neural Model Status Banner
            if (!modelState.isModelDownloaded) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Offline Neural Models (~88 MB)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    Text(
                                        text = "Download once for 100% offline Hindi & English neural speech.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            when (val status = modelState.downloadStatus) {
                                is DownloadStatus.Downloading -> {
                                    CircularProgressIndicator(
                                        progress = { status.progress },
                                        color = VioletPrimary,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                                else -> {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                modelManager.downloadModel()
                                                modelManager.downloadConfig()
                                                modelManager.downloadVoice(currentVoice.voiceId)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (modelState.downloadStatus is DownloadStatus.Downloading) {
                            val status = modelState.downloadStatus as DownloadStatus.Downloading
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = VioletPrimary,
                                trackColor = DarkOutline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${(status.progress * 100).toInt()}% • ${(status.bytesDownloaded / 1_000_000)} MB / ${(status.totalBytes / 1_000_000)} MB",
                                style = MaterialTheme.typography.labelSmall.copy(color = VioletPrimary, fontSize = 10.sp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tabs for Voices vs Languages
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurfaceContainer,
                contentColor = VioletPrimary,
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = VioletPrimary
                    )
                },
                divider = {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DarkOutline))
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Voices (${currentLanguage.displayName})",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) VioletPrimary else TextSecondary
                            )
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "All Languages (${availableLanguages.size})",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) VioletPrimary else TextSecondary
                            )
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // Tab 0: Voices for Currently Selected Language
                val allVoices = if (availableVoicesForLanguage.isNotEmpty()) availableVoicesForLanguage else NaturalVoiceCatalog.getVoicesForLanguage(currentLanguage.code)
                val filteredVoices = allVoices.filter { voice ->
                    when (selectedVoiceFilter) {
                        "NEURAL" -> voice.provider == VoiceProvider.LOCAL_NEURAL
                        "SYSTEM" -> voice.provider == VoiceProvider.SYSTEM_TTS
                        "MALE" -> voice.gender == VoiceGender.MALE
                        "FEMALE" -> voice.gender == VoiceGender.FEMALE
                        else -> true
                    }
                }

                // Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filterOptions = listOf(
                        "ALL" to "All",
                        "NEURAL" to "Neural 🧠",
                        "SYSTEM" to "System 📱",
                        "FEMALE" to "Female ♀",
                        "MALE" to "Male ♂"
                    )

                    filterOptions.forEach { (key, label) ->
                        val isFilterActive = selectedVoiceFilter == key
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedVoiceFilter = key },
                            color = if (isFilterActive) VioletPrimary.copy(alpha = 0.3f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isFilterActive) VioletPrimary else DarkOutline)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isFilterActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isFilterActive) Color.White else TextSecondary,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredVoices, key = { it.voiceId }) { voice ->
                        val isSelected = voice.voiceId == currentVoice.voiceId
                        val isNeural = voice.provider == VoiceProvider.LOCAL_NEURAL

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onVoiceSelected(voice)
                                    if (isNeural && !modelManager.isVoiceDownloaded(voice.voiceId)) {
                                        scope.launch {
                                            modelManager.downloadVoice(voice.voiceId)
                                        }
                                    }
                                }
                                .testTag("tts_voice_item_${voice.voiceId}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) VioletPrimary.copy(alpha = 0.18f) else DarkSurface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) VioletPrimary else DarkOutline
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (voice.gender == VoiceGender.FEMALE) Color(0xFFEC4899).copy(alpha = 0.2f) else Color(0xFF3B82F6).copy(alpha = 0.2f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (voice.gender == VoiceGender.FEMALE) "♀" else "♂",
                                            fontWeight = FontWeight.Bold,
                                            color = if (voice.gender == VoiceGender.FEMALE) Color(0xFFF472B6) else Color(0xFF60A5FA),
                                            fontSize = 16.sp
                                        )
                                    }

                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = voice.speakerName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (isSelected) Color.White else TextPrimary
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isNeural) {
                                                Surface(
                                                    color = Color(0xFF7C3AED).copy(alpha = 0.35f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(0.5.dp, Color(0xFFA78BFA))
                                                ) {
                                                    Text(
                                                        text = "🧠 Neural Offline",
                                                        color = Color(0xFFDDD6FE),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    color = Color(0xFF0F766E).copy(alpha = 0.35f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(0.5.dp, Color(0xFF2DD4BF))
                                                ) {
                                                    Text(
                                                        text = "📱 System TTS",
                                                        color = Color(0xFF99F6E4),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = voice.description,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(VioletPrimary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: All Languages List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableLanguages, key = { it.code }) { lang ->
                        val isSelected = lang.code == currentLanguage.code
                        val isNeuralSupported = lang.code.startsWith("hi", ignoreCase = true) || lang.code.startsWith("en", ignoreCase = true)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLanguageSelected(lang)
                                    selectedTab = 0
                                }
                                .testTag("tts_lang_item_${lang.code}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) VioletPrimary.copy(alpha = 0.15f) else DarkSurface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) VioletPrimary else DarkOutline
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = lang.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else TextPrimary
                                            )
                                        )
                                        if (lang.nativeName != lang.displayName) {
                                            Text(
                                                text = "(${lang.nativeName})",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = VioletPrimary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (isNeuralSupported) {
                                        Text(
                                            text = "🧠 Neural Offline Voice Supported",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF34D399),
                                                fontSize = 11.sp
                                            )
                                        )
                                    } else {
                                        Text(
                                            text = "📱 Android System TTS Engine",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF60A5FA),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(VioletPrimary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Device TTS Settings CTA
            OutlinedButton(
                onClick = onOpenTtsSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tts_open_system_settings_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkOutline)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Android System TTS Voice Settings",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
