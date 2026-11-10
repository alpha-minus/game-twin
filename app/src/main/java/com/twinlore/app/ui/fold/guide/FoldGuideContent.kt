package com.twinlore.app.ui.fold.guide

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.ui.screens.group.PageGroupViewerScreen
import com.twinlore.app.ui.screens.group.PageGroupViewerViewModel
import com.twinlore.app.ui.screens.guide.GuideViewerCapabilities
import com.twinlore.app.ui.screens.guide.SingleGuideViewerScreen
import com.twinlore.app.ui.screens.guide.SingleGuideViewerViewModel
import com.twinlore.app.ui.screens.pdf.PdfGuideViewModel
import com.twinlore.app.ui.screens.pdf.PdfGuideViewer

const val FOLD_GUIDE_NONE = "No guide open. Open the app to find one."
const val FOLD_GUIDE_OPEN_IN_APP = "Open the app"

/**
 * The guide, in the one window an emulator's presentation cannot cover.
 *
 * All three surfaces are the app's own viewers, not copies of them: a single
 * guide goes to [SingleGuideViewerScreen], a page group to
 * [PageGroupViewerScreen], a PDF to [PdfGuideViewer]. The panel had its own
 * WebView once, and it re-earned every feature the app already had, badly, one
 * complaint at a time: the reading position, the spacing, the advertisement
 * cleanup, image zoom, search, the chapter buttons. Hosting the real reader is
 * how all six stop being the panel's problem.
 *
 * What an overlay window cannot host is switched off through
 * [GuideViewerCapabilities], and the losses are, in full:
 *
 *  - Dialogs and the display sheet, so no text-size sheet, no link menu, no
 *    image-actions menu, no page bookmarks in a PDF. All of them are in the app.
 *  - HTML5 fullscreen video, which asks for an activity and finds none. The
 *    viewer's own guard returns without it rather than crashing.
 *
 * Text selection is not on that list either, though it was: measured on the
 * Thor, a long press in this window selects and offers its toolbar like any
 * other. Copy and Save Marker come with it.
 *
 * Image zoom is not on that list. The viewers draw it inline over the page when
 * sub-windows are unavailable, so tapping an image opens the same zoomable
 * surface, with the same next and previous, that it opens in the app.
 */
@Composable
fun FoldGuideContent(
    guide: FoldGuide?,
    loading: Boolean,
    onOpenApp: () -> Unit,
    /**
     * Where the reader's own chrome ended up, so the panel's bars can follow
     * it: one tap gets everything out of the way of the guide, the way a tap
     * in the app hides the vault session's chrome.
     *
     * A state, never a flip. Every viewer here owns its own bar and reports
     * where it landed - on a tap, on a scroll, on an auto-hide - and answering
     * any of those with a toggle is only right while the two already agree.
     * They do not from the first frame: the panel's collapsed state is
     * persisted and a reader's chrome always starts shown.
     */
    onSetChrome: (Boolean) -> Unit = {},
    /**
     * Where the panel's own bar is, which is where the reader's chrome starts.
     * Persisted, so a panel left with everything cleared off the page opens
     * that way instead of putting one bar back.
     */
    chromeVisible: Boolean = true,
    /** A search in the reader wants the keyboard; the window takes focus for it. */
    onWantsKeyboard: (Boolean) -> Unit = {},
    /** A text selection wants the window focused, with no keyboard. */
    onWantsFocus: (Boolean) -> Unit = {},
    /**
     * A link the reader refused to follow. See
     * [GuideViewerCapabilities.externalNavigation]: in this window a link can
     * neither start an activity nor reach the Browser tab, so it is reported
     * and the panel answers it.
     */
    onExternalLink: (String) -> Unit = {},
    /** Text size, driven by the panel's Settings surface. */
    textZoom: Float = 1f,
    /**
     * How much of the panel's own chrome floats over the bottom of the reader:
     * its bar, and any banner stacked on it. The bar sits over the page rather
     * than below it, so the reader's history strip has to be lifted clear of
     * it or it lands underneath, where it cannot be pressed.
     */
    chromeBelow: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    // A surface with no reader on it has nothing that takes a touch, so the
    // panel's bar could not be brought back from any of the screens below -
    // the ones where you are most likely to want it, because there is nothing
    // else on them to look at. A reader reports where its own chrome landed;
    // with no reader there is nothing to report, so these flip the bar the way
    // every other empty surface in the panel does. The app button consumes its
    // own taps, so it is still one press for one thing.
    val emptyModifier = modifier.tapOnUnhandled { onSetChrome(!chromeVisible) }

    when {
        guide == null && loading -> Loading(modifier)

        // Nothing to read is not a dead end: the app is where guides are found,
        // and the panel is where you are when you notice you need one.
        guide == null -> EmptyWithApp(FOLD_GUIDE_NONE, onOpenApp, emptyModifier)

        guide is FoldGuide.Unsupported -> EmptyWithApp(
            text = "${guide.typeLabel} guides open in the app",
            onOpenApp = onOpenApp,
            modifier = emptyModifier,
        )

        guide is FoldGuide.Web ->
            if (guide.tab.resourceId == null) {
                EmptyWithApp(FOLD_GUIDE_NONE, onOpenApp, emptyModifier)
            } else {
                PanelSingleGuide(
                    guide.tab, onSetChrome, chromeVisible, onWantsKeyboard, onExternalLink,
                    textZoom, chromeBelow, modifier, onWantsFocus = onWantsFocus,
                )
            }

        guide is FoldGuide.Group -> PanelGroupGuide(
            tab = guide.tab,
            onSetChrome = onSetChrome,
            chromeVisible = chromeVisible,
            onWantsKeyboard = onWantsKeyboard,
            onWantsFocus = onWantsFocus,
            onExternalLink = onExternalLink,
            textZoom = textZoom,
            chromeBelow = chromeBelow,
            modifier = modifier,
        )

        guide is FoldGuide.Pdf ->
            if (guide.tab.resourceId == null) {
                EmptyWithApp(FOLD_GUIDE_NONE, onOpenApp, emptyModifier)
            } else {
                PanelPdfGuide(
                    guide.tab, onWantsKeyboard, onExternalLink, onSetChrome, modifier,
                )
            }
    }
}

/**
 * The app's single-guide reader.
 *
 * Chrome starts visible, unlike in the app: it carries the chapter buttons,
 * search and contents, and in a panel there is no vault session around it to
 * offer those instead.
 */
@Composable
private fun PanelSingleGuide(
    tab: SavedTab,
    onSetChrome: (Boolean) -> Unit,
    initialChromeVisible: Boolean,
    onWantsKeyboard: (Boolean) -> Unit,
    onExternalLink: (String) -> Unit,
    textZoom: Float,
    chromeBelow: Dp,
    modifier: Modifier = Modifier,
    onWantsFocus: (Boolean) -> Unit = {},
) {
    // Scroll markers need this view model, and the panel used to pass null for
    // it, which is why the panel had no markers at all. It is built from one
    // singleton, so building it here costs nothing the group viewer's does not.
    val dependencies = rememberPanelViewerDependencies()
    val markerViewModel = rememberOverlayViewModel("single_guide_${tab.id}") {
        SingleGuideViewerViewModel(
            object : SingleGuideViewerViewModel.Repo {
                override fun markersFor(bookmarkId: String) =
                    dependencies.savedTabRepository().getScrollMarkersFlow(bookmarkId)
            },
        )
    }
    val chrome = remember(tab.id) {
        PanelReaderChrome(initiallyVisible = initialChromeVisible, onChange = onSetChrome)
    }
    SingleGuideViewerScreen(
        vaultId = tab.vaultId,
        bookmark = tab,
        // There is no picker behind the panel to go back to, so back is what
        // the top bar's other buttons are: a way to get the chrome out of the
        // way of the page. The button itself is hidden.
        onBack = { chrome.hide() },
        showBack = false,
        chromeVisible = chrome.visible,
        onChromeToggle = { chrome.toggle() },
        onChromeHide = { chrome.hide() },
        onChromeReveal = { chrome.reveal() },
        isActive = true,
        capabilities = GuideViewerCapabilities.OVERLAY,
        onWantsKeyboard = onWantsKeyboard,
        onWantsFocus = onWantsFocus,
        onExternalLink = onExternalLink,
        textZoomOverride = textZoom,
        // The panel's bar, not the vault session's: the viewer's default is
        // sized for the app's, and this one floats over the page.
        chromeBelow = chromeBelow,
        mainViewModel = null,
        viewModel = markerViewModel,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * The app's page-group viewer, which owns its chapters and the navigation
 * between them.
 *
 * Its view model is built here rather than resolved: hiltViewModel() needs an
 * activity it does not have, and Hilt refuses to inject an @HiltViewModel class
 * directly. The three things it is built from are singletons, and the overlay's
 * own store clears it when the panel closes. See [rememberOverlayViewModel].
 */
@Composable
private fun PanelGroupGuide(
    tab: SavedTab,
    onSetChrome: (Boolean) -> Unit,
    chromeVisible: Boolean,
    onWantsKeyboard: (Boolean) -> Unit,
    onExternalLink: (String) -> Unit,
    textZoom: Float,
    chromeBelow: Dp,
    modifier: Modifier = Modifier,
    onWantsFocus: (Boolean) -> Unit = {},
) {
    val dependencies = rememberPanelViewerDependencies()
    val viewModel = rememberOverlayViewModel("page_group_${tab.id}") {
        PageGroupViewerViewModel(
            dependencies.savedTabDao(),
            dependencies.pageGroupEntryDao(),
            dependencies.pageGroupRepository(),
        )
    }
    val chrome = remember(tab.id) {
        PanelReaderChrome(initiallyVisible = chromeVisible, onChange = onSetChrome)
    }
    PageGroupViewerScreen(
        vaultId = tab.vaultId,
        groupTabId = tab.id,
        onBack = { chrome.hide() },
        showBack = false,
        chromeVisible = chrome.visible,
        onChromeToggle = { chrome.toggle() },
        onChromeHide = { chrome.hide() },
        onChromeReveal = { chrome.reveal() },
        isActive = true,
        capabilities = GuideViewerCapabilities.OVERLAY,
        onWantsKeyboard = onWantsKeyboard,
        onWantsFocus = onWantsFocus,
        onExternalLink = onExternalLink,
        textZoomOverride = textZoom,
        chromeBelow = chromeBelow,
        mainViewModel = null,
        viewModel = viewModel,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * The app's PDF viewer, which brings its outline and its search with it.
 *
 * The panel used to decode every page to a bitmap up front, because a lazily
 * rendered PdfRenderer needs its file descriptor held open for the life of a
 * composition that can be torn down from another thread. The app's viewer has
 * no such problem.
 */
@Composable
private fun PanelPdfGuide(
    tab: SavedTab,
    onWantsKeyboard: (Boolean) -> Unit,
    onExternalLink: (String) -> Unit,
    onSetChrome: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dependencies = rememberPanelViewerDependencies()
    val context = LocalContext.current
    val viewModel = rememberOverlayViewModel("pdf_${tab.id}") {
        PdfGuideViewModel(
            dependencies.savedTabRepository(),
            dependencies.resourceResolver(),
            context.applicationContext,
        )
    }
    PdfGuideViewer(
        guide = tab,
        vaultId = tab.vaultId,
        onShowGuidePicker = {},
        // A PDF's links and its search-online actions are the same problem as
        // a guide's: there is nowhere in this window for them to go. Reported
        // rather than swallowed, so the panel can offer the app.
        onOpenLinkInBrowser = onExternalLink,
        onSearchOnline = { text ->
            onExternalLink("https://www.google.com/search?q=" + Uri.encode(text.trim()))
        },
        onYouTube = { text ->
            onExternalLink(
                "https://www.youtube.com/results?search_query=" + Uri.encode(text.trim()),
            )
        },
        mainViewModel = null,
        capabilities = GuideViewerCapabilities.OVERLAY,
        onWantsKeyboard = onWantsKeyboard,
        // The PDF viewer hides its own bar on a tap; the panel's bars go with
        // it, so one tap clears everything off the page.
        onChromeToggle = onSetChrome,
        isTabVisible = true,
        vm = viewModel,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * An empty surface with the one thing that would fill it.
 *
 * Everything the panel can show is something the app went and got. When there
 * is none, the answer is always the same and the panel should say it rather
 * than leaving a blank surface over a running game.
 */
@Composable
internal fun EmptyWithApp(
    text: String,
    onOpenApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onOpenApp) { Text(FOLD_GUIDE_OPEN_IN_APP) }
    }
}

/**
 * The same empty surface without the button, for when the app is not the
 * answer: a filter that happens to match nothing is fixed by changing the
 * filter, and offering to open the app there would be advice to the wrong
 * problem.
 */
@Composable
internal fun EmptyMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
