package com.example.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class YouTubeAnalyticsSnapshot(
    val channelTitle: String,
    val periodLabel: String,
    val totalViews: Long,
    val estimatedMinutesWatched: Long,
    val averageViewDurationSec: Int,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val topVideos: List<VideoMetricRow>
)

data class VideoMetricRow(
    val videoId: String,
    val title: String,
    val views: Long,
    val avgDurationSec: Int
)

data class MetaInsightsSnapshot(
    val accountName: String,
    val platformLabel: String,
    val reach: Long,
    val impressions: Long,
    val profileViews: Long,
    val engagementRatePercent: Double
)

sealed interface ExternalAnalyticsState<out T> {
    data class NotConfigured(
        val serviceName: String,
        val missingRequirements: List<String>,
        val oauthEndpointHint: String
    ) : ExternalAnalyticsState<Nothing>

    data object Loading : ExternalAnalyticsState<Nothing>
    data class Connected<T>(val data: T) : ExternalAnalyticsState<T>
    data class Error(val message: String) : ExternalAnalyticsState<Nothing>
}

/**
 * Official YouTube Analytics & Meta (Instagram/Facebook) Graph API client layer.
 * Zero secrets in APK; zero simulated metrics; never asks for account passwords.
 * Expects a server-side OAuth Backend Proxy that exchanges OAuth tokens securely
 * and proxies official Google/Meta REST endpoints.
 */
class AnalyticsServiceRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "AnalyticsServiceRepo"
    }

    suspend fun fetchYouTubeAnalytics(backendProxyUrl: String): ExternalAnalyticsState<YouTubeAnalyticsSnapshot> {
        val base = backendProxyUrl.trim().trimEnd('/')
        if (base.isEmpty() || (!base.startsWith("https://") && !base.startsWith("http://"))) {
            return ExternalAnalyticsState.NotConfigured(
                serviceName = "YouTube Analytics API",
                missingRequirements = listOf(
                    "Google Cloud OAuth 2.0 Client (yt-analytics.readonly scope) hosted on your backend proxy.",
                    "Eligible YouTube channel authorized via official Google OAuth consent (never password-based).",
                    "Configure your Backend Proxy URL in Settings -> Connected Services."
                ),
                oauthEndpointHint = "https://youtubeanalytics.googleapis.com/v2/reports"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$base/analytics/youtube/summary")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 401 || response.code == 403) {
                        return@withContext ExternalAnalyticsState.Error(
                            "YouTube OAuth authorization required on backend proxy (HTTP ${response.code})."
                        )
                    }
                    if (!response.isSuccessful) {
                        return@withContext ExternalAnalyticsState.Error(
                            "YouTube Analytics proxy returned HTTP ${response.code}."
                        )
                    }
                    val json = JSONObject(response.body?.string().orEmpty())
                    val videosArr = json.optJSONArray("topVideos")
                    val videos = buildList {
                        if (videosArr != null) {
                            for (i in 0 until videosArr.length()) {
                                val v = videosArr.optJSONObject(i) ?: continue
                                add(
                                    VideoMetricRow(
                                        videoId = v.optString("videoId"),
                                        title = v.optString("title"),
                                        views = v.optLong("views"),
                                        avgDurationSec = v.optInt("avgDurationSec")
                                    )
                                )
                            }
                        }
                    }
                    ExternalAnalyticsState.Connected(
                        YouTubeAnalyticsSnapshot(
                            channelTitle = json.optString("channelTitle", "Authorized Channel"),
                            periodLabel = json.optString("periodLabel", "Last 28 Days"),
                            totalViews = json.optLong("totalViews"),
                            estimatedMinutesWatched = json.optLong("estimatedMinutesWatched"),
                            averageViewDurationSec = json.optInt("averageViewDurationSec"),
                            likes = json.optLong("likes"),
                            comments = json.optLong("comments"),
                            shares = json.optLong("shares"),
                            topVideos = videos
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "YouTube Analytics proxy call failed", e)
                ExternalAnalyticsState.Error(
                    e.localizedMessage ?: "Unable to reach YouTube Analytics backend proxy."
                )
            }
        }
    }

    suspend fun fetchMetaInsights(backendProxyUrl: String): ExternalAnalyticsState<MetaInsightsSnapshot> {
        val base = backendProxyUrl.trim().trimEnd('/')
        if (base.isEmpty() || (!base.startsWith("https://") && !base.startsWith("http://"))) {
            return ExternalAnalyticsState.NotConfigured(
                serviceName = "Meta Graph API (Instagram & Facebook)",
                missingRequirements = listOf(
                    "Meta Developer App with approved Instagram Insights & Pages Read Engagement permissions.",
                    "Eligible Instagram Creator/Business account linked to a Facebook Page.",
                    "Server-side OAuth token exchange via Backend Proxy URL (never enter account passwords)."
                ),
                oauthEndpointHint = "https://graph.facebook.com/v21.0/me/accounts"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$base/analytics/meta/summary")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 401 || response.code == 403) {
                        return@withContext ExternalAnalyticsState.Error(
                            "Meta OAuth authorization or App Review approval required (HTTP ${response.code})."
                        )
                    }
                    if (!response.isSuccessful) {
                        return@withContext ExternalAnalyticsState.Error(
                            "Meta Insights proxy returned HTTP ${response.code}."
                        )
                    }
                    val json = JSONObject(response.body?.string().orEmpty())
                    ExternalAnalyticsState.Connected(
                        MetaInsightsSnapshot(
                            accountName = json.optString("accountName", "Connected Account"),
                            platformLabel = json.optString("platformLabel", "Instagram & Facebook"),
                            reach = json.optLong("reach"),
                            impressions = json.optLong("impressions"),
                            profileViews = json.optLong("profileViews"),
                            engagementRatePercent = json.optDouble("engagementRatePercent", 0.0)
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Meta Insights proxy call failed", e)
                ExternalAnalyticsState.Error(
                    e.localizedMessage ?: "Unable to reach Meta Insights backend proxy."
                )
            }
        }
    }
}
