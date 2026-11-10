package com.twinlore.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the fix for the wrong-image-on-tap bug: the main map image's Coil
 * memory-cache key MUST include the image identity (path). Keying on
 * vaultId + version alone collided different screenshots in the same vault,
 * so Coil returned the first-cached bitmap for every later image.
 */
class MapImageCacheKeyTest {

    @Test
    fun different_images_in_same_vault_get_different_keys() {
        // THE BUG: same vault, same version, two distinct screenshots.
        val a = mapImageMemoryCacheKey("vault1", "content://maps/faxanadu/shot_a.png", 0)
        val b = mapImageMemoryCacheKey("vault1", "content://maps/faxanadu/shot_b.png", 0)
        assertNotEquals(a, b)
    }

    @Test
    fun same_image_is_stable_for_caching() {
        val a = mapImageMemoryCacheKey("vault1", "content://maps/shot.png", 3)
        val b = mapImageMemoryCacheKey("vault1", "content://maps/shot.png", 3)
        assertEquals(a, b)
    }

    @Test
    fun version_still_busts_for_in_place_replace() {
        // Same path, new content (reassign): version bump must change the key.
        val v0 = mapImageMemoryCacheKey("vault1", "content://maps/shot.png", 0)
        val v1 = mapImageMemoryCacheKey("vault1", "content://maps/shot.png", 1)
        assertNotEquals(v0, v1)
    }

    @Test
    fun query_suffix_is_stripped_so_same_file_shares_a_key() {
        val plain = mapImageMemoryCacheKey("vault1", "content://maps/shot.png", 2)
        val withQuery = mapImageMemoryCacheKey("vault1", "content://maps/shot.png?v=2", 2)
        assertEquals(plain, withQuery)
    }

    @Test
    fun null_vault_is_handled() {
        val a = mapImageMemoryCacheKey(null, "content://maps/a.png", 0)
        val b = mapImageMemoryCacheKey(null, "content://maps/b.png", 0)
        assertNotEquals(a, b)
    }

    /**
     * Why the Always on Top panel has to know its vault.
     *
     * The key carries the vault, so a caller that omits it asks for a
     * different entry than the app did for the very same file. The panel used
     * to omit it: it missed everything the app had already decoded and decoded
     * each picture again at full size, in the process that is sharing memory
     * with a running game. A failed decode there draws nothing, and the
     * surface behind it is black, which is what the panel showed.
     */
    @Test
    fun omitting_the_vault_asks_for_a_different_entry_than_the_app() {
        val path = "content://maps/shovel-knight/plains.png"
        val appAsks = mapImageMemoryCacheKey("vault1", path, 0)
        val panelWithoutVault = mapImageMemoryCacheKey(null, path, 0)
        assertNotEquals(
            "a vault-less caller cannot share the app's cached bitmap",
            appAsks,
            panelWithoutVault,
        )
        // Passing the vault is what puts them back on the same entry.
        assertEquals(appAsks, mapImageMemoryCacheKey("vault1", path, 0))
    }
}
