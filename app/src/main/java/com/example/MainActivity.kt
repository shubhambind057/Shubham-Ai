package com.example

import android.Manifest
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AuthGateState
import com.example.core.AuthManager
import com.example.core.configureAppCheck
import com.example.data.model.AppLanguage
import com.example.data.model.PackageFormat
import com.example.data.repository.AnalyticsServiceRepository
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.example.data.repository.StockMediaRepository
import com.example.platform.ensureNotificationChannel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.screens.ToolsSubScreen
import com.example.ui.screens.VoiceScreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ShubhamAiTheme
import com.example.ui.viewmodel.AssistantViewModel
import com.example.ui.viewmodel.StudioSubTab
import com.example.ui.viewmodel.StudioViewModel
import com.example.ui.viewmodel.ToolsViewModel
import com.google.firebase.auth.FirebaseUser

enum class MainNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    STUDIO("Studio", Icons.Filled.MovieCreation, Icons.Outlined.MovieCreation),
    VOICE("Voice", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    CHAT("Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    TOOLS("Tools", Icons.Filled.Build, Icons.Outlined.Build)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // CRITICAL: Configure Firebase App Check BEFORE any Firebase AI / Firestore / ViewModel calls
        configureAppCheck(this, intent)
        ensureNotificationChannel(this)

        setContent {
            ShubhamAiTheme {
                ShubhamAiRoot(application = application)
            }
        }
    }
}

@Composable
private fun ShubhamAiRoot(application: Application) {
    val authFlow = remember { AuthManager.observePersistentSession() }
    val authState by authFlow.collectAsStateWithLifecycle(initialValue = AuthGateState.Checking)

    when (val state = authState) {
        is AuthGateState.Checking -> {
            // Calm branded splash while FirebaseAuth resolves persisted local session (zero login flicker)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg)
                    .testTag("auth_session_checking_splash"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.shubham_ai_icon_fg_1795728889843),
                        contentDescription = stringResource(R.string.cd_app_emblem),
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "PEHLU PROJECT × SHUBHAM AI",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        is AuthGateState.Unauthenticated -> {
            AuthScreen(
                sessionNotice = state.sessionNotice,
                onAuthSuccess = {
                    // AuthStateListener automatically transitions authState to Authenticated
                }
            )
        }

        is AuthGateState.Authenticated -> {
            key(state.user.uid) {
                AuthenticatedShubhamApp(
                    application = application,
                    user = state.user
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedShubhamApp(
    application: Application,
    user: FirebaseUser
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val firestoreRepo = remember(user.uid) { FirestoreRepository(application.applicationContext) }
    val aiRepo = remember(user.uid) { GeminiAiRepository() }
    val stockRepo = remember(user.uid) { StockMediaRepository() }
    val analyticsRepo = remember(user.uid) { AnalyticsServiceRepository() }

    val assistantVm: AssistantViewModel = viewModel(
        key = "assistant_${user.uid}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AssistantViewModel(application, firestoreRepo, aiRepo) as T
            }
        }
    )

    val studioVm: StudioViewModel = viewModel(
        key = "studio_${user.uid}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StudioViewModel(application, firestoreRepo, aiRepo, stockRepo) as T
            }
        }
    )

    val toolsVm: ToolsViewModel = viewModel(
        key = "tools_${user.uid}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ToolsViewModel(application, firestoreRepo, aiRepo, analyticsRepo) as T
            }
        }
    )

    var selectedTab by rememberSaveable { mutableStateOf(MainNavTab.HOME) }
    var toolsSubScreen by rememberSaveable { mutableStateOf(ToolsSubScreen.HUB) }

    // Back from secondary top-level tabs returns cleanly to Home
    BackHandler(enabled = selectedTab != MainNavTab.HOME && !(selectedTab == MainNavTab.TOOLS && toolsSubScreen != ToolsSubScreen.HUB)) {
        selectedTab = MainNavTab.HOME
    }

    // Collect ViewModel flows
    val preferences by toolsVm.preferences.collectAsStateWithLifecycle()
    val batteryStatus by toolsVm.batteryStatus.collectAsStateWithLifecycle()
    val flashlightOn by toolsVm.flashlightOn.collectAsStateWithLifecycle()
    val locationSnapshot by toolsVm.locationSnapshot.collectAsStateWithLifecycle()
    val locationError by toolsVm.locationError.collectAsStateWithLifecycle()
    val installedApps by toolsVm.installedApps.collectAsStateWithLifecycle()
    val lastDeviceAction by toolsVm.lastDeviceAction.collectAsStateWithLifecycle()
    val visionState by toolsVm.visionState.collectAsStateWithLifecycle()
    val periodicWorkState by toolsVm.periodicWorkState.collectAsStateWithLifecycle()
    val automationRuns by toolsVm.automationRuns.collectAsStateWithLifecycle()
    val youtubeState by toolsVm.youtubeState.collectAsStateWithLifecycle()
    val metaState by toolsVm.metaState.collectAsStateWithLifecycle()
    val settingsBanner by toolsVm.settingsBanner.collectAsStateWithLifecycle()

    val selectedLanguage by assistantVm.selectedLanguage.collectAsStateWithLifecycle()
    val chatWebSearchEnabled by assistantVm.chatWebSearchEnabled.collectAsStateWithLifecycle()
    val pendingDocumentarySeed by assistantVm.pendingDocumentarySeed.collectAsStateWithLifecycle()
    val chatMessages by assistantVm.chatMessages.collectAsStateWithLifecycle()
    val chatStreamState by assistantVm.chatStreamState.collectAsStateWithLifecycle()
    val voiceTurns by assistantVm.voiceTurns.collectAsStateWithLifecycle()
    val voiceSessionRunning by assistantVm.voiceSessionRunning.collectAsStateWithLifecycle()
    val useGeminiTts by assistantVm.useGeminiTtsForVoice.collectAsStateWithLifecycle()
    val voiceAiError by assistantVm.voiceAiError.collectAsStateWithLifecycle()
    val voiceEngineState by assistantVm.voiceController.engineState.collectAsStateWithLifecycle()
    val voicePartial by assistantVm.voiceController.partialTranscript.collectAsStateWithLifecycle()
    val voiceRms by assistantVm.voiceController.rmsLevel.collectAsStateWithLifecycle()
    val voiceOnline by assistantVm.voiceController.isOnline.collectAsStateWithLifecycle()
    val voiceControllerErr by assistantVm.voiceController.errorBanner.collectAsStateWithLifecycle()

    val studioSubTab by studioVm.activeSubTab.collectAsStateWithLifecycle()
    val projects by studioVm.projects.collectAsStateWithLifecycle()
    val selectedProjectId by studioVm.selectedProjectId.collectAsStateWithLifecycle()
    val discoveredTopics by studioVm.discoveredTopics.collectAsStateWithLifecycle()
    val stockResult by studioVm.stockResult.collectAsStateWithLifecycle()
    val archivalPortals by studioVm.archivalPortals.collectAsStateWithLifecycle()
    val clipSearchQuerySeed by studioVm.clipSearchQuerySeed.collectAsStateWithLifecycle()
    val clipTargetSceneNumber by studioVm.clipTargetSceneNumber.collectAsStateWithLifecycle()
    val clipTargetTimestamp by studioVm.clipTargetTimestamp.collectAsStateWithLifecycle()
    val selectedVoice by studioVm.selectedVoice.collectAsStateWithLifecycle()
    val studioBusy by studioVm.isBusy.collectAsStateWithLifecycle()
    val studioBusyLabel by studioVm.busyLabel.collectAsStateWithLifecycle()
    val studioStatus by studioVm.statusBanner.collectAsStateWithLifecycle()
    val studioError by studioVm.errorMessage.collectAsStateWithLifecycle()

    val activeProject = remember(projects, selectedProjectId) {
        if (selectedProjectId != null) projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()
        else projects.firstOrNull()
    }
    val defaultPackageFormat = remember(preferences.defaultPackageFormat) {
        PackageFormat.fromRaw(preferences.defaultPackageFormat)
    }
    val integrationLedger = remember(preferences.backendProxyUrl) {
        toolsVm.buildIntegrationLedger()
    }
    val hasFlashlightHardware = remember(toolsVm) {
        toolsVm.deviceManager.hasFlashlightHardware()
    }

    val openUrlSafe: (String) -> Unit = { url ->
        toolsVm.recordDeviceAction(toolsVm.deviceManager.openUrl(url))
    }

    val navContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Box(modifier = contentModifier.background(ObsidianBg)) {
            when (selectedTab) {
                MainNavTab.HOME -> HomeScreen(
                    userName = user.displayName.orEmpty(),
                    preferences = preferences,
                    projects = projects,
                    battery = batteryStatus,
                    flashlightOn = flashlightOn,
                    hasFlashlightHardware = hasFlashlightHardware,
                    onToggleFlashlight = { toolsVm.toggleFlashlight(it) },
                    onStartTodaysResearch = {
                        selectedTab = MainNavTab.STUDIO
                        studioVm.startTodaysResearch(
                            beat = preferences.automationBeat.ifBlank { "History, Science & Current Affairs" },
                            language = selectedLanguage
                        )
                    },
                    onOpenProject = { id ->
                        studioVm.selectProject(id, StudioSubTab.RESEARCH)
                        selectedTab = MainNavTab.STUDIO
                    },
                    onNavigateVoice = { selectedTab = MainNavTab.VOICE },
                    onNavigateChat = { selectedTab = MainNavTab.CHAT },
                    onNavigateStudio = { selectedTab = MainNavTab.STUDIO },
                    onNavigateCamera = {
                        toolsSubScreen = ToolsSubScreen.CAMERA
                        selectedTab = MainNavTab.TOOLS
                    },
                    onNavigatePhoneControls = {
                        toolsSubScreen = ToolsSubScreen.PHONE_CONTROLS
                        selectedTab = MainNavTab.TOOLS
                    },
                    onNavigateServices = {
                        toolsSubScreen = ToolsSubScreen.SERVICES
                        selectedTab = MainNavTab.TOOLS
                    },
                    onNavigateSettings = {
                        toolsSubScreen = ToolsSubScreen.SETTINGS
                        selectedTab = MainNavTab.TOOLS
                    }
                )

                MainNavTab.STUDIO -> StudioScreen(
                    activeSubTab = studioSubTab,
                    projects = projects,
                    activeProject = activeProject,
                    discoveredTopics = discoveredTopics,
                    stockResult = stockResult,
                    archivalPortals = archivalPortals,
                    clipSearchQuerySeed = clipSearchQuerySeed,
                    clipTargetSceneNumber = clipTargetSceneNumber,
                    clipTargetTimestamp = clipTargetTimestamp,
                    selectedLanguage = selectedLanguage,
                    defaultPackageFormat = defaultPackageFormat,
                    economyMode = preferences.economyModeEnabled,
                    backendProxyUrl = preferences.backendProxyUrl,
                    selectedVoice = selectedVoice,
                    isBusy = studioBusy,
                    busyLabel = studioBusyLabel,
                    statusBanner = studioStatus,
                    errorMessage = studioError,
                    onSelectSubTab = { studioVm.selectSubTab(it) },
                    onSelectProject = { id, tab -> studioVm.selectProject(id, tab) },
                    onStartTodaysResearch = { beat, lang, focus, loadMore ->
                        studioVm.startTodaysResearch(beat, lang, focus, loadMore)
                    },
                    onApproveCandidate = { cand, lang, fmt ->
                        studioVm.approveTopicCandidate(cand, lang, fmt)
                    },
                    onApproveProjectTopic = { studioVm.approveExistingProjectTopic(it) },
                    onRunFactCheck = { studioVm.runFactCheckForActiveProject(it) },
                    onGenerateScripts = { studioVm.generateDualScriptsAndCapCut(it, preferences.economyModeEnabled) },
                    onGenerateBlueprint = { studioVm.generateCompleteVideoBlueprint(it, preferences.economyModeEnabled) },
                    onSaveScripts = { id, shortS, longS, vis ->
                        studioVm.saveEditedScripts(id, shortS, longS, vis)
                    },
                    onOpenClipFinderForScene = { scNum, ts, kw ->
                        studioVm.openClipFinderForScene(scNum, ts, kw)
                    },
                    onSearchStock = { provider, query, proxy ->
                        studioVm.searchStockMedia(provider, query, proxy)
                    },
                    onAttachFootage = { proj, asset -> studioVm.attachFootageAsset(proj, asset) },
                    onSelectVoice = { studioVm.setVoiceOption(it) },
                    onGenerateVoiceover = { proj, useShort -> studioVm.generateAiVoiceover(proj, useShort) },
                    onGenerateSceneVoiceover = { proj, scNum -> studioVm.generateSceneVoiceover(proj, scNum) },
                    onPreviewAudioFile = { file -> assistantVm.voiceController.playWavFile(file) },
                    onPreviewDeviceTts = { text, lang -> assistantVm.voiceController.speakText(text, lang) },
                    onStopAudioPreview = { assistantVm.voiceController.stopPlaybackImmediately() },
                    onSaveSubtitlesAndCapCut = { id, srt, plan, checklist, auditChecklist ->
                        studioVm.saveSubtitlesAndCapCut(id, srt, plan, checklist, auditChecklist)
                    },
                    onSetFinalApproved = { proj, approved -> studioVm.setFinalContentApproved(proj, approved) },
                    onExportZip = { proj, onReady -> studioVm.buildZipPackageForShare(proj, onReady) },
                    onDeleteProject = { studioVm.deleteProject(it) },
                    onOpenUrl = openUrlSafe,
                    onClearAlerts = { studioVm.clearAlerts() }
                )

                MainNavTab.VOICE -> VoiceScreen(
                    hasMicPermission = toolsVm.deviceManager.hasPermission(Manifest.permission.RECORD_AUDIO),
                    sessionRunning = voiceSessionRunning,
                    engineState = voiceEngineState,
                    partialTranscript = voicePartial,
                    rmsLevel = voiceRms,
                    isOnline = voiceOnline,
                    turns = voiceTurns,
                    selectedLanguage = selectedLanguage,
                    useGeminiTts = useGeminiTts,
                    errorMessage = voiceAiError ?: voiceControllerErr,
                    onSelectLanguage = { assistantVm.setLanguage(it) },
                    onToggleGeminiTts = { assistantVm.toggleVoiceTtsMode(it) },
                    onStartSession = { assistantVm.startVoiceSession() },
                    onInterruptNow = { assistantVm.interruptAssistantNow() },
                    onToggleMute = { assistantVm.toggleVoiceMute() },
                    onEndSession = { assistantVm.endVoiceSession() },
                    onPermissionResult = { /* handled via live check */ },
                    onOpenAppSettings = { toolsVm.recordDeviceAction(toolsVm.deviceManager.openAppSettings()) }
                )

                MainNavTab.CHAT -> ChatScreen(
                    messages = chatMessages,
                    streamState = chatStreamState,
                    selectedLanguage = selectedLanguage,
                    webSearchEnabled = chatWebSearchEnabled,
                    pendingDocumentarySeed = pendingDocumentarySeed,
                    memoryCount = preferences.memoryNotes.size,
                    locationSnapshot = locationSnapshot,
                    onSelectLanguage = { assistantVm.setLanguage(it) },
                    onToggleWebSearch = { assistantVm.setChatWebSearchEnabled(it) },
                    onLaunchDocumentary = { seed ->
                        assistantVm.clearPendingDocumentarySeed()
                        selectedTab = MainNavTab.STUDIO
                        studioVm.startTodaysResearch(
                            beat = "Unexplored Perspectives",
                            language = selectedLanguage,
                            customFocus = seed
                        )
                    },
                    onDismissDocumentarySeed = { assistantVm.clearPendingDocumentarySeed() },
                    onSendMessage = { prompt, locHint -> assistantVm.sendChatMessage(prompt, locHint) },
                    onClearError = { assistantVm.clearChatError() },
                    onClearHistory = { assistantVm.clearChatHistory() },
                    onOpenUrl = openUrlSafe,
                    onOpenVoice = { selectedTab = MainNavTab.VOICE }
                )

                MainNavTab.TOOLS -> ToolsHubScreen(
                    activeSubScreen = toolsSubScreen,
                    onSelectSubScreen = { toolsSubScreen = it },
                    userName = user.displayName.orEmpty(),
                    userEmail = user.email.orEmpty(),
                    selectedLanguage = selectedLanguage,
                    preferences = preferences,
                    deviceManager = toolsVm.deviceManager,
                    batteryStatus = batteryStatus,
                    flashlightOn = flashlightOn,
                    locationSnapshot = locationSnapshot,
                    locationError = locationError,
                    installedApps = installedApps,
                    lastAction = lastDeviceAction,
                    visionState = visionState,
                    periodicWorkState = periodicWorkState,
                    automationRuns = automationRuns,
                    youtubeState = youtubeState,
                    metaState = metaState,
                    integrationLedger = integrationLedger,
                    settingsBanner = settingsBanner,
                    onAnalyzeBitmap = { bmp, q, lang -> toolsVm.analyzeImage(bmp, q, lang) },
                    onResetVision = { toolsVm.resetVision() },
                    onToggleFlashlight = { toolsVm.toggleFlashlight(it) },
                    onRefreshBattery = { toolsVm.refreshBattery() },
                    onRefreshInstalledApps = { toolsVm.refreshInstalledApps() },
                    onFetchLocation = { precise -> toolsVm.fetchLocation(precise) },
                    onCheckLocationRevocation = { toolsVm.clearLocationIfRevoked() },
                    onRecordDeviceAction = { toolsVm.recordDeviceAction(it) },
                    onUpdateSchedule = { en, h, m, beat -> toolsVm.updateAutomationSchedule(en, h, m, beat) },
                    onTriggerAutomationNow = { toolsVm.triggerAutomationNow() },
                    onRefreshExternalAnalytics = { toolsVm.refreshExternalAnalytics() },
                    onSavePreferences = { updated, msg ->
                        toolsVm.savePreferencesUpdate(updated, msg)
                        assistantVm.setLanguage(AppLanguage.fromRaw(updated.defaultLanguage))
                    },
                    onClearBanner = { toolsVm.clearSettingsBanner() },
                    onSignOut = {
                        assistantVm.endVoiceSession()
                        AuthManager.signOut(credentialManager, scope)
                    }
                )
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianBg)) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            Scaffold(
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (tab == MainNavTab.TOOLS && selectedTab == MainNavTab.TOOLS) {
                                        toolsSubScreen = ToolsSubScreen.HUB
                                    }
                                    selectedTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                    navContent(Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            Scaffold(
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MainNavTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (tab == MainNavTab.TOOLS && selectedTab == MainNavTab.TOOLS) {
                                        toolsSubScreen = ToolsSubScreen.HUB
                                    }
                                    selectedTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                navContent(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}
