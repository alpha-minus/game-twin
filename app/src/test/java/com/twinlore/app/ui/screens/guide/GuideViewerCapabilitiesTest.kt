package com.twinlore.app.ui.screens.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An overlay window cannot host a Dialog, an AlertDialog or a
 * ModalBottomSheet: they ask for a TYPE_APPLICATION window and the window
 * context refuses. All three guide viewers are hosted in one, so every
 * sub-window they open has to be behind the gate.
 *
 * This counts them in the source rather than trusting that the next one added
 * will remember. There is no runtime representation of "this screen does not
 * open a dialog" to assert against, and the alternative to reading the source
 * is finding out on the Thor, mid-game.
 */
class GuideViewerCapabilitiesTest {

    /** Each viewer, and the composable that is the screen itself. */
    private val viewers = mapOf(
        "src/main/java/com/twinlore/app/ui/screens/guide/SingleGuideViewerScreen.kt"
            to "SingleGuideViewerScreen",
        "src/main/java/com/twinlore/app/ui/screens/group/PageGroupViewerScreen.kt"
            to "PageGroupViewerScreen",
        "src/main/java/com/twinlore/app/ui/screens/pdf/PdfGuideViewer.kt"
            to "PdfGuideViewer",
    )

    private val openers = listOf(
        "AlertDialog(",
        "PageGroupDisplaySheet(",
        "BrowserImageContextMenu(",
        "ModalBottomSheet(",
        "GuideImageZoomViewer(",
        "ConfirmDeleteDialog(",
    )

    // Selection used to be on this list, on the theory that a floating
    // ActionMode needs an application window token. Measured on the Thor at
    // SDK 33: a long press in the panel selects and offers its toolbar, so the
    // overlay now says true, and copy and Save Marker come with it. Sub-windows
    // and external navigation remain genuinely impossible there.
    @Test
    fun `an overlay may open nothing and go nowhere, but it may select`() {
        assertEquals(false, GuideViewerCapabilities.OVERLAY.subWindows)
        assertEquals(false, GuideViewerCapabilities.OVERLAY.externalNavigation)
        assertEquals(true, GuideViewerCapabilities.OVERLAY.textSelection)
        assertEquals(true, GuideViewerCapabilities.ACTIVITY.subWindows)
        assertEquals(true, GuideViewerCapabilities.ACTIVITY.textSelection)
        assertEquals(true, GuideViewerCapabilities.ACTIVITY.externalNavigation)
    }

    @Test
    fun `every sub-window in every viewer is behind the gate`() {
        val unguarded = viewers.flatMap { (path, screenFunction) ->
            val lines = sourceAt(path).readLines()
            lines.withIndex()
                .filter { (index, line) ->
                    val code = line.trimStart()
                    !code.startsWith("//") && !code.startsWith("*") &&
                        openers.any { line.contains(it) } &&
                        // A helper composable's own body is not the gate site;
                        // where it is called from is, and that call is an
                        // opener in its own right.
                        enclosingFunction(lines, index) == screenFunction &&
                        !gatedAbove(lines, index)
                }
                .map { (index, line) -> "${path.substringAfterLast('/')}:${index + 1} ${line.trim()}" }
        }
        assertTrue("sub-window opened without capabilities.subWindows: $unguarded", unguarded.isEmpty())
    }

    /**
     * The gate is a `capabilities.subWindows` within thirty lines above. Not a
     * parser: a viewer is three thousand lines, the gate can sit above a
     * couple of view models and a resolved file, and the alternative to a
     * heuristic here is nothing checking at all.
     */
    private fun gatedAbove(lines: List<String>, index: Int): Boolean =
        (maxOf(0, index - 30) until index).any { lines[it].contains("capabilities.subWindows") }

    /** The nearest top-level `fun` above [index], by name. */
    private fun enclosingFunction(lines: List<String>, index: Int): String? =
        (index downTo 0).firstNotNullOfOrNull { line ->
            TOP_LEVEL_FUN.find(lines[line])?.groupValues?.get(1)
        }

    private companion object {
        val TOP_LEVEL_FUN = Regex("""^(?:internal |private )?fun ([A-Za-z]+)\(""")
    }

    @Test
    fun `the guard is looking at all three viewers`() {
        viewers.forEach { (path, screenFunction) ->
            val source = sourceAt(path).readText()
            assertTrue("$path is not the screen it claims", source.contains("fun $screenFunction("))
            // A viewer with no gate at all would make the test above pass
            // vacuously, which is the failure mode of every source scan.
            assertTrue("no gate at all in $path", source.contains("capabilities.subWindows"))
        }
    }

    private fun sourceAt(relative: String): File =
        listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: error("$relative not found from ${File(".").absolutePath}")
}
