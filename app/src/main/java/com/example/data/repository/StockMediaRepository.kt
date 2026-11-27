package com.example.data.repository

import android.util.Log
import com.example.data.model.FootageAssetItem
import com.example.data.model.ReusePermissionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed interface StockMediaSearchResult {
    data class Success(val provider: String, val assets: List<FootageAssetItem>) : StockMediaSearchResult
    data class ProxyRequired(val provider: String, val explanation: String) : StockMediaSearchResult
    data class Error(val provider: String, val message: String) : StockMediaSearchResult
}

data class ArchivalPortalLink(
    val portalName: String,
    val mediaScope: String,
    val searchUrl: String,
    val licenseReminder: String
)

/**
 * Zero-Secrets-in-APK Visual & Archival Clip Discovery Repository:
 * - Wikimedia Commons runs live & keyless immediately, returning real source links,
 *   creator credit, media type, and explicit reuse-permission status.
 * - Pexels & Pixabay route strictly through a user-configured server-side backend proxy URL.
 * - Never labels media copyright-free without verified license metadata; never fakes download links.
 */
class StockMediaRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "StockMediaRepo"
        private const val USER_AGENT = "ShubhamAI-PehluProject/1.1 (Documentary Research)"
    }

    fun buildArchivalPortalLinks(keywords: String): List<ArchivalPortalLink> {
        val clean = keywords.trim()
        if (clean.isEmpty()) return emptyList()
        val enc = URLEncoder.encode(clean, "UTF-8")
        return listOf(
            ArchivalPortalLink(
                portalName = "Internet Archive (Archival Video & Docs)",
                mediaScope = "Public-domain films, newsreels, historic documents",
                searchUrl = "https://archive.org/search?query=$enc",
                licenseReminder = "Check each item's Usage/License block — public viewing does NOT guarantee commercial reuse."
            ),
            ArchivalPortalLink(
                portalName = "Wikimedia Commons (Photos, Maps & Diagrams)",
                mediaScope = "Openly licensed photographs, historical maps, charts",
                searchUrl = "https://commons.wikimedia.org/w/index.php?search=$enc&title=Special:MediaSearch",
                licenseReminder = "Follow CC BY / CC BY-SA attribution requirements carefully."
            ),
            ArchivalPortalLink(
                portalName = "NASA Image & Video Library",
                mediaScope = "Official spaceflight, Earth & science footage",
                searchUrl = "https://images.nasa.gov/search?q=$enc",
                licenseReminder = "Verify NASA Media Usage Guidelines (no endorsement implied)."
            ),
            ArchivalPortalLink(
                portalName = "Library of Congress Prints & Photos",
                mediaScope = "Historical photographs, newspapers & archival maps",
                searchUrl = "https://www.loc.gov/photos/?q=$enc",
                licenseReminder = "Check 'Rights Advisory' on every individual record before reuse."
            )
        )
    }

    suspend fun searchWikimediaCommons(
        query: String,
        limit: Int = 10,
        sceneNumber: Int = 0,
        timestampRange: String = ""
    ): StockMediaSearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return StockMediaSearchResult.Error("Wikimedia Commons", "Enter a search keyword first.")
        }
        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val url = "https://commons.wikimedia.org/w/api.php" +
                    "?action=query&format=json&generator=search" +
                    "&gsrnamespace=6&gsrlimit=${limit.coerceIn(1, 20)}&gsrsearch=$encoded" +
                    "&prop=imageinfo&iiprop=url|mime|extmetadata&iiurlwidth=640"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext StockMediaSearchResult.Error(
                            "Wikimedia Commons",
                            "Wikimedia API returned HTTP ${response.code}."
                        )
                    }
                    val bodyStr = response.body?.string().orEmpty()
                    val assets = parseWikimediaResponse(
                        rawJson = bodyStr,
                        sceneNumber = sceneNumber,
                        timestampRange = timestampRange,
                        searchKeywords = trimmed
                    )
                    StockMediaSearchResult.Success("Wikimedia Commons", assets)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Wikimedia Commons search failed", e)
                StockMediaSearchResult.Error(
                    "Wikimedia Commons",
                    e.localizedMessage ?: "Network error while querying Wikimedia Commons."
                )
            }
        }
    }

    suspend fun searchViaBackendProxy(
        provider: String,
        query: String,
        backendProxyUrl: String,
        sceneNumber: Int = 0,
        timestampRange: String = ""
    ): StockMediaSearchResult {
        val cleanProxy = backendProxyUrl.trim().trimEnd('/')
        if (cleanProxy.isEmpty() || (!cleanProxy.startsWith("https://") && !cleanProxy.startsWith("http://"))) {
            return StockMediaSearchResult.ProxyRequired(
                provider = provider,
                explanation = "$provider requires a server-side API key. Per our Zero-Secrets-in-APK policy, " +
                    "no $provider secret is bundled in this app. Configure your HTTPS Backend Proxy URL " +
                    "in Settings -> Backend Proxy (GET /stock/${provider.lowercase()}?q=...) or use live Wikimedia Commons & archival portals below."
            )
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return StockMediaSearchResult.Error(provider, "Enter a search keyword first.")
        }

        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val endpoint = "$cleanProxy/stock/${provider.lowercase()}?q=$encoded"
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", USER_AGENT)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext StockMediaSearchResult.Error(
                            provider,
                            "Backend proxy returned HTTP ${response.code} for $provider."
                        )
                    }
                    val bodyStr = response.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val arr = json.optJSONArray("assets")
                    val results = buildList {
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                add(
                                    FootageAssetItem(
                                        title = obj.optString("title", "$provider Asset ${i + 1}"),
                                        provider = provider,
                                        assetUrl = obj.optString("assetUrl", ""),
                                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                                        creator = obj.optString("creator", "Unknown Creator"),
                                        license = obj.optString("license", "Check $provider License"),
                                        attributionRequired = obj.optBoolean("attributionRequired", true),
                                        commercialCaution = obj.optString(
                                            "commercialCaution",
                                            "Verify exact license & model/property release terms on source page before publishing."
                                        ),
                                        sceneNumber = sceneNumber,
                                        timestampRange = timestampRange,
                                        mediaType = obj.optString("mediaType", "Stock Media"),
                                        reuseStatus = ReusePermissionStatus.UNCLEAR_VERIFY.name,
                                        relevanceReason = "Matched search \"$trimmed\"",
                                        searchKeywords = trimmed
                                    )
                                )
                            }
                        }
                    }
                    StockMediaSearchResult.Success(provider, results)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$provider proxy search failed", e)
                StockMediaSearchResult.Error(
                    provider,
                    e.localizedMessage ?: "Failed to reach backend proxy for $provider."
                )
            }
        }
    }

    internal fun parseWikimediaResponse(
        rawJson: String,
        sceneNumber: Int = 0,
        timestampRange: String = "",
        searchKeywords: String = ""
    ): List<FootageAssetItem> {
        if (rawJson.isBlank()) return emptyList()
        val root = JSONObject(rawJson)
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val results = mutableListOf<FootageAssetItem>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val title = page.optString("title", "").removePrefix("File:")
            val infoArr = page.optJSONArray("imageinfo") ?: continue
            val info = infoArr.optJSONObject(0) ?: continue
            val descriptionUrl = info.optString("descriptionurl", info.optString("url", ""))
            val thumbUrl = info.optString("thumburl", "")
            val mime = info.optString("mime", "").lowercase()
            val meta = info.optJSONObject("extmetadata")
            val artistRaw = meta?.optJSONObject("Artist")?.optString("value", "") ?: ""
            val creator = stripHtml(artistRaw).ifBlank { "Wikimedia Contributor (check source page)" }
            val licenseShort = meta?.optJSONObject("LicenseShortName")?.optString("value", "")
                ?.trim().orEmpty().ifBlank { "License Unspecified — Verify Before Use" }
            val attributionReq = meta?.optJSONObject("AttributionRequired")?.optString("value", "true")
                ?.equals("true", ignoreCase = true) ?: true

            val mediaType = when {
                mime.startsWith("video/") -> "Video Clip"
                mime.contains("svg") -> "Diagram / Illustration"
                mime.contains("pdf") -> "Archival Document"
                title.contains("map", ignoreCase = true) -> "Historical Map"
                else -> "Photograph / Image"
            }

            val (reuseStatus, caution) = when {
                licenseShort.contains("NC", ignoreCase = true) || licenseShort.contains("Fair use", ignoreCase = true) ->
                    ReusePermissionStatus.RESTRICTED_REFERENCE_ONLY to
                        "RESTRICTED ($licenseShort): Do NOT reuse commercially without explicit rights holder permission."
                licenseShort.contains("Public domain", ignoreCase = true) ||
                    licenseShort.contains("CC0", ignoreCase = true) ->
                    ReusePermissionStatus.CONFIRMED_OPEN to
                        "Tagged $licenseShort on Wikimedia; verify source page for personality/trademark caveats."
                licenseShort.contains("CC BY", ignoreCase = true) ->
                    ReusePermissionStatus.ATTRIBUTION_REQUIRED to
                        "Attribution required ($licenseShort). Credit creator clearly in description & lower-third."
                else ->
                    ReusePermissionStatus.UNCLEAR_VERIFY to
                        "Reuse permission unclear ($licenseShort). Verify license on source page before publishing."
            }

            if (descriptionUrl.isNotBlank()) {
                results.add(
                    FootageAssetItem(
                        title = title.take(120),
                        provider = "Wikimedia Commons",
                        assetUrl = descriptionUrl,
                        thumbnailUrl = thumbUrl,
                        creator = creator.take(120),
                        license = licenseShort.take(100),
                        attributionRequired = attributionReq,
                        commercialCaution = caution,
                        sceneNumber = sceneNumber,
                        timestampRange = timestampRange,
                        mediaType = mediaType,
                        reuseStatus = reuseStatus.name,
                        relevanceReason = if (searchKeywords.isNotBlank()) "Matched \"$searchKeywords\"" else "Archival/reference visual",
                        searchKeywords = searchKeywords
                    )
                )
            }
        }
        return results
    }

    private fun stripHtml(input: String): String =
        input.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
}
