package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.platform.VoiceEngineState
import com.example.platform.VoiceTurn
import com.example.ui.components.EditorialBadge
import com.example.ui.components.ErrorBanner
import com.example.ui.components.InfoNoticeBanner
import com.example.ui.components.StudioCard
import com.example.ui.theme.BroadcastAmber
import com.example.ui.theme.CautionGold
import com.example.ui.theme.CautionGoldBg
import com.example.ui.theme.DisputedCrimson
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg
import kotlin.math.sin

@Composable
fun VoiceScreen(
    hasMicPermission: Boolean,
    sessionRunning: Boolean,
    engineState: VoiceEngineState,
    partialTranscript: String,
    rmsLevel: Float,
    isOnline: Boolean,
    turns: List<VoiceTurn>,
    selectedLanguage: AppLanguage,
    useGeminiTts: Boolean,
    errorMessage: String?,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleGeminiTts: (Boolean) -> Unit,
    onStartSession: () -> Unit,
    onInterruptNow: () -> Unit,
    onToggleMute: () -> Unit,
    onEndSession: () -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit
) {
    var micPermissionGranted by remember(hasMicPermission) { mutableStateOf(hasMicPermission) }
    var permissionDeniedOnce by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted = granted
        onPermissionResult(granted)
        if (granted) {
            onStartSession()
        } else {
            permissionDeniedOnce = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Do not leave microphone dangling if user leaves the app
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SHUBHAM AI • ACOUSTIC STUDIO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Real-Time Voice Assistant",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            val (badgeBg, badgeFg) = when (engineState) {
                VoiceEngineState.LISTENING, VoiceEngineState.SPEAKING -> VerifiedEmeraldBg to VerifiedEmerald
                VoiceEngineState.OFFLINE -> CautionGoldBg to CautionGold
                else -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
            }
            EditorialBadge(text = engineState.label, containerColor = badgeBg, contentColor = badgeFg)
        }

        // Language Chips + TTS Engine Switch
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AppLanguage.entries) { lang ->
                FilterChip(
                    selected = selectedLanguage == lang,
                    onClick = { onSelectLanguage(lang) },
                    label = { Text(lang.label) },
                    modifier = Modifier.testTag("voice_lang_${lang.name.lowercase()}")
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (useGeminiTts) "Gemini 3.1 Flash TTS Audio" else "Android Low-Latency TTS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Toggle between lowest-latency on-device speech and cloud Gemini TTS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = useGeminiTts,
                onCheckedChange = onToggleGeminiTts,
                modifier = Modifier.testTag("voice_tts_mode_switch")
            )
        }

        if (!isOnline) {
            InfoNoticeBanner(
                title = "Network Offline — Auto-Reconnect Armed",
                body = "Your voice conversation transcript and context are preserved. Speaking will auto-resume when connectivity returns.",
                icon = Icons.Outlined.WifiOff
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            ErrorBanner(message = errorMessage)
        }

        if (!micPermissionGranted && permissionDeniedOnce) {
            StudioCard {
                Text(
                    text = "Microphone Permission Required",
                    style = MaterialTheme.typography.titleSmall,
                    color = CautionGold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Shubham AI never listens secretly. Grant Microphone access to start two-way Hindi/Hinglish/English voice conversation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.testTag("voice_open_settings_button")
                ) {
                    Text("Open Android Permission Settings")
                }
            }
        }

        // Real-Time Audio Waveform Visualizer Card
        StudioCard(
            elevated = true,
            accentBorder = if (engineState == VoiceEngineState.LISTENING || engineState == VoiceEngineState.SPEAKING) {
                BroadcastAmber.copy(alpha = 0.6f)
            } else null
        ) {
            VoiceWaveformCanvas(
                rmsLevel = if (engineState == VoiceEngineState.SPEAKING) 0.55f else rmsLevel,
                active = engineState == VoiceEngineState.LISTENING || engineState == VoiceEngineState.SPEAKING,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    partialTranscript.isNotBlank() -> "Hearing: \"$partialTranscript\""
                    engineState == VoiceEngineState.SPEAKING -> "Assistant speaking — tap Interrupt anytime to barge in immediately."
                    engineState == VoiceEngineState.LISTENING -> "Speak naturally in ${selectedLanguage.label}..."
                    engineState == VoiceEngineState.THINKING -> "Generating grounded response..."
                    else -> "Tap the Amber Mic button below to start a live two-way voice session."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Live Conversation Transcript
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (turns.isEmpty()) {
                item {
                    StudioCard {
                        Text(
                            text = "Live Conversation Transcript",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "No prerecorded audio is used. Your spoken turns, interruptions, and AI responses appear here with full context preserved across turns.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(turns, key = { it.id }) { turn ->
                    val isUser = turn.role == "user"
                    StudioCard(elevated = !isUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isUser) "YOU (SPOKEN)" else "SHUBHAM AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) VerifiedEmerald else MaterialTheme.colorScheme.primary
                            )
                            if (turn.interrupted) {
                                EditorialBadge(
                                    text = "Interrupted by user",
                                    containerColor = CautionGoldBg,
                                    contentColor = CautionGold
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = turn.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Bottom Controls: Mute | Primary Mic / Interrupt | End Session
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleMute,
                enabled = sessionRunning,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag("voice_mute_button")
            ) {
                Icon(
                    imageVector = if (engineState == VoiceEngineState.MUTED) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Mute or Unmute Microphone",
                    tint = if (engineState == VoiceEngineState.MUTED) DisputedCrimson else MaterialTheme.colorScheme.onSurface
                )
            }

            if (engineState == VoiceEngineState.SPEAKING || engineState == VoiceEngineState.THINKING) {
                Button(
                    onClick = onInterruptNow,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CautionGold,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .height(58.dp)
                        .testTag("voice_interrupt_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PanTool,
                        contentDescription = "Interrupt assistant immediately"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Interrupt & Speak", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        if (micPermissionGranted) {
                            if (sessionRunning) onInterruptNow() else onStartSession()
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(58.dp)
                        .testTag("voice_start_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Start Live Voice Session"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (sessionRunning) "Tap to Speak" else "Start Live Voice",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onEndSession,
                enabled = sessionRunning,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (sessionRunning) DisputedCrimson else MaterialTheme.colorScheme.surfaceContainer)
                    .testTag("voice_end_session_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "End Voice Session",
                    tint = if (sessionRunning) ObsidianBg else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoiceWaveformCanvas(
    rmsLevel: Float,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = if (active) BroadcastAmber else MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier) {
        val barCount = 28
        val spacing = size.width / barCount
        val centerY = size.height / 2f
        val level = if (active) rmsLevel.coerceIn(0.12f, 1f) else 0.08f
        for (i in 0 until barCount) {
            val x = spacing * i + spacing / 2f
            val wave = kotlin.math.abs(sin((i + 1) * 0.55)).toFloat()
            val halfHeight = (size.height * 0.44f * level * (0.35f + 0.65f * wave)).coerceAtLeast(4f)
            drawLine(
                color = barColor,
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = (spacing * 0.45f).coerceAtLeast(3f),
                cap = StrokeCap.Round
            )
        }
    }
}
