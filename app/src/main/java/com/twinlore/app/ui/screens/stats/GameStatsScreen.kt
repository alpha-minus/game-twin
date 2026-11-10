package com.twinlore.app.ui.screens.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.LocalImageLoader
import com.twinlore.app.ui.components.AchievementGold
import com.twinlore.app.ui.components.AchievementSilver
import com.twinlore.app.ui.theme.ChecklistColorPalette
import com.twinlore.app.ui.utils.gamepadFocusable

private val RankBronze = Color(0xFFCD7F32)

/**
 * How long the page's decorative ambience — the podium medals breathing and the era card's two
 * firefly swarms — plays before settling.
 *
 * Any continuously running animation keeps this whole page repainting at 60 fps, and it is a big
 * page: measured on an AYN Thor, the stats screen sat at ~65% of a core for as long as it was
 * open, and at 0% with the ambience stopped (frame times were fine either way — battery, not jank).
 * Playing it in and then settling keeps the entrance the design is after without charging for it
 * the whole time the page is left open.
 */
private const val AMBIENCE_MS = 6_000L

/** How long the ambience takes to fade to rest, long enough that the settle reads as deliberate. */
private const val AMBIENCE_SETTLE_MS = 1_400

/** One full breath of the podium medal, as a linear loop folded into a triangle. */
private const val PODIUM_BREATH_MS = 3000L

/** Medal-ring alpha once the breathing has settled. */
private const val MEDAL_REST = 0.85f

/**
 * Play statistics for the ES-DE library, all-time (ES-DE's files carry no
 * time-series): a marquee podium of the most played games, totals, the player's
 * era (golden year + average game age from release dates), play time by system,
 * favorite genres and the rest of the top 10. Selecting a game opens its
 * "activity log" with the evolution of its play time when session history exists.
 */
@Composable
fun GameStatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val artwork by viewModel.artwork.collectAsState()
    val gameMedia by viewModel.gameMedia.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val firstFocus = remember { FocusRequester() }

    // Stats is reached from the library, which runs without the Android status bar — keep
    // it hidden here too so the chrome doesn't pop back in.
    com.twinlore.app.ui.utils.HideStatusBarWhen(true)

    BackHandler(enabled = detail == null) { onBack() }
    BackHandler(enabled = detail != null) { viewModel.closeDetail() }
    LaunchedEffect(state.topGames) {
        viewModel.ensureArtwork(state.topGames)
        viewModel.ensureGameMedia(state.topGames)
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        runCatching { firstFocus.requestFocus() }
    }

    // See [AMBIENCE_MS]: the ambience plays the page in, then stops so an open stats page is free.
    var ambient by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(AMBIENCE_MS)
        ambient = false
    }
    // Fades the ambience out rather than cutting it: 1 while playing, 0 once settled. Reading it
    // here recomposes only across the settle itself, and once it reaches 0 the animations are
    // dropped from the composition entirely, which is what actually stops the 60 fps frame loop.
    val ambience by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (ambient) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(AMBIENCE_SETTLE_MS),
        label = "stats-ambience",
    )
    val ambienceLive = ambience > 0.001f

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Hero backdrop: the #1 game's art, blurred into the theme background so the
        // page opens with the library's own imagery instead of a flat panel.
        val heroArt = state.topGames.firstOrNull()?.let { artwork[viewModel.artKey(it)] }
        if (heroArt != null) {
            AsyncImage(
                model = heroArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .blur(26.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                            1f to MaterialTheme.colorScheme.background,
                        )
                    ),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusableIconButton(
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") },
                    onClick = onBack,
                    focusRequester = firstFocus,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Play stats",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!state.loading && !state.hasGamelists) {
                HintCard(
                    "ES-DE gamelists aren't readable — set the ES-DE folder and allow " +
                        "All-Files access to see your play statistics."
                )
            }

            if (state.topGames.isNotEmpty()) {
                Podium(
                    games = state.topGames.take(3),
                    artFor = { artwork[viewModel.artKey(it)] },
                    mediaFor = { gameMedia[viewModel.artKey(it)] },
                    onOpen = { viewModel.openDetail(it) },
                    ambienceLive = ambienceLive,
                    ambienceProvider = { ambience },
                )
            }

            StatTiles(state)

            // The player's era: golden year + average game age, from release dates.
            if (state.era.goldenYear != null) {
                EraCard(state.era, ambienceLive, { ambience })
            }

            // SVG-capable loader shared by the console backdrop and the system logos.
            val baseLoader = LocalImageLoader.current
            val svgLoader = remember(baseLoader) {
                baseLoader.newBuilder()
                    .components { add(coil.decode.SvgDecoder.Factory()) }
                    .respectCacheHeaders(false)
                    .build()
            }
            // Backdrop for the system chart: the physical media of the most played game on the
            // leading system, from the user's own scraped art. It replaces a console render that
            // came from a theme repository the app no longer fetches from, and a cartridge or
            // disc suits a chart better than a wordmark would.
            val topSystemMedia by viewModel.physicalMedia.collectAsState()
            val systemLogos by viewModel.systemLogos.collectAsState()
            val systemWatermark = state.topSystemGame?.let { topSystemMedia[viewModel.artKey(it)] }
            LaunchedEffect(state.topSystemGame) {
                state.topSystemGame?.let { viewModel.ensurePhysicalMedia(it) }
            }
            StatsCard(
                title = "Play time by system",
                watermark = systemWatermark,
                watermarkLoader = svgLoader,
                focusable = true,
            ) {
                if (state.buckets.isEmpty()) {
                    EmptyChartNote()
                } else {
                    SystemBars(
                        buckets = state.buckets,
                        systemLogos = systemLogos,
                        onNeedLogo = viewModel::ensureSystemLogo,
                    )
                }
            }

            if (state.genres.isNotEmpty()) {
                StatsCard(
                    title = "Favorite genres",
                    backdrop = state.genreBackdropGame?.let { artwork[viewModel.artKey(it)] },
                    focusable = true,
                ) {
                    GenreBars(state.genres)
                }
            }

            if (state.topGames.size > 3) {
                StatsCard(title = "The rest of the top 10") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.topGames.drop(3).forEachIndexed { i, game ->
                            val media = gameMedia[viewModel.artKey(game)]
                            TopGameRow(
                                rank = i + 4,
                                game = game,
                                art = artwork[viewModel.artKey(game)],
                                marquee = media?.logo,
                                hero = media?.hero ?: media?.mix ?: media?.screenshot,
                                onOpen = { viewModel.openDetail(game) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Per-game activity log, sliding over the ranking like the live view's theme panel.
        // The last non-null detail keeps rendering while the exit animation runs.
        var lastDetail by remember { mutableStateOf<GameDetail?>(null) }
        if (detail != null) lastDetail = detail
        AnimatedVisibility(
            visible = detail != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            lastDetail?.let { GameDetailSheet(it, onClose = { viewModel.closeDetail() }) }
        }
    }
}

// ---- building blocks -------------------------------------------------------

/** Accent used for focus rings and single-series chart marks. */
@Composable
private fun accentColor(): Color =
    LocalCachedFocusColor.current?.value ?: MaterialTheme.colorScheme.primary

/** Shared activate-on-A/Enter key handling for focusable controls. */
private fun Modifier.activateOnA(onClick: () -> Unit): Modifier = onKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown &&
        (e.key == Key.ButtonA || e.key == Key.DirectionCenter || e.key == Key.Enter)
    ) {
        onClick(); true
    } else false
}

@Composable
private fun FocusableIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = accentColor()
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .gamepadFocusable()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .activateOnA(onClick)
            .then(
                if (focused) {
                    Modifier
                        .background(accent.copy(alpha = 0.22f), CircleShape)
                        .border(2.dp, accent, CircleShape)
                } else Modifier
            ),
    ) { icon() }
}

/**
 * The player's era, told big: just the golden year — the weighted median of the
 * release years their play time lives in — over two swarms of fireflies (gold +
 * theme accent). Bold, not Black: the bundled font families ship regular/bold
 * only, so heavier weights would silently fall back to a system font and ignore
 * the user's font choice.
 */
@Composable
private fun EraCard(
    era: EraStats,
    /** False once the swarms have faded out — they then leave the composition and stop costing. */
    ambienceLive: Boolean,
    /** Read in the draw phase only, so fading the swarms never recomposes the card. */
    ambienceProvider: () -> Float,
) {
    val accent = accentColor()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focused) Modifier.border(2.dp, accent, shape) else Modifier)
            .gamepadFocusable()
            .focusable(interactionSource = interaction),
    ) {
        Box {
            if (ambienceLive) {
                val fade = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = ambienceProvider() }
                com.twinlore.app.ui.ambience.AmbienceOverlay(
                    animation = com.twinlore.app.data.ambience.AmbienceAnimation.FIREFLIES,
                    accent = AchievementGold,
                    modifier = fade,
                    randomSeed = "era-fireflies-gold",
                )
                com.twinlore.app.ui.ambience.AmbienceOverlay(
                    animation = com.twinlore.app.data.ambience.AmbienceAnimation.FIREFLIES,
                    accent = accent,
                    modifier = fade,
                    randomSeed = "era-fireflies-accent",
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 30.dp),
            ) {
                Text(
                    "${era.goldenYear}",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = AchievementGold,
                    maxLines = 1,
                )
                Text(
                    "Golden year",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                // Say how the number is reached rather than leaving it to be guessed at. Half
                // the play time sits at or before this year, and the basis line names which
                // games voted, since that changes when too few clear the hour.
                Text(
                    "Median release year, weighted by play time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    when (era.basis) {
                        EraBasis.SUBSTANTIAL ->
                            "From ${era.gamesCounted} games you played over an hour"
                        EraBasis.ALL_PLAYED ->
                            "From ${era.gamesCounted} games you have played"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    /** Dimmed art of the card's subject (e.g. the top genre's most played game). */
    backdrop: Any? = null,
    /** Sharp accent layer (e.g. a console render), right-aligned. */
    watermark: Any? = null,
    watermarkLoader: coil.ImageLoader? = null,
    /**
     * Give the card its own dpad focus stop. Only for chart-only cards with no focusable
     * children — a card whose content has its own focus targets (e.g. the top-10 rows)
     * must stay false so dpad lands on the rows, not the wrapper.
     */
    focusable: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusable) {
                    Modifier
                        .then(if (focused) Modifier.border(2.dp, accentColor(), shape) else Modifier)
                        .gamepadFocusable()
                        .focusable(interactionSource = interaction)
                } else Modifier
            ),
    ) {
        Box {
            if (backdrop != null) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.30f,
                    modifier = Modifier.matchParentSize(),
                )
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.30f)))
            }
            if (watermark != null) {
                AsyncImage(
                    model = watermark,
                    imageLoader = watermarkLoader ?: LocalImageLoader.current,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterEnd,
                    alpha = 0.30f,
                    modifier = Modifier.matchParentSize().padding(8.dp),
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                content()
            }
        }
    }
}

@Composable
private fun HintCard(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun EmptyChartNote() {
    Text(
        "Nothing recorded in this period yet",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---- podium ----------------------------------------------------------------

private fun rankColor(rank: Int): Color = when (rank) {
    1 -> AchievementGold
    2 -> AchievementSilver
    else -> RankBronze
}

/**
 * The top three as marquee-over-hero cards — 2nd, 1st, 3rd with the winner raised.
 * Rank is carried by position and a breathing glow in gold / silver / bronze.
 * Each card opens the game's activity log.
 */
@Composable
private fun Podium(
    games: List<GameStat>,
    artFor: (GameStat) -> android.net.Uri?,
    mediaFor: (GameStat) -> com.twinlore.app.data.esde.GameMedia?,
    onOpen: (GameStat) -> Unit,
    ambienceLive: Boolean,
    ambienceProvider: () -> Float,
) {
    // Visual order 2-1-3; missing ranks just shrink the row.
    val slots = listOfNotNull(
        games.getOrNull(1)?.let { it to 2 },
        games.getOrNull(0)?.let { it to 1 },
        games.getOrNull(2)?.let { it to 3 },
    )
    // Always lay out three columns: with fewer games, empty spacers keep the card
    // at podium size instead of ballooning to full width.
    val leading = (3 - slots.size) / 2
    val trailing = 3 - slots.size - leading
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(leading) { Spacer(Modifier.weight(1f)) }
        slots.forEach { (game, rank) ->
            PodiumCard(
                game = game,
                rank = rank,
                art = artFor(game),
                media = mediaFor(game),
                onOpen = { onOpen(game) },
                ambienceLive = ambienceLive,
                ambienceProvider = ambienceProvider,
                modifier = Modifier.weight(if (rank == 1) 1.15f else 1f),
            )
        }
        repeat(trailing) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun PodiumCard(
    game: GameStat,
    rank: Int,
    art: android.net.Uri?,
    media: com.twinlore.app.data.esde.GameMedia?,
    onOpen: () -> Unit,
    /** While true the medal breathes; false drops the transition and the card stops repainting. */
    ambienceLive: Boolean,
    /** 1 while breathing, easing to 0 as it settles. Read in the draw phase only. */
    ambienceProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = accentColor()
    val medal = rankColor(rank)
    val shape = RoundedCornerShape(16.dp)

    // The medal is a breathing glow in gold / silver / bronze, not a badge: an animated halo
    // behind the card plus a border that pulses in step. It breathes while the page settles in
    // and then rests at [MEDAL_REST] — the rank still reads from the colour, and a stats page
    // left open stops driving frames (see [AMBIENCE_MS]).
    // On the SHARED ambience clock rather than its own InfiniteTransition. Three podium cards
    // each running a transition kept this page at 60 fps even though the era card's swarms had
    // already been moved to the slower clock — the medals were quietly setting the frame rate for
    // the whole screen. The old Reverse repeat is a triangle wave, so it is one linear loop of
    // double the length folded back on itself.
    val clock = if (ambienceLive) {
        com.twinlore.app.ui.ambience.rememberAmbienceClockMs()
    } else null
    // Every read happens inside a draw lambda, so the breath never recomposes the card.
    val glowNow: () -> Float = {
        if (clock == null) MEDAL_REST else {
            val tp = (clock.longValue % PODIUM_BREATH_MS) / PODIUM_BREATH_MS.toFloat()
            val b = 0.35f + 0.65f * (1f - kotlin.math.abs(2f * tp - 1f))
            MEDAL_REST + (b - MEDAL_REST) * ambienceProvider()
        }
    }

    val hero = media?.hero ?: media?.mix ?: media?.screenshot ?: media?.titleScreen ?: art
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            // Halo drawn before clipping so it can breathe past the card bounds.
            .drawBehind {
                val corner = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                val glow = glowNow()
                for (i in 1..3) {
                    val grow = i * 3.dp.toPx()
                    drawRoundRect(
                        color = medal.copy(alpha = glow * 0.30f / i),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = corner,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = grow),
                    )
                }
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (focused) Modifier.border(3.dp, accent, shape) else Modifier)
            // The resting medal ring pulses with the halo. It is STROKED HERE rather than passed
            // to Modifier.border: a border colour derived from [glow] is read while the modifier
            // chain is built, i.e. during composition, so every card recomposed on every
            // animation frame — which kept the whole screen's semantics tree and display list
            // being rebuilt at 60 fps. Read in the draw phase it only costs a redraw.
            .drawWithContent {
                drawContent()
                if (!focused) {
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        color = medal.copy(alpha = 0.30f + glowNow() * 0.55f),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                    )
                }
            }
            .gamepadFocusable()
            .activateOnA(onOpen)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (rank == 1) 0.9f else 1.05f)) {
            // Dimmed hero underneath; the marquee is the star on top.
            if (hero != null) {
                AsyncImage(
                    model = hero,
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.65f),
                        )
                    ),
            )
            // Marquee only. The typographic name used to sit here as a fallback, over the art
            // and fighting the hero for the same middle of the card; it now has its own row
            // under the image, so a game without a logo simply shows its artwork.
            if (media?.logo != null) {
                AsyncImage(
                    model = media.logo,
                    contentDescription = game.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.82f)
                        .fillMaxHeight(0.5f),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
            ) {
                Text(
                    formatPlayTime(game.playTimeSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = medal,
                    fontWeight = FontWeight.Bold,
                )
                val meta = listOfNotNull(
                    game.releaseYear?.toString(),
                    game.genre?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // The name sits under the artwork, the way the library grid labels a cover, rather than
        // over it. On the card it competed with the hero and the marquee for the same space and
        // needed the dim behind it to stay legible at all.
        Text(
            game.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
        )
    }
}

// ---- tiles & charts --------------------------------------------------------

/**
 * The headline tiles. Streaks are intentionally absent: ES-DE's gamelists carry only
 * one `lastplayed` date per game — not the day-by-day history a real streak needs.
 */
@Composable
private fun StatTiles(state: GameStatsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Play time", formatPlayTime(state.totals.totalPlaySeconds), Modifier.weight(1f))
        StatTile("Times played", "${state.totals.timesPlayed}", Modifier.weight(1f))
        StatTile("Games", "${state.totals.gamesPlayed}", Modifier.weight(1f))
        StatTile("Avg session", formatPlayTime(state.totals.avgSessionSeconds), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Horizontal bars for the all-time per-system breakdown, led by each system's logo. */
@Composable
private fun SystemBars(
    buckets: List<ChartBucket>,
    systemLogos: Map<String, android.net.Uri?>,
    onNeedLogo: (String) -> Unit,
) {
    LaunchedEffect(buckets) { buckets.forEach { onNeedLogo(it.label) } }
    val accent = accentColor()
    val peak = buckets.maxOf { it.seconds }.coerceAtLeast(1)
    // SVG-capable loader derived from the shared one (same caches) — system logos are SVG.
    val baseLoader = LocalImageLoader.current
    val svgLoader = remember(baseLoader) {
        baseLoader.newBuilder()
            .components { add(coil.decode.SvgDecoder.Factory()) }
            .respectCacheHeaders(false)
            .build()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        buckets.forEach { bucket ->
            var logoFailed by remember(bucket.label) {
                androidx.compose.runtime.mutableStateOf(false)
            }
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                ) {
                    // A logo the user put in their own folder, or plain text. The GitHub-hosted
                    // monochrome set that used to fill the gap is gone (user request): a system's
                    // name in text says the same thing without a network dependency.
                    val logoModel: Any? = systemLogos[bucket.label]
                    if (!logoFailed && logoModel != null) {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val onLogoError by rememberUpdatedState { logoFailed = true }
                        val request = remember(logoModel) {
                            coil.request.ImageRequest.Builder(ctx)
                                .data(logoModel)
                                .listener(onError = { _, _ -> onLogoError() })
                                .build()
                        }
                        AsyncImage(
                            model = request,
                            imageLoader = svgLoader,
                            contentDescription = bucket.label,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier.height(26.dp).width(120.dp),
                        )
                    } else {
                        Text(
                            bucket.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatPlayTime(bucket.seconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(bucket.seconds.toFloat() / peak)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent),
                    )
                }
            }
        }
    }
}

/** Top genres as labeled shares, colored from the app's fixed categorical palette. */
@Composable
private fun GenreBars(genres: List<GenreShare>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.forEachIndexed { i, share ->
            // "Other" gets the palette's slate grey; real genres keep the fixed order.
            val color = if (share.genre == "Other") {
                ChecklistColorPalette.color(10)
            } else {
                ChecklistColorPalette.color(i % ChecklistColorPalette.SIZE)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(
                    share.genre,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatPlayTime(share.weightSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(share.fraction.coerceIn(0.02f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
        }
    }
}

/** "SNES · 1994 · Action / Platformer" — system led, year and genre when scraped. */
/**
 * The line under a ranked game: system, year, genre.
 *
 * [withName] leads with the game's own name, for the rows whose name is carried only by a marquee
 * image. Those had no readable title at all, and an image of a logo is not a name you can scan a
 * list by.
 */
private fun gameMetaLine(game: GameStat, withName: Boolean = false): String = buildString {
    if (withName) game.name.takeIf { it.isNotBlank() }?.let { append("$it · ") }
    append(game.system.uppercase())
    game.releaseYear?.let { append(" · $it") }
    game.genre?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
}

/**
 * One ranked game: its dimmed hero art fills the row, the marquee leads it (art
 * thumb + name only when no marquee exists), playtime on the right.
 */
@Composable
private fun TopGameRow(
    rank: Int,
    game: GameStat,
    art: android.net.Uri?,
    marquee: android.net.Uri?,
    hero: android.net.Uri?,
    onOpen: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = accentColor()
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .then(if (focused) Modifier.border(2.dp, accent, shape) else Modifier)
            .gamepadFocusable()
            .activateOnA(onOpen)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
    ) {
        // The game's own hero art as the row's dimmed backdrop. The crop window sits
        // a fifth above the image's center — hero art carries its subject there.
        if (hero != null) {
            AsyncImage(
                model = hero,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(0f, -0.2f),
                alpha = 0.30f,
                modifier = Modifier.matchParentSize(),
            )
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(26.dp),
            )
            if (marquee != null) {
                // The marquee IS the game's name — no text needed beside it.
                Column(Modifier.weight(1f)) {
                    AsyncImage(
                        model = marquee,
                        contentDescription = game.name,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.height(42.dp).fillMaxWidth(0.7f),
                    )
                    Text(
                        gameMetaLine(game, withName = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        game.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        gameMetaLine(game),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatPlayTime(game.playTimeSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (game.timesPlayed > 0) {
                    Text(
                        if (game.timesPlayed == 1) "1 play" else "${game.timesPlayed} plays",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---- per-game activity log -------------------------------------------------

/**
 * One game's "activity log", Cocoon-style: a hero band with the game's own art and
 * logo over its ES-DE numbers. Dismissed by B, the close button, back — or by
 * pulling the hero band down (the band is the drag zone; the scrolling body
 * would swallow vertical drags).
 */
@Composable
private fun GameDetailSheet(detail: GameDetail, onClose: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    var dragPx by remember(detail.stat) { mutableStateOf(0f) }
    LaunchedEffect(detail.stat) {
        kotlinx.coroutines.delay(120)
        runCatching { closeFocus.requestFocus() }
    }
    val dismissThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 130.dp.toPx() }
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.90f)
            // Follow the finger while pulling down; released past the threshold → close.
            .offset { androidx.compose.ui.unit.IntOffset(0, dragPx.toInt()) }
            // Consume taps so they don't fall through to the list underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonB) {
                    onClose(); true
                } else false
            },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // Hero band: widescreen art with the logo (or name) sitting on it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .pointerInput(detail.stat) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dy -> dragPx = (dragPx + dy).coerceAtLeast(0f) },
                            onDragEnd = {
                                if (dragPx > dismissThreshold) onClose() else dragPx = 0f
                            },
                            onDragCancel = { dragPx = 0f },
                        )
                    },
            ) {
                val hero = detail.media.hero ?: detail.media.mix ?: detail.media.screenshot
                    ?: detail.media.titleScreen
                if (hero != null) {
                    AsyncImage(
                        model = hero,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.25f),
                                1f to MaterialTheme.colorScheme.surface,
                            )
                        ),
                )
                if (detail.media.logo != null) {
                    AsyncImage(
                        model = detail.media.logo,
                        contentDescription = detail.stat.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.55f)
                            .height(90.dp),
                    )
                }
                // Grab handle: signals the band is pull-down-to-close territory.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(width = 44.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.55f)),
                )
                FocusableIconButton(
                    icon = { Icon(Icons.Filled.Close, contentDescription = "Close") },
                    onClick = onClose,
                    focusRequester = closeFocus,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                val box = detail.media.boxFront ?: detail.media.box3d
                if (box != null) {
                    AsyncImage(
                        model = box,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(84.dp)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp),
                            ),
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (detail.favorite) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "Favorite",
                                tint = AchievementGold,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            detail.stat.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        listOfNotNull(
                            detail.systemDisplayName.uppercase(),
                            detail.genre,
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatsCard(title = "Overall activity log") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityRow("Total play time", formatPlayTime(detail.stat.playTimeSeconds))
                        ActivityRow("Times played", "${detail.stat.timesPlayed}")
                        detail.lastPlayedLabel?.let { ActivityRow("Last played", it) }
                        detail.releaseYear?.let { ActivityRow("Released", "$it") }
                        detail.stat.genre?.takeIf { it.isNotBlank() }?.let { ActivityRow("Genre", it) }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** One label/value pair of the activity log, in the app's pill-row rhythm. */
@Composable
private fun ActivityRow(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
