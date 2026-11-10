package com.twinlore.app.ui.screens

import com.twinlore.app.RepoFiles
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State that [LibraryViewModel]'s `init` block writes must be declared above it.
 *
 * Kotlin runs property initialisers and init blocks in source order, so a
 * property declared below the block does not exist yet while the block runs.
 * The block launches a coroutine on `viewModelScope` - `Dispatchers.Main.immediate`,
 * which starts executing synchronously - and that coroutine reaches
 * `_isDiscovering.value` inside `discoverVaultsFromFileSystem`. Declared below,
 * it is null there, and the app dies on the first frame with an NPE from
 * `MutableStateFlow.setValue`. Seen on the Thor 2026-09-05, on the first launch
 * after an install.
 *
 * A source-order check rather than a reproduction: the crash needs the
 * coroutine to win a race against the rest of the constructor, so a test that
 * tries to trigger it would pass whether or not the bug is present. The
 * ordering is the thing that is actually true or false, so that is what this
 * asserts.
 */
class LibraryViewModelInitOrderTest {

    private val source by lazy {
        RepoFiles.at("app/src/main/java/com/twinlore/app/ui/screens/LibraryViewModel.kt").readText()
    }

    @Test
    fun `every flow the init block can write is declared above it`() {
        val initAt = source.indexOf("\n    init {")
        assertTrue("no init block found in LibraryViewModel", initAt > 0)

        // What the init block's coroutine reaches, via discoverVaultsFromFileSystem().
        val written = listOf(
            "_isDiscovering",
            "_imagesPreloaded",
            "_vaultsRefreshTrigger",
            "discoveredForUri",
        )
        written.forEach { name ->
            val declaredAt = source.indexOf("private va")
                .let { _ -> Regex("""private va[lr] $name\b""").find(source)?.range?.first ?: -1 }
            assertTrue("$name is never declared", declaredAt >= 0)
            assertTrue(
                "$name is declared below LibraryViewModel's init block, so the coroutine " +
                    "that block launches sees it as null. Move the declaration above the block.",
                declaredAt < initAt,
            )
        }
    }
}
