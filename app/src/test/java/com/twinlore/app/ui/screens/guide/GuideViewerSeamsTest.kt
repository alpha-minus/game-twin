package com.twinlore.app.ui.screens.guide

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The three guide viewers are hosted in the fold panel's overlay window, where
 * there is no activity and hiltViewModel() cannot resolve.
 *
 * What guards that is not a runtime assertion but the absence of a top-level
 * call. A hiltViewModel() inside a branch the panel never composes is fine; one
 * at the top of the composable is a crash on the device the first time the
 * panel opens a guide, in the one surface whose whole job is to be the reliable
 * way back to the app.
 */
class GuideViewerSeamsTest {

    private val viewers = listOf(
        "src/main/java/com/twinlore/app/ui/screens/guide/SingleGuideViewerScreen.kt",
        "src/main/java/com/twinlore/app/ui/screens/group/PageGroupViewerScreen.kt",
        "src/main/java/com/twinlore/app/ui/screens/pdf/PdfGuideViewer.kt",
    )

    @Test
    fun `no viewer asks for a view model before it knows it needs one`() {
        val offenders = viewers.flatMap { path ->
            sourceAt(path).readLines().withIndex()
                .filter { (_, line) -> line.contains("hiltViewModel(") && line.startsWith("    val ") }
                .map { (index, line) -> "${path.substringAfterLast('/')}:${index + 1} ${line.trim()}" }
        }
        assertTrue(
            "hiltViewModel() at a viewer's top level cannot resolve in the fold " +
                "panel's window: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the app's state is optional in every viewer`() {
        val missing = viewers.filterNot { path ->
            sourceAt(path).readText().contains("mainViewModel: MainViewModel? =")
        }
        assertTrue(
            "a viewer must take a nullable MainViewModel so a window with no " +
                "activity can pass null: $missing",
            missing.isEmpty(),
        )
    }

    /**
     * The chrome the viewers are built from, which is the part that actually
     * bit: every Navigable button read focus colour, haptics and LED from a
     * SettingsViewModel, so a viewer hosted in the panel crashed on its own top
     * bar with "Cannot create an instance of class SettingsViewModel" before a
     * single guide had a chance to render. Scanning only the screens missed it.
     */
    private val chrome = listOf(
        "src/main/java/com/twinlore/app/ui/navigation/NavigableIconButton.kt",
        "src/main/java/com/twinlore/app/ui/navigation/NavigableButton.kt",
        "src/main/java/com/twinlore/app/ui/navigation/NavigableActionButton.kt",
        "src/main/java/com/twinlore/app/ui/navigation/NavigableMenuItem.kt",
        "src/main/java/com/twinlore/app/ui/navigation/NavigableTextField.kt",
        "src/main/java/com/twinlore/app/ui/screens/GuideBrowserFindOnPage.kt",
    )

    @Test
    fun `the chrome a viewer is built from asks for no view model either`() {
        val offenders = chrome.filter { path ->
            sourceAt(path).readLines().any { line ->
                val code = line.trimStart()
                !code.startsWith("//") && !code.startsWith("*") && code.contains("hiltViewModel(")
            }
        }
        assertTrue(
            "a button composed inside a hosted viewer cannot resolve a " +
                "hiltViewModel(): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the guard is looking at all three viewers`() {
        assertTrue(sourceAt(viewers[0]).readText().contains("fun SingleGuideViewerScreen"))
        assertTrue(sourceAt(viewers[1]).readText().contains("fun PageGroupViewerScreen"))
        assertTrue(sourceAt(viewers[2]).readText().contains("fun PdfGuideViewer"))
    }

    private fun sourceAt(relative: String): File =
        listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: error("$relative not found from ${File(".").absolutePath}")
}
