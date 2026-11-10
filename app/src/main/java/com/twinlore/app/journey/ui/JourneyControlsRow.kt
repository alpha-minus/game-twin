package com.twinlore.app.journey.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twinlore.app.journey.domain.JourneyParsing
import com.twinlore.app.journey.domain.TickGenerator
import com.twinlore.app.journey.domain.model.CompletionRow
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.ui.utils.gamepadFocusable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private const val DAY_MILLIS = 86_400_000L

/** The section's name: one rival, their colour, a duel. */
internal const val RIVAL_COMPARE_LABEL = "Compare"

/**
 * What a pending chip says out loud. The outline and the dimmed label say it to the eye; a screen
 * reader gets nothing from either, and "aria" next to "brix" with no distinction between a rival
 * who is on the chart and one who is not is the same problem this state exists to solve.
 *
 * Internal so a test names the same string the panel speaks.
 */
internal fun pendingChipText(name: String): String = "$name, not loaded yet"

/**
 * The colours every text field on this panel uses: the theme accent for the focused border and the
 * cursor, and `onSurface` for the text itself.
 *
 * The text colours are named rather than left implicit, but they are not load-bearing - they are
 * what Material's own defaults resolve to here anyway. What made the rival field unreadable was
 * that it sat behind the keyboard (see the imePadding note in JourneyScreen), not its palette.
 * This is stated explicitly so the next person reading the report does not have to re-derive it,
 * and so a theme change cannot quietly make the field's content colour drift from the rest of the
 * panel's text.
 */
@Composable
private fun journeyFieldColors(accent: androidx.compose.ui.graphics.Color) =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = accent,
        cursorColor = accent,
    )

/**
 * The Journey screen's controls panel: what to chart and over what window, and nothing about how
 * to draw it. Badges or awards, how wide the timeline is and whether the running date is shown are
 * all decided from the journey itself now, because they are one system and picking them apart by
 * hand was asking the reader to do the layout engine's job.
 *
 * Vault entries (`lockedGameId != null`) collapse to just the game title: `start()` already picked
 * the game and auto-loaded, so mode/span/rival/game-id/Load would all be dead controls there.
 */
@Composable
internal fun JourneyControlsRow(
    lockedGameId: Int?,
    lockedGameTitle: String?,
    controls: JourneyControls,
    rivalRoster: List<String>,
    rivalErrors: List<String>,
    /** Whether trying [rivalErrors] again could get a different answer. See the notice's own doc. */
    rivalErrorsRetryable: Boolean,
    /** Roster names the loaded chart knows nothing about yet. See [com.twinlore.app.journey.ui.pendingRivals]. */
    pendingRivals: List<String>,
    playedGames: List<CompletionRow>,
    playedGamesError: Boolean,
    /** Rivals already in this account's cache; the roster offers them as recents. */
    knownRivals: List<String> = emptyList(),
    onForgetRival: (String) -> Unit = {},
    onOpenPlayedGamesPicker: () -> Unit,
    onSelectGame: (Int, String) -> Unit,
    onSetMode: (JourneyMode) -> Unit,
    onSetSpanPreset: (SpanPreset) -> Unit,
    onSetCustomRange: (Int, Int) -> Unit,
    onAddRival: (String) -> Unit,
    onRemoveRival: (String) -> Unit,
    onLoad: () -> Unit,
    /**
     * Re-enters the player on the journey already loaded, or null when there is nothing to re-enter
     * (nothing loaded, or the controls have been changed since and no longer describe it).
     */
    onWatchAgain: (() -> Unit)? = null,
) {
    // The panel is composited rather than tinted: this theme's surfaceVariant is a foreground
    // colour at 12% alpha, so handing it to a Surface over a scrolling background reads as a wash
    // rather than a container. chartPanelColor is the same composite the chart's own panel uses,
    // which keeps the controls and the chart on one material.
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = chartPanelColor(scheme.surfaceVariant, scheme.background),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lockedGameId != null) {
                Text(
                    lockedGameTitle ?: "Locked game",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                // What these settings configure (user request): the journey charts the player's
                // RetroAchievements history, and nothing on the panel said whose data it is.
                Text(
                    "RetroAchievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ModeRow(controls.mode, onSetMode)
                when (controls.mode) {
                    JourneyMode.GAME -> GamePickerField(
                        selectedGameTitle = controls.selectedGameTitle,
                        playedGames = playedGames,
                        playedGamesError = playedGamesError,
                        onOpenPicker = onOpenPlayedGamesPicker,
                        onSelectGame = onSelectGame,
                    )
                    JourneyMode.SPAN -> SpanRow(controls, onSetSpanPreset, onSetCustomRange)
                }
            }

            if (lockedGameId == null) {
                RivalRow(
                    rivalRoster, rivalErrors, rivalErrorsRetryable, pendingRivals,
                    knownRivals, onAddRival, onRemoveRival, onForgetRival,
                )
            }

            if (lockedGameId == null) {
                // Load fills the row on its own until there is a loaded journey to go back into.
                // Closing the player used to be a one-way door: the Ready state, its decoded badges
                // and its measured paths were all still here, and the only way to see them again
                // was a Load that refetched the lot.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusableButton("Load", onLoad, modifier = Modifier.weight(1f))
                    if (onWatchAgain != null) {
                        FocusableButton("Watch again", onWatchAgain, outlined = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(mode: JourneyMode, onSetMode: (JourneyMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentToggle(
            text = "Single game",
            selected = mode == JourneyMode.GAME,
            onClick = { onSetMode(JourneyMode.GAME) },
            modifier = Modifier.weight(1f),
        )
        AccentToggle(
            text = "Time span",
            selected = mode == JourneyMode.SPAN,
            onClick = { onSetMode(JourneyMode.SPAN) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** How many games the picker offers before anyone asks for more, and how many each ask adds. */
internal const val PICKER_FIRST_PAGE = 5
internal const val PICKER_PAGE_STEP = 25

/** What the pinned expand row says: how many games the press will actually reveal. */
internal fun showMoreLabel(remaining: Int): String = "Show ${minOf(PICKER_PAGE_STEP, remaining)} more"

/**
 * How long the picker lets typing settle before it re-filters.
 *
 * Short enough to feel immediate, long enough that a burst of keystrokes costs one scan instead of
 * one per letter. The scan itself is cheap; what it is really buying is that the field's own
 * recomposition on each keystroke no longer drags a whole new result list along with it.
 */
internal const val PICKER_SEARCH_DEBOUNCE_MILLIS = 110L

/** What the wheel in the search field announces while the list is catching up with the typing. */
internal const val PICKER_SEARCHING_LABEL = "Searching"

/**
 * Where the list should sit after "Show more" reveals another page.
 *
 * [revealedBefore] rows were on screen, so the first new one is at index `revealedBefore`. Landing
 * one row above it keeps the last already-seen game in view: the list visibly moves - which is the
 * point, the press previously appended below the fold and looked like nothing had happened - while
 * still showing where the new rows were joined on.
 */
internal fun showMoreScrollTarget(revealedBefore: Int): Int = (revealedBefore - 1).coerceAtLeast(0)

/**
 * The games whose titles contain [query], case-insensitively, or all of them for a blank query.
 *
 * Pulled out of the composable so it can be pinned by a test, and so the one place that decides
 * what "matches" means is not buried in a menu.
 */
internal fun pickerMatches(games: List<CompletionRow>, query: String): List<CompletionRow> =
    if (query.isBlank()) games else games.filter { it.title.contains(query, ignoreCase = true) }

/**
 * How tall the picker's own scroll area may grow. Bounded so the expand row pinned under it is
 * always on screen: unbounded, the row rode at the bottom of an ever-growing menu, below the fold
 * after one press - which on the device read as the last entry being sometimes unselectable.
 */
private val PICKER_LIST_MAX_HEIGHT = 220.dp

/**
 * How tall one game's row is, fixed rather than measured.
 *
 * It buys the exact list height [pickerListHeight] needs. A menu measures its content by intrinsics
 * to decide how wide to be, and a lazy list cannot answer an intrinsic query at all - it throws.
 * `heightIn(max = ...)` does not help, because a clamp still passes the question down; only an exact
 * height answers it outright. So the rows are a known height and the list is their sum, capped.
 *
 * Menu rows are conventionally uniform anyway, and both lines inside one are capped at a single
 * line, so nothing here wraps into a height this does not account for.
 */
private val PICKER_ROW_HEIGHT = 56.dp

/** How tall the list of [rows] games is: their exact height, up to the cap. */
internal fun pickerListHeight(rows: Int): Dp =
    (PICKER_ROW_HEIGHT * rows).coerceAtMost(PICKER_LIST_MAX_HEIGHT)

/**
 * How wide the picker's list is, and with it the menu, since the list is its widest child.
 *
 * Fixed for the same reason the rows are: the menu asks its content for an intrinsic width too, and
 * a lazy list cannot answer that either. It is worth having on its own merits - the menu used to
 * take its width from whichever titles happened to be revealed, so paging or searching resized it
 * under the pointer. Narrow enough to fit a portrait phone with the menu's own margins to spare.
 */
private val PICKER_LIST_WIDTH = 300.dp

/**
 * The game picker for GAME mode: a button showing the currently selected title (or a placeholder)
 * that opens a dropdown of the player's played games, sourced from the same completion cache
 * [JourneyViewModel.loadPlayedGames] fetches.
 *
 * The list opens on the last [PICKER_FIRST_PAGE] games played, most recent first, because that is
 * what someone opening this screen is nearly always after; "Show more" adds [PICKER_PAGE_STEP] at a
 * time for the rest. A player with a thousand games in their history was previously handed all of
 * them in one menu.
 *
 * Search is not paged. It runs over every played game, not over the handful currently revealed -
 * a search that could only find what was already on screen would be worse than no search, and it
 * is the way to reach a game a hundred rows down without pressing "Show more" ten times. The manual
 * id entry that used to sit at the bottom is gone with it: it existed to reach exactly those games,
 * and asking someone to know a RetroAchievements game id was never a real answer.
 */
@Composable
private fun GamePickerField(
    selectedGameTitle: String?,
    playedGames: List<CompletionRow>,
    playedGamesError: Boolean,
    onOpenPicker: () -> Unit,
    onSelectGame: (Int, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // Two queries, deliberately: [search] is what has been typed and belongs to the text field
    // alone, [query] is what the list below is actually showing. Keystrokes only ever move the
    // first, so typing costs a text field's recomposition and nothing else; the list follows once
    // typing settles. When they disagree the wheel is lit, which is exactly the truth - what is on
    // screen is not yet an answer to what has been typed.
    var search by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var revealed by remember { mutableIntStateOf(PICKER_FIRST_PAGE) }
    val listState = rememberLazyListState()
    val pickerScope = rememberCoroutineScope()
    val accent = accentColor()

    LaunchedEffect(search) {
        if (search == query) return@LaunchedEffect
        delay(PICKER_SEARCH_DEBOUNCE_MILLIS)
        query = search
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            FocusableRow(
                onClick = {
                    onOpenPicker()
                    search = ""
                    query = ""
                    revealed = PICKER_FIRST_PAGE
                    expanded = true
                },
                modifier = Modifier.fillMaxWidth(),
                containerColor = accent.copy(alpha = 0.14f),
            ) {
                Text(selectedGameTitle ?: "Choose a game", color = accent, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = accent)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search games") },
                    singleLine = true,
                    colors = journeyFieldColors(accent),
                    trailingIcon = {
                        if (search != query) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .semantics { contentDescription = PICKER_SEARCHING_LABEL },
                                strokeWidth = 2.dp,
                                color = accent,
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).gamepadFocusable(),
                )
                if (playedGames.isEmpty() && playedGamesError) {
                    DropdownMenuItem(
                        text = {
                            Text("Could not load your games.", color = MaterialTheme.colorScheme.error)
                        },
                        onClick = {},
                        enabled = false,
                    )
                }
                val searching = query.isNotBlank()
                // Keyed on the settled query, not on what is being typed: the scan runs once per
                // answer rather than once per letter, and never on an unrelated recomposition.
                val matches = remember(playedGames, query) { pickerMatches(playedGames, query) }
                val shown = if (searching) matches else matches.take(revealed)
                // Lazy, and bounded so the expand row pinned under it never leaves the screen
                // however many pages have been revealed. Lazy is what makes a search over a large
                // library cheap: a plain scrolling Column composes every row it is given, so the
                // first keystroke - which drops paging and hands the list every match at once -
                // paid to build, measure and start an icon load for hundreds of rows that were
                // nowhere near the viewport. Only what fits in PICKER_LIST_MAX_HEIGHT composes now,
                // which is also why the rows can keep their icons.
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .width(PICKER_LIST_WIDTH)
                        .height(pickerListHeight(shown.size)),
                ) {
                    items(shown.size) { index ->
                        val row = shown[index]
                        DropdownMenuItem(
                            text = { PlayedGameRow(row) },
                            onClick = {
                                onSelectGame(row.gameId, row.title)
                                search = ""
                                query = ""
                                expanded = false
                            },
                            modifier = Modifier.height(PICKER_ROW_HEIGHT).gamepadFocusable(),
                        )
                    }
                }
                if (!searching && matches.size > shown.size) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                showMoreLabel(matches.size - shown.size),
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        // The menu stays open: this is one step through a list, not a choice. The
                        // list scrolls to where the new rows joined on, because appending below a
                        // bounded viewport is invisible - the press used to look like it had done
                        // nothing at all.
                        onClick = {
                            val revealedBefore = revealed
                            revealed += PICKER_PAGE_STEP
                            pickerScope.launch {
                                listState.animateScrollToItem(showMoreScrollTarget(revealedBefore))
                            }
                        },
                        modifier = Modifier.gamepadFocusable(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayedGameRow(row: CompletionRow) {
    // A date parse plus a format, per visible row. Unremembered it ran on every keystroke in the
    // search field above, for every row still on screen, to produce the string it produced last
    // time - the row's date does not change while the menu is open.
    val dateLabel = remember(row.mostRecentAwardedDate) {
        JourneyParsing.parseWhen(row.mostRecentAwardedDate)?.let(TickGenerator::fmtDate)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = row.iconPath?.let(JourneyUrls::media),
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(row.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (dateLabel != null) {
                // One line, like the title: the row's height is fixed (see PICKER_ROW_HEIGHT), so
                // nothing in it may wrap.
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SpanRow(
    controls: JourneyControls,
    onSetSpanPreset: (SpanPreset) -> Unit,
    onSetCustomRange: (Int, Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = accentColor()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            FocusableRow(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                containerColor = accent.copy(alpha = 0.14f),
            ) {
                Text(controls.spanPreset.label, color = accent, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = accent)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SpanPreset.entries.forEach { preset ->
                    val selected = preset == controls.spanPreset
                    DropdownMenuItem(
                        text = {
                            Text(
                                preset.label,
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onSetSpanPreset(preset)
                            expanded = false
                        },
                        modifier = Modifier.gamepadFocusable(),
                    )
                }
            }
        }
        if (controls.spanPreset == SpanPreset.CUSTOM) {
            CustomRangeFields(controls.customFromDaysAgo, controls.customToDaysAgo, onSetCustomRange)
        }
    }
}

/** Which end of the custom range a calendar dialog is currently editing. */
private enum class RangeEnd { FROM, TO }

/**
 * The custom span's two dates as tappable fields, each opening a calendar (user request: the
 * sliders could not land on a precise day - a thumb-width was weeks at the five-year end, and
 * the calendar also retires that 5-year ceiling, which was the slider's constraint rather than
 * the journey's). The picker is Material's own, so a day, month and year are each directly
 * reachable, with its built-in keyboard-entry toggle; per the owner, touch-only is acceptable
 * here - the fields themselves still take D-pad focus like every sibling row.
 *
 * The model stays days-ago ints: the picked UTC midnight converts through [customDaysAgo], the
 * ViewModel's span bounds and rebuild keys never learn the difference, and whichever field was
 * edited last, [normalizedCustomRange] keeps "From" the earlier date - the same guarantee
 * computeSpanBounds enforces, honoured here so the labels never read backwards.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeFields(
    fromDaysAgo: Int,
    toDaysAgo: Int,
    onSetCustomRange: (Int, Int) -> Unit,
) {
    val accent = accentColor()
    val now = remember { System.currentTimeMillis() }
    var editing by remember { mutableStateOf<RangeEnd?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            Triple(RangeEnd.FROM, "From", fromDaysAgo),
            Triple(RangeEnd.TO, "To", toDaysAgo),
        ).forEach { (end, label, daysAgo) ->
            FocusableRow(
                onClick = { editing = end },
                modifier = Modifier.fillMaxWidth(),
                containerColor = accent.copy(alpha = 0.08f),
            ) {
                Text(
                    "$label: ${TickGenerator.fmtDate(now - daysAgo.toLong() * DAY_MILLIS)}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.DateRange, contentDescription = "Pick a date", tint = accent)
            }
        }
    }
    editing?.let { end ->
        // key(end): the picker's state is rememberSaveable inside, and switching From to To at
        // the same call site would otherwise hand the second dialog the first one's selection.
        androidx.compose.runtime.key(end) {
            val current = if (end == RangeEnd.FROM) fromDaysAgo else toDaysAgo
            val state = androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis = now - current.toLong() * DAY_MILLIS,
            )
            androidx.compose.material3.DatePickerDialog(
                onDismissRequest = { editing = null },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        state.selectedDateMillis?.let { picked ->
                            val days = customDaysAgo(now, picked)
                            val (f, t) = when (end) {
                                RangeEnd.FROM -> normalizedCustomRange(days, toDaysAgo)
                                RangeEnd.TO -> normalizedCustomRange(fromDaysAgo, days)
                            }
                            onSetCustomRange(f, t)
                        }
                        editing = null
                    }) { Text("OK") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { editing = null }) { Text("Cancel") }
                },
            ) {
                androidx.compose.material3.DatePicker(
                    state = state,
                    // The journey is history: a future day has nothing on it to chart.
                    dateValidator = { it <= now },
                )
            }
        }
    }
}

/**
 * The picked calendar date - the UTC midnight a Material date picker reports - as whole days
 * before [nowMillis], floored: a date picked this afternoon is 0 days ago all day. Clamped at
 * zero so a picker that somehow hands back tomorrow still means "today".
 */
internal fun customDaysAgo(nowMillis: Long, pickedUtcMillis: Long): Int =
    ((nowMillis - pickedUtcMillis) / DAY_MILLIS).toInt().coerceAtLeast(0)

/**
 * From must not begin after To ends, whichever field was edited last: days-ago counts run
 * backwards, so the larger count IS the earlier date and the pair is (from, to) with from the
 * larger. The same swap `computeSpanBounds` applies to the millis it derives - done here as well
 * so the two field labels never read backwards in the meantime.
 */
internal fun normalizedCustomRange(fromDaysAgo: Int, toDaysAgo: Int): Pair<Int, Int> =
    if (fromDaysAgo >= toDaysAgo) fromDaysAgo to toDaysAgo else toDaysAgo to fromDaysAgo

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
private fun RivalRow(
    roster: List<String>,
    rivalErrors: List<String>,
    rivalErrorsRetryable: Boolean,
    pendingRivals: List<String>,
    knownRivals: List<String>,
    onAddRival: (String) -> Unit,
    onRemoveRival: (String) -> Unit,
    onForgetRival: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    // Resets whenever the failing roster itself changes (a new failure, a retry that clears
    // one, a fresh load) — the same lifetime a fresh occurrence of the notice should have.
    var errorsDismissed by remember(rivalErrors) { mutableStateOf(false) }
    val accent = accentColor()
    val keyboard = LocalSoftwareKeyboardController.current
    // The field is the last thing on a panel that is centred in whatever the keyboard leaves of the
    // screen, so focusing it is exactly when it needs to be scrolled back into view.
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    // Adding a name is a pure edit now - the chip appears at once and nothing is fetched until
    // Load - so there is nothing to be busy with and no reason to hold the button.
    val submit: () -> Unit = {
        if (text.isNotBlank()) {
            onAddRival(text)
            text = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(RIVAL_COMPARE_LABEL, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        // One rival: while a chip is up the field would only feed the cap notice, so the chip
        // takes the field's place and removing it brings the field back.
        //
        // Two modes, the way the app-wide NavigableTextField works (see the Settings screen): at
        // rest the field is a plain focusable box the D-pad walks straight over, and only a press
        // (A, or a tap) opens the real text field. A bare OutlinedTextField in the walk was a
        // focus trap for a controller - the arrows moved the cursor inside it rather than the
        // focus out of it.
        if (roster.isEmpty()) {
            var editing by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing) {
                    val editFocus = remember { FocusRequester() }
                    var gainedFocus by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Username") },
                        singleLine = true,
                        // Done rather than the default: adding a rival is the whole point of the
                        // field, and reaching for the plus button after typing a name is a second
                        // gesture for nothing.
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                submit()
                                keyboard?.hide()
                                editing = false
                            },
                        ),
                        colors = journeyFieldColors(accent),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(editFocus)
                            .bringIntoViewRequester(bringIntoView)
                            .onFocusEvent {
                                if (it.isFocused) {
                                    gainedFocus = true
                                    scope.launch { bringIntoView.bringIntoView() }
                                } else if (gainedFocus) {
                                    // Focus moved on - the D-pad walked away, or the keyboard
                                    // closed onto another control. Back to the box, so the field
                                    // can never hold focus it is not using.
                                    editing = false
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Back || event.key == Key.ButtonB || event.key == Key.Escape)
                                ) {
                                    keyboard?.hide()
                                    editing = false
                                    true
                                } else {
                                    false
                                }
                            },
                    )
                    LaunchedEffect(Unit) {
                        runCatching { editFocus.requestFocus() }
                        keyboard?.show()
                    }
                } else {
                    FocusableRow(
                        onClick = { editing = true },
                        // A row that opens an editor, and a screen reader has no other way to
                        // know: the placeholder is the only label there is, and it disappears the
                        // moment a name is typed.
                        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                            contentDescription =
                                "Rival username, " + text.ifBlank { "empty" } + ". Opens the editor."
                            role = Role.Button
                        },
                    ) {
                        Text(
                            text.ifBlank { "Username" },
                            color = if (text.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                FocusableIconButton(
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Add rival") },
                    onClick = submit,
                )
            }
            // Who this account has compared against before. Their data is already cached, so
            // picking one is both fewer keystrokes and a near-instant load; the field stays for
            // anyone new.
            if (knownRivals.isNotEmpty()) {
                Text(
                    RECENT_RIVALS_LABEL,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    knownRivals.take(RECENT_RIVALS_MAX).forEach { name ->
                        RecentRivalChip(
                            name = name,
                            onPick = { onAddRival(name) },
                            onForget = { onForgetRival(name) },
                        )
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                roster.forEach { name ->
                    RivalChip(
                        name = name,
                        hasError = rivalErrors.contains(name),
                        pending = pendingRivals.contains(name),
                        onRemove = { onRemoveRival(name) },
                    )
                }
            }
        }
        if (rivalErrors.isNotEmpty() && !errorsDismissed) {
            RivalErrorNotice(rivalErrors, rivalErrorsRetryable, onDismiss = { errorsDismissed = true })
        }
    }
}


/**
 * What the rival-error notice says, and whether it offers a way out.
 *
 * The advice used to be unconditional, and "Remove and re-add to retry" is an instruction that
 * costs the reader a trip and fixes nothing when the API refused the request outright (a 404 for a
 * name that is not there, a 400 it would not parse). The data layer already tells the two apart;
 * this is the sentence that acts on it.
 *
 * Internal so a test names the same string the panel shows.
 */
internal fun rivalErrorText(names: List<String>, retryable: Boolean): String {
    val who = names.joinToString(", ")
    return if (retryable) {
        "Could not fetch data for $who. Remove and re-add to retry."
    } else {
        "Could not fetch data for $who."
    }
}

/**
 * The same panel every other dismissible notice in this feature is: a 10dp rounded Surface on the
 * composited panel colour, `bodySmall` text, a close button on the end. It was a bare Row in
 * `labelSmall`, which read as a different and less finished component sitting next to its siblings
 * (`NoticeRow`, `ExportNotice`, `MissingGamesNotice`). The text keeps the error colour: this one is
 * the only notice here that reports a failure.
 */
@Composable
private fun RivalErrorNotice(names: List<String>, retryable: Boolean, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = chartPanelColor(scheme.surfaceVariant, scheme.background),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rivalErrorText(names, retryable),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
                modifier = Modifier.weight(1f),
            )
            FocusableIconButton(
                icon = { Icon(Icons.Filled.Close, contentDescription = "Dismiss") },
                onClick = onDismiss,
                modifier = Modifier.size(CHIP_TOUCH_TARGET),
            )
        }
    }
}

/**
 * One roster entry.
 *
 * Three states, because the roster is configure state now and a name can be on it without being on
 * the chart: loaded (solid), [pending] (outlined and dimmed - typed since the last Load, so nobody
 * has tried to fetch them), and [hasError] (the error container). Pending is drawn rather than
 * merely tolerated because the alternative is a chip that looks exactly like a loaded rival for a
 * player who is on no curve anywhere, which is the disagreement between the two surfaces this whole
 * wave is about. It is spoken as well as drawn: an outline is nothing to a screen reader.
 */
@Composable
private fun RivalChip(name: String, hasError: Boolean, pending: Boolean, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = when {
            hasError -> scheme.errorContainer
            pending -> Color.Transparent
            else -> scheme.surface
        },
        border = if (pending) BorderStroke(1.dp, scheme.outline) else null,
        modifier = if (pending) {
            Modifier.semantics(mergeDescendants = true) { contentDescription = pendingChipText(name) }
        } else {
            Modifier
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        ) {
            AsyncImage(
                model = JourneyUrls.userPic(name),
                contentDescription = null,
                modifier = Modifier.size(20.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                // Dimmed while pending, so the chip reads as an intention rather than a fact.
                color = if (pending) scheme.onSurfaceVariant else Color.Unspecified,
            )
            if (hasError) {
                Spacer(Modifier.width(4.dp))
                // Spoken as what it means rather than as the glyph it is: on its own a screen
                // reader announces "exclamation mark", which says nothing about this rival's data
                // having failed to arrive. The notice below the roster carries the same fact in
                // words for everyone else.
                Text(
                    "!",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        contentDescription = "Could not fetch data for $name"
                    },
                )
            }
            FocusableIconButton(
                icon = { Icon(Icons.Filled.Close, contentDescription = "Remove $name") },
                onClick = onRemove,
                modifier = Modifier.size(CHIP_TOUCH_TARGET),
            )
        }
    }
}

/**
 * The hit target for the small close buttons on the roster chips and the rival error notice.
 *
 * They were `Modifier.size(28.dp)`, and `size` fixes both min and max constraints, so
 * `IconButton`'s own `minimumInteractiveComponentSize()` could not expand them: the real target was
 * 28dp, well under the 44dp guideline and half of what every other icon button in this feature
 * offers. Only the box grows - the glyph inside it stays the default `Icon` size that `NoticeRow`,
 * `ExportNotice` and every other dismiss in this feature draw, so the three read as one control at
 * three places rather than as a small variant of it.
 */
private val CHIP_TOUCH_TARGET = 44.dp

/** What the recents row is called, and how many of them it offers before it stops. */
internal const val RECENT_RIVALS_LABEL = "Recent"

private const val RECENT_RIVALS_MAX = 8

/**
 * One recent rival: their face, their name, a press that puts them straight on the roster, and a
 * dismiss that forgets them.
 *
 * A chip rather than a menu entry, so the row reads as the roster chips it is offering to become.
 * The dismiss is a button rather than a long press: the roster chips beside it remove the same
 * way, and a gesture with nothing on screen to announce it is one nobody finds.
 */
@Composable
private fun RecentRivalChip(name: String, onPick: () -> Unit, onForget: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        // Named rather than derived: Material has no content colour for a transparent container,
        // so the dismiss glyph inherited whatever was ambient and came out all but invisible on
        // this panel.
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outline),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusableRow(onClick = onPick, shape = RoundedCornerShape(20.dp)) {
                AsyncImage(
                    model = JourneyUrls.userPic(name),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = "Add $name" },
                )
            }
            FocusableIconButton(
                icon = { Icon(Icons.Filled.Close, contentDescription = forgetRivalLabel(name)) },
                onClick = onForget,
                modifier = Modifier.size(CHIP_TOUCH_TARGET),
            )
        }
    }
}

/** What the recents chip's dismiss says out loud, and the string a test finds it by. */
internal fun forgetRivalLabel(name: String): String = "Forget $name"

