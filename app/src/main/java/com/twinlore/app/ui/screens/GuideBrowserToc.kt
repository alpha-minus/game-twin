package com.twinlore.app.ui.screens

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

private val ROMAN_NUMERAL_PREFIX = Regex("^[IVXivx]{1,6}[.):\\s]")
private val NUMERIC_PREFIX = Regex("^\\d{1,3}([.):]|(\\.\\d{1,3}){1,3}[.):\\s])")
private val TOP_LEVEL_KEYWORDS = listOf(
    "chapter", "section", "part", "appendix", "introduction", "walkthrough",
    "conclusion", "credits", "overview", "controls", "basics", "tips",
    "boss", "weapons", "items", "equipment", "side quests", "frequently",
    "getting started", "prologue", "epilogue", "contents", "solutions",
    "bestiary", "characters", "missions", "quests", "secrets", "collectibles",
    "achievements", "trophies", "strategies", "abilities", "magic", "spells",
    "armors", "enemies", "locations", "mini-games", "minigames", "crafting",
    "shops", "story", "endings", "cheats", "glitches", "legal", "disclaimer",
    "contact", "versions", "table of", "FAQ", "troubleshoot", "changelog",
    "history", "maps", "materias", "summons", "unlockables", "inventory"
)

/** Filter to identify obvious top-level TOC entries. */
internal fun isTopLevelEntry(label: String): Boolean {
    val trimmed = label.trimStart()
    if (NUMERIC_PREFIX.containsMatchIn(trimmed)) return true
    if (ROMAN_NUMERAL_PREFIX.containsMatchIn(trimmed)) return true
    val lower = trimmed.lowercase()
    return TOP_LEVEL_KEYWORDS.any { lower.contains(it) }
}

/** Overlay: scrim + TOC panel sliding in from the right (like bottom drawer but from the side). Transparent. Graphical circle strip when no TOC found (7 positions). */
@Composable
internal fun TocDrawerOverlay(
    entries: List<TocEntry>,
    webView: WebView?,
    currentScrollY: Int,
    maxScrollY: Int,
    focusColor: Color,
    onClose: () -> Unit,
    onEntryClick: (TocEntry) -> Unit,
    tocScrollState: ScrollState? = null,
    basicView: Boolean = true,
    onToggleView: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isGraphicalFallback = entries.size == 7 &&
        entries.getOrNull(0)?.label == "Top" &&
        entries.getOrNull(6)?.label == "Bottom" &&
        entries.getOrNull(1)?.label?.startsWith("Section ") == true
    val baseIndex = entries.indexOfLast { it.scrollY <= currentScrollY }.let { if (it >= 0) it else 0 }
    val activeIndex = if (isGraphicalFallback && entries.size == 7) {
        if (maxScrollY > 0) {
            val ratio = (currentScrollY.toFloat() / maxScrollY).coerceIn(0f, 1f)
            (ratio * 6f).toInt().coerceIn(0, 6)
        } else {
            val prevScroll = entries[entries.lastIndex - 1].scrollY
            val lastScroll = entries[entries.lastIndex].scrollY
            val midpoint = (prevScroll + lastScroll) / 2
            if (currentScrollY >= midpoint) entries.lastIndex else baseIndex
        }
    } else baseIndex
    val panelWidth = if (isGraphicalFallback) {
        (configuration.screenWidthDp * 0.1728f).dp
    } else {
        (configuration.screenWidthDp * 0.6f).dp
    }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                .clickable(onClick = onClose)
        )
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .clickable(enabled = false) {},
                shadowElevation = 0.dp,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                color = if (isGraphicalFallback) Color.Transparent else Color.Black.copy(alpha = 0.7f),
                contentColor = focusColor
            ) {
                if (isGraphicalFallback) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No TOC detected",
                            style = MaterialTheme.typography.labelSmall,
                            color = focusColor.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        TocGraphicalStrip(
                            entries = entries,
                            activeIndex = activeIndex,
                            focusColor = focusColor,
                            onEntryClick = onEntryClick,
                            modifier = Modifier.fillMaxSize().weight(1f)
                        )
                    }
                } else {
                    // Remove entries with 4+ duplicates (same label)
                    val dedupedEntries = remember(entries) {
                        val counts = entries.groupingBy { it.label }.eachCount()
                        entries.filter { (counts[it.label] ?: 0) < 4 }
                    }
                    // Filter entries for basic/detailed view
                    val basicFiltered = remember(dedupedEntries) {
                        dedupedEntries.filter { isTopLevelEntry(it.label) }
                    }
                    val hasBasicEntries = basicFiltered.isNotEmpty()
                    val showBasic = basicView && hasBasicEntries
                    // In basic mode, add Top/Bottom navigation entries
                    val displayEntries = if (showBasic) {
                        remember(basicFiltered, maxScrollY) {
                            val top = TocEntry(label = "Top", scrollY = 0)
                            val bottom = TocEntry(label = "Bottom", scrollY = maxScrollY)
                            listOf(top) + basicFiltered + listOf(bottom)
                        }
                    } else dedupedEntries
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (hasBasicEntries) Modifier.clickable { onToggleView() } else Modifier)
                                .padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Contents",
                                style = MaterialTheme.typography.titleMedium,
                                color = focusColor
                            )
                            if (hasBasicEntries) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = if (showBasic) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                    contentDescription = if (showBasic) "Show all" else "Show basic",
                                    tint = focusColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        val scrollState = tocScrollState ?: rememberScrollState()
                        val coroutineScope = rememberCoroutineScope()
                        // Focus requesters for each entry (controller navigation)
                        val focusRequesters = remember(displayEntries.size) {
                            List(displayEntries.size) { FocusRequester() }
                        }
                        // Active index within the display list
                        val displayActiveIndex = displayEntries.indexOfLast { it.scrollY <= currentScrollY }.let { if (it >= 0) it else 0 }
                        // Auto-focus current chapter entry when TOC opens. Wait one frame so
                        // the entries' FocusRequesters are attached before requesting — a
                        // same-frame request fires before binding and is silently dropped,
                        // leaving nothing focused and dpad dead (same fix as VaultSessionScreen).
                        LaunchedEffect(displayEntries.size, displayActiveIndex) {
                            if (displayEntries.isNotEmpty()) {
                                androidx.compose.runtime.withFrameNanos {}
                                val targetIndex = displayActiveIndex.coerceIn(0, displayEntries.size - 1)
                                try { focusRequesters[targetIndex].requestFocus() } catch (_: Throwable) {}
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(scrollState)
                        ) {
                            displayEntries.forEachIndexed { index, entry ->
                                val isActive = index == displayActiveIndex
                                val interactionSource = remember(index) { MutableInteractionSource() }
                                val isFocused by interactionSource.collectIsFocusedAsState()
                                var itemY by remember { mutableStateOf(0f) }
                                Text(
                                    text = entry.label,
                                    style = if (isActive || isFocused) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                    color = if (isFocused) Color.White else focusColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequesters[index])
                                        .focusable(interactionSource = interactionSource)
                                        .onGloballyPositioned { coords ->
                                            itemY = coords.positionInParent().y
                                        }
                                        .onKeyEvent { keyEvent ->
                                            val code = keyEvent.nativeKeyEvent.keyCode
                                            val action = keyEvent.nativeKeyEvent.action
                                            if (action == android.view.KeyEvent.ACTION_DOWN) {
                                                when (code) {
                                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                        if (index < displayEntries.size - 1) {
                                                            try { focusRequesters[index + 1].requestFocus() } catch (_: Throwable) {}
                                                        }
                                                        true
                                                    }
                                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                        if (index > 0) {
                                                            try { focusRequesters[index - 1].requestFocus() } catch (_: Throwable) {}
                                                        }
                                                        true
                                                    }
                                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                                    android.view.KeyEvent.KEYCODE_BUTTON_A,
                                                    android.view.KeyEvent.KEYCODE_ENTER -> {
                                                        onEntryClick(entry)
                                                        true
                                                    }
                                                    android.view.KeyEvent.KEYCODE_BACK,
                                                    android.view.KeyEvent.KEYCODE_BUTTON_B -> {
                                                        onClose()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else {
                                                // Consume UP for handled buttons
                                                code in listOf(
                                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                                    android.view.KeyEvent.KEYCODE_BUTTON_A,
                                                    android.view.KeyEvent.KEYCODE_ENTER,
                                                    android.view.KeyEvent.KEYCODE_BACK,
                                                    android.view.KeyEvent.KEYCODE_BUTTON_B
                                                )
                                            }
                                        }
                                        .then(
                                            if (isFocused) Modifier.background(
                                                focusColor,
                                                RoundedCornerShape(6.dp)
                                            ) else if (isActive) Modifier.background(
                                                focusColor.copy(alpha = 0.2f),
                                                RoundedCornerShape(6.dp)
                                            ) else Modifier
                                        )
                                        .clickable {
                                            onEntryClick(entry)
                                        }
                                        .padding(vertical = 10.dp, horizontal = if (isActive || isFocused) 6.dp else 0.dp)
                                )
                                // Auto-scroll to focused item
                                if (isFocused) {
                                    LaunchedEffect(index) {
                                        coroutineScope.launch {
                                            val targetScroll = (itemY - 100f).coerceAtLeast(0f).toInt()
                                            scrollState.animateScrollTo(targetScroll)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Graphical representation of 7 scroll positions: big circles for Top, Center, Bottom; small circles in between. Line only between first and last circle. Circles use focus color. */
@Composable
internal fun TocGraphicalStrip(
    entries: List<TocEntry>,
    activeIndex: Int,
    focusColor: Color,
    onEntryClick: (TocEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val lineColor = focusColor.copy(alpha = 0.4f)
    val circleColor = focusColor
    val bigSize = 20.dp
    val smallSize = 10.dp
    val strokeWidthPx = with(density) { 2.dp.toPx() }
    val verticalPaddingPx = with(density) { 16.dp.toPx() }
    val bigSizePx = with(density) { bigSize.toPx() }
    val smallSizePx = with(density) { smallSize.toPx() }
    val totalCirclesHeightPx = 3 * bigSizePx + 4 * smallSizePx
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val contentHeight = size.height - 2 * verticalPaddingPx
            val gapPx = ((contentHeight - totalCirclesHeightPx) / 8f).coerceAtLeast(0f)
            val firstCenterY = verticalPaddingPx + gapPx + bigSizePx / 2f
            val lastCenterY = verticalPaddingPx + 7 * gapPx + totalCirclesHeightPx - bigSizePx / 2f
            drawLine(
                color = lineColor,
                start = Offset(centerX, firstCenterY),
                end = Offset(centerX, lastCenterY),
                strokeWidth = strokeWidthPx
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            entries.forEachIndexed { index, entry ->
                val isBig = index == 0 || index == 3 || index == 6
                val sizeDp = if (isBig) bigSize else smallSize
                val isActive = index == activeIndex
                Box(
                    modifier = Modifier
                        .size(sizeDp)
                        .clip(CircleShape)
                        .background(if (isActive) circleColor else circleColor.copy(alpha = 0.5f))
                        .clickable { onEntryClick(entry) },
                    contentAlignment = Alignment.Center
                ) {}
            }
        }
    }
}

