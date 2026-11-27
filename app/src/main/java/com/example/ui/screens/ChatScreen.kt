package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.ChatMessageRecord
import com.example.data.model.CitationItem
import com.example.data.model.LocationSnapshot
import com.example.ui.components.EditorialBadge
import com.example.ui.components.ErrorBanner
import com.example.ui.components.StudioCard
import com.example.ui.theme.CautionGold
import com.example.ui.theme.CautionGoldBg
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SetupSlate
import com.example.ui.theme.SetupSlateBg
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldBg
import com.example.ui.viewmodel.ChatStreamState

@Composable
fun ChatScreen(
    messages: List<ChatMessageRecord>,
    streamState: ChatStreamState,
    selectedLanguage: AppLanguage,
    webSearchEnabled: Boolean,
    pendingDocumentarySeed: String?,
    memoryCount: Int,
    locationSnapshot: LocationSnapshot?,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleWebSearch: (Boolean) -> Unit,
    onLaunchDocumentary: (String) -> Unit,
    onDismissDocumentarySeed: () -> Unit,
    onSendMessage: (String, String?) -> Unit,
    onClearError: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenVoice: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var includeLocation by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streamState) {
        val total = messages.size + if (streamState is ChatStreamState.Streaming) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Header + Language, Quota-Aware Web Search & Context Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SHUBHAM AI • PERSONAL ASSISTANT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (webSearchEnabled) "Web-Grounded Research Chat" else "Fast Lite Assistant + Jarvis",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row {
                    IconButton(
                        onClick = onOpenVoice,
                        modifier = Modifier.testTag("chat_switch_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Switch to Real-Time Voice",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearHistory,
                            modifier = Modifier.testTag("chat_clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Clear Chat History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = webSearchEnabled,
                        onClick = { onToggleWebSearch(!webSearchEnabled) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = "Toggle Web Search Grounding",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                if (webSearchEnabled) "Web Search ON" else "Lite (Fast)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        modifier = Modifier.testTag("chat_web_search_toggle")
                    )
                }
                items(AppLanguage.entries) { lang ->
                    FilterChip(
                        selected = selectedLanguage == lang,
                        onClick = { onSelectLanguage(lang) },
                        label = { Text(lang.label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.testTag("chat_lang_${lang.name.lowercase()}")
                    )
                }
                if (locationSnapshot != null) {
                    item {
                        FilterChip(
                            selected = includeLocation,
                            onClick = { includeLocation = !includeLocation },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = "Include shared location",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    if (includeLocation) "Loc: ${locationSnapshot.areaLabel.take(18)}" else "Add Location",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = if (webSearchEnabled) {
                    "Tier 2 Web Search Grounded • $memoryCount memory note(s) • Jarvis commands run locally (0 AI cost)"
                } else {
                    "Tier 1 Economy Lite (saves quota) • $memoryCount memory note(s) • Jarvis commands run locally (0 AI cost)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // One-tap PEHLU PROJECT Documentary Handoff Card
        if (!pendingDocumentarySeed.isNullOrBlank()) {
            StudioCard(
                elevated = true,
                accentBorder = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MovieCreation,
                        contentDescription = "PEHLU PROJECT Studio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "PEHLU PROJECT Documentary Ready",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Topic: \"$pendingDocumentarySeed\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onLaunchDocumentary(pendingDocumentarySeed) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("chat_launch_documentary_button")
                    ) {
                        Text("Open PEHLU Studio")
                    }
                    OutlinedButton(
                        onClick = onDismissDocumentarySeed,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }

        if (streamState is ChatStreamState.Error) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ErrorBanner(
                    message = streamState.message,
                    onDismiss = onClearError
                )
                if (!streamState.retryPrompt.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            val retry = streamState.retryPrompt
                            onClearError()
                            onSendMessage(retry, null)
                        },
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("chat_retry_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry Last Message")
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && streamState is ChatStreamState.Idle) {
                item {
                    StudioCard(elevated = true) {
                        Text(
                            text = "Chat Naturally or Trigger Jarvis Quick Actions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Ordinary chats run on fast Economy Lite to save quota. Device commands (Flashlight, YouTube, Apps, Battery) execute locally with zero API calls. Toggle Web Search ON when you want live web citations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        val starters = listOf(
                            "Explain quantum computing simply in Hinglish",
                            "Make a documentary about India's semiconductor mission",
                            "Turn flashlight on",
                            "Search YouTube for ISRO Gaganyaan test"
                        )
                        starters.forEach { q ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        val locHint = if (includeLocation && locationSnapshot != null) {
                                            "${locationSnapshot.areaLabel} (${locationSnapshot.latitude}, ${locationSnapshot.longitude})"
                                        } else null
                                        onSendMessage(q, locHint)
                                    }
                            ) {
                                Text(
                                    text = q,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { msg ->
                ChatBubble(
                    isUser = msg.role == "user",
                    content = msg.content,
                    certaintyTag = msg.certaintyTag,
                    citations = msg.parsedCitations(),
                    onOpenUrl = onOpenUrl
                )
            }

            when (streamState) {
                is ChatStreamState.Loading -> {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (webSearchEnabled) "Searching web sources & composing reply..." else "Thinking (Lite mode)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is ChatStreamState.Streaming -> {
                    item {
                        ChatBubble(
                            isUser = false,
                            content = streamState.text + " ▍",
                            certaintyTag = if (streamState.citations.isNotEmpty()) "VERIFIED" else "MIXED",
                            citations = streamState.citations,
                            onOpenUrl = onOpenUrl
                        )
                    }
                }
                else -> Unit
            }
        }

        // Composer Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask in ${selectedLanguage.label}...") },
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )
            Spacer(Modifier.width(8.dp))
            val sending = streamState is ChatStreamState.Loading || streamState is ChatStreamState.Streaming
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !sending) {
                        val locHint = if (includeLocation && locationSnapshot != null) {
                            "${locationSnapshot.areaLabel} (${locationSnapshot.latitude}, ${locationSnapshot.longitude})"
                        } else null
                        onSendMessage(inputText, locHint)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !sending,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    isUser: Boolean,
    content: String,
    certaintyTag: String,
    citations: List<CitationItem>,
    onOpenUrl: (String) -> Unit
) {
    val bg = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.88f else 0.96f)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SHUBHAM AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val (tagBg, tagFg, tagText) = when (certaintyTag) {
                            "VERIFIED" -> Triple(VerifiedEmeraldBg, VerifiedEmerald, "Sourced")
                            "UNCERTAIN" -> Triple(CautionGoldBg, CautionGold, "Contains Uncertainty")
                            else -> Triple(SetupSlateBg, SetupSlate, "Fact / Analysis")
                        }
                        EditorialBadge(text = tagText, containerColor = tagBg, contentColor = tagFg)
                    }
                }
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (citations.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "VERIFIED SOURCES (${citations.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerifiedEmerald
                        )
                        citations.take(6).forEach { cite ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { onOpenUrl(cite.url) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = "Open source link",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = cite.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
