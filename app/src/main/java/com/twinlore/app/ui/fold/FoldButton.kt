package com.twinlore.app.ui.fold

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.R
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.fold.FoldHandleLook
import com.twinlore.app.data.fold.FoldHandleStyle
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.navigation.longPressHandler
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.utils.gamepadFocusable
import com.twinlore.app.ui.utils.runWithHapticAndLed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Whether the overlay button belongs on screen: landscape only, matching how
 * LibraryStatusIndicators gates itself. There is no opt-in setting; a press
 * without the overlay permission explains itself and offers the grant.
 */
fun shouldShowFoldButton(isLandscape: Boolean): Boolean = isLandscape

/**
 * Whether GuideBrowserTopBar should render its own fold button, as opposed to
 * deferring to VaultSessionTopBar's copy.
 *
 * When embedded in a vault session, VaultSessionTopBar normally owns the button
 * (site 4) and GuideBrowserTopBar (site 5) stays quiet to avoid a duplicate. But
 * [vaultSessionTopBarVisible] can be false for several independent reasons —
 * the software keyboard open without the browser URL field focused, the Achievements wallpaper screensaver, or the
 * active tab's own auto-hide-on-idle flag (see
 * `com.twinlore.app.ui.screens.vaultSessionTopBarChromeVisible` /
 * `vaultSessionTopBarRenders`, the single source of truth VaultSessionScreen
 * itself renders from) — and GuideBrowserTopBar's own visibility runs on a
 * different, interaction-timeout clock that ignores all of them. Whenever
 * VaultSessionTopBar is not actually on screen for any of those reasons, this
 * bar is the only one left standing and must carry the fold button itself.
 *
 * Standalone (not embedded — no VaultSessionTopBar exists at all) always shows
 * its own button regardless of [vaultSessionTopBarVisible]: nothing else could.
 *
 * The invariant this preserves, scoped to the Guide and Browser tabs (the only
 * two that host this bar): for every (isEmbeddedInVaultSession,
 * vaultSessionTopBarVisible) combination, exactly one fold button is reachable
 * — this one, xor VaultSessionTopBar's — never zero, never two.
 *
 * It is deliberately not a claim about the whole vault session. Elsewhere in a
 * session there is genuinely no fold button on screen: the Map
 * tab with its chrome hidden, the Achievements wallpaper screensaver, and the
 * Guide TOC drawer all suppress both bars at once. Those states have no host
 * top bar to put a button in, so the way out of them is to bring chrome back
 * first, not to fold from them.
 */
fun shouldShowFoldButtonInHostTopBar(
    isEmbeddedInVaultSession: Boolean,
    vaultSessionTopBarVisible: Boolean,
): Boolean = !isEmbeddedInVaultSession || !vaultSessionTopBarVisible

/** What [FoldButton] should do in response to a [FoldResult]. Kept as a pure
 * mapping (see [foldResultAction]) so the dispatch is unit-testable without a
 * Compose host — the inline `when` this replaces was invisible to a plain
 * unit test and could silently drop a branch or swap two outcomes. */
enum class FoldAction { NONE, REQUEST_PERMISSION, REPORT_FAILURE }

/**
 * Maps a fold attempt's outcome to the action the button takes. [FoldResult.FOLDED]
 * and [FoldResult.ALREADY_FOLDED] both mean the app is (now) folded — nothing left
 * to do. [FoldResult.PERMISSION_MISSING] sends the user to the grant screen.
 * [FoldResult.TAB_FAILED] means the fold was refused to avoid stranding the app
 * behind nothing, and the user needs to be told, since they pressed a button and
 * nothing visibly happened.
 */
fun foldResultAction(result: FoldResult): FoldAction = when (result) {
    FoldResult.FOLDED, FoldResult.ALREADY_FOLDED -> FoldAction.NONE
    FoldResult.PERMISSION_MISSING -> FoldAction.REQUEST_PERMISSION
    FoldResult.TAB_FAILED -> FoldAction.REPORT_FAILURE
}

/** What a tap on the button raises, before anything happens. */
enum class FoldPress { SHOW_SWAP_HINT, ENTER }

/**
 * Decides what a tap on the button does. Extracted for the same reason
 * [foldResultAction] was: the `when` this replaces sat inside a composable that
 * needs `hiltViewModel()`, so it could not be tested at all.
 *
 * One notice, before the second entry, and only where there is a second
 * screen to hold for. The first press goes straight in: it is for seeing what
 * the mode is, and the one-time word about Overlay Mode is already under the
 * button for that. A dialog about a hold gesture on top of it was two
 * explanations for one press. [enteredBefore] is whether that first entry has
 * happened; [canSwapFromHere] is whether [FoldController.canSwapFrom] says
 * there is a second screen, and without one there is no gesture worth
 * teaching.
 */
fun foldPressAction(
    swapHintSeen: Boolean,
    canSwapFromHere: Boolean,
    enteredBefore: Boolean,
): FoldPress = when {
    !swapHintSeen && canSwapFromHere && enteredBefore -> FoldPress.SHOW_SWAP_HINT
    else -> FoldPress.ENTER
}

/** What a hold on the button raises. */
enum class FoldLongPress { SWAP, SAY_NO_SECOND_SCREEN }

/**
 * Decides what a hold on the button does. Extracted for the reason
 * [foldPressAction] is: the `when` it replaces sat inside a composable that
 * needs `hiltViewModel()`, so it could not be tested at all.
 *
 * A hold moves the app itself to the other screen and leaves it unfolded,
 * which is the same promise the panel's Return keeps when it is held. With no
 * other screen the button says so rather than doing nothing: the user pressed
 * something and held it, and silence reads as a broken control.
 */
fun foldLongPressAction(canSwapFromHere: Boolean): FoldLongPress =
    if (canSwapFromHere) FoldLongPress.SWAP else FoldLongPress.SAY_NO_SECOND_SCREEN

/** Same key set the D-pad/gamepad confirm buttons send; mirrors the private
 * `activateOnA` helper in GameStatsScreen.kt. IconButton's own `clickable`
 * already answers touch taps, but does not reliably answer these key codes on
 * this handheld, so the confirm keys are dispatched to [onClick] directly.
 *
 * `internal` rather than private because it is shared, and no longer only
 * within this file: [PinTopBarButtonContent] sits beside the fold button in the
 * same bar, and
 * [com.twinlore.app.ui.screens.ShowcaseEmblemButtonContent] uses it from
 * another package for the same D-pad behaviour. [FoldButtonContent] itself is
 * not a caller any more, having moved to `longPressHandler` when it grew a
 * second action; this stayed here because the other two still need it, and
 * moving it somewhere neutral is a separate change. */
internal fun Modifier.activateOnConfirmKey(onClick: () -> Unit): Modifier = onKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown &&
        (event.key == Key.ButtonA || event.key == Key.DirectionCenter || event.key == Key.Enter)
    ) {
        onClick()
        true
    } else {
        false
    }
}

/**
 * The presentational half. Visibility is a parameter so it can be tested
 * without pushing display qualifiers around.
 *
 * Focus ring, D-pad confirm handling and gamepad focusability follow the same
 * pattern as GameStatsScreen's FocusableIconButton: a plain IconButton doesn't
 * paint a focus ring or reliably answer D-pad confirm presses on this
 * controller-driven handheld, and every other control in these top bars
 * (NavigableIconButton, the search button, the app emblem) supplies both.
 * [LocalCachedFocusColor] keeps this Hilt-free, same as that precedent, so
 * FoldButtonContent stays unit-testable with a bare createComposeRule.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoldButtonContent(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onLongClick: (() -> Unit)? = null,
) {
    if (!visible) return
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusColor = LocalCachedFocusColor.current?.value ?: MaterialTheme.colorScheme.primary
    // A Box rather than an IconButton: IconButton has no long-press slot, and a
    // second clickable nested inside one never receives the gesture. Everything
    // IconButton was supplying (32dp target, centred content, focusable against
    // this interaction source) is spelled out instead.
    Box(
        modifier = modifier
            .size(32.dp)
            .testTag("fold_button")
            .then(
                if (isFocused) {
                    Modifier
                        .background(focusColor.copy(alpha = 0.22f), CircleShape)
                        .border(2.dp, focusColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .gamepadFocusable()
            .focusable(interactionSource = interactionSource)
            // The controller path. Activation moves from key-down to key-up
            // under threshold, which is what a button with two actions has to
            // do; LibraryTopMenuBar's search button already works this way.
            //
            // This order is load-bearing and the tests know it. combinedClickable
            // installs its own key handler that answers DirectionCenter, Enter,
            // NumPadEnter and Spacebar with a plain onClick, and the two
            // handlers are competing for the same four-key confirm set (minus
            // ButtonA, which only longPressHandler knows about). With this order
            // longPressHandler wins and all four keys can hold; swap the two
            // lines and every one of them folds instead of swapping, including
            // ButtonA. FoldButtonTest's four `holding <key> is a long press`
            // tests fail as a set if anyone reorders these.
            .longPressHandler(
                onLongPress = { onLongClick?.invoke() },
                onPress = onClick,
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            // A small rectangle over a larger one: the behaviour, drawn.
            imageVector = Icons.Default.PictureInPictureAlt,
            contentDescription = stringResource(R.string.fold_button_description),
            tint = tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Opens Game Twin over whatever is running: the handle, the panel, and the
 * app goes to the back. Landscape only, with no setting gating it.
 *
 * A tap enters overlay on the screen the button is on. A hold is the
 * destination past that: overlay on the other screen, panel over there and
 * the game over here, in one gesture. On a device with one screen the hold
 * says so and does nothing else.
 *
 * If the overlay permission has not been granted, pressing the button does
 * not enter: it shows [FoldPermissionDialog] explaining why the permission is
 * needed. Continuing from that dialog opens the system grant screen;
 * dismissing it leaves the app exactly as it was. Backgrounding the app with
 * no tab would leave no way back, so nothing proceeds without the permission.
 *
 * If the permission check passes but the overlay still cannot be put on
 * screen, the app deliberately stays foregrounded and a toast says so.
 *
 * [onInputReceived], when supplied, resets the host screen's idle timer the
 * way its other top-bar controls do.
 */
@Composable
fun FoldButton(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onInputReceived: (() -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    if (!shouldShowFoldButton(isLandscape)) return

    // Only the fallback for a store that cannot answer: [performEnter] reads
    // the stored values at press time so the handle comes back where it was
    // left. These carry a default until DataStore has emitted, and a press
    // before that emission is exactly the case that used to drop a handle
    // dragged to the bottom back at the default mid-height edge. They come
    // from SettingsRepository so that changing a default changes both at once.
    val positionX by settingsViewModel.foldPositionX
        .collectAsState(initial = SettingsRepository.DEFAULT_FOLD_POSITION_X)
    val positionY by settingsViewModel.foldPositionY
        .collectAsState(initial = SettingsRepository.DEFAULT_FOLD_POSITION_Y)
    val handleAlpha by settingsViewModel.foldHandleAlpha
        .collectAsState(initial = FoldHandleStyle.DEFAULT_ALPHA)
    val handleSizeDp by settingsViewModel.foldHandleSizeDp
        .collectAsState(initial = FoldHandleStyle.DEFAULT_SIZE_DP)
    val handleRecess by settingsViewModel.foldHandleRecess
        .collectAsState(initial = FoldHandleStyle.DEFAULT_RECESS)
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)
    val focusColor = LocalCachedFocusColor.current?.value ?: MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val controller = rememberFoldController()
    var showPermissionDialog by remember { mutableStateOf(false) }
    // Shown once, before the second entry. True until DataStore says otherwise
    // so a cold-start press enters rather than opening a dialog the user has
    // already dismissed once.
    val swapHintSeen by settingsViewModel.foldSwapHintSeen.collectAsState(initial = true)
    // False until DataStore says otherwise, for the same reason: a press that
    // beats the first emission enters rather than teaching a hold.
    val enteredBefore by settingsViewModel.overlayEnteredBefore.collectAsState(initial = false)
    var showSwapHint by remember { mutableStateOf(false) }
    // Same "true until DataStore says otherwise" as the swap hint, so a cold
    // start does not flash a hint that was dismissed long ago.
    val overlayHintSeen by settingsViewModel.foldOverlayHintSeen.collectAsState(initial = true)
    val hintContent = rememberOverlayHintContent()
    val vaultHasContent by hintContent.currentVaultHasContent().collectAsState(initial = false)

    fun toast(text: String) {
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun reportFailure() = toast("Could not show over the game")

    fun currentDisplayId(): Int? = view.display?.displayId

    /**
     * Folds the app down to the handle on [targetDisplayId], putting the
     * handle back exactly where it was left. The panel stays down: a press
     * from the app asks for the screen back, and the handle is the way to the
     * panel from there.
     *
     * The stored position is read here rather than taken from the collected
     * state above, because that state carries a default until DataStore has
     * emitted and a press can easily beat the first emission — on a cold start
     * it always does. A handle dragged to the bottom then came back at the
     * default mid-height edge, which is the thing this reads around. The
     * collected values are the fallback for a store that cannot answer.
     *
     * The handle's whole look comes along for the same reason: opacity, size
     * and reading size describe one object, and half-restoring it would be its
     * own surprise.
     *
     * On [scope], which composes on the main thread — [FoldController.fold]
     * adds a window, and that is the main thread's alone.
     */
    fun performEnter(targetDisplayId: Int) {
        scope.launch {
            val x = runCatching { settingsViewModel.foldPositionX.first() }.getOrDefault(positionX)
            val y = runCatching { settingsViewModel.foldPositionY.first() }.getOrDefault(positionY)
            val storedLook = FoldHandleLook.of(
                alpha = runCatching { settingsViewModel.foldHandleAlpha.first() }
                    .getOrDefault(handleAlpha),
                sizeDp = runCatching { settingsViewModel.foldHandleSizeDp.first() }
                    .getOrDefault(handleSizeDp),
                recessScale = runCatching { settingsViewModel.foldHandleRecess.first() }
                    .getOrDefault(handleRecess),
            )
            val result = controller.fold(targetDisplayId, x, y, storedLook)
            when (foldResultAction(result)) {
                FoldAction.REQUEST_PERMISSION -> showPermissionDialog = true
                FoldAction.REPORT_FAILURE -> reportFailure()
                // An entry that went through is the first press having done
                // its job; the next one may teach the hold.
                FoldAction.NONE -> settingsViewModel.setOverlayEnteredBefore(true)
            }
        }
    }

    /** The tap: overlay on the screen the button is on. */
    fun performEnterHere() {
        // No fallback to the default display. Guessing here defeats the guard
        // AndroidFoldPlatform.showTab keeps: a tab put on display 0 while
        // moveAppToBack() backgrounds an app that was on display 4 is the
        // stranding scenario itself. If the view cannot say which display it
        // is on, refuse the same way a tab that never appeared does.
        val displayId = currentDisplayId()
        if (displayId == null) {
            reportFailure()
            return
        }
        performEnter(displayId)
    }

    /** The hold: overlay on the other screen, or a word about there not being one. */
    /**
     * The hold: the app itself moves to the other screen and stays unfolded.
     *
     * Not an entry into overlay, which is what this hold used to be. Moving
     * the app is the thing a second screen is actually for, and the panel's
     * own Swap already covers moving the panel; entering overlay over there
     * was neither, and it cost the gesture that could be both.
     *
     * The permission is not consulted, because nothing is drawn over anything:
     * this is the app being relaunched onto a display it is allowed to use.
     */
    fun performSwapScreen() {
        val displayId = currentDisplayId()
        if (displayId == null) {
            reportFailure()
            return
        }
        val action = foldLongPressAction(
            canSwapFromHere = controller.canSwapFrom(displayId),
        )
        when (action) {
            // A toast rather than a silent no-op: the user pressed something
            // and needs to know it was heard.
            FoldLongPress.SAY_NO_SECOND_SCREEN ->
                toast(context.getString(R.string.fold_swap_none))
            FoldLongPress.SWAP ->
                if (!controller.swapAppDisplay(displayId)) reportFailure()
        }
    }

    fun openGrantScreen() {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // No activity handles the overlay-permission screen on this device.
            reportFailure()
        }
    }

    if (showSwapHint) {
        FoldSwapHintDialog(
            onContinue = {
                showSwapHint = false
                settingsViewModel.setFoldSwapHintSeen(true)
                // The press that opened this was a request to enter, and it is
                // not thrown away.
                performEnterHere()
            },
            // Backed out rather than acknowledged, so the hint has not been
            // read and the next press offers it again.
            onDismiss = { showSwapHint = false },
        )
    }

    if (showPermissionDialog) {
        FoldPermissionDialog(
            onContinue = {
                showPermissionDialog = false
                openGrantScreen()
            },
            onDismiss = { showPermissionDialog = false },
        )
    }

    // A Box so the hint below can anchor to the button rather than to the bar.
    Box {
    FoldButtonContent(
        visible = true,
        modifier = modifier,
        tint = tint,
        onClick = {
            runWithHapticAndLed(
                view, hapticFeedbackIntensity, ledEnabled, focusColor,
                ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs,
            ) {
                onInputReceived?.invoke()
                val displayId = currentDisplayId()
                val action = foldPressAction(
                    swapHintSeen = swapHintSeen,
                    canSwapFromHere = displayId != null && controller.canSwapFrom(displayId),
                    enteredBefore = enteredBefore,
                )
                when (action) {
                    FoldPress.SHOW_SWAP_HINT -> showSwapHint = true
                    FoldPress.ENTER -> performEnterHere()
                }
            }
        },
        onLongClick = {
            runWithHapticAndLed(
                view, hapticFeedbackIntensity, ledEnabled, focusColor,
                ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs,
            ) {
                onInputReceived?.invoke()
                performSwapScreen()
            }
        },
    )
    // Said once, and only with a guide or an image to show over the game:
    // before that the button has nothing to do and the hint explains a
    // control that would open an empty panel.
    if (shouldShowOverlayHint(vaultHasContent, overlayHintSeen, isLandscape)) {
        OverlayHintPopup(onDismiss = { settingsViewModel.setFoldOverlayHintSeen(true) })
    }
    }
}

/** The port that answers whether the open vault has anything to read. */
@Composable
private fun rememberOverlayHintContent(): OverlayHintContent {
    val appContext = LocalContext.current.applicationContext
    return remember {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                appContext,
                com.twinlore.app.di.FoldControllerEntryPoint::class.java,
            )
            .overlayHintContent()
    }
}

@Composable
private fun rememberFoldController(): FoldController {
    val appContext = LocalContext.current.applicationContext
    return remember {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                appContext,
                com.twinlore.app.di.FoldControllerEntryPoint::class.java,
            )
            .foldController()
    }
}
