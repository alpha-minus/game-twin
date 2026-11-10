package com.twinlore.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.twinlore.app.ui.utils.gamepadFocusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.abs
import android.view.Display
import android.view.KeyEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.twinlore.app.domain.AppState
import com.twinlore.app.domain.AchievementAward
import com.twinlore.app.domain.AchievementSortInfo
import com.twinlore.app.domain.LibraryAchievementCalc
import com.twinlore.app.domain.LibrarySortField
import com.twinlore.app.domain.LibraryVaultSorter
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.domain.next
import com.twinlore.app.domain.previous
import com.twinlore.app.ui.utils.ImagePathUtils
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.LocalImageLoader
import com.twinlore.app.data.scraping.VaultScrapeCoordinator
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.LibraryViewMode
import com.twinlore.app.data.LibraryBackgroundStyle
import com.twinlore.app.ui.screens.SettingsViewModel
import com.twinlore.app.ui.screens.library.LibraryFocusRules
import com.twinlore.app.ui.utils.PaletteExtractor
import com.twinlore.app.ui.utils.performHapticFeedback
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.thor.led.ThorLedManager
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.data.GameNameSuggestionProvider
import com.twinlore.app.ui.utils.runWithHapticAndLed
import com.twinlore.app.di.GameNameSuggestionEntryPoint
import dagger.hilt.EntryPoints
import com.twinlore.app.ui.theme.ensureReadableAccent
import com.twinlore.app.ui.theme.getContrastingTextColor
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import com.twinlore.app.ui.components.GlowingAwardCircle
import com.twinlore.app.ui.components.accentBarBrush
import com.twinlore.app.ui.components.achievementBarBrush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Context
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.data.retroachievements.RaCredentials
import com.twinlore.app.data.steam.SteamCredentials
import com.twinlore.app.R
import com.twinlore.app.ui.navigation.NavigableMenuItem
import com.twinlore.app.ui.navigation.NavigableTextField
import com.twinlore.app.ui.screens.launcher.LauncherViewModel
import com.twinlore.app.ui.screens.launcher.LauncherApp
import com.twinlore.app.domain.VaultTab
import com.twinlore.app.ui.navigation.longPressHandler
import com.twinlore.app.ui.theme.getFocusContainer
import com.twinlore.app.util.PerfLog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * Opens the support/"chip in" destination: the Gold Edition checkout, the cheapest way in.
 *
 * Shared by the milestone support prompt and the emblem-tap dialog, so both land where the
 * Settings "Chip in" row does. It used to open the store front page, which made the same
 * intent behave two different ways depending on where it was tapped.
 * Entry points that know which edition the user is after should call
 * [com.twinlore.app.data.license.LicenseConfig.checkoutUrlFor] instead.
 */
internal fun openSupportDevPage(context: Context) = openLemonSqueezyCheckout(context)

/** Opens the default checkout. Kept separate from [openSupportDevPage] only to keep call sites
 *  reading in the language of their own screen. */
internal fun openLemonSqueezyCheckout(context: Context) {
    val url = com.twinlore.app.data.license.LicenseConfig.CHECKOUT_URL
    try {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url),
        ).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("LibraryScreen", "Failed to open checkout URL: $url", e)
    }
}

/**
 * Converts packed ARGB Int (from stored vault.artworkVibrantArgb) to Compose Color for FocusColor.findClosest.
 */
private fun argbToComposeColor(argb: Int): Color = Color(
    red = (argb shr 16 and 0xFF) / 255f,
    green = (argb shr 8 and 0xFF) / 255f,
    blue = (argb and 0xFF) / 255f,
    alpha = (argb shr 24 and 0xFF) / 255f
)

/**
 * Vault context menu and its sub-dialogs (rename, delete, assign artwork, scrape artwork).
 * Extracted from LibraryScreen to reduce register count.
 */
@Composable
internal fun LibraryVaultContextMenuSection(
    vault: Vault,
    libraryViewModel: LibraryViewModel,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMultiDeleteDialog by remember { mutableStateOf(false) }
    var showAssignBoxArtDialog by remember { mutableStateOf(false) }
    var showScrapeArtworkDialog by remember { mutableStateOf(false) }
    var scrapedArtworkOptions by remember { mutableStateOf<List<ArtworkOption>?>(null) }
    var isScrapingArtwork by remember { mutableStateOf(false) }
    var scrapingArtworkError by remember { mutableStateOf<String?>(null) }
    var isAssigningArtwork by remember { mutableStateOf(false) }

    // File picker launcher for artwork assignment.
    val artworkImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            libraryViewModel.assignArtworkFromFile(
                vaultId = vault.id,
                fileUri = uri,
                context = context,
                onSuccess = {
                    android.widget.Toast.makeText(
                        context,
                        "Artwork assigned",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    showScrapeArtworkDialog = false
                    onDismiss()
                },
                onError = { error ->
                    android.widget.Toast.makeText(
                        context,
                        "Error: $error",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    if (showMultiDeleteDialog) {
        MultiDeleteVaultsDialog(
            libraryViewModel = libraryViewModel,
            initiallySelectedId = vault.id,
            onDismiss = {
                showMultiDeleteDialog = false
                onDismiss()
            }
        )
    }

    // The picker replaces the menu rather than stacking on it.
    if (!showMultiDeleteDialog) VaultContextMenu(
        vault = vault,
        onDismiss = onDismiss,
        onRename = { showRenameDialog = true },
        onScrapeArtwork = {
            showScrapeArtworkDialog = true
            isScrapingArtwork = true
            scrapingArtworkError = null
            scrapedArtworkOptions = null
        },
        onRescanLocalMedia = {
            libraryViewModel.rescanLocalMediaForVault(vault.id)
        },
        onDelete = { showDeleteDialog = true },
        onDeleteMultiple = { showMultiDeleteDialog = true }
    )

    // Rename Dialog
    if (showRenameDialog) {
        RenameVaultDialog(
            currentName = vault.displayName,
            onDismiss = {
                showRenameDialog = false
                onDismiss()
            },
            onConfirm = { newName ->
                libraryViewModel.renameVault(vault.id, newName)
                showRenameDialog = false
                onDismiss()
                android.widget.Toast.makeText(
                    context,
                    "Game renamed",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Assign Box Art Dialog (per PRD: Long-press image -> "Assign Image as Box Art")
    if (showAssignBoxArtDialog) {
        AssignBoxArtDialog(
            vaultName = vault.displayName,
            onDismiss = { showAssignBoxArtDialog = false },
            onConfirm = { imageUrl ->
                libraryViewModel.assignBoxArtFromUrl(
                    vaultId = vault.id,
                    imageUrl = imageUrl,
                    onSuccess = {
                        libraryViewModel.refreshVaultList()
                        libraryViewModel.onInputReceived()
                        android.widget.Toast.makeText(
                            context,
                            "Box art updated",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        showAssignBoxArtDialog = false
                        onDismiss()
                    },
                    onError = { error ->
                        android.widget.Toast.makeText(
                            context,
                            "Error: $error",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        )
    }

    // Delete Dialog
    if (showDeleteDialog) {
        DeleteVaultDialog(
            vault = vault,
            onDismiss = {
                showDeleteDialog = false
                onDismiss()
            },
            onConfirm = {
                libraryViewModel.deleteVault(vault.id)
                showDeleteDialog = false
                onDismiss()
                android.widget.Toast.makeText(
                    context,
                    "Vault deleted",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Collect artwork options when dialog is shown
    LaunchedEffect(showScrapeArtworkDialog) {
        if (showScrapeArtworkDialog) {
            isScrapingArtwork = true
            scrapingArtworkError = null
            scrapedArtworkOptions = null

            libraryViewModel.scrapeArtworkOptions(vault.id).collect { result ->
                isScrapingArtwork = false
                when (result) {
                    is ArtworkOptionsResult.Success -> {
                        scrapedArtworkOptions = result.options
                        if (result.options.isEmpty()) {
                            scrapingArtworkError = "No artwork found for this game"
                        }
                    }
                    is ArtworkOptionsResult.Error -> {
                        scrapedArtworkOptions = emptyList()
                        scrapingArtworkError = result.message
                    }
                }
            }
        }
    }

    // Scrape Artwork Selection Dialog
    if (showScrapeArtworkDialog) {
        ScrapeArtworkSelectionDialog(
            artworkOptions = scrapedArtworkOptions ?: emptyList(),
            isLoading = isScrapingArtwork,
            errorMessage = scrapingArtworkError,
            isAssigning = isAssigningArtwork,
            onAddFromFile = {
                artworkImagePickerLauncher.launch(arrayOf("image/*"))
            },
            onRemoveArtwork = if (vault.hasArtwork) {
                {
                    libraryViewModel.removeArtwork(vault.id)
                    libraryViewModel.onInputReceived()
                    android.widget.Toast.makeText(
                        context,
                        "Artwork removed",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    showScrapeArtworkDialog = false
                    onDismiss()
                }
            } else null,
            onArtworkSelected = { imageUrl ->
                isAssigningArtwork = true
                val option = scrapedArtworkOptions?.firstOrNull { it.url == imageUrl }
                if (option?.isLocal == true && option.resourceFileId == null) {
                    // Untracked image sitting in the vault folder — assign via its URI.
                    libraryViewModel.assignArtworkFromLocalUri(
                        vaultId = vault.id,
                        uriString = imageUrl,
                        onSuccess = {
                            isAssigningArtwork = false
                            libraryViewModel.refreshVaultList()
                            libraryViewModel.onInputReceived()
                            android.widget.Toast.makeText(
                                context,
                                "Artwork updated",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            showScrapeArtworkDialog = false
                            onDismiss()
                        },
                        onError = { error ->
                            isAssigningArtwork = false
                            android.widget.Toast.makeText(
                                context,
                                "Failed: $error",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                } else if (option?.isLocal == true && option.resourceFileId != null) {
                    libraryViewModel.assignArtworkFromResource(
                        vaultId = vault.id,
                        resourceFileId = option.resourceFileId,
                        onSuccess = {
                            isAssigningArtwork = false
                            libraryViewModel.onInputReceived()
                            android.widget.Toast.makeText(
                                context,
                                "Artwork updated",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            showScrapeArtworkDialog = false
                            onDismiss()
                        },
                        onError = { error ->
                            isAssigningArtwork = false
                            android.widget.Toast.makeText(
                                context,
                                "Failed: $error",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                } else {
                    libraryViewModel.assignBoxArtFromUrl(
                        vaultId = vault.id,
                        imageUrl = imageUrl,
                        onSuccess = {
                            isAssigningArtwork = false
                            libraryViewModel.refreshVaultList()
                            libraryViewModel.onInputReceived()
                            android.widget.Toast.makeText(
                                context,
                                "Artwork updated",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            showScrapeArtworkDialog = false
                            onDismiss()
                        },
                        onError = { error ->
                            isAssigningArtwork = false
                            android.widget.Toast.makeText(
                                context,
                                "Error: $error",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            },
            onDismiss = {
                if (!isAssigningArtwork) {
                    showScrapeArtworkDialog = false
                }
            }
        )
    }
}

/**
 * Whether the detection dialog may show yet.
 *
 * [LibraryScrapingProgressDialog] is a Dialog, and a Dialog is its own window:
 * it draws over the preload splash no matter where it sits in the Library's
 * Compose tree. Detection usually starts inside the preload window - Show Last
 * Played on Launch runs it on every cold start - so without this the first
 * thing a cold start showed was "Detecting game..." stamped across the logo.
 *
 * The whole fade counts, not just the hold: the dialog appearing halfway
 * through the splash dissolving is the same problem arriving early.
 */
internal fun detectionDialogVisible(isScraping: Boolean, splashAlpha: Float): Boolean =
    isScraping && splashAlpha == 0f

/**
 * Whether a finished detection pass reveals the fullscreen showcase. Every pass
 * emits detectionFocusApplied, including ones that found nothing, so a focused
 * vault alone is not enough: it may be left over from before the pass. Only a
 * pass that raised justFocusedByDetection has something to present.
 */
internal fun detectionShowcaseReveals(focusedVaultId: String?, justFocusedByDetection: Boolean): Boolean =
    focusedVaultId != null && justFocusedByDetection

/**
 * Scraping progress dialog shown during box art detection.
 * Extracted from LibraryScreen to reduce register count.
 */
@Composable
private fun LibraryScrapingProgressDialog(
    scrapingProgress: VaultScrapeCoordinator.ScrapingProgress?,
    displayFocusColor: FocusColor,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* Non-dismissible */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Show progress message
            val message = when {
                scrapingProgress != null -> {
                    when (val progress = scrapingProgress!!) {
                        is VaultScrapeCoordinator.ScrapingProgress.Processing -> progress.message
                        is VaultScrapeCoordinator.ScrapingProgress.Complete -> {
                            // Only box art is scraped now, so this is always a
                            // single asset — show a plain confirmation instead of
                            // an always-1/1 count.
                            val assigned = progress.results.any { it is VaultScrapeCoordinator.AssetScrapeResult.Success }
                            if (assigned) "Artwork assigned" else "No artwork found"
                        }
                        is VaultScrapeCoordinator.ScrapingProgress.Error -> {
                            val err = progress.error
                            if (err is com.twinlore.app.data.scraping.ScrapeError) {
                                err.userMessage
                            } else {
                                err.message ?: err.javaClass.simpleName
                            }
                        }
                        is VaultScrapeCoordinator.ScrapingProgress.AssetResult -> {
                            when (val result = progress.result) {
                                is VaultScrapeCoordinator.AssetScrapeResult.Success -> "Downloaded ${result.assetType.name.lowercase()}"
                                is VaultScrapeCoordinator.AssetScrapeResult.NotFound -> "${result.assetType.name} not found"
                                is VaultScrapeCoordinator.AssetScrapeResult.Error -> "Error downloading ${result.assetType.name}"
                                is VaultScrapeCoordinator.AssetScrapeResult.Skipped -> "Skipped ${result.assetType.name}: ${result.reason}"
                            }
                        }
                    }
                }
                else -> "Starting scraping..."
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    val focusColor = displayFocusColor.color

                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = focusColor,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(bottom = 8.dp)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val isError = scrapingProgress is VaultScrapeCoordinator.ScrapingProgress.Error
                    // Skip button to cancel scraping - show while work is in flight.
                    // Don't show on Complete or Error states (Error gets its own
                    // Retry / Dismiss actions below).
                    val showSkipButton = scrapingProgress?.let { progress ->
                        when (progress) {
                            is VaultScrapeCoordinator.ScrapingProgress.Processing -> true
                            is VaultScrapeCoordinator.ScrapingProgress.AssetResult -> true
                            is VaultScrapeCoordinator.ScrapingProgress.Complete -> false
                            is VaultScrapeCoordinator.ScrapingProgress.Error -> false
                        }
                    } ?: false

                    if (showSkipButton) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = focusColor
                            )
                        ) {
                            Text("Skip")
                        }
                    }

                    // On a network/timeout failure the dialog stays open (no
                    // auto-dismiss) so the user reads the guidance and chooses:
                    // Retry re-runs the scrape, Dismiss closes it and leaves the
                    // entry — the user can Assign Artwork or add a file later.
                    if (isError) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextButton(
                                onClick = onDismissError,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("Dismiss", maxLines = 1, softWrap = false)
                            }
                            TextButton(
                                onClick = onRetry,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = focusColor
                                )
                            ) {
                                Text("Retry", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

// `LibraryPreloadSplash` lives in ui/components: it's also rendered by
// MainActivity during `StartupGate.Loading` so the in-Compose splash covers
// the DataStore-read window the same way it covers the image-preload window.

/**
 * All three fuzzy match confirmation dialogs (manual creation, auto-detection, scrape-path).
 * Extracted from LibraryScreen to reduce register count.
 */
@Composable
private fun LibraryFuzzyMatchDialogs(
    fuzzyMatchPrompt: LibraryViewModel.FuzzyMatchPrompt?,
    autoDetectFuzzyMatch: com.twinlore.app.data.SyncLatestPlayedGameUseCase.PendingFuzzyMatch?,
    scrapePathFuzzyMatch: LibraryViewModel.AutoDetectFuzzyMatchForScrape?,
    accentColor: Color,
    libraryViewModel: LibraryViewModel
) {
    val palette = LocalThemePalette.current
    val dialogSurface = palette.background
    val dialogText = palette.foreground

    // Fuzzy Match Confirmation Dialog (manual creation)
    fuzzyMatchPrompt?.let { prompt ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { libraryViewModel.declineFuzzyMatch() },
            containerColor = dialogSurface,
            titleContentColor = accentColor,
            textContentColor = dialogText,
            title = { androidx.compose.material3.Text("Similar vault found") },
            text = {
                androidx.compose.material3.Text(
                    "\"${prompt.newName}\" is similar to an existing vault " +
                    "\"${prompt.existingVault.displayName}\" " +
                    "(${(prompt.similarity * 100).toInt()}% match).\n\nUse existing vault?"
                )
            },
            confirmButton = {
                com.twinlore.app.ui.navigation.NavigableButton(
                    text = "Use existing",
                    onClick = { libraryViewModel.confirmFuzzyMatch() },
                    focusId = "fuzzy_confirm",
                    containerColor = accentColor,
                    contentColor = accentColor.getContrastingTextColor(),
                    compact = true
                )
            },
            dismissButton = {
                com.twinlore.app.ui.navigation.NavigableButton(
                    text = "Create new",
                    onClick = { libraryViewModel.declineFuzzyMatch() },
                    focusId = "fuzzy_decline",
                    containerColor = dialogSurface,
                    contentColor = dialogText,
                    compact = true
                )
            }
        )
    }

    // Auto-Detection Fuzzy Match Confirmation Dialog
    autoDetectFuzzyMatch?.let { pending ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { libraryViewModel.dismissAutoDetectFuzzyMatch() },
            containerColor = dialogSurface,
            titleContentColor = accentColor,
            textContentColor = dialogText,
            title = { androidx.compose.material3.Text("Similar entry detected") },
            text = {
                androidx.compose.material3.Text(
                    "\"${pending.newName}\" was detected but a similar entry " +
                    "\"${pending.existingVault.displayName}\" already exists " +
                    "(${(pending.similarity * 100).toInt()}% match).\n\nIs this the same game?"
                )
            },
            confirmButton = {
                com.twinlore.app.ui.navigation.NavigableButton(
                    text = "Yes, same game",
                    onClick = { libraryViewModel.confirmAutoDetectFuzzyMatch() },
                    focusId = "auto_fuzzy_confirm",
                    containerColor = accentColor,
                    contentColor = accentColor.getContrastingTextColor(),
                    compact = true
                )
            },
            dismissButton = {
                com.twinlore.app.ui.navigation.NavigableButton(
                    text = "No, create new",
                    onClick = { libraryViewModel.declineAutoDetectFuzzyMatch() },
                    focusId = "auto_fuzzy_decline",
                    containerColor = dialogSurface,
                    contentColor = dialogText,
                    compact = true
                )
            }
        )
    }

    // Scrape-path (RetroArch/ES-DE) Fuzzy Match Confirmation Dialog
    scrapePathFuzzyMatch?.let { pending ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { libraryViewModel.dismissScrapePathFuzzyMatch() },
            containerColor = dialogSurface,
            titleContentColor = accentColor,
            textContentColor = dialogText,
            title = { androidx.compose.material3.Text("Similar entry detected") },
            text = {
                androidx.compose.material3.Text(
                    "\"${pending.newName}\" was detected but a similar entry " +
                    "\"${pending.existingVault.displayName}\" already exists " +
                    "(${(pending.similarity * 100).toInt()}% match).\n\nIs this the same game?"
                )
            },
            confirmButton = {
                com.twinlore.app.ui.navigation.NavigableButton(
                    text = "Yes, same game",
                    onClick = { libraryViewModel.confirmScrapePathFuzzyMatch() },
                    focusId = "scrape_fuzzy_confirm",
                    containerColor = accentColor,
                    contentColor = accentColor.getContrastingTextColor(),
                    compact = true
                )
            },
            dismissButton = {
                com.twinlore.app.ui.navigation.NavigableButton(
                    text = "No, create new",
                    onClick = { libraryViewModel.declineScrapePathFuzzyMatch() },
                    focusId = "scrape_fuzzy_decline",
                    containerColor = dialogSurface,
                    contentColor = dialogText,
                    compact = true
                )
            }
        )
    }
}

/**
 * Library screen - root screen showing all vaults in Steam-style grid
 */
@Composable
fun LibraryScreen(
    mainViewModel: MainViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    launcherViewModel: LauncherViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        PerfLog.mark("LibraryScreen first composition")
    }
    val currentDisplayId = LocalView.current.display?.displayId ?: Display.DEFAULT_DISPLAY
    // Vaults are now a StateFlow that stays hot in ViewModel - no reinitialization needed
    val vaults by libraryViewModel.vaults.collectAsState()
    val searchQuery by libraryViewModel.searchQuery.collectAsState()
    val filteredVaults = remember(vaults, searchQuery) {
        if (searchQuery.isBlank()) {
            vaults
        } else {
            val query = searchQuery.lowercase()
            vaults.filter { vault ->
                vault.displayName.lowercase().contains(query) ||
                vault.romFilename?.lowercase()?.contains(query) == true ||
                vault.romPath?.lowercase()?.contains(query) == true
            }
        }
    }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val achievementSortInfo by libraryViewModel.achievementSortInfo.collectAsState()
    val librarySortField by settingsViewModel.librarySortField.collectAsState(initial = LibrarySortField.DATE_ADDED)
    val librarySortDirection by settingsViewModel.librarySortDirection.collectAsState(initial = SortDirection.DESC)
    val libraryAchievementProgress by settingsViewModel.libraryAchievementProgress.collectAsState(initial = true)
    val sortedVaults = remember(filteredVaults, librarySortField, librarySortDirection, achievementSortInfo) {
        LibraryVaultSorter.sort(filteredVaults, librarySortField, librarySortDirection, achievementSortInfo)
    }
    val isScraping by libraryViewModel.isScraping.collectAsState()
    val isDiscovering by libraryViewModel.isDiscovering.collectAsState()
    val scrapingProgress by libraryViewModel.scrapingProgress.collectAsState()
    val scrapingVaultIds by libraryViewModel.scrapingVaultIds.collectAsState()
    val scrapeError by libraryViewModel.scrapeError.collectAsState()
    val scrapeErrorSnackbarHost = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(scrapeError) {
        val err = scrapeError ?: return@LaunchedEffect
        scrapeErrorSnackbarHost.showSnackbar(err.userMessage)
        libraryViewModel.onScrapeErrorShown()
    }
    // Handle vault-missing events (user tried to open a vault whose files are gone from disk)
    LaunchedEffect(Unit) {
        mainViewModel.vaultMissing.collect { vaultId ->
            scrapeErrorSnackbarHost.showSnackbar("Vault files not found on disk. The storage folder may have changed.")
        }
    }
    val fuzzyMatchPrompt by libraryViewModel.fuzzyMatchPrompt.collectAsState()
    val supportPrompt by libraryViewModel.supportPrompt.collectAsState()
    val refreshSummary by libraryViewModel.refreshSummary.collectAsState()
    val autoDetectFuzzyMatch by libraryViewModel.autoDetectFuzzyMatch.collectAsState()
    val scrapePathFuzzyMatch by libraryViewModel.autoDetectFuzzyMatchForScrape.collectAsState()
    val justFocusedByDetection by libraryViewModel.justFocusedByDetection.collectAsState()
    val justFocusedByDetectionVersion by libraryViewModel.justFocusedByDetectionVersion.collectAsState()
    val artDisplayModeState = libraryViewModel.artDisplayMode.collectAsState(initial = com.twinlore.app.data.ArtDisplayMode.FIT)
    val needsFolderSelection by libraryViewModel.needsFolderSelection.collectAsState()
    val lastPlayedSource by settingsViewModel.lastPlayedSource.collectAsState(initial = com.twinlore.app.data.LastPlayedSource.RETROARCH)
    val focusedVaultId by libraryViewModel.focusedVaultId.collectAsState()
    val vaultsInitialLoadComplete by libraryViewModel.vaultsInitialLoadComplete.collectAsState()
    val isTopBarFocused by libraryViewModel.isTopBarFocused.collectAsState()
    val powerSaverMode by settingsViewModel.powerSaverMode.collectAsState(initial = false)
    val autoFocusColorDetection by settingsViewModel.autoFocusColorDetection.collectAsState(initial = true)
    val palette = LocalThemePalette.current
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val focusSoundEnabled by settingsViewModel.focusSoundEnabled.collectAsState(initial = true)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = com.twinlore.app.data.LedTurnOffTime.SEC_15)
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val blurArtworkInvalidateTimestamp by libraryViewModel.blurArtworkInvalidateTimestamp.collectAsState(initial = null)
    val libraryViewMode by settingsViewModel.libraryViewMode.collectAsState(initial = LibraryViewMode.GRID_EXTRA_LARGE)
    // Hoisted so the bottom hint bar can offer a way back to the top: the bar is rendered here
    // while the scroll lives in the surfaces below. Keyed only on the grid-versus-launcher
    // boundary, not the full view mode: the launcher's items (bar, carousel, dividers, section
    // headers, app cells) are nothing like the vault grid's, so a position carried across would
    // scroll to a meaningless place. LIST, GRID_SMALL, GRID_MEDIUM and GRID_EXTRA_LARGE
    // deliberately share one position because they lay out the same vaults, just at different
    // column counts, so L1 must not throw the user's place away when cycling between them.
    // rememberSaveable so the grid position survives process death / config changes not covered
    // by android:configChanges. The launcher position does not: libraryViewMode comes from a
    // cold DataStore flow whose `initial` is GRID_EXTRA_LARGE, so the first composition after
    // recreation always keys false, rememberSaveable restores into the grid-side state, and when
    // DataStore then emits LAUNCHER the key flips to a fresh LazyGridState() at 0. Deliberately
    // NOT ViewModel state: the previous round deleted the last VM-held scroll flag because two
    // collectors writing one shared flag is what made the chrome unpredictable.
    val libraryScrollState = rememberSaveable(
        libraryViewMode == LibraryViewMode.LAUNCHER,
        saver = LazyGridState.Saver,
    ) { LazyGridState() }
    // derivedStateOf, not a direct read: firstVisibleItemIndex changes on every scrolled row and
    // reading it in this composition would recompose the whole library screen each time. Item 0
    // is the top bar and item 1 the first row, so >= 2 means the top is properly gone and the
    // control cannot flicker while nudging around the first row.
    val showBackToTop by remember(libraryScrollState) {
        derivedStateOf { libraryScrollState.firstVisibleItemIndex >= 2 }
    }
    val libraryScrollCoroutineScope = rememberCoroutineScope()
    val gridDisplayRatioState by settingsViewModel.gridDisplayRatio.collectAsState(initial = com.twinlore.app.data.GridDisplayRatio.RATIO_3_4)
    val gridAspectRatio = gridDisplayRatioState.aspectRatio
    val showLibraryItemNames by settingsViewModel.showLibraryItemNames.collectAsState(initial = true)
    // The library's full-screen backdrop (round two, task 2). BLURRED_ART is today's
    // look: per-game blurred cover art. SOLID skips that entirely -- no decode, no blur,
    // no cross-dissolve, and no reason to prefetch neighbours' artwork paths, which is
    // the whole point of offering it. WALLPAPER draws the shared "app wallpaper" image
    // (picked in Settings).
    val libraryBackgroundStyle by settingsViewModel.libraryBackgroundStyle.collectAsState(initial = LibraryBackgroundStyle.BLURRED_ART)
    val libraryWallpaperUri by settingsViewModel.appWallpaperImage.collectAsState(initial = null)
    val libraryWallpaperDim by settingsViewModel.libraryWallpaperDim.collectAsState(initial = 0.5f)
    val libraryWallpaperBlur by settingsViewModel.libraryWallpaperBlur.collectAsState(initial = 0f)
    val wallpaperContext = LocalContext.current
    /**
     * The wallpaper as a Coil request with the blur BAKED IN at decode, keyed by radius.
     *
     * Deliberately not Modifier.blur. A live full-screen RenderEffect here is the exact
     * regression WallpaperBlurTransformation was written to remove: profiled on an AYN Thor at
     * 15.1ms per frame against 3.9ms without it (see LibraryWallpaperBlur.kt). The wallpaper is
     * a still image whose radius only changes when the slider moves, so blurring once and
     * letting Coil cache it costs nothing per frame. Null when there is nothing to draw, which is
     * also what tells the bar and the launcher's readability scrim to keep their ordinary
     * item-names dim instead of standing down for the wallpaper's own.
     */
    val libraryWallpaperModel: Any? = remember(
        libraryWallpaperUri, libraryWallpaperBlur, powerSaverMode, wallpaperContext,
    ) {
        val uri = libraryWallpaperUri
        when {
            powerSaverMode || uri == null -> null
            // Unblurred: full resolution, no transformation, no downscale.
            libraryWallpaperBlur <= 0f -> uri
            else -> {
                val radius = (libraryWallpaperBlur * WALLPAPER_MAX_BLUR_RADIUS)
                    .toInt().coerceIn(1, WALLPAPER_MAX_BLUR_RADIUS)
                ImageRequest.Builder(wallpaperContext)
                    .data(uri)
                    // Small source is the point: the blur runs over 270x480 pixels and scales
                    // back up, which is invisible once blurred and keeps the decode cheap.
                    .size(coil.size.Size(270, 480))
                    .transformations(WallpaperBlurTransformation(radius = radius))
                    .memoryCacheKey("library_wallpaper_b${radius}_${uri.hashCode()}")
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
            }
        }
    }
    val buttonGlyphStyle by settingsViewModel.buttonGlyphStyle.collectAsState(initial = com.twinlore.app.data.ButtonGlyphStyle.XBOX)
    val swapFaceButtons by settingsViewModel.swapFaceButtons.collectAsState(initial = false)
    // Noir library mode rides on the effective (tier-gated) app-icon style: selecting the
    // Noir emblem desaturates every unfocused library cover. Provided via CompositionLocal
    // to the cover tiles below so the three view-mode wrappers need no new parameter.
    val effectiveAppIcon by settingsViewModel.appIcon.collectAsState()
    val noirLibraryMode = effectiveAppIcon == com.twinlore.app.data.AppIcon.NOIR
    // Adventure premium style: warm amber grade on the blurred wallpaper (static, zero idle
    // cost) — the Adventure counterpart to Noir's cold wash. See [adventureBackdropColorFilter].
    val adventureLibraryMode = effectiveAppIcon == com.twinlore.app.data.AppIcon.ADVENTURE
    // Gold premium style: gold focus shimmer on covers (Adventure gets the silver variant).
    val goldLibraryMode = effectiveAppIcon == com.twinlore.app.data.AppIcon.GOLD
    val homeFolderUri by settingsViewModel.homeFolderUri.collectAsState(initial = "placeholder")
    val raCredentials by settingsViewModel.raCredentials.collectAsState(initial = null)
    val steamCredentials by settingsViewModel.steamCredentials.collectAsState(initial = null)
    val hasAchievementCredentials = raCredentials != null || steamCredentials != null
    // Achievement progress bars show in any sort mode, gated on the Appearance toggle + credentials.
    val showAchievementBars = libraryAchievementProgress && hasAchievementCredentials
    val isDarkTheme = palette.isDark
    val displayFocusColor: FocusColor = remember(autoFocusColorDetection, focusColorState, focusedVaultId, filteredVaults, isDarkTheme) {
        when {
            !autoFocusColorDetection -> focusColorState
            else -> {
                val v = focusedVaultId?.let { id -> filteredVaults.firstOrNull { it.id == id } }
                if (v?.artworkVibrantArgb != null) FocusColor.findClosest(argbToComposeColor(v.artworkVibrantArgb), isDarkTheme)
                else focusColorState
            }
        }
    }
    // Raw accent color for the focused vault so grid, carousel, and vault session all use the same detected color (no enum quantizing).
    // Readability-adjusted so it stays visible on the current theme.
    val targetAccentColorRaw: Color = remember(autoFocusColorDetection, focusColorState, focusedVaultId, filteredVaults, isDarkTheme) {
        if (autoFocusColorDetection) {
            val v = focusedVaultId?.let { id -> filteredVaults.firstOrNull { it.id == id } }
            v?.artworkVibrantArgb?.let { argb ->
                argbToComposeColor(argb).ensureReadableAccent(isDarkTheme)
            } ?: focusColorState.color
        } else focusColorState.color
    }
    // Animate accent color so grid borders/backgrounds transition smoothly instead of flashing
    // Read in the DRAW phase by everything that only paints it: reading an animated value in
    // composition and passing it down by value invalidates every consumer on every frame of the
    // tween, not just the frame the target actually changed on. Instrumentation confirmed this
    // change removes the whole-grid recomposition on a focus move (roughly 7x12 recomposes per
    // keypress down to 3). It did not by itself lower the measured main-thread CPU during
    // navigation: per-frame window invalidation during the tween has other sources. The
    // recomposition-scoping fix stands on its own regardless of that number.
    val accentColorState = androidx.compose.animation.animateColorAsState(
        targetValue = targetAccentColorRaw,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 120),
        label = "accentColor"
    )
    val accentColorProvider: () -> Color =
        remember(accentColorState) { { accentColorState.value } }
    // The settled target, for consumers that need a plain Color in composition (dialogs, menu
    // text, the LED). Reading this creates no animation subscription, so those consumers stop
    // recomposing per tween frame. They lose the 120ms fade, which is invisible on a dialog
    // that is not on screen when the focus moves.
    val displayAccentColorRaw: Color = targetAccentColorRaw
    val context = LocalContext.current
    val view = LocalView.current

    // Immersive in landscape: hide the system status bar so our overlay top bar
    // can sit at the very top of the screen. Restore on dispose / portrait
    // (refcounted — see HideStatusBarInLandscape for the screen-handoff race).
    com.twinlore.app.ui.utils.HideStatusBarInLandscape()

    // Resolves a vault.artworkResourceId to a Uri suitable for Coil. Captured once
    // at this scope so non-Composable callers (LaunchedEffect bodies) can use it.
    val libraryResourceResolver = remember(context) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                context.applicationContext,
                com.twinlore.app.di.ResourceResolverEntryPoint::class.java
            )
            .resourceResolver()
    }

    // Pre-initialize sound manager on screen load so it's ready for first focus change.
    // The library-arrival startup chime fires later, once the content is actually
    // visible (see the imagesPreloaded-keyed LaunchedEffect below).
    LaunchedEffect(Unit) {
        FeedbackManager.initialize(context)
    }

    // Update Thor LED when focus/color settle. Key on targetAccentColorRaw (the
    // settled target value) instead of displayAccentColorRaw (the per-frame
    // animateColorAsState output) so a single color transition doesn't relaunch
    // the effect 7+ times during its 120ms tween. The breath animation inside
    // onFocusGained already handles the visual transition on the LED itself.
    // Skip when lazy extraction is pending (vault has artwork but no artworkVibrantArgb) to avoid double LED change.
    LaunchedEffect(focusedVaultId, targetAccentColorRaw, filteredVaults.size, ledEnabled, ledBrightness, ledSaturationBoost, ledTurnOffTime) {
        val v = focusedVaultId?.let { id -> filteredVaults.firstOrNull { it.id == id } }
        if (v != null && v.hasArtwork && v.artworkVibrantArgb == null) return@LaunchedEffect
        kotlinx.coroutines.delay(100)
        if (focusedVaultId != null && ledEnabled) {
            ThorLedManager.onFocusGained(
                targetAccentColorRaw,
                ledEnabled,
                ledBrightness,
                ledSaturationBoost,
                ledTurnOffTime.inactivityMs
            )
        }
    }
    // When auto-detection is on and focus has settled, persist the detected color so Settings, vault tabs, menus use it as accent.
    // Also persist the readability-adjusted raw color so vault session screens show the exact same accent as the library grid/carousel.
    // Update the cached focus color IMMEDIATELY so the bottom bar / navigable buttons
    // reflect the correct accent without waiting for the DataStore round-trip (which
    // goes: 300 ms delay → DataStore write → combine flow → cache). Without this,
    // entering a vault causes a visible color flash on the bottom menu.
    val cachedFocusColor = com.twinlore.app.ui.LocalCachedFocusColor.current
    LaunchedEffect(focusedVaultId, displayFocusColor, targetAccentColorRaw, autoFocusColorDetection, isDarkTheme) {
        if (!autoFocusColorDetection || focusedVaultId == null) {
            if (!autoFocusColorDetection) settingsViewModel.setRawAccentColorArgb(null)
            return@LaunchedEffect
        }
        // Update the cached color so the bottom bar picks it up on the next
        // frame — no waiting for the DataStore round-trip below. Key on
        // targetAccentColorRaw (the settled target) rather than the per-frame
        // animateColorAsState output, so this effect fires once per real focus
        // or palette change instead of ~7 times during the 120ms color tween.
        cachedFocusColor?.value = targetAccentColorRaw
        kotlinx.coroutines.delay(300)
        if (focusedVaultId != null && autoFocusColorDetection) {
            settingsViewModel.setDetectedAccent(displayFocusColor, targetAccentColorRaw.toArgb())
        }
    }
    
    // Observe current state to detect when returning to library
    val currentState by mainViewModel.currentState.collectAsState()
    
    // Observe vault to focus when returning to Library
    val vaultToFocus by mainViewModel.vaultToFocusInLibrary.collectAsState()
    // True once startup detection (sync latest played) has completed; wait before showing content so focus is correct from first frame
    val startupFocusComplete by mainViewModel.startupFocusComplete.collectAsState(initial = false)
    // Auto-refresh: when file watcher detected a change, run the same refresh as the Refresh button
    val triggerRefreshWhenOnLibrary by mainViewModel.triggerRefreshWhenOnLibrary.collectAsState()
    // Lightweight open-last-played path: vault id to focus and showcase without detection.
    val vaultToShowcase by mainViewModel.vaultToShowcase.collectAsState()
    val backPressNavigating by mainViewModel.backPressNavigating.collectAsState()
    val detectionCancelled by libraryViewModel.detectionCancelled.collectAsState()

    // Shared fade overlay for detection transitions (fades to black, applies focus, fades back)
    val fadeOverlayAlpha = remember { Animatable(0f) }
    // When true, detection effect owns the transition
    var detectionTransitionInProgress by remember { mutableStateOf(false) }

    // After refresh detection finishes, surface the focused vault as a fullscreen
    // showcase: cover art on a blurred backdrop, confirm/tap launches the game,
    // any other input dismisses back to the library. Triggered only by detection
    // completion (no time-based trigger, no idle-mode coupling).
    var showVaultShowcase by remember { mutableStateOf(false) }
    // Set true by the showcase once its artwork is resolved, so the detection flow can
    // hold the black cover until the full-size view is painted (no theme-colour flash).
    var showcaseContentReady by remember { mutableStateOf(false) }

    // vaultStorageFolderUri observation moved to LibraryViewModel.init to avoid
    // spurious discovery on Crossfade recomposition (collectAsState initial=null
    // caused a transient "default" key that bypassed the discoveredForUri guard).
    
    // Report library-focused vault to MainViewModel (for auto-refresh: same vault = no navigate)
    LaunchedEffect(currentState, focusedVaultId) {
        if (currentState is AppState.Library) {
            mainViewModel.setLibraryFocusedVaultId(focusedVaultId)
        } else {
            mainViewModel.setLibraryFocusedVaultId(null)
        }
    }

    // When box art was assigned from browser (or elsewhere), refresh library and invalidate artwork for that vault
    LaunchedEffect(Unit) {
        mainViewModel.pendingArtworkAssignedVaultId.collect { vaultId ->
            if (vaultId != null) {
                libraryViewModel.notifyArtworkAssignedForVault(vaultId)
                mainViewModel.getAndClearArtworkAssignedVaultId()
            }
        }
    }

    var showHomeFolderRequiredDialog by remember { mutableStateOf(false) }
    var pendingActionAfterFolderPick by remember { mutableStateOf<String?>(null) }
    // Somewhere else in the app hit an action that needs the storage folder and sent the user
    // here for it. Same dialog, same picker. The flag is cleared when the dialog is answered,
    // not when it opens, so re-collecting the flow while the dialog is still up does not
    // re-trigger it.
    val homeFolderSetupRequested by mainViewModel.homeFolderSetupRequest.collectAsState()
    LaunchedEffect(homeFolderSetupRequested) {
        if (homeFolderSetupRequested) {
            pendingActionAfterFolderPick = "add"
            showHomeFolderRequiredDialog = true
        }
    }
    // tooNewVersion is non-null when the newest snapshot in the folder was
    // written by a newer app build than this one can read; the load is then
    // refused and the too-new dialog is shown instead of switching storage.
    data class PendingLibraryLoad(
        val folderUri: Uri,
        val snapshotCount: Int,
        val tooNewVersion: Int? = null,
    )
    var pendingLibraryLoad by remember { mutableStateOf<PendingLibraryLoad?>(null) }
    // Shown once an empty-folder pick is adopted as the storage folder. needsRestart is
    // true when files were moved in from the old folder, so the UI re-reads them from
    // the new one; otherwise the pending Add Game / Refresh action carries on in place.
    data class StorageFolderSet(val needsRestart: Boolean, val pendingAction: String?)
    var storageFolderSet by remember { mutableStateOf<StorageFolderSet?>(null) }
    var libScreenStorageMigrating by remember { mutableStateOf(false) }
    var libScreenStorageMigrationProgress by remember {
        mutableStateOf<com.twinlore.app.data.library.StorageFolderMigrationCoordinator.Progress?>(null)
    }

    // Detection: fade to black first, then run refresh (so focus is applied while black), then fade back. Use version counter as key so clearing trigger doesn't cancel this effect.
    // Skip auto-refresh when the user explicitly navigated back (back-press); the focused vault is already correct.
    var detectionTransitionVersion by remember { mutableIntStateOf(0) }
    val triggerLibraryRefresh: () -> Unit = remember(homeFolderUri) {
        {
            if (homeFolderUri == null) {
                pendingActionAfterFolderPick = "refresh"
                showHomeFolderRequiredDialog = true
            } else {
                detectionTransitionVersion++
            }
        }
    }
    var launcherFocusedAppPackage by remember { mutableStateOf<String?>(null) }
    var launcherContextMenuRequestedTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentState, triggerRefreshWhenOnLibrary, backPressNavigating) {
        if (currentState is AppState.Library && backPressNavigating) {
            mainViewModel.clearTriggerRefreshWhenOnLibrary()
            mainViewModel.clearBackPressNavigating()
            return@LaunchedEffect
        }
        if (currentState is AppState.Library && triggerRefreshWhenOnLibrary) {
            detectionTransitionVersion++
        }
    }
    LaunchedEffect(detectionTransitionVersion) {
        if (detectionTransitionVersion == 0) return@LaunchedEffect
        detectionTransitionInProgress = true
        mainViewModel.clearTriggerRefreshWhenOnLibrary()
        fadeOverlayAlpha.animateTo(1f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
        val detectionRunId = libraryViewModel.getNextDetectionRunId()
        kotlinx.coroutines.coroutineScope {
            val deferred = async {
                libraryViewModel.detectionFocusApplied.first { it >= detectionRunId }
            }
            libraryViewModel.scrapeAllBoxArt(detectionRunId = detectionRunId)
            kotlinx.coroutines.withTimeoutOrNull(15000) { deferred.await() }
        }
        // If user pressed Skip, go straight back (no transition)
        if (detectionCancelled) {
            // This early return skips the clearJustFocusedByDetection() call further
            // down, so clear it here too — otherwise the flag is left sticky-true and
            // a later re-arm (same value, no edge) has nothing to signal off of.
            libraryViewModel.clearJustFocusedByDetection()
            fadeOverlayAlpha.animateTo(0f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
            detectionTransitionInProgress = false
            return@LaunchedEffect
        }
        // Small delay so focus is visible before fading back
        kotlinx.coroutines.delay(80)
        // Reveal the showcase under the fade so the user lands on the focused
        // vault full-screen instead of the regular library view. Dismiss in the
        // showcase falls back to the library, which is also rendered underneath.
        if (detectionShowcaseReveals(libraryViewModel.focusedVaultId.value, libraryViewModel.justFocusedByDetection.value)) {
            showcaseContentReady = false
            showVaultShowcase = true
            // Hold the black cover until the showcase artwork is resolved (or a short
            // timeout), so it fades back onto a painted view instead of the bare
            // background — same idea as the lib↔vault dissolve keeping the cover up
            // while the incoming screen composes.
            kotlinx.coroutines.withTimeoutOrNull(700) {
                snapshotFlow { showcaseContentReady }.first { it }
            }
        }
        // LED update is handled by the main LED LaunchedEffect when focusedVaultId/displayAccentColorRaw settle
        fadeOverlayAlpha.animateTo(0f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(100)
        libraryViewModel.clearJustFocusedByDetection()
        detectionTransitionInProgress = false
    }

    // Lightweight open-last-played path: focus the recorded vault and raise the
    // showcase without a detection pass. The justFocusedByDetection observer
    // below does the raising.
    LaunchedEffect(vaultToShowcase, currentState) {
        val target = vaultToShowcase ?: return@LaunchedEffect
        if (currentState !is AppState.Library) return@LaunchedEffect
        // Cold start can reach this effect before the vault list's first Room
        // emission; without waiting, focusVaultForShowcase would see an empty
        // list and wrongly judge a valid record stale.
        libraryViewModel.vaultsInitialLoadComplete.first { it }
        // Clear only once focus is about to be applied: if this coroutine were
        // cancelled between clearing and focusing (currentState changing while the
        // await above was pending), clearing first would drop the signal on the
        // floor with no showcase ever raised.
        mainViewModel.clearVaultToShowcase()
        libraryViewModel.focusVaultForShowcase(target)
    }

    // Show showcase when detection via handleFolderSelection focuses a vault
    // (bypasses the detectionTransitionVersion flow, so needs its own observer).
    // Keyed on the monotonic version rather than the boolean itself: StateFlow is
    // conflated, so a plain true-to-true re-arm (flag left sticky from a run that
    // exited early, e.g. the detectionCancelled branch above) would not restart an
    // effect keyed on the boolean.
    LaunchedEffect(justFocusedByDetectionVersion) {
        if (justFocusedByDetection && !detectionTransitionInProgress && libraryViewModel.focusedVaultId.value != null) {
            showVaultShowcase = true
        }
    }

    // Vault discovery is triggered by LibraryViewModel.init observing the storage
    // folder URI flow — no LaunchedEffect needed here.
    
    // Track loading state - start as true to prevent empty state flash on app startup
    var isLoading by remember { mutableStateOf(true) }
    val hasSeenDatabaseLoadFromVm by libraryViewModel.hasSeenDatabaseLoad.collectAsState(initial = false)
    var startupFocusWaitOver by remember { mutableStateOf(false) }
    LaunchedEffect(startupFocusComplete, vaultToFocus) {
        if (startupFocusComplete && vaultToFocus != null && !startupFocusWaitOver) {
            kotlinx.coroutines.delay(3000)
            startupFocusWaitOver = true
        }
    }
    var showCreateVaultDialog by remember { mutableStateOf(false) }

    val homeFolderPickScope = rememberCoroutineScope()
    val homeFolderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            pendingActionAfterFolderPick = null
            return@rememberLauncherForActivityResult
        }
        // Take the persistable URI grant immediately so it survives the
        // import+restart path below; otherwise the saved URI loses its
        // read/write grant after process restart and the folder becomes
        // inaccessible. (pickHomeFolder also takes the grant via the setup
        // coordinator on the else branch, but the load branch bypasses that.)
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.e("LibraryScreen", "Could not take persistable URI permission", e)
            android.widget.Toast.makeText(
                context,
                "Could not access folder: ${e.message ?: "permission denied"}",
                android.widget.Toast.LENGTH_LONG
            ).show()
            pendingActionAfterFolderPick = null
            return@rememberLauncherForActivityResult
        }
        homeFolderPickScope.launch {
            // If the picked folder already has a library snapshot (bare or per-device),
            // surface the load-instead-of-overwrite dialog. Otherwise proceed with the
            // original pending action (Add Game / Refresh) as before.
            val snapshotCount = settingsViewModel.findLibrarySnapshot(uri)
            if (snapshotCount > 0) {
                val tooNewVersion = settingsViewModel.newestSnapshotSchemaVersion(uri)
                    ?.takeIf { it > com.twinlore.app.data.local.SCHEMA_VERSION }
                pendingLibraryLoad = PendingLibraryLoad(
                    folderUri = uri,
                    snapshotCount = snapshotCount,
                    tooNewVersion = tooNewVersion,
                )
            } else {
                val oldUri = settingsViewModel.homeFolderUri.first()?.let(Uri::parse)
                val migratable = if (oldUri != null) settingsViewModel.countMigratableFiles() else 0
                val movesFiles = oldUri != null && migratable > 0
                if (oldUri != null && movesFiles) {
                    libScreenStorageMigrating = true
                    libScreenStorageMigrationProgress = null
                    try {
                        settingsViewModel.migrateStorageFolder(oldUri, uri) { p ->
                            libScreenStorageMigrationProgress = p
                        }
                    } catch (e: Exception) {
                        Log.e("LibraryScreen", "Storage migration failed", e)
                        android.widget.Toast.makeText(
                            context,
                            "Migration failed: ${e.message ?: "unknown error"}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        libScreenStorageMigrating = false
                    }
                } else {
                    settingsViewModel.switchStorageWithoutMigration(uri)
                }
                runCatching { settingsViewModel.exportLibraryNow() }
                // Confirm the folder before going on. When files were moved in, the
                // dialog restarts the app so the UI re-reads them from the new folder,
                // matching the load-existing-library path (the pending action is
                // dropped; the user re-triggers it after the restart). With nothing
                // moved there is nothing to re-read, so the pending action continues.
                storageFolderSet = StorageFolderSet(
                    needsRestart = movesFiles,
                    pendingAction = pendingActionAfterFolderPick,
                )
                pendingActionAfterFolderPick = null
            }
        }
    }

    var showSearchModal by remember { mutableStateOf(false) }
    var selectedVaultForContextMenu by remember { mutableStateOf<Vault?>(null) }

    // Method chosen in the choose-method dialog for the pick in flight. Saved only
    // once the picked folder checks out; null for re-picks of the saved method.
    var pickerSetupSource by remember { mutableStateOf<com.twinlore.app.data.LastPlayedSource?>(null) }

    // Folder picker launcher for RetroArch history folder
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { folderUri: Uri? ->
        val chosenSource = pickerSetupSource
        pickerSetupSource = null
        if (folderUri != null) {
            try {
                // Take persistable URI permission
                context.contentResolver.takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                // Handle folder selection in ViewModel
                libraryViewModel.handleFolderSelection(folderUri, chosenSource)
            } catch (e: Exception) {
                Log.e("LibraryScreen", "Error handling folder selection", e)
                libraryViewModel.cancelFolderSelection()
            }
        } else {
            libraryViewModel.cancelFolderSelection()
        }
    }
    
    // Show explanation dialog before folder picker when needed
    var showFolderExplanationDialog by remember { mutableStateOf(false) }
    var hasShownPicker by remember { mutableStateOf(false) }
    val needsSourceSetup by libraryViewModel.needsSourceSetup.collectAsState()
    val sourceSetupFailure by libraryViewModel.sourceSetupFailure.collectAsState()

    // A first-setup pick that held none of the launcher's files saved nothing:
    // back to the choose-method dialog, which shows the failure so the user can retry.
    LaunchedEffect(sourceSetupFailure) {
        if (sourceSetupFailure != null) showFolderExplanationDialog = true
    }
    LaunchedEffect(showFolderExplanationDialog) {
        if (!showFolderExplanationDialog) libraryViewModel.clearSourceSetupFailure()
    }

    // When needsSourceSetup becomes true, show the setup dialog
    LaunchedEffect(needsSourceSetup) {
        if (needsSourceSetup && !showFolderExplanationDialog) {
            showFolderExplanationDialog = true
            libraryViewModel.clearNeedsSourceSetup()
        }
    }

    // Toast emissions from per-vault local-media re-scan (long-press menu).
    LaunchedEffect(Unit) {
        libraryViewModel.localBindToast.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // When needsFolderSelection becomes true, show explanation dialog first
    LaunchedEffect(needsFolderSelection) {
        if (needsFolderSelection && !hasShownPicker && !showFolderExplanationDialog) {
            showFolderExplanationDialog = true
        } else if (!needsFolderSelection) {
            // Reset flags when needsFolderSelection becomes false. Leave the dialog up
            // when it was reopened to report a failed setup pick.
            hasShownPicker = false
            if (sourceSetupFailure == null) showFolderExplanationDialog = false
        }
    }
    
    // Launch folder picker after user confirms in explanation dialog

    // Remember we've triggered a refresh for the current vaultToFocus (avoid refresh loop).
    // Reset when vaultToFocus changes so each new focus request gets one refresh attempt.
    var hasRefreshedForVaultToFocus by remember { mutableStateOf(false) }
    LaunchedEffect(vaultToFocus) {
        hasRefreshedForVaultToFocus = false
    }

    // Break idle mode when returning to library screen and focus on vault if specified.
    // Only apply focus when the vault is in filteredVaults (e.g. after auto-refresh creates a new vault,
    // Room must re-emit and the new vault must pass the filter; until then we keep vaultToFocus and retry).
    LaunchedEffect(currentState, vaultToFocus, filteredVaults.size) {
        if (currentState is AppState.Library && !detectionTransitionInProgress) {
            // Reset fade overlay (may be at 1f after idle-to-vault fade transition)
            fadeOverlayAlpha.snapTo(0f)
            libraryViewModel.onInputReceived()
            if (vaultToFocus != null) {
                val vaultInList = filteredVaults.any { it.id == vaultToFocus }
                    if (vaultInList) {
                    libraryViewModel.setFocusedVault(vaultToFocus)
                    mainViewModel.clearVaultToFocusInLibrary()
                    hasRefreshedForVaultToFocus = false
                } else {
                    // New vault may not be in list yet (e.g. auto-refresh just created it). Force re-query once so list updates.
                    if (!hasRefreshedForVaultToFocus) {
                        hasRefreshedForVaultToFocus = true
                        libraryViewModel.refreshVaultList()
                    }
                }
            } else {
                hasRefreshedForVaultToFocus = false
            }
        } else {
            hasRefreshedForVaultToFocus = false
        }
    }
    
    // Track when vaults have been loaded from database to prevent empty state flash.
    // Only delay 500ms on first app load (hasSeenDatabaseLoadFromVm persists in ViewModel across vault ↔ library).
    LaunchedEffect(Unit) {
        if (!hasSeenDatabaseLoadFromVm) delay(500)
        libraryViewModel.setHasSeenDatabaseLoad(true)
        isLoading = false
    }
    
    // Also stop loading immediately if vaults becomes non-empty (database loaded successfully)
    LaunchedEffect(vaults) {
        if (vaults.isNotEmpty()) {
            libraryViewModel.setHasSeenDatabaseLoad(true)
            isLoading = false
        }
        // Initialize focus when vaults are loaded (skip if vaultToFocus is set — startup detection will apply it)
        if (vaultToFocus == null) libraryViewModel.initializeFocus(vaults)
    }

    // Preload first batch of grid artwork images so they're in Coil's memory cache
    // before the grid becomes visible. Also writes the per-vault thumbnail cache
    // when missing, so subsequent cold starts skip the slow source decode.
    val imagesPreloaded by libraryViewModel.imagesPreloaded.collectAsState()
    val imageLoader = LocalImageLoader.current
    val thumbnailCache = remember(context) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                context.applicationContext,
                com.twinlore.app.di.ArtworkThumbnailCacheEntryPoint::class.java
            )
            .artworkThumbnailCache()
    }
    // Must match LibraryGrid's computation or this preload writes to a Coil slot the
    // runtime never reads. Both go through gridDecodeSize and both snap to the same
    // 32-px bucket, which is what lets this window-derived width and the grid's
    // container-measured width agree — a tolerance, not a guarantee; see the BUCKET_PX
    // doc. reportPreloadDecode/checkPreloadDecodeAgreement log it if they ever diverge,
    // because the only symptom would otherwise be a silently slower cold start.
    val preloadDensity = LocalDensity.current
    val preloadConfiguration = LocalConfiguration.current
    val preloadDecode = remember(preloadDensity, preloadConfiguration, libraryViewMode, gridAspectRatio) {
        with(preloadDensity) {
            gridDecodeSize(
                containerWidthPx = preloadConfiguration.screenWidthDp.dp.roundToPx(),
                // CAROUSEL and LAUNCHER have no grid columns and use their own cache
                // keys; default to the 3-column grid so switching into it lands warm.
                cols = libraryViewMode.gridColumns ?: 3,
                aspectRatio = gridAspectRatio,
                spacingPx = GRID_SPACING_DP.dp.roundToPx(),
            )
        }
    }
    LaunchedEffect(preloadDecode) { reportPreloadDecode(preloadDecode) }
    // How many tiles gate the loading splash. The grid is a LazyVerticalGrid, so
    // tiles below the fold are never composed and never request their artwork
    // until scrolled to — gating on all of them made the user wait for pixels
    // they could not see. Everything past this count still preloads, just behind
    // the visible library instead of in front of it.
    //
    // ONLY the multi-column grid modes get a viewport-derived gate, because only
    // there does preloadDecode.height track the on-screen tile height (both come
    // out of gridDecodeSize as width/aspectRatio):
    //
    //  - LIST (cols == 1) returns a fixed 960x1280 decode budget that has nothing
    //    to do with how tall a list row draws. Dividing screen height by 1280
    //    yields 1 row, gating ~2 items while five or six rows are on screen —
    //    guaranteed pop-in. Not a viewport estimate, so don't pretend it is.
    //  - CAROUSEL / LAUNCHER have no grid at all (gridColumns == null) and render
    //    through their own cache keys, so a grid-geometry gate would be measuring
    //    something the user cannot see.
    //
    // Those three keep the old behaviour — gate on the whole batch — which is
    // correct-but-slower rather than fast-but-wrong.
    val preloadGateCount = remember(preloadDensity, preloadConfiguration, libraryViewMode, preloadDecode) {
        val cols = libraryViewMode.gridColumns
        if (cols == null || cols < 2) {
            Int.MAX_VALUE
        } else {
            val screenHeightPx = with(preloadDensity) { preloadConfiguration.screenHeightDp.dp.roundToPx() }
            val tileHeightPx = preloadDecode.height.coerceAtLeast(1)
            val rowsOnScreen = ((screenHeightPx + tileHeightPx - 1) / tileHeightPx).coerceAtLeast(1)
            // +1 buffer row, then a floor. The buffer absorbs the documented
            // tolerance between this window-derived decode size and the grid's
            // own container-measured one (they only agree to a BUCKET_PX bucket),
            // which is the one way rowsOnScreen can come out short. On the 3-column
            // default this already gates ~9 tiles against ~5 visible, so it is
            // conservative before the floor even applies. Taller screens raise
            // rowsOnScreen on their own — the estimate is measured, not assumed,
            // so it scales without a magic multiplier.
            (cols * (rowsOnScreen + 1)).coerceAtLeast(MIN_PRELOAD_GATE)
        }
    }
    // Give the WHOLE library thumbnail coverage deterministically, on a scope a
    // recomposition cannot cancel, rather than leaving the tail to whichever tiles the user
    // happens to scroll past. Measured as CPU-neutral at startup; see warmArtworkThumbnails
    // for what it does and does not buy.
    LaunchedEffect(vaults, isLoading) {
        if (isLoading) return@LaunchedEffect
        libraryViewModel.warmArtworkThumbnails(vaults)
    }
    LaunchedEffect(vaults, isLoading) {
        if (isLoading || imagesPreloaded) return@LaunchedEffect
        val vaultsWithArt = vaults.filter { it.hasArtwork }
        if (vaultsWithArt.isEmpty()) {
            libraryViewModel.setImagesPreloaded(true)
            return@LaunchedEffect
        }
        val preloadCount = vaultsWithArt.take(18)
        // Split into what the user is about to look at and everything else.
        // The library sits behind a loading splash until `imagesPreloaded` flips,
        // so every tile in the gating batch is latency the user waits through.
        //
        // Cold-start CPU accounting (Thor, 14 vaults, release): this loop was 84
        // of the ~236 ticks a cold start spends — 36% of startup CPU — and all 14
        // decodes gated the splash even though only the first ~5 fit on screen.
        // It also sat in a completely uninstrumented window between "first vaults
        // emit" and the deferred network checks, which is why two thirds of
        // startup CPU had no marker to attribute it to. thumbsGenerated separates
        // "cost of generating thumbs" (first run after a cache wipe) from "cost of
        // decoding already-cached thumbs" (every run after) — very different
        // numbers that were previously indistinguishable.
        val gatingBatch = preloadCount.take(preloadGateCount)
        val tailBatch = preloadCount.drop(preloadGateCount)
        var thumbsGenerated = 0
        // One tile's worth of preload. Suspending; the caller picks the dispatcher.
        suspend fun preloadOne(vault: Vault) {
                try {
                    val resourceId = vault.artworkResourceId ?: return
                    val resolvedUri = libraryResourceResolver.resolve(resourceId) ?: run {
                        android.util.Log.w(PRELOAD_TAG, "resolve null vault=${vault.id} rid=$resourceId")
                        return
                    }
                    // Generate the thumb on first encounter so subsequent cold
                    // starts skip the full-source decode. Falls back to the
                    // original Uri only if generate also fails — Coil then
                    // decodes the full source for this one render.
                    val thumbFile = thumbnailCache.pathFor(vault.id)
                        ?: thumbnailCache.generate(vault.id, resolvedUri).also {
                            thumbsGenerated++
                            if (it == null) android.util.Log.w(PRELOAD_TAG, "thumb generate failed vault=${vault.id}")
                        }
                    val data: Any = thumbFile
                        ?: ImagePathUtils.toImageData(resolvedUri.toString(), vault.id)
                        ?: return
                    // The `_<w>x<h>_0` suffix MUST match VaultGridItem's runtime key format
                    // (`grid_art_<vault>_<rid>_<w>x<h>_<busterTs>`) or this preload writes to
                    // a slot the runtime never reads — cold-start preload becomes dead weight.
                    // Both sides derive it from gridDecodeSize for exactly that reason.
                    val req = ImageRequest.Builder(context)
                        .data(data)
                        .memoryCacheKey(
                            "grid_art_${vault.id}_${resourceId}_${preloadDecode.width}x${preloadDecode.height}_0"
                        )
                        .size(coil.size.Size(preloadDecode.width, preloadDecode.height))
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .allowHardware(true)
                        .build()
                    imageLoader.execute(req)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // MUST rethrow. This effect is keyed on (vaults, isLoading) and really does
                    // get cancelled and restarted during startup; swallowing the cancellation
                    // here (CancellationException is an Exception) made the dead coroutine walk
                    // the whole batch doing nothing, logging one "preload failed ... The
                    // coroutine scope left the composition" per vault. 18 per launch on a
                    // 39-vault library.
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(PRELOAD_TAG, "preload failed vault=${vault.id}: ${e.message}")
                }
        }

        PerfLog.mark("grid preload gate begin (n=${gatingBatch.size} of ${preloadCount.size})")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            gatingBatch.forEach { preloadOne(it) }
        }
        PerfLog.mark("grid preload gate end (thumbsGenerated=$thumbsGenerated)")
        // Open the splash as soon as the visible tiles are warm. Everything below
        // the fold keeps warming behind the now-visible library, so scrolling down
        // still finds a populated Coil cache instead of decoding on the fly.
        libraryViewModel.setImagesPreloaded(true)

        if (tailBatch.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                tailBatch.forEach { preloadOne(it) }
            }
            PerfLog.mark("grid preload tail end (n=${tailBatch.size})")
        }
    }

    // Library-arrival cue: fire when the library CONTENT is actually on
    // screen, not while the loading splash is still up. The preload splash overlay
    // is gated on `!imagesPreloaded` and the content-vs-spinner branch on
    // `startupFocusComplete && !isLoading`, so all three together mean "items are
    // visible".
    val isLibraryContentVisible = imagesPreloaded && !isLoading && startupFocusComplete
    LaunchedEffect(isLibraryContentVisible) {
        // Marks the boundary between "preload finished" and the grid's first real
        // render. The window that opens here was the other big startup cost —
        // ~82 ticks, 35% of cold-start CPU, burning for ~1.4 s after this turns
        // true. Attributed with scripts/perfprobe-threads.sh; worth recording
        // because all three obvious explanations are wrong:
        //
        //  - Not rendering. RenderThread takes 0.01-0.02 s of it.
        //  - Not an animation. 19-20 frames land in 2 s (~10 fps); a running
        //    animation would produce ~120. Nothing here drives per-vsync work.
        //  - Not re-decoding artwork. LibraryArtworkSizing's agreement check
        //    stays silent, so the grid reads the slots the preload wrote.
        //
        // It is main-thread composition (~0.25 s, part of it Compose semantics
        // diffing forced by whatever a11y services the device runs) plus the
        // Dispatchers.Default library-hygiene block in MainViewModel. Once that
        // drains, the app reaches a true idle: 0.00 s of CPU and 0 frames over
        // repeated 5 s windows, so there is no leaked InfiniteTransition here.
        PerfLog.mark("libraryContentVisible=$isLibraryContentVisible (preloaded=$imagesPreloaded loading=$isLoading focus=$startupFocusComplete)")
        if (isLibraryContentVisible) {
            // Arrival cue: the focused item's normal tick, not a special chime.
            FeedbackManager.playSound(context, FeedbackEvent.FOCUS, FeedbackManager.soundEnabled)
        }
    }

    // Animate the preload overlay fade-out, but never before the splash has had its minimum
    // time on screen (see PRELOAD_SPLASH_MIN_SHOW_MS) — once per process: a return from
    // Settings or a vault starts with the hold already consumed, so no splash replays.
    var splashHoldElapsed by remember { mutableStateOf(splashHoldConsumed) }
    LaunchedEffect(Unit) {
        if (!splashHoldElapsed) {
            splashHoldConsumed = true
            kotlinx.coroutines.delay(PRELOAD_SPLASH_MIN_SHOW_MS)
            splashHoldElapsed = true
        }
    }
    val preloadOverlayAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (imagesPreloaded && splashHoldElapsed) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "preloadOverlay"
    )

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // FocusRequester for first menu button (for D-pad UP from vault)
        val topMenuFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

        // FocusRequester for the add (+) button in the top menu bar
        val addButtonFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

        // FocusRequester for the currently focused vault (for D-pad DOWN from top menu)
        val focusedVaultFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        var requestFocusTrigger by remember { mutableIntStateOf(0) }
        
        // FocusRequester for the main content Box (to receive D-pad events)
        val contentBoxFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }


        // On first load (and whenever we have vaults), give content Box focus so D-pad
        // left/right work immediately without requiring 2 initial presses (which would otherwise be
        // consumed by focus traversal when the vault item had focus).
        // Launcher and carousel modes handle their own focus via item-level callbacks.
        LaunchedEffect(focusedVaultId, filteredVaults.size, libraryViewMode) {
            if (libraryViewMode == LibraryViewMode.LAUNCHER ||
                libraryViewMode == LibraryViewMode.CAROUSEL) return@LaunchedEffect
            if (focusedVaultId != null && filteredVaults.isNotEmpty()) {
                kotlinx.coroutines.delay(80)
                try {
                    contentBoxFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus errors - composable might not be ready yet
                }
            }
        }
        
        // When user presses D-pad DOWN from top menu (requestFocusTrigger > 0), move focus to vault item.
        // Grid, launcher, and carousel modes handle this internally via their own mechanisms.
        LaunchedEffect(focusedVaultId, requestFocusTrigger, libraryViewMode) {
            if (libraryViewMode.isGrid ||
                libraryViewMode == LibraryViewMode.LAUNCHER ||
                libraryViewMode == LibraryViewMode.CAROUSEL) return@LaunchedEffect
            if (requestFocusTrigger > 0 && focusedVaultId != null && filteredVaults.isNotEmpty()) {
                kotlinx.coroutines.delay(16)
                try {
                    focusedVaultFocusRequester.requestFocus()
                } catch (_: Exception) {
                    kotlinx.coroutines.delay(80)
                    try { focusedVaultFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            }
        }

        val effectiveOverlayAlpha = fadeOverlayAlpha.value

        // Get focused vault for blurred background in idle mode
        val focusedVault = remember(focusedVaultId, filteredVaults) {
            focusedVaultId?.let { id ->
                filteredVaults.firstOrNull { it.id == id }
            }
        }
        // Debounced focus id for the EXPENSIVE blurred wallpaper only. Scroll-driven focus can
        // change every frame; the two-layer blur crossfade would thrash reloading a new
        // wallpaper per item. Settle it ~160ms after focus stops moving so the backdrop swaps
        // once per landed item. The accent border / neon glow / hint bar stay on the instant
        // `focusedVault`; the LED has its own 100ms debounce. See [[blur pipeline]].
        var blurFocusedVaultId by remember { mutableStateOf(focusedVaultId) }
        LaunchedEffect(focusedVaultId) {
            if (blurFocusedVaultId != focusedVaultId) {
                kotlinx.coroutines.delay(160)
                blurFocusedVaultId = focusedVaultId
            }
        }
        val blurFocusedVault = remember(blurFocusedVaultId, filteredVaults) {
            blurFocusedVaultId?.let { id -> filteredVaults.firstOrNull { it.id == id } }
        }
        // Preload blurred background size (270x480) for focused + neighbors so background updates instantly on focus change.
        // Carousel preload uses 768x1024; blur uses 270x480 — both must be warm for snappy switching.
        // BLURRED_ART only: this prefetch resolves + warms three artwork paths (prev/cur/next)
        // per keypress purely to feed LibraryBlurBackground's cross-dissolve. SOLID and
        // WALLPAPER draw no per-game wallpaper at all, so there is nothing here to warm --
        // skipping it is the largest single saving of round two, task 2.
        val blurImageLoader = LocalImageLoader.current
        LaunchedEffect(focusedVaultId, sortedVaults, libraryBackgroundStyle) {
            if (libraryBackgroundStyle != LibraryBackgroundStyle.BLURRED_ART) return@LaunchedEffect
            if (sortedVaults.isNotEmpty()) {
                val idx = sortedVaults.indexOfFirst { it.id == focusedVaultId }.takeIf { it >= 0 } ?: 0
                val prev = if (idx == 0) sortedVaults.size - 1 else idx - 1
                val next = if (idx == sortedVaults.size - 1) 0 else idx + 1
                listOf(prev, idx, next).distinct().forEach { i ->
                    val v = sortedVaults.getOrNull(i) ?: return@forEach
                    val rid = v.artworkResourceId
                    if (v.hasArtwork && rid != null) {
                        try {
                            val data = withContext(Dispatchers.IO) {
                                val uri = libraryResourceResolver.resolve(rid) ?: return@withContext null
                                ImagePathUtils.toImageData(uri.toString(), v.id)
                            }
                            if (data != null) {
                                blurImageLoader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(data)
                                        .size(coil.size.Size(270, 480))
                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .crossfade(false)
                                        .build()
                                )
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
        
        // Controller: Start/Menu button in Library opens context menu for focused vault (same as long-press).
        // Toggle: if context menu is already open, close it; otherwise open for currently focused vault.
        // Keys ensure we use current focusedVaultId/filteredVaults when the event is received (not stale from first composition).
        LaunchedEffect(focusedVaultId, filteredVaults.size, libraryViewMode, launcherFocusedAppPackage) {
            mainViewModel.requestOpenLibraryContextMenu.collect {
                // Launcher mode + a focused app cell: bump the trigger so the cell
                // opens its own DropdownMenu (Pin/Uninstall).
                if (libraryViewMode == LibraryViewMode.LAUNCHER && launcherFocusedAppPackage != null) {
                    launcherContextMenuRequestedTrigger++
                    return@collect
                }
                // Otherwise: existing vault context-menu behavior.
                if (selectedVaultForContextMenu != null) {
                    selectedVaultForContextMenu = null
                } else {
                    val vault = focusedVaultId?.let { id ->
                        filteredVaults.firstOrNull { it.id == id }
                    } ?: sortedVaults.firstOrNull()
                    vault?.let { selectedVaultForContextMenu = it }
                }
            }
        }

        // Controller: L1/R1 cycles library view mode
        LaunchedEffect(Unit) {
            mainViewModel.requestCycleLibraryViewMode.collect { forward ->
                val nextMode = if (forward) when (libraryViewMode) {
                    LibraryViewMode.CAROUSEL -> LibraryViewMode.LIST
                    LibraryViewMode.LIST -> LibraryViewMode.GRID_EXTRA_LARGE
                    LibraryViewMode.GRID_EXTRA_LARGE -> LibraryViewMode.GRID_MEDIUM
                    LibraryViewMode.GRID_MEDIUM -> LibraryViewMode.GRID_SMALL
                    LibraryViewMode.GRID_SMALL -> LibraryViewMode.LAUNCHER
                    LibraryViewMode.LAUNCHER -> LibraryViewMode.CAROUSEL
                } else when (libraryViewMode) {
                    LibraryViewMode.CAROUSEL -> LibraryViewMode.LAUNCHER
                    LibraryViewMode.LIST -> LibraryViewMode.CAROUSEL
                    LibraryViewMode.GRID_EXTRA_LARGE -> LibraryViewMode.LIST
                    LibraryViewMode.GRID_MEDIUM -> LibraryViewMode.GRID_EXTRA_LARGE
                    LibraryViewMode.GRID_SMALL -> LibraryViewMode.GRID_MEDIUM
                    LibraryViewMode.LAUNCHER -> LibraryViewMode.GRID_SMALL
                }
                settingsViewModel.setLibraryViewMode(nextMode)
            }
        }

        // Controller: R1 cycles the library sort field, applying that field's default direction.
        LaunchedEffect(Unit) {
            mainViewModel.requestCycleLibrarySort.collect { forward ->
                val nextField = if (forward) librarySortField.next() else librarySortField.previous()
                settingsViewModel.setLibrarySortField(nextField)
                settingsViewModel.setLibrarySortDirection(nextField.defaultDirection)
            }
        }

        // Controller: X button triggers full detection/refresh (same as refresh button in top bar)
        LaunchedEffect(Unit) {
            mainViewModel.requestRefreshLibrary.collect {
                triggerLibraryRefresh()
            }
        }

        // Controller: X button launches the focused vault's game (or opens the vault if no launch data).
        LaunchedEffect(focusedVaultId, filteredVaults.size, libraryViewMode, launcherFocusedAppPackage) {
            mainViewModel.requestLaunchFocusedVault.collect {
                // Launcher mode + a focused app cell: launch the app directly.
                if (libraryViewMode == LibraryViewMode.LAUNCHER && launcherFocusedAppPackage != null) {
                    launcherViewModel.launchApp(launcherFocusedAppPackage!!)
                    return@collect
                }
                // Open the focused vault (game launching disabled for now)
                val vaultId = focusedVaultId ?: return@collect
                val vault = filteredVaults.firstOrNull { it.id == vaultId } ?: return@collect
                mainViewModel.openVault(vault.id)
            }
        }

        // Derive vibrant color for background glow from stored vault metadata (no extraction on focus).
        val displayVibrantColor: Color? = if (focusedVaultId != null && !powerSaverMode) {
            focusedVault?.artworkVibrantArgb?.let(::argbToComposeColor)
        } else null
        // Lazy extraction: when focused vault has artwork but no stored color, extract once and persist.
        val localContext = LocalContext.current
        val imageLoader = LocalImageLoader.current
        LaunchedEffect(focusedVaultId, focusedVault?.artworkResourceId, focusedVault?.artworkVibrantArgb, powerSaverMode) {
            if (powerSaverMode) return@LaunchedEffect
            val v = focusedVault ?: return@LaunchedEffect
            val rid = v.artworkResourceId
            if (v.artworkVibrantArgb != null || !v.hasArtwork || rid == null) return@LaunchedEffect
            val data = withContext(Dispatchers.IO) {
                val uri = libraryResourceResolver.resolve(rid) ?: return@withContext null
                ImagePathUtils.toImageData(uri.toString(), v.id)
            }
            val color = PaletteExtractor.extractVibrantColor(localContext, data, imageLoader)
            if (color != null) {
                val argb = ((color.alpha * 255).toInt() shl 24) or
                    ((color.red * 255).toInt() shl 16) or
                    ((color.green * 255).toInt() shl 8) or
                    (color.blue * 255).toInt()
                libraryViewModel.persistVibrantArgb(v.id, argb)
            }
        }

        // Transparent placeholder so we never show gallery-icon blur while new image loads.
        val transparentPlaceholder = remember {
            object : androidx.compose.ui.graphics.painter.Painter() {
                override val intrinsicSize get() = androidx.compose.ui.geometry.Size.Unspecified
                override fun DrawScope.onDraw() {}
            }
        }

        // Off-main blur-request resolve + two-layer cross-dissolve, owned by a helper to
        // keep this composable under the Dalvik register ceiling. Disabled outside
        // BLURRED_ART (round two, task 2): no per-game wallpaper drawn, nothing to resolve.
        val blurLayers = rememberLibraryBlurLayers(
            blurFocusedVault,
            blurArtworkInvalidateTimestamp,
            enabled = libraryBackgroundStyle == LibraryBackgroundStyle.BLURRED_ART
        )
        val displayedBlurReq = blurLayers.displayed
        val outgoingBlurReq = blurLayers.outgoing

        // Content Box (Carousel & Background) - Put this FIRST so it sits at the bottom layer
        Box(
            modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    // Universal L1/R1 handler: cycles view mode from ANY mode (carousel ↔ grid ↔ launcher).
                    // Uses onPreviewKeyEvent so it fires before any focused child's onKeyEvent, preventing
                    // L1/R1 from being swallowed by grid items or launcher app cells. L1 cycles the
                    // view mode, R1 cycles the sort field (mirrors the MainActivity bumper routing).
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_BUTTON_L1 -> {
                                    settingsViewModel.setLibraryViewMode(libraryViewMode.next())
                                    view.performHapticFeedback(hapticFeedbackIntensity)
                                    true
                                }
                                KeyEvent.KEYCODE_BUTTON_R1 -> {
                                    val nextField = librarySortField.next()
                                    settingsViewModel.setLibrarySortField(nextField)
                                    settingsViewModel.setLibrarySortDirection(nextField.defaultDirection)
                                    view.performHapticFeedback(hapticFeedbackIntensity)
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    // Keep parent key handling in carousel mode only.
                    // In grid mode, the parent can steal focus and consume D-pad UP prematurely,
                    // which causes unexpected jumps to the top menu.
                    .then(
                        if (libraryViewMode.isGrid ||
                            libraryViewMode == LibraryViewMode.LAUNCHER ||
                            libraryViewMode == LibraryViewMode.CAROUSEL) {
                            // Grid, launcher, and carousel modes handle their own focus + d-pad
                            // via item-level callbacks / their own onPreviewKeyEvent handler.
                            Modifier
                        } else {
                            Modifier
                                .focusRequester(contentBoxFocusRequester)
                                .focusable()
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        // Carousel mode key handling
                                        libraryViewModel.onInputReceived()

                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                FeedbackManager.playSound(context, FeedbackEvent.FOCUS, focusSoundEnabled)
                                                view.performHapticFeedback(hapticFeedbackIntensity)
                                                libraryViewModel.focusPreviousVault(sortedVaults)
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                FeedbackManager.playSound(context, FeedbackEvent.FOCUS, focusSoundEnabled)
                                                view.performHapticFeedback(hapticFeedbackIntensity)
                                                libraryViewModel.focusNextVault(sortedVaults)
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                // D-pad UP from vault should focus the top menu
                                                view.performHapticFeedback(hapticFeedbackIntensity)
                                                topMenuFocusRequester.requestFocus()
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                // D-pad DOWN from top menu should focus the currently focused vault
                                                if (focusedVaultId != null && filteredVaults.isNotEmpty()) {
                                                    view.performHapticFeedback(hapticFeedbackIntensity)
                                                    // Trigger focus request by incrementing the trigger counter
                                                    requestFocusTrigger++
                                                }
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_CENTER,
                                            KeyEvent.KEYCODE_BUTTON_A,
                                            KeyEvent.KEYCODE_ENTER -> {
                                                focusedVaultId?.let { vaultId ->
                                                    filteredVaults.firstOrNull { it.id == vaultId }?.let { vault ->
                                                        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                                        if (ledEnabled) {
                                                            ThorLedManager.setColor(
                                                                displayAccentColorRaw,
                                                                true,
                                                                ledBrightness,
                                                                ledSaturationBoost,
                                                                ledTurnOffTime.inactivityMs
                                                            )
                                                        }
                                                        mainViewModel.openVault(vault.id)
                                                        libraryViewModel.updateLastOpenedDeferred(vault.id)
                                                    }
                                                }
                                                true
                                            }
                                            KeyEvent.KEYCODE_BUTTON_X -> {
                                                // No action on X button in library
                                                false
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                        }
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                libraryViewModel.onInputReceived()
                            }
                        )
                    }
            ) {
                // Background neon glow (carousel/list/launcher; grid relies on per-item border).
                // Never over a SOLID background: that style is a deliberately flat backdrop, and
                // the accent wash re-tinting on every focus change read as a glow animation the
                // user had opted out of.
                if (libraryBackgroundStyle != LibraryBackgroundStyle.SOLID) {
                    LibraryNeonGlow(
                        vibrantColor = displayVibrantColor,
                        powerSaverMode = powerSaverMode,
                        isGridMode = libraryViewMode.isGrid
                    )
                }

                // Two-layer blur background: incoming opaque + outgoing fading 1→0 so the
                // previous focus dissolves away instead of snapping off. The premium styles
                // grade the wallpaper: Noir = muted wash, Adventure = warm amber (both static).
                val backdropColorFilter = when {
                    noirLibraryMode -> noirBackdropColorFilter(true)
                    adventureLibraryMode -> adventureBackdropColorFilter(true)
                    else -> null
                }
                // Round two, task 2: the background is a choice. BLURRED_ART is today's
                // per-game blurred cover art, unchanged. WALLPAPER draws the same static
                // "app wallpaper" image -- one image,
                // no per-focus resolve. SOLID draws nothing here at all: the Box above
                // already paints MaterialTheme.colorScheme.background, which *is* the solid
                // backdrop -- there is no decode, no blur, and no cross-dissolve to skip
                // because none of that machinery ever runs for this style.
                when (libraryBackgroundStyle) {
                    LibraryBackgroundStyle.BLURRED_ART -> LibraryBlurBackground(
                        displayedBlurReq = displayedBlurReq,
                        outgoingBlurReq = outgoingBlurReq,
                        outgoingBlurAlphaProvider = blurLayers.outgoingAlpha,
                        powerSaverMode = powerSaverMode,
                        hasArtwork = blurFocusedVault?.hasArtwork == true,
                        placeholder = transparentPlaceholder,
                        backdropColorFilter = backdropColorFilter
                    )
                    LibraryBackgroundStyle.WALLPAPER -> LibraryWallpaperBackground(
                        model = libraryWallpaperModel,
                        dim = libraryWallpaperDim,
                        backdropColorFilter = backdropColorFilter
                    )
                    LibraryBackgroundStyle.SOLID -> { /* Box's own background is the whole backdrop. */ }
                }

                // When item names are shown, dim the wallpaper with the same translucent
                // scrim the launcher uses behind its content, so the names (theme-coloured,
                // no per-text panel) stay readable over bright/unpredictable artwork.
                // Launcher mode applies its own scrim internally, so it skips this one.
                // Carousel used to skip it too ("keeps its hero gradient"), but with titles
                // on its blurred backdrop was the one view left undimmed — it now dims like
                // the rest. SOLID skips it: the flat theme background is
                // already a deliberately paired contrast with the item-name colour (see
                // VaultGridItem), so this scrim would only needlessly darken a backdrop
                // that was never the unpredictable thing it was built to tame.
                // Noir mode dims unconditionally (names on or off) over real artwork: the
                // darkened wallpaper is the whole point of the noir look — grey grid, one
                // lit cover. It has no special meaning over a flat SOLID card, so it doesn't
                // override the SOLID skip either.
                if ((showLibraryItemNames || noirLibraryMode) &&
                    libraryViewMode != LibraryViewMode.LAUNCHER &&
                    libraryBackgroundStyle != LibraryBackgroundStyle.SOLID &&
                    // WALLPAPER owns its wash through the user's dim slider; stacking this on
                    // top would make that slider mean something different here than elsewhere.
                    libraryBackgroundStyle != LibraryBackgroundStyle.WALLPAPER
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(palette.background.copy(alpha = 0.7f))
                    )
                }

            when {
                !startupFocusComplete || isLoading || (!libraryViewMode.isGrid && vaultToFocus != null && focusedVaultId != vaultToFocus && !startupFocusWaitOver) -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                filteredVaults.isEmpty() && searchQuery.isNotBlank() -> {
                    EmptySearchState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                filteredVaults.isEmpty() && !vaultsInitialLoadComplete -> {
                    // Still loading vaults from database — show spinner instead of empty state
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                filteredVaults.isEmpty() && libraryViewMode != LibraryViewMode.LAUNCHER -> {
                    EmptyState(
                        onAddVault = {
                            if (homeFolderUri == null) {
                                pendingActionAfterFolderPick = "add"
                                showHomeFolderRequiredDialog = true
                            } else {
                                showCreateVaultDialog = true
                            }
                        },
                        onDetectLastPlayed = triggerLibraryRefresh,
                        onBrowseEmulators = {
                            // Land in launcher view when the user returns from the catalog —
                            // browse-emulators is the entry point into the apps surface.
                            settingsViewModel.setLibraryViewMode(LibraryViewMode.LAUNCHER)
                            mainViewModel.navigateToAppCatalog()
                        },
                        modifier = Modifier.align(Alignment.Center),
                        focusColor = displayFocusColor.color
                    )
                }
                else -> {
                  androidx.compose.runtime.CompositionLocalProvider(
                      com.twinlore.app.ui.LocalNoirLibraryMode provides noirLibraryMode,
                      com.twinlore.app.ui.LocalAdventureLibraryMode provides adventureLibraryMode,
                      com.twinlore.app.ui.LocalGoldLibraryMode provides goldLibraryMode
                  ) {
                    if (libraryViewMode == LibraryViewMode.LAUNCHER) {
                        LauncherModeContent(
                            focusedVaultId = focusedVaultId,
                            displayAccentColorRaw = displayAccentColorRaw,
                            accentColorProvider = accentColorProvider,
                            focusColorFallback = focusColorState.color,
                            hapticFeedbackIntensity = hapticFeedbackIntensity,
                            artDisplayMode = artDisplayModeState.value,
                            gridAspectRatio = gridAspectRatio,
                            showLibraryItemNames = showLibraryItemNames,
                            sortedVaults = sortedVaults,
                            achievementInfoById = achievementSortInfo,
                            showAchievementBars = showAchievementBars,
                            onVaultLongPress = { vault -> selectedVaultForContextMenu = vault },
                            onFocusChange = { vaultId ->
                                FeedbackManager.playSound(context, FeedbackEvent.FOCUS, focusSoundEnabled)
                                view.performHapticFeedback(hapticFeedbackIntensity)
                                libraryViewModel.setFocusedVault(vaultId)
                            },
                            onSearchClick = { showSearchModal = true },
                            triggerLibraryRefresh = triggerLibraryRefresh,
                            focusedAppPackage = launcherFocusedAppPackage,
                            onFocusedAppPackageChange = { launcherFocusedAppPackage = it },
                            contextMenuRequestedTrigger = launcherContextMenuRequestedTrigger,
                            mainViewModel = mainViewModel,
                            libraryViewModel = libraryViewModel,
                            palette = palette,
                            isDarkTheme = isDarkTheme,
                            // Style-gated like the grid's: a configured-but-not-active wallpaper
                            // must not stand the launcher's readability scrim (or the bar's dim)
                            // down while another style is what is actually drawn.
                            wallpaperBackdrop = libraryWallpaperModel
                                .takeIf { libraryBackgroundStyle == LibraryBackgroundStyle.WALLPAPER },
                            gridState = libraryScrollState,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (libraryViewMode.isGrid) {
                        // Grid mode: one scrolling page whose first row is the top bar.
                        LibraryGridMode(
                            sortedVaults = sortedVaults,
                            achievementInfoById = achievementSortInfo,
                            showAchievementBars = showAchievementBars,
                            focusedVaultId = focusedVaultId,
                            displayFocusColor = displayFocusColor,
                            displayAccentColorRaw = displayAccentColorRaw,
                            accentColorProvider = accentColorProvider,
                            scrapingVaultIds = scrapingVaultIds,
                            detectionTransitionInProgress = detectionTransitionInProgress,
                            focusSoundEnabled = focusSoundEnabled,
                            hapticFeedbackIntensity = hapticFeedbackIntensity,
                            artDisplayMode = artDisplayModeState.value,
                            columns = (libraryViewMode.gridColumns ?: 4).coerceIn(1, 9),
                            topMenuFocusRequester = topMenuFocusRequester,
                            requestFocusTrigger = requestFocusTrigger,
                            isTopBarFocused = isTopBarFocused,
                            blurArtworkInvalidateTimestamp = blurArtworkInvalidateTimestamp,
                            ledEnabled = ledEnabled,
                            ledBrightness = ledBrightness,
                            ledSaturationBoost = ledSaturationBoost,
                            ledTurnOffTime = ledTurnOffTime,
                            gridAspectRatio = gridAspectRatio,
                            showLibraryItemNames = showLibraryItemNames,
                            isDarkTheme = isDarkTheme,
                            focusColorFallback = focusColorState.color,
                            searchQuery = searchQuery,
                            isScraping = isScraping,
                            addButtonFocusRequester = addButtonFocusRequester,
                            canFocusGrid = focusedVaultId != null && filteredVaults.isNotEmpty(),
                            libraryViewModel = libraryViewModel,
                            mainViewModel = mainViewModel,
                            onSelectVaultForContextMenu = { selectedVaultForContextMenu = it },
                            onRefreshTransition = { detectionTransitionVersion++ },
                            onShowCreateVault = {
                                if (homeFolderUri == null) {
                                    pendingActionAfterFolderPick = "add"
                                    showHomeFolderRequiredDialog = true
                                } else {
                                    showCreateVaultDialog = true
                                }
                            },
                            onShowSearch = { showSearchModal = true },
                            onRequestGridFocus = {
                                view.performHapticFeedback(hapticFeedbackIntensity)
                                requestFocusTrigger++
                            },
                            triggerLibraryRefresh = triggerLibraryRefresh,
                            gridState = libraryScrollState,
                        )
                    } else {
                        // Carousel mode
                        LibraryCarousel(
                            vaults = sortedVaults,
                            focusedVaultId = focusedVaultId,
                            displayFocusColor = displayFocusColor,
                            displayAccentColorRaw = displayAccentColorRaw,
                            accentColorProvider = accentColorProvider,
                            onVaultClick = { vault ->
                                libraryViewModel.onInputReceived()
                                mainViewModel.openVault(vault.id)
                                libraryViewModel.updateLastOpenedDeferred(vault.id)
                            },
                            // Game launching disabled for now (see project_game_launching.md)
                            onVaultDoubleTap = null,
                            onVaultLongPress = { vault ->
                                libraryViewModel.onInputReceived()
                                selectedVaultForContextMenu = vault
                            },
                            onFocusChange = { vaultId ->
                                FeedbackManager.playSound(context, FeedbackEvent.FOCUS, focusSoundEnabled)
                                view.performHapticFeedback(hapticFeedbackIntensity)
                                libraryViewModel.setFocusedVault(vaultId)
                            },
                            onScrollSettleFocus = { vaultId -> libraryViewModel.setFocusedVault(vaultId) },
                            artDisplayMode = artDisplayModeState.value,
                            topMenuFocusRequester = topMenuFocusRequester,
                            requestFocusTrigger = requestFocusTrigger,
                            modifier = Modifier.fillMaxSize(),
                            blurArtworkInvalidateTimestamp = blurArtworkInvalidateTimestamp,
                            gridAspectRatio = gridAspectRatio,
                            hapticFeedbackIntensity = hapticFeedbackIntensity,
                            ledEnabled = ledEnabled,
                            ledBrightness = ledBrightness,
                            ledSaturationBoost = ledSaturationBoost,
                            ledTurnOffTime = ledTurnOffTime,
                            showLibraryItemNames = showLibraryItemNames,
                            isDarkTheme = isDarkTheme,
                            powerSaverMode = powerSaverMode
                        )
                    }
                  }
                }
            }
        }

        // Top menu bar (Overlay):
        //  - Grid/list/launcher: hidden here, they carry the bar as their page's first row
        //  - Carousel: shown (overlay on content; it has no vertical scroll to ride)
        //  - Empty library in grid/list: shown, because those modes take the EmptyState branch
        //    above and never compose a grid to carry the row
        //  - Empty library in LAUNCHER: still hidden. The launcher is the one mode that renders
        //    its content composable while empty, so its own item-0 bar is on screen; showing
        //    this one too would stack two of the same bar, one of which then scrolls out from
        //    under the other. That is the fresh-install state, so it is the first thing anyone sees.
        val vaultsEmpty = filteredVaults.isEmpty() && !isLoading && startupFocusComplete
        val showOverlayTopBar = libraryViewMode != LibraryViewMode.LAUNCHER &&
            (!libraryViewMode.isGrid || vaultsEmpty)
        // When the library is empty there is no grid to receive focus, so park focus on the top
        // menu's first button (the + button) so the controller has somewhere to land.
        //
        // NOT when the empty state's own action buttons are on screen. Those are focusable now
        // and anchor focus on their first action, which is the thing the user actually came to
        // press; this effect fires later and would take it straight back to the + button. It
        // still covers the cases with nothing focusable in the content area: a search that
        // matched nothing and a library still loading.
        //
        // Never in LAUNCHER: this requester belongs to the overlay bar, which that mode no
        // longer composes, so aiming at it would burn the retry on an unattached requester.
        // The launcher's inline EmptyState anchors focus on its own first action.
        val emptyStateOwnsFocus = vaultsEmpty &&
            searchQuery.isBlank() &&
            vaultsInitialLoadComplete &&
            libraryViewMode != LibraryViewMode.LAUNCHER
        LaunchedEffect(vaultsEmpty, emptyStateOwnsFocus, libraryViewMode) {
            if (vaultsEmpty && !emptyStateOwnsFocus && libraryViewMode != LibraryViewMode.LAUNCHER) {
                kotlinx.coroutines.delay(100)
                try { topMenuFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
        key(showOverlayTopBar) {
            if (showOverlayTopBar) {
            LibraryOverlayTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    libraryViewModel.updateSearchQuery(it)
                    libraryViewModel.onInputReceived()
                },
                onCreateVault = {
                    if (homeFolderUri == null) {
                        pendingActionAfterFolderPick = "add"
                        showHomeFolderRequiredDialog = true
                    } else {
                        showCreateVaultDialog = true
                    }
                },
                onSettings = { mainViewModel.navigateToSettings() },
                onJourney = { mainViewModel.navigateToJourney() },
                onRefreshBoxArt = triggerLibraryRefresh,
                onSearchClick = { showSearchModal = true },
                onInputReceived = { libraryViewModel.onInputReceived() },
                onTopBarFocusChanged = { focused -> libraryViewModel.setTopBarFocused(focused) },
                isScraping = isScraping,
                firstButtonFocusRequester = topMenuFocusRequester,
                addButtonFocusRequester = addButtonFocusRequester,
                accentColorOverride = displayAccentColorRaw,
                canFocusGrid = focusedVaultId != null && filteredVaults.isNotEmpty(),
                onDpadDownToGrid = {
                    view.performHapticFeedback(hapticFeedbackIntensity)
                    requestFocusTrigger++
                },
            )
            }
        }

        // Bottom controller hint bar — landscape only, library views only,
        // user-toggleable from Settings → Look & Feel.
        val libraryHintBarVisible by settingsViewModel.libraryHintBarVisible.collectAsState(initial = true)
        if (libraryHintBarVisible) {
            LibraryBottomHintBar(
                // Pinned in every view mode now, as it already was in carousel. It is a
                // controller legend, so it is most useful when it never moves.
                visible = true,
                libraryViewMode = libraryViewMode,
                librarySortField = librarySortField,
                librarySortDirection = librarySortDirection,
                isScraping = isScraping,
                onSettings = { mainViewModel.navigateToSettings() },
                onRefresh = { mainViewModel.requestRefreshLibrary() },
                onEdit = { mainViewModel.requestOpenLibraryContextMenu() },
                onOpen = {
                    val vaultId = focusedVaultId ?: return@LibraryBottomHintBar
                    mainViewModel.openVault(vaultId)
                },
                onViewModeSelected = { settingsViewModel.setLibraryViewMode(it) },
                onSortFieldSelected = { field ->
                    settingsViewModel.setLibrarySortField(field)
                    settingsViewModel.setLibrarySortDirection(field.defaultDirection)
                },
                onSortDirectionToggle = {
                    settingsViewModel.setLibrarySortDirection(
                        if (librarySortDirection == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
                    )
                },
                buttonGlyphStyle = buttonGlyphStyle,
                swapFaceButtons = swapFaceButtons,
                // Carousel has no vertical scroll, so there is no "top" to return to.
                showBackToTop = showBackToTop && libraryViewMode != LibraryViewMode.CAROUSEL,
                onBackToTop = {
                    libraryScrollCoroutineScope.launch {
                        // scrollToItem, NOT animateScrollToItem. The animated variant walks the
                        // scroll through every intermediate row and only teleports when the target
                        // is ~100+ items away, so at ordinary library sizes it composes and
                        // measures the entire list in one frame. Profiled on the S24 (39 vaults):
                        // one 178ms frame, 216ms of main-thread CPU, of which 130ms was inside
                        // animateScrollToItem, 112ms measureLazyGrid and 54ms text layout. The
                        // jump only ever composes the destination viewport.
                        try { libraryScrollState.scrollToItem(0) } catch (_: Exception) {}
                        // Carry the SELECTION to the top too, not just the viewport. Without this
                        // the list jumps home while focus stays on whatever row it was on, so the
                        // next d-pad press snaps the view straight back down — and in LIST mode,
                        // where the selection is what the whole layout is built around, the page
                        // just looked wrong.
                        //
                        // Sequenced after the scroll, inside the same coroutine, on purpose: the
                        // grid's focus effect only scrolls when its target is off-screen, so if
                        // focus moved first it would race the jump and scroll item 1 to the top of
                        // the viewport, hiding the top bar the jump exists to reveal.
                        //
                        // isGrid == LIST + the three grid sizes; CAROUSEL has no pill and LAUNCHER's
                        // first element is its carousel, not sortedVaults[0], so neither applies.
                        if (libraryViewMode.isGrid) {
                            sortedVaults.firstOrNull()?.let { first ->
                                libraryViewModel.setFocusedVault(first.id)
                                // The grid re-keys its focus effect on this, which is what makes
                                // the request land even when the id did not change (focus already
                                // on the first vault) or the top bar currently holds focus.
                                requestFocusTrigger++
                            }
                        }
                    }
                },
            )
        }

        // Post-refresh fullscreen vault showcase. Sits below the fade overlay so
        // the fade-back animation reveals it. Confirm/tap opens the vault; any
        // other input dismisses back to the library underneath.
        if (showVaultShowcase) {
            // The rings pulse with the cover art's glow for as long as the
            // showcase is up, then settle on the accent when it goes.
            DisposableEffect(ledEnabled, ledBrightness, ledSaturationBoost, ledTurnOffTime) {
                ThorLedManager.startPulse(
                    displayAccentColorRaw,
                    ledEnabled,
                    ledBrightness,
                    ledSaturationBoost,
                    ledTurnOffTime.inactivityMs
                )
                onDispose { ThorLedManager.stopPulse() }
            }
            LibraryShowcaseOverlay(
                vault = focusedVault,
                accentColor = displayAccentColorRaw,
                hapticFeedbackIntensity = hapticFeedbackIntensity,
                libraryViewModel = libraryViewModel,
                mainViewModel = mainViewModel,
                onDismissShowcase = {
                    showVaultShowcase = false
                },
                onContentReady = { showcaseContentReady = true },
            )
        }

        // Fade-to-black overlay (idle switch or detection): screen goes black then fades back to show new content; LED breathe runs (no haptic)
        if (effectiveOverlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(effectiveOverlayAlpha)
                    .background(Color.Black)
            )
        }


        // Preload splash: shows the app logo with a loading animation until
        // initial grid images are in Coil's cache and the minimum splash time has
        // passed. Keyed on the alpha alone so the hold and the fade both count.
        if (preloadOverlayAlpha > 0f) {
            val splashIcon by settingsViewModel.appIcon.collectAsState()
            com.twinlore.app.ui.components.LibraryPreloadSplash(
                preloadOverlayAlpha = preloadOverlayAlpha,
                logoRes = splashIcon.splashLogoRes,
                bgColor = splashIcon.splashBgColor,
                backdropIcon = splashIcon,
            )
        }

        storageFolderSet?.let { set ->
            val proceed: () -> Unit = {
                storageFolderSet = null
                if (set.needsRestart) {
                    com.twinlore.app.ui.utils.restartApp(context)
                } else {
                    when (set.pendingAction) {
                        "refresh" -> detectionTransitionVersion++
                        "add" -> showCreateVaultDialog = true
                    }
                }
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = proceed,
                title = {
                    Text("Storage folder selected", color = MaterialTheme.colorScheme.onSurface)
                },
                text = {
                    val message = if (set.needsRestart) {
                        "Your files will be saved here. The app will restart to load your library from the new folder."
                    } else {
                        "Your files will be saved here."
                    }
                    Text(message, color = MaterialTheme.colorScheme.onSurface)
                },
                containerColor = LocalThemePalette.current.background,
                confirmButton = {
                    TextButton(
                        onClick = proceed,
                        colors = ButtonDefaults.textButtonColors(contentColor = displayFocusColor.color)
                    ) {
                        Text(if (set.needsRestart) "Restart" else "Continue")
                    }
                },
            )
        }

        if (showHomeFolderRequiredDialog) {
            HomeFolderRequiredDialog(
                onPickFolder = {
                    showHomeFolderRequiredDialog = false
                    mainViewModel.consumeHomeFolderSetupRequest()
                    homeFolderPickerLauncher.launch(null)
                },
                onCancel = {
                    showHomeFolderRequiredDialog = false
                    pendingActionAfterFolderPick = null
                    mainViewModel.consumeHomeFolderSetupRequest()
                },
                focusColor = displayFocusColor.color
            )
        }

        val libLoad = pendingLibraryLoad
        if (libLoad != null && libLoad.tooNewVersion != null) {
            // The newest snapshot in the picked folder was saved by a newer app
            // build than this one can read. Refuse the load entirely — never
            // switch storage or import — so the current library folder stays
            // put and the unreadable one is left untouched.
            com.twinlore.app.ui.startup.LibrarySnapshotTooNewDialog(
                savedVersion = libLoad.tooNewVersion,
                supportedVersion = com.twinlore.app.data.local.SCHEMA_VERSION,
                onPickDifferentFolder = {
                    pendingLibraryLoad = null
                    pendingActionAfterFolderPick = null
                    homeFolderPickerLauncher.launch(null)
                },
                onUpdate = {
                    pendingLibraryLoad = null
                    pendingActionAfterFolderPick = null
                    settingsViewModel.checkForUpdate()
                },
                onDismiss = {
                    pendingLibraryLoad = null
                    pendingActionAfterFolderPick = null
                },
                dismissable = true,
            )
        } else if (libLoad != null) {
            val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
            val accent = focusColorState.color
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    pendingLibraryLoad = null
                    pendingActionAfterFolderPick = null
                },
                title = {
                    Text(
                        "Load library from this folder?",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    val message = if (libLoad.snapshotCount > 1) {
                        "This folder has ${libLoad.snapshotCount} library snapshots. The most recent one will be loaded. The app will restart."
                    } else {
                        "This folder already has a library. Load it instead of starting fresh? The app will restart."
                    }
                    Text(message, color = MaterialTheme.colorScheme.onSurface)
                },
                containerColor = LocalThemePalette.current.background,
                confirmButton = {
                    TextButton(
                        onClick = {
                            val uriToLoad = libLoad.folderUri
                            pendingLibraryLoad = null
                            pendingActionAfterFolderPick = null
                            homeFolderPickScope.launch {
                                try {
                                    settingsViewModel.runExclusiveSave {
                                        // Import first, switch only on success: a
                                        // refused load must never adopt the folder.
                                        settingsViewModel.loadLibraryFromFolder(uriToLoad)
                                    }
                                    com.twinlore.app.ui.utils.restartApp(context)
                                } catch (e: com.twinlore.app.data.library.LibrarySnapshotTooNewException) {
                                    // Defense-in-depth for a folder that turned
                                    // too-new between the pre-check and here: the
                                    // load was refused BEFORE any switch, so the
                                    // library folder is unchanged. Surface the
                                    // dialog instead of switching.
                                    Log.w("LibraryLoadFromPick", "snapshot too new", e)
                                    pendingLibraryLoad = PendingLibraryLoad(
                                        folderUri = uriToLoad,
                                        snapshotCount = libLoad.snapshotCount,
                                        tooNewVersion = e.savedVersion,
                                    )
                                } catch (e: Exception) {
                                    Log.e("LibraryLoadFromPick", "Load failed", e)
                                    android.widget.Toast.makeText(
                                        context,
                                        "Load failed: ${e.message ?: "unknown error"}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = accent)
                    ) {
                        Text("Load")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingLibraryLoad = null
                            pendingActionAfterFolderPick = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = accent)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Storage migration progress dialog (shown when picker migrates files to new library folder)
        if (libScreenStorageMigrating) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { /* non-dismissable */ },
                title = {
                    Text(
                        text = "Moving files…",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    when (val p = libScreenStorageMigrationProgress) {
                        is com.twinlore.app.data.library.StorageFolderMigrationCoordinator.Progress.Working -> {
                            Column {
                                Text(
                                    text = "${p.current} of ${p.total}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = p.filename,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        is com.twinlore.app.data.library.StorageFolderMigrationCoordinator.Progress.Failed -> {
                            Text(
                                text = "A file failed to copy: ${p.cause.message ?: "unknown error"}. Continuing…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Text(
                                text = "Preparing…",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                containerColor = LocalThemePalette.current.background,
                confirmButton = {},
                dismissButton = {}
            )
        }

        // Show summary toast when migration finishes
        LaunchedEffect(libScreenStorageMigrationProgress) {
            val p = libScreenStorageMigrationProgress
            if (p is com.twinlore.app.data.library.StorageFolderMigrationCoordinator.Progress.Done) {
                val msg = when {
                    p.failed == 0 && p.migrated == 0 -> "Library folder switched."
                    p.failed == 0 -> "Moved ${p.migrated} file(s) to the new library folder."
                    else -> "Moved ${p.migrated} file(s); ${p.failed} failed."
                }
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                libScreenStorageMigrationProgress = null
            }
        }

        // RetroArch log-file help dialog. Auto-opens when refresh or folder pick
        // lands on a non-Valid state (wrong folder, logging off, etc).
        val pendingRetroArchHelpDialog by libraryViewModel.pendingRetroArchHelpDialog.collectAsState()
        if (pendingRetroArchHelpDialog) {
            com.twinlore.app.ui.components.RetroArchHelpDialog(
                onPickFolder = {
                    libraryViewModel.dismissRetroArchHelpDialog()
                    try {
                        folderPickerLauncher.launch(null)
                    } catch (e: Exception) {
                        Log.e("LibraryScreen", "Error launching RA folder picker from help dialog", e)
                    }
                },
                onDismiss = { libraryViewModel.dismissRetroArchHelpDialog() }
            )
        }

        // ES-DE log-file help dialog. Same pattern as RetroArch.
        val pendingESDEHelpDialog by libraryViewModel.pendingESDEHelpDialog.collectAsState()
        if (pendingESDEHelpDialog != null) {
            com.twinlore.app.ui.components.ESDELoggingHelpDialog(
                state = pendingESDEHelpDialog,
                onPickFolder = {
                    libraryViewModel.dismissESDEHelpDialog()
                    try {
                        folderPickerLauncher.launch(null)
                    } catch (e: Exception) {
                        Log.e("LibraryScreen", "Error launching ES-DE folder picker from help dialog", e)
                    }
                },
                onDismiss = { libraryViewModel.dismissESDEHelpDialog() }
            )
        }

        // Cocoon log-file help dialog. Same pattern as RetroArch.
        val pendingCocoonHelpDialog by libraryViewModel.pendingCocoonHelpDialog.collectAsState()
        if (pendingCocoonHelpDialog != null) {
            com.twinlore.app.ui.components.CocoonLoggingHelpDialog(
                state = pendingCocoonHelpDialog,
                onPickFolder = {
                    libraryViewModel.dismissCocoonHelpDialog()
                    try {
                        folderPickerLauncher.launch(null)
                    } catch (e: Exception) {
                        Log.e("LibraryScreen", "Error launching Cocoon folder picker from help dialog", e)
                    }
                },
                onDismiss = { libraryViewModel.dismissCocoonHelpDialog() }
            )
        }

        // Setup dialog for play history source selection
        if (showFolderExplanationDialog) {
            LastPlayedFolderExplanationDialog(
                onSetRetroArch = {
                    pickerSetupSource = com.twinlore.app.data.LastPlayedSource.RETROARCH
                    hasShownPicker = true
                    showFolderExplanationDialog = false
                    try {
                        folderPickerLauncher.launch(null)
                    } catch (e: Exception) {
                        Log.e("LibraryScreen", "Error launching folder picker", e)
                        libraryViewModel.cancelFolderSelection()
                        hasShownPicker = false
                        showFolderExplanationDialog = false
                    }
                },
                onSetESDE = {
                    pickerSetupSource = com.twinlore.app.data.LastPlayedSource.ES_DE
                    hasShownPicker = true
                    showFolderExplanationDialog = false
                    try {
                        folderPickerLauncher.launch(null)
                    } catch (e: Exception) {
                        Log.e("LibraryScreen", "Error launching folder picker", e)
                        libraryViewModel.cancelFolderSelection()
                        hasShownPicker = false
                        showFolderExplanationDialog = false
                    }
                },
                onSetCocoon = {
                    pickerSetupSource = com.twinlore.app.data.LastPlayedSource.COCOON
                    hasShownPicker = true
                    showFolderExplanationDialog = false
                    try {
                        folderPickerLauncher.launch(null)
                    } catch (e: Exception) {
                        Log.e("LibraryScreen", "Error launching folder picker", e)
                        libraryViewModel.cancelFolderSelection()
                        hasShownPicker = false
                        showFolderExplanationDialog = false
                    }
                },
                onSetAchievements = {
                    settingsViewModel.setLastPlayedSource(com.twinlore.app.data.LastPlayedSource.ACHIEVEMENTS)
                    showFolderExplanationDialog = false
                    libraryViewModel.cancelFolderSelection()
                    if (hasAchievementCredentials) {
                        detectionTransitionVersion++
                    } else {
                        mainViewModel.navigateToSettings(expandAchievements = true)
                    }
                },
                onSkip = {
                    showFolderExplanationDialog = false
                    libraryViewModel.cancelFolderSelection()
                    homeFolderPickScope.launch {
                        kotlinx.coroutines.delay(100)
                        try { addButtonFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                },
                focusColor = displayFocusColor.color,
                isDarkTheme = isDarkTheme,
                errorMessage = sourceSetupFailure,
            )
        }

        // Scraping progress indicator using Dialog to ensure it appears on top -
        // of everything, which is why it has to wait for the splash. See
        // [detectionDialogVisible].
        if (detectionDialogVisible(isScraping, preloadOverlayAlpha)) {
            LibraryScrapingProgressDialog(
                scrapingProgress = scrapingProgress,
                displayFocusColor = displayFocusColor,
                onSkip = { libraryViewModel.cancelScraping() },
                onRetry = { libraryViewModel.retryScrapeArtwork() },
                onDismissError = { libraryViewModel.dismissScrapeProgress() },
            )
        }
        
        // Vault discovery/sync indicator — shown while scanning a new storage folder
        if (isDiscovering) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = displayAccentColorRaw
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Syncing vaults\u2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Search Modal
        // Track search modal state to prevent idle mode
        LaunchedEffect(showSearchModal) {
            libraryViewModel.setSearchModalOpen(showSearchModal)
        }
        
        if (showSearchModal) {
            SearchVaultModal(
                vaults = filteredVaults,
                onDismiss = {
                    libraryViewModel.setSearchModalOpen(false)
                    showSearchModal = false
                },
                onVaultSelected = { vault ->
                    libraryViewModel.setFocusedVault(vault.id)
                    libraryViewModel.onInputReceived()
                    libraryViewModel.setSearchModalOpen(false)
                    showSearchModal = false
                },
                isDarkTheme = isDarkTheme,
                focusColor = displayFocusColor.color,
                hapticFeedbackIntensity = hapticFeedbackIntensity,
                ledEnabled = ledEnabled,
                ledBrightness = ledBrightness,
                ledSaturationBoost = ledSaturationBoost,
                ledTurnOffTime = ledTurnOffTime
            )
        }
        
        // Create Vault Dialog
        if (showCreateVaultDialog) {
            // Get suggestion provider via Hilt EntryPoint
            val suggestionProvider = remember {
                EntryPoints.get(
                    context.applicationContext,
                    GameNameSuggestionEntryPoint::class.java
                ).gameNameSuggestionProvider()
            }
            val hasRaCreds by libraryViewModel.hasRaCredentials.collectAsState()

            CreateVaultDialog(
                onDismiss = { showCreateVaultDialog = false },
                onConfirm = { gameName ->
                    libraryViewModel.createVault(gameName)
                    showCreateVaultDialog = false
                },
                onAddAotW = { gameName, raGameId ->
                    if (hasRaCreds) {
                        libraryViewModel.createVaultWithRaLink(gameName, raGameId)
                    } else {
                        libraryViewModel.createVaultWithRaLink(gameName, null)
                    }
                    showCreateVaultDialog = false
                },
                suggestionProvider = suggestionProvider
            )
        }
        
        // Milestone "support the development" prompt (unlicensed installs only).
        // Deferred while the scrape-progress dialog is up; the state stays set,
        // so it appears the moment the scrape dialog leaves.
        var supportPreviewIcon by remember { mutableStateOf<com.twinlore.app.data.AppIcon?>(null) }
        supportPrompt?.let { prompt ->
            if (!isScraping) {
                com.twinlore.app.ui.license.SupportDevDialog(
                    onSupport = {
                        openSupportDevPage(context)
                        libraryViewModel.dismissSupportPrompt()
                    },
                    onDismiss = { libraryViewModel.dismissSupportPrompt() },
                    isFinal = prompt.isFinal,
                    onIconTap = { supportPreviewIcon = it },
                )
            }
        }
        com.twinlore.app.ui.license.ThemePreviewHost(
            icon = supportPreviewIcon,
            settingsViewModel = settingsViewModel,
            onDismiss = { supportPreviewIcon = null },
        )

        // Refresh summary dialog: only surfaces when the user must act (license-cap reached).
        // Successful local-bind results are now shown inline as a step in the scraping progress
        // dialog ("Linked N local assets"), so the post-refresh dialog no longer fires for them.
        refreshSummary?.takeIf { it.skippedByLicenseCap.isNotEmpty() }?.let { summary ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { libraryViewModel.dismissRefreshSummary() },
                title = { androidx.compose.material3.Text(refreshDialogTitle(summary)) },
                text = {
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        if (summary.localBindEntries.isNotEmpty()) {
                            androidx.compose.material3.Text(
                                "Linked from your library:",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            )
                            androidx.compose.foundation.layout.Column(
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                            ) {
                                summary.localBindEntries.forEach { e ->
                                    androidx.compose.material3.Text(
                                        "  \u2022 ${e.vaultName}: ${formatBindParts(e)}"
                                    )
                                }
                            }
                        }
                        if (summary.skippedByLicenseCap.isNotEmpty()) {
                            if (summary.localBindEntries.isNotEmpty()) {
                                androidx.compose.foundation.layout.Spacer(
                                    modifier = androidx.compose.ui.Modifier.height(8.dp)
                                )
                            }
                            androidx.compose.material3.Text(
                                "${summary.skippedByLicenseCap.size} game${if (summary.skippedByLicenseCap.size == 1) " was" else "s were"} found but " +
                                    "not added because the free version is limited to 2 vaults:"
                            )
                            summary.skippedByLicenseCap.forEach { name ->
                                androidx.compose.material3.Text("  \u2022 $name")
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        if (summary.skippedByLicenseCap.isNotEmpty()) {
                            openLemonSqueezyCheckout(context)
                        }
                        libraryViewModel.dismissRefreshSummary()
                    }) {
                        androidx.compose.material3.Text(
                            if (summary.skippedByLicenseCap.isNotEmpty()) "Buy License" else "OK"
                        )
                    }
                },
                dismissButton = {
                    androidx.compose.foundation.layout.Column {
                        if (summary.skippedByLicenseCap.isNotEmpty()) {
                            androidx.compose.material3.TextButton(onClick = {
                                libraryViewModel.dismissRefreshSummary()
                                mainViewModel.navigateToSettings()
                            }) { androidx.compose.material3.Text("I have a key") }
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { libraryViewModel.dismissRefreshSummary() },
                        ) { androidx.compose.material3.Text("Dismiss") }
                    }
                },
            )
        }

        // Fuzzy Match Confirmation Dialogs (manual creation, auto-detection, scrape-path)
        LibraryFuzzyMatchDialogs(
            fuzzyMatchPrompt = fuzzyMatchPrompt,
            autoDetectFuzzyMatch = autoDetectFuzzyMatch,
            scrapePathFuzzyMatch = scrapePathFuzzyMatch,
            accentColor = focusColorState.color,
            libraryViewModel = libraryViewModel
        )

        // Vault Context Menu
        selectedVaultForContextMenu?.let { vault ->
            LibraryVaultContextMenuSection(
                vault = vault,
                libraryViewModel = libraryViewModel,
                context = context,
                onDismiss = { selectedVaultForContextMenu = null }
            )
        }

        androidx.compose.material3.SnackbarHost(
            hostState = scrapeErrorSnackbarHost,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        )
    }
}

/** Badge URL prefix for RetroAchievements CDN. */
private const val RA_SHOWCASE_BADGE_CDN = com.twinlore.app.data.retroachievements.RaUrls.BADGE_BASE

private const val PRELOAD_TAG = "LibraryPreload"

/**
 * The themed splash stays up at least this long even when the preload finishes
 * sooner. A warm thumbnail cache finishes preloading in well under a second,
 * which reduced the splash (and its per-theme backdrop animation) to a flash.
 */
private const val PRELOAD_SPLASH_MIN_SHOW_MS = 2_000L

/**
 * One hold per process: the minimum splash time is a cold-start affordance, and navigating
 * back to the library (from Settings, a vault, the Journey) must not replay it. Deliberately
 * process-global rather than composition state, because LibraryScreen leaves composition on
 * every one of those trips.
 */
private var splashHoldConsumed = false

/**
 * Floor for the viewport-derived splash gate, so an unexpected geometry (a very
 * short window, a decode size that buckets up further than the tile actually
 * draws) can never gate fewer tiles than a small grid shows at once. Costs a few
 * decodes on the critical path in the worst case; the alternative is visible
 * pop-in on the first screenful, which is the thing the preload exists to prevent.
 */
private const val MIN_PRELOAD_GATE = 8

/** Common achievement display data for the showcase overlay. */
private val ShowcaseSilver = Color(0xFFC0C0C0)
private val ShowcaseGold = Color(0xFFFFD700)

/** Min age before the showcase re-fetches a game's progress on (re)open — avoids
 *  back-to-back force-refreshes (each is 3 RA / 2-3 Steam calls) when reopening. */
private const val SHOWCASE_REFRESH_TTL_MS = 60_000L

private data class ShowcaseAchievementData(
    val unlocked: Int,
    val total: Int,
    val percentText: String,
    /** Hardcore unlock count (RA only, null for Steam). */
    val hardcoreUnlocked: Int? = null,
    /** Hardcore completion percent text (RA only, null for Steam). */
    val hardcorePercentText: String? = null,
    /** RA highest award kind (mastered/completed/beaten-*); null for Steam. */
    val awardKind: String? = null,
    val badges: List<ShowcaseBadge>
)

private data class ShowcaseBadge(
    val imageUrl: String?,
    val isUnlocked: Boolean
)

private fun com.twinlore.app.data.retroachievements.RaGameProgress.toShowcaseData(): ShowcaseAchievementData {
    val sorted = achievements.sortedWith(compareByDescending<com.twinlore.app.data.retroachievements.RaAchievementDisplay> { it.dateAwarded != null }.thenBy { it.displayOrder })
    return ShowcaseAchievementData(
        unlocked = userNumAchieved,
        total = numAchievements,
        percentText = userCompletionPercent ?: if (numAchievements > 0) "${userNumAchieved * 100 / numAchievements}%" else "0%",
        hardcoreUnlocked = userNumHardcore,
        hardcorePercentText = userCompletionHardcorePercent ?: if (numAchievements > 0) "${userNumHardcore * 100 / numAchievements}%" else "0%",
        awardKind = highestAwardKind,
        badges = sorted.map { a ->
            ShowcaseBadge(
                imageUrl = if (a.badgeName.isNotBlank()) "$RA_SHOWCASE_BADGE_CDN${a.badgeName}.png" else null,
                isUnlocked = a.dateAwarded != null
            )
        }
    )
}

private fun com.twinlore.app.data.steam.SteamGameProgress.toShowcaseData(): ShowcaseAchievementData {
    val sorted = achievements.sortedWith(compareByDescending<com.twinlore.app.data.steam.SteamAchievementDisplay> { it.achieved }.thenBy { it.title })
    return ShowcaseAchievementData(
        unlocked = numUnlocked,
        total = numAchievements,
        percentText = if (numAchievements > 0) "${numUnlocked * 100 / numAchievements}%" else "0%",
        badges = sorted.map { a ->
            ShowcaseBadge(
                imageUrl = if (a.achieved) a.iconUrl else a.iconGrayUrl,
                isUnlocked = a.achieved
            )
        }
    )
}

/**
 * Fullscreen overlay shown after refresh detection completes. Renders the focused
 * vault's cover art on a blurred backdrop so the user lands on the just-detected
 * game. Confirm/tap launches it; any other input dismisses back to the library.
 *
 * Triggered exclusively from the detection LaunchedEffect — no time-based timer,
 * no idle-mode coupling.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultShowcaseOverlay(
    vault: Vault?,
    accentColor: Color,
    hapticFeedbackIntensity: HapticFeedbackIntensity,
    resourceResolver: com.twinlore.app.data.library.ResourceResolver,
    raGameCacheRepository: com.twinlore.app.data.retroachievements.RaGameCacheRepository,
    steamGameCacheRepository: com.twinlore.app.data.steam.SteamGameCacheRepository,
    raProfileRepository: com.twinlore.app.data.retroachievements.RaProfileRepository,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onContentReady: () -> Unit = {},
) {
    if (vault == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val showcaseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { showcaseFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // --- Exit state: spinner on art while vault loads ---
    var isOpening by remember { mutableStateOf(false) }
    // Mirrored up from ShowcaseEmblemButton. The root key handler below
    // dismisses the showcase on any non-confirm key, so it has to stand down
    // while the support dialog is up or a d-pad press aimed at the dialog
    // would tear the showcase down behind it.
    var supportDialogOpen by remember { mutableStateOf(false) }
    var contentReadySignaled by remember(vault.id) { mutableStateOf(false) }

    fun startOpenTransition() {
        if (isOpening) return
        isOpening = true
        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
        // Fire openVault and keep the overlay (spinner) covering the screen. openVault
        // is async (DB + artwork IO) — once it flips state to VaultSession the nav
        // dissolve fades the vault in ON TOP of this still-opaque overlay and then
        // removes the library screen, so we never reveal the library grid in between.
        // Do NOT dismiss on a fixed timer: a slow openVault would race it and flash the
        // grid. This is only a safety net for the rare case openVault never flips state
        // (e.g. the vault was deleted) — long enough that it never fires on the happy
        // path (the overlay leaves composition with the library screen first).
        onOpen()
        scope.launch {
            kotlinx.coroutines.delay(4000)
            onDismiss()
        }
    }

    BackHandler(enabled = !isOpening) { onDismiss() }

    // Resolve artwork off the main thread so the overlay can paint immediately.
    // Bounds-decode in the same IO pass so [artworkAspect] is known before any
    // composable that reads it gets to lay out — the artwork composable is gated
    // on `artworkRequest != null`, which is set in the same write batch, so the
    // landscape/stacked branch never flips after first paint.
    var artworkRequest by remember(vault.id) { mutableStateOf<ImageRequest?>(null) }
    // Separate small request for the blurred backdrop — blurring a full-screen
    // bitmap is the showcase's main mount-time cost; a 256px source blurs instantly
    // (the 40dp blur destroys detail anyway).
    var backdropRequest by remember(vault.id) { mutableStateOf<ImageRequest?>(null) }
    var artworkAspect by remember(vault.id) { mutableStateOf<Float?>(null) }
    var artworkPx by remember(vault.id) { mutableStateOf<androidx.compose.ui.unit.IntSize?>(null) }
    LaunchedEffect(vault.id, vault.artworkResourceId) {
        val rid = vault.artworkResourceId ?: run {
            artworkRequest = null
            backdropRequest = null
            artworkAspect = null
            artworkPx = null
            contentReadySignaled = true
            onContentReady() // no artwork to wait for
            return@LaunchedEffect
        }
        val (data, aspect) = withContext(Dispatchers.IO) {
            val uri = resourceResolver.resolve(rid) ?: return@withContext null to null
            val bounds = runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                }
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    androidx.compose.ui.unit.IntSize(opts.outWidth, opts.outHeight)
                } else {
                    null
                }
            }.getOrNull()
            ImagePathUtils.toImageData(uri.toString(), vault.id) to bounds
        }
        artworkPx = aspect
        artworkAspect = aspect?.let { it.width.toFloat() / it.height }
        artworkRequest = if (data == null) null else ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey("showcase_${vault.id}_$rid")
            .placeholderMemoryCacheKey("grid_art_${vault.id}_${rid}_256x341_0")
            .crossfade(180)
            .allowHardware(true)
            .build()
        backdropRequest = if (data == null) null else ImageRequest.Builder(context)
            .data(data)
            .size(256)
            .memoryCacheKey("showcase_bg_${vault.id}_$rid")
            .placeholderMemoryCacheKey("grid_art_${vault.id}_${rid}_256x341_0")
            .allowHardware(true)
            .build()
        // If there's no artwork to paint, signal ready now. Otherwise the foreground
        // AsyncImage's onState fires onContentReady once the image actually paints, so
        // the detection flow reveals a correctly-sized image (no resize pop on a cold
        // refresh where the artwork isn't cached yet).
        if (data == null) {
            contentReadySignaled = true
            onContentReady()
        }
    }

    // --- Achievement data: cache-first + background refresh ---
    val raGameId = vault.raGameId?.toIntOrNull()
    val steamAppId = vault.steamAppId?.toIntOrNull()

    var achievementData by remember(vault.id) { mutableStateOf<ShowcaseAchievementData?>(null) }
    val achievementAlpha = remember { Animatable(0f) }

    // RA rich-presence ("now playing") line: shown below the progress bar only when
    // the detected game IS the RA last-played game (LastGameID == this vault's raGameId).
    // Refreshed on the last-detect flow (see LibraryViewModel / SyncLatestPlayedGameUseCase).
    val raProfile by raProfileRepository.profile.collectAsState(initial = null)
    val rpProfile = raProfile?.takeIf { raGameId != null && it.lastGameId == raGameId }
    // Already trimmed/blank-stripped at the source (RaProfileRepository.mapSummaryToDisplay).
    val richPresence = rpProfile?.richPresenceMsg
    val richPresenceIcon = rpProfile
        ?.recentlyPlayed?.firstOrNull { it.gameId == raGameId }
        ?.imageIcon
        ?.takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("http")) it else "https://retroachievements.org$it" }

    LaunchedEffect(vault.id, raGameId, steamAppId) {
        achievementData = null
        // 1) Cache-only read for instant display + its age (no network).
        val raCached = if (raGameId != null) raGameCacheRepository.getCachedProgress(vault.id) else null
        val steamCached = if (raGameId == null && steamAppId != null) steamGameCacheRepository.getCachedProgress(vault.id) else null
        val cached = raCached?.toShowcaseData() ?: steamCached?.toShowcaseData()
        if (cached != null && cached.total > 0) {
            achievementData = cached
            achievementAlpha.animateTo(1f, animationSpec = tween(durationMillis = 300))
        }
        // 2) Refresh-on-detect — but only if the cache is missing or older than the
        // short TTL, so reopening the showcase within a minute doesn't refetch (each
        // refetch is 3 RA / 2-3 Steam calls). forceRefresh rewrites the cache, so the
        // library tile + award update too. Falls back to the cached value on failure.
        val cacheAge = raCached?.lastSyncedAt ?: steamCached?.lastSyncedAt
        val stale = cacheAge == null || (System.currentTimeMillis() - cacheAge) > SHOWCASE_REFRESH_TTL_MS
        if (stale) {
            val fresh: ShowcaseAchievementData? = when (
                val progress = refreshShowcaseProgress(vault.id, raGameId, steamAppId, raGameCacheRepository, steamGameCacheRepository)
            ) {
                is ShowcaseProgress.Ra -> progress.progress.toShowcaseData()
                is ShowcaseProgress.Steam -> progress.progress.toShowcaseData()
                null -> null
            }
            if (fresh != null && fresh.total > 0) {
                val hadCache = achievementData != null
                achievementData = fresh
                if (!hadCache) achievementAlpha.animateTo(1f, animationSpec = tween(durationMillis = 300))
            }
        }
    }

    // --- Pulsing diffuse glow for cover art (rune-like) ---
    val infiniteTransition = rememberInfiniteTransition(label = "artGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(showcaseFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (supportDialogOpen) return@onPreviewKeyEvent false  // the dialog owns its keys
                if (isOpening) return@onPreviewKeyEvent true
                val isDown = keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                if (!isDown) return@onPreviewKeyEvent false
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_SPACE -> {
                        startOpenTransition()
                        true
                    }
                    else -> {
                        onDismiss()
                        true
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { startOpenTransition() })
            }
    ) {
        val isLandscape = maxWidth > maxHeight

        // Blurred backdrop fills entire screen (small source — see backdropRequest).
        backdropRequest?.let { req ->
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
                    .alpha(0.45f),
            )
        }

        Column(Modifier.fillMaxSize()) {
            // --- Top bar: back button + marquee game name ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = accentColor,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDismiss() }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = vault.displayName,
                    color = accentColor,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            delayMillis = 1500
                        )
                )
                // Both orientations. The pin this replaced was landscape only,
                // to keep the portrait showcase minimal; the emblem is the only
                // place that asks for support while the showcase covers the
                // library, so it is worth one mark beside the back arrow.
                ShowcaseEmblemButton(
                    accentColor = accentColor,
                    hapticFeedbackIntensity = hapticFeedbackIntensity,
                    onDialogVisibilityChange = { supportDialogOpen = it },
                )
            }

            // --- Body: art + optional achievement panel ---
            val hasAchievements = achievementData != null

            // Shared art modifier: gaussian outer-glow halo
            val artGlowModifier = Modifier
                .drawBehind {
                    val blurRadius = 40.dp.toPx()
                    val cornerR = 14.dp.toPx()
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            color = accentColor.copy(alpha = glowAlpha)
                        }
                        paint.asFrameworkPaint().maskFilter =
                            android.graphics.BlurMaskFilter(
                                blurRadius,
                                android.graphics.BlurMaskFilter.Blur.OUTER
                            )
                        canvas.drawRoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height,
                            radiusX = cornerR,
                            radiusY = cornerR,
                            paint = paint
                        )
                    }
                }
                .clip(RoundedCornerShape(10.dp))

            // Side-by-side is calibrated for portrait-ish or near-square cover art (3:4,
            // 4:3, 1:1, 8:7). Anything 3:2 or wider gets crushed into the 55%-width slot
            // with heavy letterboxing — at 3:1 the art ends up about a third of the slot's
            // height and looks tiny next to a tall achievements panel. Force stacked in
            // that case: art on top spanning full width, achievements below, regardless of
            // device orientation. Threshold 1.5 catches 16:10, 16:9, 2:1, Steam's 460×215
            // grid (2.14), and 3:1; portrait and square art keep the original layout.
            val isWideArt = (artworkAspect ?: 0f) >= 1.5f
            if (isLandscape && !isWideArt) {
                // Side-by-side: art left, achievements right
                Row(Modifier.fillMaxSize()) {
                    // Art section
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(if (hasAchievements) 0.55f else 1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        val (artW, artH) = showcaseArtSize(
                            slotWidth = maxWidth - 48.dp, slotHeight = maxHeight - 32.dp,
                            aspect = artworkAspect, nativePx = artworkPx,
                            density = LocalDensity.current.density,
                        )
                        artworkRequest?.let { req ->
                            AsyncImage(
                                model = req,
                                contentDescription = vault.displayName,
                                contentScale = ContentScale.Fit,
                                onState = {
                                    if (it is coil.compose.AsyncImagePainter.State.Success) {
                                        contentReadySignaled = true
                                        onContentReady()
                                    }
                                },
                                modifier = Modifier
                                    // An explicit size, from the slot and the art's own
                                    // pixels; see showcaseArtSize for why neither the
                                    // decoded size nor a fill would do. The node is the
                                    // shape of the picture, so the glow hugs the art.
                                    .size(artW, artH)
                                    .alpha(if (isOpening) 0.4f else 1f)
                                    .then(artGlowModifier),
                            )
                        }
                        // No artwork assigned \u2014 invite the user to open instead of
                        // leaving the full-art slot blank.
                        if (!vault.hasArtwork && !isOpening) {
                            Text(
                                text = "Tap to Open",
                                color = accentColor,
                                fontSize = 22.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                        }
                        if (isOpening) {
                            Text("Opening\u2026", color = accentColor, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        }
                    }
                    // Achievement panel (landscape)
                    if (hasAchievements) {
                        ShowcaseAchievementPanel(
                            data = achievementData!!,
                            accentColor = accentColor,
                            richPresence = richPresence,

                            richPresenceIcon = richPresenceIcon,
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight()
                                .alpha(achievementAlpha.value)
                                .padding(end = 16.dp, top = 8.dp, bottom = 16.dp)
                        )
                    }
                }
            } else {
                // Stacked: art top, achievements bottom
                Column(Modifier.fillMaxSize()) {
                    // Art section
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(if (hasAchievements) 0.6f else 1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val (artW, artH) = showcaseArtSize(
                            slotWidth = maxWidth - 64.dp, slotHeight = maxHeight - 32.dp,
                            aspect = artworkAspect, nativePx = artworkPx,
                            density = LocalDensity.current.density,
                        )
                        artworkRequest?.let { req ->
                            AsyncImage(
                                model = req,
                                contentDescription = vault.displayName,
                                contentScale = ContentScale.Fit,
                                onState = {
                                    if (it is coil.compose.AsyncImagePainter.State.Success) {
                                        contentReadySignaled = true
                                        onContentReady()
                                    }
                                },
                                modifier = Modifier
                                    // Same as the side-by-side branch.
                                    .size(artW, artH)
                                    .alpha(if (isOpening) 0.4f else 1f)
                                    .then(artGlowModifier),
                            )
                        }
                        // No artwork assigned \u2014 invite the user to open instead of
                        // leaving the full-art slot blank.
                        if (!vault.hasArtwork && !isOpening) {
                            Text(
                                text = "Tap to Open",
                                color = accentColor,
                                fontSize = 22.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                        }
                        if (isOpening) {
                            Text("Opening\u2026", color = accentColor, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        }
                    }
                    // Achievement panel (portrait)
                    if (hasAchievements) {
                        ShowcaseAchievementPanel(
                            data = achievementData!!,
                            accentColor = accentColor,
                            richPresence = richPresence,

                            richPresenceIcon = richPresenceIcon,
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxWidth()
                                .alpha(achievementAlpha.value)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowcaseAchievementPanel(
    data: ShowcaseAchievementData,
    accentColor: Color,
    richPresence: String? = null,
    richPresenceIcon: String? = null,
    modifier: Modifier = Modifier
) {
    val isRa = data.hardcoreUnlocked != null

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- "Now playing" card: RA rich-presence + game icon, above the bar.
        // Shown only when the detected game is the RA last-played game (caller-gated).
        if (richPresence != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    // Borderless translucent fill matching the progress-bar panel
                    // below, so the two stacked panels read as a matched pair.
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title + RA logo removed to save space; the rich-presence text
                // gets the whole box.
                Text(
                    text = richPresence,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // --- Progress bar ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(6.dp)
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${data.unlocked} / ${data.total}",
                    // Same white family as the rich-presence line; the gold
                    // percentage stays the single accent. 0.6 read as muted/
                    // mismatched, so lift it to clearly-intentional secondary.
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )
                if (isRa) {
                    // When soft count == hard count the two percentages are identical
                    // (set earned in hardcore) — collapse to just the gold hard value.
                    if (data.unlocked == data.hardcoreUnlocked) {
                        Text(text = data.hardcorePercentText ?: data.percentText, color = ShowcaseGold, fontSize = 11.sp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = data.percentText, color = ShowcaseSilver, fontSize = 11.sp)
                            Text(text = data.hardcorePercentText ?: "", color = ShowcaseGold, fontSize = 11.sp)
                        }
                    }
                } else {
                    Text(
                        text = data.percentText,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
            // Progress bar track + glowing award knob at the end.
            // RA: gold = mastered/completed, silver = beaten (mirrors the badge).
            // Steam: accent knob once every achievement is unlocked.
            val raAward = if (isRa) LibraryAchievementCalc.award(
                data.awardKind,
                if (data.total > 0) (data.hardcoreUnlocked ?: 0).toFloat() / data.total else 0f
            ) else AchievementAward.NONE
            val steamComplete = !isRa && data.total > 0 && data.unlocked >= data.total
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                ) {
                    if (isRa) {
                        // Dual bar: silver (softcore) layer, gold (hardcore) layer on top — gradient sheen.
                        val softFraction = if (data.total > 0) data.unlocked.toFloat() / data.total else 0f
                        val hardFraction = if (data.total > 0) (data.hardcoreUnlocked ?: 0).toFloat() / data.total else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(softFraction.coerceIn(0f, 1f))
                                .background(achievementBarBrush(mastered = false), RoundedCornerShape(3.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(hardFraction.coerceIn(0f, 1f))
                                .background(achievementBarBrush(mastered = true), RoundedCornerShape(3.dp))
                        )
                    } else {
                        // Single bar with accent gradient (Steam)
                        val fraction = if (data.total > 0) data.unlocked.toFloat() / data.total else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(accentBarBrush(accentColor), RoundedCornerShape(3.dp))
                        )
                    }
                }
                if (raAward != AchievementAward.NONE) {
                    GlowingAwardCircle(
                        size = 13.dp,
                        mastered = raAward == AchievementAward.GOLD,
                        filled = LibraryAchievementCalc.awardFilled(data.awardKind),
                    )
                } else if (steamComplete) {
                    GlowingAwardCircle(
                        size = 13.dp,
                        base = accentColor,
                        bright = lerp(accentColor, Color.White, 0.30f),
                        deep = lerp(accentColor, Color.Black, 0.18f),
                    )
                }
            }
        }

        // --- Badge grid: auto-size to fit ALL badges ---
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val badgeCount = data.badges.size
            // if, not `return@BoxWithConstraints`: badgeCount flips with the loaded achievement
            // data, and a composable branch that changes shape between frames is exactly what
            // broke the AI tab. Safe here (BoxWithConstraints's content lambda is not inlined,
            // so the compiler closes its groups on every exit path) but not worth relying on.
            if (badgeCount > 0) {
            val gapDp = 3.dp
            val gapPx = with(LocalDensity.current) { gapDp.toPx() }
            val availableWidthPx = constraints.maxWidth.toFloat()
            val availableHeightPx = constraints.maxHeight.toFloat()
            // Find the column count that fits all badges: try increasing columns
            // until all badges fit in the available area.
            var bestCols = 4
            for (cols in 4..20) {
                val cellPx = (availableWidthPx - gapPx * (cols - 1)) / cols
                if (cellPx < 16f) break // too small
                val rows = (badgeCount + cols - 1) / cols // ceil division
                val totalHeight = rows * cellPx + (rows - 1) * gapPx
                bestCols = cols
                if (totalHeight <= availableHeightPx) break
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(bestCols),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(gapDp),
                verticalArrangement = Arrangement.spacedBy(gapDp),
                userScrollEnabled = false
            ) {
                items(badgeCount, key = { it }) { index ->
                    val badge = data.badges[index]
                    val badgeModifier = Modifier
                        .aspectRatio(1f)
                        .then(
                            if (badge.isUnlocked)
                                Modifier.border(1.dp, accentColor, RoundedCornerShape(3.dp))
                            else
                                Modifier
                        )
                        .alpha(if (badge.isUnlocked) 1f else 0.35f)
                        .background(
                            Color.White.copy(alpha = 0.05f),
                            RoundedCornerShape(3.dp)
                        )
                        .clip(RoundedCornerShape(3.dp))

                    if (badge.imageUrl != null) {
                        AsyncImage(
                            // Badges render tiny; cap decode at 96px and skip the
                            // crossfade — N badges fading in at once on entrance is the
                            // main residual mount cost.
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(badge.imageUrl)
                                .size(96)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = badgeModifier
                        )
                    } else {
                        Box(modifier = badgeModifier)
                    }
                }
            }
            } // badgeCount > 0
        }
    }
}

/**
 * Horizontal carousel display for vaults. Built on a [HorizontalPager] (like the launcher
 * vault row) so touch swipes fling with momentum across multiple items and snap to one,
 * while d-pad steps one item and focus snaps in lockstep. The pager is the single source of
 * truth for position; its settled page drives the ViewModel's focusedVaultId. Finite —
 * stops at the ends (no wrap).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCarousel(
    vaults: List<Vault>,
    focusedVaultId: String?,
    displayFocusColor: FocusColor,
    displayAccentColorRaw: Color,
    accentColorProvider: () -> Color,
    onVaultClick: (Vault) -> Unit,
    onVaultDoubleTap: ((Vault) -> Unit)? = null,
    onVaultLongPress: (Vault) -> Unit,
    onFocusChange: (String?) -> Unit,
    // Silent focus set for the touch-settle path (the scroll ticks already gave feedback, so no
    // FOCUS "landing" sound). D-pad keeps using onFocusChange with its sound.
    onScrollSettleFocus: (String) -> Unit,
    artDisplayMode: com.twinlore.app.data.ArtDisplayMode,
    topMenuFocusRequester: androidx.compose.ui.focus.FocusRequester,
    requestFocusTrigger: Int,
    modifier: Modifier = Modifier,
    blurArtworkInvalidateTimestamp: Pair<String, Long>? = null,
    gridAspectRatio: Float = com.twinlore.app.data.GridDisplayRatio.RATIO_3_4.aspectRatio,
    hapticFeedbackIntensity: com.twinlore.app.data.HapticFeedbackIntensity = com.twinlore.app.data.HapticFeedbackIntensity.MEDIUM,
    ledEnabled: Boolean = true,
    ledBrightness: Int = 127,
    ledSaturationBoost: Float = 0.75f,
    ledTurnOffTime: com.twinlore.app.data.LedTurnOffTime = com.twinlore.app.data.LedTurnOffTime.SEC_15,
    showLibraryItemNames: Boolean = false,
    isDarkTheme: Boolean = false,
    powerSaverMode: Boolean = false
) {
    if (vaults.isEmpty()) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val focusColorFallback = displayFocusColor.color
    val carouselResourceResolver = remember(context) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                context.applicationContext,
                com.twinlore.app.di.ResourceResolverEntryPoint::class.java
            )
            .resourceResolver()
    }

    // Card scale: fit a 238dp reference card within the screen minus the top menu bar.
    val refTotalCarouselWidthDp = 522f / 1.2f
    val horizontalPaddingDp = 32f
    val topMenuReservedDp = 64f
    val focusedCardBaseWidth = 238f
    val focusedCard3DScale = 1.1f
    val refCardHeightAtScale1Dp = (focusedCardBaseWidth / gridAspectRatio) * focusedCard3DScale
    val minScale = 0.5f
    val maxScale = 1.3f * 1.2f
    val scaleFromWidth = (configuration.screenWidthDp - horizontalPaddingDp) / refTotalCarouselWidthDp
    val scaleFromHeight = (configuration.screenHeightDp - topMenuReservedDp) / refCardHeightAtScale1Dp
    val scale = (kotlin.math.min(scaleFromWidth, scaleFromHeight)).coerceIn(minScale, maxScale)

    // The pager owns the rendered position. focusedVaultId is a settle-driven follower.
    // carouselTargetPage shadows the intended page so rapid d-pad presses always read the
    // correct origin regardless of scroll-animation timing (mirrors the launcher vault row).
    val initialPage = remember(vaults.size) {
        vaults.indexOfFirst { it.id == focusedVaultId }.takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { vaults.size }
    // Seeded from the pager, NOT from initialPage: rememberPagerState is saveable, so on a
    // return from a vault session the pager restores its old page while initialPage is
    // recomputed from focusedVaultId — which can be stale at that instant (the deferred
    // last-opened write has not landed yet). Seeding the target from focus while the pager
    // restored a different page made the two reconciliation effects below fight each other in
    // an endless focus/scroll loop (observed live: a ~50ms ping-pong until the user dragged).
    // The pager is the one that owns the rendered position, so the target starts wherever it
    // actually is; the external-sync effect then moves both together if focus disagrees.
    var carouselTargetPage by remember { mutableIntStateOf(pagerState.currentPage) }
    // True while a scrollToPage issued by the external-sync effect below is settling. Its
    // settle must not write focus back: the pager was following focus, so "correcting" focus
    // from the page would undo a newer focus value with a one-frame-stale one — the second
    // half of the loop described above.
    var carouselExternalScroll by remember { mutableStateOf(false) }
    // True from finger-down until the settle effect consumes it: "this settle ends a touch
    // gesture". Unlike carouselTouchArmed (cleared by an independent collector the moment
    // scrolling stops — racy to read at settle time), only the settle effect clears this, so
    // it reliably distinguishes a fling's settle from a spontaneous one. The pager settles on
    // its own when the vault list re-sorts under it (its id-keyed items follow the moved key),
    // and adopting THAT settle wrote focus from a page/list pair mid-shift.
    var carouselTouchScroll by remember { mutableStateOf(false) }
    // The settle effect is keyed on settledPage, so its lambda captures the vaults parameter
    // from the composition it last restarted in — stale by exactly one re-sort. Read through
    // this instead, so a settle that fires during a re-sort maps pages to the current list.
    val liveCarouselVaults by rememberUpdatedState(vaults)
    // True while a d-pad scroll animation is still travelling to carouselTargetPage. The pager
    // reports a settle whenever isScrollInProgress dips, including in the gap where one
    // animateScrollToPage has been cancelled and its replacement has not yet acquired the
    // scroll mutex — so that flag cannot tell an intermediate settle from a touch fling's.
    // Only the d-pad sets this, which is what makes it a reliable signal.
    var carouselDpadSettling by remember { mutableStateOf(false) }
    var carouselHasFocus by remember { mutableStateOf(false) }
    // Momentum fling: a fast swipe travels up to 10 items before snapping (default is 1).
    // Lower snapPositionalThreshold (default 0.5) so a short drag past ~28% of a card commits
    // to the next item instead of springing back — single-item moves feel light, not resistant.
    val carouselFling = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(10),
        snapPositionalThreshold = 0.28f
    )
    val itemFocusRequesters = remember(vaults.size) {
        List(vaults.size) { androidx.compose.ui.focus.FocusRequester() }
    }

    // Preload artwork around the landed item so the next step is warm. Same 768x1024 decode
    // size as the carousel card so the focused bitmap is reused without a flash.
    val settledPageForPreload = pagerState.settledPage
    val imageLoader = LocalImageLoader.current
    LaunchedEffect(settledPageForPreload, vaults.size, artDisplayMode) {
        if (vaults.isNotEmpty()) {
            val indicesToPreload = (settledPageForPreload - 2..settledPageForPreload + 2)
                .filter { it in vaults.indices }
            indicesToPreload.forEach { index ->
                val vault = vaults[index]
                val rid = vault.artworkResourceId
                if (vault.hasArtwork && rid != null) {
                    try {
                        val data = withContext(Dispatchers.IO) {
                            val uri = carouselResourceResolver.resolve(rid) ?: return@withContext null
                            ImagePathUtils.toImageData(uri.toString(), vault.id)
                        }
                        if (data != null) {
                            imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(data)
                                    .memoryCacheKey("art_${vault.id}_${rid}_0")
                                    .size(coil.size.Size(768, 1024))
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .crossfade(false)
                                    .allowHardware(true)
                                    .build()
                            )
                        }
                    } catch (e: Exception) {
                        // Ignore preload errors
                    }
                }
            }
        }
    }

    // Pager settled (touch fling or d-pad) -> reconcile ViewModel focus + Compose focus.
    // Writes the VM only when the settled vault differs, so it can't feed back into the
    // external-sync effect below.
    // A settle only counts when it agrees with intent. Two quick d-pad presses means the
    // second animateScrollToPage cancels the first, isScrollInProgress can drop, and the
    // pager reports a settle on the page in between — refocusing that is the snap-back the
    // user sees. carouselTargetPage is set by the d-pad handler on key-down, so it is
    // already correct here.
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        val agreed = settled == carouselTargetPage
        // A settle produced by the external-sync effect's own scrollToPage: the pager was
        // brought to where focus already is, so there is nothing to write back — and writing
        // would re-arm the loop this flag exists to break. Consume the flag and stand down.
        val externallyScrolled = carouselExternalScroll
        carouselExternalScroll = false
        val touchScrolled = carouselTouchScroll
        carouselTouchScroll = false
        if (agreed) {
            if (!externallyScrolled) {
                liveCarouselVaults.getOrNull(settled)?.let { v ->
                    // Touch settles only (the d-pad pre-sets focus): set it silently so no
                    // lower-pitch FOCUS sound plays after a scroll stops.
                    if (v.id != focusedVaultId) onScrollSettleFocus(v.id)
                }
            }
        } else {
            // A touch fling has no prior intent, so adopt the page it actually landed on
            // rather than dragging it back to where the last d-pad press left off. ONLY a
            // touch fling, though: the pager also settles on its own when the vault list
            // re-sorts under it, and adopting that settle committed a page/list pair caught
            // mid-shift. Without a finger involved, focus is authoritative and the external
            // -sync effect re-aligns the pager to it.
            if (!carouselDpadSettling && !externallyScrolled && touchScrolled) {
                carouselTargetPage = settled
                liveCarouselVaults.getOrNull(settled)?.let { v ->
                    if (v.id != focusedVaultId) onScrollSettleFocus(v.id)
                }
            }
        }
        if (LibraryFocusRules.shouldRefocusOnSettle(settled, carouselTargetPage, carouselHasFocus)) {
            try { itemFocusRequesters.getOrNull(settled)?.requestFocus() } catch (_: Exception) {}
        }
    }

    // External focusedVaultId change (search, add/delete, restore) -> scroll the pager to it.
    // Internally-driven changes (d-pad, touch settle) set carouselTargetPage to the new index
    // *before* touching focusedVaultId, so `idx == carouselTargetPage` means the pager is
    // already animating there — skip, or an instant scrollToPage would fight the animation.
    // Keyed on the vault LIST, not just its size: a last-opened re-sort keeps size and focus
    // identical while every index shifts, and with size as the key the pager silently kept
    // rendering the old page over the reordered list.
    LaunchedEffect(focusedVaultId, vaults) {
        val idx = vaults.indexOfFirst { it.id == focusedVaultId }.takeIf { it >= 0 } ?: return@LaunchedEffect
        if (idx == carouselTargetPage) return@LaunchedEffect
        carouselTargetPage = idx
        carouselExternalScroll = true
        try { pagerState.scrollToPage(idx) } catch (_: Exception) {}
    }

    // Give the CENTRED card key focus on first show — deliberately not the focused vault's
    // card: the two can disagree transiently right after a return from a vault session (the
    // composition-time focus is stale until the restore lands), and requestFocus on an
    // off-screen pager card drags the pager over via bring-into-view — an animation the
    // reconciliation effects above know nothing about. Two of those competing was observed
    // to strand the pager between two pages. The centred card is always on screen, so this
    // never scrolls; if focus disagrees, the external-sync effect realigns the pager and the
    // settle's own refocus lands on the right card afterwards.
    LaunchedEffect(vaults.size) {
        kotlinx.coroutines.delay(80)
        try { itemFocusRequesters.getOrNull(pagerState.settledPage)?.requestFocus() } catch (_: Exception) {}
    }

    // D-pad DOWN from the top menu routes focus back into the carousel.
    LaunchedEffect(requestFocusTrigger) {
        if (requestFocusTrigger > 0) {
            kotlinx.coroutines.delay(16)
            val idx = vaults.indexOfFirst { it.id == focusedVaultId }.takeIf { it >= 0 } ?: 0
            try { itemFocusRequesters.getOrNull(idx)?.requestFocus() } catch (_: Exception) {
                kotlinx.coroutines.delay(80)
                try { itemFocusRequesters.getOrNull(idx)?.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    // Decode dims for the card artwork (wide ratios need a larger source; matches the launcher).
    val (carouselDecodeWidth, carouselDecodeHeight) = if (gridAspectRatio >= 1.5f) {
        val w = 960
        val h = (w / gridAspectRatio).toInt().coerceIn(160, 1280)
        w to h
    } else {
        256 to 341
    }

    // Per-item scroll tick while the user drags/flings the carousel (same soft tick the list
    // uses). Armed on finger-down so d-pad — which already plays the FOCUS sound via
    // onFocusChange — never double-ticks.
    var carouselTouchArmed by remember { mutableStateOf(false) }
    var carouselFingerDown by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        var last = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }.collect { p ->
            if (p != last) {
                if (carouselTouchArmed) {
                    FeedbackManager.playSound(context, FeedbackEvent.SCROLL_TICK, FeedbackManager.soundEnabled)
                }
                last = p
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && !carouselFingerDown) carouselTouchArmed = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    carouselFingerDown = true
                    carouselTouchArmed = true
                    carouselTouchScroll = true
                    val downPage = pagerState.currentPage
                    do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                    carouselFingerDown = false
                    // A pure tap produces no settle to consume the touch flag, and a stale
                    // flag would let the next spontaneous (re-sort) settle pass as a fling.
                    if (!pagerState.isScrollInProgress && pagerState.currentPage == downPage) {
                        carouselTouchScroll = false
                    }
                }
            }
            .onFocusChanged { carouselHasFocus = it.hasFocus }
            // Intercept d-pad before any focused card sees it. Uses carouselTargetPage
            // (updated immediately on each press) so the next press reads the right origin,
            // and moves Compose focus in lockstep so pressing A before the scroll settles
            // opens the targeted card, not a stale one.
            .onPreviewKeyEvent { keyEvent ->
                val isDown = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN
                val isUp = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP
                if (!(isDown || isUp)) return@onPreviewKeyEvent false
                when (keyEvent.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isDown) {
                            val current = carouselTargetPage
                            if (current > 0) {
                                val prev = current - 1
                                carouselTargetPage = prev
                                onFocusChange(vaults[prev].id)
                                try { itemFocusRequesters.getOrNull(prev)?.requestFocus() } catch (_: Exception) {}
                                carouselDpadSettling = true
                                coroutineScope.launch {
                                    try { pagerState.animateScrollToPage(prev) } finally {
                                        // An interrupted animation's finally runs while a newer press is already in
                                        // flight, and carouselTargetPage has moved on by then — so only the last press
                                        // of a burst clears the flag.
                                        if (carouselTargetPage == prev) carouselDpadSettling = false
                                    }
                                }
                            }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isDown) {
                            val current = carouselTargetPage
                            if (current < vaults.size - 1) {
                                val next = current + 1
                                carouselTargetPage = next
                                onFocusChange(vaults[next].id)
                                try { itemFocusRequesters.getOrNull(next)?.requestFocus() } catch (_: Exception) {}
                                carouselDpadSettling = true
                                coroutineScope.launch {
                                    try { pagerState.animateScrollToPage(next) } finally {
                                        // An interrupted animation's finally runs while a newer press is already in
                                        // flight, and carouselTargetPage has moved on by then — so only the last press
                                        // of a burst clears the flag.
                                        if (carouselTargetPage == next) carouselDpadSettling = false
                                    }
                                }
                            }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        if (isDown) {
                            try { topMenuFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // No content below the library carousel — keep focus on the current card.
                        if (isDown) {
                            try { itemFocusRequesters.getOrNull(carouselTargetPage)?.requestFocus() } catch (_: Exception) {}
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val pageWidth = (238.dp * scale).coerceAtMost(maxWidth - 32.dp)
        // Center the current page; neighbors peek symmetrically (the "bigger launcher" look).
        val sidePad = ((maxWidth - pageWidth) / 2).coerceAtLeast(0.dp)

        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(pageWidth),
            pageSpacing = 16.dp * scale,
            contentPadding = PaddingValues(horizontal = sidePad),
            beyondBoundsPageCount = 2,
            flingBehavior = carouselFling,
            key = { page ->
                vaults.getOrNull(page)?.let { "carousel_${it.id}" } ?: "carousel_ph_$page"
            },
            // Fill the carousel area so each full-height page gives a tall horizontal-swipe band.
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val vault = vaults.getOrNull(page) ?: return@HorizontalPager
            // The card is marked only while focus is genuinely inside the carousel.
            // focusedVaultId alone is ViewModel state, so the card used to stay lit while the
            // top bar held real focus — two rings on screen at once. The backdrop, adaptive
            // accent and LED keep reading focusedVaultId, so the top bar still shows the
            // focused vault's artwork and colour behind the carousel.
            val isFocused = carouselHasFocus && vault.id == focusedVaultId
            // Complement the current selection with a continuous size/brightness curve that
            // tracks the scroll: the centered card is full-size and bright, neighbors shrink
            // and dim toward the edges. Read the pager offset inside graphicsLayer so it
            // re-evaluates in the layer phase every frame without recomposing the page.
            val pageScaleModifier = Modifier.graphicsLayer {
                val pageOffset = kotlin.math.abs(
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).coerceIn(0f, 1f)
                val nearness = 1f - pageOffset  // 1 at dead-center, 0 at a full page away
                val sc = 0.82f + (1f - 0.82f) * nearness
                scaleX = sc
                scaleY = sc
                alpha = 0.6f + (1f - 0.6f) * nearness
            }
            // Let each page fill the carousel's full height so horizontal swipes anywhere
            // in the vertical band — not just directly on the cover — drive the carousel
            // (the pager's drag area is otherwise only as tall as its pages). The card stays
            // centered, so nothing moves visually; only the invisible scroll band grows.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                VaultGridItem(
                    vault = vault,
                    onClick = { onVaultClick(vault) },
                    onDoubleTap = if (vault.hasLaunchData && onVaultDoubleTap != null) {{ onVaultDoubleTap(vault) }} else null,
                    onLongPress = { onVaultLongPress(vault) },
                    isSettledFocus = isFocused,
                    isViewModelFocused = isFocused,
                    borderColorWhenSettled = if (isFocused) accentColorProvider else null,
                    focusColor = if (isFocused) accentColorProvider else { { focusColorFallback } },
                    settledAccentColor = if (isFocused) displayAccentColorRaw else focusColorFallback,
                    hapticFeedbackIntensity = hapticFeedbackIntensity,
                    ledEnabled = ledEnabled,
                    ledBrightness = ledBrightness,
                    ledSaturationBoost = ledSaturationBoost,
                    ledTurnOffTime = ledTurnOffTime,
                    focusRequester = itemFocusRequesters.getOrNull(page) ?: remember { androidx.compose.ui.focus.FocusRequester() },
                    onFocusRequested = { onFocusChange(vault.id) },
                    artDisplayMode = artDisplayMode,
                    gridAspectRatio = gridAspectRatio,
                    decodeWidth = carouselDecodeWidth,
                    decodeHeight = carouselDecodeHeight,
                    blurArtworkInvalidateTimestamp = blurArtworkInvalidateTimestamp,
                    showLibraryItemNames = showLibraryItemNames,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.width(pageWidth).then(pageScaleModifier)
                )
            }
        }
    }
}

// Shared chrome bar transitions, hoisted to file scope so they are not re-allocated as
// locals inside the (very large) LibraryScreen composable — that function sits near the
// Dalvik 256-register ceiling and inline transitions push it over (VerifyError at load).
// Only the bottom pair survives: the top bar is now a lazy item, not an animated overlay.
private val ChromeBarEnterBottom = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn()
private val ChromeBarExitBottom = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut()

/** Radial accent glow behind the blurred backdrop (carousel/list/launcher only). Extracted
 *  from LibraryScreen for register relief. */
@Composable
private fun LibraryNeonGlow(vibrantColor: Color?, powerSaverMode: Boolean, isGridMode: Boolean) {
    if (powerSaverMode || vibrantColor == null || isGridMode) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        vibrantColor.copy(alpha = 0.32f),
                        vibrantColor.copy(alpha = 0.20f),
                        vibrantColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    radius = 2000f
                )
            )
    )
}

/** Two-layer blurred wallpaper background (incoming opaque + outgoing fading). Extracted
 *  from LibraryScreen for register relief. */
@Composable
private fun LibraryBlurBackground(
    displayedBlurReq: ImageRequest?,
    outgoingBlurReq: ImageRequest?,
    outgoingBlurAlphaProvider: () -> Float,
    powerSaverMode: Boolean,
    hasArtwork: Boolean,
    placeholder: androidx.compose.ui.graphics.painter.Painter,
    // Premium-style grade for the wallpaper (Noir muted wash / Adventure warm amber), or
    // null for the default full-colour backdrop. Static render-time filter, no idle cost.
    backdropColorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
) {
    if (powerSaverMode || !hasArtwork) return
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        displayedBlurReq?.let { req ->
            AsyncImage(
                model = req,
                contentDescription = null,
                // Decorative full-screen blur: nobody can interact with these pixels, but
                // Coil's AsyncImage still contributes an applySemantics node per image even
                // with contentDescription = null. Collapse it to nothing.
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
                contentScale = ContentScale.FillBounds,
                colorFilter = backdropColorFilter,
                placeholder = placeholder,
                error = placeholder
            )
        }
        outgoingBlurReq?.let { req ->
            AsyncImage(
                model = req,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = outgoingBlurAlphaProvider() }
                    .clearAndSetSemantics {},
                contentScale = ContentScale.FillBounds,
                colorFilter = backdropColorFilter,
                placeholder = placeholder,
                error = placeholder
            )
        }
    }
}

/**
 * Static "app wallpaper" background for [LibraryBackgroundStyle.WALLPAPER] (round two,
 * task 2). Unlike [LibraryBlurBackground] this is not keyed to the focused vault -- it is
 * one user-picked image ([com.twinlore.app.data.SettingsRepository.appWallpaperImage], set
 * in Settings), so there is no per-selection resolve, blur, or
 * cross-dissolve: Coil loads it once and keeps it cached like any other image, and it stays on
 * screen unchanged as focus moves.
 */
@Composable
internal fun LibraryWallpaperBackground(
    model: Any?,
    /** 0f-1f. Its own setting, independent of the blurred-art style's treatment. */
    dim: Float,
    backdropColorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
) {
    if (model == null) return
    val palette = LocalThemePalette.current
    AsyncImage(
        model = model,
        contentDescription = null,
        // The app's loader, NOT Coil's singleton. coil-compose's no-loader AsyncImage overload
        // resolves to the singleton, which this app never configures — so it has no
        // ImageDecoderDecoder and an animated GIF or WebP wallpaper decodes to its first frame
        // and sits there. The animated decoder is registered on LocalImageLoader in MainActivity,
        // which is why the same picture animates elsewhere and froze here.
        imageLoader = LocalImageLoader.current,
        modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
        // Fit, not FillBounds: the wallpaper is whatever aspect ratio the user picked, and
        // stretching it to the screen distorts faces and straight lines. Fit shows the whole
        // picture and letterboxes onto the theme background behind it — chosen over Crop so
        // nothing the user picked gets cut off.
        contentScale = ContentScale.Fit,
        colorFilter = backdropColorFilter,
    )
    if (dim > 0f) {
        // A wash toward the THEME background, not toward black. Black works on a dark theme and
        // ruins a light one: the launcher and grid draw palette.foreground text over this, so a
        // light theme needs the wash to lighten. This is the only full-screen wash the wallpaper
        // style applies — the grid's item-name scrim and the launcher's readability scrim both
        // stand down for it, so this slider is the whole story wherever the wallpaper shows.
        Box(Modifier.fillMaxSize().background(palette.background.copy(alpha = dim.coerceIn(0f, 1f))))
    }
}

/**
 * The library's top bar as the first row of the scrolling page. Always composed, never
 * animated: it scrolls out of view with the content and comes back when the list is at the
 * top, which is also what makes it reachable by pressing up from the first row of tiles.
 *
 * Replaces the old BoxScope + AnimatedVisibility version, which was driven by a
 * scroll-direction flag that revealed the bar on any upward delta.
 */
@Composable
private fun LibraryPageTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCreateVault: () -> Unit,
    onSettings: () -> Unit,
    onJourney: () -> Unit,
    onRefreshBoxArt: () -> Unit,
    onSearchClick: () -> Unit,
    onInputReceived: () -> Unit,
    onTopBarFocusChanged: (Boolean) -> Unit,
    isScraping: Boolean,
    firstButtonFocusRequester: FocusRequester,
    addButtonFocusRequester: FocusRequester,
    accentColorOverride: Color,
    canFocusGrid: Boolean,
    onDpadDownToGrid: () -> Unit,
) {
    LibraryTopMenuBar(
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onCreateVault = onCreateVault,
        onSettings = onSettings,
        onJourney = onJourney,
        onRefreshBoxArt = onRefreshBoxArt,
        onSearchClick = onSearchClick,
        onInputReceived = onInputReceived,
        onTopBarFocusChanged = onTopBarFocusChanged,
        isScraping = isScraping,
        firstButtonFocusRequester = firstButtonFocusRequester,
        addButtonFocusRequester = addButtonFocusRequester,
        accentColorOverride = accentColorOverride,
        leadingContent = {
            // No fold button here. The library is the one screen with nothing
            // to fold away from: no guide, no map, and no game running beside
            // it. The vault session and the guide browser carry it, which is
            // where it gets used.
            LibraryStatusIndicators()
        },
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { keyEvent ->
                val isDown = keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                val isUp = keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP
                if (isDown || isUp) {
                    if (isDown) onInputReceived()
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (canFocusGrid) {
                                if (isDown) onDpadDownToGrid()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    )
}

/** Resolved blur layers for the library wallpaper background. */
private class LibraryBlurLayers(
    val displayed: ImageRequest?,
    val outgoing: ImageRequest?,
    val outgoingAlpha: () -> Float,
)

/** Owns the off-main blur-request resolve + two-layer cross-dissolve state. Extracted from
 *  LibraryScreen (3 MutableState + Animatable + 2 LaunchedEffects) for register relief. */
@Composable
private fun rememberLibraryBlurLayers(
    focusedVault: Vault?,
    blurArtworkInvalidateTimestamp: Pair<String, Long>?,
    // BLURRED_ART only (round two, task 2): when the library background style isn't
    // drawing per-game blurred art (SOLID or WALLPAPER), there is nothing for this to
    // resolve. Kept as a parameter -- rather than skipping the call at the composition
    // level -- so the resolve is gated *inside* the effect, before any IO runs, and
    // flipping the style back to BLURRED_ART resumes resolving the current focus.
    enabled: Boolean = true,
): LibraryBlurLayers {
    val context = LocalContext.current
    val resolver = remember(context) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                context.applicationContext,
                com.twinlore.app.di.ResourceResolverEntryPoint::class.java
            )
            .resourceResolver()
    }
    var curBlurRequest by remember { mutableStateOf<ImageRequest?>(null) }
    val blurBusterTs = if (blurArtworkInvalidateTimestamp?.first == focusedVault?.id) blurArtworkInvalidateTimestamp?.second ?: 0L else 0L
    LaunchedEffect(focusedVault?.id, focusedVault?.artworkResourceId, blurBusterTs, enabled) {
        if (!enabled) {
            curBlurRequest = null
            return@LaunchedEffect
        }
        val v = focusedVault
        val rid = v?.artworkResourceId
        if (v == null || rid == null) {
            curBlurRequest = null
            return@LaunchedEffect
        }
        val buster = if (blurArtworkInvalidateTimestamp?.first == v.id) blurArtworkInvalidateTimestamp!!.second else 0L
        val resolveKey = "${v.id}_${rid}_$buster"
        val data = blurResolvedDataCache[resolveKey] ?: withContext(Dispatchers.IO) {
            val uri = resolver.resolve(rid) ?: return@withContext null
            ImagePathUtils.toImageData(uri.toString(), v.id)
        }?.also { blurResolvedDataCache[resolveKey] = it }
        curBlurRequest = if (data == null) null
        else ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey("blur_cur_${v.id}_${rid}_${buster}_b24")
            .size(coil.size.Size(270, 480))
            .transformations(WallpaperBlurTransformation(radius = 24))
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .crossfade(350)
            .build()
    }
    var displayedBlurReq by remember { mutableStateOf<ImageRequest?>(null) }
    var outgoingBlurReq by remember { mutableStateOf<ImageRequest?>(null) }
    val outgoingBlurAlpha = remember { Animatable(0f) }
    LaunchedEffect(curBlurRequest) {
        val next = curBlurRequest
        val prev = displayedBlurReq
        if (prev != null && next != null && prev != next) {
            outgoingBlurReq = prev
            outgoingBlurAlpha.snapTo(1f)
            displayedBlurReq = next
            kotlinx.coroutines.delay(16)
            outgoingBlurAlpha.animateTo(0f, animationSpec = tween(400))
            if (outgoingBlurReq == prev) outgoingBlurReq = null
        } else {
            displayedBlurReq = next
        }
    }
    return LibraryBlurLayers(displayedBlurReq, outgoingBlurReq, { outgoingBlurAlpha.value })
}

/** Post-refresh vault showcase overlay. Extracted from LibraryScreen (2 collectAsState +
 *  several closures) for register relief; builds its callbacks internally. */
@Composable
private fun LibraryShowcaseOverlay(
    vault: Vault?,
    accentColor: Color,
    hapticFeedbackIntensity: HapticFeedbackIntensity,
    libraryViewModel: LibraryViewModel,
    mainViewModel: MainViewModel,
    onDismissShowcase: () -> Unit,
    onContentReady: () -> Unit,
) {
    val context = LocalContext.current
    val resolver = remember(context) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                context.applicationContext,
                com.twinlore.app.di.ResourceResolverEntryPoint::class.java
            )
            .resourceResolver()
    }
    VaultShowcaseOverlay(
        vault = vault,
        accentColor = accentColor,
        hapticFeedbackIntensity = hapticFeedbackIntensity,
        resourceResolver = resolver,
        raGameCacheRepository = libraryViewModel.raGameCacheRepository,
        steamGameCacheRepository = libraryViewModel.steamGameCacheRepository,
        raProfileRepository = libraryViewModel.raProfileRepository,
        onOpen = {
            val v = vault ?: run { onDismissShowcase(); return@VaultShowcaseOverlay }
            libraryViewModel.clearJustFocusedByDetection()
            mainViewModel.openVault(v.id)
            libraryViewModel.updateLastOpenedDeferred(v.id)
        },
        onDismiss = {
            libraryViewModel.clearJustFocusedByDetection()
            onDismissShowcase()
        },
        onContentReady = onContentReady,
    )
}

/** Carousel/empty-state overlay top bar (translucent gradient, no scroll-hide). Extracted
 *  for register relief. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.LibraryOverlayTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCreateVault: () -> Unit,
    onSettings: () -> Unit,
    onJourney: () -> Unit,
    onRefreshBoxArt: () -> Unit,
    onSearchClick: () -> Unit,
    onInputReceived: () -> Unit,
    onTopBarFocusChanged: (Boolean) -> Unit,
    isScraping: Boolean,
    firstButtonFocusRequester: FocusRequester,
    addButtonFocusRequester: FocusRequester,
    accentColorOverride: Color,
    canFocusGrid: Boolean,
    onDpadDownToGrid: () -> Unit,
) {
    LibraryTopMenuBar(
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onCreateVault = onCreateVault,
        onSettings = onSettings,
        onJourney = onJourney,
        onRefreshBoxArt = onRefreshBoxArt,
        onSearchClick = onSearchClick,
        onInputReceived = onInputReceived,
        onTopBarFocusChanged = onTopBarFocusChanged,
        isScraping = isScraping,
        firstButtonFocusRequester = firstButtonFocusRequester,
        addButtonFocusRequester = addButtonFocusRequester,
        accentColorOverride = accentColorOverride,
        leadingContent = {
            // No fold button here. The library is the one screen with nothing
            // to fold away from: no guide, no map, and no game running beside
            // it. The vault session and the guide browser carry it, which is
            // where it gets used.
            LibraryStatusIndicators()
        },
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .onPreviewKeyEvent { keyEvent ->
                val isDown = keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN
                val isUp = keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP
                if (isDown || isUp) {
                    if (isDown) onInputReceived()
                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (canFocusGrid) {
                            if (isDown) onDpadDownToGrid()
                            true
                        } else false
                    } else false
                } else false
            }
    )
}

/** Bottom controller-hint bar, always visible with the content. Extracted for register relief. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.LibraryBottomHintBar(
    visible: Boolean,
    libraryViewMode: LibraryViewMode,
    librarySortField: LibrarySortField,
    librarySortDirection: SortDirection,
    isScraping: Boolean,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onOpen: () -> Unit,
    onViewModeSelected: (LibraryViewMode) -> Unit,
    onSortFieldSelected: (LibrarySortField) -> Unit,
    onSortDirectionToggle: () -> Unit,
    buttonGlyphStyle: com.twinlore.app.data.ButtonGlyphStyle,
    swapFaceButtons: Boolean,
    showBackToTop: Boolean = false,
    onBackToTop: () -> Unit = {},
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = ChromeBarEnterBottom,
        exit = ChromeBarExitBottom,
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
    ) {
        ControllerHintBar(
            libraryViewMode = libraryViewMode,
            librarySortField = librarySortField,
            librarySortDirection = librarySortDirection,
            isScraping = isScraping,
            onSettings = onSettings,
            onRefresh = onRefresh,
            onEdit = onEdit,
            onOpen = onOpen,
            onViewModeSelected = onViewModeSelected,
            onSortFieldSelected = onSortFieldSelected,
            onSortDirectionToggle = onSortDirectionToggle,
            buttonGlyphStyle = buttonGlyphStyle,
            swapFaceButtons = swapFaceButtons,
            showBackToTop = showBackToTop,
            onBackToTop = onBackToTop,
        )
    }
}

/**
 * Whole grid/list mode (scrollable grid, whose first row is the top bar). Extracted
 * from LibraryScreen so the dense concurrent argument set of LibraryGrid + LibraryPageTopBar —
 * the function's register PEAK — lives here. Builds its callbacks internally so the call
 * site passes only values + a few setter lambdas.
 */
@Composable
private fun LibraryGridMode(
    sortedVaults: List<Vault>,
    achievementInfoById: Map<String, AchievementSortInfo>,
    showAchievementBars: Boolean,
    focusedVaultId: String?,
    displayFocusColor: FocusColor,
    displayAccentColorRaw: Color,
    accentColorProvider: () -> Color,
    scrapingVaultIds: Set<String>,
    detectionTransitionInProgress: Boolean,
    focusSoundEnabled: Boolean,
    hapticFeedbackIntensity: com.twinlore.app.data.HapticFeedbackIntensity,
    artDisplayMode: com.twinlore.app.data.ArtDisplayMode,
    columns: Int,
    topMenuFocusRequester: FocusRequester,
    requestFocusTrigger: Int,
    isTopBarFocused: Boolean,
    blurArtworkInvalidateTimestamp: Pair<String, Long>?,
    ledEnabled: Boolean,
    ledBrightness: Int,
    ledSaturationBoost: Float,
    ledTurnOffTime: com.twinlore.app.data.LedTurnOffTime,
    gridAspectRatio: Float,
    showLibraryItemNames: Boolean,
    isDarkTheme: Boolean,
    focusColorFallback: Color,
    searchQuery: String,
    isScraping: Boolean,
    addButtonFocusRequester: FocusRequester,
    canFocusGrid: Boolean,
    libraryViewModel: LibraryViewModel,
    mainViewModel: MainViewModel,
    onSelectVaultForContextMenu: (Vault) -> Unit,
    onRefreshTransition: () -> Unit,
    onShowCreateVault: () -> Unit,
    onShowSearch: () -> Unit,
    onRequestGridFocus: () -> Unit,
    triggerLibraryRefresh: () -> Unit,
    /** Hoisted in LibraryScreen, keyed on the grid-versus-launcher boundary (not the full view
     *  mode) so the bottom hint bar's back-to-top pill can drive it. Grid sizes deliberately
     *  share one position so L1 does not lose the user's place. See LibraryScreen's
     *  `libraryScrollState`. */
    gridState: LazyGridState,
) {
    val context = LocalContext.current
    val view = LocalView.current
    LibraryGrid(
        vaults = sortedVaults,
        achievementInfoById = achievementInfoById,
        showAchievementBars = showAchievementBars,
        focusedVaultId = focusedVaultId,
        onFocusChange = { vaultId ->
            if (!detectionTransitionInProgress) {
                FeedbackManager.playSound(context, FeedbackEvent.FOCUS, focusSoundEnabled)
                view.performHapticFeedback(hapticFeedbackIntensity)
            }
            libraryViewModel.setFocusedVault(vaultId)
        },
        displayFocusColor = displayFocusColor,
        displayAccentColorRaw = displayAccentColorRaw,
        accentColorProvider = accentColorProvider,
        vaultIdsBeingScraped = scrapingVaultIds,
        onVaultClick = { vault ->
            libraryViewModel.onInputReceived()
            mainViewModel.openVault(vault.id)
            libraryViewModel.updateLastOpenedDeferred(vault.id)
        },
        onVaultDoubleTap = null,
        onVaultLongPress = { vault ->
            libraryViewModel.onInputReceived()
            onSelectVaultForContextMenu(vault)
        },
        artDisplayMode = artDisplayMode,
        columns = columns,
        topMenuFocusRequester = topMenuFocusRequester,
        requestFocusTrigger = requestFocusTrigger,
        suppressAutoFocusToGrid = isTopBarFocused,
        topBarHasFocus = isTopBarFocused,
        onInputReceived = { if (!detectionTransitionInProgress) libraryViewModel.onInputReceived() },
        onRefresh = onRefreshTransition,
        blurArtworkInvalidateTimestamp = blurArtworkInvalidateTimestamp,
        modifier = Modifier.fillMaxSize(),
        hapticFeedbackIntensity = hapticFeedbackIntensity,
        ledEnabled = ledEnabled,
        ledBrightness = ledBrightness,
        ledSaturationBoost = ledSaturationBoost,
        ledTurnOffTime = ledTurnOffTime,
        gridAspectRatio = gridAspectRatio,
        showLibraryItemNames = showLibraryItemNames,
        isDarkTheme = isDarkTheme,
        focusColorFallback = focusColorFallback,
        focusSoundEnabled = focusSoundEnabled,
        libraryViewModel = libraryViewModel,
        gridState = gridState,
        topBar = {
            LibraryPageTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    libraryViewModel.updateSearchQuery(it)
                    libraryViewModel.onInputReceived()
                },
                onCreateVault = onShowCreateVault,
                onSettings = { mainViewModel.navigateToSettings() },
                onJourney = { mainViewModel.navigateToJourney() },
                onRefreshBoxArt = triggerLibraryRefresh,
                onSearchClick = onShowSearch,
                onInputReceived = { libraryViewModel.onInputReceived() },
                onTopBarFocusChanged = { focused -> libraryViewModel.setTopBarFocused(focused) },
                isScraping = isScraping,
                firstButtonFocusRequester = topMenuFocusRequester,
                addButtonFocusRequester = addButtonFocusRequester,
                accentColorOverride = displayAccentColorRaw,
                canFocusGrid = canFocusGrid,
                // In wallpaper mode the user's dim is the only wash, so the bar must not add the
                // item-names one on top of it (the library background stands down for the same
                // reason). SOLID stands down too: flat backdrop, no page scrim to match.
                onDpadDownToGrid = onRequestGridFocus,
            )
        },
    )
}

/**
 * Scroll [index] into view after a D-pad move. In LIST mode ([centreInList] = true) the row is
 * smoothly animated to the vertical centre of the viewport so it lines up with the peak of the
 * size/brightness curve; multi-column grids keep the instant top-align behaviour.
 */
private suspend fun scrollFocusIntoView(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    index: Int,
    centreInList: Boolean
) {
    if (!centreInList) {
        try { gridState.scrollToItem(index) } catch (_: Exception) {}
        return
    }
    // Bring it on-screen first if it's far off (top-aligned), then centre by the exact delta —
    // measuring live layout avoids content-padding math and works regardless of item height.
    if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
        try { gridState.scrollToItem(index) } catch (_: Exception) {}
    }
    val info = gridState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCentre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val itemCentre = item.offset.y + item.size.height / 2f
    try { gridState.animateScrollBy(itemCentre - viewportCentre) } catch (_: Exception) {}
}

/**
 * Grid display for vaults in the library. Supports D-pad navigation with wrap,
 * focus boundary only on focused item, and focus from top menu (D-pad Down).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LibraryGrid(
    vaults: List<Vault>,
    focusedVaultId: String?,
    onFocusChange: (String?) -> Unit,
    displayFocusColor: FocusColor,
    displayAccentColorRaw: Color,
    accentColorProvider: () -> Color,
    vaultIdsBeingScraped: Set<String>,
    onVaultClick: (Vault) -> Unit,
    onVaultDoubleTap: ((Vault) -> Unit)? = null,
    onVaultLongPress: (Vault) -> Unit,
    artDisplayMode: com.twinlore.app.data.ArtDisplayMode,
    columns: Int,
    topMenuFocusRequester: FocusRequester,
    requestFocusTrigger: Int,
    suppressAutoFocusToGrid: Boolean,
    /**
     * True while the top bar holds Compose focus. Tiles drop their focus mark then, so the bar's
     * own focus ring is the only one on screen. Shares a source with [suppressAutoFocusToGrid]
     * (both are the library's isTopBarFocused) but means a different thing: that one routes
     * focus, this one only decides what is painted.
     */
    topBarHasFocus: Boolean,
    onInputReceived: () -> Unit,
    onRefresh: () -> Unit = {},
    blurArtworkInvalidateTimestamp: Pair<String, Long>?,
    modifier: Modifier = Modifier,
    hapticFeedbackIntensity: com.twinlore.app.data.HapticFeedbackIntensity = com.twinlore.app.data.HapticFeedbackIntensity.MEDIUM,
    ledEnabled: Boolean = true,
    ledBrightness: Int = 127,
    ledSaturationBoost: Float = 0.75f,
    ledTurnOffTime: com.twinlore.app.data.LedTurnOffTime = com.twinlore.app.data.LedTurnOffTime.SEC_15,
    gridAspectRatio: Float = com.twinlore.app.data.GridDisplayRatio.RATIO_3_4.aspectRatio,
    showLibraryItemNames: Boolean = false,
    isDarkTheme: Boolean = false,
    focusColorFallback: Color = displayFocusColor.color,
    focusSoundEnabled: Boolean = true,
    achievementInfoById: Map<String, AchievementSortInfo> = emptyMap(),
    showAchievementBars: Boolean = false,
    libraryViewModel: LibraryViewModel,
    /** Hoisted by LibraryScreen so the bottom hint bar's back-to-top pill can read and drive
     *  the scroll position. Keyed there on the grid-versus-launcher boundary, not the full
     *  view mode: grid sizes deliberately share one position so L1 does not lose the user's
     *  place. */
    gridState: LazyGridState,
    /** Rendered as the grid's first, full-span item. See LibraryPageTopBar. */
    topBar: @Composable () -> Unit,
) {
    if (vaults.isEmpty()) return
    val n = vaults.size
    val cols = columns.coerceIn(1, 9)
    val focusRequesters = remember(n) { List(n) { FocusRequester() } }
    val coroutineScope = rememberCoroutineScope()

    // The index the user deliberately moved to. Written by the d-pad handlers below on
    // key-down, and by external focus changes. Incidental focus callbacks — a neighbour
    // Compose's fallback search grabbed while the previously focused item was scrolled out
    // and disposed — are dropped against it. Null until something claims the grid.
    var intendedIndex by remember(n) { mutableStateOf<Int?>(null) }

    // True from finger-down on the LIST until its centring snap completes. Declared up here,
    // ahead of the external-focus effect below, because that effect must stand down while a
    // touch scroll owns the list; written by the touch-scroll machinery further down.
    var userScrollActive by remember { mutableStateOf(false) }

    // LIST-mode safety cap: at cols=1 the item is `width × width/ratio`. With portrait
    // ratios (2:3, 3:4) on landscape phones the derived height balloons past the screen
    // — a single row eats the entire viewport. Cap the width so the derived height stays
    // ≤ half the available screen height, which guarantees at least 2 rows are always
    // visible (minus the small margin for spacing + chrome). Wider ratios never trigger
    // this — for them width/ratio is already smaller than screen height.
    // Reads LocalConfiguration.screenHeightDp (cheap, no layout impact); BoxWithConstraints
    // would be more precise but adds a layout pass we don't need for a guard rail.
    val listItemMaxWidth: androidx.compose.ui.unit.Dp? = if (cols == 1) {
        val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
        // -16dp keeps a small breathing margin between the two visible items + chrome
        // (the top bar row + ControllerHintBarReservedHeight are larger than this in
        // practice, but the cap doesn't need to be tight — it just has to prevent the
        // 1-item-fills-screen failure mode).
        ((screenHeightDp / 2) - 16.dp).coerceAtLeast(80.dp) * gridAspectRatio
    } else null

    // Settled focus: wait for accent color animation to finish before showing border. A single
    // write, not a null-then-id pair: writing null first used to hide every visible tile's
    // border for the 140ms gap, which meant this state invalidated every tile TWICE per
    // keypress (round two, task 3 -- each write re-triggers the whole grid's accessibility
    // semantics diff, the single largest main-thread cost in the navigation profile). Safe to
    // drop now that the border colour is a draw-phase read (round one): the previously-settled
    // tile keeps painting `settledBorderColor()` -- the live, still-tweening accent -- right up
    // until this state flips to the new id, so nothing ever paints a *stale* colour. Verified by
    // eye: no flash of the old tile's colour, no gap where the border disappears.
    var settledFocusedVaultId by remember { mutableStateOf<String?>(focusedVaultId) }
    var focusSettledGeneration by remember { mutableStateOf(0L) }
    LaunchedEffect(focusedVaultId) {
        kotlinx.coroutines.delay(140) // matches 120ms color animation + small buffer
        settledFocusedVaultId = focusedVaultId
        focusSettledGeneration++
    }

    // Vault index -> LazyGrid item index and back. The top bar is item 0, so every scroll
    // target, visibleItemsInfo read and prefetch window has to convert. Both directions live
    // here so that one assumption is editable in one place; a negative result from the inverse
    // means the item is the top bar, i.e. not a vault at all.
    fun gridItemIndexForVault(vaultIndex: Int): Int = vaultIndex + 1
    fun vaultIndexForGridItem(itemIndex: Int): Int = itemIndex - 1

    // The last requestFocusTrigger this grid acted on. A bump is the top bar explicitly handing
    // focus back on d-pad DOWN, which the suppress guard below must not swallow.
    var lastHandledFocusTrigger by remember { mutableIntStateOf(requestFocusTrigger) }

    // External focus changes only: search, detection, restoration from another screen, and
    // the top bar's d-pad-down. A d-pad move inside the grid already set intent and requested
    // focus itself in the handlers above, so `idx == intendedIndex` means there is nothing to
    // correct — reacting to it is what let a stale focusedVaultId drag the selection back.
    //
    // This replaces two overlapping effects that both watched focusedVaultId and both
    // scroll-and-refocused, racing each other and the handlers.
    LaunchedEffect(focusedVaultId, vaults.size, suppressAutoFocusToGrid, requestFocusTrigger) {
        if (focusedVaultId == null) return@LaunchedEffect
        // A focus change arriving while a LIST touch scroll (or its fling) is running is the
        // scroll's own live follow, not an external jump. Reacting to it here — scrollToItem
        // when the row is out of view, requestFocus (bring-into-view) always — grabbed the
        // scroll mid-fling and killed its momentum, so a fast flick sometimes advanced a
        // single row and sometimes flew as expected, depending on the race. The settle
        // handler centres and focuses once the gesture truly ends.
        if (userScrollActive) return@LaunchedEffect
        val idx = vaults.indexOfFirst { it.id == focusedVaultId }
        if (idx !in focusRequesters.indices) return@LaunchedEffect
        val internal = idx == intendedIndex
        if (!internal) {
            intendedIndex = idx
            // visibleItemsInfo is in ITEM space (the bar is item 0); idx is a vault index.
            val visibleIndices = gridState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
            if (gridItemIndexForVault(idx) !in visibleIndices) {
                try { gridState.scrollToItem(gridItemIndexForVault(idx)) } catch (_: Exception) {}
            }
        }
        // A requestFocusTrigger bump is the top bar explicitly handing focus back on d-pad DOWN.
        // suppressAutoFocusToGrid means "the bar has focus", which is true by definition at that
        // moment — honouring it here would swallow the handover and strand focus in the bar, since
        // the bar consumes the key so Compose's own focus search never runs. Ambient re-focus
        // (a focusedVaultId change while the user is working in the bar) must still be suppressed.
        val handoverFromTopBar = requestFocusTrigger != lastHandledFocusTrigger
        lastHandledFocusTrigger = requestFocusTrigger
        if (suppressAutoFocusToGrid && !handoverFromTopBar) return@LaunchedEffect
        // One retry: the cell attaches its requester a frame or more after a scroll composes it.
        kotlinx.coroutines.delay(16)
        try { focusRequesters[idx].requestFocus() } catch (_: Exception) {
            kotlinx.coroutines.delay(80)
            try { focusRequesters[idx].requestFocus() } catch (_: Exception) {}
        }
    }

    // Scroll-ahead artwork prefetch: for the rows just past the visible window, PREBUILD
    // the tile's ImageRequest (cached so the tile reads it synchronously — no placeholder/
    // IO-resolve frame) and warm Coil's bitmap. Same key/size the tiles use, so it's a real
    // hit. Runs off-main, only ~3 rows ahead, cache evicts naturally — gentle on CPU/memory
    // and scales to any library size, unlike a big upfront preload.
    val prefetchContext = LocalContext.current
    val prefetchLoader = LocalImageLoader.current
    val prefetchThumbnailCache = remember(prefetchContext) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                prefetchContext.applicationContext,
                com.twinlore.app.di.ArtworkThumbnailCacheEntryPoint::class.java
            )
            .artworkThumbnailCache()
    }
    val prefetchResolver = remember(prefetchContext) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(
                prefetchContext.applicationContext,
                com.twinlore.app.di.ResourceResolverEntryPoint::class.java
            )
            .resourceResolver()
    }

    fun nextIndexUp(index: Int): Int {
        return if (index < cols) {
            val lastRowStart = ((n - 1) / cols) * cols
            (lastRowStart + (index % cols)).coerceIn(0, n - 1)
        } else {
            (index - cols).coerceIn(0, n - 1)
        }
    }
    /** Down: move to row below; at bottom row do nothing (return current index). */
    fun nextIndexDown(index: Int): Int {
        return if (index + cols >= n) index else (index + cols).coerceIn(0, n - 1)
    }
    fun nextIndexLeft(index: Int): Int {
        val next = if (index % cols == 0) index + cols - 1 else index - 1
        return next.coerceIn(0, n - 1)
    }
    fun nextIndexRight(index: Int): Int {
        val next = if ((index + 1) % cols == 0) index - cols + 1 else index + 1
        return next.coerceIn(0, n - 1)
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) onInputReceived()
    }

    // ---- Scroll-driven focus (touch) ----------------------------------------------------
    // As you drag/fling, the item nearest the viewport centre in the focused COLUMN becomes
    // the selection — vertical scroll moves focus vertically, like nudging the controller.
    // (A vertical grid has no horizontal scroll axis, so there is no horizontal equivalent.)
    // Only USER touch scrolls drive this (tracked via `fingerDown`) so programmatic d-pad
    // scrolls, which centre + focus on their own, never double up. Focus changes continuously
    // (accent follows instantly; the border self-settles after 140ms; the blurred wallpaper is
    // debounced in the parent and the LED self-debounces — none thrash). The normal FOCUS sound
    // is muted on this path; a soft, throttled SCROLL_TICK fires per item instead.
    val gridContext = LocalContext.current
    // Live snapshots so the long-lived (gridState-keyed) collectors below never read stale
    // captures — vaults / focus change during a scroll, so they can't be closure-captured once.
    val liveVaults by rememberUpdatedState(vaults)
    val liveFocusedVaultId by rememberUpdatedState(focusedVaultId)
    val liveFocusRequesters by rememberUpdatedState(focusRequesters)
    val liveFocusSoundEnabled by rememberUpdatedState(focusSoundEnabled)
    val liveCols by rememberUpdatedState(cols)
    // (userScrollActive is declared above the external-focus effect, which reads it.)
    // Armed on finger-DOWN (before any scroll) so the "scroll started" edge can't be missed by
    // a snapshot-sampling race; touchAnchorIdx is the row we began on, for the low-threshold snap.
    var fingerDown by remember { mutableStateOf(false) }
    var touchScrollArmed by remember { mutableStateOf(false) }
    var touchAnchorIdx by remember { mutableIntStateOf(0) }
    // Distinguishes the settle of our own centering snap from the settle of a user fling, so a
    // second scroll started DURING the snap (contiguous, isScrollInProgress never drops) still
    // commits instead of being swallowed.
    var justSnapped by remember { mutableStateOf(false) }

    // Fires when the whole gesture is truly done: finger up AND the list not scrolling. Handles
    // drag+hold+release (no fling) and fling alike, and — because it's level-based, not edge-
    // based — never misses a contiguous scroll-during-snap. Keyed on cols so a view-mode switch
    // re-arms it. The centering snap runs async (coroutineScope.launch) so this collector never
    // blocks and can react immediately when the user grabs the list again.
    LaunchedEffect(gridState, cols) {
        snapshotFlow { !fingerDown && !gridState.isScrollInProgress }.collect { settled ->
            if (!settled || !touchScrollArmed || cols != 1) return@collect
            if (justSnapped) {
                // This settle is our own snap finishing — the gesture is fully done.
                justSnapped = false
                touchScrollArmed = false
                userScrollActive = false
                return@collect
            }
            // Commit to the row the drag was heading toward using a LOW positional threshold
            // (30% of a row, vs the 50% "nearest-centre" would need) so moving one item feels
            // light, not resistant — then snap it to exact centre.
            val info = gridState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) { touchScrollArmed = false; userScrollActive = false; return@collect }
            val vaultsNow = liveVaults
            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            // Item 0 is the top bar, which is neither a row nor a selectable target. Measure the
            // pitch off two consecutive TILE rows: with the bar on screen, visible[0] is the bar
            // and visible[1] - visible[0] is the bar's height, not a row's, which threw the
            // low-threshold commit below completely off.
            val tiles = visible.filter { vaultIndexForGridItem(it.index) >= 0 }
            if (tiles.isEmpty()) { touchScrollArmed = false; userScrollActive = false; return@collect }
            val pitch = if (tiles.size >= 2)
                (tiles[1].offset.y - tiles[0].offset.y).toFloat().coerceAtLeast(1f)
            else tiles[0].size.height.toFloat().coerceAtLeast(1f)
            val anchor = visible.firstOrNull { it.index == gridItemIndexForVault(touchAnchorIdx) }
            val target = if (anchor != null) {
                val moved = -((anchor.offset.y + anchor.size.height / 2f) - centre) / pitch
                val steps = if (moved >= 0f) kotlin.math.floor(moved + 0.70f).toInt()
                            else kotlin.math.ceil(moved - 0.70f).toInt()
                (touchAnchorIdx + steps).coerceIn(0, vaultsNow.size - 1)
            } else {
                // `tiles`, so a drag can never commit the selection to the top bar; and the
                // nearest item's index is in ITEM space, so convert it back to a vault index.
                tiles.minByOrNull { kotlin.math.abs((it.offset.y + it.size.height / 2f) - centre) }
                    ?.let { vaultIndexForGridItem(it.index) } ?: touchAnchorIdx
            }
            vaultsNow.getOrNull(target)?.let { v ->
                if (v.id != liveFocusedVaultId) {
                    libraryViewModel.setFocusedVault(v.id)
                    FeedbackManager.playSound(gridContext, FeedbackEvent.SCROLL_TICK, liveFocusSoundEnabled)
                }
                try { liveFocusRequesters.getOrNull(target)?.requestFocus() } catch (_: Exception) {}
            }
            val targetItem = gridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == gridItemIndexForVault(target) }
            val delta = targetItem?.let { (it.offset.y + it.size.height / 2f) - centre }
            if (targetItem != null && kotlin.math.abs(delta!!) > 2f) {
                justSnapped = true
                coroutineScope.launch { try { gridState.animateScrollBy(delta) } catch (_: Exception) {} }
            } else if (targetItem == null) {
                justSnapped = true
                coroutineScope.launch { try { gridState.animateScrollToItem(gridItemIndexForVault(target)) } catch (_: Exception) {} }
            } else {
                // Already centred — gesture done, nothing to animate.
                touchScrollArmed = false
                userScrollActive = false
            }
        }
    }

    // While a user scroll is active, follow the centred row live (border/accent track it). LIST
    // only — in multi-column grids, having the selection walk a column as you scroll feels wrong.
    LaunchedEffect(userScrollActive, cols) {
        if (!userScrollActive || cols != 1) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@snapshotFlow -1
            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            // Item space -> vault space. When the top bar is itself the nearest item this is
            // -1, which reads as "nothing to select" both here and in getOrNull below.
            vaultIndexForGridItem(
                info.visibleItemsInfo
                    .minByOrNull { kotlin.math.abs((it.offset.y + it.size.height / 2f) - centre) }
                    ?.index ?: 0
            )
        }.distinctUntilChanged().collect { idx ->
            if (idx >= 0) {
                liveVaults.getOrNull(idx)?.let { v ->
                    if (v.id != liveFocusedVaultId) {
                        libraryViewModel.setFocusedVault(v.id)
                        FeedbackManager.playSound(gridContext, FeedbackEvent.SCROLL_TICK, liveFocusSoundEnabled)
                    }
                }
            }
        }
    }

    // Measured, not assumed: gridDecodeSize turns the grid's real container width
    // into the decode size for every tile. The cold-start preload in LibraryScreen
    // runs the same helper against the window, and both snap to the same 32-px
    // bucket, so the two produce identical Coil memoryCacheKeys.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridSpacingPx = with(LocalDensity.current) { GRID_SPACING_DP.dp.roundToPx() }
        val gridDecode = gridDecodeSize(
            containerWidthPx = constraints.maxWidth,
            cols = cols,
            aspectRatio = gridAspectRatio,
            spacingPx = gridSpacingPx,
        )
        LaunchedEffect(gridDecode) { checkPreloadDecodeAgreement(gridDecode) }

        LaunchedEffect(vaults, cols, gridDecode) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { lastVisibleItem ->
                    if (lastVisibleItem < 0) return@collect
                    // Item space -> vault space: the top bar is item 0, so without this the
                    // window would start one vault late and skip the very next tile.
                    val lastVisible = vaultIndexForGridItem(lastVisibleItem)
                    val to = (lastVisible + cols * 3).coerceAtMost(vaults.lastIndex)
                    if (lastVisible + 1 > to) return@collect
                    withContext(Dispatchers.IO) {
                        for (i in (lastVisible + 1)..to) {
                            val v = vaults.getOrNull(i) ?: continue
                            val rid = v.artworkResourceId ?: continue
                            if (!v.hasArtwork) continue
                            runCatching {
                                prewarmGridArtwork(
                                    prefetchContext, prefetchLoader, prefetchResolver, v.id, rid,
                                    gridDecode.width, gridDecode.height, prefetchThumbnailCache,
                                )
                            }
                        }
                    }
                }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = gridState,
            // Nothing above item 0. Two things used to sit here and both are now wrong:
            //  - the status-bar inset, which the bar already applies itself via
            //    windowInsetsPadding(statusBars), so counting it here reserved it twice and left
            //    a visible empty band above the bar in portrait (invisible in landscape, where
            //    the status bar is hidden and the inset is zero);
            //  - a 4dp breathing gap, which dated from the bar being a pinned overlay. With the
            //    bar as item 0 that gap sits ABOVE it and shows the library background through as
            //    a thin bright line across the top. The carousel never had it because its bar is
            //    an overlay with no content padding above it. verticalArrangement already spaces
            //    the bar from the first row.
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp + ControllerHintBarReservedHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
                // Arm scroll-driven focus on finger-DOWN (so the scroll-start edge is never missed)
                // and record the anchor row. A pure tap (no move, no fling) disarms on release so a
                // later programmatic d-pad scroll isn't mistaken for a touch scroll. Observation
                // only — never consumes, so it coexists with the grid's own scroll gesture.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // Scroll-driven focus is LIST-only; don't arm in multi-column grids.
                        if (liveCols != 1) return@awaitEachGesture
                        val downIdx = gridState.firstVisibleItemIndex
                        val downOffset = gridState.firstVisibleItemScrollOffset
                        touchAnchorIdx = liveVaults.indexOfFirst { it.id == liveFocusedVaultId }.coerceAtLeast(0)
                        touchScrollArmed = true
                        userScrollActive = true
                        justSnapped = false   // a fresh user grab, even mid-snap, must commit
                        fingerDown = true
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        fingerDown = false
                        val moved = gridState.firstVisibleItemIndex != downIdx ||
                            gridState.firstVisibleItemScrollOffset != downOffset
                        // Pure tap (no scroll, no fling): disarm so a later d-pad scroll isn't
                        // mistaken for a touch scroll.
                        if (!moved && !gridState.isScrollInProgress) {
                            touchScrollArmed = false
                            userScrollActive = false
                        }
                    }
                }
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val bleed = 8.dp   // MUST equal this grid's contentPadding start/end
                Box(
                    Modifier.layout { measurable, constraints ->
                        // The bar is the page's first row but it is chrome, not content: its
                        // vertical gradient has to reach both screen edges, which means it must
                        // be measured at the full window width rather than the grid's inset
                        // content width. Measure past the grid's horizontal padding and shift
                        // back.
                        val extra = bleed.roundToPx() * 2
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = constraints.maxWidth + extra,
                                maxWidth = constraints.maxWidth + extra,
                            )
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.place(-bleed.roundToPx(), 0)
                        }
                    }
                ) { topBar() }
            }
            itemsIndexed(vaults, key = { _, v -> v.id }) { index, vault ->
                // The tile is marked only while focus is genuinely inside the grid, not while
                // the top bar holds it. focusedVaultId alone is ViewModel state, so the tile
                // used to stay lit while the bar held real focus — two rings on screen at once.
                // The backdrop, adaptive accent and LED keep reading focusedVaultId, so the
                // top bar still shows the focused vault's artwork and colour behind it.
                val isSettledFocus = !topBarHasFocus && vault.id == settledFocusedVaultId
                val isFocused = !topBarHasFocus && vault.id == focusedVaultId
                val achInfo = if (showAchievementBars) achievementInfoById[vault.id] else null
                // Use displayAccentColorRaw immediately when focused (not after settled) so no white/Enum flash.
                // Keep settled only for border visibility and scale animation.
                VaultGridItem(
                    vault = vault,
                    onClick = { onVaultClick(vault) },
                    onDoubleTap = if (vault.hasLaunchData && onVaultDoubleTap != null) {{ onVaultDoubleTap(vault) }} else null,
                    onLongPress = { onVaultLongPress(vault) },
                    focusId = "grid_${vault.id}",
                    focusManager = null,
                    focusRequester = focusRequesters[index],
                    artDisplayMode = artDisplayMode,
                    blurArtworkInvalidateTimestamp = blurArtworkInvalidateTimestamp,
                    onFocusRequested = {
                        // Rule 3: believe this only when it agrees with where the user meant
                        // to be. A disposal-driven fallback focus reports a neighbour, and
                        // writing that through is what pulled the selection backwards.
                        if (LibraryFocusRules.acceptsFocusReport(intendedIndex, index)) {
                            intendedIndex = index
                            onFocusChange(vault.id)
                        }
                    },
                    isSettledFocus = isSettledFocus,
                    isViewModelFocused = isFocused,
                    focusSettledGeneration = focusSettledGeneration,
                    borderColorWhenSettled = if (isSettledFocus) accentColorProvider else null,
                    focusColor = if (isFocused || isSettledFocus) accentColorProvider else { { focusColorFallback } },
                    settledAccentColor = if (isFocused || isSettledFocus) displayAccentColorRaw else focusColorFallback,
                    hapticFeedbackIntensity = hapticFeedbackIntensity,
                    ledEnabled = ledEnabled,
                    ledBrightness = ledBrightness,
                    ledSaturationBoost = ledSaturationBoost,
                    ledTurnOffTime = ledTurnOffTime,
                    gridAspectRatio = gridAspectRatio,
                    showLibraryItemNames = showLibraryItemNames,
                    isDarkTheme = isDarkTheme,
                    achievementSoftcore = achInfo?.softcoreFraction,
                    achievementHardcore = achInfo?.hardcoreFraction,
                    achievementAwardKind = achInfo?.raAwardKind,
                    decodeWidth = gridDecode.width,
                    decodeHeight = gridDecode.height,
                    onButtonX = null, // No action on X button in library
                    onDpadUp = {
                        onInputReceived()
                        if (index < cols) {
                            // Move focus directly to top menu — no clearFocus needed since
                            // onPreviewKeyEvent consumes both ACTION_DOWN and ACTION_UP.
                            // The bar is item 0 of a lazy grid, so it is not composed while the
                            // first tile row is at the top of the viewport and its requester is
                            // unattached. Scroll it in first; requesting focus before the scroll
                            // would throw and be swallowed, leaving the key consumed and focus
                            // stuck on the tile forever.
                            coroutineScope.launch {
                                gridState.animateScrollToItem(0)
                                repeat(6) {
                                    kotlinx.coroutines.delay(30)
                                    try { topMenuFocusRequester.requestFocus(); return@launch } catch (_: Exception) {}
                                }
                            }
                        } else {
                            val next = nextIndexUp(index)
                            if (next in focusRequesters.indices && next != index) {
                                // Intent and focus first, scroll second. Requesting focus after
                                // the scroll animation left a window in which the old item was
                                // disposed mid-flight, Compose released focus, and its fallback
                                // search handed it to a neighbour that then reported itself.
                                intendedIndex = next
                                onFocusChange(vaults[next].id)
                                try { focusRequesters[next].requestFocus() } catch (_: Exception) {}
                                coroutineScope.launch {
                                    scrollFocusIntoView(gridState, gridItemIndexForVault(next), cols == 1)
                                }
                            }
                        }
                    },
                    onDpadDown = {
                        val next = nextIndexDown(index)
                        if (next in focusRequesters.indices && next != index) {
                            intendedIndex = next
                            onFocusChange(vaults[next].id)
                            try { focusRequesters[next].requestFocus() } catch (_: Exception) {}
                            coroutineScope.launch {
                                scrollFocusIntoView(gridState, gridItemIndexForVault(next), cols == 1)
                            }
                        }
                    },
                    onDpadLeft = {
                        val next = nextIndexLeft(index)
                        if (next in focusRequesters.indices && next != index) {
                            intendedIndex = next
                            onFocusChange(vaults[next].id)
                            try { focusRequesters[next].requestFocus() } catch (_: Exception) {}
                        }
                    },
                    onDpadRight = {
                        val next = nextIndexRight(index)
                        if (next in focusRequesters.indices && next != index) {
                            intendedIndex = next
                            onFocusChange(vaults[next].id)
                            try { focusRequesters[next].requestFocus() } catch (_: Exception) {}
                        }
                    },
                    // LIST mode caps the width (and therefore the derived item height) so
                    // at least 2 rows fit on screen; `wrapContentWidth(CenterHorizontally)`
                    // centers the now-narrower item in the cell so portrait-ratio art at
                    // landscape orientations doesn't hug the left edge. Non-LIST stays at
                    // a vanilla fillMaxWidth — multi-column grids tile naturally.
                    modifier = if (listItemMaxWidth != null) {
                        Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = listItemMaxWidth)
                            // Complement the selection with a continuous size/brightness curve that
                            // tracks the scroll (same feel as the carousel, on the vertical axis):
                            // the row nearest the viewport centre is full-size and bright, rows
                            // above/below shrink and dim by their distance from centre. Read in the
                            // layer phase so it re-evaluates every scroll frame without recomposing.
                            .graphicsLayer {
                                val info = gridState.layoutInfo
                                val item = info.visibleItemsInfo
                                    .firstOrNull { it.index == gridItemIndexForVault(index) }
                                if (item != null) {
                                    val viewportCentre =
                                        (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                    val itemCentre = item.offset.y + item.size.height / 2f
                                    val half = ((info.viewportEndOffset - info.viewportStartOffset) / 2f)
                                        .coerceAtLeast(1f)
                                    val frac = (kotlin.math.abs(itemCentre - viewportCentre) / half)
                                        .coerceIn(0f, 1f)
                                    val nearness = 1f - frac
                                    val sc = 0.84f + (1f - 0.84f) * nearness
                                    scaleX = sc
                                    scaleY = sc
                                    alpha = 0.6f + (1f - 0.6f) * nearness
                                }
                            }
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
            }
        }
    }
}

/**
 * Launcher mode content: status bar, vault carousel, pinned apps, app grid.
 * Rendered when libraryViewMode == LAUNCHER inside the library's blur background.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LauncherModeContent(
    focusedVaultId: String?,
    displayAccentColorRaw: Color,
    accentColorProvider: () -> Color,
    focusColorFallback: Color,
    hapticFeedbackIntensity: HapticFeedbackIntensity,
    artDisplayMode: com.twinlore.app.data.ArtDisplayMode,
    gridAspectRatio: Float,
    showLibraryItemNames: Boolean = false,
    sortedVaults: List<Vault> = emptyList(),
    achievementInfoById: Map<String, AchievementSortInfo> = emptyMap(),
    showAchievementBars: Boolean = false,
    onVaultLongPress: (Vault) -> Unit,
    onFocusChange: (String?) -> Unit,
    onSearchClick: () -> Unit,
    triggerLibraryRefresh: () -> Unit,
    focusedAppPackage: String?,
    onFocusedAppPackageChange: (String?) -> Unit,
    contextMenuRequestedTrigger: Int,
    mainViewModel: MainViewModel,
    libraryViewModel: LibraryViewModel,
    palette: com.twinlore.app.data.ThemePalette,
    isDarkTheme: Boolean,
    onNeedsRomsFolder: (Vault) -> Unit = {},
    /**
     * The library wallpaper, when that style is showing one (style-gated at the call site).
     * Non-null makes the launcher's readability scrim stand down: the user's own wallpaper
     * dim is then the only wash. See [launcherScrim].
     */
    wallpaperBackdrop: Any? = null,
    /** Hoisted in LibraryScreen, keyed on the grid-versus-launcher boundary (not the full view
     *  mode) so the bottom hint bar's back-to-top pill can drive it. Grid sizes deliberately
     *  share one position so L1 does not lose the user's place. See LibraryScreen's
     *  `libraryScrollState`. */
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val launcherViewModel: LauncherViewModel = hiltViewModel()
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val currentDisplayId = view.display?.displayId ?: Display.DEFAULT_DISPLAY

    val nonFavoriteApps by launcherViewModel.nonFavoriteApps.collectAsState()
    val favoriteApps by launcherViewModel.favoriteApps.collectAsState()
    val emulatorApps by launcherViewModel.emulatorApps.collectAsState()
    // Launcher vault strip reflects the library sort option (full sorted library, not just recent).
    val launcherVaults = sortedVaults
    val isLoadingApps by launcherViewModel.isLoadingApps.collectAsState()
    val appsCollapsed by launcherViewModel.appsSectionCollapsed.collectAsState()

    // Multi-select mode for bulk Pin / Unpin across all launcher sections.
    var selectionMode by remember { mutableStateOf(false) }
    val selectedPackages = remember { mutableStateListOf<String>() }
    fun exitSelection() {
        selectionMode = false
        selectedPackages.clear()
    }
    fun toggleSelected(pkg: String) {
        if (!selectedPackages.remove(pkg)) selectedPackages.add(pkg)
    }
    BackHandler(enabled = selectionMode) { exitSelection() }

    val favoriteFocusRequesters = remember(favoriteApps) {
        favoriteApps.associate { it.packageName to androidx.compose.ui.focus.FocusRequester() }
    }
    val regularFocusRequesters = remember(nonFavoriteApps) {
        nonFavoriteApps.associate { it.packageName to androidx.compose.ui.focus.FocusRequester() }
    }
    val emulatorFocusRequesters = remember(emulatorApps) {
        emulatorApps.associate { it.app.packageName to androidx.compose.ui.focus.FocusRequester() }
    }

    // One menu at a time, one owner, one restore path. Per-cell `contextMenuExpanded`
    // booleans had no way to give focus back: a DropdownMenu is a Popup with its own window,
    // so opening one pulls focus off the cell, and on dismiss Compose fell back to the grid's
    // first focusable — the top-left cell. focusedAppPackage was null by then too, which
    // disabled the X and A controller routes and made the view feel dead until it was rebuilt.
    var contextMenuPackage by remember { mutableStateOf<String?>(null) }
    var contextMenuSection by remember { mutableStateOf(LibraryFocusRules.AppSection.ALL_APPS) }
    var contextMenuIndex by remember { mutableIntStateOf(0) }

    fun openAppContextMenu(pkg: String, section: LibraryFocusRules.AppSection, index: Int) {
        contextMenuPackage = pkg
        contextMenuSection = section
        contextMenuIndex = index
    }

    // Resolve the requester at restore time rather than holding one: Pin/Unpin rebuilds both
    // maps (they are remembered off the app lists) and moves the app between sections, so any
    // requester captured when the menu opened is already dead. The restore coroutine below
    // outlives the composition that launched it and its closure would capture the pre-Pin maps
    // for the same reason, so it reads them through rememberUpdatedState instead.
    val liveFavoriteRequesters by rememberUpdatedState(favoriteFocusRequesters)
    val liveEmulatorRequesters by rememberUpdatedState(emulatorFocusRequesters)
    val liveRegularRequesters by rememberUpdatedState(regularFocusRequesters)
    fun requesterFor(pkg: String): androidx.compose.ui.focus.FocusRequester? =
        liveFavoriteRequesters[pkg] ?: liveEmulatorRequesters[pkg] ?: liveRegularRequesters[pkg]

    fun closeAppContextMenu() {
        val pkg = contextMenuPackage ?: return
        val section = contextMenuSection
        val index = contextMenuIndex
        contextMenuPackage = null
        coroutineScope.launch {
            val target = LibraryFocusRules.restoreTargetAfterMenu(
                closedPackage = pkg,
                sectionAtOpen = section,
                indexAtOpen = index,
                pinned = favoriteApps.map { it.packageName },
                emulators = emulatorApps.map { it.app.packageName },
                allApps = nonFavoriteApps.map { it.packageName },
            ) ?: return@launch
            // Scroll the target's cell into view before focusing it: Pin moves an app from
            // wherever the user was in All apps up to the pinned section at the top of the page,
            // Unpin drops it the other way, a lazy grid disposes off-screen cells, and an
            // unattached FocusRequester throws. The scroll is what makes the requester
            // attachable at all.
            //
            // The section is resolved INSIDE the loop, not once up front, because Pin/Unpin is a
            // fire-and-forget Room write feeding these lists through stateIn while this coroutine
            // runs on the UI dispatcher within the same frame. Up front it would read the
            // PRE-move lists and scroll to where the cell used to be, while requesterFor — which
            // reads the rebuilt maps live — would by then resolve the cell's NEW requester in the
            // section we never scrolled to, so every retry would throw. Recomputing per attempt
            // makes the emission timing irrelevant: whichever iteration first sees the post-move
            // lists scrolls to the right place, and the next one's requestFocus succeeds.
            //
            // The retries also cover the Popup's window teardown, which takes a frame or more.
            var lastScrolledTo = -1
            repeat(12) {
                kotlinx.coroutines.delay(50)
                val pinnedIdx = favoriteApps.indexOfFirst { it.packageName == target }
                val emulatorIdx = emulatorApps.indexOfFirst { it.app.packageName == target }
                val regularIdx = nonFavoriteApps.indexOfFirst { it.packageName == target }
                val targetSection = when {
                    pinnedIdx >= 0 -> LibraryFocusRules.AppSection.PINNED to pinnedIdx
                    emulatorIdx >= 0 -> LibraryFocusRules.AppSection.EMULATORS to emulatorIdx
                    regularIdx >= 0 -> LibraryFocusRules.AppSection.ALL_APPS to regularIdx
                    else -> null
                }
                if (targetSection != null) {
                    val itemIndex = LibraryFocusRules.appItemIndex(
                        section = targetSection.first,
                        indexInSection = targetSection.second,
                        hasVaults = launcherVaults.isNotEmpty(),
                        pinnedCount = favoriteApps.size,
                        emulatorCount = emulatorApps.size,
                    )
                    // Only when it moved: re-issuing the same scroll every 50ms would fight the
                    // frame the cell needs to compose and attach.
                    if (itemIndex != lastScrolledTo) {
                        lastScrolledTo = itemIndex
                        try { gridState.scrollToItem(itemIndex) } catch (_: Exception) {}
                    }
                }
                val requester = requesterFor(target) ?: return@repeat
                try {
                    requester.requestFocus()
                    onFocusedAppPackageChange(target)
                    return@launch
                } catch (_: Exception) {}
            }
        }
    }

    // The controller's Edit button arrives as a counter bump from LibraryScreen. Handle it once,
    // here, for whichever app currently has focus. It used to be broadcast to every cell as a
    // per-cell prop gated on the focused package, which meant a focus move flipped the newly
    // focused cell's prop from 0 to the live counter — a fresh effect key that opened its menu
    // unbidden. Owning it in one place removes that whole class of bug.
    // Initialised to the CURRENT counter, which is the point: the counter lives at LibraryScreen
    // level and is never reset, and neither is launcherFocusedAppPackage — so cycling out to the
    // grid and back re-composes this effect with a stale package and a counter already > 0, and
    // would re-open that app's menu unbidden. Seeding it here makes first composition a no-op.
    var lastHandledContextMenuTrigger by remember { mutableIntStateOf(contextMenuRequestedTrigger) }
    LaunchedEffect(contextMenuRequestedTrigger) {
        if (contextMenuRequestedTrigger == lastHandledContextMenuTrigger) return@LaunchedEffect
        lastHandledContextMenuTrigger = contextMenuRequestedTrigger
        if (contextMenuRequestedTrigger <= 0) return@LaunchedEffect
        val pkg = focusedAppPackage ?: return@LaunchedEffect
        val pinnedIdx = favoriteApps.indexOfFirst { it.packageName == pkg }
        val emulatorIdx = emulatorApps.indexOfFirst { it.app.packageName == pkg }
        val regularIdx = nonFavoriteApps.indexOfFirst { it.packageName == pkg }
        when {
            pinnedIdx >= 0 -> openAppContextMenu(pkg, LibraryFocusRules.AppSection.PINNED, pinnedIdx)
            emulatorIdx >= 0 -> openAppContextMenu(pkg, LibraryFocusRules.AppSection.EMULATORS, emulatorIdx)
            regularIdx >= 0 -> openAppContextMenu(pkg, LibraryFocusRules.AppSection.ALL_APPS, regularIdx)
        }
    }

    // Sync library focus to launcher VM
    LaunchedEffect(focusedVaultId) {
        launcherViewModel.setFocusedVault(focusedVaultId)
    }

    // Settled focus delay (matches library pattern; see LibraryGrid's copy of this effect for
    // why the null-then-id write became a single write in round two, task 3).
    var settledFocusedVaultId by remember { mutableStateOf<String?>(focusedVaultId) }
    var focusSettledGeneration by remember { mutableStateOf(0L) }
    LaunchedEffect(focusedVaultId) {
        delay(140)
        settledFocusedVaultId = focusedVaultId
        focusSettledGeneration++
    }

    // Create vault dialog state
    var showCreateVaultDialog by remember { mutableStateOf(false) }
    var pendingNewVaultPreviousId by remember { mutableStateOf<String?>(null) }
    var waitingForNewVault by remember { mutableStateOf(false) }
    val libraryFocusedVaultId by libraryViewModel.focusedVaultId.collectAsState()
    LaunchedEffect(libraryFocusedVaultId, waitingForNewVault) {
        if (waitingForNewVault && libraryFocusedVaultId != null && libraryFocusedVaultId != pendingNewVaultPreviousId) {
            waitingForNewVault = false
            pendingNewVaultPreviousId = null
            launcherViewModel.updateLastOpened(libraryFocusedVaultId!!)
            // Don't auto-navigate; let the vault showcase overlay handle it
            // (triggered via justFocusedByDetection in the main library content).
        }
    }

    // Lifecycle observer for app refresh
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                launcherViewModel.refreshApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Pager state for the vault carousel (snap built-in)
    val initialPage = remember(launcherVaults.size) {
        if (focusedVaultId != null) {
            launcherVaults.indexOfFirst { it.id == focusedVaultId }.takeIf { it >= 0 } ?: 0
        } else 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { launcherVaults.size }
    // Momentum fling: a fast swipe travels up to 10 pages before snapping (default is 1),
    // giving the standard-Android list feel. Snap resolution is intrinsic to the pager, so
    // focus still lands on a single page and the settledPage effect below reconciles it.
    // Lower snapPositionalThreshold (default 0.5) so a short drag commits to the next item.
    val carouselFling = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(10),
        snapPositionalThreshold = 0.28f
    )
    val vaultFocusRequesters = remember(launcherVaults.size) { List(launcherVaults.size) { FocusRequester() } }
    // Our own target page, updated immediately on D-pad press so the next
    // press always reads the correct position regardless of animation timing.
    var carouselTargetPage by remember { mutableIntStateOf(initialPage) }
    // True while a d-pad scroll animation is still travelling to carouselTargetPage. The pager
    // reports a settle whenever isScrollInProgress dips, including in the gap where one
    // animateScrollToPage has been cancelled and its replacement has not yet acquired the
    // scroll mutex — so that flag cannot tell an intermediate settle from a touch fling's.
    // Only the d-pad sets this, which is what makes it a reliable signal.
    var carouselDpadSettling by remember { mutableStateOf(false) }
    var carouselHasFocus by remember { mutableStateOf(false) }

    // Per-item scroll tick while the user drags/flings the apps carousel (matches the library
    // carousel + list). Armed on finger-down so d-pad presses don't double up on the tick.
    var carouselTouchArmed by remember { mutableStateOf(false) }
    var carouselFingerDown by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        var last = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }.collect { p ->
            if (p != last) {
                if (carouselTouchArmed) {
                    FeedbackManager.playSound(context, FeedbackEvent.SCROLL_TICK, FeedbackManager.soundEnabled)
                }
                last = p
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && !carouselFingerDown) carouselTouchArmed = false
        }
    }

    // After the pager settles (from D-pad or gesture), sync everything.
    // Same intent guard as the library carousel — see the comment there.
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        if (settled != carouselTargetPage && !carouselDpadSettling) {
            carouselTargetPage = settled
        }
        if (settled == carouselTargetPage) {
            val vault = launcherVaults.getOrNull(settled)
            if (vault != null && vault.id != focusedVaultId) {
                libraryViewModel.setFocusedVault(vault.id)
            }
        }
        if (LibraryFocusRules.shouldRefocusOnSettle(settled, carouselTargetPage, carouselHasFocus)) {
            try { vaultFocusRequesters.getOrNull(settled)?.requestFocus() } catch (_: Exception) {}
        }
    }

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val launcherHomeFolderUri by settingsViewModel.homeFolderUri.collectAsState(initial = "placeholder")
    val isScraping by libraryViewModel.isScraping.collectAsState()
    val searchQuery by libraryViewModel.searchQuery.collectAsState()
    val topMenuFocusRequester = remember { FocusRequester() }
    var showLauncherHomeFolderDialog by remember { mutableStateOf(false) }

    // Home folder picker for launcher mode (gates create vault)
    val launcherHomeFolderPickScope = rememberCoroutineScope()
    val launcherHomeFolderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            launcherHomeFolderPickScope.launch {
                settingsViewModel.pickHomeFolder(uri)
                showCreateVaultDialog = true
            }
        }
    }

    // Item index of the first All-apps cell, counted by the rules module so the grid below and
    // the focus code share one answer. It counts the top bar as item 0.
    val firstAllAppsIndex = LibraryFocusRules.firstAllAppsItemIndex(
        hasVaults = launcherVaults.isNotEmpty(),
        pinnedCount = favoriteApps.size,
        emulatorCount = emulatorApps.size,
    )

    var landingMode by remember { mutableStateOf<LibraryFocusRules.LauncherLanding?>(null) }
    LaunchedEffect(Unit) {
        val landing = LibraryFocusRules.launcherLanding(hasVaults = launcherVaults.isNotEmpty())
        landingMode = landing
        // A collapsed All apps section would leave the app-grid landing nothing to land on.
        if (landing == LibraryFocusRules.LauncherLanding.APP_GRID) {
            launcherViewModel.setAppsSectionCollapsed(false)
        }
    }

    var landed by remember { mutableStateOf(false) }

    // Open on the vault carousel like every other view mode.
    LaunchedEffect(landingMode, launcherVaults.size) {
        if (landingMode != LibraryFocusRules.LauncherLanding.VAULT_CAROUSEL || landed) return@LaunchedEffect
        if (launcherVaults.isEmpty()) return@LaunchedEffect
        gridState.scrollToItem(0)
        val idx = launcherVaults.indexOfFirst { it.id == focusedVaultId }.takeIf { it >= 0 } ?: 0
        pagerState.scrollToPage(idx)
        carouselTargetPage = idx
        repeat(12) {
            kotlinx.coroutines.delay(50)
            try {
                vaultFocusRequesters.getOrNull(idx)?.requestFocus()
                landed = true
                return@LaunchedEffect
            } catch (_: Exception) {}
        }
    }

    // The app grid, for a library that had no carousel to land on when the launcher opened.
    //
    // firstAllAppsIndex is a key, not just a captured value. It depends on favoriteApps.size,
    // and favoriteApps arrives on a DIFFERENT Room flow from nonFavoriteApps — so a user with
    // pinned apps can hit a frame where the index was computed before the pins landed. Without
    // this key the effect would keep the stale index, scroll short of the All apps section and
    // fail all its focus retries, leaving the launcher on the emulator rows with nothing focused.
    LaunchedEffect(landingMode, regularFocusRequesters, appsCollapsed, isLoadingApps, firstAllAppsIndex) {
        if (landingMode != LibraryFocusRules.LauncherLanding.APP_GRID || landed) return@LaunchedEffect
        if (isLoadingApps) return@LaunchedEffect
        if (launcherVaults.isEmpty()) {
            // The app grid is only where launcherLanding sends an empty library, and EmptyState (item 1) already anchors focus on its first action with a
            // retry loop of its own. Landing here too would put two writers on initial focus and
            // show a visible jump on first run — and because the landing scrolls the hint out of
            // the lazy grid, it would dispose the very node holding focus. So neither the scroll
            // nor the request: EmptyState is the sole focus writer for an empty library. The app
            // grid stays reachable by d-pad down the page, and the app rows now have a route back
            // up to the hint and the top bar.
            landed = true
            return@LaunchedEffect
        }
        val firstApp = nonFavoriteApps.firstOrNull()
        if (firstApp == null || appsCollapsed) {
            // Asked for the app grid, but there is nothing in it — every installed app is
            // pinned, or the user left the section collapsed. With vaults present, fall back to
            // the carousel so the launcher is never left with nothing focused and a dead d-pad.
            // With NO vaults there is deliberately no fallback here: EmptyState is composed in
            // that case and anchors focus on its own first action. Racing it would put two
            // writers on initial focus, which is the failure this whole change removes.
            // `isLoadingApps` is handled above — that means "not yet", not "nothing".
            if (launcherVaults.isNotEmpty()) {
                gridState.scrollToItem(0)
                val idx = launcherVaults.indexOfFirst { it.id == focusedVaultId }.takeIf { it >= 0 } ?: 0
                pagerState.scrollToPage(idx)
                carouselTargetPage = idx
                repeat(12) {
                    kotlinx.coroutines.delay(50)
                    try {
                        vaultFocusRequesters.getOrNull(idx)?.requestFocus()
                        landed = true
                        return@LaunchedEffect
                    } catch (_: Exception) {}
                }
            }
            return@LaunchedEffect
        }
        val requester = regularFocusRequesters[firstApp.packageName] ?: return@LaunchedEffect
        gridState.scrollToItem(firstAllAppsIndex)
        // The cell composes a frame or more after the scroll and requestFocus throws until its
        // FocusRequester is attached, so retry rather than taking one shot at a guessed delay.
        // The latch is only set on success: if every attempt fails, the next app-list emission
        // restarts this effect and tries again.
        repeat(12) {
            kotlinx.coroutines.delay(50)
            try {
                requester.requestFocus()
                landed = true
                return@LaunchedEffect
            } catch (_: Exception) {}
        }
    }

    // Fixed readability scrim over per-game blur or the flat theme colour. NOT over a wallpaper:
    // that style carries a dim the user sets, and stacking a second one on top of it would make
    // their slider mean something different here than everywhere else.
    val launcherScrim = if (wallpaperBackdrop != null) {
        Color.Transparent
    } else {
        palette.background.copy(alpha = 0.7f)
    }
    Box(modifier = modifier.background(launcherScrim)) {

        // Single scrollable grid, whose first item is the top bar (no space reserved for it).
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                // No status-bar inset here: the bar is item 0 and applies its own
                // windowInsetsPadding(statusBars), so adding it again reserved the inset twice and
                // left a visible empty band above the bar in portrait. Landscape hid it because the
                // status bar is hidden there and the inset is zero.
                top = 0.dp,
                bottom = 64.dp + ControllerHintBarReservedHeight
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // The top bar is the page's first row: always composed, scrolling away with
            // the content and back into view at the top, which is what makes it reachable
            // by pressing up from the row below it.
            item(span = { GridItemSpan(maxLineSpan) }) {
                val bleed = 16.dp   // MUST equal this grid's contentPadding start/end
                Box(
                    Modifier.layout { measurable, constraints ->
                        // The bar is the page's first row but it is chrome, not content: its
                        // vertical gradient has to reach both screen edges, which means it must
                        // be measured at the full window width rather than the grid's inset
                        // content width. Measure past the grid's horizontal padding and shift
                        // back.
                        val extra = bleed.roundToPx() * 2
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = constraints.maxWidth + extra,
                                maxWidth = constraints.maxWidth + extra,
                            )
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.place(-bleed.roundToPx(), 0)
                        }
                    }
                ) {
                    LibraryTopMenuBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { libraryViewModel.updateSearchQuery(it) },
                        onCreateVault = {
                            if (launcherHomeFolderUri == null) {
                                showLauncherHomeFolderDialog = true
                            } else {
                                showCreateVaultDialog = true
                            }
                        },
                        onSettings = { mainViewModel.navigateToSettings() },
                        onJourney = { mainViewModel.navigateToJourney() },
                        onRefreshBoxArt = triggerLibraryRefresh,
                        onSearchClick = onSearchClick,
                        onInputReceived = {},
                        isScraping = isScraping,
                        firstButtonFocusRequester = topMenuFocusRequester,
                        accentColorOverride = displayAccentColorRaw,
                        leadingContent = {
                            LibraryStatusIndicators()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { keyEvent ->
                                val isDown = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN
                                val isUp = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP
                                if (isDown || isUp) {
                                    if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                                        if (isDown) {
                                            // Return focus to the vault carousel (or first app if no vaults)
                                            val focusedIdx = launcherVaults.indexOfFirst { it.id == focusedVaultId }
                                                .takeIf { it >= 0 } ?: 0
                                            val vaultTarget = vaultFocusRequesters.getOrNull(focusedIdx)
                                            if (vaultTarget != null) {
                                                // The carousel is item 1 and the bar is item 0, so
                                                // while the bar holds focus the carousel is already
                                                // composed — no scroll needed here.
                                                coroutineScope.launch {
                                                    pagerState.scrollToPage(focusedIdx)
                                                    try { vaultTarget.requestFocus() } catch (_: Exception) {}
                                                }
                                            } else {
                                                // No carousel, so the first app cell instead — and it is NOT
                                                // composed while the bar has focus. With an empty library the
                                                // grid sits at item 0 and the hint, two dividers, two headers
                                                // and the browse tile fill the viewport before even the pinned
                                                // row, let alone All apps at item 7. A single unguarded shot
                                                // throws, the catch eats it, the key is consumed and DOWN is
                                                // dead for every press after. Scroll the target's section in
                                                // first and retry, like every other cross-section hop here.
                                                val pinnedTarget = favoriteApps.firstOrNull()?.packageName
                                                    ?.let { favoriteFocusRequesters[it] }
                                                val appTarget = pinnedTarget
                                                    ?: nonFavoriteApps.firstOrNull()?.packageName?.let { regularFocusRequesters[it] }
                                                val sectionItem = if (pinnedTarget != null) {
                                                    LibraryFocusRules.firstPinnedItemIndex(launcherVaults.isNotEmpty())
                                                } else firstAllAppsIndex
                                                if (appTarget != null) {
                                                    coroutineScope.launch {
                                                        gridState.animateScrollToItem(sectionItem)
                                                        repeat(6) {
                                                            kotlinx.coroutines.delay(30)
                                                            try { appTarget.requestFocus(); return@launch } catch (_: Exception) {}
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        true // Consume both DOWN and UP
                                    } else false
                                } else false
                            }
                    )
                }
            }
            // --- Empty library hint (inline, before apps) ---
            if (launcherVaults.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            onAddVault = {
                                if (launcherHomeFolderUri == null) {
                                    showLauncherHomeFolderDialog = true
                                } else {
                                    showCreateVaultDialog = true
                                }
                            },
                            onDetectLastPlayed = triggerLibraryRefresh,
                            onBrowseEmulators = { mainViewModel.navigateToAppCatalog() },
                            focusColor = displayAccentColorRaw,
                        )
                    }
                }
            }
            // --- Vault row (full-span, pager with snap) ---
            if (launcherVaults.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    carouselFingerDown = true
                                    carouselTouchArmed = true
                                    do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                                    carouselFingerDown = false
                                }
                            }
                            .onFocusChanged { carouselHasFocus = it.hasFocus }
                            // Intercept D-pad before any VaultGridItem sees it.
                            // Uses carouselTargetPage (updated immediately on each
                            // press) so the next press always reads the right value.
                            .onPreviewKeyEvent { keyEvent ->
                                val isDown = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN
                                val isUp = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP
                                if (!(isDown || isUp)) return@onPreviewKeyEvent false
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        if (isDown) {
                                            val current = carouselTargetPage
                                            if (current > 0) {
                                                val prev = current - 1
                                                carouselTargetPage = prev
                                                onFocusChange(launcherVaults[prev].id)
                                                // Move Compose focus in lockstep with navigation.
                                                // The settled-page LaunchedEffect below also calls
                                                // requestFocus() but only after the scroll animation
                                                // completes — if the user presses A before settle
                                                // (or returns from a vault session that desynced
                                                // the focus handshake), focus stays pinned on the
                                                // previous page's Column and that page's onClick
                                                // fires with its captured vault. Diagnosed via
                                                // `LauncherCarouselDbg` logs 2026-06-09 showing
                                                // page=1 firing while pagerCurrent=0/2.
                                                try { vaultFocusRequesters.getOrNull(prev)?.requestFocus() } catch (_: Exception) {}
                                                carouselDpadSettling = true
                                                coroutineScope.launch {
                                                    try { pagerState.animateScrollToPage(prev) } finally {
                                                        // An interrupted animation's finally runs while a newer press is already in
                                                        // flight, and carouselTargetPage has moved on by then — so only the last press
                                                        // of a burst clears the flag.
                                                        if (carouselTargetPage == prev) carouselDpadSettling = false
                                                    }
                                                }
                                            }
                                        }
                                        true
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        if (isDown) {
                                            val current = carouselTargetPage
                                            if (current < launcherVaults.size - 1) {
                                                val next = current + 1
                                                carouselTargetPage = next
                                                onFocusChange(launcherVaults[next].id)
                                                try { vaultFocusRequesters.getOrNull(next)?.requestFocus() } catch (_: Exception) {}
                                                carouselDpadSettling = true
                                                coroutineScope.launch {
                                                    try { pagerState.animateScrollToPage(next) } finally {
                                                        // An interrupted animation's finally runs while a newer press is already in
                                                        // flight, and carouselTargetPage has moved on by then — so only the last press
                                                        // of a burst clears the flag.
                                                        if (carouselTargetPage == next) carouselDpadSettling = false
                                                    }
                                                }
                                            }
                                        }
                                        true
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (isDown) {
                                            // The bar is item 0 of a lazy grid, so it is not composed while the
                                            // carousel is at the top of the viewport and its requester is
                                            // unattached. Scroll it in first; requesting focus before the scroll
                                            // would throw and be swallowed, leaving the key consumed and focus
                                            // stuck on the carousel forever.
                                            coroutineScope.launch {
                                                gridState.animateScrollToItem(0)
                                                repeat(6) {
                                                    kotlinx.coroutines.delay(30)
                                                    try { topMenuFocusRequester.requestFocus(); return@launch } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                        true // Consume both DOWN and UP
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (isDown) {
                                            val firstFavoritePackage = favoriteApps.firstOrNull()?.packageName
                                            val firstRegularPackage = nonFavoriteApps.firstOrNull()?.packageName
                                            val targetRequester = firstFavoritePackage?.let { favoriteFocusRequesters[it] }
                                                ?: firstRegularPackage?.let { regularFocusRequesters[it] }
                                            targetRequester?.let {
                                                try { it.requestFocus() } catch (_: Exception) {}
                                            }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                    ) {
                        // Carousel sizing pivoted from "fixed width 160dp, height derived
                        // from ratio" to "fixed height 240dp, width derived from ratio".
                        // VaultGridItem internally does .fillMaxWidth().aspectRatio(ratio),
                        // so the height it computes is pageWidth/ratio — substituting
                        // pageWidth = referenceHeight*ratio yields height = referenceHeight,
                        // constant across every ratio. 2:3 still resolves to 160dp wide so
                        // there's no regression in the default config; 3:1 jumps from a
                        // ~53dp-tall sliver to the same 240dp strip the 2:3 setting has
                        // always shown.
                        //
                        // Narrow viewports (phone portrait at extreme ratios like 3:1) would
                        // otherwise get items wider than the screen — pageWidth is clamped
                        // so at least ~30dp of the next page peeks, and the aspect-derived
                        // height shrinks proportionally. Clamp floor (120dp) keeps things
                        // sane even with absurd inputs.
                        val carouselReferenceHeight = 240.dp
                        val rawPageWidth = carouselReferenceHeight * gridAspectRatio
                        val pageWidthCap = (maxWidth - 32.dp).coerceAtLeast(120.dp)
                        val pageWidth = rawPageWidth.coerceAtMost(pageWidthCap)
                        // End padding lets the last item snap to the left edge
                        val endPadding = (maxWidth - pageWidth).coerceAtLeast(0.dp)
                        // Decode size for the carousel item. Default 256×341 is calibrated
                        // for the old 160dp pageWidth; at the new wider-ratio sizes (720dp
                        // at 3:1) Compose would upscale ~5× from 256-wide and the LIST-mode
                        // pixelation you remember would resurface here. Same ratio threshold
                        // as LibraryGrid's LIST path. Memory: ~1.2 MB/bitmap × ≤5 in-flight
                        // pages (2.6 visible + beyondBoundsPageCount = 1 buffer) ≈ 6 MB.
                        val (carouselDecodeWidth, carouselDecodeHeight) = if (gridAspectRatio >= 1.5f) {
                            val w = 960
                            val h = (w / gridAspectRatio).toInt().coerceIn(160, 1280)
                            w to h
                        } else {
                            256 to 341
                        }

                        HorizontalPager(
                            state = pagerState,
                            pageSize = PageSize.Fixed(pageWidth),
                            pageSpacing = 12.dp,
                            contentPadding = PaddingValues(end = endPadding),
                            beyondBoundsPageCount = 1,
                            flingBehavior = carouselFling,
                            // Bounds-check on both key and content: the pager lives in a
                            // deferred composition (inside LazyVerticalGrid), so its inner
                            // `latestKey` lambda is captured one recomp later than the outer
                            // `pageCount` lambda. When sortedVaults grows (e.g. user adds a
                            // vault to a non-empty library), state.pageCount can briefly
                            // return the new size while the key lambda's closure still holds
                            // the previous list — NearestRangeKeyIndexMap then asks for an
                            // out-of-range index and crashes.
                            key = { page ->
                                launcherVaults.getOrNull(page)?.let { "vault_${it.id}" }
                                    ?: "vault_placeholder_$page"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            val vault = launcherVaults.getOrNull(page) ?: return@HorizontalPager
                            // The card is marked only while focus is genuinely inside the
                            // carousel. focusedVaultId alone is ViewModel state, so the card
                            // used to stay lit while an app cell held real focus — two rings
                            // on screen at once. The backdrop, adaptive accent and LED keep
                            // reading focusedVaultId, so browsing apps still keeps the focused
                            // vault's artwork and colour behind the grid.
                            val isSettled = carouselHasFocus && vault.id == settledFocusedVaultId
                            val isFocused = carouselHasFocus && vault.id == focusedVaultId
                            val achInfo = if (showAchievementBars) achievementInfoById[vault.id] else null
                            // Continuous size/brightness curve tracking the scroll, same as the
                            // library carousel: the centred card is full-size/bright, neighbours
                            // shrink and dim. Read in the layer phase so it doesn't recompose pages.
                            val pageScaleModifier = Modifier.graphicsLayer {
                                val pageOffset = kotlin.math.abs(
                                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                ).coerceIn(0f, 1f)
                                val nearness = 1f - pageOffset
                                val sc = 0.82f + (1f - 0.82f) * nearness
                                scaleX = sc
                                scaleY = sc
                                alpha = 0.6f + (1f - 0.6f) * nearness
                            }

                            VaultGridItem(
                                vault = vault,
                                onClick = {
                                    launcherViewModel.updateLastOpened(vault.id)
                                    mainViewModel.openVault(vault.id)
                                },
                                // Game launching disabled for now
                                onDoubleTap = null,
                                onLongPress = { onVaultLongPress(vault) },
                                isSettledFocus = isSettled,
                                isViewModelFocused = isFocused,
                                focusSettledGeneration = focusSettledGeneration,
                                borderColorWhenSettled = if (isSettled) accentColorProvider else null,
                                focusColor = if (isFocused || isSettled) accentColorProvider else { { focusColorFallback } },
                                settledAccentColor = if (isFocused || isSettled) displayAccentColorRaw else focusColorFallback,
                                hapticFeedbackIntensity = hapticFeedbackIntensity,
                                focusRequester = vaultFocusRequesters.getOrNull(page) ?: remember { FocusRequester() },
                                onFocusRequested = { onFocusChange(vault.id) },
                                artDisplayMode = artDisplayMode,
                                gridAspectRatio = gridAspectRatio,
                                decodeWidth = carouselDecodeWidth,
                                decodeHeight = carouselDecodeHeight,
                                // Each tile renders its own bottom-overlay
                                // title when the user has the setting on,
                                // matching how the regular library grid
                                // displays vault names — so the launcher no
                                // longer needs the dedicated marquee line
                                // that used to live below the carousel.
                                showLibraryItemNames = showLibraryItemNames,
                                modifier = Modifier.width(pageWidth).then(pageScaleModifier),
                                isDarkTheme = isDarkTheme,
                                achievementSoftcore = achInfo?.softcoreFraction,
                                achievementHardcore = achInfo?.hardcoreFraction,
                                achievementAwardKind = achInfo?.raAwardKind
                            )
                        }
                    }
                }
            }

            // --- Divider between carousel and apps grid ---
            // (Previously this section also rendered a marquee with the
            // focused vault's name; removed because each tile now owns its
            // own title overlay via `showLibraryItemNames`.)
            if (launcherVaults.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 4.dp)
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        displayAccentColorRaw.copy(alpha = 0.6f),
                                        displayAccentColorRaw,
                                        displayAccentColorRaw.copy(alpha = 0.6f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            // --- Pinned apps (5-column grid cells) ---
            itemsIndexed(favoriteApps, key = { _, app -> "pin_${app.packageName}" }) { index, app ->
                // Only intercept DPAD_UP on the first row to jump to the vault
                // carousel; let default grid navigation handle all other movement.
                val isFirstRow = index < 5
                Box(
                    modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                        if (!isFirstRow) return@onPreviewKeyEvent false
                        val isDown = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN
                        val isUp = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP
                        if (!(isDown || isUp)) return@onPreviewKeyEvent false
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                if (isDown) {
                                    // Scroll grid to top and pager to the focused
                                    // vault so its composable is live for focus.
                                    coroutineScope.launch {
                                        val focusedIdx = launcherVaults.indexOfFirst { it.id == focusedVaultId }
                                            .takeIf { it >= 0 } ?: 0
                                        val target = vaultFocusRequesters.getOrNull(focusedIdx)
                                        if (target != null) {
                                            // The carousel, not the top bar above it.
                                            gridState.animateScrollToItem(LibraryFocusRules.VAULT_ITEM_INDEX)
                                            pagerState.scrollToPage(focusedIdx)
                                            // The page attaches its requester a frame or more
                                            // after the scroll composes it; a single shot that
                                            // throws would leave the key consumed and focus
                                            // nowhere. Same retry as the carousel's own UP.
                                            repeat(6) {
                                                kotlinx.coroutines.delay(30)
                                                try { target.requestFocus(); return@launch } catch (_: Exception) {}
                                            }
                                            return@launch
                                        }
                                        // Empty library: item 1 is the empty-library hint, not a
                                        // carousel, so there is nothing there to focus. Head for
                                        // the top bar rather than consuming the key into the void
                                        // — on a fresh install the launcher always lands on the
                                        // app grid, so this is the only d-pad route out of it.
                                        gridState.animateScrollToItem(0)
                                        repeat(6) {
                                            kotlinx.coroutines.delay(30)
                                            try { topMenuFocusRequester.requestFocus(); return@launch } catch (_: Exception) {}
                                        }
                                    }
                                }
                                true // Consume both DOWN and UP
                            }
                            else -> false
                        }
                    }
                ) {
                    LauncherAppIconCell(
                        app = app,
                        showName = true,
                        foregroundColor = palette.foreground,
                        onClick = {
                            if (selectionMode) {
                                toggleSelected(app.packageName)
                            } else {
                                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                launcherViewModel.launchApp(app.packageName)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                openAppContextMenu(app.packageName, LibraryFocusRules.AppSection.PINNED, index)
                            }
                        },
                        focusColor = displayAccentColorRaw,
                        focusRequester = favoriteFocusRequesters[app.packageName] ?: remember { androidx.compose.ui.focus.FocusRequester() },
                        onFocusChanged = { focused ->
                            if (focused) {
                                FeedbackManager.play(view, FeedbackEvent.FOCUS)
                                onFocusedAppPackageChange(app.packageName)
                            } else if (focusedAppPackage == app.packageName && contextMenuPackage == null) {
                                // Rule 3: a cell losing focus because a Popup opened is not the
                                // user leaving it. Clearing here killed the X/A routes.
                                onFocusedAppPackageChange(null)
                            }
                        },
                        selectionMode = selectionMode,
                        isSelected = selectedPackages.contains(app.packageName),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = contextMenuPackage == app.packageName,
                        onDismissRequest = { closeAppContextMenu() }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Unpin") },
                            onClick = {
                                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                launcherViewModel.toggleFavorite(app.packageName)
                                closeAppContextMenu()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("App info") },
                            onClick = {
                                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                closeAppContextMenu()
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", app.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select") },
                            onClick = {
                                FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                closeAppContextMenu()
                                selectionMode = true
                                if (!selectedPackages.contains(app.packageName)) {
                                    selectedPackages.add(app.packageName)
                                }
                            }
                        )
                    }
                }
            }

            // --- Accent divider with gradient fade (full-span) ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 4.dp)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    displayAccentColorRaw.copy(alpha = 0.6f),
                                    displayAccentColorRaw,
                                    displayAccentColorRaw.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // --- Emulators section ---
            //
            // Always rendered in launcher mode. When at least one installed package matches
            // a Category.Emulator entry in the recommended-apps catalog for the user's
            // device type, the section shows those apps (Pinned and Regular sections still
            // exist independently; dedup happens in the ViewModel). When no emulators are
            // installed, the section shows a single full-span "Browse recommended emulators"
            // placeholder tile that opens the App Catalog.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Emulators",
                    color = palette.foreground,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (emulatorApps.isNotEmpty()) {
                itemsIndexed(emulatorApps, key = { _, entry -> "emu_${entry.app.packageName}" }) { index, entry ->
                    val app = entry.app
                    Box {
                        LauncherAppIconCell(
                            app = app,
                            showName = true,
                            foregroundColor = palette.foreground,
                            onClick = {
                                if (selectionMode) {
                                    toggleSelected(app.packageName)
                                } else {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    launcherViewModel.launchApp(app.packageName)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    openAppContextMenu(app.packageName, LibraryFocusRules.AppSection.EMULATORS, index)
                                }
                            },
                            focusColor = displayAccentColorRaw,
                            focusRequester = emulatorFocusRequesters[app.packageName] ?: remember { androidx.compose.ui.focus.FocusRequester() },
                            onFocusChanged = { focused ->
                                if (focused) {
                                    FeedbackManager.play(view, FeedbackEvent.FOCUS)
                                    onFocusedAppPackageChange(app.packageName)
                                } else if (focusedAppPackage == app.packageName && contextMenuPackage == null) {
                                    // Rule 3: a cell losing focus because a Popup opened is not the
                                    // user leaving it. Clearing here killed the X/A routes.
                                    onFocusedAppPackageChange(null)
                                }
                            },
                            selectionMode = selectionMode,
                            isSelected = selectedPackages.contains(app.packageName),
                            modifier = Modifier.fillMaxWidth(),
                            subtitle = entry.about
                        )
                        DropdownMenu(
                            expanded = contextMenuPackage == app.packageName,
                            onDismissRequest = { closeAppContextMenu() }
                        ) {
                            val isFav = launcherViewModel.isFavorite(app.packageName)
                            DropdownMenuItem(
                                text = { Text(if (isFav) "Unpin" else "Pin") },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    launcherViewModel.toggleFavorite(app.packageName)
                                    closeAppContextMenu()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("App info") },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    closeAppContextMenu()
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", app.packageName, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Uninstall") },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.BACK)
                                    closeAppContextMenu()
                                    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                        data = Uri.fromParts("package", app.packageName, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Select") },
                                onClick = {
                                    FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                                    closeAppContextMenu()
                                    selectionMode = true
                                    if (!selectedPackages.contains(app.packageName)) {
                                        selectedPackages.add(app.packageName)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Catalog entry point — always rendered as the last item in the section
            // so users can browse recommended emulators even when some are already installed.
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmulatorPlaceholderTile(
                    foregroundColor = palette.foreground,
                    focusColor = displayAccentColorRaw,
                    onClick = { mainViewModel.navigateToAppCatalog() }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 4.dp)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    displayAccentColorRaw.copy(alpha = 0.6f),
                                    displayAccentColorRaw,
                                    displayAccentColorRaw.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // --- "All apps" collapsible header (controls the regular grid below) ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                            launcherViewModel.setAppsSectionCollapsed(!appsCollapsed)
                        }
                        .padding(horizontal = 32.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All apps",
                        color = palette.foreground,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        imageVector = if (appsCollapsed)
                            Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = if (appsCollapsed) "Expand apps" else "Collapse apps",
                        tint = palette.foreground,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // --- Loading spinner ---
            if (!appsCollapsed && isLoadingApps && nonFavoriteApps.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = displayAccentColorRaw,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // --- Regular apps (5-column cells) — hidden when the section is collapsed ---
            if (!appsCollapsed) {
            itemsIndexed(nonFavoriteApps, key = { _, app -> app.packageName }) { index, app ->
                // Only intercept DPAD_UP on the first row to cross into the
                // pinned-apps / vault section; let default grid nav handle the rest.
                val isFirstRow = index < 5
                Box(
                    modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                        if (!isFirstRow) return@onPreviewKeyEvent false
                        val isDown = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN
                        val isUp = keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP
                        if (!(isDown || isUp)) return@onPreviewKeyEvent false
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                if (isDown) {
                                    // Scroll grid to top so pinned apps / vault
                                    // get composed, then request focus.
                                    coroutineScope.launch {
                                        val firstFavorite = favoriteApps.firstOrNull()?.packageName
                                        val target = firstFavorite?.let { favoriteFocusRequesters[it] }
                                        if (target != null) {
                                            // Scrolling the carousel in composes the pinned row
                                            // just below it too.
                                            gridState.animateScrollToItem(LibraryFocusRules.VAULT_ITEM_INDEX)
                                            // Retry rather than take one shot: the cell attaches
                                            // its requester a frame or more after the scroll
                                            // composes it, and the key is already consumed.
                                            repeat(6) {
                                                kotlinx.coroutines.delay(30)
                                                try { target.requestFocus(); return@launch } catch (_: Exception) {}
                                            }
                                            return@launch
                                        }
                                        val focusedIdx = launcherVaults.indexOfFirst { it.id == focusedVaultId }
                                            .takeIf { it >= 0 } ?: 0
                                        val vaultTarget = vaultFocusRequesters.getOrNull(focusedIdx)
                                        if (vaultTarget != null) {
                                            // The carousel, not the top bar above it.
                                            gridState.animateScrollToItem(LibraryFocusRules.VAULT_ITEM_INDEX)
                                            pagerState.scrollToPage(focusedIdx)
                                            repeat(6) {
                                                kotlinx.coroutines.delay(30)
                                                try { vaultTarget.requestFocus(); return@launch } catch (_: Exception) {}
                                            }
                                            return@launch
                                        }
                                        // Nothing pinned and no vaults — a fresh install. Item 1
                                        // is the empty-library hint, which is not focusable, so
                                        // head for the top bar rather than consuming the key into
                                        // the void and stranding focus in the app grid.
                                        gridState.animateScrollToItem(0)
                                        repeat(6) {
                                            kotlinx.coroutines.delay(30)
                                            try { topMenuFocusRequester.requestFocus(); return@launch } catch (_: Exception) {}
                                        }
                                    }
                                }
                                true // Consume both DOWN and UP
                            }
                            else -> false
                        }
                    }
                ) {
                    LauncherAppIconCell(
                        app = app,
                        showName = true,
                        foregroundColor = palette.foreground,
                        onClick = {
                            if (selectionMode) toggleSelected(app.packageName)
                            else launcherViewModel.launchApp(app.packageName)
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                openAppContextMenu(app.packageName, LibraryFocusRules.AppSection.ALL_APPS, index)
                            }
                        },
                        focusColor = displayAccentColorRaw,
                        focusRequester = regularFocusRequesters[app.packageName] ?: remember { androidx.compose.ui.focus.FocusRequester() },
                        onFocusChanged = { focused ->
                            if (focused) onFocusedAppPackageChange(app.packageName)
                            else if (focusedAppPackage == app.packageName && contextMenuPackage == null) {
                                onFocusedAppPackageChange(null)
                            }
                        },
                        selectionMode = selectionMode,
                        isSelected = selectedPackages.contains(app.packageName),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = contextMenuPackage == app.packageName,
                        onDismissRequest = { closeAppContextMenu() }
                    ) {
                        val isFav = launcherViewModel.isFavorite(app.packageName)
                        DropdownMenuItem(
                            text = { Text(if (isFav) "Unpin" else "Pin") },
                            onClick = {
                                launcherViewModel.toggleFavorite(app.packageName)
                                closeAppContextMenu()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("App info") },
                            onClick = {
                                closeAppContextMenu()
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", app.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Uninstall") },
                            onClick = {
                                closeAppContextMenu()
                                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                    data = Uri.fromParts("package", app.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select") },
                            onClick = {
                                closeAppContextMenu()
                                selectionMode = true
                                if (!selectedPackages.contains(app.packageName)) {
                                    selectedPackages.add(app.packageName)
                                }
                            }
                        )
                    }
                }
            }
            }

        }

        androidx.compose.animation.AnimatedVisibility(
            visible = selectionMode,
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.background)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${selectedPackages.size} selected",
                    color = palette.foreground,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = selectedPackages.isNotEmpty(),
                    onClick = {
                        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                        launcherViewModel.pinAll(selectedPackages.toSet())
                        exitSelection()
                    }
                ) { Text("Pin", color = displayAccentColorRaw) }
                TextButton(
                    enabled = selectedPackages.isNotEmpty(),
                    onClick = {
                        FeedbackManager.play(view, FeedbackEvent.CONFIRM)
                        launcherViewModel.unpinAll(selectedPackages.toSet())
                        exitSelection()
                    }
                ) { Text("Unpin", color = displayAccentColorRaw) }
                TextButton(
                    onClick = {
                        FeedbackManager.play(view, FeedbackEvent.BACK)
                        exitSelection()
                    }
                ) { Text("Cancel", color = palette.foreground) }
            }
        }
    }

    // Home folder required dialog (launcher mode)
    if (showLauncherHomeFolderDialog) {
        HomeFolderRequiredDialog(
            onPickFolder = {
                showLauncherHomeFolderDialog = false
                launcherHomeFolderPickerLauncher.launch(null)
            },
            onCancel = {
                showLauncherHomeFolderDialog = false
            },
            focusColor = focusColorFallback
        )
    }

    // Create Vault Dialog
    if (showCreateVaultDialog) {
        val suggestionProvider = remember {
            dagger.hilt.EntryPoints.get(
                context.applicationContext,
                com.twinlore.app.di.GameNameSuggestionEntryPoint::class.java
            ).gameNameSuggestionProvider()
        }
        val hasRaCreds by libraryViewModel.hasRaCredentials.collectAsState()

        CreateVaultDialog(
            onDismiss = { showCreateVaultDialog = false },
            onConfirm = { gameName ->
                pendingNewVaultPreviousId = libraryFocusedVaultId
                waitingForNewVault = true
                libraryViewModel.createVault(gameName)
                showCreateVaultDialog = false
            },
            onAddAotW = { gameName, raGameId ->
                pendingNewVaultPreviousId = libraryFocusedVaultId
                waitingForNewVault = true
                if (hasRaCreds) {
                    libraryViewModel.createVaultWithRaLink(gameName, raGameId)
                } else {
                    libraryViewModel.createVaultWithRaLink(gameName, null)
                }
                showCreateVaultDialog = false
            },
            suggestionProvider = suggestionProvider
        )
    }
}

/**
 * Catalog entry point at the end of the Emulators section. Focusable in its own right so D-pad
 * navigation treats it as another cell of the grid rather than stepping over it — it carries the
 * same focus ring, tint and FOCUS feedback as [LauncherAppIconCell].
 */
@Composable
private fun EmulatorPlaceholderTile(
    foregroundColor: Color,
    focusColor: Color,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val border = foregroundColor.copy(alpha = 0.3f)
    val labelColor = foregroundColor.copy(alpha = 0.75f)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) {
        if (isFocused) FeedbackManager.play(view, FeedbackEvent.FOCUS)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusable(interactionSource = interactionSource)
            .indication(interactionSource = interactionSource, indication = null)
            .background(
                color = if (isFocused) focusColor.copy(alpha = 0.18f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) focusColor else border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = if (isFocused) focusColor else labelColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Browse emulators",
            color = if (isFocused) focusColor else labelColor,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

/**
 * App icon cell for launcher mode (pinned apps and app grid).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LauncherAppIconCell(
    app: LauncherApp,
    showName: Boolean,
    foregroundColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusColor: Color,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val density = LocalDensity.current
    val iconSizeDp = 64.dp
    val iconSizePx = with(density) { iconSizeDp.roundToPx() }

    val imageBitmap = remember(app.packageName, iconSizePx) {
        app.icon.toBitmap(iconSizePx, iconSizePx).asImageBitmap()
    }

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    androidx.compose.runtime.LaunchedEffect(isFocused) {
        onFocusChanged(isFocused)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .indication(interactionSource = interactionSource, indication = null)
            .background(
                color = if (isFocused) focusColor.copy(alpha = 0.18f) else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) focusColor else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Image(
                bitmap = imageBitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .size(iconSizeDp)
                    .alpha(if (selectionMode && !isSelected) 0.45f else 1f)
            )
            if (selectionMode) {
                Icon(
                    imageVector = if (isSelected)
                        Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) focusColor else foregroundColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (showName) {
            Text(
                text = app.label,
                color = foregroundColor.copy(alpha = 0.85f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = foregroundColor.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Blur radius at 100% on the wallpaper slider, in WallpaperBlurTransformation's units (it blurs
 * a 270x480 source, where 24 is the tuned default the blurred-art backdrop uses).
 */
internal const val WALLPAPER_MAX_BLUR_RADIUS = 24

/**
 * Empty state when library has no vaults
 */
@Composable
private fun EmptyState(
    onAddVault: () -> Unit,
    onDetectLastPlayed: () -> Unit,
    onBrowseEmulators: () -> Unit,
    modifier: Modifier = Modifier,
    focusColor: Color = Color.Unspecified
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    // Land focus on the first action. With no vaults there is nothing else on screen to focus,
    // and the library's startup focus effects all key on a vault that does not exist — so
    // without this the controller starts with focus nowhere and the first D-pad press goes to
    // the top bar instead of the actions the screen exists to offer.
    val firstActionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The button attaches its requester a frame or more after this effect starts, and
        // requestFocus throws until it does, so retry rather than guessing a delay.
        repeat(10) {
            delay(50)
            try { firstActionFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) {}
        }
    }
    // IntrinsicSize.Max + fillMaxWidth on each button locks them all to the widest
    // button's intrinsic width so they read as one uniform stack.
    Column(
        modifier = modifier
            .padding(32.dp)
            .width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EmptyStateActionButton(
            icon = Icons.Default.Add,
            label = "Add a game manually",
            onClick = onAddVault,
            focusColor = focusColor,
            focusRequester = firstActionFocus,
            modifier = Modifier.fillMaxWidth()
        )
        EmptyStateActionButton(
            icon = Icons.Default.Refresh,
            label = "Detect last played game",
            onClick = onDetectLastPlayed,
            focusColor = focusColor,
            modifier = Modifier.fillMaxWidth()
        )
        if (isLandscape) {
            EmptyStateActionButton(
                icon = Icons.Default.RocketLaunch,
                label = "Browse emulators",
                onClick = onBrowseEmulators,
                focusColor = focusColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * One action in the empty-library state.
 *
 * Controller-focusable on purpose. A bare Material button is NOT reachable by D-pad on this
 * hardware: Compose gates a clickable's focusability on non-touch mode, and the handheld never
 * leaves touch mode because its D-pad arrives as synthesized key events (see
 * [com.twinlore.app.ui.utils.gamepadFocusable]). Without the override these buttons were the
 * only thing on screen and none of them could be reached or pressed with a controller.
 * activateOnA is needed for the same reason: the face button is Key.ButtonA, which a plain
 * clickable does not treat as activation.
 */
@Composable
private fun EmptyStateActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusColor: Color = Color.Unspecified,
    focusRequester: FocusRequester? = null,
) {
    val buttonColor = MaterialTheme.colorScheme.onSurface
    val accent = focusColor.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .gamepadFocusable()
            .activateOnEmptyStateA(onClick),
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) accent else buttonColor.copy(alpha = 0.6f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (focused) accent.copy(alpha = 0.18f) else Color.Transparent,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (focused) accent else buttonColor
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (focused) accent else buttonColor)
    }
}

/** Gamepad A / D-pad centre / Enter all activate. Mirrors the helper in the ES-DE setup screen. */
private fun Modifier.activateOnEmptyStateA(onActivate: () -> Unit): Modifier = onKeyEvent { e ->
    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
        (e.key == androidx.compose.ui.input.key.Key.ButtonA ||
            e.key == androidx.compose.ui.input.key.Key.DirectionCenter ||
            e.key == androidx.compose.ui.input.key.Key.Enter)
    ) {
        onActivate(); true
    } else false
}

/**
 * Empty state when search returns no results
 */
@Composable
private fun EmptySearchState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Results",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Try a different search term",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/**
 * Search modal - dropdown list of vault titles with search input field
 * Selecting a vault centers it in carousel and closes modal
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Suppress("UNUSED_PARAMETER") // modifier reserved for future layout
private fun SearchVaultModal(
    vaults: List<Vault>,
    onDismiss: () -> Unit,
    onVaultSelected: (Vault) -> Unit,
    isDarkTheme: Boolean,
    focusColor: Color,
    hapticFeedbackIntensity: HapticFeedbackIntensity,
    ledEnabled: Boolean,
    ledBrightness: Int,
    ledSaturationBoost: Float,
    ledTurnOffTime: LedTurnOffTime,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchPerformedTrigger by remember { mutableIntStateOf(0) }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    
    // Use theme palette for dialog background
    val paletteSearch = LocalThemePalette.current
    val cardBackgroundColor = paletteSearch.background
    
    // Filter vaults based on search query
    val filteredVaults = remember(vaults, searchQuery) {
        if (searchQuery.isBlank()) {
            vaults
        } else {
            val query = searchQuery.lowercase()
            vaults.filter { vault ->
                vault.displayName.lowercase().contains(query) ||
                vault.romFilename?.lowercase()?.contains(query) == true ||
                vault.romPath?.lowercase()?.contains(query) == true
            }
        }
    }
    
    // Handle scrolling when search is performed (keyboard hiding is done in handlers inside Dialog scope)
    LaunchedEffect(searchPerformedTrigger, filteredVaults.size) {
        if (searchPerformedTrigger > 0) {
            // Wait for keyboard to hide and layout to settle, and for list to recompose
            kotlinx.coroutines.delay(300)
            // Scroll to top of list to show search results
            if (filteredVaults.isNotEmpty()) {
                try {
                    // Always scroll to top, even if already there, to ensure view is positioned correctly
                    listState.scrollToItem(0, scrollOffset = 0)
                } catch (e: Exception) {
                    Log.e("SearchVaultModal", "Error scrolling to top", e)
                    // Fallback: try animateScrollToItem with longer delay
                    kotlinx.coroutines.delay(100)
                    try {
                        listState.animateScrollToItem(0)
                    } catch (e2: Exception) {
                        Log.e("SearchVaultModal", "Error with animateScrollToItem too", e2)
                    }
                }
            } else {
            }
        }
    }
    
    // Don't auto-focus - let user click to focus and show keyboard
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = LocalView.current
        
        // Semi-transparent background overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(500.dp)
                    .pointerInput(Unit) {
                        // Prevent background dismiss when clicking inside card
                        detectTapGestures { }
                    },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = cardBackgroundColor
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Text(
                        text = "Search Games",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                    
                    // Search input field
                    NavigableTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "Search game titles...",
                        placeholder = "Type to filter games",
                        leadingIcon = Icons.Default.Search,
                        imeAction = ImeAction.Search,
                        onSubmit = { searchPerformedTrigger++ },
                        externalFocusRequester = searchFieldFocusRequester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    // Vault list (filtered)
                    if (filteredVaults.isEmpty() && searchQuery.isNotEmpty()) {
                        // Empty state when search returns no results
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No games found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        ) {
                            items(
                                items = filteredVaults,
                                key = { it.id }
                            ) { vault ->
                                NavigableMenuItem(
                                    text = vault.displayName,
                                    onClick = {
                                        onVaultSelected(vault)
                                    },
                                    focusId = "search_vault_${vault.id}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun HomeFolderRequiredDialog(
    onPickFolder: () -> Unit,
    onCancel: () -> Unit,
    focusColor: Color
) {
    val paletteDialog = LocalThemePalette.current
    val dialogBackgroundColor = paletteDialog.background
    val textColor = paletteDialog.foreground

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 520.dp)
                .fillMaxWidth(0.92f)
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = dialogBackgroundColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Storage Folder Required",
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Game Twin needs a storage folder for your library data (artwork, downloads, backups).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.87f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onPickFolder,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = focusColor)
                ) {
                    Text(
                        text = "Pick a folder",
                        color = focusColor.getContrastingTextColor()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text(
                            text = "Cancel",
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog when neither RetroArch nor ES-DE folder is set. Lets user choose which folder to set up or use RetroAchievements.
 */
@Composable
private fun LastPlayedFolderExplanationDialog(
    onSetRetroArch: () -> Unit,
    onSetESDE: () -> Unit,
    onSetCocoon: () -> Unit,
    onSetAchievements: () -> Unit,
    onSkip: () -> Unit,
    focusColor: Color,
    isDarkTheme: Boolean,
    errorMessage: String? = null,
) {
    
    val paletteDialog = LocalThemePalette.current
    val dialogBackgroundColor = paletteDialog.background
    val textColor = paletteDialog.foreground

    val config = LocalConfiguration.current
    val maxDialogHeight = (config.screenHeightDp * 0.85f).dp

    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 520.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = maxDialogHeight)
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = dialogBackgroundColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.last_played_folder_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Choose a method",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSetRetroArch,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = focusColor)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "RetroArch", color = focusColor.getContrastingTextColor())
                            Text(
                                text = "(All Frontend Launchers)",
                                style = MaterialTheme.typography.labelSmall,
                                color = focusColor.getContrastingTextColor().copy(alpha = 0.7f)
                            )
                        }
                    }
                    Button(
                        onClick = onSetESDE,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = focusColor)
                    ) {
                        Text(text = "ES-DE", color = focusColor.getContrastingTextColor())
                    }
                    Button(
                        onClick = onSetCocoon,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = focusColor)
                    ) {
                        Text(text = "Cocoon", color = focusColor.getContrastingTextColor())
                    }
                    Button(
                        onClick = onSetAchievements,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = focusColor)
                    ) {
                        Text(text = "Achievements (Steam / RetroAchievements)", color = focusColor.getContrastingTextColor())
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.widthIn(min = 96.dp)
                    ) {
                        Text(
                            text = "Skip",
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

private fun refreshDialogTitle(summary: LibraryViewModel.RefreshSummary): String = when {
    summary.skippedByLicenseCap.isNotEmpty() && summary.localBindEntries.isNotEmpty() -> "Refresh complete"
    summary.skippedByLicenseCap.isNotEmpty() -> "New games detected"
    else -> "Linked local files"
}

private fun formatBindParts(e: LibraryViewModel.LocalBindEntry): String {
    val parts = mutableListOf<String>()
    if (e.artworkBound != null) parts += "cover \"${e.artworkBound}\""
    if (e.guideBound != null) parts += "guide \"${e.guideBound}\""
    val extraArt = e.artworkAvailable - (if (e.artworkBound != null) 1 else 0)
    if (extraArt > 0) parts += "$extraArt more in picker"
    return if (parts.isEmpty()) "${e.artworkAvailable} candidate(s) in picker" else parts.joinToString(", ")
}

/**
 * Resolved artwork image-data per "<vaultId>_<rid>_<buster>". The SAF resolve
 * round-trip — not Coil decode — is what delayed the library wallpaper on
 * every re-entry; with this cache the ImageRequest is built immediately and
 * the memory-cached bitmap paints on the first frame.
 */
private val blurResolvedDataCache = java.util.concurrent.ConcurrentHashMap<String, Any>()
