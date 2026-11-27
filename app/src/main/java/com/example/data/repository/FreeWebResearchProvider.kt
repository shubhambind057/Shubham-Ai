package com.example.data.repository

import android.util.Log
import com.example.data.model.CitationItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LiveReferenceArticle(
    val title: String,
    val snippet: String,
    val url: String,
    val lastEdited: String,
    val publisher: String = "Wikipedia (Live Reference)"
) {
    fun toCitation(): CitationItem = CitationItem(title = title, url = url)
}

/**
 * 100% Free, Keyless, Zero-Quota Live Web Reference Provider.
 *
 * Uses the public MediaWiki API (en.wikipedia.org) over HTTPS to retrieve real, verifiable
 * reference summaries and canonical URLs without triggering Firebase Spark-tier
 * Google Search Grounding (HTTP 429 / quota) limits.
 */
class FreeWebResearchProvider(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "FreeWebResearch"
        private const val USER_AGENT = "ShubhamAI-PehluProject/1.2 (Documentary Research)"
    }

    suspend fun searchReferences(query: String, limit: Int = 5): List<LiveReferenceArticle> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(clean, "UTF-8")
                val url = "https://en.wikipedia.org/w/api.php" +
                    "?action=query&format=json&list=search" +
                    "&srsearch=$encoded&srlimit=${limit.coerceIn(1, 8)}&utf8=1"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Wikipedia search returned HTTP ${response.code}")
                        return@withContext emptyList()
                    }
                    parseSearchJson(response.body?.string().orEmpty())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Live reference lookup failed (continuing safely): ${e.message}")
                emptyList()
            }
        }
    }

    internal fun parseSearchJson(rawJson: String): List<LiveReferenceArticle> {
        if (rawJson.isBlank()) return emptyList()
        val root = JSONObject(rawJson)
        val arr = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                val pageId = obj.optLong("pageid", 0L)
                val rawSnippet = obj.optString("snippet")
                val cleanSnippet = rawSnippet
                    .replace(Regex("<[^>]*>"), "")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&#039;", "'")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val timestamp = obj.optString("timestamp").take(10)
                if (title.isNotBlank() && pageId > 0L) {
                    val slug = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                    add(
                        LiveReferenceArticle(
                            title = title,
                            snippet = cleanSnippet,
                            url = "https://en.wikipedia.org/wiki/$slug",
                            lastEdited = timestamp
                        )
                    )
                }
            }
        }
    }
}
