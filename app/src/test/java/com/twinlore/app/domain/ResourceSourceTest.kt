package com.twinlore.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceSourceTest {
    @Test
    fun fromRaw_local_link_returns_LOCAL_LINK() {
        assertEquals(ResourceSource.LOCAL_LINK, ResourceSource.fromRaw("local_link"))
    }

    @Test
    fun local_link_raw_round_trips() {
        assertEquals("local_link", ResourceSource.LOCAL_LINK.raw)
    }
}
