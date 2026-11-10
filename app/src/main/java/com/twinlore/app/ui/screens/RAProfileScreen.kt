package com.twinlore.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.twinlore.app.data.retroachievements.RaProfileDisplay
import com.twinlore.app.data.retroachievements.RaRecentAchievementDisplay
import com.twinlore.app.data.retroachievements.RaRecentlyPlayedDisplay
import com.twinlore.app.ui.MainViewModel
import com.twinlore.app.ui.navigation.NavigableButton
import android.content.Intent
import android.net.Uri

// Achievement badges live under /Badge/ on the shared CDN host (the old /Images/ host meant the
// same badge was cached separately from every other screen's copy).
private const val RA_BADGE_URL = com.twinlore.app.data.retroachievements.RaUrls.BADGE_BASE
private const val RA_PROFILE_URL_PREFIX = "https://retroachievements.org/user/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RAProfileScreen(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
    viewModel: RAProfileViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RetroAchievements Profile") },
                navigationIcon = {
                    IconButton(onClick = { mainViewModel.handleBackNavigation() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        when {
            loading && profile == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && profile == null -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            profile != null -> {
                val p = profile!!
                val ctx = LocalContext.current
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Stats", style = MaterialTheme.typography.titleMedium)
                    Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total points"); Text("${p.totalPoints}")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Softcore points"); Text("${p.totalSoftcorePoints}")
                            }
                            p.rank?.let { r ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Rank"); Text("$r")
                                }
                            }
                            p.memberSince?.let { ms ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Member since"); Text(ms)
                                }
                            }
                        }
                    }

                    if (p.recentAchievements.isNotEmpty()) {
                        Text("Recent achievements", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(p.recentAchievements.take(10)) { a ->
                                Card(colors = CardDefaults.cardColors(), modifier = Modifier.padding(4.dp)) {
                                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        AsyncImage(
                                            model = (RA_BADGE_URL + (a.badgeName ?: "") + ".png").takeIf { !a.badgeName.isNullOrBlank() },
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Text(a.title, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                        Text("+${a.points}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    if (p.recentlyPlayed.isNotEmpty()) {
                        Text("Recently played", style = MaterialTheme.typography.titleMedium)
                        p.recentlyPlayed.forEach { g ->
                            Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(g.title ?: "Game ${g.gameId}", style = MaterialTheme.typography.bodyMedium)
                                        g.consoleName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        if (g.numAchieved != null && g.numPossible != null && g.numPossible > 0) {
                                            Text("${g.numAchieved}/${g.numPossible} achievements", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    NavigableButton(
                        text = "View Full Profile on RetroAchievements",
                        onClick = {
                            val url = RA_PROFILE_URL_PREFIX + java.net.URLEncoder.encode(p.username, "UTF-8")
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            else -> Box(Modifier.fillMaxSize()) {}
        }
    }
}
