package com.bratik.clockweather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Информация о последнем релизе приложения на GitHub. */
data class ReleaseInfo(
    val version: String,
    val apkUrl: String,
    val notes: String,
    val pageUrl: String,
)

sealed class UpdateResult {
    /** Есть версия новее текущей. */
    data class Available(val info: ReleaseInfo) : UpdateResult()

    /** Установлена последняя версия. */
    object UpToDate : UpdateResult()

    /** Проверить не удалось (нет сети, репозиторий без релизов и т.п.). */
    data class Failed(val message: String) : UpdateResult()
}

/**
 * Проверка обновлений через GitHub Releases публичного репозитория.
 * Никакой телеметрии: один GET на releases/latest.
 */
object UpdateChecker {

    private const val OWNER = "rioorob-hash2"
    private const val REPO = "ClockWeather-Android"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val PAGE = "https://github.com/$OWNER/$REPO/releases"
    private const val TIMEOUT_MS = 12_000

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(API)
            val info = UpdateParser.parseRelease(body, PAGE) ?: return@withContext UpdateResult.Failed(
                "В релизах нет версии для Android",
            )
            if (com.bratik.clockweather.util.Version.isNewer(info.version, currentVersion)) {
                UpdateResult.Available(info)
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: "нет связи с GitHub")
        }
    }

    fun releasesPage(): String = PAGE

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "ClockWeather-Android")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                conn.errorStream?.use { it.readBytes() }
                throw IOException("GitHub ответил HTTP $code")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** Чистый разбор ответа GitHub Releases (тестируется без сети). */
object UpdateParser {

    fun parseRelease(json: String, fallbackPage: String): ReleaseInfo? {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }
        val tag = root.optString("tag_name", "").trim()
        if (tag.isEmpty()) return null

        val assets = root.optJSONArray("assets")
        var apkUrl = ""
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name", "")
                val url = a.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                    apkUrl = url
                    break
                }
            }
        }
        // Если APK не приложен — отправляем на страницу релизов.
        if (apkUrl.isEmpty()) apkUrl = fallbackPage

        return ReleaseInfo(
            version = com.bratik.clockweather.util.Version.normalize(tag),
            apkUrl = apkUrl,
            notes = root.optString("body", "").trim(),
            pageUrl = root.optString("html_url", fallbackPage),
        )
    }
}
