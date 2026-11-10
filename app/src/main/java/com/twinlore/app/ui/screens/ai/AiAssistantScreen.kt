package com.twinlore.app.ui.screens.ai

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import com.twinlore.app.ui.utils.rememberResourceImage
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.twinlore.app.data.ai.AiMessage
import com.twinlore.app.data.ai.AiRole
import com.twinlore.app.domain.SavedMap
import com.twinlore.app.services.ScreenCaptureService
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.screens.browser.BrowserUndoBanner
import com.twinlore.app.ui.utils.rememberMapThumbnail
import java.io.File
import kotlinx.coroutines.launch

/** Tags this tab's screen-capture requests so the package-wide result broadcast is
 *  consumed here and not by the Map/Image tab's receiver (which would save it). */
private const val SCREENSHOT_TAG_AI = "ai"

/**
 * Common Gemini models offered in the AI view's model picker (mirrors Settings).
 * Refreshed 2026-08-24 against Google's model list: gemini-2.0-flash is shut down;
 * the 3.x flash family is current and the 2.5 stable line still serves. The
 * -latest alias stays first as the recommended default.
 */
private val AI_MODELS = listOf(
    "gemini-flash-latest",
    "gemini-3.7-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-2.5-pro",
)

@OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)
@Composable
fun AiAssistantScreen(
    vaultId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onHide: (() -> Unit)? = null,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    // Current vault chrome visibility, owned by the parent. Drives tap-toggle and
    // whether the conversation + template/input area are shown (tap-only model).
    chromeVisible: Boolean = true,
    onChromeVisibilityChange: ((Boolean) -> Unit)? = null,
    // Host of the left-stick scroll channel; null outside the vault session.
    mainViewModel: com.twinlore.app.ui.MainViewModel? = null,
    // The AI tab stays composed while other tabs are selected — gates the stick-scroll loop.
    isTabVisible: Boolean = true,
    viewModel: AiAssistantViewModel = hiltViewModel(),
) {
    val palette = LocalThemePalette.current
    val context = LocalContext.current
    val rootView = LocalView.current
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // At the latest message when the list can't scroll further down. Drives the
    // scroll-to-latest FAB and arms the extra-push-at-the-bottom chrome reveal.
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }

    // Left-stick Y scrolls the chat (same channel as Achievements/Checklist).
    // Poll .value inside the loop so 60 Hz motion events never recompose.
    if (mainViewModel != null) {
        LaunchedEffect(isTabVisible) {
            if (!isTabVisible) return@LaunchedEffect
            val analogDeadzone = 0.1f
            val pxPerFrame = 56f
            while (true) {
                val v = mainViewModel.vaultSessionListScroll.value
                if (kotlin.math.abs(v) > analogDeadzone) {
                    listState.scrollBy(v * pxPerFrame)
                    kotlinx.coroutines.delay(16)
                } else {
                    kotlinx.coroutines.delay(200)
                }
            }
        }
    }
    val scope = rememberCoroutineScope()
    // Latest chrome visibility for the long-lived collector below (don't capture the
    // first composition's value — see the rememberUpdatedState pattern).
    val chromeVisibleNow = androidx.compose.runtime.rememberUpdatedState(chromeVisible)
    // Set when the user scrolls UP during a pin-to-bottom settle, so we abort and
    // don't yank them back down.
    var pinAborted by remember { mutableStateOf(false) }

    // Scroll to the TRUE bottom of the page, accounting for two things a naive
    // animateScrollToItem(last) misses: (1) a final message taller than the viewport
    // (we add its overshoot), and (2) the composer / template row + bars that appear
    // with the chrome and shrink the chat viewport — we wait for them to settle, then
    // scroll past the newly-hidden tail so the last lines sit above the template row.
    suspend fun pinToBottom(smooth: Boolean) {
        pinAborted = false
        onChromeVisibilityChange?.invoke(true)
        val firstLast = listState.layoutInfo.totalItemsCount - 1
        if (firstLast < 0) return
        if (smooth) listState.animateScrollToItem(firstLast)
        // Wait out the chrome's padding + composer animation so the viewport is at its
        // final (smaller) size before we measure the overshoot.
        kotlinx.coroutines.delay(320)
        if (pinAborted) return
        val info = listState.layoutInfo
        val lastIdx = info.totalItemsCount - 1
        if (lastIdx < 0) return
        val item = info.visibleItemsInfo.lastOrNull { it.index == lastIdx }
        val overshoot = if (item != null)
            (item.size - (info.viewportEndOffset - info.viewportStartOffset)).coerceAtLeast(0)
        else 0
        if (smooth) listState.animateScrollToItem(lastIdx, overshoot)
        else listState.scrollToItem(lastIdx, overshoot)
    }

    // Landing at the bottom does NOT show the chrome — the last message stays readable
    // full-screen. It only ARMS the reveal: once the list sits at the bottom and the
    // scroll that brought it there (drag or fling) has settled, one more downward push
    // brings the chrome back (see chromeScroll below). Leaving the bottom disarms.
    var pushArmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { atBottom to listState.isScrollInProgress }
            .collect { (bottom, scrolling) ->
                if (bottom && !scrolling) pushArmed = true
                else if (!bottom) pushArmed = false
            }
    }
    // Scrolling UP into history (finger drags down, available.y > 0) HIDES the chrome.
    // Scrolling DOWN toward the latest shows nothing on the way down — the chrome only
    // reappears on a fresh drag past the end while already settled at the bottom
    // (pushArmed above; programmatic and fling deltas don't count), which then pins the
    // last lines above the incoming composer. So the swipe that lands you at the bottom
    // never pops the bars by itself. An up-scroll also aborts any in-flight pin so we
    // never fight the user. Tapping the conversation also toggles chrome (see the
    // LazyColumn below).
    val chromeScroll = remember(onChromeVisibilityChange) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 6f) {
                    onChromeVisibilityChange?.invoke(false)
                    pinAborted = true
                } else if (
                    available.y < -6f && source == NestedScrollSource.Drag &&
                    pushArmed && !chromeVisibleNow.value && !listState.canScrollForward
                ) {
                    pushArmed = false
                    // The pin's scrolls can be cancelled if the triggering drag still
                    // holds the scroll mutex; chrome is already shown by then, so a
                    // lost overshoot is cosmetic.
                    scope.launch { runCatching { pinToBottom(smooth = true) } }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(vaultId) { viewModel.start(vaultId) }
    // Back closes the keyboard first if it's open, otherwise leaves the screen.
    BackHandler {
        if (imeVisible) {
            keyboard?.hide()
            focusManager.clearFocus()
        } else {
            onBack()
        }
    }

    // Submits the message and dismisses the keyboard.
    val doSend = {
        viewModel.send()
        keyboard?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text, state.isStreaming) {
        val count = state.messages.size + if (state.isStreaming) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // Pick from images already saved to this vault (the Image tab), not the file system.
    var showVaultImagePicker by remember { mutableStateOf(false) }

    // Capture the display the APP'S WINDOW is on, resolved fresh at capture time via
    // the root view's attached display (same approach as the Map/Image tab). On a
    // dual-screen handheld this is the screen Game Twin is on; reliably detecting and
    // capturing the *other* (game) screen has never worked, so we don't attempt it here.
    val resolveDisplayId: () -> Int = {
        rootView.display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
    }
    // MediaProjection — service stays alive so the permission dialog only shows once
    // per session; subsequent taps send ACTION_CAPTURE to the running service.
    val screenshotPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val saveDir = File(context.cacheDir, "screenshots").absolutePath
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_SAVE_DIR, saveDir)
                putExtra(ScreenCaptureService.EXTRA_DISPLAY_ID, resolveDisplayId())
                putExtra(ScreenCaptureService.EXTRA_REQUEST_TAG, SCREENSHOT_TAG_AI)
            }
            context.startForegroundService(intent)
        }
    }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == ScreenCaptureService.ACTION_SCREENSHOT_TAKEN) {
                    // Only consume screenshots this tab requested — the Map/Image tab
                    // also listens for this package-wide broadcast and would otherwise
                    // save our temporary AI screenshot to the Images tab.
                    if (intent.getStringExtra(ScreenCaptureService.EXTRA_REQUEST_TAG) != SCREENSHOT_TAG_AI) return
                    val path = intent.getStringExtra(ScreenCaptureService.EXTRA_FILE_PATH) ?: return
                    val f = File(path)
                    if (f.exists()) {
                        viewModel.attachImage(f, System.currentTimeMillis())
                        f.delete()
                    }
                }
            }
        }
        val filter = IntentFilter(ScreenCaptureService.ACTION_SCREENSHOT_TAKEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val onTakeScreenshot: () -> Unit = {
        if (ScreenCaptureService.isRunning) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_CAPTURE
                putExtra(ScreenCaptureService.EXTRA_SAVE_DIR, File(context.cacheDir, "screenshots").absolutePath)
                putExtra(ScreenCaptureService.EXTRA_DISPLAY_ID, resolveDisplayId())
                putExtra(ScreenCaptureService.EXTRA_REQUEST_TAG, SCREENSHOT_TAG_AI)
            }
            context.startForegroundService(intent)
        } else {
            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenshotPermission.launch(mpm.createScreenCaptureIntent())
        }
    }

    var showTemplateEditor by remember { mutableStateOf(false) }
    if (showTemplateEditor) {
        TemplateEditorDialog(
            templates = state.templates,
            onDismiss = { showTemplateEditor = false },
            onSave = { viewModel.saveTemplates(it); showTemplateEditor = false },
        )
    }

    if (showVaultImagePicker) {
        VaultImagePickerDialog(
            images = state.vaultImages,
            onDismiss = { showVaultImagePicker = false },
            onPick = { resourceId ->
                viewModel.attachVaultImage(resourceId)
                showVaultImagePicker = false
            },
        )
    }

    Box(modifier = modifier.fillMaxSize().background(palette.background)) {
        // Blurred game artwork behind the chat (kept subtle for text legibility). Held back
        // until a key exists: the setup state is a flat theme-coloured page, like the
        // Achievements tab's connect-an-account state.
        val artworkUri = rememberResourceImage(state.artworkResourceId)
        if (artworkUri != null && state.apiKeyConfigured) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(30.dp)
                    .alpha(0.22f),
                contentScale = ContentScale.Crop,
            )
        }

        Column(
        modifier = Modifier
            .fillMaxSize()
            // Only pad the bottom (nav bar). The top is already handled by the vault
            // top bar's own status-bar padding plus aiTopPad; padding the top here too
            // double-counts the status bar and leaves an empty strip under the top bar.
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
            .padding(bottom = bottomInset)
            // targetSdk 35 forces edge-to-edge, so adjustResize is ignored — without
            // imePadding the input row sits behind the keyboard.
            .imePadding(),
    ) {
        // No in-tab header — the vault top bar shows the game name, Clear lives in
        // the chips row, and Hide lives in the no-key card.

        // Chrome stays up on the setup page. Tap-to-toggle lives on the conversation list, which
        // the no-key state doesn't compose, so hiding the bars there left the bottom bar with no
        // way back — the tab became a dead end. Entering with a key up shows the bars too; from
        // there the conversation's own tap handler takes over.
        LaunchedEffect(state.apiKeyConfigured) {
            onChromeVisibilityChange?.invoke(true)
        }
        // …and they stay up for as long as there is no key, whatever hid them.
        LaunchedEffect(state.apiKeyConfigured, chromeVisible) {
            if (!state.apiKeyConfigured && !chromeVisible) onChromeVisibilityChange?.invoke(true)
        }

        // With no key there is no conversation to show, so the setup card IS the tab: centred on
        // the flat theme background, the same shape the Achievements tab uses when no account is
        // connected. Composing the empty message list and the composer behind it only advertised
        // a chat that cannot happen, over a blurred backdrop meant for content. Once a key is
        // accepted the real view appears, chrome and all.
        // if/else, never `return@Column`. Column is an inline composable, so a non-local return
        // out of its content lambda jumps straight past the compiler-emitted group-closing calls
        // for everything it skips. apiKeyConfigured starts false and flips to true a few frames
        // after the tab composes (the key loads asynchronously), so that jump happened on every
        // vault open — and the next recomposition died in ComposerImpl.endRoot with
        // "ArrayIndexOutOfBoundsException: length=…; index=-1".
        if (!state.apiKeyConfigured) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                AiKeyEntry(
                    onSave = { viewModel.setApiKey(it) },
                    onHide = onHide?.let { hide -> { viewModel.hideBottomTab(); hide() } },
                )
            }
        } else {

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(chromeScroll),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    // Tap-only chrome: tapping the conversation toggles the top/bottom
                    // bars + the template/input composer. The message history itself
                    // stays visible so you can read immersively (like the Image tab,
                    // where the image stays and only the chrome toggles).
                    .pointerInput(chromeVisible) {
                        detectTapGestures(onTap = { onChromeVisibilityChange?.invoke(!chromeVisible) })
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages) { msg -> MessageBubble(msg) }
                // Typing indicator while waiting for the first streamed token.
                if (state.isStreaming && state.messages.lastOrNull()?.role == AiRole.USER) {
                    item { TypingIndicator() }
                    // First-token watchdog notice ("taking longer than usual…"); the
                    // ViewModel clears it on the first delta or at the 60s timeout.
                    state.slowNotice?.let { notice ->
                        item {
                            Text(
                                notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                            )
                        }
                    }
                }
            }
            // Scroll-to-latest button when reading back through history.
            if (!atBottom && state.messages.isNotEmpty()) {
                Surface(
                    onClick = { scope.launch { pinToBottom(smooth = true) } },
                    shape = CircleShape,
                    color = palette.foreground.copy(alpha = 0.14f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(40.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Scroll to latest",
                        tint = palette.foreground,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }

        state.errorBanner?.let { err ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    SelectionContainer(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { viewModel.retry() }) {
                        Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        state.pendingAttachmentPath?.let { path ->
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "Attachment",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                IconButton(onClick = { viewModel.removePendingAttachment() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = palette.foreground)
                }
            }
        }

        // Undo banner after clearing the conversation (mirrors the browser tabs UX).
        BrowserUndoBanner(
            visible = state.showClearedUndo,
            message = "Conversation cleared",
            onUndo = { viewModel.undoClearSession() },
            focusColor = palette.foreground,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        // The chips row + input are only useful once a key is configured, and
        // they hide with the chrome (tap-only immersive read mode) — the message
        // history above stays visible.
        // apiKeyConfigured is always true here — this is the else branch of the no-key path.
        if (chromeVisible) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Model dropdown — change the Gemini model without leaving the chat.
            var modelOpen by remember { mutableStateOf(false) }
            Box {
                AssistChip(
                    onClick = { modelOpen = true },
                    label = {
                        // Short label: drop the "gemini-" prefix so the distinguishing
                        // suffix (flash-latest, 2.5-pro, …) shows; clip the front if long.
                        val short = state.model.removePrefix("gemini-").ifBlank { "Model" }
                        val shown = if (short.length > 14) "…" + short.takeLast(13) else short
                        Text(shown, maxLines = 1)
                    },
                    trailingIcon = {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
                DropdownMenu(expanded = modelOpen, onDismissRequest = { modelOpen = false }) {
                    AI_MODELS.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            trailingIcon = if (m == state.model) {
                                { Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp)) }
                            } else null,
                            onClick = { viewModel.setModel(m); modelOpen = false },
                        )
                    }
                }
            }
            // Clear the conversation context. Clears immediately; an UNDO banner
            // (below) lets the user restore it within a few seconds.
            AssistChip(
                onClick = { viewModel.clearSession() },
                label = { Text("Context") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Clear context",
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            // Templates dropdown — tap a template to drop it into the input. Sits right
            // before Auto-shot because the two are coupled: picking a template can
            // auto-attach a screenshot.
            var templatesOpen by remember { mutableStateOf(false) }
            Box {
                AssistChip(
                    onClick = { templatesOpen = true },
                    label = { Text("Templates") },
                    trailingIcon = {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
                DropdownMenu(
                    expanded = templatesOpen,
                    onDismissRequest = { templatesOpen = false },
                    modifier = Modifier.width(330.dp),
                ) {
                    state.templates.forEach { t ->
                        DropdownMenuItem(
                            modifier = Modifier.fillMaxWidth(),
                            text = { Text(t) },
                            onClick = {
                                viewModel.insertTemplate(t)
                                // Auto-attach a screenshot so picking a template is one tap.
                                if (state.autoScreenshot) onTakeScreenshot()
                                templatesOpen = false
                            },
                        )
                    }
                    if (state.templates.isNotEmpty()) Divider()
                    DropdownMenuItem(
                        modifier = Modifier.fillMaxWidth(),
                        text = { Text("Edit templates…") },
                        leadingIcon = {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = { templatesOpen = false; showTemplateEditor = true },
                    )
                }
            }
            // Auto-attach a screenshot whenever a template is picked (last in the row).
            FilterChip(
                selected = state.autoScreenshot,
                onClick = { viewModel.setAutoScreenshot(!state.autoScreenshot) },
                label = { Text("Auto-shot") },
                leadingIcon = {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach", tint = palette.foreground)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Take screenshot") },
                        leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onTakeScreenshot() },
                    )
                    DropdownMenuItem(
                        text = { Text("Pick saved image") },
                        leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; showVaultImagePicker = true },
                    )
                }
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = { viewModel.onInputChange(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about your game…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { doSend() }),
                maxLines = 4,
            )
            IconButton(
                onClick = doSend,
                enabled = !state.isStreaming &&
                    (state.input.isNotBlank() || state.pendingAttachmentPath != null),
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = palette.foreground)
            }
        }
        } // end key-gated chips + input
        } // else: key configured
        } // Column
    } // Box
}

@Composable
private fun AiKeyEntry(
    onSave: (String) -> Unit,
    onHide: (() -> Unit)? = null,
) {
    val palette = LocalThemePalette.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var key by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.foreground.copy(alpha = 0.08f))
            .padding(14.dp),
    ) {
        Text(
            "Enter your Gemini API key to start chatting",
            color = palette.foreground,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("AIza…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.foreground,
                unfocusedTextColor = palette.foreground,
                focusedBorderColor = palette.foreground.copy(alpha = 0.6f),
                unfocusedBorderColor = palette.foreground.copy(alpha = 0.3f),
                cursorColor = palette.foreground,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSave(key) },
            enabled = key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save API key")
        }
        TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }) {
            Text("Get free API key", color = palette.foreground.copy(alpha = 0.8f))
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.OpenInNew,
                contentDescription = "Opens Google AI Studio",
                tint = palette.foreground.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
        }
        if (onHide != null) {
            TextButton(onClick = onHide) {
                Text("Hide AI tab", color = palette.foreground.copy(alpha = 0.8f))
            }
            Text(
                "You can turn it back on in Settings.",
                color = palette.foreground.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    val palette = LocalThemePalette.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.foreground.copy(alpha = 0.06f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600, delayMillis = i * 150),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot$i",
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.foreground.copy(alpha = alpha)),
                )
            }
        }
    }
}

@OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
private fun TemplateEditorDialog(
    templates: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val palette = LocalThemePalette.current
    val draft = remember { mutableStateListOf(*templates.toTypedArray()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Match the chat input: back closes the keyboard first, then dismisses.
    BackHandler {
        if (imeVisible) {
            keyboard?.hide()
            focusManager.clearFocus()
        } else {
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.background,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(min = 280.dp),
            ) {
                Text(
                    text = "Edit templates",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.foreground,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    draft.forEachIndexed { index, value ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { draft[index] = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = palette.foreground,
                                    unfocusedTextColor = palette.foreground,
                                    focusedBorderColor = palette.foreground.copy(alpha = 0.6f),
                                    unfocusedBorderColor = palette.foreground.copy(alpha = 0.3f),
                                    cursorColor = palette.foreground,
                                ),
                            )
                            // Up/down to reorder this template.
                            Column {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val t = draft[index]; draft[index] = draft[index - 1]; draft[index - 1] = t
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowUp,
                                        contentDescription = "Move up",
                                        tint = palette.foreground.copy(alpha = if (index > 0) 0.7f else 0.25f),
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (index < draft.size - 1) {
                                            val t = draft[index]; draft[index] = draft[index + 1]; draft[index + 1] = t
                                        }
                                    },
                                    enabled = index < draft.size - 1,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Move down",
                                        tint = palette.foreground.copy(alpha = if (index < draft.size - 1) 0.7f else 0.25f),
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { draft.removeAt(index) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete template",
                                    tint = palette.foreground.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { draft.add("") },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = palette.foreground,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add template", color = palette.foreground)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = palette.foreground)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleaned = draft.map { it.trim() }.filter { it.isNotEmpty() }
                            onSave(cleaned)
                        },
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultImagePickerDialog(
    images: List<SavedMap>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val palette = LocalThemePalette.current
    val pickable = images.filter { it.resourceId != null }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.background,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(min = 280.dp),
            ) {
                Text(
                    text = "Pick a saved image",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.foreground,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (pickable.isEmpty()) {
                    Text(
                        "No saved images for this game yet. Add images to the Image tab first.",
                        color = palette.foreground.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 360.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(pickable, key = { it.id }) { map ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = rememberMapThumbnail(map.resourceId),
                                    contentDescription = map.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { map.resourceId?.let(onPick) },
                                )
                                Text(
                                    map.title,
                                    color = palette.foreground,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = palette.foreground)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: AiMessage) {
    val palette = LocalThemePalette.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val isUser = msg.role == AiRole.USER
    val bubbleColor = if (isUser) palette.foreground.copy(alpha = 0.12f) else palette.foreground.copy(alpha = 0.06f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .padding(10.dp),
        ) {
            msg.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.height(6.dp))
            }
            if (msg.text.isNotBlank()) {
                // SelectionContainer allows partial selection + the system Copy/Select-all
                // toolbar; the copy button below is a one-tap "copy the whole message".
                // Assistant answers render Markdown; user messages stay plain text.
                SelectionContainer {
                    if (isUser) {
                        Text(msg.text, color = palette.foreground)
                    } else {
                        MarkdownText(msg.text, color = palette.foreground)
                    }
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(msg.text))
                        android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End).size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy message",
                        tint = palette.foreground.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
