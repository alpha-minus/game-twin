package com.twinlore.app.ui.fold.guide

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The panel lives in a TYPE_APPLICATION_OVERLAY window. Compose's Popup,
 * Dialog, DropdownMenu and ModalBottomSheet are sub-windows that ask the host
 * view for an application window token, and an overlay window has none, so any
 * of them in the panel's own source is a BadTokenException on the device, in
 * the surface whose whole job is to stay visible over a running game.
 *
 * This scrapes source, which `FoldSettingsCopyTest` deliberately does not: it
 * named its constants so it would not have to. The exception holds because what
 * is banned here is an import. There is no runtime representation of "this file
 * does not use Popup" to assert against, and the alternative to reading the
 * source is finding out on the Thor.
 */
class PanelHasNoSubWindowsTest {

    private val banned = listOf(
        "Popup(",
        "Dialog(",
        "AlertDialog(",
        "DropdownMenu(",
        "ModalBottomSheet(",
        "androidx.compose.ui.window",
    )

    @Test
    fun `no panel source opens a sub-window`() {
        val offenders = panelSourceDir().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                // Comments are allowed to name what is banned, and several of
                // these files explain the ban at length. Only code counts.
                val code = file.readLines()
                    .filterNot { it.trimStart().startsWith("*") }
                    .filterNot { it.trimStart().startsWith("//") }
                    .filterNot { it.trimStart().startsWith("/*") }
                    .joinToString("\n")
                banned.filter { code.contains(it) }.map { "${file.name}: $it" }
            }
            .toList()

        assertTrue(
            "Compose sub-windows cannot exist in an overlay window: $offenders",
            offenders.isEmpty(),
        )
    }

    // The panel's own source is sub-window free, but it hosts three of the
    // app's screens, and those do open dialogs when they are allowed to.
    // Saying so at every call site is what keeps that true here.
    @Test
    fun `every hosted viewer is told what the window can do`() {
        val content = panelSourceDir().resolve("FoldGuideContent.kt").readText()
        val hosted = content.split("ViewerScreen(", "PdfGuideViewer(").size - 1
        val declared = content.split("GuideViewerCapabilities.OVERLAY").size - 1
        assertTrue(
            "every viewer the panel hosts must be passed " +
                "GuideViewerCapabilities.OVERLAY: $declared of $hosted",
            hosted > 0 && declared == hosted,
        )
    }

    // The other half of what the overlay cannot do. A viewer hosted without a
    // link handler falls back to `startActivity` and drops a player out of a
    // running game, or to a null mainViewModel and does nothing at all.
    @Test
    fun `every hosted viewer is given somewhere to send a link`() {
        val content = panelSourceDir().resolve("FoldGuideContent.kt").readText()
        val hosted = content.split("ViewerScreen(", "PdfGuideViewer(").size - 1
        val handled = content.split("onExternalLink = ", "onOpenLinkInBrowser = onExternalLink")
            .size - 1
        assertTrue(
            "every viewer the panel hosts must be handed onExternalLink: $handled of $hosted",
            hosted > 0 && handled >= hosted,
        )
    }

    // A directory that moved would make the tests above pass vacuously, which
    // is the failure mode of every source-scanning check.
    @Test
    fun `the guard is actually looking at the panel's source`() {
        val files = panelSourceDir().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.name }
            .toList()
        assertTrue("no panel sources found", files.size >= 5)
        assertTrue("FoldGuidePanelScreen.kt not scanned", files.contains("FoldGuidePanelScreen.kt"))
        assertTrue("FoldGuideContent.kt not scanned", files.contains("FoldGuideContent.kt"))
    }

    private fun panelSourceDir(): File {
        val relative = "src/main/java/com/twinlore/app/ui/fold/guide"
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Panel source not found from ${File(".").absolutePath}")
    }
}
