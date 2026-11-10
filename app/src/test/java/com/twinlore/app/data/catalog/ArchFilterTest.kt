package com.twinlore.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchFilterTest {

    private val arm64Device = arrayOf("arm64-v8a", "armeabi-v7a")
    private val x86Device = arrayOf("x86_64", "x86")

    @Test
    fun `matches arm64 variant on arm64 device`() {
        val assets = listOf("app-arm64-v8a.apk", "app-armeabi-v7a.apk", "app-x86_64.apk")
        val result = ArchFilter.filter(assets, arm64Device)
        assertEquals(listOf("app-arm64-v8a.apk"), result)
    }

    @Test
    fun `matches aarch64 naming on arm64 device`() {
        val assets = listOf("emulator-aarch64.apk", "emulator-x86_64.apk")
        val result = ArchFilter.filter(assets, arm64Device)
        assertEquals(listOf("emulator-aarch64.apk"), result)
    }

    @Test
    fun `matches x86_64 on x86 device`() {
        val assets = listOf("app-arm64-v8a.apk", "app-x86_64.apk")
        val result = ArchFilter.filter(assets, x86Device)
        assertEquals(listOf("app-x86_64.apk"), result)
    }

    @Test
    fun `universal always passes`() {
        val assets = listOf("app-universal.apk")
        val result = ArchFilter.filter(assets, arm64Device)
        assertEquals(listOf("app-universal.apk"), result)
    }

    @Test
    fun `returns all assets when none have arch substrings`() {
        val assets = listOf("myapp-release.apk", "myapp-beta.apk")
        val result = ArchFilter.filter(assets, arm64Device)
        assertEquals(listOf("myapp-release.apk", "myapp-beta.apk"), result)
    }

    @Test
    fun `empty input returns empty`() {
        val result = ArchFilter.filter(emptyList(), arm64Device)
        assertTrue(result.isEmpty())
    }
}
