package com.twinlore.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.ContentSources
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.GuideSeries
import com.twinlore.app.data.SeriesMatchResult
import com.twinlore.app.data.ignGuideUrl
import com.twinlore.app.data.matchSeries
import com.twinlore.app.data.neoseekerSearchUrl
import com.twinlore.app.data.sanitiseSiteSearchName
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.components.SupportChip
import com.twinlore.app.ui.theme.getFocusContainer
import kotlinx.coroutines.delay

/**
 * Dropdown menu shown when the user taps **Browse Guide** on the Guide tab picker.
 *
 * Default rows (in order): GameFAQs, Neoseeker, IGN, Steam, Game manual (PDF).
 * Auto-detected rows are prepended when the vault name matches a curated source,
 * each highlighted with a subtle focus-color tint (and an accented game name in
 * the subtitle) to signal the curated recommendation: a [GuideSeries] match
 * (Direct → "Go to <title> walkthrough"; Hub → "Browse <series> walkthroughs"),
 * a StrategyWiki match, a GameFAQs featured-FAQ match, then a Steam Community
 * curated guide match. The GameFAQs and Steam auto rows open their specific
 * curated entry; the generic search rows below stay as fallbacks.
 *
 * On open, focus shifts to the first row so D-pad UP/DOWN works immediately.
 *
 * @param expanded whether the menu is visible (caller-owned state).
 * @param gameName vault display name; blank disables series matching but defaults still show.
 * @param buildSearchUrlForEngine builds a search URL on the user's chosen general search
 *   engine — reused for the Game manual filetype:pdf query so the menu respects user choice.
 * @param onPickUrl called when the user picks any row; receives the URL to load.
 * @param onDismiss called when the menu is dismissed without a pick.
 * @param onOpenSupportUrl called when the user taps the support chip on a row; receives
 *   the source's support URL and should open it in the Browser tab.
 */
@Composable
fun GuideFindMenu(
    expanded: Boolean,
    gameName: String,
    buildSearchUrlForEngine: (String) -> String,
    onPickUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenSupportUrl: (String) -> Unit = {},
    onDownloadCodeWheel: (com.twinlore.app.data.codewheel.CodeWheelEntry) -> Unit = {},
) {
    val seriesMatch = remember(gameName) { matchSeries(gameName) }
    val strategyWikiVm: StrategyWikiMatchViewModel = hiltViewModel()
    val strategyWikiMatch = remember(gameName) { strategyWikiVm.match(gameName) }
    val codeWheelVm: CodeWheelMatchViewModel = hiltViewModel()
    val codeWheelMatch = remember(gameName) { codeWheelVm.match(gameName) }
    val gameFaqsVm: GameFaqsMatchViewModel = hiltViewModel()
    val gameFaqsMatch = remember(gameName) { gameFaqsVm.match(gameName) }
    val steamGuideVm: SteamGuideMatchViewModel = hiltViewModel()
    val steamGuideMatch = remember(gameName) { steamGuideVm.match(gameName) }

    // Not a ViewModel: this is composed inside the fold panel's overlay
    // window too, where there is no activity for hiltViewModel() to scope
    // one to. See AppUiSettings.
    val uiSettings = com.twinlore.app.ui.rememberAppUiSettings()
    val focusColorState by uiSettings.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color

    // First row gets a dedicated requester so we can hand it focus when the menu opens.
    val firstRowFocusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            // DropdownMenu renders into a Popup; the row isn't attached until the next frame.
            delay(50)
            try { firstRowFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 280.dp, max = 380.dp),
    ) {
        val safeName = gameName.trim()
        var firstRowConsumed = false

        // Series row (optional, top) — highlighted with focus color to mark the curated match.
        if (seriesMatch != null) {
            val (label, subtitle, url) = when (seriesMatch) {
                is SeriesMatchResult.Direct -> {
                    val annotatedSubtitle = buildAnnotatedString {
                        append("Go to ")
                        withStyle(SpanStyle(color = focusColor, fontWeight = FontWeight.Bold)) {
                            append(seriesMatch.entry.title)
                        }
                        append(" walkthrough")
                    }
                    Triple(
                        AnnotatedString(seriesMatch.series.displayName),
                        annotatedSubtitle,
                        seriesMatch.entry.url,
                    )
                }
                is SeriesMatchResult.Hub -> Triple(
                    AnnotatedString(seriesMatch.series.displayName),
                    AnnotatedString("Browse ${seriesMatch.series.displayName} walkthroughs"),
                    seriesMatch.series.hubUrl,
                )
            }
            val seriesFaviconHost = hostOf(seriesMatch.series.hubUrl)
            FindMenuRow(
                label = label,
                subtitle = subtitle,
                onClick = { onPickUrl(url) },
                focusColor = focusColor,
                externalFocusRequester = firstRowFocusRequester,
                highlighted = true,
                faviconHost = seriesFaviconHost,
                supportUrl = ContentSources.byFaviconHost(seriesFaviconHost)?.supportUrl,
                onOpenSupport = onOpenSupportUrl,
            )
            firstRowConsumed = true
        }

        // StrategyWiki row (optional) — below the series row, above generic
        // sources. Highlighted like the series row; opens the walkthrough page.
        if (strategyWikiMatch != null) {
            // Subtitle mirrors the series row: "Go to <game> walkthrough" with
            // the matched guide name highlighted.
            val swSubtitle = buildAnnotatedString {
                append("Go to ")
                withStyle(SpanStyle(color = focusColor, fontWeight = FontWeight.Bold)) {
                    append(strategyWikiMatch.title)
                }
                append(" walkthrough")
            }
            FindMenuRow(
                label = AnnotatedString("StrategyWiki"),
                subtitle = swSubtitle,
                onClick = { onPickUrl(strategyWikiMatch.walkthroughUrl) },
                focusColor = focusColor,
                externalFocusRequester = if (!firstRowConsumed) firstRowFocusRequester else null,
                highlighted = true,
                faviconHost = "strategywiki.org",
                supportUrl = ContentSources.byFaviconHost("strategywiki.org")?.supportUrl,
                onOpenSupport = onOpenSupportUrl,
            )
            firstRowConsumed = true
        }

        // Code-wheel row (optional) — downloads the matched archive.org code wheel
        // instead of opening a URL; shown between StrategyWiki and GameFAQs rows.
        if (codeWheelMatch != null) {
            val cwSubtitle = buildAnnotatedString {
                append("Download code wheel for ")
                withStyle(SpanStyle(color = focusColor, fontWeight = FontWeight.Bold)) {
                    append(codeWheelMatch.title)
                }
            }
            FindMenuRow(
                label = AnnotatedString("Code Wheel"),
                subtitle = cwSubtitle,
                onClick = {
                    onDismiss()
                    onDownloadCodeWheel(codeWheelMatch)
                },
                focusColor = focusColor,
                externalFocusRequester = if (!firstRowConsumed) firstRowFocusRequester else null,
                highlighted = true,
                faviconHost = "archive.org",
                supportUrl = com.twinlore.app.data.codewheel.CodeWheelEntry.SUPPORT_URL,
                onOpenSupport = onOpenSupportUrl,
            )
            firstRowConsumed = true
        }

        // GameFAQs auto-detect row (optional) — a curated featured FAQ matched
        // to the vault name. Highlighted; opens that FAQ directly. The generic
        // GameFAQs search row below stays as a fallback if this picks wrong.
        if (gameFaqsMatch != null) {
            val gfSubtitle = buildAnnotatedString {
                append("Go to ")
                withStyle(SpanStyle(color = focusColor, fontWeight = FontWeight.Bold)) {
                    append(gameFaqsMatch.title)
                }
                append(" walkthrough")
            }
            FindMenuRow(
                label = AnnotatedString("GameFAQs"),
                subtitle = gfSubtitle,
                onClick = { onPickUrl(gameFaqsMatch.faqUrl) },
                focusColor = focusColor,
                externalFocusRequester = if (!firstRowConsumed) firstRowFocusRequester else null,
                highlighted = true,
                faviconHost = "gamefaqs.gamespot.com",
                supportUrl = ContentSources.byFaviconHost("gamefaqs.gamespot.com")?.supportUrl,
                onOpenSupport = onOpenSupportUrl,
            )
            firstRowConsumed = true
        }

        // Steam Community auto-detect row — a curated top-rated walkthrough
        // matched to the vault name. Highlighted; deep-links to that guide. The
        // generic Steam search row below stays as the fallback.
        if (steamGuideMatch != null) {
            val scSubtitle = buildAnnotatedString {
                append("Recommended guide for ")
                withStyle(SpanStyle(color = focusColor, fontWeight = FontWeight.Bold)) {
                    append(steamGuideMatch.title)
                }
            }
            FindMenuRow(
                label = AnnotatedString("Steam Community"),
                subtitle = scSubtitle,
                onClick = { onPickUrl(steamGuideMatch.guideUrl) },
                focusColor = focusColor,
                externalFocusRequester = if (!firstRowConsumed) firstRowFocusRequester else null,
                highlighted = true,
                faviconHost = "steamcommunity.com",
                supportUrl = ContentSources.byFaviconHost("steamcommunity.com")?.supportUrl,
                onOpenSupport = onOpenSupportUrl,
            )
            firstRowConsumed = true
        }

        FindMenuRow(
            label = AnnotatedString("GameFAQs"),
            subtitle = AnnotatedString("Recommended for retro games"),
            onClick = { onPickUrl(gameFaqsSearchUrl(safeName)) },
            focusColor = focusColor,
            externalFocusRequester = if (!firstRowConsumed) firstRowFocusRequester else null,
            faviconHost = "gamefaqs.gamespot.com",
            supportUrl = ContentSources.byFaviconHost("gamefaqs.gamespot.com")?.supportUrl,
            onOpenSupport = onOpenSupportUrl,
        )
        FindMenuRow(
            label = AnnotatedString("Neoseeker"),
            subtitle = AnnotatedString("Recommended for retro games"),
            onClick = { onPickUrl(neoseekerSearchUrl(safeName)) },
            focusColor = focusColor,
            faviconHost = "neoseeker.com",
            supportUrl = ContentSources.byFaviconHost("neoseeker.com")?.supportUrl,
            onOpenSupport = onOpenSupportUrl,
        )
        FindMenuRow(
            label = AnnotatedString("IGN"),
            subtitle = AnnotatedString("Game wiki & walkthrough"),
            onClick = { onPickUrl(ignGuideUrl(safeName)) },
            focusColor = focusColor,
            faviconHost = "ign.com",
            supportUrl = ContentSources.byFaviconHost("ign.com")?.supportUrl,
            onOpenSupport = onOpenSupportUrl,
        )
        FindMenuRow(
            label = AnnotatedString("Steam Community Guides"),
            subtitle = AnnotatedString("Recommended for modern games"),
            onClick = { onPickUrl(buildSearchUrlForEngine(steamGuidesQuery(safeName))) },
            focusColor = focusColor,
            faviconHost = "steamcommunity.com",
        )
        FindMenuRow(
            label = AnnotatedString("Game manual (PDF)"),
            subtitle = AnnotatedString("Search for an official PDF manual"),
            onClick = { onPickUrl(buildSearchUrlForEngine(gameManualPdfQuery(safeName))) },
            focusColor = focusColor,
            fallbackIcon = Icons.Filled.PictureAsPdf,
        )
    }
}

@Composable
private fun FindMenuRow(
    label: AnnotatedString,
    subtitle: AnnotatedString,
    onClick: () -> Unit,
    focusColor: Color,
    externalFocusRequester: FocusRequester? = null,
    highlighted: Boolean = false,
    faviconHost: String? = null,
    fallbackIcon: ImageVector? = null,
    supportUrl: String? = null,
    onOpenSupport: (String) -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val rowBg = when {
        isFocused -> focusColor.getFocusContainer()
        highlighted -> focusColor.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    // Titles always use the default theme color, including auto-detected
    // (highlighted) rows — those are signalled by the tinted background and the
    // accented game name in the subtitle, not an accent-colored title.
    val labelColor = MaterialTheme.colorScheme.onSurface
    val rowShape = RoundedCornerShape(8.dp)

    val baseModifier = Modifier
        .background(rowBg, rowShape)
        .then(
            if (isFocused) Modifier.border(2.dp, focusColor, rowShape) else Modifier
        )
        .then(
            if (externalFocusRequester != null) Modifier.focusRequester(externalFocusRequester) else Modifier
        )
        .focusable(interactionSource = interactionSource)

    DropdownMenuItem(
        modifier = baseModifier,
        leadingIcon = if (faviconHost != null || fallbackIcon != null) {
            {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (faviconHost != null) {
                        com.twinlore.app.ui.components.SourceIcon(
                            host = faviconHost,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    } else if (fallbackIcon != null) {
                        Icon(
                            imageVector = fallbackIcon,
                            contentDescription = null,
                            tint = if (highlighted && !isFocused) focusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        } else null,
        trailingIcon = if (supportUrl != null) {
            {
                SupportChip(
                    onTap = { onOpenSupport(supportUrl) },
                    contentDescription = "Support",
                )
            }
        } else null,
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onClick = onClick,
    )
}

/** GameFAQs direct site search — the user explicitly requested this URL shape. */
private fun gameFaqsSearchUrl(gameName: String): String {
    val cleaned = sanitiseSiteSearchName(gameName).ifBlank { "walkthrough" }
    val q = java.net.URLEncoder.encode(cleaned, "UTF-8")
    return "https://gamefaqs.gamespot.com/search?game=$q"
}

/**
 * Steam Community guides search via the user's general search engine
 * scoped to the steamcommunity.com domain. Routing through the user's
 * search engine (rather than Steam's internal search) returns the same
 * style of result for every entry in this menu.
 */
private fun steamGuidesQuery(gameName: String): String =
    if (gameName.isNotBlank()) "$gameName guide site:steamcommunity.com"
    else "guide site:steamcommunity.com"

/** Game-manual PDF query for the user's general search engine. */
private fun gameManualPdfQuery(gameName: String): String =
    if (gameName.isNotBlank()) "$gameName game manual filetype:pdf" else "game manual filetype:pdf"


/** Extract host from a full URL string; falls back to empty if malformed. */
private fun hostOf(url: String): String =
    try { java.net.URI.create(url).host ?: "" } catch (_: Exception) { "" }
