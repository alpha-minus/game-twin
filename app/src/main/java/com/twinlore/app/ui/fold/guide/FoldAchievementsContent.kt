package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.DisposableEffect
import com.twinlore.app.domain.RaAchievementSearch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.twinlore.app.data.retroachievements.RaAchievementDisplay
import com.twinlore.app.data.steam.SteamAchievementDisplay
import com.twinlore.app.domain.SteamAchievementSearch
import com.twinlore.app.domain.SteamAchievementSortField
import com.twinlore.app.domain.SteamAchievementSorter
import com.twinlore.app.ui.components.accentBarBrush
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.twinlore.app.domain.RaAchievementSortField
import com.twinlore.app.domain.RaAchievementSorter
import com.twinlore.app.domain.RaAchievementTypeFilter
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.ui.components.HighestAwardIcon
import com.twinlore.app.ui.components.achievementBarBrush
import com.twinlore.app.ui.screens.LinkifiedCommentText
import com.twinlore.app.ui.screens.RowAchievementItem
import com.twinlore.app.ui.screens.formatCommentTime
import kotlinx.coroutines.launch
import java.text.NumberFormat

const val FOLD_AWARDS_NONE = "No achievements yet. Open the app to link this game."
const val FOLD_AWARDS_UNLINKED = "This vault is not linked to a game. Open the app to link it."
const val FOLD_AWARDS_NO_MATCH = "Nothing matches this filter"
const val FOLD_AWARDS_SEARCH = "Search achievements"
const val FOLD_AWARDS_CLEAR_SEARCH = "Clear search"
const val FOLD_AWARDS_REFRESH = "Refresh"
const val FOLD_AWARDS_REFRESHING = "Refreshing"

/** How far the scroll-to-top button sits above the surface's bottom edge. */
private val FOLD_AWARDS_TO_TOP_LIFT = 96.dp
const val FOLD_AWARDS_LOCKED_ONLY = "Locked only"
const val FOLD_AWARDS_SORT_ASC = "Sort ascending"
const val FOLD_AWARDS_SORT_DESC = "Sort descending"
const val FOLD_AWARDS_NO_COMMENTS = "No comments on this one yet"
const val FOLD_AWARDS_LOADING_COMMENTS = "Loading comments"
const val FOLD_AWARDS_CLOSE_COMMENTS = "Close"
const val FOLD_AWARDS_TO_TOP = "Scroll to top"

/**
 * The vault's achievements: how far you are, what the award is, and the list.
 *
 * Draws the app's own [RowAchievementItem] and the app's own progress bar
 * rather than plainer copies of both, so badge artwork, the greyscale on locked
 * achievements, the accent on unlocked ones, the soft and hardcore fills and
 * the award badge are one implementation instead of two that drift.
 *
 * Opening the surface never starts a sync. The panel exists while a game is
 * running, the process is already under memory pressure from it, and somebody
 * glancing at a list mid-game did not ask for a network round trip. What shows
 * is what the app last cached; [onRefresh] is the tap that goes and looks, and
 * it goes through the same cache the app's drawer refreshes, so a refresh in
 * either place is a refresh for both.
 *
 * Comments open in place of the list rather than in the sheet the app uses: a
 * ModalBottomSheet is a Dialog, and a Dialog cannot be shown from an overlay
 * window.
 *
 * A Steam-linked vault gets the same surface over its Steam set: Steam's own
 * sort fields, no type filter and no comments (Steam has neither), and a row
 * shaped like the app's Steam tile without its browser and checklist buttons,
 * which have nowhere to go over a running game.
 */
@Composable
fun FoldAchievementsContent(
    awards: FoldAwards,
    loading: Boolean,
    refreshing: Boolean,
    typeFilter: RaAchievementTypeFilter,
    lockedOnly: Boolean,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onWantsKeyboard: (Boolean) -> Unit,
    sortField: RaAchievementSortField,
    sortDirection: SortDirection,
    steamSortField: SteamAchievementSortField,
    steamSortDirection: SortDirection,
    commentsFor: RaAchievementDisplay?,
    comments: List<FoldComment>,
    commentsLoading: Boolean,
    onRefresh: () -> Unit,
    onCycleTypeFilter: () -> Unit,
    onToggleLockedOnly: () -> Unit,
    onCycleSort: () -> Unit,
    onToggleSortDirection: () -> Unit,
    onShowComments: (RaAchievementDisplay) -> Unit,
    onDismissComments: () -> Unit,
    onBlockedLink: (String) -> Unit,
    onToggleChrome: () -> Unit,
    onOpenApp: () -> Unit,
    scroll: Pair<Int, Int>?,
    onScrollChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    // The whole surface answers a tap, not only the rows.
    //
    // A row still carries its own [onTap] below: it consumes the gesture, so
    // this never sees it and the bar moves once. Everything a row is not -
    // the header, the pills' background, an empty list, the space under the
    // last row, the comment thread - lands here instead of nowhere.
    //
    // And it paints its own ground. The panel's surfaces dissolve into each
    // other, the incoming one fading in over the outgoing one, so a surface
    // with no ground is a window onto whatever it is replacing for the length
    // of the fade. Recorded on the Thor: the list fading in with the map still
    // fully visible through it, and the map then dropping out 300ms later.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .tapOnUnhandled(onToggleChrome),
    ) {
        // if/else all the way down, never an early return: returning out of an
        // inline composable lambda unbalances Compose's slot table and crashes
        // as ArrayIndexOutOfBoundsException in SlotTableKt.key.
        if (commentsFor != null) {
            AwardComments(
                achievement = commentsFor,
                comments = comments,
                loading = commentsLoading,
                onClose = onDismissComments,
                onBlockedLink = onBlockedLink,
                colors = colors,
            )
        } else {
            // Narrowed then sorted, in that order and with the app's own
            // helpers: sorting a shorter list is the same list either way, and
            // reusing the domain types is what keeps the two surfaces agreeing
            // about what "missable" and "by unlock time" mean.
            val shown = remember(
                awards.achievements, typeFilter, lockedOnly, searchQuery, sortField, sortDirection,
            ) {
                val narrowed = awards.achievements
                    .filter { typeFilter.matches(it.type) }
                    .filter { !lockedOnly || it.dateAwarded == null }
                // The app's own matcher, so "search" means the same thing in
                // both places: title or description, case blind.
                RaAchievementSorter.sort(
                    RaAchievementSearch.filter(narrowed, searchQuery), sortField, sortDirection,
                )
            }
            val isSteam = awards.source == FoldAwardSource.STEAM
            val steamShown = remember(
                awards.steamAchievements, lockedOnly, searchQuery, steamSortField, steamSortDirection,
            ) {
                foldSteamAwardsShown(
                    awards.steamAchievements, lockedOnly, searchQuery, steamSortField, steamSortDirection,
                )
            }
            val shownCount = if (isSteam) steamShown.size else shown.size
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()

            // Restored by scrolling once, not by seeding rememberLazyListState.
            // The stored position arrives from DataStore, which on the first
            // panel of a process can land after this composes; a seed read then
            // would be a zero, and the list would silently open at the top
            // having already "restored". Waiting for the list to have rows and
            // then jumping is late by a frame and right every time.
            var restored by remember { mutableStateOf(false) }
            LaunchedEffect(shownCount, scroll) {
                // Nothing to restore to yet. A null is the store still being
                // read, and marking the list restored against it would ignore
                // the real position when it arrives a frame later.
                if (restored || shownCount == 0 || scroll == null) return@LaunchedEffect
                restored = true
                val (index, offset) = scroll
                if (index > 0 || offset > 0) {
                    // Two header items sit above the rows, so the stored index
                    // is clamped against the whole list rather than the rows.
                    runCatching {
                        listState.scrollToItem(index.coerceAtMost(shownCount + 1), offset)
                    }
                }
            }
            // Reported when the scroll settles rather than per row: this ends in
            // a disk write, and a LazyColumn emits a new position for every row
            // it passes under the finger.
            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress }
                    .collect { scrolling ->
                        if (!scrolling && restored) {
                            onScrollChanged(
                                listState.firstVisibleItemIndex,
                                listState.firstVisibleItemScrollOffset,
                            )
                        }
                    }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    // The header scrolls. It is four lines of context you read
                    // once and then want out of the way, and on a panel this
                    // short a pinned one was spending a third of the screen
                    // restating what you already knew.
                    item(key = "header") {
                        if (isSteam) {
                            SteamAwardsHeader(awards, refreshing, onRefresh, colors)
                        } else {
                            AwardsHeader(awards, refreshing, onRefresh, colors)
                        }
                    }
                    item(key = "search") {
                        AwardSearch(searchQuery, onSearchChanged, onWantsKeyboard, colors)
                    }
                    item(key = "filters") {
                        AwardFilters(
                            showTypeFilter = !isSteam,
                            sortLabel = if (isSteam) steamSortField.label else sortField.label,
                            typeFilter = typeFilter,
                            lockedOnly = lockedOnly,
                            sortDirection = if (isSteam) steamSortDirection else sortDirection,
                            onCycleTypeFilter = onCycleTypeFilter,
                            onToggleLockedOnly = onToggleLockedOnly,
                            onCycleSort = onCycleSort,
                            onToggleSortDirection = onToggleSortDirection,
                            colors = colors,
                        )
                    }
                    if (loading && awards.isEmpty) {
                        item(key = "loading") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    } else if (awards.isEmpty) {
                        // An unlinked vault and a linked one with nothing cached
                        // are different problems with different answers, and the
                        // message that names the right one is worth showing.
                        item(key = "empty") {
                            EmptyWithApp(
                                if (awards.linked) FOLD_AWARDS_NONE else FOLD_AWARDS_UNLINKED,
                                onOpenApp,
                            )
                        }
                    } else if (shownCount == 0) {
                        // A filter that hides everything is not the same answer
                        // as a vault with no achievements, and saying so is the
                        // difference between a narrowed list and a panel that
                        // looks broken.
                        item(key = "no-match") {
                            EmptyMessage(
                                if (searchQuery.isBlank()) FOLD_AWARDS_NO_MATCH
                                else "No achievements match \"${searchQuery.trim()}\"",
                            )
                        }
                    } else if (isSteam) {
                        items(steamShown, key = { it.apiName }) { award ->
                            SteamAwardRow(award, colors, onTap = onToggleChrome)
                        }
                    } else {
                        items(shown, key = { it.id }) { award ->
                            RowAchievementItem(
                                achievement = award,
                                gameTitle = awards.gameTitle,
                                totalDistinctPlayers = awards.totalPlayers,
                                accentColor = colors.primary,
                                sheetBg = colors.background,
                                textColor = colors.onSurface,
                                variantColor = colors.onSurfaceVariant,
                                borderColor = colors.onBackground.copy(alpha = 0.3f),
                                onShowComments = { onShowComments(award) },
                                // The app copies the achievement to the
                                // clipboard on a long press. Inert here: what it
                                // wants to report afterwards is a dialog.
                                onLongPress = {},
                                // Same as a tap on a guide: the panel's chrome
                                // comes and goes with a tap on what you read.
                                onTap = onToggleChrome,
                            )
                        }
                    }
                }

                // The app's own scroll-to-top, same trigger and same glyph. It
                // matters more here than there: the header is no longer pinned,
                // so scrolling down is the only way to lose it and this is the
                // only way back.
                val showToTop by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showToTop,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    // Lifted well clear of the corner: the handle is usually
                    // parked down there, and the bottom bar floats over the edge.
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = FOLD_AWARDS_TO_TOP_LIFT),
                ) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = colors.primary,
                        contentColor = colors.background,
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = FOLD_AWARDS_TO_TOP)
                    }
                }
            }
        }
    }
}

/**
 * The bar, the award and the numbers, built the way the drawer builds them.
 *
 * Two fills over one track: softcore underneath in silver, hardcore over it in
 * gold, so a set earned the hard way reads as one bar rather than two. The
 * award badge sits after the bar rather than up with the text, which is where
 * the drawer moved it to, and no badge means no gap.
 */
@Composable
private fun AwardsHeader(
    awards: FoldAwards,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    colors: ColorScheme,
) {
    val nf = remember { NumberFormat.getIntegerInstance() }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The set's own icon, which is the fastest way to know the panel is
            // showing the game you are actually playing. An identifier rather
            // than artwork, but big enough to recognise at a glance over a game.
            awards.gameIconUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .padding(end = 0.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            // The drawer's own stats string, character for character, playtime
            // included, so the two headers read as one thing seen twice rather
            // than two summaries that happen to agree.
            Text(
                text = if (awards.total > 0) {
                    buildString {
                        append("${awards.unlocked}/${awards.total} \u00b7 ")
                        append("${nf.format(awards.points)}/${nf.format(awards.totalPoints)} pts")
                        awards.playtimeMinutes?.takeIf { it > 0 }?.let { minutes ->
                            append(" \u00b7 ${minutes / 60}h ${minutes % 60}m")
                        }
                    }
                } else {
                    ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (refreshing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = FOLD_AWARDS_REFRESHING,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = FOLD_AWARDS_REFRESH,
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }

        if (awards.total > 0) {
            val softPct = awards.unlocked.toFloat() / awards.total
            val hardPct = awards.unlockedHardcore.toFloat() / awards.total
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .border(1.dp, colors.onBackground.copy(alpha = 0.3f), CircleShape)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(colors.background, CircleShape)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(softPct.coerceIn(0f, 1f))
                                .background(achievementBarBrush(mastered = false), CircleShape)
                        )
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(hardPct.coerceIn(0f, 1f))
                                .background(achievementBarBrush(mastered = true), CircleShape)
                        )
                    }
                }
                awards.highestAwardKind?.let {
                    Spacer(Modifier.width(8.dp))
                    HighestAwardIcon(it)
                }
            }

            // When the same set was earned in hardcore the two percentages are
            // identical, so the drawer collapses to just the hard one. Same
            // rule here, or the panel reports twice what the app reports once.
            val soft = 100 * awards.unlocked / awards.total
            val hard = 100 * awards.unlockedHardcore / awards.total
            // Ranking is only meaningful once there is a score to be ranked on,
            // and the drawer suppresses it otherwise for the same reason: rank
            // against zero achievements is a number about nobody.
            val rank = awards.rank
                ?.takeIf { awards.points > 0 || awards.unlocked > 0 }
                ?.takeIf { awards.totalPlayers > 0 }
                ?.let { r ->
                    val topPercent = 100.0 * r / awards.totalPlayers.coerceAtLeast(1)
                    val top = when {
                        topPercent >= 99.995 -> "1%"
                        topPercent >= 1 -> "${topPercent.toInt()}%"
                        topPercent >= 0.1 -> "%.1f%%".format(topPercent)
                        else -> "<0.1%"
                    }
                    "Ranking: ${nf.format(r)}/${nf.format(awards.totalPlayers)} Top $top"
                }
            val line = buildList {
                if (awards.unlocked == awards.unlockedHardcore) {
                    add("$hard% (Hard)")
                } else {
                    add("$soft% (Soft)")
                    add("$hard% (Hard)")
                }
                rank?.let { add(it) }
            }.joinToString("  \u00b7  ")
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurface,
                // Clear of the bar: the Pixel font sets its glyphs high in the
                // line, and at 2dp its caps sat on the bar's border.
                modifier = Modifier.padding(top = 5.dp, bottom = 2.dp),
            )
        }
    }
}

/**
 * The Steam drawer's header, in the panel's layout: the store banner, count
 * and playtime beside Refresh, one accent bar (Steam has no hardcore), and the
 * drawer's own "unlocked/total · N% complete" line.
 */
@Composable
private fun SteamAwardsHeader(
    awards: FoldAwards,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    colors: ColorScheme,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The same height as the RA icon, at the banner's own 460x215 shape,
            // so the header neither jumps when it loads nor crops the logo.
            awards.gameIconUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .height(48.dp)
                        .aspectRatio(STEAM_HEADER_ASPECT)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (awards.total > 0) {
                    buildString {
                        append("${awards.unlocked}/${awards.total}")
                        awards.playtimeMinutes?.takeIf { it > 0 }?.let { minutes ->
                            append(" \u00b7 ${minutes / 60}h ${minutes % 60}m")
                        }
                    }
                } else {
                    ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (refreshing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = FOLD_AWARDS_REFRESHING,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = FOLD_AWARDS_REFRESH,
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }

        if (awards.total > 0) {
            val pct = awards.unlocked.toFloat() / awards.total
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .border(1.dp, colors.onBackground.copy(alpha = 0.3f), CircleShape)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(colors.background, CircleShape)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(pct.coerceIn(0f, 1f))
                            .background(accentBarBrush(colors.primary), CircleShape)
                    )
                }
            }
            Text(
                text = "${awards.unlocked}/${awards.total} \u00b7 ${100 * awards.unlocked / awards.total}% complete",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurface,
                // Clear of the bar: the Pixel font sets its glyphs high in the
                // line, and at 2dp its caps sat on the bar's border.
                modifier = Modifier.padding(top = 5.dp, bottom = 2.dp),
            )
        }
    }
}

/**
 * One Steam achievement, drawn the way the app's Steam tile draws it: the
 * colour icon when unlocked and the grey one (greyscaled too) when not, the
 * title in the accent once earned, then description, unlock date and global
 * rate. The tile's search and checklist actions are left out: one opens a
 * browser and the other reports in a dialog, and neither can happen over a
 * running game. A tap moves the panel's chrome, like a tap on any other row.
 */
@Composable
private fun SteamAwardRow(
    achievement: SteamAchievementDisplay,
    colors: ColorScheme,
    onTap: () -> Unit,
) {
    val unlocked = achievement.achieved
    val iconUrl = if (unlocked) achievement.iconUrl else achievement.iconGrayUrl
    val grayscale = remember(unlocked) {
        if (unlocked) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = iconUrl?.takeIf { it.isNotBlank() },
            contentDescription = null,
            colorFilter = grayscale,
            modifier = Modifier.size(64.dp).padding(end = 8.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                achievement.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (unlocked) colors.primary else colors.onSurface,
            )
            achievement.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            achievement.unlockTime?.takeIf { unlocked && it > 0 }?.let { seconds ->
                Text(
                    "Unlocked ${steamUnlockDate(seconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            achievement.globalPercent?.let { percent ->
                Text(
                    "${"%.1f".format(percent)}% of players",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        if (unlocked) {
            Icon(
                Icons.Default.TaskAlt,
                contentDescription = "Unlocked",
                tint = colors.primary,
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
            )
        }
    }
}

/** Steam's store header.jpg is 460x215. */
private const val STEAM_HEADER_ASPECT = 460f / 215f

/** The Steam tile's date format, "MMM d, yyyy", from unix seconds. */
private fun steamUnlockDate(seconds: Long): String =
    java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(seconds * 1000))

/**
 * What a Steam list shows: narrowed to locked when asked, then to the search
 * with the app's own Steam matcher, then sorted with the app's own sorter, so
 * the panel and the drawer agree about all three.
 */
internal fun foldSteamAwardsShown(
    achievements: List<SteamAchievementDisplay>,
    lockedOnly: Boolean,
    query: String,
    field: SteamAchievementSortField,
    direction: SortDirection,
): List<SteamAchievementDisplay> {
    val narrowed = if (lockedOnly) achievements.filter { !it.achieved } else achievements
    return SteamAchievementSorter.sort(SteamAchievementSearch.filter(narrowed, query), field, direction)
}

/**
 * The filter row: type, locked-only, sort field, sort direction, search.
 *
 * Every control is a tap that moves to the next value and says what it now is,
 * rather than the drawer's menus. Whether a DropdownMenu survives an overlay
 * window has been answered both ways on this device, and a control that cycles
 * needs no answer: it is the same four values either way, with nothing to
 * float over anything.
 */
@Composable
private fun AwardFilters(
    showTypeFilter: Boolean,
    sortLabel: String,
    typeFilter: RaAchievementTypeFilter,
    lockedOnly: Boolean,
    sortDirection: SortDirection,
    onCycleTypeFilter: () -> Unit,
    onToggleLockedOnly: () -> Unit,
    onCycleSort: () -> Unit,
    onToggleSortDirection: () -> Unit,
    colors: ColorScheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sort leads. It is the control that decides what the top of the list
        // is, and the top of the list is what a glance mid-game actually reads;
        // the two narrowings after it only decide what is left further down.
        FilterPill(sortLabel, selected = false, colors = colors, onClick = onCycleSort)
        // An arrow, not the field's own word for its direction. "Most won" and
        // "Newest" and "A to Z" all mean descending, and a control that renames
        // itself per field makes the reader work out afresh, every time, which
        // way the list is about to go. The arrow means one thing.
        SortDirectionPill(sortDirection, colors, onToggleSortDirection)
        // Steam achievements carry no type, so there is nothing to narrow by.
        if (showTypeFilter) {
            FilterPill(
                typeFilter.label,
                selected = typeFilter != RaAchievementTypeFilter.ALL,
                colors = colors,
                onClick = onCycleTypeFilter,
            )
        }
        FilterPill(FOLD_AWARDS_LOCKED_ONLY, lockedOnly, colors, onToggleLockedOnly)
    }
}

/**
 * Search, as the app's drawer has it, in the shape this window can host.
 *
 * The drawer collapses its field behind a button; here it is a row of its own
 * under the header, because the panel is narrow enough that a button and a
 * field would fight for the same space and a reader mid-game should not have
 * to open something to find something. A plain field, not Material's: this is
 * drawn over a game and the palette's surfaceVariant is a 12%-alpha
 * foreground, so it gets the two-layer base everything else here has.
 *
 * Focus asks the window for the keyboard. An overlay is FLAG_NOT_FOCUSABLE
 * so the game underneath keeps its input; the panel flips that only while a
 * field of its own is focused, and gives it back on blur and on dispose so a
 * fold mid-typing does not leave the game deaf.
 */
@Composable
private fun AwardSearch(
    query: String,
    onQueryChanged: (String) -> Unit,
    onWantsKeyboard: (Boolean) -> Unit,
    colors: ColorScheme,
) {
    val focusManager = LocalFocusManager.current
    DisposableEffect(Unit) { onDispose { onWantsKeyboard(false) } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.background)
            .background(colors.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = FOLD_AWARDS_SEARCH,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onWantsKeyboard(it.isFocused) },
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = FOLD_AWARDS_CLEAR_SEARCH,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .clickable {
                        onQueryChanged("")
                        focusManager.clearFocus()
                    },
            )
        }
    }
}

/** Up or down, and nothing else. Same shape as the pills it sits beside. */
@Composable
private fun SortDirectionPill(
    direction: SortDirection,
    colors: ColorScheme,
    onClick: () -> Unit,
) {
    val ascending = direction == SortDirection.ASC
    Icon(
        imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
        contentDescription = if (ascending) FOLD_AWARDS_SORT_ASC else FOLD_AWARDS_SORT_DESC,
        tint = colors.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .size(16.dp),
    )
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    colors: ColorScheme,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * One achievement's comment thread, in place of the list.
 *
 * What people write under an achievement is usually how to get it, which is
 * exactly what somebody stuck mid-game is looking for.
 *
 * The links in it are drawn by the app's own [LinkifiedCommentText], underlined
 * and in the accent, and they are the reason this needs saying: in the app a
 * tap on one hands the URL to a browser. Doing that from here would throw a
 * browser over a running game, so the tap is routed to [onBlockedLink] and
 * answered by the panel's banner instead. Drawn as links rather than as plain
 * text on purpose - a URL that looks like a URL and explains why it will not
 * open beats one silently flattened into prose.
 */
@Composable
private fun AwardComments(
    achievement: RaAchievementDisplay,
    comments: List<FoldComment>,
    loading: Boolean,
    onClose: () -> Unit,
    onBlockedLink: (String) -> Unit,
    colors: ColorScheme,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(FOLD_AWARDS_CLOSE_COMMENTS) }
        }

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text = FOLD_AWARDS_LOADING_COMMENTS,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else if (comments.isEmpty()) {
            EmptyMessage(FOLD_AWARDS_NO_COMMENTS)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            ) {
                // No key. Both fields come back from RA through orEmpty(), so
                // two comments missing a user or sharing a second collide on
                // one key and LazyColumn throws; prefixing the index makes the
                // key unique but also makes it position, which is what an
                // absent key already is. The list is set once per load and
                // never reordered.
                items(comments) { comment ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = comment.author.ifBlank { "Unknown" },
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.primary,
                            )
                            Text(
                                text = formatCommentTime(comment.submitted),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        LinkifiedCommentText(
                            text = comment.text,
                            textColor = colors.onSurface,
                            linkColor = colors.primary,
                            style = MaterialTheme.typography.bodySmall,
                            onLinkClick = onBlockedLink,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
