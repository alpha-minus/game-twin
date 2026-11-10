package com.twinlore.app.ui.screens.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.R
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.legal.LegalDocument
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.navigation.NavigableMenuItem
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.theme.getFocusContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * One dialog for all three long-form legal texts (Impressum, privacy policy, terms of use),
 * bilingual. The language toggle is dialog-local and not persisted: a legal text is read once,
 * and a persisted preference here would be one more thing to migrate for no benefit.
 *
 * The scroll body is a focusable D-pad target (see the [Modifier.onKeyEvent] block below): the
 * old single-focusable Impressum dialog this replaces could not be scrolled by a controller,
 * which is invisible with four lines of Impressum text but a real defect once the terms of use
 * and a multi-page privacy policy are behind the same dialog. Under § 305 Abs. 2 BGB terms bind
 * only if the user can actually take notice of them, so this has to work on the handheld, not
 * just with a mouse in a preview.
 *
 * No device is available to confirm the D-pad path end to end, so this deliberately avoids
 * relying on Compose's implicit fallback behaviour (auto-focus-on-open, arrow-key focus search
 * finding an unindicated focus target) in favour of the explicit patterns already established
 * elsewhere in this codebase: request focus on open like `BookmarkManagementDialog` does, and
 * draw the focus state like `NavigableMenuItem` does, so the two things that would otherwise be
 * silent are instead explicit and, on inspection, correct by construction.
 */
@Composable
fun LegalDocumentDialog(document: LegalDocument, onDismiss: () -> Unit) {
    var german by remember { mutableStateOf(Locale.getDefault().language == "de") }

    val context = LocalContext.current
    val body by produceState(initialValue = "", document, german) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(document.assetPath(german)).bufferedReader().use { it.readText() }
            }.getOrElse { "" }
        }
    }

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val palette = LocalThemePalette.current
    val dialogBackgroundColor = palette.background

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    // One D-pad press moves the body by roughly a third of a phone-sized viewport rather than
    // jumping to the very top/bottom, so the reader can actually track where they are.
    val scrollStepPx = with(LocalDensity.current) { 160.dp.toPx() }

    val bodyFocusRequester = remember { FocusRequester() }
    val bodyInteractionSource = remember { MutableInteractionSource() }
    val isBodyFocused by bodyInteractionSource.collectIsFocusedAsState()

    // Explicit initial focus, matching BookmarkManagementDialog's "focus first item on open"
    // rather than depending on Compose's undocumented first-arrow-key-enters-content fallback.
    // With no device to verify this on, the explicit request is the only thing that's provably
    // correct: the first DPAD_DOWN scrolls the body instead of merely focusing it.
    // Guarded like ControllerTextField and NavigableTextField do: requestFocus() throws if the
    // requester is not attached yet, which can happen if the dialog leaves composition in the
    // same frame it entered it.
    LaunchedEffect(Unit) {
        runCatching { bodyFocusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(document.title(german)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    NavigableMenuItem(
                        content = {
                            Text(
                                text = "Deutsch",
                                color = if (german) focusColorState.color else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        onClick = { german = true },
                        focusId = "legal_lang_de",
                        modifier = Modifier.weight(1f),
                    )
                    NavigableMenuItem(
                        content = {
                            Text(
                                text = "English",
                                color = if (!german) focusColorState.color else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        onClick = { german = false },
                        focusId = "legal_lang_en",
                        modifier = Modifier.weight(1f),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        // The focus target and the focus indicator (background + border) MUST
                        // sit above `.verticalScroll(scrollState)` in this chain, not below it.
                        // `verticalScroll` measures everything after it in the chain with
                        // maxHeight = Infinity (that's what lets the content be taller than the
                        // 480dp viewport) and clips the result back down for display. A modifier
                        // placed after it therefore sizes and positions itself against the full,
                        // unclipped document height, not the viewport: a focus rect built that
                        // way is thousands of pixels tall, which breaks Compose's 2D focus search
                        // (DPAD_DOWN at the bottom looks for a candidate below that rect, i.e.
                        // far below the confirm button, and typically finds nothing and gets
                        // stuck) and a background/border built that way only looks right at
                        // scrollState.value == 0; mid-scroll it degenerates into two vertical
                        // stripes pinned to the screen edges. Putting these modifiers here
                        // instead sizes and positions them against the already-clamped viewport,
                        // which is what both the focus rect and the drawn indicator need to be.
                        // If this ever gets reordered to "tidy up" the chain, that regression
                        // comes back.
                        .focusRequester(bodyFocusRequester)
                        .focusable(interactionSource = bodyInteractionSource)
                        .background(
                            if (isBodyFocused) focusColorState.color.getFocusContainer() else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .then(
                            if (isBodyFocused) {
                                Modifier.border(
                                    width = 4.dp,
                                    color = focusColorState.color,
                                    shape = RoundedCornerShape(8.dp),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .verticalScroll(scrollState)
                        .padding(8.dp)
                        // Maps DPAD_UP/DOWN to one animated scroll step each, while there's still
                        // room to move. At either end it reports the key as unhandled so focus
                        // falls through to the language row above or the confirm button below
                        // instead of getting trapped — a real escape, not a coincidental one,
                        // because the focus rect above is the viewport, so the 2D search from its
                        // edge actually lands on an adjacent control.
                        //
                        // Both the guard and the motion read scrollState directly, every time.
                        // There is deliberately no accumulated target, queue or nonce mirroring
                        // the scroll position: the body is also a plain touch-draggable
                        // verticalScroll, so any second copy of "where the document is" has to be
                        // kept in agreement with the real one across drags, flings, cancelled
                        // animations and clamping — and every past attempt to do that desynced
                        // somewhere (scrolling backwards after a swipe, or consuming a key that
                        // then moved nothing). With one source of truth, "key consumed" and
                        // "scroll happens" are the same event.
                        //
                        // The accepted cost: ScrollableState serialises scrolls through a
                        // MutatorMutex, so each new press cancels the previous animation partway
                        // and three fast presses travel less than 3 steps. That truncation is
                        // deliberate. It is bounded, self-limiting and always moves in the
                        // direction of the key. Do not "fix" it by reintroducing a target.
                        .onKeyEvent { keyEvent ->
                            val native = keyEvent.nativeKeyEvent
                            if (native.action == android.view.KeyEvent.ACTION_DOWN) {
                                when (native.keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (scrollState.value < scrollState.maxValue) {
                                            coroutineScope.launch {
                                                scrollState.animateScrollBy(scrollStepPx)
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (scrollState.value > 0) {
                                            coroutineScope.launch {
                                                scrollState.animateScrollBy(-scrollStepPx)
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        },
                ) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        containerColor = dialogBackgroundColor,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = focusColorState.color),
            ) {
                Text(stringResource(R.string.impressum_close))
            }
        },
    )
}
