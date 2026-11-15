package com.bratik.clockweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateParserTest {

    private val page = "https://github.com/rioorob-hash2/ClockWeather-Android/releases"

    @Test
    fun parsesReleaseWithApk() {
        val json = """
            {
              "tag_name": "v1.5",
              "html_url": "https://github.com/x/y/releases/tag/v1.5",
              "body": "Проверка обновлений",
              "assets": [
                {"name": "ClockWeather-1.5.apk", "browser_download_url": "https://example.com/apk"},
                {"name": "notes.txt", "browser_download_url": "https://example.com/notes"}
              ]
            }
        """.trimIndent()
        val info = UpdateParser.parseRelease(json, page)
        assertNotNull(info)
        assertEquals("1.5", info!!.version)
        assertEquals("https://example.com/apk", info.apkUrl)
        assertEquals("Проверка обновлений", info.notes)
    }

    @Test
    fun fallsBackToReleasesPageWithoutApk() {
        val json = """{"tag_name":"v2.0","html_url":"https://gh/rel","assets":[]}"""
        val info = UpdateParser.parseRelease(json, page)
        assertEquals(page, info!!.apkUrl)
    }

    @Test
    fun missingTagGivesNull() {
        assertNull(UpdateParser.parseRelease("""{"assets":[]}""", page))
        assertNull(UpdateParser.parseRelease("не json", page))
        assertNull(UpdateParser.parseRelease("{}", page))
    }
}
