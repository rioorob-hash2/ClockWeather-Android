package com.bratik.clockweather.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun comparesSimpleVersions() {
        assertTrue(Version.isNewer("1.5", "1.4"))
        assertFalse(Version.isNewer("1.4", "1.5"))
        assertFalse(Version.isNewer("1.4", "1.4"))
    }

    @Test
    fun comparesNumericSegmentsNotStrings() {
        assertTrue("1.10 должно быть новее 1.9", Version.isNewer("1.10", "1.9"))
        assertFalse(Version.isNewer("1.9", "1.10"))
        assertTrue(Version.isNewer("1.10.1", "1.10"))
        assertFalse(Version.isNewer("1.10", "1.10.1"))
    }

    @Test
    fun stripsPrefixV() {
        assertTrue(Version.isNewer("v1.6", "1.5"))
        assertEquals("1.6", Version.normalize(" v1.6 "))
    }

    @Test
    fun ignoresPrereleaseSuffix() {
        assertTrue(Version.isNewer("1.6-beta", "1.5"))
        assertFalse(Version.isNewer("1.5-beta", "1.5"))
    }

    @Test
    fun handlesGarbage() {
        assertFalse(Version.isNewer("", "1.5"))
        assertFalse(Version.isNewer("abc", "1.5"))
        assertTrue(Version.isNewer("1", "0"))
    }
}
