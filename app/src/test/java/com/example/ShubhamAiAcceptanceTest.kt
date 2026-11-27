package com.example

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.model.AppLanguage
import com.example.data.model.BlueprintSceneItem
import com.example.data.model.ChecklistEntry
import com.example.data.model.ClaimClassification
import com.example.data.model.FootageAssetItem
import com.example.data.model.ProjectOverviewData
import com.example.data.model.ReusePermissionStatus
import com.example.data.model.StoryStructureData
import com.example.data.model.StudioProject
import com.example.data.model.VerifiedFactItem
import com.example.data.repository.AnalyticsServiceRepository
import com.example.data.repository.ExternalAnalyticsState
import com.example.data.repository.FreeWebResearchProvider
import com.example.data.repository.GeminiAiRepository
import com.example.data.repository.StockMediaRepository
import com.example.data.repository.StockMediaSearchResult
import com.example.platform.AssistantCommand
import com.example.platform.AssistantCommandRouter
import com.example.platform.DailyAutomationScheduler
import com.example.platform.DailyResearchWorker
import com.example.platform.DeviceControlManager
import com.example.platform.SrtAndZipExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShubhamAiAcceptanceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `app name matches Shubham AI branding`() {
        assertEquals("Shubham AI", context.getString(R.string.app_name))
    }

    @Test
    fun `AssistantCommandRouter intercepts Jarvis commands locally with zero AI cost`() {
        assertEquals(AssistantCommand.Flashlight(true), AssistantCommandRouter.parse("Turn flashlight on"))
        assertEquals(AssistantCommand.Flashlight(false), AssistantCommandRouter.parse("torch band karo"))
        assertEquals(AssistantCommand.OpenYouTube, AssistantCommandRouter.parse("open youtube"))
        assertEquals(
            AssistantCommand.SearchYouTube("ISRO Gaganyaan"),
            AssistantCommandRouter.parse("Search YouTube for ISRO Gaganyaan")
        )
        assertEquals(
            AssistantCommand.OpenWebsite("https://isro.gov.in"),
            AssistantCommandRouter.parse("open website isro.gov.in")
        )
        assertEquals(
            AssistantCommand.OpenInstalledApp("WhatsApp"),
            AssistantCommandRouter.parse("open WhatsApp")
        )
        assertEquals(AssistantCommand.CheckBattery, AssistantCommandRouter.parse("check battery status"))
        assertEquals(
            AssistantCommand.StartDocumentary("India Semiconductor Mission"),
            AssistantCommandRouter.parse("Make a documentary about India Semiconductor Mission")
        )
        assertEquals(
            AssistantCommand.None,
            AssistantCommandRouter.parse("Explain quantum entanglement simply in Hinglish")
        )
    }

    @Test
    fun `GeminiAiRepository classifies quota 429 errors with friendly recovery advice`() {
        val quotaEx = RuntimeException("HTTP 429 RESOURCE_EXHAUSTED: Quota exceeded for free tier")
        assertTrue(GeminiAiRepository.isQuotaOrRateLimitError(quotaEx))
        val msg = GeminiAiRepository.formatUserFriendlyAiError(quotaEx, "Fallback")
        assertTrue("Expected helpful quota message", msg.contains("quota", ignoreCase = true) || msg.contains("rate limit", ignoreCase = true))
        assertFalse(GeminiAiRepository.isQuotaOrRateLimitError(IllegalStateException("Network timeout")))
    }

    @Test
    fun `FreeWebResearchProvider parses Wikipedia JSON cleanly into verifiable citations`() {
        val sampleJson = """
            {
              "query": {
                "search": [
                  {
                    "title": "Dholavira",
                    "pageid": 54321,
                    "snippet": "<span class=\"searchmatch\">Dholavira</span> is an archaeological site &amp; Harappan city.",
                    "timestamp": "2026-03-14T10:22:00Z"
                  },
                  {
                    "title": "Indus Valley Civilisation",
                    "pageid": 98765,
                    "snippet": "Ancient Bronze Age &quot;civilisation&quot; in South Asia.",
                    "timestamp": "2026-04-01T08:00:00Z"
                  }
                ]
              }
            }
        """.trimIndent()

        val provider = FreeWebResearchProvider()
        val results = provider.parseSearchJson(sampleJson)
        assertEquals(2, results.size)
        assertEquals("Dholavira", results[0].title)
        assertEquals("Dholavira is an archaeological site & Harappan city.", results[0].snippet)
        assertEquals("https://en.wikipedia.org/wiki/Dholavira", results[0].url)
        assertEquals("2026-03-14", results[0].lastEdited)

        val citation = results[1].toCitation()
        assertEquals("Indus Valley Civilisation", citation.title)
        assertEquals("https://en.wikipedia.org/wiki/Indus_Valley_Civilisation", citation.url)
    }

    @Test
    fun `SRT generation supports UTF-8 Hindi and Hinglish cues cleanly`() {
        val script = "Aaj hum ISRO ke Gaganyaan mission ka sach jaanenge. यह भारत का पहला मानव अंतरिक्ष मिशन है! Kya yeh timeline confirm hai?"
        val srt = SrtAndZipExporter.generateSrtFromScript(script)
        assertTrue("SRT should contain timestamp arrow", srt.contains("-->"))
        assertTrue("SRT should preserve Devanagari UTF-8", srt.contains("यह भारत का पहला मानव अंतरिक्ष मिशन है"))
        assertTrue("SRT should validate cleanly", SrtAndZipExporter.isValidSrt(srt))

        val cues = SrtAndZipExporter.parseSrt(srt)
        assertTrue("Expected at least 2 subtitle cues", cues.size >= 2)
    }

    @Test
    fun `10-point audit and 7-column editing sheet detect issues and export structured CSV`() {
        val project = StudioProject(
            id = "proj_audit_1",
            topicTitle = "Indus Valley Water Engineering",
            topicApproved = true,
            verifiedFacts = listOf(
                VerifiedFactItem(
                    claim = "Dholavira featured rock-cut cascading reservoirs.",
                    classification = ClaimClassification.CONFIRMED.name,
                    sourceType = "PRIMARY",
                    sourceTitle = "ASI Excavation Report",
                    publisher = "Archaeological Survey of India",
                    sourceUrl = "https://asi.nic.in",
                    publishedDate = "2015",
                    limitations = "Exact population capacity estimates vary by scholar."
                ).toMap()
            ),
            disputedClaims = listOf("Reason for final urban decline remains debated."),
            shortScript = "Dholavira ka water system 4500 saal pehle desert mein kaise chalta tha?",
            blueprintScenes = listOf(
                BlueprintSceneItem(
                    sceneNumber = 1,
                    startTime = "00:00",
                    endTime = "00:15",
                    purpose = "Hook",
                    voiceOverNarration = "4500 saal pehle Rann of Kutch mein ek aisa shehar tha jahan paani kabhi khatam nahi hota tha.",
                    requiredVisuals = "Aerial view of Dholavira reservoirs",
                    onScreenText = "DHOLAVIRA • 2500 BCE",
                    bgmMood = "Deep desert percussion",
                    sfx = "Wind whoosh",
                    transition = "Cross dissolve",
                    researchRefs = "ASI Excavation Report",
                    alternativeVisualIdea = "2D animated cross-section diagram of cascading tanks"
                ).toMap()
            ),
            footageAssets = listOf(
                FootageAssetItem(
                    title = "Dholavira Eastern Reservoir",
                    provider = "Wikimedia Commons",
                    assetUrl = "https://commons.wikimedia.org/wiki/File:Dholavira.jpg",
                    creator = "ASI Contributor",
                    license = "CC BY-SA 4.0",
                    reuseStatus = ReusePermissionStatus.ATTRIBUTION_REQUIRED.name,
                    sceneNumber = 1,
                    timestampRange = "00:00-00:15"
                ).toMap()
            ),
            subtitlesSrt = SrtAndZipExporter.generateSrtFromScript("4500 saal pehle Rann of Kutch mein ek aisa shehar tha.")
        )

        val audit = SrtAndZipExporter.buildTenPointAudit(project)
        assertEquals(10, audit.size)
        assertTrue("All 10 checks should pass for complete sample dossier", audit.all { it.completed })

        val csv = SrtAndZipExporter.buildSevenColumnEditingSheetCsv(project)
        assertTrue(csv.startsWith("Timestamp,Voice-over,Video Clip/Image,On-screen Text,Music/SFX,Transition,Source"))
        assertTrue(csv.contains("00:00 - 00:15"))
        assertTrue(csv.contains("Dholavira Eastern Reservoir"))
    }

    @Test
    fun `ZIP project exporter bundles all 14 PEHLU PROJECT deliverables and WAV files`() {
        val tempWav = File(context.cacheDir, "test_voiceover.wav").apply {
            writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00))
        }
        val sceneWav = File(context.cacheDir, "test_scene_1.wav").apply {
            writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00))
        }
        val project = StudioProject(
            id = "proj_test_1",
            topicTitle = "ISRO Gaganyaan Timeline",
            categoryBeat = "Science & Tech",
            language = AppLanguage.HINGLISH.name,
            angle = "Engineering milestones vs public myths",
            hook = "Kya 2026 tak Indian astronauts space jaayenge?",
            whyCoverNow = "Recent crew escape system tests",
            topicApproved = true,
            finalApproved = true,
            verifiedFacts = listOf(
                VerifiedFactItem(
                    claim = "ISRO conducted the TV-D1 Flight Test successfully.",
                    classification = ClaimClassification.CONFIRMED.name,
                    sourceType = "PRIMARY",
                    sourceTitle = "ISRO Official",
                    publisher = "ISRO",
                    sourceUrl = "https://www.isro.gov.in",
                    publishedDate = "2023-10-21"
                ).toMap()
            ),
            disputedClaims = listOf("Exact final crew launch date remains subject to test milestones."),
            assumptions = listOf("Assumes no further parachute redesign delays."),
            shortScript = "Kya aap jaante hain Gaganyaan ke peeche kitna bada safety test hai?",
            longScript = "Namaste dosto, aaj PEHLU PROJECT mein baat karenge Gaganyaan ki poori engineering story.",
            visualDirections = "Scene 1: Launchpad B-roll with animated timeline.",
            onScreenText = listOf("TV-D1 TEST: SUCCESS"),
            titleOptions = listOf("ISRO Gaganyaan: The Untold Safety Engineering"),
            thumbnailConcepts = listOf("Split frame crew module + TV-D1 launch"),
            descriptionAndChapters = "00:00 Intro\n01:30 Crew Escape System",
            projectOverview = ProjectOverviewData(
                documentaryTitle = "ISRO Gaganyaan Engineering",
                centralIdea = "How human-rating a rocket works"
            ).toMap(),
            storyStructure = StoryStructureData(
                openingHook = "Why human spaceflight takes years of abort tests",
                mainChapters = listOf("Chapter 1: Crew Escape", "Chapter 2: Life Support")
            ).toMap(),
            blueprintScenes = listOf(
                BlueprintSceneItem(
                    sceneNumber = 1,
                    startTime = "00:00",
                    endTime = "00:20",
                    purpose = "Hook",
                    voiceOverNarration = "Gaganyaan sirf ek launch nahi, ek life-safety challenge hai.",
                    requiredVisuals = "TV-D1 liftoff archival footage",
                    audioLocalPath = sceneWav.absolutePath
                ).toMap()
            ),
            subtitlesSrt = SrtAndZipExporter.generateSrtFromScript("Kya aap jaante hain Gaganyaan ke peeche kitna bada safety test hai?"),
            footageAssets = listOf(
                FootageAssetItem(
                    provider = "Wikimedia Commons",
                    title = "ISRO LVM3 Launch",
                    assetUrl = "https://commons.wikimedia.org/wiki/File:LVM3.jpg",
                    creator = "ISRO",
                    license = "GODL-India",
                    reuseStatus = ReusePermissionStatus.ATTRIBUTION_REQUIRED.name,
                    attributionRequired = true,
                    commercialCaution = "Verify attribution text before commercial monetization.",
                    sceneNumber = 1
                ).toMap()
            ),
            capcutPlan = "Track 1: Voiceover, Track 2: B-roll, Track 3: SRT",
            capcutChecklist = listOf(ChecklistEntry("Import WAV and SRT into CapCut", true).toMap()),
            voiceoverLocalPath = tempWav.absolutePath
        )

        val baos = ByteArrayOutputStream()
        SrtAndZipExporter.writeProjectZipToStream(project, baos)
        val zipBytes = baos.toByteArray()
        assertTrue("ZIP payload must not be empty", zipBytes.isNotEmpty())

        val entryNames = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zis.nextEntry
            }
        }

        val expectedEntries = setOf(
            "research_dossier.md",
            "short_script_40_60s.txt",
            "long_documentary_script.txt",
            "subtitles.srt",
            "footage_licenses.csv",
            "capcut_production_plan.md",
            "video_blueprint.md",
            "editing_sheet_7col.csv",
            "titles_thumbnails_chapters.md",
            "voiceover_script_guide.md",
            "final_audit_checklist.md",
            "voiceover.wav",
            "scene_01_voiceover.wav"
        )
        assertEquals(expectedEntries, entryNames)
        tempWav.delete()
        sceneWav.delete()
    }

    @Test
    fun `Wikimedia parser classifies reuse permissions and archival portals honestly`() {
        val sampleJson = """
            {
              "query": {
                "pages": {
                  "123": {
                    "title": "File:ISRO_Rocket.jpg",
                    "imageinfo": [
                      {
                        "url": "https://upload.wikimedia.org/isro_rocket.jpg",
                        "descriptionurl": "https://commons.wikimedia.org/wiki/File:ISRO_Rocket.jpg",
                        "thumburl": "https://upload.wikimedia.org/thumb_isro.jpg",
                        "mime": "image/jpeg",
                        "extmetadata": {
                          "Artist": { "value": "<a>ISRO Media</a>" },
                          "LicenseShortName": { "value": "CC BY-SA 4.0" },
                          "AttributionRequired": { "value": "true" }
                        }
                      }
                    ]
                  },
                  "124": {
                    "title": "File:Apollo_11_PD.jpg",
                    "imageinfo": [
                      {
                        "url": "https://upload.wikimedia.org/apollo.jpg",
                        "descriptionurl": "https://commons.wikimedia.org/wiki/File:Apollo_11_PD.jpg",
                        "mime": "image/jpeg",
                        "extmetadata": {
                          "Artist": { "value": "NASA" },
                          "LicenseShortName": { "value": "Public domain" },
                          "AttributionRequired": { "value": "false" }
                        }
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val repo = StockMediaRepository()
        val parsed = repo.parseWikimediaResponse(sampleJson, sceneNumber = 2, timestampRange = "00:20-00:40")
        assertEquals(2, parsed.size)
        val isro = parsed.first { it.title.contains("ISRO") }
        assertEquals(ReusePermissionStatus.ATTRIBUTION_REQUIRED.name, isro.reuseStatus)
        assertEquals(2, isro.sceneNumber)
        val apollo = parsed.first { it.title.contains("Apollo") }
        assertEquals(ReusePermissionStatus.CONFIRMED_OPEN.name, apollo.reuseStatus)

        val portals = repo.buildArchivalPortalLinks("Indus Valley")
        assertEquals(4, portals.size)
        assertTrue(portals.any { it.searchUrl.contains("archive.org") })
        assertTrue(portals.any { it.searchUrl.contains("images.nasa.gov") })
    }

    @Test
    fun `Pexels and Pixabay require backend proxy and never fabricate results`() = runBlocking {
        val repo = StockMediaRepository()
        val res = repo.searchViaBackendProxy("Pexels", "space", backendProxyUrl = "")
        assertTrue("Expected ProxyRequired when proxy URL is blank", res is StockMediaSearchResult.ProxyRequired)
    }

    @Test
    fun `YouTube and Meta Analytics stay honestly NotConfigured without backend proxy`() = runBlocking {
        val repo = AnalyticsServiceRepository()
        val yt = repo.fetchYouTubeAnalytics("")
        val meta = repo.fetchMetaInsights("")
        assertTrue("YouTube must be NotConfigured without proxy", yt is ExternalAnalyticsState.NotConfigured)
        assertTrue("Meta must be NotConfigured without proxy", meta is ExternalAnalyticsState.NotConfigured)
    }

    @Test
    fun `DeviceControlManager sanitizes phone numbers, blocks unsafe URLs, and handles missing flash gracefully`() {
        val manager = DeviceControlManager(context)
        assertEquals("+919876543210", manager.sanitizePhoneNumber("+91 98765-43210 (home)"))
        val blocked = manager.openUrl("javascript:alert(1)")
        assertFalse("Non-http(s) URL must be blocked", blocked.success)
        val battery = manager.readBatteryStatus()
        assertTrue("Battery voltage should be non-negative", battery.voltageMv >= 0)
        // Robolectric default device has no camera flash; must fail gracefully without crashing
        val flashRes = manager.setFlashlight(true)
        assertFalse("Expected graceful false when no hardware flash exists", flashRes.success)
        assertTrue("Expected honest message", flashRes.message.isNotBlank())
    }

    @Test
    fun `DailyAutomationScheduler schedules and cancels periodic WorkManager job cleanly`() {
        val delay = DailyAutomationScheduler.computeInitialDelayMinutes(8, 0)
        assertTrue("Initial delay should be >= 1 minute", delay >= 1L)

        DailyAutomationScheduler.scheduleDaily(
            context = context,
            hour = 8,
            minute = 0,
            beat = "Science & Tech",
            language = AppLanguage.HINGLISH
        )
        val wm = WorkManager.getInstance(context)
        val enqueued = wm.getWorkInfosForUniqueWork(DailyResearchWorker.UNIQUE_PERIODIC_WORK).get()
        assertEquals(1, enqueued.size)
        assertEquals(WorkInfo.State.ENQUEUED, enqueued.first().state)

        DailyAutomationScheduler.pauseOrStop(context)
        val afterStop = wm.getWorkInfosForUniqueWork(DailyResearchWorker.UNIQUE_PERIODIC_WORK).get()
        assertTrue(
            "Periodic work must be cancelled after pauseOrStop",
            afterStop.isEmpty() || afterStop.first().state == WorkInfo.State.CANCELLED
        )
    }
}
