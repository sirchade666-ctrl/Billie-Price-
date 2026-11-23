package com.example.engine

import com.example.data.model.AppReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateCheckOutcome {
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateCheckOutcome()
    data class UpToDate(val latestVersion: String) : UpdateCheckOutcome()
    data class NeedsSourceConfig(val defaultStoreUrl: String) : UpdateCheckOutcome()
    data class Error(val message: String) : UpdateCheckOutcome()
}

/**
 * Google Play Creator Program-compliant app update checker.
 * Queries GitHub Releases API (or a custom release/store URL) over HTTPS and opens
 * external download/store pages via zero-permission ACTION_VIEW intents without
 * requesting REQUEST_INSTALL_PACKAGES or downloading APKs inside the app.
 */
object AppUpdateEngine {

    fun defaultStoreListingUrl(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"

    /**
     * Resolves user-entered update source into a GitHub Releases API URL if it is a
     * GitHub repo slug (`owner/repo`) or GitHub repository URL, otherwise null.
     */
    fun resolveGitHubLatestReleaseApiUrl(rawSource: String): String? {
        val trimmed = rawSource.trim().removeSuffix("/")
        if (trimmed.isBlank()) return null

        // Direct API endpoint
        if (trimmed.startsWith("https://api.github.com/repos/") && trimmed.contains("/releases")) {
            return trimmed
        }

        // Standard GitHub web URL: https://github.com/owner/repo(/...)
        val ghPrefix = Regex("^https?://(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:/.*)?$")
        val urlMatch = ghPrefix.matchEntire(trimmed)
        if (urlMatch != null) {
            val owner = urlMatch.groupValues[1]
            val repo = urlMatch.groupValues[2].removeSuffix(".git")
            return "https://api.github.com/repos/$owner/$repo/releases/latest"
        }

        // Shorthand slug: owner/repo
        val slugRegex = Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$")
        val slugMatch = slugRegex.matchEntire(trimmed)
        if (slugMatch != null) {
            val owner = slugMatch.groupValues[1]
            val repo = slugMatch.groupValues[2].removeSuffix(".git")
            return "https://api.github.com/repos/$owner/$repo/releases/latest"
        }

        return null
    }

    /**
     * Resolves an external browser/store URL to open when the user clicks "Open Download Page".
     */
    fun resolveExternalPageUrl(rawSource: String, packageName: String): String {
        val trimmed = rawSource.trim().removeSuffix("/")
        if (trimmed.isBlank()) return defaultStoreListingUrl(packageName)
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed
        }
        val slugRegex = Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$")
        if (slugRegex.matches(trimmed)) {
            return "https://github.com/$trimmed/releases/latest"
        }
        return defaultStoreListingUrl(packageName)
    }

    /**
     * Compares semantic version strings (e.g. "2.0" vs "v2.1", "2.0.1", "3.0-beta").
     * Returns true strictly when [remoteVersion] is newer than [currentVersion].
     */
    fun isNewerVersion(currentVersion: String, remoteVersion: String): Boolean {
        val currentParts = parseNumericVersionParts(currentVersion)
        val remoteParts = parseNumericVersionParts(remoteVersion)
        if (remoteParts.isEmpty()) return false
        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseNumericVersionParts(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        val core = cleaned.substringBefore('-').substringBefore('+')
        return core.split('.').mapNotNull { segment ->
            val digits = segment.filter { it.isDigit() }
            digits.toIntOrNull()
        }
    }

    /**
     * Parses a GitHub Releases API JSON payload (or simple custom JSON `{ "version": "...", "notes": "...", "url": "..." }`).
     */
    fun parseReleaseJson(jsonText: String, fallbackPageUrl: String): AppReleaseInfo? {
        if (!jsonText.trim().startsWith("{")) return null
        val versionTag = extractJsonStringField(jsonText, "tag_name")
            .ifBlank { extractJsonStringField(jsonText, "version") }
            .ifBlank { extractJsonStringField(jsonText, "versionName") }
            .trim()
        if (versionTag.isBlank()) return null

        val title = extractJsonStringField(jsonText, "name")
            .ifBlank { "Geoport EMF $versionTag" }
            .trim()
        val notes = extractJsonStringField(jsonText, "body")
            .ifBlank { extractJsonStringField(jsonText, "notes") }
            .ifBlank { "Bug fixes, sensor improvements, and new investigation tools." }
            .trim()
        val htmlUrl = extractJsonStringField(jsonText, "html_url")
            .ifBlank { extractJsonStringField(jsonText, "downloadUrl") }
            .ifBlank { extractJsonStringField(jsonText, "url") }
            .ifBlank { fallbackPageUrl }
            .trim()
        val publishedAt = extractJsonStringField(jsonText, "published_at")
            .substringBefore("T")
            .trim()

        return AppReleaseInfo(
            versionName = versionTag.removePrefix("v").removePrefix("V"),
            releaseTitle = title,
            releaseNotes = notes,
            downloadPageUrl = htmlUrl,
            publishedAt = publishedAt
        )
    }

    private fun extractJsonStringField(jsonText: String, key: String): String {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val match = pattern.find(jsonText) ?: return ""
        return unescapeJsonString(match.groupValues[1])
    }

    private fun unescapeJsonString(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> { out.append('\n'); i += 2 }
                    'r' -> { out.append('\r'); i += 2 }
                    't' -> { out.append('\t'); i += 2 }
                    '"' -> { out.append('"'); i += 2 }
                    '\\' -> { out.append('\\'); i += 2 }
                    '/' -> { out.append('/'); i += 2 }
                    'u' -> {
                        if (i + 5 < raw.length) {
                            val code = raw.substring(i + 2, i + 6).toIntOrNull(16)
                            if (code != null) {
                                out.append(code.toChar())
                                i += 6
                            } else {
                                out.append(next); i += 2
                            }
                        } else {
                            out.append(next); i += 2
                        }
                    }
                    else -> { out.append(next); i += 2 }
                }
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    suspend fun checkForUpdates(
        currentVersionName: String,
        updateSource: String,
        packageName: String
    ): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        val trimmed = updateSource.trim()
        val storeFallback = defaultStoreListingUrl(packageName)
        if (trimmed.isBlank()) {
            return@withContext UpdateCheckOutcome.NeedsSourceConfig(storeFallback)
        }

        val apiUrl = resolveGitHubLatestReleaseApiUrl(trimmed)
            ?: if (trimmed.endsWith(".json", ignoreCase = true) && trimmed.startsWith("https://")) {
                trimmed
            } else {
                return@withContext UpdateCheckOutcome.Error(
                    "Enter a GitHub repo (e.g. username/geoport-emf) or a release JSON URL, or open the page directly."
                )
            }

        val fallbackPage = resolveExternalPageUrl(trimmed, packageName)

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "GeoportEMF/$currentVersionName")
            }

            val code = conn.responseCode
            if (code == 404) {
                return@withContext UpdateCheckOutcome.Error(
                    "No published release found yet for \"$trimmed\". Create a GitHub Release (e.g. v2.1) or check the repo name."
                )
            }
            if (code !in 200..299) {
                return@withContext UpdateCheckOutcome.Error("Update server responded with HTTP $code.")
            }

            val responseBody = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val release = parseReleaseJson(responseBody, fallbackPage)
                ?: return@withContext UpdateCheckOutcome.Error("Could not read version details from release feed.")

            if (isNewerVersion(currentVersionName, release.versionName)) {
                UpdateCheckOutcome.UpdateAvailable(release)
            } else {
                UpdateCheckOutcome.UpToDate(release.versionName)
            }
        } catch (e: Exception) {
            UpdateCheckOutcome.Error(
                "Unable to check for updates right now (${e.localizedMessage ?: "network error"})."
            )
        } finally {
            conn?.disconnect()
        }
    }
}
