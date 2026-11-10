package com.twinlore.app.ui.screens.catalog

import android.content.Intent
import android.net.Uri
import android.view.Display
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.twinlore.app.R
import com.twinlore.app.data.catalog.RecommendedAppsCatalog
import com.twinlore.app.domain.Category
import com.twinlore.app.domain.DeviceType
import com.twinlore.app.domain.RecommendedApp
import com.twinlore.app.ui.LocalThemePalette
import kotlinx.coroutines.launch

private const val SOURCE_URL = "https://github.com/RJNY/Obtainium-Emulation-Pack"

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppCatalogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppCatalogViewModel = hiltViewModel(),
    mainViewModel: com.twinlore.app.ui.MainViewModel = hiltViewModel()
) {
    val palette = LocalThemePalette.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val deviceType by viewModel.deviceType.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val items by viewModel.filteredApps.collectAsState()
    val allApps by viewModel.allApps.collectAsState()
    val selectedKeys by viewModel.selectedKeys.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val hasActionableDownload = downloadStates.any { it.value !is ApkDownloadState.Installed }
    val inSelectionMode = selectedKeys.isNotEmpty()
    val allFilteredSelected = items.isNotEmpty() && items.all { it.id in selectedKeys }
    val countsByCategory = remember(allApps) { allApps.groupingBy { it.category }.eachCount() }
    val pmInstalledApps = remember(allApps) { allApps.filter { viewModel.isAppInstalled(it.id) }.map { it.id }.toSet() }
    val justInstalledApps = downloadStates.filterValues { it is ApkDownloadState.Installed }.keys
    val installedApps = pmInstalledApps + justInstalledApps
    val context = LocalContext.current
    val screenView = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCopyDialog by remember { mutableStateOf(false) }
    var showObtainiumDialog by remember { mutableStateOf(false) }
    // Track what action triggered the Obtainium dialog so we can proceed after install
    var pendingObtainiumAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // ON_RESUME lifecycle detection: check install results and refresh download states
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDownloadStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val n = viewModel.writeObtainiumImportFile(uri)
        val displayId = screenView.display?.displayId ?: Display.DEFAULT_DISPLAY
        if (n == 0) {
            Toast.makeText(context, "Could not write the file.", Toast.LENGTH_SHORT).show()
        } else if (viewModel.openObtainiumApp(displayId)) {
            Toast.makeText(
                context,
                "Saved $n apps. Open the file via Obtainium → Import/Export → Obtainium import.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            showObtainiumDialog = true
        }
    }

    BackHandler {
        if (inSelectionMode) {
            viewModel.clearSelection()
        } else {
            onBack()
        }
    }

    val listState = rememberLazyListState()

    // Left-stick scroll: polls analog stick, idle at 5Hz, active at 60Hz
    LaunchedEffect(Unit) {
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

    Box(modifier = modifier.fillMaxSize().background(palette.background)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = if (hasActionableDownload) 72.dp else 0.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Top bar (scrolls with everything else).
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (inSelectionMode) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = palette.foreground)
                        }
                        Text("${selectedKeys.size} selected", color = palette.foreground)
                        Spacer(Modifier.weight(1f))
                        SelectAllAction(
                            onClick = viewModel::toggleSelectAllFiltered,
                            allSelected = allFilteredSelected,
                            tint = palette.foreground
                        )
                        DownloadAction(onClick = viewModel::downloadSelected, tint = palette.foreground)
                        ExportAction(onClick = { showCopyDialog = true }, tint = palette.foreground)
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = palette.foreground)
                        }
                        Text("Recommended apps", color = palette.foreground, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                color = palette.foreground,
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(end = 12.dp).size(22.dp)
                            )
                        } else {
                            IconButton(onClick = {
                                viewModel.refresh { result ->
                                    val msg = when (result) {
                                        is RecommendedAppsCatalog.RefreshResult.Success -> "Lists updated."
                                        is RecommendedAppsCatalog.RefreshResult.Failure -> "Error."
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh catalog", tint = palette.foreground)
                            }
                        }
                        SelectAllAction(
                            onClick = viewModel::toggleSelectAllFiltered,
                            allSelected = allFilteredSelected,
                            tint = palette.foreground
                        )
                    }
                }
            }

            // Source attribution.
            item {
                Text(
                    text = "Data from RJNY's awesome list on GitHub",
                    color = palette.foreground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Device-type chips (above search).
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeviceType.values().forEach { type ->
                        FilterChip(
                            selected = type == deviceType,
                            onClick = { viewModel.onDeviceTypeSelected(type) },
                            label = { Text(type.displayName) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            // Search field.
            item {
                val focusManager = LocalFocusManager.current
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                    focusManager.moveFocus(FocusDirection.Up); true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    focusManager.moveFocus(FocusDirection.Down); true
                                }
                                else -> false
                            }
                        }
                )
            }

            // Tabs.
            item {
                ScrollableTabRow(
                    selectedTabIndex = Category.values().indexOf(selectedTab),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    edgePadding = 8.dp
                ) {
                    Category.values().forEach { cat ->
                        val count = countsByCategory[cat] ?: 0
                        Tab(
                            selected = cat == selectedTab,
                            onClick = { viewModel.onTabSelected(cat) },
                            text = { Text("${cat.displayName} ($count)") }
                        )
                    }
                }
            }

            // Empty state or rows.
            if (items.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
                    ) {
                        Text("No apps match this filter", color = palette.foreground)
                        TextButton(onClick = viewModel::clearFilters) { Text("Clear filters") }
                    }
                }
            } else {
                items(items, key = { it.id }) { app ->
                    val dlState = downloadStates[app.id]
                    if (dlState != null && dlState !is ApkDownloadState.Idle && dlState !is ApkDownloadState.Installed) {
                        DownloadingAppRow(
                            app = app,
                            state = dlState,
                            onInstall = { viewModel.installApp(app.id) },
                            onRetry = { viewModel.retryApp(app.id) },
                            onCancel = { viewModel.cancelApp(app.id) }
                        )
                    } else {
                        RecommendedAppRow(
                            app = app,
                            inSelectionMode = inSelectionMode,
                            isSelected = app.id in selectedKeys,
                            isInstalled = app.id in installedApps,
                            onOpenSource = { displayId -> viewModel.openSource(app, displayId) },
                            onDownload = { viewModel.downloadSingleApp(app.id) },
                            onAddToObtainium = { displayId ->
                                when (viewModel.openObtainium(app, displayId)) {
                                    AppCatalogViewModel.ObtainiumLaunchResult.Success -> Unit
                                    AppCatalogViewModel.ObtainiumLaunchResult.ObtainiumMissing -> {
                                        showObtainiumDialog = true
                                    }
                                }
                            },
                            onGuideSearch = { displayId -> viewModel.openGoogle(app, displayId) },
                            onYouTubeSearch = { displayId -> viewModel.openYouTube(app, displayId) },
                            onToggleSelected = { if (app.id !in installedApps) viewModel.toggleSelection(app.id) },
                            onLongPress = { if (app.id !in installedApps) viewModel.toggleSelection(app.id) }
                        )
                    }
                    Divider(color = palette.foreground.copy(alpha = 0.12f))
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )

        if (hasActionableDownload) {
            val readyCount = downloadStates.count { it.value is ApkDownloadState.Ready }
            val downloadingCount = downloadStates.count { it.value is ApkDownloadState.Resolving || it.value is ApkDownloadState.Downloading }
            val installedCount = downloadStates.count { it.value is ApkDownloadState.Installed }
            val failedCount = downloadStates.count { it.value is ApkDownloadState.Failed }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(palette.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Status line
                val statusParts = mutableListOf<String>()
                if (readyCount > 0) statusParts.add("$readyCount ready")
                if (downloadingCount > 0) statusParts.add("$downloadingCount downloading")
                if (installedCount > 0) statusParts.add("$installedCount installed")
                if (failedCount > 0) statusParts.add("$failedCount failed")
                Text(
                    statusParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.foreground.copy(alpha = 0.7f)
                )
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = viewModel::cancelDownloads) {
                        Text("Cancel all")
                    }
                    if (readyCount > 0) {
                        TextButton(onClick = viewModel::installAllReady) {
                            Text("Install next")
                        }
                    }
                }
            }
        }
    }

    if (showCopyDialog) {
        val count = selectedKeys.size
        AlertDialog(
            onDismissRequest = { showCopyDialog = false },
            title = { Text("Export $count apps for Obtainium?") },
            text = {
                Text("Pick a folder to save the JSON file, then open it via Obtainium → Import/Export → Obtainium import.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showCopyDialog = false
                    val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                        .format(java.util.Date())
                    saveFileLauncher.launch("game-twin-obtainium-$ts.json")
                }) {
                    Text("Choose folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showObtainiumDialog) {
        val obtainiumDlState = downloadStates["dev.imranr.obtainium"]
        AlertDialog(
            onDismissRequest = { showObtainiumDialog = false },
            title = { Text("Obtainium not installed") },
            text = {
                Column {
                    Text("Obtainium keeps all your sideloaded apps up to date automatically. Install it to use the Add to Obtainium and Export to Obtainium features.")
                    if (obtainiumDlState != null) {
                        Spacer(Modifier.padding(top = 8.dp))
                        when (obtainiumDlState) {
                            is ApkDownloadState.Resolving, is ApkDownloadState.Downloading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Downloading...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is ApkDownloadState.Ready -> {
                                Text("Download complete. Tap Install to continue.", style = MaterialTheme.typography.bodySmall)
                            }
                            is ApkDownloadState.Failed -> {
                                Text(obtainiumDlState.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            else -> {}
                        }
                    }
                }
            },
            confirmButton = {
                if (obtainiumDlState is ApkDownloadState.Ready) {
                    TextButton(onClick = {
                        viewModel.installApp("dev.imranr.obtainium")
                        showObtainiumDialog = false
                    }) {
                        Text("Install")
                    }
                } else if (obtainiumDlState == null || obtainiumDlState is ApkDownloadState.Failed) {
                    TextButton(onClick = {
                        viewModel.downloadObtainium()
                    }) {
                        Text("Download Obtainium")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showObtainiumDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Two-line top-bar action: a circle icon (filled when every visible row is
 * already selected, empty otherwise) over "All" text. Tapping toggles all-
 * visible into the selection.
 */
@Composable
private fun SelectAllAction(onClick: () -> Unit, allSelected: Boolean, tint: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (allSelected) "All visible selected" else "Select all visible",
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text("All", style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Two-line top-bar action: download icon over "Download" text. */
@Composable
private fun DownloadAction(onClick: () -> Unit, tint: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Download and install selected apps",
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text("Download", style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Two-line top-bar action: Obtainium icon over "Export" text. */
@Composable
private fun ExportAction(onClick: () -> Unit, tint: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_obtainium),
            contentDescription = "Export Obtainium import file",
            tint = Color.Unspecified,
            modifier = Modifier.size(22.dp)
        )
        Text("Export", style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun DownloadingAppRow(
    app: RecommendedApp,
    state: ApkDownloadState,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = LocalThemePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                color = palette.foreground,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            when (state) {
                is ApkDownloadState.Resolving -> {
                    Text("Finding latest version...", color = palette.foreground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
                is ApkDownloadState.Downloading -> {
                    if (state.totalBytes > 0) {
                        Text(
                            "${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.totalBytes)}",
                            color = palette.foreground.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = state.progress ?: 0f,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 16.dp),
                            color = palette.foreground
                        )
                    } else {
                        Text("Downloading...", color = palette.foreground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 16.dp),
                            color = palette.foreground
                        )
                    }
                }
                is ApkDownloadState.Ready -> {
                    Text(state.version ?: "Ready to install", color = palette.foreground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
                is ApkDownloadState.Installing -> {
                    Text("Installing...", color = palette.foreground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
                is ApkDownloadState.Installed -> {
                    Text("Installed", color = palette.foreground.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
                is ApkDownloadState.Failed -> {
                    Text(state.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                else -> {}
            }
        }

        when (state) {
            is ApkDownloadState.Resolving, is ApkDownloadState.Downloading -> {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = palette.foreground, modifier = Modifier.size(20.dp))
                }
            }
            is ApkDownloadState.Ready -> {
                TextButton(onClick = onInstall) { Text("Install") }
            }
            is ApkDownloadState.Installing -> {
                CircularProgressIndicator(color = palette.foreground, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            is ApkDownloadState.Installed -> {
                Icon(Icons.Default.CheckCircle, contentDescription = "Installed", tint = palette.foreground.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
            }
            is ApkDownloadState.Failed -> {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecommendedAppRow(
    app: RecommendedApp,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    isInstalled: Boolean,
    onOpenSource: (Int) -> Unit,
    onDownload: () -> Unit,
    onAddToObtainium: (Int) -> Unit,
    onGuideSearch: (Int) -> Unit,
    onYouTubeSearch: (Int) -> Unit,
    onToggleSelected: () -> Unit,
    onLongPress: () -> Unit
) {
    val palette = LocalThemePalette.current
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (inSelectionMode) Modifier.combinedClickable(
                    onClick = onToggleSelected,
                    onLongClick = onLongPress
                ) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inSelectionMode) {
            Icon(
                imageVector = if (isInstalled) Icons.Default.CheckCircle
                    else if (isSelected) Icons.Default.CheckCircle
                    else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isInstalled) "Already installed" else if (isSelected) "Selected" else "Not selected",
                tint = if (isInstalled) palette.foreground.copy(alpha = 0.3f) else palette.foreground,
                modifier = Modifier.padding(end = 12.dp)
            )
        } else if (isInstalled) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Already installed",
                tint = palette.foreground.copy(alpha = 0.4f),
                modifier = Modifier.padding(end = 12.dp).size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f).then(
                if (!inSelectionMode) Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                ) else Modifier
            )
        ) {
            Text(
                text = app.name,
                color = palette.foreground,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (app.about.isNotBlank()) {
                Text(
                    text = app.about,
                    color = palette.foreground.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (!inSelectionMode) {
            val displayId = view.display?.displayId ?: Display.DEFAULT_DISPLAY
            IconButton(onClick = { onOpenSource(displayId) }) {
                Icon(Icons.Default.OpenInNew, contentDescription = "Open project page", tint = palette.foreground)
            }
            if (!isInstalled && app.category != Category.TrackOnly) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download and install", tint = palette.foreground)
                }
            }
            IconButton(onClick = { onAddToObtainium(displayId) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_obtainium),
                    contentDescription = "Add to Obtainium",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = { onGuideSearch(displayId) }) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Search for Android setup guide",
                    tint = palette.foreground
                )
            }
            IconButton(onClick = { onYouTubeSearch(displayId) }) {
                Icon(Icons.Default.Videocam, contentDescription = "YouTube setup guide", tint = palette.foreground)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
    else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
}
