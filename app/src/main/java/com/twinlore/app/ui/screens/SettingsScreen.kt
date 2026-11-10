package com.twinlore.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.twinlore.app.ui.components.ESDE_BANNER_COPY
import com.twinlore.app.ui.components.COCOON_BANNER_COPY
import com.twinlore.app.ui.components.RetroArchHelpDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.documentfile.provider.DocumentFile
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.focus.FocusRequester
import com.twinlore.app.ui.components.SettingsSubheader
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.navigation.NavigableMenuItem
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinlore.app.data.ThemePalette
import com.twinlore.app.data.AppFont
import com.twinlore.app.ui.LocalThemePalette
import com.twinlore.app.ui.theme.toFontFamily
import com.twinlore.app.data.ArtDisplayMode
import com.twinlore.app.data.DefaultAssetType
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.fold.FoldHandleStyle
import com.twinlore.app.data.GridDisplayRatio
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LastPlayedSource
import com.twinlore.app.data.StatusBarMode
import androidx.compose.ui.platform.LocalView
import com.twinlore.app.ui.utils.FeedbackManager
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.data.license.LicenseState
import com.twinlore.app.data.license.tierDisplayName
import com.twinlore.app.data.update.UpdateState
import com.twinlore.app.data.retroachievements.RaCredentials
import com.twinlore.app.data.steam.SteamCredentials
import com.twinlore.app.domain.LibrarySortField
import com.twinlore.app.domain.SortDirection
import com.twinlore.app.data.LibraryViewMode
import com.twinlore.app.data.LibraryBackgroundStyle
import com.twinlore.app.domain.Result
import com.twinlore.app.ui.navigation.NavigableMenuItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import com.twinlore.app.ui.navigation.NavigableTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Warning
import com.twinlore.app.ui.navigation.NavigableIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.focus.focusProperties
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.twinlore.app.BuildConfig
import com.twinlore.app.R
import com.twinlore.app.data.legal.LegalDocument
import com.twinlore.app.ui.screens.legal.LegalDocumentDialog

private data class LibrarySwitchPending(
    val newUri: Uri,
    val oldUri: Uri?,
    val newHasLibrary: Boolean,
    val newSnapshotCount: Int,
    // Non-null when the newest snapshot in the folder was written by a newer app
    // build than this one can read (its effective schema version). The confirm
    // dialog then refuses the load and shows the "saved by a newer version"
    // message instead of switching storage + importing (which would corrupt the
    // file and spin the cold-start restart loop).
    val tooNewVersion: Int? = null
)

/**
 * Settings screen - global application settings.
 *
 * Thin wrapper that defers the heavy body until the navigation dissolve has had
 * a frame to play. [SettingsScreenContent] subscribes to ~66 settings flows and
 * creates the [SettingsViewModel] on first compose; doing that work on the very
 * first frame of the nav transition janks the asymmetric fade. We paint only the
 * palette background for one short beat, then compose the real content — so the
 * dissolve runs over a cheap solid frame and the stutter is gone. See memory
 * `project_vault_entry_exit_profiling` (same class of fix).
 */
@Composable
fun SettingsScreen(
    expandAchievements: Boolean = false,
    onAchievementsExpanded: () -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Same landscape immersion as the library/vault screens — the Android
    // status bar has no business over Settings on a handheld.
    com.twinlore.app.ui.utils.HideStatusBarInLandscape()

    var contentReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // One nav-dissolve beat (fade-in tween is 200ms). Compose the body just
        // after the transition has visibly started so creation cost lands on a
        // settled frame, not mid-animation.
        delay(120)
        contentReady = true
    }

    // Gentle fade so the body materializes over the palette frame instead of
    // popping in after the deferral beat.
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentReady) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "settingsContentFade"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalThemePalette.current.background)
    ) {
        if (contentReady) {
            SettingsScreenContent(
                expandAchievements = expandAchievements,
                onAchievementsExpanded = onAchievementsExpanded,
                onBackClick = onBackClick,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha }
            )
        }
    }
}

@Composable
private fun SettingsScreenContent(
    expandAchievements: Boolean = false,
    onAchievementsExpanded: () -> Unit = {},
    onBackClick: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val themePaletteSelection by settingsViewModel.themePalette.collectAsState(initial = null)
    val appFont by settingsViewModel.appFont.collectAsState(initial = AppFont.DEFAULT)
    val resolvedPalette = LocalThemePalette.current
    val artDisplayMode by settingsViewModel.artDisplayMode.collectAsState(initial = ArtDisplayMode.FIT)
    val statusBarMode by settingsViewModel.statusBarMode.collectAsState(initial = StatusBarMode.STANDARD)
    val autoBoxArtScraping by settingsViewModel.autoBoxArtScraping.collectAsState(initial = true)
    val lastPlayedSource by settingsViewModel.lastPlayedSource.collectAsState(initial = LastPlayedSource.RETROARCH)
    val retroArchHistoryFolder by settingsViewModel.retroArchHistoryFolder.collectAsState(initial = null)
    val retroArchHistoryFolderPath by settingsViewModel.retroArchHistoryFolderPath.collectAsState(initial = null)
    val retroArchFolderState by settingsViewModel.retroArchFolderState.collectAsState(initial = null)
    val pendingRetroArchHelpDialog by settingsViewModel.pendingRetroArchHelpDialog.collectAsState(initial = false)
    val esdeFolderPath by settingsViewModel.esdeFolderPath.collectAsState(initial = null)
    val esdeFolderState by settingsViewModel.esdeFolderState.collectAsState(initial = null)
    val pendingESDEHelpDialog by settingsViewModel.pendingESDEHelpDialog.collectAsState(initial = null)
    val cocoonFolderPath by settingsViewModel.cocoonFolderPath.collectAsState(initial = null)
    val cocoonFolderState by settingsViewModel.cocoonFolderState.collectAsState(initial = null)
    val pendingCocoonHelpDialog by settingsViewModel.pendingCocoonHelpDialog.collectAsState(initial = null)
    val romsFolderUri by settingsViewModel.romsFolderUri.collectAsState(initial = null)
    val retroArchAutoRefresh by settingsViewModel.retroArchAutoRefresh.collectAsState(initial = false)
    val createVaultIfNotFound by settingsViewModel.createVaultIfNotFound.collectAsState(initial = true)
    val openLastPlayedOnLaunch by settingsViewModel.openLastPlayedOnLaunch.collectAsState(initial = true)
    val foldHandleAlpha by settingsViewModel.foldHandleAlpha
        .collectAsState(initial = FoldHandleStyle.DEFAULT_ALPHA)
    val foldHandleSizeDp by settingsViewModel.foldHandleSizeDp
        .collectAsState(initial = FoldHandleStyle.DEFAULT_SIZE_DP)
    val foldHandleRecess by settingsViewModel.foldHandleRecess
        .collectAsState(initial = FoldHandleStyle.DEFAULT_RECESS)
    val foldHandleClockBattery by settingsViewModel.foldHandleClockBattery
        .collectAsState(initial = false)
    val focusColor by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val gridDisplayRatio by settingsViewModel.gridDisplayRatio.collectAsState(initial = GridDisplayRatio.RATIO_3_4)
    val steamGridApiKey by settingsViewModel.steamGridApiKey.collectAsState(initial = null)
    val defaultAssetType by settingsViewModel.defaultAssetType.collectAsState(initial = DefaultAssetType.GRID)
    val autoFocusColorDetection by settingsViewModel.autoFocusColorDetection.collectAsState(initial = true)
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val buttonGlyphStyle by settingsViewModel.buttonGlyphStyle.collectAsState(initial = com.twinlore.app.data.ButtonGlyphStyle.XBOX)
    val swapFaceButtons by settingsViewModel.swapFaceButtons.collectAsState(initial = false)
    val focusSoundEnabled by settingsViewModel.focusSoundEnabled.collectAsState(initial = true)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 255)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0f)
    val homeFolderUri by settingsViewModel.homeFolderUri.collectAsState(initial = null)
    val showLibraryItemNames by settingsViewModel.showLibraryItemNames.collectAsState(initial = true)
    val libraryAchievementProgress by settingsViewModel.libraryAchievementProgress.collectAsState(initial = true)
    val libraryHintBarVisible by settingsViewModel.libraryHintBarVisible.collectAsState(initial = true)
    val librarySortField by settingsViewModel.librarySortField.collectAsState(initial = LibrarySortField.DATE_ADDED)
    val librarySortDirection by settingsViewModel.librarySortDirection.collectAsState(initial = SortDirection.DESC)
    val libraryViewMode by settingsViewModel.libraryViewMode.collectAsState(initial = LibraryViewMode.GRID_EXTRA_LARGE)
    val libraryBackgroundStyle by settingsViewModel.libraryBackgroundStyle.collectAsState(initial = LibraryBackgroundStyle.BLURRED_ART)
    val appWallpaperImage by settingsViewModel.appWallpaperImage.collectAsState(initial = null)
    val libraryWallpaperDim by settingsViewModel.libraryWallpaperDim.collectAsState(initial = 0.5f)
    val libraryWallpaperBlur by settingsViewModel.libraryWallpaperBlur.collectAsState(initial = 0f)
    val licenseState by settingsViewModel.licenseState.collectAsState(initial = LicenseState.NotActivated)
    val licenseUiState by settingsViewModel.licenseUiState.collectAsState()
    val raCredentials by settingsViewModel.raCredentials.collectAsState(initial = null)
    val raValidationState by settingsViewModel.raValidationState.collectAsState(initial = SettingsViewModel.RaValidationState.Idle)
    val steamCredentials by settingsViewModel.steamCredentials.collectAsState(initial = null)
    val steamValidationState by settingsViewModel.steamValidationState.collectAsState(initial = SettingsViewModel.SteamValidationState.Idle)

    val isDarkTheme = resolvedPalette.isDark
    val impressumDialogBackgroundColor = resolvedPalette.background
    
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Hoisted: scan state + scan→bind chain. Hoisted so the custom-search-path
    // launcher (declared just below) can also kick off the chain when the user
    // adds a new folder. Must be declared above the launcher because Kotlin
    // lambdas capture lexically.
    val scanState by settingsViewModel.scanState.collectAsState()

    // Folder picker launcher for RetroArch history folder
    val retroArchFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                Log.d("FolderPicker", "RetroArch folder selected: $uri")
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                // Mirror ES-DE / Cocoon: derive a short display name so the
                // Settings row reads "RetroArch ✓" instead of the raw SAF tree URI.
                val doc = DocumentFile.fromTreeUri(context, uri)
                val displayPath = doc?.name ?: uri.toString()
                settingsViewModel.setRetroArchFolderFromPicker(uri, displayPath)
                Log.d("FolderPicker", "RetroArch folder saved: $displayPath")
            } catch (e: Exception) {
                Log.e("FolderPicker", "Error handling RetroArch folder selection", e)
            }
        } else {
            Log.d("FolderPicker", "RetroArch folder picker cancelled by user")
        }
    }

    val esdeFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                Log.d("FolderPicker", "ES-DE folder selected: $uri")
                // WRITE is persisted alongside READ here even though nothing in the app
                // currently writes into this tree, so a future write does not need the user
                // to re-grant the folder.
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val doc = DocumentFile.fromTreeUri(context, uri)
                val displayPath = doc?.name ?: uri.toString()
                settingsViewModel.setEsdeFolderFromPicker(uri, displayPath)
                Log.d("FolderPicker", "ES-DE folder saved: $displayPath")
            } catch (e: Exception) {
                Log.e("FolderPicker", "Error handling ES-DE folder selection", e)
            }
        } else {
            Log.d("FolderPicker", "ES-DE folder picker cancelled by user")
        }
    }

    val cocoonFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                Log.d("FolderPicker", "Cocoon folder selected: $uri")
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val doc = DocumentFile.fromTreeUri(context, uri)
                val displayPath = doc?.name ?: uri.toString()
                settingsViewModel.setCocoonFolderFromPicker(uri, displayPath)
                Log.d("FolderPicker", "Cocoon folder saved: $displayPath")
            } catch (e: Exception) {
                Log.e("FolderPicker", "Error handling Cocoon folder selection", e)
            }
        } else {
            Log.d("FolderPicker", "Cocoon folder picker cancelled by user")
        }
    }

    val romsFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            settingsViewModel.setRomsFolderUri(uri.toString())
        }
    }

    val customSearchPathPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {}
        // Use DocumentFile.name so the label matches what MediaSearchPathRepository.checkPath
        // reports as currentName. Otherwise the freshly-added path gets flagged as "renamed"
        // because the URI's lastPathSegment ("primary:DCIM") differs from the document's
        // display name ("DCIM").
        val label = try {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
        } catch (_: Exception) { null }
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Custom"
        settingsViewModel.addManualSearchPath(uri, label)
    }

    // ── Storage section state ──
    // Storage folder picker. Picking a new tree either loads an existing
    // library snapshot (newest, when multiple devices' files coexist) or
    // moves the current data into the new folder.
    var storageMigrationProgress by remember {
        mutableStateOf<com.twinlore.app.data.library.StorageFolderMigrationCoordinator.Progress?>(null)
    }
    var storageMigrating by remember { mutableStateOf(false) }
    var pendingLibrarySwitch by remember { mutableStateOf<LibrarySwitchPending?>(null) }
    val homeFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e("FolderPicker", "Error taking persistable permission", e)
                android.widget.Toast.makeText(
                    context,
                    "Could not access folder: ${e.message ?: "unknown error"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch {
                val oldUri = settingsViewModel.homeFolderUri.first()?.let(Uri::parse)
                if (oldUri != null) {
                    runCatching { settingsViewModel.exportLibraryNow() }
                }
                val snapshotCount = settingsViewModel.findLibrarySnapshot(uri)
                val tooNewVersion = if (snapshotCount > 0) {
                    val v = settingsViewModel.newestSnapshotSchemaVersion(uri)
                    if (v != null && v > com.twinlore.app.data.local.SCHEMA_VERSION) v else null
                } else null
                pendingLibrarySwitch = LibrarySwitchPending(
                    newUri = uri,
                    oldUri = oldUri,
                    newHasLibrary = snapshotCount > 0,
                    newSnapshotCount = snapshotCount,
                    tooNewVersion = tooNewVersion
                )
            }
        }
    }
    
    val settingsScrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            // imePadding so the scrollable area shrinks when the keyboard
            // opens. targetSdk 35 enforces edge-to-edge on Android 15+, so
            // the manifest's `adjustResize` is effectively ignored — without
            // this, the verticalScroll's viewport stays full-height behind
            // the IME and `bringIntoView` from focused text fields has
            // nothing to scroll against.
            .imePadding()
            .verticalScroll(settingsScrollState)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            NavigableIconButton(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to Library",
                onClick = onBackClick,
                focusId = "settings_back",
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // ── Look & Feel ──
        ExpandableSettingsSection(title = "Look & Feel", focusId = "settings_section_look_and_feel") {
            PaletteSetting(
                currentPalette = themePaletteSelection,
                onPaletteSelected = { settingsViewModel.setThemePalette(it) },
                focusColor = focusColor,
                focusId = "settings_theme"
            )
            FontSetting(
                currentFont = appFont,
                onFontSelected = { settingsViewModel.setAppFont(it) },
                focusId = "settings_font"
            )
            val currentAppIcon by settingsViewModel.appIcon.collectAsState()
            val appIconTier by settingsViewModel.licenseTier.collectAsState()
            val tapHighScore by settingsViewModel.tapGameHighScore.collectAsState()
            var previewAppIcon by remember { mutableStateOf<com.twinlore.app.data.AppIcon?>(null) }
            val cocoonIconPackHintShown by settingsViewModel.cocoonIconPackHintShown.collectAsState(initial = false)
            var pendingCocoonHintIcon by remember { mutableStateOf<com.twinlore.app.data.AppIcon?>(null) }
            AppIconSetting(
                currentIcon = currentAppIcon,
                tier = appIconTier,
                tapHighScore = tapHighScore,
                onIconSelected = { previewAppIcon = it },
                onIconTap = { previewAppIcon = it },
                focusId = "settings_app_icon",
            )
            // Tapping any tile (or cycling the row) previews the theme: what it
            // changes, and the action its state allows (checkout, key entry, or
            // apply). The preview is the one and only confirmation: its Apply
            // carries the close warning and applies directly. Applying flips the
            // launcher alias, which closes the app, so no apply is offered for
            // the theme that is already active, unless it was picked in Overlay
            // Mode and the launcher has not caught up with it yet.
            val launcherBehind = remember(previewAppIcon, currentAppIcon) {
                previewAppIcon == currentAppIcon &&
                    previewAppIcon?.let { !settingsViewModel.isLauncherIconApplied(it) } == true
            }
            com.twinlore.app.ui.license.ThemePreviewHost(
                icon = previewAppIcon,
                settingsViewModel = settingsViewModel,
                onApply = if (previewAppIcon == currentAppIcon && !launcherBehind) null else { icon ->
                    // On Cocoon (and only the first time), the "show it in
                    // Cocoon too" hint interrupts the apply instead of firing
                    // it directly; the hint dialog's own confirm applies for
                    // real once dismissed.
                    if (com.twinlore.app.data.shouldShowCocoonIconPackHint(lastPlayedSource, cocoonIconPackHintShown)) {
                        pendingCocoonHintIcon = icon
                    } else {
                        settingsViewModel.setAppIcon(icon) { closeApp(context) }
                    }
                },
                onDismiss = { previewAppIcon = null },
            )
            pendingCocoonHintIcon?.let { icon ->
                com.twinlore.app.ui.license.CocoonIconPackHintDialog(
                    onApply = {
                        settingsViewModel.applyAppIconAfterCocoonHint(icon) { closeApp(context) }
                        pendingCocoonHintIcon = null
                    },
                    onBack = { pendingCocoonHintIcon = null },
                )
            }
            AutoFocusColorDetectionSetting(
                enabled = autoFocusColorDetection,
                onToggle = { settingsViewModel.setAutoFocusColorDetection(it) },
                focusId = "settings_auto_focus_color"
            )
            FocusColorSetting(
                currentColor = focusColor,
                onColorSelected = { settingsViewModel.setFocusColor(it) },
                focusId = "settings_focus_color",
                enabled = !autoFocusColorDetection
            )
            HapticFeedbackSetting(
                currentIntensity = hapticFeedbackIntensity,
                onIntensityChanged = { settingsViewModel.setHapticFeedbackIntensity(it) },
                focusId = "settings_haptic_feedback"
            )
            FocusSoundSetting(
                enabled = focusSoundEnabled,
                onToggle = { settingsViewModel.setFocusSoundEnabled(it) },
                focusId = "settings_focus_sound"
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

            DefaultAssetTypeSetting(
                assetType = defaultAssetType,
                onAssetTypeChanged = { settingsViewModel.setDefaultAssetType(it) },
                focusId = "settings_default_asset_type"
            )
            ArtDisplayModeSetting(
                currentMode = artDisplayMode,
                onModeSelected = { settingsViewModel.setArtDisplayMode(it) },
                focusId = "settings_art_display"
            )
            GridDisplayRatioSetting(
                currentRatio = gridDisplayRatio,
                onRatioSelected = { settingsViewModel.setGridDisplayRatio(it) },
                focusId = "settings_grid_ratio"
            )
            LibraryBackgroundStyleSetting(
                currentStyle = libraryBackgroundStyle,
                onStyleSelected = { settingsViewModel.setLibraryBackgroundStyle(it) },
                focusId = "settings_library_background_style"
            )
            if (libraryBackgroundStyle == LibraryBackgroundStyle.WALLPAPER) {
                LibraryWallpaperImageSetting(
                    currentImage = appWallpaperImage,
                    onImageSelected = { settingsViewModel.setAppWallpaperImage(it) },
                    focusId = "settings_library_wallpaper_image"
                )
                // The wallpaper's own dim and blur. Only offered for this style: the blurred-art
                // backdrop derives its treatment from the cover it is showing, and Solid has
                // nothing to treat.
                NavigableSliderRow(
                    label = "Wallpaper Dim",
                    value = libraryWallpaperDim.coerceIn(0f, 1f),
                    stepCount = 20,
                    valueText = "${(libraryWallpaperDim * 100).toInt()}%",
                    onValueChange = { settingsViewModel.setLibraryWallpaperDim(it) },
                    focusId = "settings_library_wallpaper_dim",
                )
                NavigableSliderRow(
                    label = "Wallpaper Blur",
                    value = libraryWallpaperBlur.coerceIn(0f, 1f),
                    stepCount = 20,
                    valueText = "${(libraryWallpaperBlur * 100).toInt()}%",
                    onValueChange = { settingsViewModel.setLibraryWallpaperBlur(it) },
                    focusId = "settings_library_wallpaper_blur",
                )
            }
            ShowLibraryItemNamesSetting(
                enabled = showLibraryItemNames,
                onToggle = { settingsViewModel.setShowLibraryItemNames(it) },
                focusId = "settings_show_library_item_names"
            )
            AchievementProgressSetting(
                enabled = libraryAchievementProgress,
                onToggle = { settingsViewModel.setLibraryAchievementProgress(it) },
                focusId = "settings_achievement_progress"
            )
            LibrarySortFieldSetting(
                current = librarySortField,
                onSelected = { settingsViewModel.setLibrarySortField(it) },
                focusId = "settings_library_sort_field"
            )
            LibrarySortDirectionSetting(
                current = librarySortDirection,
                onSelected = { settingsViewModel.setLibrarySortDirection(it) },
                focusId = "settings_library_sort_direction"
            )
            LibraryViewModeSetting(
                current = libraryViewMode,
                onSelected = { settingsViewModel.setLibraryViewMode(it) },
                focusId = "settings_library_view_mode"
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            LibraryHintBarVisibleSetting(
                enabled = libraryHintBarVisible,
                onToggle = { settingsViewModel.setLibraryHintBarVisible(it) },
                focusId = "settings_library_hint_bar_visible"
            )
            ButtonGlyphStyleSetting(
                currentStyle = buttonGlyphStyle,
                onStyleChanged = { settingsViewModel.setButtonGlyphStyle(it) },
                focusId = "settings_button_glyph_style"
            )
            SwapFaceButtonsSetting(
                enabled = swapFaceButtons,
                onToggle = { settingsViewModel.setSwapFaceButtons(it) },
                focusId = "settings_swap_face_buttons"
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

            Text(
                text = stringResource(R.string.led_control_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            )
            LedEnabledSetting(
                enabled = ledEnabled,
                onToggle = { settingsViewModel.setLedEnabled(it) },
                focusId = "settings_led_enabled"
            )
            if (ledEnabled) {
                LedBrightnessSetting(
                    brightness = ledBrightness,
                    onBrightnessChanged = { settingsViewModel.setLedBrightness(it) },
                    focusId = "settings_led_brightness"
                )
                LedSaturationSetting(
                    saturationBoost = ledSaturationBoost,
                    onSaturationChanged = { settingsViewModel.setLedSaturationBoost(it) },
                    focusId = "settings_led_saturation"
                )
            }
            if (!ledEnabled) {
                Text(
                    text = stringResource(R.string.led_disabled_restore_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
                )
            }
            StatusBarModeSetting(
                currentMode = statusBarMode,
                onModeSelected = { settingsViewModel.setStatusBarMode(it) },
                focusId = "settings_status_bar_mode"
            )
            // The mode's own two rows, under a name, a rule and a sentence
            // saying what the mode is: these rows are about a thing the reader
            // may never have turned on, and "Handle Visibility" explains nothing on
            // its own. The rest of what used to be here (which screen, what a
            // tap does) is no longer a choice: the app always comes back on
            // the top screen, and a tap always opens the panel.
            Text(
                text = OVERLAY_SETTINGS_HEADER,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                text = OVERLAY_SETTINGS_EXPLANATION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp)
            )
            FoldHandleVisibilitySetting(
                alpha = foldHandleAlpha,
                onAlphaSelected = { settingsViewModel.setFoldHandleAlpha(it) },
                focusId = "settings_fold_dim",
            )
            FoldHandleSizeSetting(
                sizeDp = foldHandleSizeDp,
                onSizeSelected = { settingsViewModel.setFoldHandleSizeDp(it) },
                focusId = "settings_fold_tab_size",
            )
            FoldHandleReadingSizeSetting(
                recess = foldHandleRecess,
                onRecessSelected = { settingsViewModel.setFoldHandleRecess(it) },
                focusId = "settings_fold_handle_recess",
            )
            FoldHandleClockBatterySetting(
                on = foldHandleClockBattery,
                onToggled = { settingsViewModel.setFoldHandleClockBattery(it) },
                focusId = "settings_fold_handle_readout",
            )
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // ── Auto Game Detection ──
        ExpandableSettingsSection(title = "Auto Game Detection", focusId = "settings_section_auto_game_detection") {
            Text(
                text = "Auto-detect what game you're playing from your emulator's launcher and link it to your library.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val sourceDisplayName = when (lastPlayedSource) {
                LastPlayedSource.NOT_CONFIGURED -> "Not Configured"
                LastPlayedSource.RETROARCH -> "RetroArch"
                LastPlayedSource.ES_DE -> "ES-DE"
                LastPlayedSource.COCOON -> "Cocoon"
                LastPlayedSource.ACHIEVEMENTS -> "Achievements"
            }
            NavigableMenuItem(
                content = {
                    SettingsSelectorRow(
                        label = "Source",
                        currentValue = sourceDisplayName,
                    )
                },
                onClick = {
                    val all = LastPlayedSource.entries
                    val idx = all.indexOf(lastPlayedSource)
                    val next = all[(idx + 1) % all.size]
                    settingsViewModel.setLastPlayedSource(next)
                },
                focusId = "settings_last_played_source"
            )
            when (lastPlayedSource) {
                LastPlayedSource.RETROARCH -> RetroArchFolderSetting(
                    // Display path is the short folder name (mirrors ES-DE / Cocoon);
                    // raw URI remains the source of truth for SAF access via the
                    // separate retroArchHistoryFolder flow elsewhere. Legacy users
                    // who picked before this change get null path → "Not Set" until
                    // they re-pick once.
                    folderPath = retroArchHistoryFolderPath,
                    state = retroArchFolderState,
                    autoOpenHelpFor = pendingRetroArchHelpDialog,
                    onHelpDismissed = { settingsViewModel.dismissRetroArchHelpDialog() },
                    onFolderSelected = {
                        Log.d("FolderPicker", "Launching RetroArch folder picker")
                        retroArchFolderPickerLauncher.launch(null)
                    },
                    focusId = "settings_retroarch_folder"
                )
                LastPlayedSource.ES_DE -> {
                    ESDEFolderSetting(
                        folderPath = esdeFolderPath,
                        state = esdeFolderState,
                        autoOpenHelpFor = pendingESDEHelpDialog,
                        onHelpDismissed = { settingsViewModel.dismissESDEHelpDialog() },
                        onFolderSelected = {
                            Log.d("FolderPicker", "Launching ES-DE folder picker")
                            esdeFolderPickerLauncher.launch(null)
                        },
                        focusId = "settings_esde_folder"
                    )
                }
                LastPlayedSource.COCOON -> CocoonFolderSetting(
                    folderPath = cocoonFolderPath,
                    state = cocoonFolderState,
                    autoOpenHelpFor = pendingCocoonHelpDialog,
                    onHelpDismissed = { settingsViewModel.dismissCocoonHelpDialog() },
                    onFolderSelected = {
                        Log.d("FolderPicker", "Launching Cocoon folder picker")
                        cocoonFolderPickerLauncher.launch(null)
                    },
                    focusId = "settings_cocoon_folder"
                )
                LastPlayedSource.ACHIEVEMENTS -> Text(
                    text = stringResource(R.string.last_played_achievements_source_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                )
                LastPlayedSource.NOT_CONFIGURED -> Text(
                    text = stringResource(R.string.last_played_not_configured_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                )
            }
            // Show Last Played on Launch. First after Source, because it is what
            // configuring a source is usually for: the app opens on the game you
            // just quit. Needs a configured source; greyed out otherwise.
            val openLastPlayedApplicable = lastPlayedSource != LastPlayedSource.NOT_CONFIGURED
            if (!openLastPlayedApplicable) {
                NavigableMenuItem(
                    content = {
                        SettingsToggleRow(
                            label = "Show Last Played on Launch",
                            checked = false
                        )
                    },
                    onClick = { },
                    focusId = "settings_open_last_played_on_launch",
                    enabled = false
                )
                Text(
                    text = "Only available when a source is configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                )
            } else {
                NavigableMenuItem(
                    content = {
                        SettingsToggleRow(
                            label = "Show Last Played on Launch",
                            checked = openLastPlayedOnLaunch
                        )
                    },
                    onClick = { settingsViewModel.setOpenLastPlayedOnLaunch(!openLastPlayedOnLaunch) },
                    focusId = "settings_open_last_played_on_launch"
                )
                Text(
                    text = stringResource(R.string.open_last_played_on_launch_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                )
            }
            // Auto-Detect toggle. Disabled (greyed) for sources that don't
            // have an auto-refresh path (RetroArch and the no-file sources). Enabled and bound for ES-DE /
            // Cocoon.
            val autoDetectApplicable = lastPlayedSource == LastPlayedSource.ES_DE
                || lastPlayedSource == LastPlayedSource.COCOON
            if (!autoDetectApplicable) {
                NavigableMenuItem(
                    content = {
                        SettingsToggleRow(
                            label = "Auto-Detect Launched Games",
                            checked = false
                        )
                    },
                    onClick = { },
                    focusId = "settings_retroarch_auto_refresh",
                    enabled = false
                )
                Text(
                    text = "Only available when using ES-DE or Cocoon as source.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                )
            } else {
                NavigableMenuItem(
                    content = {
                        SettingsToggleRow(
                            label = "Auto-Detect Launched Games",
                            checked = retroArchAutoRefresh
                        )
                    },
                    onClick = { settingsViewModel.setRetroArchAutoRefresh(!retroArchAutoRefresh) },
                    focusId = "settings_retroarch_auto_refresh"
                )
                Text(
                    text = stringResource(R.string.retroarch_auto_refresh_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                )
            }
            NavigableMenuItem(
                content = {
                    SettingsToggleRow(
                        label = "Auto-Create Games",
                        checked = createVaultIfNotFound
                    )
                },
                onClick = { settingsViewModel.setCreateVaultIfNotFound(!createVaultIfNotFound) },
                focusId = "settings_retroarch_create_vault_if_not_found"
            )
            Text(
                text = stringResource(R.string.retroarch_create_vault_if_not_found_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            )

        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // ── Auto Asset Detection ──
        ExpandableSettingsSection(title = "Auto Asset Detection", focusId = "settings_section_auto_asset_detection") {
            Text(
                text = "Find cover art, manuals, and other media files on your device and link them to vaults automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val paths by settingsViewModel.searchPaths.collectAsState()
            val fileCount by settingsViewModel.indexFileCount.collectAsState()
            val pathStatuses by settingsViewModel.searchPathStatuses.collectAsState()
            LaunchedEffect(Unit) { settingsViewModel.reloadSearchPaths() }

            val removeDialogState by settingsViewModel.removeDialogState.collectAsState()
            (removeDialogState as? SettingsViewModel.RemoveDialogState.Shown)?.let { s ->
                RemoveSearchPathDialog(
                    pathLabel = s.pathLabel,
                    affectedVaultCount = s.affectedCount,
                    mode = s.mode,
                    onConfirm = { settingsViewModel.confirmRemoveDialog() },
                    onDismiss = { settingsViewModel.dismissRemoveDialog() },
                )
            }

            androidx.compose.material3.Text(
                text = "Auto-detected",
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            paths.filter { it.origin != com.twinlore.app.domain.MediaSearchPath.Origin.MANUAL }
                .forEach { sp ->
                    val status = pathStatuses[sp.id]
                    val isMissing = status is com.twinlore.app.domain.MediaSearchPathStatus.Missing
                    val disabled = sp.disabled
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .let { mod ->
                                if (disabled) mod.clickable { settingsViewModel.enableSearchPath(sp.id) }
                                else mod
                            },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = when {
                                disabled -> androidx.compose.material.icons.Icons.Default.Block
                                isMissing -> androidx.compose.material.icons.Icons.Default.Warning
                                else -> androidx.compose.material.icons.Icons.Default.Check
                            },
                            contentDescription = null,
                            tint = when {
                                disabled -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                isMissing -> androidx.compose.material3.MaterialTheme.colorScheme.error
                                else -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(
                                text = sp.label,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = if (disabled)
                                    androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            )
                            androidx.compose.material3.Text(
                                text = when {
                                    disabled -> "Disabled. Tap to re-enable."
                                    isMissing -> "Folder not found. Check Play History setting."
                                    else -> sp.uri
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = if (isMissing && !disabled)
                                    androidx.compose.material3.MaterialTheme.colorScheme.error
                                else
                                    androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        if (!disabled) {
                            androidx.compose.material3.IconButton(
                                onClick = { settingsViewModel.requestRemoveSearchPath(sp.id) }
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "Remove",
                                )
                            }
                        }
                    }
                }
            if (paths.none { it.origin != com.twinlore.app.domain.MediaSearchPath.Origin.MANUAL }) {
                androidx.compose.material3.Text(
                    text = "Set ES-DE or Cocoon folder under Play History to auto-detect.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Text(
                text = "Custom search paths",
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            paths.filter { it.origin == com.twinlore.app.domain.MediaSearchPath.Origin.MANUAL }
                .forEach { sp ->
                    val status = pathStatuses[sp.id]
                    val isMissing = status is com.twinlore.app.domain.MediaSearchPathStatus.Missing
                    val ok = status as? com.twinlore.app.domain.MediaSearchPathStatus.Ok
                    val driftedName = ok?.currentName?.takeIf { it.isNotBlank() && it != sp.label }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = when {
                                isMissing -> androidx.compose.material.icons.Icons.Default.Warning
                                driftedName != null -> androidx.compose.material.icons.Icons.Default.Edit
                                else -> androidx.compose.material.icons.Icons.Default.Check
                            },
                            contentDescription = null,
                            tint = when {
                                isMissing -> androidx.compose.material3.MaterialTheme.colorScheme.error
                                driftedName != null -> androidx.compose.ui.graphics.Color(0xFFFFA000)
                                else -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(
                                text = sp.label,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            )
                            androidx.compose.material3.Text(
                                text = when {
                                    isMissing -> "Folder not found. Restore the folder name or remove."
                                    driftedName != null -> "Renamed to \"$driftedName\". Tap to update the label."
                                    else -> sp.uri
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = when {
                                    isMissing -> androidx.compose.material3.MaterialTheme.colorScheme.error
                                    driftedName != null -> androidx.compose.ui.graphics.Color(0xFFFFA000)
                                    else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 2,
                                modifier = if (driftedName != null) {
                                    Modifier.clickable { settingsViewModel.relabelSearchPath(sp.id, driftedName) }
                                } else Modifier,
                            )
                        }
                        androidx.compose.material3.IconButton(
                            onClick = { settingsViewModel.requestRemoveSearchPath(sp.id) }
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = "Remove",
                            )
                        }
                    }
                }
            androidx.compose.material3.TextButton(
                onClick = { customSearchPathPickerLauncher.launch(null) }
            ) {
                androidx.compose.material3.Text("+ Add folder…")
            }

            Spacer(Modifier.height(16.dp))
            val isScanning = scanState is com.twinlore.app.data.library.MediaIndexer.ScanState.Running
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Button(
                    onClick = {
                        if (isScanning) settingsViewModel.cancelScan()
                        else settingsViewModel.startScan()
                    },
                ) {
                    if (isScanning) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Text("Cancel")
                    } else {
                        androidx.compose.material3.Text("Scan")
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    when (val s = scanState) {
                        is com.twinlore.app.data.library.MediaIndexer.ScanState.Running ->
                            androidx.compose.material3.Text(
                                text = "${s.scanned} files (${s.label})",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        is com.twinlore.app.data.library.MediaIndexer.ScanState.Done ->
                            androidx.compose.material3.Text(
                                text = "${s.total} files ready to be linked",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        else ->
                            androidx.compose.material3.Text(
                                text = "$fileCount files ready to be linked",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                    }
                }
            }
            if (isScanning) {
                androidx.compose.material3.Text(
                    text = "This will continue in the background. You can leave Settings.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // ── 2. Achievements (RetroAchievements + Steam) ──
        // When another screen sent the user here for this section (the journey's locked card),
        // expanding it is not enough: it sits below the fold, so scroll it to the top once its
        // position is known. One-shot, so the user's own scrolling is never fought afterwards.
        var achievementsSectionTop by remember { mutableStateOf<Float?>(null) }
        var scrolledToAchievements by remember { mutableStateOf(false) }
        LaunchedEffect(achievementsSectionTop) {
            if (expandAchievements && !scrolledToAchievements) {
                achievementsSectionTop?.let { top ->
                    scrolledToAchievements = true
                    settingsScrollState.animateScrollTo(top.roundToInt().coerceAtLeast(0))
                }
            }
        }
        Box(
            Modifier.onGloballyPositioned {
                achievementsSectionTop = it.positionInParent().y
            }
        ) {
        ExpandableSettingsSection(
            title = "Achievements",
            focusId = "settings_section_achievements",
            initiallyExpanded = expandAchievements,
            onExpanded = { if (expandAchievements) onAchievementsExpanded() }
        ) {
            val achievementsTab by settingsViewModel.achievementsTabVisible.collectAsState()
            NavigableMenuItem(
                content = {
                    SettingsToggleRow(
                        label = "Show Achievements tab",
                        checked = achievementsTab,
                    )
                },
                onClick = { settingsViewModel.setAchievementsTabVisible(!achievementsTab) },
                focusId = "settings_achievements_tab",
            )
            // ── RetroAchievements sub-section ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RetroAchievements",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (raCredentials != null) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Connected",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    } else {
                        Text(
                            text = "Not connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            RetroAchievementsSetting(
                raCredentials = raCredentials,
                raValidationState = raValidationState,
                onValidateAndSave = { user, key -> settingsViewModel.validateAndSaveRetroAchievementsCredentials(user, key) },
                onClear = { settingsViewModel.clearRetroAchievementsCredentials() },
                onClearValidationState = { settingsViewModel.clearRaValidationState() },
                focusId = "settings_ra"
            )
            val raKey by settingsViewModel.raApiKeyOverride.collectAsState()
            com.twinlore.app.ui.components.ApiKeyRow(
                label = "RetroAchievements API key",
                currentValue = raKey,
                onSave = settingsViewModel::setRaApiKeyOverride,
                // The key moved to its own tab on the RA settings page; the bare /settings link
                // lands on the profile tab, where there is no key to copy.
                keySourceUrl = "https://retroachievements.org/settings?tab=applications",
                keySourceLabel = "RetroAchievements Settings",
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

            // ── Steam sub-section ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Steam",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (steamCredentials != null) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Connected",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    } else {
                        Text(
                            text = "Not connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            SteamAchievementsSetting(
                steamCredentials = steamCredentials,
                steamValidationState = steamValidationState,
                onValidateAndSave = { vanityUrl -> settingsViewModel.validateAndSaveSteamCredentials(vanityUrl) },
                onClear = { settingsViewModel.clearSteamCredentials() },
                onClearValidationState = { settingsViewModel.clearSteamValidationState() },
                focusId = "settings_steam"
            )

            // Steam Web API key override — used by the game library,
            // recently-played detection, and Steam achievements.
            val steamKey by settingsViewModel.steamApiKeyOverride.collectAsState()
            var steamKeyEditRequest by remember { mutableStateOf(0) }
            com.twinlore.app.ui.components.ApiKeyRow(
                label = "Steam Web API key",
                currentValue = steamKey,
                onSave = settingsViewModel::setSteamApiKey,
                keySourceUrl = "https://steamcommunity.com/dev/apikey",
                keySourceLabel = "Steam API Key page",
                editRequest = steamKeyEditRequest,
            )
            val steamOwnKeyPrompt by settingsViewModel.steamOwnKeyPrompt.collectAsState()
            steamOwnKeyPrompt?.let { prompt ->
                AlertDialog(
                    onDismissRequest = settingsViewModel::dismissSteamOwnKeyPrompt,
                    title = { Text(prompt.title) },
                    text = { Text(prompt.body) },
                    confirmButton = {
                        TextButton(onClick = {
                            settingsViewModel.dismissSteamOwnKeyPrompt()
                            steamKeyEditRequest++
                        }) { Text("Add API key") }
                    },
                    dismissButton = {
                        TextButton(onClick = settingsViewModel::dismissSteamOwnKeyPrompt) { Text("Not now") }
                    },
                )
            }
        }
        } // end scroll-target Box (wraps the Achievements section only)

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // ── Library Data & Sync ──
        run {
            val syncUpdateNotices by settingsViewModel.syncUpdateNotices.collectAsState()
            val syncRestorableConflicts by settingsViewModel.syncRestorableConflicts.collectAsState()
            val syncRestorePoints by settingsViewModel.syncRestorePoints.collectAsState()
            ExpandableSettingsSection(
                title = "Library Data & Sync",
                focusId = "settings_section_storage",
            ) {
            val homeFolderDisplay = displayNameForUri(homeFolderUri)
            NavigableMenuItem(
                content = {
                    Column {
                        Text(
                            "Library folder",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = homeFolderDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                onClick = {
                    Log.d("FolderPicker", "Launching library folder picker")
                    homeFolderPickerLauncher.launch(null)
                },
                focusId = "settings_storage_folder"
            )
            // Storage migration progress dialog
            if (storageMigrating) {
                val migProgressFocus by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
                val migAccent = migProgressFocus.color
                AlertDialog(
                    onDismissRequest = { /* non-dismissable */ },
                    title = {
                        Text(
                            text = "Moving files…",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        when (val p = storageMigrationProgress) {
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
            LaunchedEffect(storageMigrationProgress) {
                val p = storageMigrationProgress
                if (p is com.twinlore.app.data.library.StorageFolderMigrationCoordinator.Progress.Done) {
                    val msg = when {
                        p.failed == 0 && p.migrated == 0 -> "Library folder switched."
                        p.failed == 0 -> "Moved ${p.migrated} file(s) to the new library folder."
                        else -> "Moved ${p.migrated} file(s); ${p.failed} failed."
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    storageMigrationProgress = null
                }
            }

            NavigableMenuItem(
                text = "Save Library",
                onClick = {
                    coroutineScope.launch {
                        try {
                            val entry = settingsViewModel.exportLibraryNow()
                            val msg = if (entry != null) {
                                "Saved in the library folder (${(entry.sizeBytes + 1023) / 1024} KB)"
                            } else {
                                "Couldn't save library. Pick a library folder first."
                            }
                            android.widget.Toast.makeText(
                                context,
                                msg,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Log.e("LibraryExport", "Save failed", e)
                            android.widget.Toast.makeText(
                                context,
                                "Save failed: ${e.message ?: "unknown error"}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                focusId = "settings_library_export"
            )

            // Library-switch confirm dialog
            val switchPending = pendingLibrarySwitch
            if (switchPending != null && switchPending.tooNewVersion != null) {
                // The newest snapshot in the picked folder was written by a newer
                // app build than this one can read. Refuse the load entirely —
                // never switch storage or import — and show the standard
                // "saved by a newer version" message.
                com.twinlore.app.ui.startup.LibrarySnapshotTooNewDialog(
                    savedVersion = switchPending.tooNewVersion,
                    supportedVersion = com.twinlore.app.data.local.SCHEMA_VERSION,
                    onPickDifferentFolder = {
                        pendingLibrarySwitch = null
                        homeFolderPickerLauncher.launch(null)
                    },
                    onUpdate = {
                        pendingLibrarySwitch = null
                        settingsViewModel.checkForUpdate()
                    },
                    onDismiss = { pendingLibrarySwitch = null },
                    dismissable = true,
                )
            } else if (switchPending != null) {
                val switchFocus by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
                val switchAccent = switchFocus.color
                val oldLabel = remember(switchPending.oldUri) {
                    displayNameForUri(switchPending.oldUri?.toString())
                }
                AlertDialog(
                    onDismissRequest = { pendingLibrarySwitch = null },
                    title = {
                        Text(
                            text = if (switchPending.newHasLibrary)
                                "Load library from this folder?"
                            else
                                "Use this folder as your library folder?",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        val savedClause = if (switchPending.oldUri != null && oldLabel.isNotBlank())
                            "Your current library has been saved to \"$oldLabel\"; pick that folder again to return to it. "
                        else ""
                        val message = when {
                            switchPending.newSnapshotCount > 1 ->
                                savedClause + "This folder has ${switchPending.newSnapshotCount} library snapshots. The most recent one will be loaded. The app will restart."
                            switchPending.newHasLibrary ->
                                savedClause + "The app will restart."
                            else ->
                                savedClause + "Your files will be moved into this folder."
                        }
                        Text(text = message, color = MaterialTheme.colorScheme.onSurface)
                    },
                    containerColor = LocalThemePalette.current.background,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val pending = switchPending
                                pendingLibrarySwitch = null
                                coroutineScope.launch {
                                    if (pending.newHasLibrary) {
                                        try {
                                            settingsViewModel.runExclusiveSave {
                                                // Import first, switch only on success: a
                                                // refused (too-new) load must never adopt
                                                // the unreadable folder as the home folder.
                                                settingsViewModel.loadLibraryFromFolder(pending.newUri)
                                            }
                                            com.twinlore.app.ui.utils.restartApp(context)
                                        } catch (e: Exception) {
                                            Log.e("LibrarySwitch", "Load failed", e)
                                            android.widget.Toast.makeText(
                                                context,
                                                "Load failed: ${e.message ?: "unknown error"}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } else {
                                        val migratable = if (pending.oldUri != null)
                                            settingsViewModel.countMigratableFiles() else 0
                                        if (pending.oldUri == null || migratable == 0) {
                                            settingsViewModel.switchStorageWithoutMigration(pending.newUri)
                                        } else {
                                            storageMigrating = true
                                            storageMigrationProgress = null
                                            try {
                                                settingsViewModel.migrateStorageFolder(pending.oldUri, pending.newUri) { p ->
                                                    storageMigrationProgress = p
                                                }
                                            } catch (e: Exception) {
                                                Log.e("StorageMigration", "Migration failed", e)
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Migration failed: ${e.message ?: "unknown error"}",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            } finally {
                                                storageMigrating = false
                                            }
                                        }
                                        runCatching { settingsViewModel.exportLibraryNow() }
                                        // Restart so the UI re-reads from the new storage folder,
                                        // matching the load-existing-library path's behaviour.
                                        com.twinlore.app.ui.utils.restartApp(context)
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = switchAccent)
                        ) {
                            Text(if (switchPending.newHasLibrary) "Load" else "Move")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { pendingLibrarySwitch = null },
                            colors = ButtonDefaults.textButtonColors(contentColor = switchAccent)
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // ── Library integrity ──
            // Two actions: Rescan re-checks resource_files for missing files;
            // Clean up opens a confirm dialog with the per-vault breakdown
            // and applies the removal policy.
            val integrityState by settingsViewModel.integrityState.collectAsState()
            val isIntegrityRescanning by settingsViewModel.isIntegrityRescanning.collectAsState()
            var showIntegrityCleanupConfirm by remember { mutableStateOf(false) }

            val integrityIsHealthy = integrityState is com.twinlore.app.domain.LibraryIntegrityState.Healthy
            val integritySubtitle = when (val s = integrityState) {
                is com.twinlore.app.domain.LibraryIntegrityState.Issues -> {
                    val unreadable = s.report.unreadable.size
                    if (s.report.totalMissing == 0) {
                        // Nothing is actually gone: these rows point into a folder
                        // whose permission this install has not been given yet.
                        val f = if (unreadable == 1) "file needs" else "files need"
                        "$unreadable $f their folder picked again."
                    } else {
                        "${s.report.totalMissing} missing across ${s.report.perVault.size} vaults" +
                            (if (s.report.orphans.isNotEmpty()) " + ${s.report.orphans.size} unfiled" else "") +
                            (if (unreadable > 0) " + $unreadable unreadable" else "")
                    }
                }
                com.twinlore.app.domain.LibraryIntegrityState.Healthy -> "No missing files."
                com.twinlore.app.domain.LibraryIntegrityState.Checking -> "Checking…"
                else -> "Tap to scan for missing files."
            }
            // Material green ~600 for the all-clear state. Falls back to the
            // theme's tertiary if you ever want palette-aware tinting.
            val integrityHealthyColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)

            NavigableMenuItem(
                content = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Rescan storage",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = integritySubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (integrityIsHealthy) integrityHealthyColor
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        if (isIntegrityRescanning) {
                            Text(
                                text = "Scanning…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (integrityIsHealthy) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "All files present",
                                tint = integrityHealthyColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                onClick = { settingsViewModel.runIntegrityCheck() },
                focusId = "settings_integrity_rescan"
            )

            val issuesNow = (integrityState as? com.twinlore.app.domain.LibraryIntegrityState.Issues)
                // Unreadable rows are not removable, so a report holding nothing else
                // must not offer a cleanup that would do nothing.
                ?.takeIf { it.report.totalMissing > 0 }
            if (issuesNow != null) {
                NavigableMenuItem(
                    content = {
                        SettingsSelectorRow(
                            label = "Clean up missing files",
                            currentValue = "",
                            subtitle = "Remove broken entries from the library."
                        )
                    },
                    onClick = { showIntegrityCleanupConfirm = true },
                    focusId = "settings_integrity_cleanup"
                )
            }

            // All-Files access is a speed setting, not a requirement, but without it
            // every artwork and guide lookup goes through SAF's per-child Binder
            // queries instead of one stat, which is what turns a rescan on a large
            // library into minutes. A reinstall revokes it silently and the startup
            // prompt is one-shot (never shown again once declined), so this row is
            // the only way back to it.
            val allFilesLifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            var allFilesGranted by remember {
                mutableStateOf(com.twinlore.app.data.esde.EsdeFsAccess.isGranted())
            }
            DisposableEffect(allFilesLifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        allFilesGranted = com.twinlore.app.data.esde.EsdeFsAccess.isGranted()
                    }
                }
                allFilesLifecycleOwner.lifecycle.addObserver(observer)
                onDispose { allFilesLifecycleOwner.lifecycle.removeObserver(observer) }
            }
            if (!allFilesGranted) {
                NavigableMenuItem(
                    content = {
                        SettingsSelectorRow(
                            label = "Speed up storage scans",
                            currentValue = "",
                            subtitle = "Grant all-files access. Scans read files directly instead of one folder lookup at a time."
                        )
                    },
                    onClick = { com.twinlore.app.ui.utils.openAllFilesAccessSettings(context) },
                    focusId = "settings_all_files_access"
                )
            }

            if (showIntegrityCleanupConfirm && issuesNow != null) {
                val report = issuesNow.report
                val deleted = report.perVault.count { it.willBeDeleted }
                val partials = report.perVault.filter { !it.willBeDeleted }
                val partialsCount = partials.size
                val partialsFiles = partials.sumOf { it.missing.size }
                val orphansCount = report.orphans.size
                val cleanupAccentState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
                val cleanupAccent = cleanupAccentState.color
                AlertDialog(
                    onDismissRequest = { showIntegrityCleanupConfirm = false },
                    title = {
                        Text(
                            text = "Clean up missing files?",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Headline
                            Text(
                                text = "${report.totalMissing} files across ${report.perVault.size} vaults" +
                                    if (orphansCount > 0) " + $orphansCount unfiled." else ".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                            Spacer(Modifier.height(8.dp))
                            // Per-vault breakdown
                            for (group in report.perVault) {
                                val detail = if (group.willBeDeleted) {
                                    "all ${group.totalResourceCount} files missing"
                                } else {
                                    "${group.missing.size} of ${group.totalResourceCount} files missing"
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = group.vaultName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (group.willBeDeleted) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Will be removed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            if (orphansCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Unfiled",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "$orphansCount files",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                            Spacer(Modifier.height(8.dp))
                            // Summary of action
                            val lines = buildList {
                                if (deleted > 0) {
                                    val v = if (deleted == 1) "vault" else "vaults"
                                    add("$deleted $v will be deleted entirely.")
                                }
                                if (partialsCount > 0) {
                                    val f = if (partialsFiles == 1) "file" else "files"
                                    val ov = if (partialsCount == 1) "1 other vault" else "$partialsCount other vaults"
                                    add("$partialsFiles $f will be removed from $ov.")
                                }
                                if (orphansCount > 0) {
                                    val f = if (orphansCount == 1) "unfiled file" else "unfiled files"
                                    add("$orphansCount $f will be removed.")
                                }
                                val unreadableCount = report.unreadable.size
                                if (unreadableCount > 0) {
                                    val f = if (unreadableCount == 1) "file is" else "files are"
                                    add(
                                        "$unreadableCount more $f only unreadable because " +
                                            "their folder has not been picked again yet. " +
                                            "Those are kept."
                                    )
                                }
                            }
                            for (line in lines) {
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    containerColor = LocalThemePalette.current.background,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showIntegrityCleanupConfirm = false
                                settingsViewModel.cleanupMissingFiles()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = cleanupAccent)
                        ) {
                            Text("Clean up")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showIntegrityCleanupConfirm = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = cleanupAccent)
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

            Text(
                text = "Cross-device sync",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Text(
                text = "Syncing works when all devices point at the same library folder through a sync app such as Syncthing or a cloud drive folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            NavigableMenuItem(
                text = "Sync now",
                onClick = {
                    settingsViewModel.syncNow()
                    android.widget.Toast.makeText(
                        context,
                        "Checking for changes…",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                focusId = "settings_sync_now"
            )
            // ── Restore points: pre-sync whole-library backups (last 3 kept) ──
            var pendingRestorePoint by remember {
                mutableStateOf<com.twinlore.app.data.library.sync.SyncRestorePoint?>(null)
            }
            fun formatRestorePoint(atMillis: Long): String =
                android.text.format.DateUtils.formatDateTime(
                    context,
                    atMillis,
                    android.text.format.DateUtils.FORMAT_SHOW_DATE or
                        android.text.format.DateUtils.FORMAT_SHOW_TIME or
                        android.text.format.DateUtils.FORMAT_SHOW_YEAR
                )
            if (syncRestorePoints.isNotEmpty()) {
                Text(
                    text = "Restore points",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)
                )
                for (point in syncRestorePoints) {
                    NavigableMenuItem(
                        text = "Sync on ${formatRestorePoint(point.atMillis)}",
                        onClick = { pendingRestorePoint = point },
                        focusId = "settings_sync_restorepoint_${point.atMillis}"
                    )
                }
            }
            // ── Per-conflict restore rows (inside the restore group) ──
            if (syncRestorableConflicts.isNotEmpty()) {
                Text(
                    text = "Overwritten by a recent sync. Tap to bring your version back:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            for (conflict in syncRestorableConflicts) {
                NavigableMenuItem(
                    text = "Restore previous version of ${conflict.name}",
                    onClick = {
                        settingsViewModel.restorePreviousVersion(conflict.vaultId)
                        android.widget.Toast.makeText(
                            context,
                            "Restored. Will sync to other devices",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    focusId = "settings_sync_restore_${conflict.vaultId}"
                )
            }
            val restorePending = pendingRestorePoint
            if (restorePending != null) {
                val restoreFocus by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
                val restoreAccent = restoreFocus.color
                AlertDialog(
                    onDismissRequest = { pendingRestorePoint = null },
                    title = {
                        Text(
                            text = "Restore library?",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Text(
                            text = "This restores your whole library to the state before the sync on " +
                                "${formatRestorePoint(restorePending.atMillis)}. Changes made since then " +
                                "on this device will be replaced, and the app will restart. The restored " +
                                "version will sync to your other devices.",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    containerColor = LocalThemePalette.current.background,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingRestorePoint = null
                                settingsViewModel.restoreToPoint(restorePending)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = restoreAccent)
                        ) {
                            Text("Restore & restart")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { pendingRestorePoint = null },
                            colors = ButtonDefaults.textButtonColors(contentColor = restoreAccent)
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            for (notice in syncUpdateNotices) {
                Text(
                    text = "⬆ ${notice.deviceLabel} has a newer version of Game Twin. Update this device to sync with it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (syncUpdateNotices.isNotEmpty()) {
                NavigableMenuItem(
                    text = "Dismiss update notices",
                    onClick = { settingsViewModel.dismissSyncUpdateNotices() },
                    focusId = "settings_sync_dismiss_notices"
                )
            }

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

            Text(
                text = "Danger zone",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Reset library entry — two-stage destructive flow.
            // Confirm metadata reset. Optionally also wipe
            // files in the storage folder. Both stages are explicit so an
            // accidental tap can't destroy data.
            val resetStageConfirm = remember { mutableStateOf(false) }
            val resetStageDeleteFiles = remember { mutableStateOf(false) }
            val resetWorking = remember { mutableStateOf(false) }
            NavigableMenuItem(
                text = "Reset Library",
                onClick = { resetStageConfirm.value = true },
                focusId = "settings_storage_reset_library"
            )

            if (resetStageConfirm.value) {
                AlertDialog(
                    onDismissRequest = { resetStageConfirm.value = false },
                    title = {
                        Text(
                            text = "Reset library?",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    text = {
                        Text(
                            text = "Permanently erases every vault, saved tab, marker, bookmark, and checklist. This cannot be undone.",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    containerColor = LocalThemePalette.current.background,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                resetStageConfirm.value = false
                                resetWorking.value = true
                                coroutineScope.launch {
                                    try {
                                        settingsViewModel.resetLibraryMetadata()
                                        resetStageDeleteFiles.value = true
                                    } catch (e: Exception) {
                                        Log.e("ResetLibrary", "Metadata reset failed", e)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Reset failed: ${e.message ?: "unknown error"}",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    } finally {
                                        resetWorking.value = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reset")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetStageConfirm.value = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (resetStageDeleteFiles.value) {
                AlertDialog(
                    onDismissRequest = {
                        resetStageDeleteFiles.value = false
                        android.widget.Toast.makeText(
                            context,
                            "Library reset. Storage files kept.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    },
                    title = {
                        Text(
                            text = "Also delete files?",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    text = {
                        Text(
                            text = "Library metadata is gone. Do you also want to permanently delete every file inside your library folder? Linked files in other folders are not affected. This cannot be undone.",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    containerColor = LocalThemePalette.current.background,
                    confirmButton = {
                        TextButton(
                            onClick = {
                                resetStageDeleteFiles.value = false
                                resetWorking.value = true
                                coroutineScope.launch {
                                    try {
                                        val n = settingsViewModel.deleteAllFilesInStorageFolder()
                                        android.widget.Toast.makeText(
                                            context,
                                            "Library reset and $n storage entries deleted.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    } catch (e: Exception) {
                                        Log.e("ResetLibrary", "File delete failed", e)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Files could not be deleted: ${e.message ?: "unknown error"}",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    } finally {
                                        resetWorking.value = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete files")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            resetStageDeleteFiles.value = false
                            android.widget.Toast.makeText(
                                context,
                                "Library reset. Storage files kept.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }) {
                            Text("Keep files")
                        }
                    }
                )
            }

            if (resetWorking.value) {
                AlertDialog(
                    onDismissRequest = { /* non-dismissable */ },
                    title = { Text("Working…", color = MaterialTheme.colorScheme.onSurface) },
                    text = { Text("Erasing library data…", color = MaterialTheme.colorScheme.onSurface) },
                    containerColor = LocalThemePalette.current.background,
                    confirmButton = {},
                    dismissButton = {}
                )
            }
        } // end ExpandableSettingsSection content
        } // end run

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // ── Artwork Scrapers ──
        ExpandableSettingsSection(
            title = "Artwork Scrapers",
            focusId = "settings_section_artwork_scrapers"
        ) {
            AutoScrapingSetting(
                enabled = autoBoxArtScraping,
                onToggle = { settingsViewModel.setAutoBoxArtScraping(it) },
                focusId = "settings_auto_scraping"
            )
            // Toggle is the inverse of the underlying preferScraperForArtwork setting:
            // ON means local media wins; OFF means the scraper takes over. Default ON
            // (preferScraperForArtwork stores false by default, so the inverse is true).
            val preferScraper by settingsViewModel.preferScraperForArtwork.collectAsState(initial = false)
            val preferLocalMedia = !preferScraper
            NavigableMenuItem(
                content = {
                    SettingsToggleRow(
                        label = "Prefer Local Images",
                        subtitle = "Use existing files in the auto asset detection as priority. Auto download will still fill in gaps if enabled.",
                        checked = preferLocalMedia,
                    )
                },
                onClick = { settingsViewModel.setPreferScraperForArtwork(preferLocalMedia) },
                focusId = "settings_prefer_local_media",
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            val sgdbKey by settingsViewModel.steamGridApiKeyOverride.collectAsState()
            val tgdbKey by settingsViewModel.theGamesDbApiKeyOverride.collectAsState()
            Text(
                text = "Override bundled scraper API keys. Leave blank to use the defaults.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val uriHandler = LocalUriHandler.current
            val linkStyle = androidx.compose.ui.text.SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            )
            val sgdbHintBase = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val sgdbHintText = buildAnnotatedString {
                append("If auto-download is not working, create a free account at ")
                pushStringAnnotation(tag = "URL", annotation = "https://www.steamgriddb.com/profile/preferences/api")
                withStyle(linkStyle) { append("steamgriddb.com") }
                pop()
                append(" and paste your API key above.")
            }
            com.twinlore.app.ui.components.ApiKeyRow(
                label = "SteamGridDB key",
                description = "Primary source for box art, heroes, and logos.",
                currentValue = sgdbKey,
                onSave = settingsViewModel::setSteamGridApiKeyOverride,
            )
            @Suppress("DEPRECATION")
            ClickableText(
                text = sgdbHintText,
                style = sgdbHintBase,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                onClick = { offset ->
                    sgdbHintText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { uriHandler.openUri(it.item) }
                },
            )
            com.twinlore.app.ui.components.ApiKeyRow(
                label = "TheGamesDB key",
                description = "Fallback source when SteamGridDB has no match.",
                currentValue = tgdbKey,
                onSave = settingsViewModel::setTheGamesDbApiKey,
            )
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // ── AI Assistant ──
        ExpandableSettingsSection(
            title = "AI Assistant",
            focusId = "settings_section_ai_assistant",
        ) {
            val geminiKey by settingsViewModel.geminiApiKey.collectAsState()
            val geminiModel by settingsViewModel.geminiModel.collectAsState()

            val aiBottomTab by settingsViewModel.aiBottomTabVisible.collectAsState()
            NavigableMenuItem(
                content = {
                    SettingsToggleRow(
                        label = "Show AI tab",
                        checked = aiBottomTab,
                    )
                },
                onClick = { settingsViewModel.setAiBottomTabVisible(!aiBottomTab) },
                focusId = "settings_ai_bottom_tab",
            )
            com.twinlore.app.ui.components.ApiKeyRow(
                label = "Gemini API key",
                currentValue = geminiKey,
                onSave = settingsViewModel::setGeminiApiKey,
                description = "Bring your own key. Used for the in-game AI assistant.",
                keySourceUrl = "https://aistudio.google.com/apikey",
                keySourceLabel = "Google AI Studio",
                allowDefault = false,
            )
            // Model picker — a list of common Gemini models (not a masked key field).
            // The current selection is shown as the subtitle so the default
            // (gemini-flash-latest) is always visible.
            // Mirrors AiAssistantScreen's AI_MODELS (refreshed 2026-08-24;
            // gemini-2.0-flash is shut down, the 3.x flash family is current).
            val aiModels = listOf(
                "gemini-flash-latest",
                "gemini-3.7-flash",
                "gemini-3.5-flash",
                "gemini-3.5-flash-lite",
                "gemini-2.5-pro",
            )
            var modelMenuOpen by remember { mutableStateOf(false) }
            NavigableMenuItem(
                content = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemini model", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                geminiModel,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        DropdownMenu(
                            expanded = modelMenuOpen,
                            onDismissRequest = { modelMenuOpen = false },
                        ) {
                            aiModels.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        settingsViewModel.setGeminiModel(m)
                                        modelMenuOpen = false
                                    },
                                    trailingIcon = if (m == geminiModel) {
                                        { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                                    } else null,
                                )
                            }
                        }
                    }
                },
                onClick = { modelMenuOpen = true },
                focusId = "settings_ai_model",
            )
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // ── 8. About ──
        ExpandableSettingsSection(title = "About", focusId = "settings_section_about") {
            AppVersionSetting()
            CheckForUpdatesSetting(settingsViewModel = settingsViewModel)
            NavigableMenuItem(
                text = stringResource(R.string.impressum_menu_title),
                onClick = { legalDocument = LegalDocument.IMPRESSUM },
                focusId = "settings_impressum"
            )
            NavigableMenuItem(
                text = stringResource(R.string.privacy_menu_title),
                onClick = { legalDocument = LegalDocument.PRIVACY },
                focusId = "settings_privacy",
            )
            NavigableMenuItem(
                text = stringResource(R.string.terms_menu_title),
                onClick = { legalDocument = LegalDocument.TERMS },
                focusId = "settings_terms",
            )
            NavigableMenuItem(
                text = stringResource(R.string.open_source_licenses_menu_item),
                onClick = { showLicensesDialog = true },
                focusId = "settings_licenses"
            )
            NavigableMenuItem(
                text = stringResource(R.string.credits_menu_title),
                onClick = { showCreditsDialog = true },
                focusId = "settings_credits",
            )
            val feedbackUriHandler = LocalUriHandler.current
            NavigableMenuItem(
                text = "Feedback & Bug Reports",
                onClick = { feedbackUriHandler.openUri("https://github.com/alpha-minus/game-twin/issues") },
                focusId = "settings_feedback"
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    MadeInBerlinSetting()
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // ── 9. Support Game Twin ──
        val supportUriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        var showSupportActivateDialog by remember { mutableStateOf(false) }
        var showRemoveKeyConfirm by remember { mutableStateOf(false) }
        val supportTier by settingsViewModel.licenseTier.collectAsState()
        val hasKey = supportTier != com.twinlore.app.data.license.LicenseTier.NONE
        ExpandableSettingsSection(title = "Support Game Twin", focusId = "settings_section_support_game_twin") {
            Text(
                text = "If Game Twin has earned a place on your handheld, consider supporting it. " +
                    "Your support is the only thing funding it. No ads, no trackers, " +
                    "no subscriptions. You'll unlock some special app themes as a thank-you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            NavigableMenuItem(
                content = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The same helping-hand mark the premium-locked app icons wear, so
                        // "support" reads as one idea wherever it shows up.
                        Icon(
                            imageVector = Icons.Filled.VolunteerActivism,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Chip in", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                onClick = {
                    // The cheapest edition's checkout, not the store front: one row, one tap,
                    // no browsing. The other editions are reachable from the checkout page.
                    supportUriHandler.openUri(com.twinlore.app.data.license.LicenseConfig.CHECKOUT_URL)
                },
                focusId = "settings_support_chip_in",
            )
            NavigableMenuItem(
                content = {
                    SettingsSelectorRow(
                        label = "Enter License Key",
                        currentValue = if (hasKey) supportTier.tierDisplayName() else "Tap to enter",
                    )
                },
                onClick = { showSupportActivateDialog = true },
                focusId = "settings_support_key",
            )
            if (hasKey) {
                NavigableMenuItem(
                    text = "Remove Key",
                    onClick = { showRemoveKeyConfirm = true },
                    focusId = "settings_support_remove_key",
                )
            }
        }
        if (showSupportActivateDialog) {
            ActivateLicenseDialog(
                settingsViewModel = settingsViewModel,
                onDismiss = { showSupportActivateDialog = false },
            )
        }
        if (showRemoveKeyConfirm) {
            AlertDialog(
                onDismissRequest = { showRemoveKeyConfirm = false },
                title = { Text("Remove your key?") },
                text = {
                    Text(
                        "This removes the supporter key from this device and re-locks the " +
                            "thank-you app themes. You can enter it again anytime.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRemoveKeyConfirm = false
                        coroutineScope.launch { settingsViewModel.deactivateLicense() }
                    }) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveKeyConfirm = false }) { Text("Cancel") }
                },
            )
        }
    }

    legalDocument?.let { doc ->
        LegalDocumentDialog(document = doc, onDismiss = { legalDocument = null })
    }

    if (showLicensesDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text(stringResource(R.string.open_source_licenses_title)) },
            text = {
                OpenSourceLicensesScreen(
                    onDismiss = { showLicensesDialog = false }
                )
            },
            containerColor = impressumDialogBackgroundColor,
            confirmButton = {
                TextButton(
                    onClick = { showLicensesDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = focusColor.color)
                ) {
                    Text(stringResource(R.string.impressum_close))
                }
            }
        )
    }

    if (showCreditsDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreditsDialog = false },
            title = { Text(stringResource(R.string.credits_menu_title)) },
            text = {
                CreditsScreen(
                    onDismiss = { showCreditsDialog = false }
                )
            },
            containerColor = impressumDialogBackgroundColor,
            confirmButton = {
                TextButton(
                    onClick = { showCreditsDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = focusColor.color)
                ) {
                    Text(stringResource(R.string.impressum_close))
                }
            }
        )
    }

}

/**
 * Human-friendly label for a stored folder URI. Returns "Not set" for null/empty, the path
 * for `file://` URIs, and a truncated last-path-segment for `content://` tree URIs.
 */
private fun displayNameForUri(uri: String?): String {
    if (uri.isNullOrBlank()) return "Not set"
    return try {
        val parsed = Uri.parse(uri)
        when (parsed.scheme) {
            "file" -> parsed.path ?: uri
            "content" -> parsed.lastPathSegment ?: uri
            else -> uri
        }
    } catch (e: Exception) {
        uri
    }
}

/**
 * Expandable settings section: header row toggles visibility of content.
 * Uses an animated chevron icon; focus and key handling via NavigableMenuItem.
 */
@Composable
private fun ExpandableSettingsSection(
    title: String,
    focusId: String,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    onExpanded: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded) {
            expanded = true
            onExpanded()
        }
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron_rotation"
    )

    Column {
        NavigableMenuItem(
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            onClick = { expanded = !expanded },
            focusId = focusId
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}

/**
 * Reusable row layout for ON/OFF toggle settings.
 * Shows label (+ optional subtitle) on the left, a decorative [Switch] on the right.
 * The switch is non-interactive; the enclosing [NavigableMenuItem] handles tap/key events.
 */
@Composable
private fun SettingsToggleRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val accentColor = focusColorState.color

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accentColor,
                checkedTrackColor = accentColor.copy(alpha = 0.4f)
            )
        )
    }
}

/**
 * Reusable row layout for multi-option cycle settings.
 * Shows label (+ optional subtitle) on the left, current value on the right.
 */
@Composable
private fun SettingsSelectorRow(
    label: String,
    currentValue: String,
    subtitle: String? = null,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = currentValue,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/**
 * App version display (informational, non-interactive).
 */
@Composable
private fun AppVersionSetting() {
    val versionName = BuildConfig.VERSION_NAME
    Text(
        text = "Version $versionName",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun CheckForUpdatesSetting(settingsViewModel: SettingsViewModel) {
    val isChecking by settingsViewModel.isCheckingForUpdate.collectAsState()
    val updateState by settingsViewModel.updateState.collectAsState()

    NavigableMenuItem(
        text = "Check for updates",
        onClick = { settingsViewModel.checkForUpdate() },
        focusId = "settings_check_updates"
    )

    when {
        isChecking -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Checking…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        updateState is UpdateState.Available -> {
            Text(
                text = "Update available: v${(updateState as UpdateState.Available).version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            )
        }
        updateState is UpdateState.UpToDate -> {
            Text(
                text = "You're up to date",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4CAF50),
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun MadeInBerlinSetting() {
    Text(
        text = "Designed & Developed in Berlin",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 4.dp)
    )
    Text(
        text = "by Mert Aslantürk",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    )
}

/**
 * Theme palette selection — grid of color swatches + System option.
 */
@Composable
private fun PaletteSetting(
    currentPalette: ThemePalette?,
    onPaletteSelected: (ThemePalette?) -> Unit,
    focusColor: FocusColor,
    focusId: String
) {
    val displayName = currentPalette?.displayName ?: "System"
    Column(modifier = Modifier.fillMaxWidth()) {
        NavigableMenuItem(
            content = {
                SettingsSelectorRow(label = "Theme", currentValue = displayName)
            },
            onClick = {
                val palettes = ThemePalette.values()
                val nextPalette = if (currentPalette == null) {
                    palettes.first()
                } else {
                    val currentIndex = palettes.indexOf(currentPalette)
                    if (currentIndex == palettes.lastIndex) null else palettes[currentIndex + 1]
                }
                onPaletteSelected(nextPalette)
            },
            focusId = focusId
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            item {
                PaletteSwatch(
                    background = Color.Black,
                    foreground = Color.White,
                    isSelected = currentPalette == null,
                    isSystem = true,
                    accentColor = focusColor.color,
                    onClick = { onPaletteSelected(null) }
                )
            }
            items(ThemePalette.values().toList()) { palette ->
                PaletteSwatch(
                    background = palette.background,
                    foreground = palette.foreground,
                    isSelected = palette == currentPalette,
                    isSystem = false,
                    accentColor = focusColor.color,
                    onClick = { onPaletteSelected(palette) }
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    background: Color,
    foreground: Color,
    isSelected: Boolean,
    isSystem: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSystem) {
                    Modifier.background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            0f to Color.Black,
                            0.5f to Color.Black,
                            0.5f to Color.White,
                            1f to Color.White
                        )
                    )
                } else {
                    Modifier.background(background)
                }
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = accentColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else if (isSystem) {
                    // System gradient swatch has no per-palette accent;
                    // keep the muted gray border.
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    // Inactive palette border picks up the palette's own
                    // foreground so the swatch reads as a paired bg+fg pill.
                    Modifier.border(
                        width = 1.dp,
                        color = foreground,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!isSystem) {
            Text(
                text = "A",
                color = foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Close the app so a launcher-icon (activity-alias) change applies on next launch.
 * We do NOT auto-relaunch: relaunching flashed a splash and then died (the launch
 * component was mid-flip), so we just finish the task and kill the process. The
 * user reopens Game Twin manually.
 */
private fun closeApp(context: android.content.Context) {
    var c: android.content.Context = context
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) {
            c.finishAffinity()
            break
        }
        c = c.baseContext
    }
    Runtime.getRuntime().exit(0)
}

/**
 * App-icon picker (Look & Feel). A d-pad row of preview thumbnails with lock
 * badges on tiers the user has not unlocked, plus a "Support the development"
 * link. Selecting an unlocked icon applies it to launcher + emblem + splash.
 */
@Composable
private fun AppIconSetting(
    currentIcon: com.twinlore.app.data.AppIcon,
    tier: com.twinlore.app.data.license.LicenseTier,
    tapHighScore: Int,
    onIconSelected: (com.twinlore.app.data.AppIcon) -> Unit,
    onIconTap: (com.twinlore.app.data.AppIcon) -> Unit,
    focusId: String,
) {
    // Pixel is free but earned via the tap-game high score; treat it as not
    // selectable until then (but it is NOT shown as a premium lock).
    fun pixelLocked(icon: com.twinlore.app.data.AppIcon) =
        icon == com.twinlore.app.data.AppIcon.PIXEL &&
            tapHighScore < com.twinlore.app.data.PIXEL_UNLOCK_TAP_SCORE
    fun selectable(icon: com.twinlore.app.data.AppIcon) = icon.isUnlockedBy(tier) && !pixelLocked(icon)

    Column(modifier = Modifier.fillMaxWidth()) {
        NavigableMenuItem(
            content = {
                SettingsSelectorRow(label = "App Theme", currentValue = currentIcon.displayName)
            },
            onClick = {
                // Cycle through the selectable icons only.
                val choices = com.twinlore.app.data.AppIcon.entries.filter { selectable(it) }
                if (choices.isNotEmpty()) {
                    val idx = choices.indexOf(currentIcon).coerceAtLeast(0)
                    onIconSelected(choices[(idx + 1) % choices.size])
                }
            },
            focusId = focusId,
        )

        // Grouped panel so the icons read as one set instead of separated chips. Background
        // follows the theme (black in dark, white in light) — no grey fill. Premium tiles
        // carry a tier-coloured "helping hand" (support) badge on their boundary; the earn-
        // in-game Pixel tile carries a padlock. Both sit on the tile's top edge.
        val panelColor = if (LocalThemePalette.current.isDark) Color.Black else Color.White
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(panelColor),
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                items(com.twinlore.app.data.AppIcon.entries.toList()) { icon ->
                    val premiumLocked = !icon.isUnlockedBy(tier)
                    val pixLocked = pixelLocked(icon)
                    val locked = premiumLocked || pixLocked
                    val selected = icon == currentIcon
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(56.dp)
                            .clickable { onIconTap(icon) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(icon.previewRes),
                            contentDescription = icon.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .then(if (locked) Modifier.alpha(0.4f) else Modifier),
                        )
                        // Boundary rectangle: accent ring when selected, else the tier
                        // colour when premium-locked. Unlocked, unselected tiles have no
                        // border so the set reads as one grouped strip.
                        val ringColor = when {
                            selected -> MaterialTheme.colorScheme.onSurface
                            premiumLocked -> com.twinlore.app.ui.license.tierLockColor(icon.requiredTier)
                            else -> Color.Transparent
                        }
                        if (ringColor != Color.Transparent) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .border(
                                        width = if (selected) 2.5.dp else 1.5.dp,
                                        color = ringColor,
                                        shape = RoundedCornerShape(12.dp),
                                    ),
                            )
                        }
                        if (locked) {
                            // Premium → tier-coloured helping hand (support). Pixel → padlock.
                            val badgeIcon = if (premiumLocked) Icons.Filled.VolunteerActivism else Icons.Filled.Lock
                            val badgeTint = if (premiumLocked) com.twinlore.app.ui.license.tierLockColor(icon.requiredTier)
                                else MaterialTheme.colorScheme.onSurface
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-6).dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(3.dp),
                            ) {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = if (premiumLocked) "Supporter theme" else "Locked",
                                    tint = badgeTint,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        // No store/sales copy here — just the icons. Tapping a locked one opens a
        // theme preview (handled by the caller via onIconTap -> ThemePreviewHost).
    }
}

/**
 * Font selection — row of "Aa" swatches in different fonts.
 */
@Composable
private fun FontSetting(
    currentFont: AppFont,
    onFontSelected: (AppFont) -> Unit,
    focusId: String
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        NavigableMenuItem(
            content = {
                SettingsSelectorRow(label = "Font", currentValue = currentFont.displayName)
            },
            onClick = {
                val fonts = AppFont.values()
                val currentIndex = fonts.indexOf(currentFont)
                val nextIndex = (currentIndex + 1) % fonts.size
                onFontSelected(fonts[nextIndex])
            },
            focusId = focusId
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(AppFont.values().toList()) { font ->
                FontSwatch(
                    font = font,
                    isSelected = font == currentFont,
                    context = context,
                    onClick = { onFontSelected(font) }
                )
            }
        }
    }
}

@Composable
private fun FontSwatch(
    font: AppFont,
    isSelected: Boolean,
    context: android.content.Context,
    onClick: () -> Unit
) {
    val fontFamily = font.toFontFamily(context)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Aa",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontFamily = fontFamily
        )
    }
}

/**
 * Cover art scaling mode — cycles Fit → Crop → Fill.
 */
@Composable
private fun ArtDisplayModeSetting(
    currentMode: ArtDisplayMode,
    onModeSelected: (ArtDisplayMode) -> Unit,
    focusId: String
) {
    val displayName = when (currentMode) {
        ArtDisplayMode.FIT -> "Fit"
        ArtDisplayMode.CROP -> "Crop"
        ArtDisplayMode.FILL -> "Fill"
    }
    NavigableMenuItem(
        content = {
            SettingsSelectorRow(
                label = "Cover Art Scaling",
                currentValue = displayName,
            )
        },
        onClick = {
            val nextMode = when (currentMode) {
                ArtDisplayMode.FIT -> ArtDisplayMode.CROP
                ArtDisplayMode.CROP -> ArtDisplayMode.FILL
                ArtDisplayMode.FILL -> ArtDisplayMode.FIT
            }
            onModeSelected(nextMode)
        },
        focusId = focusId
    )
}

/**
 * Global status cluster in the landscape top bars — cycles
 * Standard → Performance → Off.
 */
@Composable
private fun StatusBarModeSetting(
    currentMode: StatusBarMode,
    onModeSelected: (StatusBarMode) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsSelectorRow(
                label = "Status Bar",
                currentValue = currentMode.displayName,
                subtitle = "Performance adds system meters to the vault top bar; " +
                    "Off empties it.",
            )
        },
        onClick = {
            val modes = StatusBarMode.entries
            onModeSelected(modes[(modes.indexOf(currentMode) + 1) % modes.size])
        },
        focusId = focusId
    )
}

/** The subheader over the overlay's rows, named so the fold copy guard can reach it. */
internal const val OVERLAY_SETTINGS_HEADER = "Overlay Mode"

/** And the sentence under its rule, named for the same reason. */
internal const val OVERLAY_SETTINGS_EXPLANATION =
    "Display Game Twin on top of all apps, so guides, images and achievements stay on screen while you play. " +
        "Turn it on with the button in a game's top bar."

/** This row's label, named so the fold copy guard can reach it. */
internal const val FOLD_VISIBILITY_SETTING_LABEL = "Handle Visibility"

/** And its one-line explanation, named for the same reason. */
internal const val FOLD_VISIBILITY_SETTING_SUBTITLE =
    "How solid the handle is drawn. It stays at this, and no longer fades on its own."

/**
 * How solid the folded handle is drawn, 0% to 100%.
 *
 * Three named steps once, Visible / Faint / Transparent, cycled by tapping the
 * row. Three opacities is a coarse instrument against a game whose brightness
 * the app does not control, and the row could not show the value it was
 * setting anyway. The same slider lives on the panel's own Settings surface,
 * where you can see the handle while you drag it.
 *
 * The row exists at all because 0% is reachable and a handle at 0% is
 * invisible: something has to be able to bring it back that is not the handle.
 */
@Composable
private fun FoldHandleVisibilitySetting(
    alpha: Float,
    onAlphaSelected: (Float) -> Unit,
    focusId: String
) {
    NavigableSliderRow(
        label = FOLD_VISIBILITY_SETTING_LABEL,
        value = alpha.coerceIn(0f, 1f),
        stepCount = FoldHandleStyle.ALPHA_STEP_COUNT,
        valueText = FoldHandleStyle.alphaPercentLabel(alpha),
        onValueChange = { onAlphaSelected(FoldHandleStyle.alpha(it)) },
        focusId = focusId,
    )
    SettingsRowSubtitle(FOLD_VISIBILITY_SETTING_SUBTITLE)
}

/** This row's label, named so the fold copy guard can reach it. */
internal const val FOLD_TAB_SIZE_SETTING_LABEL = "Handle Size"

/** And its one-line explanation. */
internal const val FOLD_TAB_SIZE_SETTING_SUBTITLE =
    "How big the handle is, so it stays easy to hit."

/**
 * How big the folded handle is, in dp.
 *
 * Beside the visibility row because they are the same kind of choice about the
 * same object, and because a handle is a thing you have to be able to hit: a
 * small one on a handheld held in two hands is harder to reach than it looks.
 * The floor is the platform's minimum touch target, not a size worth arriving
 * at.
 */
@Composable
private fun FoldHandleSizeSetting(
    sizeDp: Int,
    onSizeSelected: (Int) -> Unit,
    focusId: String
) {
    NavigableSliderRow(
        label = FOLD_TAB_SIZE_SETTING_LABEL,
        value = FoldHandleStyle.sizeFraction(sizeDp),
        stepCount = FoldHandleStyle.SIZE_STEP_COUNT,
        valueText = FoldHandleStyle.sizeLabel(sizeDp),
        onValueChange = { onSizeSelected(FoldHandleStyle.sizeDpFromFraction(it)) },
        focusId = focusId,
    )
    SettingsRowSubtitle(FOLD_TAB_SIZE_SETTING_SUBTITLE)
}

/** This row's label, named so the fold copy guard can reach it. */
internal const val FOLD_READING_SIZE_SETTING_LABEL = "Handle Size While Reading"

/** And its one-line explanation. */
internal const val FOLD_READING_SIZE_SETTING_SUBTITLE =
    "How big the handle is drawn while a guide is open over it."

/**
 * How big the handle is drawn while the overlay's panel is up, as a fraction
 * of the size above.
 *
 * It was a hardcoded 0.62 for everybody: a judgement about how much of the
 * page a handle may cover, made on behalf of a reader whose guide, screen and
 * eyesight this code has never seen. 100% is the default now, so the handle
 * keeps the size it was chosen at, and the shrinking is there for whoever
 * wants the page back.
 */
@Composable
private fun FoldHandleReadingSizeSetting(
    recess: Float,
    onRecessSelected: (Float) -> Unit,
    focusId: String
) {
    NavigableSliderRow(
        label = FOLD_READING_SIZE_SETTING_LABEL,
        value = FoldHandleStyle.recessFraction(recess),
        stepCount = FoldHandleStyle.RECESS_STEP_COUNT,
        valueText = FoldHandleStyle.recessPercentLabel(recess),
        onValueChange = { onRecessSelected(FoldHandleStyle.recessFromFraction(it)) },
        focusId = focusId,
    )
    SettingsRowSubtitle(FOLD_READING_SIZE_SETTING_SUBTITLE)
}

/** This row's label, named so the fold copy guard can reach it. */
internal const val FOLD_READOUT_SETTING_LABEL = "Clock and Battery on Handle"

/** And its one-line explanation. */
internal const val FOLD_READOUT_SETTING_SUBTITLE =
    "Shows the time on the folded handle, the battery as five bars along its bottom, and a mark when the connection is lost."

/**
 * Whether the folded handle shows the time and the battery. Off by default,
 * so a handle nobody asked to change does not grow.
 */
@Composable
private fun FoldHandleClockBatterySetting(
    on: Boolean,
    onToggled: (Boolean) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsToggleRow(
                label = FOLD_READOUT_SETTING_LABEL,
                subtitle = FOLD_READOUT_SETTING_SUBTITLE,
                checked = on,
            )
        },
        onClick = { onToggled(!on) },
        focusId = focusId
    )
}

/**
 * The line under a slider row.
 *
 * [SettingsSelectorRow] carries its own subtitle, and [NavigableSliderRow]
 * cannot: the slider already spends the row's width. So it goes underneath,
 * the same shape the LED section's restore hint uses.
 */
@Composable
private fun SettingsRowSubtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

/**
 * Cover art aspect ratio — cycles through available ratios.
 */
@Composable
private fun GridDisplayRatioSetting(
    currentRatio: GridDisplayRatio,
    onRatioSelected: (GridDisplayRatio) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsSelectorRow(
                label = "Cover Art Aspect Ratio",
                currentValue = currentRatio.displayName,
            )
        },
        onClick = {
            val ratios = GridDisplayRatio.values()
            val currentIndex = ratios.indexOf(currentRatio)
            val nextIndex = (currentIndex + 1) % ratios.size
            onRatioSelected(ratios[nextIndex])
        },
        focusId = focusId
    )
}

/**
 * The library's full-screen backdrop: cycles Blurred art / Solid colour / Wallpaper.
 * See [LibraryBackgroundStyle] for what each one draws.
 *
 * Wallpaper draws the shared app wallpaper; picking or changing that
 * image is [LibraryWallpaperImageSetting], shown directly beneath this row when Wallpaper is
 * selected. With none set, Wallpaper draws nothing and the solid theme background shows through.
 */
@Composable
private fun LibraryBackgroundStyleSetting(
    currentStyle: LibraryBackgroundStyle,
    onStyleSelected: (LibraryBackgroundStyle) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsSelectorRow(
                label = "Library Background",
                currentValue = currentStyle.displayName,
            )
        },
        onClick = {
            val entries = LibraryBackgroundStyle.entries
            val nextIndex = (entries.indexOf(currentStyle) + 1) % entries.size
            onStyleSelected(entries[nextIndex])
        },
        focusId = focusId
    )
}

/**
 * Picker for the shared app wallpaper ([SettingsViewModel.setAppWallpaperImage]), shown only
 * while [LibraryBackgroundStyle.WALLPAPER] is selected above. One app-wide wallpaper, also drawn
 * behind the Journey stage.
 *
 * Follows the WallpaperRow pattern used there: an OpenDocument launcher, then
 * takePersistableUriPermission on the result so the Uri survives the next app restart.
 */
@Composable
private fun LibraryWallpaperImageSetting(
    currentImage: String?,
    onImageSelected: (String) -> Unit,
    focusId: String
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onImageSelected(uri.toString())
        }
    }
    NavigableMenuItem(
        content = {
            SettingsSelectorRow(
                label = "Wallpaper Image",
                currentValue = if (currentImage != null) "Change" else "Choose",
            )
        },
        onClick = { picker.launch(arrayOf("image/*")) },
        focusId = focusId
    )
}

/**
 * Toggle for automatic artwork downloading.
 */
@Composable
private fun AutoScrapingSetting(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsToggleRow(label = "Auto-Download Artwork", checked = enabled)
        },
        onClick = { onToggle(!enabled) },
        focusId = focusId
    )
}

/**
 * Toggle for showing or hiding vault names in the library carousel view.
 */
@Composable
private fun ShowLibraryItemNamesSetting(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsToggleRow(label = "Show Game Titles", checked = enabled)
        },
        onClick = { onToggle(!enabled) },
        focusId = focusId
    )
}

/**
 * Toggle for the dual-layer achievement progress bar + completion trophy shown on
 * library covers. Requires RA/Steam credentials; appears in every sort mode.
 */
@Composable
private fun AchievementProgressSetting(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsToggleRow(
                label = "Achievement Progress Bars",
                subtitle = "Show achievement progress + trophy on library covers (needs RA/Steam login)",
                checked = enabled
            )
        },
        onClick = { onToggle(!enabled) },
        focusId = focusId
    )
}

/**
 * Toggle for showing or hiding the bottom controller hint bar in library views.
 */
@Composable
private fun LibraryHintBarVisibleSetting(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsToggleRow(
                label = "Controller Hint Bar",
                subtitle = "Show button mappings at the bottom of the library",
                checked = enabled
            )
        },
        onClick = { onToggle(!enabled) },
        focusId = focusId
    )
}

/**
 * Library sort field — cycles through available sort fields.
 */
@Composable
private fun LibrarySortFieldSetting(
    current: LibrarySortField,
    onSelected: (LibrarySortField) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsSelectorRow(
                label = "Library sort",
                currentValue = current.label,
            )
        },
        onClick = {
            val entries = LibrarySortField.entries
            val nextIndex = (entries.indexOf(current) + 1) % entries.size
            onSelected(entries[nextIndex])
        },
        focusId = focusId
    )
}

/**
 * Library sort direction — cycles between Ascending and Descending.
 */
@Composable
private fun LibrarySortDirectionSetting(
    current: SortDirection,
    onSelected: (SortDirection) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsSelectorRow(
                label = "Sort direction",
                currentValue = when (current) {
                    SortDirection.ASC -> "Ascending"
                    SortDirection.DESC -> "Descending"
                },
            )
        },
        onClick = {
            val entries = SortDirection.entries
            val nextIndex = (entries.indexOf(current) + 1) % entries.size
            onSelected(entries[nextIndex])
        },
        focusId = focusId
    )
}

/**
 * Library view mode — cycles through Carousel, Large grid, Medium grid, Small grid, Launcher.
 */
@Composable
private fun LibraryViewModeSetting(
    current: LibraryViewMode,
    onSelected: (LibraryViewMode) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsSelectorRow(
                label = "Library view",
                currentValue = current.displayName,
            )
        },
        onClick = {
            val entries = LibraryViewMode.entries
            val nextIndex = (entries.indexOf(current) + 1) % entries.size
            onSelected(entries[nextIndex])
        },
        focusId = focusId
    )
}

/**
 * Adaptive accent colour — extracts accent from box art automatically.
 */
@Composable
private fun AutoFocusColorDetectionSetting(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsToggleRow(
                label = "Adaptive Accent Color",
                subtitle = "Extract accent from cover art",
                checked = enabled
            )
        },
        onClick = { onToggle(!enabled) },
        focusId = focusId
    )
}

/**
 * Navigation sounds toggle.
 */
@Composable
private fun FocusSoundSetting(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsToggleRow(label = "Navigation Sounds", checked = enabled)
        },
        onClick = { onToggle(!enabled) },
        focusId = focusId
    )
}

/**
 * Joystick LED ring toggle.
 */
@Composable
private fun LedEnabledSetting(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsToggleRow(label = "LED follows accent color", checked = enabled)
        },
        onClick = { onToggle(!enabled) },
        focusId = focusId
    )
}

/**
 * LED brightness slider. Drag or d-pad Left/Right to set 0%–100% (0% = LED off).
 * Scales the focus colour's brightness on the LED without touching its hue or saturation.
 */
@Composable
private fun LedBrightnessSetting(
    brightness: Int,
    onBrightnessChanged: (Int) -> Unit,
    focusId: String
) {
    NavigableSliderRow(
        label = "LED Brightness",
        value = LedSliderMath.brightnessToFraction(brightness),
        stepCount = LedSliderMath.BRIGHTNESS_STEP_COUNT,
        valueText = LedSliderMath.brightnessPercentLabel(brightness),
        onValueChange = { onBrightnessChanged(LedSliderMath.fractionToBrightness(it)) },
        focusId = focusId,
    )
}

/**
 * LED saturation slider. Drag or d-pad Left/Right to set Off–100%. Off leaves the focus
 * colour's saturation untouched; higher values make the LED more vivid without changing its hue.
 */
@Composable
private fun LedSaturationSetting(
    saturationBoost: Float,
    onSaturationChanged: (Float) -> Unit,
    focusId: String
) {
    NavigableSliderRow(
        label = "LED Saturation",
        value = saturationBoost.coerceIn(0f, 1f),
        stepCount = LedSliderMath.SATURATION_STEP_COUNT,
        valueText = LedSliderMath.saturationLabel(saturationBoost),
        onValueChange = { onSaturationChanged(LedSliderMath.snapFraction(it, LedSliderMath.SATURATION_STEP_COUNT)) },
        focusId = focusId,
    )
}

/**
 * Focusable settings row wrapping a Material [Slider]. Gamepad-first: when the row is
 * focused, d-pad Left/Right nudges the value one discrete step and consumes the event so
 * it never leaks into pane/tab navigation. Touch drag on the slider also works. The slider
 * itself is marked non-focusable so d-pad focus stays on the row (which carries the shared
 * focus visuals / LED / sound via [NavigableMenuItem]).
 *
 * [value] is a normalized 0f..1f fraction; [stepCount] is the number of intervals.
 */
@Composable
private fun NavigableSliderRow(
    label: String,
    value: Float,
    stepCount: Int,
    valueText: String,
    onValueChange: (Float) -> Unit,
    focusId: String,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val accent = focusColorState.color
    val delta = 1f / stepCount

    NavigableMenuItem(
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = value,
                    onValueChange = { onValueChange(LedSliderMath.snapFraction(it, stepCount)) },
                    valueRange = 0f..1f,
                    steps = (stepCount - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = accent.copy(alpha = 0.3f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusProperties { canFocus = false },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End,
                )
            }
        },
        onClick = { /* adjustment is via d-pad Left/Right or touch drag */ },
        focusId = focusId,
        onKeyEvent = { keyEvent ->
            val native = keyEvent.nativeKeyEvent
            when (native.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (native.action == AndroidKeyEvent.ACTION_DOWN) {
                        val step = if (native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT) delta else -delta
                        val next = LedSliderMath.snapFraction(value + step, stepCount)
                        if (next != value) onValueChange(next)
                    }
                    // Consume down AND up so horizontal presses never move focus.
                    true
                }
                else -> false
            }
        },
    )
}

/**
 * RetroArch folder picker row + auto-opened help dialog.
 *
 * State-aware badging (the old "✓ Detected" / "Wrong folder" /
 * "Logging not enabled" copy) is gone
 * — the help dialog now has single content regardless of failure mode.
 *
 * autoOpenHelpFor: when true, shows the help dialog once. Caller
 * consumes via onHelpDismissed so it doesn't re-open on
 * configuration change.
 */
@Composable
private fun RetroArchFolderSetting(
    folderPath: String?,
    state: com.twinlore.app.data.RetroArchHistoryParser.RetroArchFolderState?,
    autoOpenHelpFor: Boolean,
    onHelpDismissed: () -> Unit,
    onFolderSelected: () -> Unit,
    focusId: String,
) {
    var showHelp by remember { mutableStateOf(false) }
    LaunchedEffect(autoOpenHelpFor) {
        if (autoOpenHelpFor) showHelp = true
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Mirror ESDEFolderSetting's ✓/green affordance so the three sources
        // surface validity identically. `state == null` means "no folder set
        // yet" (initial flow value) — same render as the "Not Set" fallback.
        val isValid = state is com.twinlore.app.data.RetroArchHistoryParser.RetroArchFolderState.Valid
        val statusText = buildString {
            append(folderPath ?: "Not Set")
            if (isValid) append(" ✓")
        }
        NavigableMenuItem(
            content = {
                SettingsSelectorRow(
                    label = "RetroArch Folder",
                    currentValue = statusText,
                    valueColor = if (isValid) Color(0xFF4CAF50) else null
                )
            },
            onClick = onFolderSelected,
            focusId = focusId
        )
        Text(
            text = "Select the RetroArch parent folder (default: Internal Storage/RetroArch).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
        )
    }

    if (showHelp) {
        RetroArchHelpDialog(
            onPickFolder = {
                showHelp = false
                onHelpDismissed()
                onFolderSelected()
            },
            onDismiss = {
                showHelp = false
                if (autoOpenHelpFor) onHelpDismissed()
            },
        )
    }
}

/**
 * ES-DE folder setting for last played detection.
 * Banner copy depends on the ESDEFolderState (Valid / DebugModeNotEnabled /
 * NotESDEFolder / FolderUnreachable). Help dialog auto-opens after the picker
 * callback emits a non-Valid state.
 */
@Composable
private fun ESDEFolderSetting(
    folderPath: String?,
    state: com.twinlore.app.data.ESDELogParser.ESDEFolderState?,
    autoOpenHelpFor: com.twinlore.app.data.ESDELogParser.ESDEFolderState?,
    onHelpDismissed: () -> Unit,
    onFolderSelected: () -> Unit,
    focusId: String
) {
    var showHelp by remember { mutableStateOf(false) }
    val isValid = state is com.twinlore.app.data.ESDELogParser.ESDEFolderState.Valid
    val showBanner = folderPath != null &&
        state != null &&
        state !is com.twinlore.app.data.ESDELogParser.ESDEFolderState.Valid

    LaunchedEffect(autoOpenHelpFor) {
        if (autoOpenHelpFor != null) showHelp = true
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val statusText = buildString {
            append(folderPath ?: "Not Set")
            if (isValid) append(" ✓")
        }
        NavigableMenuItem(
            content = {
                SettingsSelectorRow(
                    label = "ES-DE Folder",
                    currentValue = statusText,
                    valueColor = if (isValid) Color(0xFF4CAF50) else null
                )
            },
            onClick = onFolderSelected,
            focusId = focusId
        )
        if (showBanner) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
                Text(
                    text = ESDE_BANNER_COPY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = { showHelp = true }) {
                    Text("Show how to fix")
                }
            }
        }
        Text(
            text = stringResource(R.string.esde_folder_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
        )
    }

    if (showHelp) {
        com.twinlore.app.ui.components.ESDELoggingHelpDialog(
            state = state,
            onPickFolder = {
                showHelp = false
                onHelpDismissed()
                onFolderSelected()
            },
            onDismiss = {
                showHelp = false
                onHelpDismissed()
            }
        )
    }
}

/**
 * Cocoon folder setting. Cocoon writes its log automatically, so the banner
 * either indicates a folder/launch problem or shows nothing.
 */
@Composable
private fun CocoonFolderSetting(
    folderPath: String?,
    state: com.twinlore.app.data.CocoonLogParser.CocoonFolderState?,
    autoOpenHelpFor: com.twinlore.app.data.CocoonLogParser.CocoonFolderState?,
    onHelpDismissed: () -> Unit,
    onFolderSelected: () -> Unit,
    focusId: String
) {
    var showHelp by remember { mutableStateOf(false) }
    val isValid = state is com.twinlore.app.data.CocoonLogParser.CocoonFolderState.Valid
    val showBanner = folderPath != null &&
        state != null &&
        state !is com.twinlore.app.data.CocoonLogParser.CocoonFolderState.Valid

    LaunchedEffect(autoOpenHelpFor) {
        if (autoOpenHelpFor != null) showHelp = true
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val statusText = buildString {
            append(folderPath ?: "Not Set")
            if (isValid) append(" ✓")
        }
        NavigableMenuItem(
            content = {
                SettingsSelectorRow(
                    label = "Cocoon Folder",
                    currentValue = statusText,
                    valueColor = if (isValid) Color(0xFF4CAF50) else null
                )
            },
            onClick = onFolderSelected,
            focusId = focusId
        )
        if (showBanner) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
                Text(
                    text = COCOON_BANNER_COPY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = { showHelp = true }) {
                    Text("Show how to fix")
                }
            }
        }
        Text(
            text = "Select the Cocoon parent folder (default: Internal Storage/Cocoon).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
        )
    }

    if (showHelp) {
        com.twinlore.app.ui.components.CocoonLoggingHelpDialog(
            state = state,
            onPickFolder = {
                showHelp = false
                onHelpDismissed()
                onFolderSelected()
            },
            onDismiss = {
                showHelp = false
                onHelpDismissed()
            }
        )
    }
}

/**
 * Accent color selection with color swatches.
 * When [enabled] is false (e.g. adaptive accent is on), manual selection is disabled.
 */
@Composable
private fun FocusColorSetting(
    currentColor: FocusColor,
    onColorSelected: (FocusColor) -> Unit,
    focusId: String,
    enabled: Boolean = true
) {
    // Theme-aware: hide accents that are unreadable on the current background.
    val isDarkTheme = com.twinlore.app.ui.LocalThemePalette.current.isDark
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!enabled) Modifier.alpha(0.6f) else Modifier)
    ) {
        NavigableMenuItem(
            content = {
                SettingsSelectorRow(label = "Accent Color", currentValue = currentColor.displayName)
            },
            onClick = {
                if (enabled) {
                    val colors = FocusColor.selectableFor(isDarkTheme)
                    val currentIndex = colors.indexOf(currentColor)
                    val nextIndex = (currentIndex + 1) % colors.size
                    onColorSelected(colors[nextIndex])
                }
            },
            focusId = focusId,
            enabled = enabled
        )
        
        // Color swatches - scrollable row (disabled when auto focus color is on)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(FocusColor.selectableFor(isDarkTheme)) { color ->
                ColorSwatch(
                    color = color,
                    isSelected = color == currentColor,
                    onClick = { if (enabled) onColorSelected(color) }
                )
            }
        }
    }
}

/**
 * RetroAchievements credentials setting.
 * Username field with auto-validate on submit; shows error inline.
 */
@Composable
private fun RetroAchievementsSetting(
    raCredentials: RaCredentials?,
    raValidationState: SettingsViewModel.RaValidationState,
    onValidateAndSave: (username: String, apiKey: String) -> Unit,
    onClear: () -> Unit,
    onClearValidationState: () -> Unit,
    focusId: String
) {

    var username by remember(raCredentials?.username) { mutableStateOf(raCredentials?.username ?: "") }

    LaunchedEffect(raCredentials) {
        if (raCredentials != null) {
            username = raCredentials.username
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
    ) {
        NavigableTextField(
            value = username,
            onValueChange = { username = it; onClearValidationState() },
            label = "Username",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            onSubmit = { if (username.isNotBlank()) onValidateAndSave(username, "") },
            trailingLoading = raValidationState is SettingsViewModel.RaValidationState.Loading
        )
        when (raValidationState) {
            is SettingsViewModel.RaValidationState.Error -> {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(raValidationState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> { }
        }
    }
}

/**
 * Steam Web API credentials setting with validation.
 * Mirrors RetroAchievementsSetting for visual consistency.
 */
@Composable
private fun SteamAchievementsSetting(
    steamCredentials: SteamCredentials?,
    steamValidationState: SettingsViewModel.SteamValidationState,
    onValidateAndSave: (vanityUrl: String) -> Unit,
    onClear: () -> Unit,
    onClearValidationState: () -> Unit,
    focusId: String
) {

    var vanityUrl by remember(steamCredentials?.vanityUrl) { mutableStateOf(steamCredentials?.vanityUrl ?: "") }

    LaunchedEffect(steamCredentials) {
        if (steamCredentials != null) {
            vanityUrl = steamCredentials.vanityUrl
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
    ) {
        NavigableTextField(
            value = vanityUrl,
            onValueChange = { vanityUrl = it; onClearValidationState() },
            label = "Profile URL or Vanity Name",
            placeholder = "e.g. myname or steamcommunity.com/id/myname",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            onSubmit = { if (vanityUrl.isNotBlank()) onValidateAndSave(vanityUrl) },
            trailingLoading = steamValidationState is SettingsViewModel.SteamValidationState.Loading
        )
        when (steamValidationState) {
            is SettingsViewModel.SteamValidationState.Error -> {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(steamValidationState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> { }
        }
    }
}

/**
 * Haptic feedback intensity — cycles None → Light → Medium → Strong.
 * Vibrates at the new intensity on each change so the user can feel the difference.
 */
@Composable
private fun HapticFeedbackSetting(
    currentIntensity: HapticFeedbackIntensity,
    onIntensityChanged: (HapticFeedbackIntensity) -> Unit,
    focusId: String
) {
    val view = LocalView.current
    NavigableMenuItem(
        content = {
            SettingsSelectorRow(
                label = "Haptic Feedback",
                currentValue = currentIntensity.displayName,
            )
        },
        onClick = {
            val intensities = HapticFeedbackIntensity.values()
            val currentIndex = intensities.indexOf(currentIntensity)
            val nextIndex = (currentIndex + 1) % intensities.size
            val nextIntensity = intensities[nextIndex]
            onIntensityChanged(nextIntensity)
            if (nextIntensity == HapticFeedbackIntensity.NONE) {
                // Selecting "No" must be felt as silence: kill the NavigableMenuItem
                // click haptic that just fired at the OLD intensity (a NONE preview
                // is a no-op, so nothing would otherwise pre-empt it).
                FeedbackManager.cancelHaptic()
            } else {
                // Preview the NEW intensity immediately (deliberate haptic-only call:
                // the NavigableMenuItem click already played the CONFIRM sound, and its
                // old-intensity haptic is pre-empted by this vibrate() call).
                FeedbackManager.playHaptic(view, FeedbackEvent.CONFIRM, nextIntensity)
            }
        },
        focusId = focusId
    )
}

/**
 * Preferred artwork style. Cycles Logo to Grid to Hero.
 */
@Composable
private fun DefaultAssetTypeSetting(
    assetType: DefaultAssetType,
    onAssetTypeChanged: (DefaultAssetType) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsSelectorRow(
                label = "Preferred Art Style",
                currentValue = assetType.displayName,
            )
        },
        onClick = {
            val assetTypes = DefaultAssetType.values()
            val currentIndex = assetTypes.indexOf(assetType)
            val nextIndex = (currentIndex + 1) % assetTypes.size
            onAssetTypeChanged(assetTypes[nextIndex])
        },
        focusId = focusId
    )
}

/**
 * Controller face-button glyph style shown in the library hint bar. Cycles Xbox letters ↔
 * PlayStation shapes.
 */
@Composable
private fun ButtonGlyphStyleSetting(
    currentStyle: com.twinlore.app.data.ButtonGlyphStyle,
    onStyleChanged: (com.twinlore.app.data.ButtonGlyphStyle) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            // Show the actual glyphs (not a platform name) so the choice reads by how it looks.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Controller Buttons",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                FaceButtonGlyphPreview(
                    style = currentStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = {
            val styles = com.twinlore.app.data.ButtonGlyphStyle.values()
            val currentIndex = styles.indexOf(currentStyle)
            val nextIndex = (currentIndex + 1) % styles.size
            onStyleChanged(styles[nextIndex])
        },
        focusId = focusId
    )
}

/**
 * Swap A↔B and X↔Y for controllers with a Nintendo-style button layout. Remaps the input
 * app-wide and flips the on-screen hint glyphs to match.
 */
@Composable
private fun SwapFaceButtonsSetting(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    focusId: String
) {
    NavigableMenuItem(
        content = {
            SettingsToggleRow(label = "Swap A/B · X/Y", checked = enabled)
        },
        onClick = { onToggle(!enabled) },
        focusId = focusId
    )
}

/**
 * Color swatch for focus color selection
 */
@Composable
private fun ColorSwatch(
    color: FocusColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color.color)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

/**
 * "Enter your key" dialog — the supporter key that unlocks the cosmetic app icons.
 * Deliberately avoids "license"/"purchase" language: it's framed as a thank-you key.
 * The Paste button reads the clipboard and unlocks in one tap (no need to touch the
 * field). Reused by the Support Development section and the locked app-icon prompt.
 * Closes itself on success; the unlocked icons appear reactively.
 */
@Composable
internal fun ActivateLicenseDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val palette = LocalThemePalette.current
    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = com.twinlore.app.data.FocusColor.CRIMSON)
    val focusColor = focusColorState.color
    val dialogBackgroundColor = palette.background
    val coroutineScope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }
    var activationError by remember { mutableStateOf<String?>(null) }
    var isActivating by remember { mutableStateOf(false) }
    val keyFocusRequester = remember { FocusRequester() }

    // Trim + activate a key; used by both the typed "Unlock" and one-tap "Paste".
    fun unlock(rawKey: String) {
        val key = rawKey.trim()
        if (key.isBlank()) {
            activationError = "Add your key, or tap Paste if you copied it"
            return
        }
        keyInput = key
        isActivating = true
        activationError = null
        coroutineScope.launch {
            try {
                val result = settingsViewModel.activateLicense(key, null)
                if (result.isSuccess()) {
                    android.widget.Toast.makeText(
                        context,
                        "Key accepted. Your themes are unlocked. Thank you!",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    onDismiss()
                } else {
                    activationError = (result as? com.twinlore.app.domain.Result.Failure)?.error?.message
                        ?: "That key didn't work. Double-check it and try again."
                }
            } catch (e: Exception) {
                activationError = e.message ?: "Something went wrong. Try again."
            } finally {
                isActivating = false
            }
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isActivating) onDismiss() },
        title = { Text("Enter your key") },
        text = {
            Column {
                Text(
                    "Paste the key from your supporter email to unlock your themes. No account needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                NavigableTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; activationError = null },
                    label = "Your key",
                    placeholder = "Paste or type your key",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isActivating,
                    externalFocusRequester = keyFocusRequester,
                )
                activationError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        containerColor = dialogBackgroundColor,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NavigableButton(
                    text = "Paste",
                    onClick = {
                        val clip = clipboard.getText()?.text?.trim().orEmpty()
                        if (clip.isBlank()) {
                            activationError = "Clipboard is empty. Copy your key first."
                        } else {
                            unlock(clip)
                        }
                    },
                    enabled = !isActivating,
                    containerColor = Color.Transparent,
                    contentColor = focusColor,
                    compact = true,
                )
                NavigableButton(
                    text = if (isActivating) "Unlocking..." else "Unlock",
                    onClick = { unlock(keyInput) },
                    enabled = !isActivating,
                    containerColor = Color.Transparent,
                    contentColor = focusColor,
                    compact = true,
                )
            }
        },
        dismissButton = {
            NavigableButton(
                text = "Cancel",
                onClick = { if (!isActivating) onDismiss() },
                enabled = !isActivating,
                containerColor = Color.Transparent,
                contentColor = focusColor,
                compact = true,
            )
        },
    )
}

