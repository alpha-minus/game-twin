package com.twinlore.app.data.library

import android.content.Context
import com.twinlore.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * `saved_tabs.group_folder_name` and `vaults.folder_name` are copied wholesale
 * out of a synced snapshot and are not sanitized on adoption --
 * [LibraryFolderManager.resolveFolderName] deliberately prefers the persisted
 * column over the sanitizer, so the sanitizer's dot-dot guard never sees them.
 * Both reach [PageGroupSaveTargets.deleteGroupFolder], which built
 * `File(File(home, vaultName), groupFolderName)` and called `deleteRecursively()`
 * on it with no containment check: a *directory* delete steered by a foreign
 * snapshot.
 *
 * The home folder is a subdirectory of the temp folder here so the escape
 * targets stay inside the rule's own tree.
 */
@RunWith(RobolectricTestRunner::class)
class PageGroupDeleteContainmentTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var ctx: Context
    private lateinit var settings: SettingsRepository
    private lateinit var fm: LibraryFolderManager
    private lateinit var home: File

    @Before
    fun setup() {
        ctx = RuntimeEnvironment.getApplication()
        home = tempFolder.newFolder("home")
        settings = mock()
        whenever(settings.homeFolderUri).thenReturn(MutableStateFlow("file://${home.absolutePath}"))
        whenever(settings.vaultStorageFolderUri).thenReturn(MutableStateFlow(null))
        fm = LibraryFolderManager(ctx, settings)
    }

    @Test
    fun a_group_folder_name_with_a_parent_reference_cannot_escape_the_home_folder() = runTest {
        // A legitimate group folder, to prove the guard does not change what a
        // normal delete does.
        val legit = File(File(home, "MyVault"), "Zelda Walkthrough").apply { mkdirs() }
        File(legit, "page-1.mht").writeText("x")

        // `<home>/MyVault/../../outside` canonicalizes to a directory outside the
        // home folder entirely.
        val victim = tempFolder.newFolder("outside")
        File(victim, "important.txt").writeText("do not delete me")

        PageGroupSaveTargets.deleteGroupFolder(ctx, fm, "MyVault", "../../outside")

        assertTrue("a snapshot-supplied parent reference deleted a directory outside home", victim.exists())
        assertTrue(File(victim, "important.txt").exists())

        PageGroupSaveTargets.deleteGroupFolder(ctx, fm, "MyVault", "Zelda Walkthrough")
        assertFalse("a legitimate group folder must still be deleted", legit.exists())
    }

    @Test
    fun a_vault_folder_name_with_a_parent_reference_cannot_escape_the_legacy_delete() = runTest {
        // The legacy layout is `<home>/<vaultName>/groups/<groupTabId>/`, and
        // vaultName is the same unsanitized vaults.folder_name column.
        // `<home>/../groups/escape` canonicalizes outside the home folder.
        val victim = tempFolder.newFolder("groups", "escape")
        File(victim, "important.txt").writeText("do not delete me")

        PageGroupSaveTargets.legacyDeleteGroupFolder(ctx, fm, "..", "escape")

        assertTrue("a snapshot-supplied vault folder name escaped the home folder", victim.exists())
        assertTrue(File(victim, "important.txt").exists())
    }
}
