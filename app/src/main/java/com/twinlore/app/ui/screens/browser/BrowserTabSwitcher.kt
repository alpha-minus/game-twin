package com.twinlore.app.ui.screens.browser

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twinlore.app.domain.BrowserTab

/**
 * Full-screen tab switcher overlay. Mounted as the last child of BrowserScreen's
 * root Box; conditional on vm.showSwitcher.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserTabSwitcher(
    tabs: List<BrowserTab>,
    activeTabId: String?,
    focusColor: Color,
    onTap: (BrowserTab) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
    onCloseOthers: () -> Unit,
    vaultTopBarHeightDp: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val cols = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    val view = LocalView.current

    // displayedTabs mirrors `tabs` but defers removals until the per-item
    // AnimatedVisibility exit transition has finished. Without this lag,
    // a closed tab is yanked out of the lazy grid on the same frame the
    // close fires — there's nothing left to animate, the gap snaps closed,
    // and the user perceives it as "the tab just vanished". Adds and
    // field updates (title / favicon refresh) sync through on the next
    // tabs emission as normal.
    val displayedTabs = remember { mutableStateListOf<BrowserTab>() }
    LaunchedEffect(tabs) {
        tabs.forEachIndexed { idx, incoming ->
            val existingIdx = displayedTabs.indexOfFirst { it.id == incoming.id }
            if (existingIdx == -1) {
                displayedTabs.add(idx.coerceAtMost(displayedTabs.size), incoming)
            } else if (displayedTabs[existingIdx] != incoming) {
                displayedTabs[existingIdx] = incoming
            }
        }
        // Removals are NOT applied here — each item's LaunchedEffect below
        // removes itself from displayedTabs after the exit animation idles.
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = vaultTopBarHeightDp)
        ) {
            // Single-line header: "Tabs (N)" + close button. The vault's game
            // name is already shown by VaultSessionTopBar above this overlay,
            // so duplicating it here just pushed the title row lower than the
            // browser content's top edge and shifted the close button off the
            // title's vertical center.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Header count tracks the live `tabs` (not displayedTabs) so
                // it decrements the instant a close fires, not after the
                // exit transition finishes.
                Text(
                    text = "Tabs (${tabs.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (tabs.size > 1) {
                    IconButton(onClick = {
                        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                        onCloseOthers()
                    }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Close other tabs",
                        )
                    }
                }
                IconButton(onClick = {
                    FeedbackManager.play(view, FeedbackEvent.BACK)
                    onDismiss()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close switcher")
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item("__new_tab__") {
                    NewTabCard(onClick = {
                        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                        onNewTab()
                    })
                }
                items(displayedTabs, key = { it.id }) { tab ->
                    // Two layers compose the "fluid close" feel:
                    //   • AnimatedVisibility (shrinkOut + fadeOut) on the
                    //     closed tab itself — without this the card is
                    //     yanked instantly and the slide has nothing to
                    //     contrast against.
                    //   • animateItemPlacement on the card so the remaining
                    //     siblings slide into their new positions instead
                    //     of snapping.
                    // The visibleState pattern (vs `visible: Boolean`) lets
                    // us watch for transition idle and only THEN remove the
                    // tab from displayedTabs, so the lazy grid keeps the
                    // slot alive for the full duration of the exit anim.
                    val isPresent = tabs.any { it.id == tab.id }
                    val visibleState = remember {
                        MutableTransitionState(initialState = true)
                    }
                    visibleState.targetState = isPresent
                    LaunchedEffect(visibleState.currentState) {
                        if (!visibleState.currentState && !visibleState.targetState) {
                            displayedTabs.removeAll { it.id == tab.id }
                        }
                    }
                    AnimatedVisibility(
                        visibleState = visibleState,
                        exit = shrinkOut(
                            shrinkTowards = Alignment.Center,
                            animationSpec = tween(220)
                        ) + fadeOut(animationSpec = tween(220)),
                        modifier = Modifier.animateItemPlacement()
                    ) {
                        BrowserTabCard(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            focusColor = focusColor,
                            onTap = {
                                FeedbackManager.play(view, FeedbackEvent.SWITCH)
                                onTap(tab)
                            },
                            onClose = {
                                FeedbackManager.play(view, FeedbackEvent.BACK)
                                onClose(tab)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserTabCard(
    tab: BrowserTab,
    isActive: Boolean,
    focusColor: Color,
    onTap: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLanding = tab.url.contains("landing.html")
    val labelText = if (isLanding) "Home" else tab.displayTitle
    val hostText = if (isLanding) "" else remember(tab.url) {
        runCatching { java.net.URL(tab.url).host }.getOrNull() ?: tab.url.take(40)
    }
    val borderMod = if (isActive) Modifier.border(2.dp, focusColor, RoundedCornerShape(12.dp)) else Modifier
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .then(borderMod)
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isLanding) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FaviconView(tab.faviconBytes, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                if (!isLanding) {
                    Text(
                        text = hostText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close tab", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NewTabCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text("New tab", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FaviconView(bytes: ByteArray?, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) {
        bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface)
        )
    } else {
        Icon(
            imageVector = Icons.Default.Public,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
