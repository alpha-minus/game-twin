package com.twinlore.app.data.esde

import org.junit.Assert.*
import org.junit.Test

class EsdeFsAccessTest {
    @Test
    fun `docIdToFsPath maps primary volume tree id to internal storage`() {
        assertEquals("/storage/emulated/0/ES-DE", EsdeFsAccess.docIdToFsPath("primary:ES-DE"))
        assertEquals("/storage/emulated/0/Roms/ES-DE", EsdeFsAccess.docIdToFsPath("primary:Roms/ES-DE"))
    }

    @Test
    fun `docIdToFsPath maps sd-card volume tree id to its mount`() {
        assertEquals("/storage/1A2B-3C4D/ES-DE", EsdeFsAccess.docIdToFsPath("1A2B-3C4D:ES-DE"))
    }

    @Test
    fun `docIdToFsPath handles volume root and rejects colonless ids`() {
        assertEquals("/storage/emulated/0", EsdeFsAccess.docIdToFsPath("primary:"))
        assertNull(EsdeFsAccess.docIdToFsPath("no-colon"))
    }
}
