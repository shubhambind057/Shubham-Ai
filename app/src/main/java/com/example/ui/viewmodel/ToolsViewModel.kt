package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.data.model.AppLanguage
import com.example.data.model.AutomationRun
import com.example.data.model.BatteryStatusInfo
import com.example.data.model.IntegrationStatusItem
import com.example.data.model.LocationSnapshot
import com.example.data.model.PackageFormat
import com.example.data.model.ReadinessTier
import com.example.data.model.UserPreferenceProfile
import com.example.data.repository.AnalyticsServiceRepository
import com.example.data.repository.ExternalAnalyticsState
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.example.data.repository.MetaInsightsSnapshot
import com.example.data.repository.YouTubeAnalyticsSnapshot
import com.example.platform.DailyAutomationScheduler
import com.example.platform.DeviceActionResult
import com.example.platform.DeviceControlManager
import com.example.platform.InstalledAppSummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VisionUiState {
    data object Idle : VisionUiState
    data object Analyzing : VisionUiState
    data class Success(val answer: String) : VisionUiState
    data class Error(val message: String) : VisionUiState
}

class ToolsViewModel(
    application: Application,
    private val firestoreRepo: FirestoreRepository,
    private val aiRepo: GeminiAiRepository,
    private val analyticsRepo: AnalyticsServiceRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ToolsVM"
    }

    val deviceManager = DeviceControlManager(application.applicationContext)

    val preferences: StateFlow<UserPreferenceProfile> = firestoreRepo.observePreferences()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UserPreferenceProfile())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferenceProfile())

    val automationRuns: StateFlow<List<AutomationRun>> = firestoreRepo.observeAutomationRuns()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val periodicWorkState: StateFlow<List<WorkInfo>> = DailyAutomationScheduler
        .observePeriodicWork(application.applicationContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _batteryStatus = MutableStateFlow(deviceManager.readBatteryStatus())
    val batteryStatus: StateFlow<BatteryStatusInfo> = _batteryStatus.asStateFlow()

    private val _locationSnapshot = MutableStateFlow<LocationSnapshot?>(null)
    val locationSnapshot: StateFlow<LocationSnapshot?> = _locationSnapshot.asStateFlow()

    private val _locationError = MutableStateFlow<String?>(null)
    val locationError: StateFlow<String?> = _locationError.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppSummary>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppSummary>> = _installedApps.asStateFlow()

    private val _lastDeviceAction = MutableStateFlow<DeviceActionResult?>(null)
    val lastDeviceAction: StateFlow<DeviceActionResult?> = _lastDeviceAction.asStateFlow()

    private val _flashlightOn = MutableStateFlow(false)
    val flashlightOn: StateFlow<Boolean> = _flashlightOn.asStateFlow()

    private val _visionState = MutableStateFlow<VisionUiState>(VisionUiState.Idle)
    val visionState: StateFlow<VisionUiState> = _visionState.asStateFlow()

    private val _youtubeState = MutableStateFlow<ExternalAnalyticsState<YouTubeAnalyticsSnapshot>>(
        ExternalAnalyticsState.NotConfigured(
            serviceName = "YouTube Analytics API",
            missingRequirements = listOf(
                "Google Cloud OAuth 2.0 Client (yt-analytics.readonly) on your backend proxy.",
                "Eligible YouTube channel OAuth consent (never password-based).",
                "Backend Proxy URL configured in Settings."
            ),
            oauthEndpointHint = "https://youtubeanalytics.googleapis.com/v2/reports"
        )
    )
    val youtubeState: StateFlow<ExternalAnalyticsState<YouTubeAnalyticsSnapshot>> = _youtubeState.asStateFlow()

    private val _metaState = MutableStateFlow<ExternalAnalyticsState<MetaInsightsSnapshot>>(
        ExternalAnalyticsState.NotConfigured(
            serviceName = "Meta Graph API (Instagram & Facebook)",
            missingRequirements = listOf(
                "Meta Developer App with approved Instagram Insights & Pages Read Engagement.",
                "Eligible Instagram Creator/Business account linked to a Facebook Page.",
                "Server-side OAuth token exchange via Backend Proxy URL."
            ),
            oauthEndpointHint = "https://graph.facebook.com/v21.0/me/accounts"
        )
    )
    val metaState: StateFlow<ExternalAnalyticsState<MetaInsightsSnapshot>> = _metaState.asStateFlow()

    private val _settingsBanner = MutableStateFlow<String?>(null)
    val settingsBanner: StateFlow<String?> = _settingsBanner.asStateFlow()

    init {
        refreshBattery()
        refreshInstalledApps()
    }

    fun refreshBattery() {
        _batteryStatus.value = deviceManager.readBatteryStatus()
    }

    fun refreshInstalledApps() {
        _installedApps.value = deviceManager.listLaunchableApps()
    }

    fun clearLocationIfRevoked() {
        val hasAny = deviceManager.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            deviceManager.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!hasAny) {
            _locationSnapshot.value = null
            _locationError.value = "Location permission is currently revoked. No location data is accessed."
        }
    }

    fun fetchLocation(usePrecise: Boolean) {
        viewModelScope.launch {
            _locationError.value = null
            deviceManager.fetchCurrentLocation(usePrecise)
                .onSuccess { _locationSnapshot.value = it }
                .onFailure { _locationError.value = it.localizedMessage ?: "Failed to read location." }
        }
    }

    fun recordDeviceAction(result: DeviceActionResult) {
        _lastDeviceAction.value = result
    }

    fun toggleFlashlight(enable: Boolean) {
        val result = deviceManager.setFlashlight(enable)
        if (result.success) {
            _flashlightOn.value = enable
        }
        _lastDeviceAction.value = result
    }

    fun analyzeImage(bitmap: Bitmap, question: String, language: AppLanguage) {
        viewModelScope.launch {
            _visionState.value = VisionUiState.Analyzing
            try {
                val answer = aiRepo.analyzeVisualContent(bitmap, question, language)
                _visionState.value = VisionUiState.Success(answer)
            } catch (e: TimeoutCancellationException) {
                _visionState.value = VisionUiState.Error("Visual analysis timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Vision analysis failed", e)
                _visionState.value = VisionUiState.Error(e.localizedMessage ?: "Image analysis failed.")
            }
        }
    }

    fun resetVision() {
        _visionState.value = VisionUiState.Idle
    }

    fun savePreferencesUpdate(updated: UserPreferenceProfile, feedback: String = "Preferences saved.") {
        viewModelScope.launch {
            firestoreRepo.savePreferences(updated)
                .onSuccess { _settingsBanner.value = feedback }
                .onFailure { _settingsBanner.value = it.localizedMessage ?: "Failed to save settings." }
        }
    }

    fun updateAutomationSchedule(enabled: Boolean, hour: Int, minute: Int, beat: String) {
        val current = preferences.value
        val lang = AppLanguage.fromRaw(current.defaultLanguage)
        val updated = current.copy(
            automationEnabled = enabled,
            automationHour = hour.coerceIn(0, 23),
            automationMinute = minute.coerceIn(0, 59),
            automationBeat = beat.trim().ifBlank { "History, Science & Current Affairs" }
        )
        if (enabled) {
            DailyAutomationScheduler.scheduleDaily(
                context = getApplication(),
                hour = updated.automationHour,
                minute = updated.automationMinute,
                beat = updated.automationBeat,
                language = lang
            )
        } else {
            DailyAutomationScheduler.pauseOrStop(getApplication())
        }
        savePreferencesUpdate(
            updated,
            feedback = if (enabled) {
                "Daily WorkManager research scheduled (~${String.format("%02d:%02d", updated.automationHour, updated.automationMinute)} window, network required)."
            } else {
                "Daily automation paused and pending WorkManager schedule cancelled."
            }
        )
    }

    fun triggerAutomationNow() {
        val current = preferences.value
        DailyAutomationScheduler.triggerRunNow(
            context = getApplication(),
            beat = current.automationBeat.ifBlank { "History, Science & Current Affairs" },
            language = AppLanguage.fromRaw(current.defaultLanguage)
        )
        _settingsBanner.value = "Enqueued immediate background research run via WorkManager."
    }

    fun refreshExternalAnalytics() {
        val proxy = preferences.value.backendProxyUrl
        viewModelScope.launch {
            _youtubeState.value = ExternalAnalyticsState.Loading
            _youtubeState.value = analyticsRepo.fetchYouTubeAnalytics(proxy)
        }
        viewModelScope.launch {
            _metaState.value = ExternalAnalyticsState.Loading
            _metaState.value = analyticsRepo.fetchMetaInsights(proxy)
        }
    }

    fun clearSettingsBanner() {
        _settingsBanner.value = null
    }

    fun buildIntegrationLedger(): List<IntegrationStatusItem> {
        val proxySet = preferences.value.backendProxyUrl.isNotBlank()
        return listOf(
            IntegrationStatusItem(
                name = "Quota-Resilient Dual-Tier Gemini AI",
                tier = ReadinessTier.FIREBASE_READY,
                detail = "Auto-cascades gemini-3.5-flash-lite ↔ gemini-3.8-flash on rate limits + keyless live Wikipedia reference grounding (0 search quota).",
                setupRequirement = "Keyless Firebase AI Logic + App Check; works on Spark free tier without billing."
            ),
            IntegrationStatusItem(
                name = "Optional Scene-Wise AI Voiceover + Device TTS",
                tier = ReadinessTier.FIREBASE_READY,
                detail = "Free on-device TTS preview default + optional scene-wise gemini-3.1-flash-tts-preview WAV generation.",
                setupRequirement = "Keep optional to conserve free-tier quota."
            ),
            IntegrationStatusItem(
                name = "Persistent Google Sign-In & PEHLU Cloud Archive",
                tier = ReadinessTier.FIREBASE_READY,
                detail = "Native Android Firebase Auth session persistence across restarts + per-user Firestore archive.",
                setupRequirement = "Zero-trust security rules verified & deployed."
            ),
            IntegrationStatusItem(
                name = "Wikimedia Commons & Archival Portal Discovery",
                tier = ReadinessTier.READY_NOW,
                detail = "Live keyless Wikimedia Commons + Internet Archive/NASA/LOC links with explicit reuse-permission tags.",
                setupRequirement = "Works immediately with zero API key."
            ),
            IntegrationStatusItem(
                name = "14-Deliverable ZIP, 7-Col Editing Sheet & Jarvis Actions",
                tier = ReadinessTier.READY_NOW,
                detail = "Flashlight, YouTube, Apps, Websites (0 AI cost) + Blueprint, SRT, 10-Point Audit & ZIP export.",
                setupRequirement = "Works immediately on device."
            ),
            IntegrationStatusItem(
                name = "Camera, Mic, Location, Notifications & Call",
                tier = ReadinessTier.PERMISSION_REQUIRED,
                detail = "Explicit runtime Android permissions; zero secret activation; revocable anytime.",
                setupRequirement = "Grant per feature inside the app or manage in Android Settings."
            ),
            IntegrationStatusItem(
                name = "Daily Research Automation (WorkManager)",
                tier = ReadinessTier.PERMISSION_REQUIRED,
                detail = "On-device periodic WorkManager with NetworkType.CONNECTED, 3x backoff retries & failure alerts.",
                setupRequirement = "Inexact timing due to Android Doze/Battery Optimization. Exact server cron requires Cloud Functions + Blaze billing."
            ),
            IntegrationStatusItem(
                name = "Pexels & Pixabay Stock Libraries",
                tier = ReadinessTier.EXTERNAL_SETUP_REQUIRED,
                detail = if (proxySet) "Backend Proxy URL configured — ready to query." else "Zero secrets kept in APK; requires your server-side Backend Proxy URL.",
                setupRequirement = "Host Pexels/Pixabay keys on your backend proxy and set its URL in Settings."
            ),
            IntegrationStatusItem(
                name = "YouTube Analytics & Meta (IG/FB) Insights",
                tier = ReadinessTier.EXTERNAL_SETUP_REQUIRED,
                detail = if (proxySet) "Backend Proxy URL configured — requires completed OAuth consent." else "Official OAuth 2.0 + App Review required; never asks for passwords or shows fake charts.",
                setupRequirement = "Configure Google Cloud OAuth & Meta App Review on your backend proxy."
            )
        )
    }

    fun formatDefaultSummary(profile: UserPreferenceProfile): String {
        val lang = AppLanguage.fromRaw(profile.defaultLanguage).label
        val fmt = PackageFormat.fromRaw(profile.defaultPackageFormat).label
        return "$lang • $fmt"
    }
}
