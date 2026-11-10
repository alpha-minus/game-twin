package com.twinlore.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release network config is read straight off the repo: a user-installed
 * CA must never be a trust anchor for license, API, or APK traffic.
 */
class NetworkSecurityConfigTest {

    private val main = RepoFiles.at("app/src/main/res/xml/network_security_config.xml").readText()
    private val debug = RepoFiles.at("app/src/debug/res/xml/network_security_config.xml").readText()

    @Test
    fun `release config has no user trust anchor`() {
        assertFalse(main.contains("src=\"user\""))
        assertTrue(main.contains("src=\"system\""))
    }

    @Test
    fun `debug config keeps user anchors for proxy work`() {
        assertTrue(debug.contains("src=\"user\""))
    }
}
