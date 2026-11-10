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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.twinlore.app.data.SearchEngine
import com.twinlore.app.data.SeriesMatchResult
import com.twinlore.app.data.matchSeries
import com.twinlore.app.data.neoseekerSearchUrl
import com.twinlore.app.ui.LocalCachedFocusColor
import com.twinlore.app.ui.components.SupportChip
import com.twinlore.app.ui.theme.getFocusContainer
import kotlinx.coroutines.delay

/**
 * Dropdown menu shown when the user taps **Browse Image** on the Maps tab picker.
 *
 * Rows (in order):
 *   1. VGMaps — always shown, highlighted. Opens the in-app VGMaps picker (not a
 *      web search) via [onOpenVgmapsPicker].
 *   2. (Optional) Omega Metroid row when matchSeries(gameName).series == Metroid.
 *      Direct match goes to the curated per-title /map/ page (entry.mapUrl);
 *      Hub fallback to a Google image search restricted to site:omegametroid.com.
 *      Label is "Omega Metroid"; the "Go to <Game Name> maps" message lives in
 *      the subtitle with the game name styled in focus color.
 *   3. Neoseeker — the Guide tab's own Neoseeker search, the same builder and
 *      so the same results. Always shown.
 *   4. Full Map — image search via the caller's [buildImageSearchUrlForEngine]
 *      with "<game> full map high resolution" as the query. Carries the search
 *      engine's host as its icon to signal "this opens a web image search."
 *
 * The Cheat Sheet / 100% Completion / Resource Farming / Boss-Enemy / Puzzle
 * Solutions shortcuts that used to live here moved to the browser landing
 * page's quick-search tiles.
 *
 * Auto-focuses the first row 50ms after [expanded] flips true so D-pad UP/DOWN
 * navigation works immediately.
 *
 * Zelda and Pokemon series matches are intentionally filtered out — the user
 * requested Metroid-only for this menu.
 *
 * @param expanded whether the menu is visible (caller-owned).
 * @param gameName vault display name; blank still shows defaults but disables
 *   per-game search-query prepending.
 * @param currentSearchEngine the user's chosen search engine for image search.
 * @param buildImageSearchUrlForEngine builds an image-search URL for a given engine
 *   and query — reused from `GuideBrowserHelpers.kt`. Threaded in so the menu has
 *   no static dependency on a global helper.
 * @param onPickUrl called when the user picks any row; receives the URL to load
 *   in the Browser tab.
 * @param onOpenVgmapsPicker called when the user taps the VGMaps row; opens
 *   the VGMaps picker screen.
 * @param onDismiss called when the menu is dismissed without a pick.
 * @param onOpenSupportUrl called when the user taps the support chip on a row; receives
 *   the source's support URL and should open it in the Browser tab.
 */
@Composable
fun MapBrowseMenu(
    expanded: Boolean,
    gameName: String,
    currentSearchEngine: SearchEngine,
    buildImageSearchUrlForEngine: (SearchEngine, String) -> String,
    onPickUrl: (String) -> Unit,
    onOpenVgmapsPicker: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSupportUrl: (String) -> Unit = {},
) {
    val seriesMatch = remember(gameName) {
        matchSeries(gameName)?.takeIf { it.series == GuideSeries.Metroid }
    }

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val cachedFocusColor = LocalCachedFocusColor.current?.value
    val focusColor = cachedFocusColor ?: focusColorState.color

    val firstRowFocusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
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

        // 0. VGMaps. Pinned top, highlighted (recommended). Always shown.
        MapBrowseMenuRow(
            label = AnnotatedString("VGMaps"),
            subtitle = AnnotatedString(
                if (safeName.isNotBlank()) "Browse vgmaps.com maps for $safeName"
                else "Browse vgmaps.com"
            ),
            onClick = {
                onDismiss()
                onOpenVgmapsPicker()
            },
            focusColor = focusColor,
            externalFocusRequester = firstRowFocusRequester,
            highlighted = true,
            faviconHost = "vgmaps.com",
            supportUrl = ContentSources.byFaviconHost("vgmaps.com")?.supportUrl,
            onOpenSupport = onOpenSupportUrl,
        )

        // 1. Metroid series row (optional, highlighted). Label is just "Omega Metroid"
        //    (fits the row's width budget); the "Go to <Game> maps" message lives in
        //    the subtitle with the game name styled in focus color so it still pops.
        if (seriesMatch != null) {
            val (subtitle, url) = when (seriesMatch) {
                is SeriesMatchResult.Direct -> {
                    val annotatedSubtitle = buildAnnotatedString {
                        append("Go to ")
                        withStyle(SpanStyle(color = focusColor, fontWeight = FontWeight.Bold)) {
                            append(seriesMatch.entry.title)
                        }
                        append(" maps")
                    }
                    // Use the curated per-title map page when present; otherwise fall back
                    // to a Google site-restricted image search on omegametroid.com.
                    val target = seriesMatch.entry.mapUrl
                        ?: omegaMetroidMapsSearchUrl(seriesMatch.entry.title)
                    annotatedSubtitle to target
                }
                is SeriesMatchResult.Hub ->
                    AnnotatedString("Browse Omega Metroid maps") to
                        // No curated map for unmatched titles — fall back to site search.
                        omegaMetroidMapsSearchUrl(safeName.ifBlank { "Metroid" })
            }
            MapBrowseMenuRow(
                label = AnnotatedString("Omega Metroid"),
                subtitle = subtitle,
                onClick = { onPickUrl(url) },
                focusColor = focusColor,
                externalFocusRequester = null,
                highlighted = true,
                faviconHost = "omegametroid.com",
                supportUrl = ContentSources.byFaviconHost("omegametroid.com")?.supportUrl,
                onOpenSupport = onOpenSupportUrl,
            )
        }

        // 2. Neoseeker — always shown. Gets the first-row focus requester when no
        //    series row is rendered above it.
        MapBrowseMenuRow(
            label = AnnotatedString("Neoseeker"),
            subtitle = AnnotatedString("Search maps and charts"),
            onClick = { onPickUrl(neoseekerSearchUrl(safeName)) },
            focusColor = focusColor,
            externalFocusRequester = null,
            faviconHost = "neoseeker.com",
            supportUrl = ContentSources.byFaviconHost("neoseeker.com")?.supportUrl,
            onOpenSupport = onOpenSupportUrl,
        )

        // 3. Google — the remaining generic image-search shortcut. Routes through the
        //    user's chosen image search engine. The other five categories (Cheat Sheet,
        //    100% Completion, Resource Farming, Boss / Enemy Location, Puzzle Solutions)
        //    moved to the browser landing page's quick-search templates.
        MapBrowseMenuRow(
            label = AnnotatedString("Google"),
            subtitle = AnnotatedString("Search full map image"),
            onClick = {
                val query = if (safeName.isNotBlank()) "$safeName full map high resolution"
                            else "full map high resolution"
                onPickUrl(buildImageSearchUrlForEngine(currentSearchEngine, query))
            },
            focusColor = focusColor,
            // The search engine's host as the icon signals "this opens a web image
            // search" — the actual engine respects the user's setting.
            faviconHost = "google.com",
        )
    }
}

@Composable
private fun MapBrowseMenuRow(
    label: AnnotatedString,
    subtitle: AnnotatedString,
    onClick: () -> Unit,
    focusColor: Color,
    externalFocusRequester: FocusRequester? = null,
    highlighted: Boolean = false,
    faviconHost: String? = null,
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
    val labelColor = when {
        highlighted && !isFocused -> focusColor
        else -> MaterialTheme.colorScheme.onSurface
    }
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
        leadingIcon = if (faviconHost != null) {
            {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    com.twinlore.app.ui.components.SourceIcon(
                        host = faviconHost,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
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

/**
 * Google image search restricted to omegametroid.com for the supplied game name.
 * tbm=isch forces image-results mode (consistent with buildImageSearchUrlForEngine).
 */
private fun omegaMetroidMapsSearchUrl(gameName: String): String {
    val q = java.net.URLEncoder.encode("site:omegametroid.com $gameName map", "UTF-8")
    return "https://www.google.com/search?tbm=isch&q=$q"
}
