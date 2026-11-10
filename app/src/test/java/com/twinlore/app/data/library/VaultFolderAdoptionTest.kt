package com.twinlore.app.data.library

import com.twinlore.app.data.local.VaultEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The bug this covers: a vault imported from a RetroArch playlist carries the
 * No-Intro trailing-article title ("Legend of Zelda, The: A Link to the Past &
 * Four Swords") and therefore sanitizes to a DIFFERENT folder than the same
 * game entered leading-article on another device ("The Legend of Zelda: ...").
 * Syncthing replicates the other device's folder into the home tree, but the
 * local vault's folder_name points at a directory that does not exist, so its
 * artwork picker / guides / maps all come up empty next to a folder full of
 * files.
 */
class VaultFolderAdoptionTest {

    private fun vault(
        id: String,
        name: String,
        folderName: String? = null,
    ) = VaultEntity(
        id = id,
        name = name,
        romPath = null,
        romFilename = null,
        artworkVibrantArgb = null,
        systemeid = null,
        gameId = null,
        source = null,
        raGameId = null,
        steamAppId = null,
        lastOpened = null,
        createdAt = Date(0),
        folderName = folderName,
    )

    @Test
    fun `adopts the leading-article folder for a trailing-article vault`() {
        val v = vault(
            id = "d242e163",
            name = "Legend of Zelda, The: A Link to the Past & Four Swords",
            folderName = "Legend of Zelda, The A Link to the Past & Four Swords",
        )
        val dirs = listOf(
            "The Legend of Zelda A Link to the Past & Four Swords",
            "The Legend of Zelda - A Link to the Past",
            "Pokemon Emerald",
        )

        val plan = VaultFolderAdoption.planAdoptions(listOf(v), dirs)

        assertEquals(
            mapOf("d242e163" to "The Legend of Zelda A Link to the Past & Four Swords"),
            plan,
        )
    }

    @Test
    fun `adopts the trailing-article folder for a leading-article vault`() {
        val v = vault(
            id = "v1",
            name = "The Legend of Zelda: A Link to the Past & Four Swords",
            folderName = "The Legend of Zelda A Link to the Past & Four Swords",
        )
        val dirs = listOf("Legend of Zelda, The A Link to the Past & Four Swords")

        val plan = VaultFolderAdoption.planAdoptions(listOf(v), dirs)

        assertEquals(mapOf("v1" to "Legend of Zelda, The A Link to the Past & Four Swords"), plan)
    }

    @Test
    fun `leaves a vault alone when its own folder exists`() {
        val v = vault(
            id = "v1",
            name = "Legend of Zelda, The: A Link to the Past",
            folderName = "Legend of Zelda, The A Link to the Past",
        )
        // Both spellings on disk: the vault's own folder wins, no adoption.
        val dirs = listOf(
            "Legend of Zelda, The A Link to the Past",
            "The Legend of Zelda A Link to the Past",
        )

        assertTrue(VaultFolderAdoption.planAdoptions(listOf(v), dirs).isEmpty())
    }

    @Test
    fun `does not hijack a folder another vault already owns`() {
        val trailing = vault(
            id = "trailing",
            name = "Legend of Zelda, The: A Link to the Past",
            folderName = "Legend of Zelda, The A Link to the Past",
        )
        val leading = vault(
            id = "leading",
            name = "The Legend of Zelda: A Link to the Past",
            folderName = "The Legend of Zelda A Link to the Past",
        )
        // Only the leading-article folder exists, and `leading` owns it.
        val dirs = listOf("The Legend of Zelda A Link to the Past")

        assertTrue(VaultFolderAdoption.planAdoptions(listOf(trailing, leading), dirs).isEmpty())
    }

    @Test
    fun `matches the on-disk casing when adopting`() {
        val v = vault(
            id = "v1",
            name = "Legend of Zelda, The: A Link to the Past",
            folderName = "Legend of Zelda, The A Link to the Past",
        )
        val dirs = listOf("the legend of zelda a link to the past")

        assertEquals(
            mapOf("v1" to "the legend of zelda a link to the past"),
            VaultFolderAdoption.planAdoptions(listOf(v), dirs),
        )
    }

    @Test
    fun `ignores vaults with no article to move`() {
        val v = vault(id = "v1", name = "Chrono Trigger", folderName = "Chrono Trigger")
        assertTrue(VaultFolderAdoption.planAdoptions(listOf(v), listOf("Super Metroid")).isEmpty())
    }

    @Test
    fun `does not treat a mid-title comma as an article`() {
        // "Tails" is not an article, so nothing is rewritten and nothing adopted.
        val v = vault(id = "v1", name = "Sonic, Tails and Knuckles", folderName = "Sonic, Tails and Knuckles")
        val dirs = listOf("Tails and Knuckles, Sonic", "and Knuckles Sonic, Tails")
        assertTrue(VaultFolderAdoption.planAdoptions(listOf(v), dirs).isEmpty())
    }

    @Test
    fun `normalizeArticle moves a trailing article to the front`() {
        assertEquals(
            "The Legend of Zelda: A Link to the Past & Four Swords",
            LibraryFolderManager.normalizeArticle("Legend of Zelda, The: A Link to the Past & Four Swords"),
        )
        assertEquals("The Legend of Zelda", LibraryFolderManager.normalizeArticle("Legend of Zelda, The"))
        assertEquals(
            "A Link to the Past - Redux",
            LibraryFolderManager.normalizeArticle("Link to the Past, A - Redux"),
        )
        // Untouched: no trailing article.
        assertEquals("Chrono Trigger", LibraryFolderManager.normalizeArticle("Chrono Trigger"))
        assertEquals(
            "Sonic, Tails and Knuckles",
            LibraryFolderManager.normalizeArticle("Sonic, Tails and Knuckles"),
        )
    }
}
