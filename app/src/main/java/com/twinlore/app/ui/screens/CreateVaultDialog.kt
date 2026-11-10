package com.twinlore.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.twinlore.app.data.FocusColor
import com.twinlore.app.data.GameNameSuggestionProvider
import com.twinlore.app.data.HapticFeedbackIntensity
import com.twinlore.app.data.LedTurnOffTime
import com.twinlore.app.ui.navigation.NavigableButton
import com.twinlore.app.ui.navigation.NavigableTextField
import com.twinlore.app.ui.theme.getContrastingTextColor
import com.twinlore.app.ui.utils.FeedbackEvent
import com.twinlore.app.ui.utils.runWithHapticAndLed
import kotlinx.coroutines.delay

private const val RA_BASE_URL = "https://retroachievements.org"

@Composable
fun CreateVaultDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onAddAotW: (gameName: String, raGameId: Int) -> Unit = { name, _ -> onConfirm(name) },
    modifier: Modifier = Modifier,
    suggestionProvider: GameNameSuggestionProvider? = null
) {
    val view = LocalView.current
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val aotWViewModel: AotWViewModel = hiltViewModel()

    val focusColorState by settingsViewModel.focusColor.collectAsState(initial = FocusColor.CRIMSON)
    val focusColor = focusColorState.color
    val hapticFeedbackIntensity by settingsViewModel.hapticFeedbackIntensity.collectAsState(initial = HapticFeedbackIntensity.MEDIUM)
    val ledEnabled by settingsViewModel.ledEnabled.collectAsState(initial = true)
    val ledBrightness by settingsViewModel.ledBrightness.collectAsState(initial = 127)
    val ledSaturationBoost by settingsViewModel.ledSaturationBoost.collectAsState(initial = 0.75f)
    val ledTurnOffTime by settingsViewModel.ledTurnOffTime.collectAsState(initial = LedTurnOffTime.SEC_15)

    val menuBackgroundColor = MaterialTheme.colorScheme.background
    val menuTextColor = MaterialTheme.colorScheme.onBackground

    val aotWState by aotWViewModel.aotWState.collectAsState()

    var vaultName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val textFieldFocusRequester = remember { FocusRequester() }
    val createButtonFocusRequester = remember { FocusRequester() }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }
    var suggestionsSuppressed by remember { mutableStateOf(false) }

    LaunchedEffect(vaultName) {
        if (suggestionsSuppressed) {
            suggestions = emptyList()
            isLoadingSuggestions = false
            return@LaunchedEffect
        }
        if (vaultName.isNotBlank() && suggestionProvider != null) {
            isLoadingSuggestions = true
            val raw = suggestionProvider.getSuggestions(vaultName, limit = Int.MAX_VALUE)
            val trimmed = vaultName.trim()
            suggestions = raw.filter { !it.equals(trimmed, ignoreCase = true) }
            isLoadingSuggestions = false
        } else {
            suggestions = emptyList()
            isLoadingSuggestions = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = menuBackgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // ===== Manual Entry Section (first) =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Create New",
                        style = MaterialTheme.typography.titleMedium,
                        color = menuTextColor
                    )
                    IconButton(
                        onClick = {
                            runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs, event = FeedbackEvent.BACK, action = onDismiss)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = menuTextColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                NavigableTextField(
                    value = vaultName,
                    onValueChange = {
                        vaultName = it
                        errorMessage = null
                        suggestionsSuppressed = false
                    },
                    label = "Game Name",
                    placeholder = "Enter any game name...",
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                    externalFocusRequester = textFieldFocusRequester,
                    trailingLoading = isLoadingSuggestions
                )
                Text(
                    "Any name works — no worries if your game is not in the autocomplete list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = menuTextColor.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                LaunchedEffect(Unit) {
                    delay(100)
                    try { textFieldFocusRequester.requestFocus() } catch (_: Throwable) {}
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (suggestions.isNotEmpty() && vaultName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = menuBackgroundColor)
                    ) {
                        LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                            items(suggestions) { suggestion ->
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vaultName = suggestion
                                            suggestionsSuppressed = true
                                            suggestions = emptyList()
                                            try {
                                                createButtonFocusRequester.requestFocus()
                                            } catch (_: Throwable) {}
                                        }
                                        .padding(12.dp),
                                    color = menuTextColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavigableButton(
                        text = "Create",
                        onClick = {
                            runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                                val trimmedName = vaultName.trim()
                                if (trimmedName.isBlank()) {
                                    errorMessage = "Game name cannot be empty"
                                } else if (trimmedName.length > 100) {
                                    errorMessage = "Game name must be 100 characters or less"
                                } else {
                                    onConfirm(trimmedName)
                                    onDismiss()
                                }
                            }
                        },
                        containerColor = focusColor,
                        contentColor = focusColor.getContrastingTextColor(),
                        compact = true,
                        externalFocusRequester = createButtonFocusRequester
                    )
                }

                // ===== AotW Section (below manual entry) =====
                when (val state = aotWState) {
                    is AotWUiState.Loading -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = menuTextColor.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Or if you're looking for a new game idea, try out:",
                            style = MaterialTheme.typography.bodySmall,
                            color = menuTextColor.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = focusColor)
                        }
                    }

                    is AotWUiState.Loaded -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = menuTextColor.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Or if you're looking for a new game idea, try out:",
                            style = MaterialTheme.typography.bodySmall,
                            color = menuTextColor.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        AotWHeroSection(
                            state = state,
                            focusColor = focusColor,
                            menuTextColor = menuTextColor,
                            onAddToLibrary = {
                                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                                    onAddAotW(state.gameTitle, state.gameId)
                                    onDismiss()
                                }
                            },
                            onExploreGame = {
                                runWithHapticAndLed(view, hapticFeedbackIntensity, ledEnabled, focusColor, ledBrightness, ledSaturationBoost, ledTurnOffTime.inactivityMs) {
                                    val query = Uri.encode("\"${state.gameTitle}\" gameplay")
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                                    context.startActivity(intent)
                                }
                            },
                            onOpenRa = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://retroachievements.org"))
                                context.startActivity(intent)
                            }
                        )
                    }

                    is AotWUiState.Error -> { /* Silent — manual entry only */ }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AotWHeroSection(
    state: AotWUiState.Loaded,
    focusColor: Color,
    menuTextColor: Color,
    onAddToLibrary: () -> Unit,
    onExploreGame: () -> Unit,
    onOpenRa: () -> Unit
) {
    // Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "ACHIEVEMENT OF THE WEEK",
            style = MaterialTheme.typography.labelMedium,
            color = focusColor,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        AotWViewModel.formatCountdown(state.endsAtMillis)?.let { countdown ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = menuTextColor.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    countdown,
                    style = MaterialTheme.typography.labelSmall,
                    color = menuTextColor.copy(alpha = 0.5f)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    // Box art hero with game icon badge
    if (state.imageBoxArt != null) {
        Box {
            AsyncImage(
                model = "$RA_BASE_URL${state.imageBoxArt}",
                contentDescription = "${state.gameTitle} box art",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            if (state.imageIcon != null) {
                AsyncImage(
                    model = "$RA_BASE_URL${state.imageIcon}",
                    contentDescription = "${state.gameTitle} icon",
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Thumbnail row: title screen + in-game screenshot
    if (state.imageTitle != null || state.imageIngame != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.imageTitle?.let { path ->
                AsyncImage(
                    model = "$RA_BASE_URL$path",
                    contentDescription = "Title screen",
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 100.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            state.imageIngame?.let { path ->
                AsyncImage(
                    model = "$RA_BASE_URL$path",
                    contentDescription = "In-game screenshot",
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 100.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Game title + console
    Text(
        state.gameTitle,
        style = MaterialTheme.typography.titleLarge,
        color = menuTextColor,
        fontWeight = FontWeight.Bold
    )
    Text(
        "Platform - ${state.consoleName}",
        style = MaterialTheme.typography.bodyMedium,
        color = menuTextColor.copy(alpha = 0.6f)
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Game metadata: genre, publisher, release date
    val metadataItems = buildList {
        state.genre?.let { add("Genre: $it") }
        state.publisher?.let { add("Publisher: $it") }
        state.released?.let { add("Released: $it") }
    }
    if (metadataItems.isNotEmpty()) {
        metadataItems.forEach { item ->
            Text(
                item,
                style = MaterialTheme.typography.bodySmall,
                color = menuTextColor.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Achievement card with badge image
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = focusColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.achievementBadgeUrl != null) {
                    AsyncImage(
                        model = state.achievementBadgeUrl,
                        contentDescription = "Achievement badge",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    state.achievementTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = menuTextColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Card(
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = focusColor)
                ) {
                    Text(
                        "${state.achievementPoints} pts",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = focusColor.getContrastingTextColor(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                state.achievementDescription,
                style = MaterialTheme.typography.bodySmall,
                color = menuTextColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "by ${state.achievementAuthor}",
                style = MaterialTheme.typography.bodySmall,
                color = menuTextColor.copy(alpha = 0.5f)
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    // Stats row
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatChip("${state.totalPlayers} players", menuTextColor, Icons.Default.People)
        if (state.numAchievements > 0) {
            StatChip("${state.numAchievements} achievements", menuTextColor, Icons.Default.EmojiEvents)
        }
        AotWViewModel.formatMedianTime(state.medianTimeToBeatSeconds)?.let {
            StatChip("Beat: $it", menuTextColor, Icons.Default.Timer)
        }
        AotWViewModel.formatMedianTime(state.medianTimeToMasterSeconds)?.let {
            StatChip("Master: $it", menuTextColor, Icons.Default.Timer)
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    // Action buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NavigableButton(
            text = "Add to Library",
            onClick = onAddToLibrary,
            containerColor = focusColor,
            contentColor = focusColor.getContrastingTextColor(),
            compact = true,
            modifier = Modifier.weight(1f)
        )
        NavigableButton(
            text = "Explore Game",
            onClick = onExploreGame,
            containerColor = Color.Transparent,
            contentColor = focusColor,
            compact = true,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    // RA description (footer)
    Text(
        "RetroAchievements is a community that adds achievements to classic retro games. Each week, one achievement is featured as a challenge for players.",
        style = MaterialTheme.typography.bodySmall,
        color = menuTextColor.copy(alpha = 0.5f)
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        "retroachievements.org",
        style = MaterialTheme.typography.bodySmall,
        color = focusColor,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { onOpenRa() }
    )
}

@Composable
private fun StatChip(text: String, textColor: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = textColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = textColor.copy(alpha = 0.6f)
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}
