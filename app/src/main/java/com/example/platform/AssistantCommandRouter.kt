package com.example.platform

import java.util.Locale

sealed interface AssistantCommand {
    data class Flashlight(val enable: Boolean) : AssistantCommand
    data class SearchYouTube(val query: String) : AssistantCommand
    data object OpenYouTube : AssistantCommand
    data class OpenWebsite(val url: String) : AssistantCommand
    data class OpenInstalledApp(val appNameQuery: String) : AssistantCommand
    data object CheckBattery : AssistantCommand
    data class StartDocumentary(val topicSeed: String) : AssistantCommand
    data object None : AssistantCommand
}

/**
 * Zero-API-cost local intent router for SHUBHAM AI.
 * Intercepts Jarvis-style Android quick actions and PEHLU PROJECT documentary
 * kickoff phrases before calling Gemini, saving free-tier quota and responding instantly.
 */
object AssistantCommandRouter {

    private val FLASHLIGHT_ON_PATTERNS = listOf(
        "flashlight on", "turn on flashlight", "turn on the flashlight",
        "torch on", "turn on torch", "torch jalao", "torch chalu", "flashlight chalu",
        "flashlight jalao", "flash light on"
    )

    private val FLASHLIGHT_OFF_PATTERNS = listOf(
        "flashlight off", "turn off flashlight", "turn off the flashlight",
        "torch off", "turn off torch", "torch band", "flashlight band",
        "torch bujhao", "flash light off"
    )

    private val DOCUMENTARY_REGEX = Regex(
        pattern = """(?i)(?:make|create|start|plan|research|banani|banana|banao)\s+(?:a\s+|ek\s+)?(?:documentary|video|episode|pehlu\s+project)\s*(?:about|on|par|pe)?\s*(.+)"""
    )

    private val YOUTUBE_SEARCH_REGEX = Regex(
        pattern = """(?i)(?:search\s+(?:for\s+)?(.+?)\s+on\s+youtube|search\s+youtube\s+(?:for\s+)?(.+)|youtube\s+(?:pe|par|search)\s+(.+?)(?:\s+search\s*karo|\s+chalao)?$)"""
    )

    private val URL_REGEX = Regex(
        pattern = """(?i)\b(?:open|kholo|visit|go to)\s+(?:(?:website|site|url)\s+)?(https?://\S+|[a-z0-9-]+(?:\.[a-z0-9-]+)+(?:/\S*)?)"""
    )

    private val OPEN_APP_REGEX = Regex(
        pattern = """(?i)^(?:open|launch|start)\s+([a-z0-9\s.+_-]{2,30})$|^([a-z0-9\s.+_-]{2,30})\s+(?:kholo|open\s+karo|chalu\s+karo)$"""
    )

    fun parse(rawInput: String): AssistantCommand {
        val text = rawInput.trim()
        if (text.isEmpty()) return AssistantCommand.None
        val lower = text.lowercase(Locale.US)

        if (FLASHLIGHT_ON_PATTERNS.any { lower.contains(it) }) {
            return AssistantCommand.Flashlight(enable = true)
        }
        if (FLASHLIGHT_OFF_PATTERNS.any { lower.contains(it) }) {
            return AssistantCommand.Flashlight(enable = false)
        }

        if (lower == "open youtube" || lower == "youtube kholo" || lower == "youtube open karo") {
            return AssistantCommand.OpenYouTube
        }

        YOUTUBE_SEARCH_REGEX.find(text)?.let { match ->
            val q = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (q.isNotBlank()) return AssistantCommand.SearchYouTube(q)
        }

        URL_REGEX.find(text)?.let { match ->
            val rawUrl = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (rawUrl.contains(".")) {
                val normalized = if (rawUrl.startsWith("http://", true) || rawUrl.startsWith("https://", true)) {
                    rawUrl
                } else {
                    "https://$rawUrl"
                }
                return AssistantCommand.OpenWebsite(normalized)
            }
        }

        if (lower.contains("battery") && (lower.contains("status") || lower.contains("kitni") || lower.contains("level") || lower.contains("check"))) {
            return AssistantCommand.CheckBattery
        }

        DOCUMENTARY_REGEX.find(text)?.let { match ->
            val seed = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (seed.length >= 3) {
                return AssistantCommand.StartDocumentary(seed)
            }
        }

        OPEN_APP_REGEX.find(text)?.let { match ->
            val appName = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            val cleaned = appName.lowercase(Locale.US)
            if (cleaned.isNotBlank() && cleaned != "flashlight" && cleaned != "torch" && cleaned != "documentary") {
                return if (cleaned == "youtube") AssistantCommand.OpenYouTube
                else AssistantCommand.OpenInstalledApp(appName)
            }
        }

        return AssistantCommand.None
    }
}
