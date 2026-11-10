package com.twinlore.app.ui.fold.guide

import com.twinlore.app.data.GuideViewStateManager
import com.twinlore.app.data.SavedMapRepository
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.data.LastPlayedSource
import com.twinlore.app.data.SettingsRepository
import com.twinlore.app.data.VaultRepository
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.data.retroachievements.RaGameCacheRepository
import com.twinlore.app.data.retroachievements.RaGameProgress
import com.twinlore.app.data.steam.SteamGameCacheRepository
import com.twinlore.app.data.steam.SteamGameProgress
import com.twinlore.app.domain.FilePickerSortField
import com.twinlore.app.domain.SavedMap
import com.twinlore.app.domain.SavedTab
import com.twinlore.app.domain.SortDirection
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FoldGuideData] over the app's real storage.
 *
 * Every dependency here is already a `@Singleton`, which is the fact that makes
 * the panel possible at all: what the app has open lives outside the activity,
 * keyed by vault id, and the panel exists precisely while the activity is
 * backgrounded.
 */
@Singleton
class AndroidFoldGuideData @Inject constructor(
    private val savedTabRepository: SavedTabRepository,
    private val guideViewStateManager: GuideViewStateManager,
    private val savedMapRepository: SavedMapRepository,
    private val resourceResolver: ResourceResolver,
    private val settingsRepository: SettingsRepository,
    private val mapViewStateManager: com.twinlore.app.data.MapViewStateManager,
    private val vaultRepository: VaultRepository,
    private val raGameCacheRepository: RaGameCacheRepository,
    private val raCommentsRepository: com.twinlore.app.data.retroachievements.RaCommentsRepository,
    private val steamGameCacheRepository: SteamGameCacheRepository,
    private val syncLatestPlayedGameUseCase: com.twinlore.app.data.SyncLatestPlayedGameUseCase,
    private val contentUriFileWatcher: com.twinlore.app.data.ContentUriFileWatcher,
    private val markerManager: com.twinlore.app.data.MapMarkerManager,
    private val mapThumbnailCache: com.twinlore.app.data.library.MapThumbnailCache,
    private val imageCleaner: com.twinlore.app.data.library.VaultImageCleaner,
) : FoldGuideData {

    /**
     * The guide the app has open, by the app's own definition of that.
     *
     * `getActiveBookmarkId` is the single source of truth the Guide tab
     * restores from, written on every guide open. It is emphatically not
     * `isCurrentContent` or `isFavorite`: those are only ever set from the tab
     * manager, so a vault with a guide open has neither, and asking for them is
     * what once made this surface report "No guide open" for every vault.
     */
    override suspend fun currentGuide(vaultId: String): SavedTab? {
        val bookmarkId = guideViewStateManager.getActiveBookmarkId(vaultId) ?: return null
        return savedTabRepository.getTabById(bookmarkId)
    }

    /**
     * The vault's images in the app's own order, so `Next` in the panel and the
     * chevron in the app walk the same list. The sort is a user setting, and
     * reading it here rather than sorting by something fixed is the difference
     * between two surfaces that agree and two that nearly agree.
     */
    override suspend fun images(vaultId: String): List<FoldImage> {
        val maps = savedMapRepository.getMapsByVaultIdOnce(vaultId)
        val field = settingsRepository.imagesPickerSortField.first()
        val direction = settingsRepository.imagesPickerSortDirection.first()
        return maps.sortedWith(comparatorFor(field, direction))
            .map { map ->
                FoldImage(
                    id = map.id,
                    title = map.title,
                    uri = map.resourceId?.let { resourceResolver.resolve(it)?.toString() },
                )
            }
    }

    /**
     * Size and type are not sorted on here.
     *
     * The app sorts by those from file metadata it loads asynchronously per
     * image, and the panel opens over a running game: doing that work to order
     * a list somebody is about to page through with two buttons is not worth
     * the reads. Those two fall back to the app's default order instead.
     */
    private fun comparatorFor(
        field: FilePickerSortField,
        direction: SortDirection,
    ): Comparator<SavedMap> {
        val ascending: Comparator<SavedMap> = when (field) {
            FilePickerSortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            FilePickerSortField.LAST_OPENED -> compareBy { it.lastAccessed }
            FilePickerSortField.DATE_ADDED,
            FilePickerSortField.SIZE,
            FilePickerSortField.TYPE,
            -> compareBy { it.createdAt }
        }
        return if (direction == SortDirection.DESC) ascending.reversed() else ascending
    }

    override suspend fun currentImage(vaultId: String): String? =
        mapViewStateManager.getSelectedMapId(vaultId)

    /**
     * Both records, because the app keeps both: the selection is what its
     * Image tab opens on, and last-accessed is what its "last opened" sort
     * orders by. Writing one without the other is a surface that agrees with
     * the app about the picture and disagrees about the list.
     */
    override suspend fun touchImage(vaultId: String, imageId: String) {
        mapViewStateManager.saveSelectedMapId(vaultId, imageId)
        savedMapRepository.updateLastAccessed(imageId)
    }

    override suspend fun imageView(vaultId: String, imageId: String): FoldImageView? =
        mapViewStateManager.getState(vaultId, imageId)?.let {
            FoldImageView(it.zoom, it.offsetX, it.offsetY)
        }

    override suspend fun saveImageView(vaultId: String, imageId: String, view: FoldImageView) {
        mapViewStateManager.saveState(vaultId, view.zoom, view.offsetX, view.offsetY, imageId)
    }

    override suspend fun saveTextZoom(bookmarkId: String, zoom: Float) {
        savedTabRepository.getTabById(bookmarkId)?.let { tab ->
            savedTabRepository.updateTab(tab.copy(zoomLevel = zoom))
        }
    }

    /**
     * `getCachedProgress`, never `getGameProgress`: the first reads what is
     * already on disk, the second will go to the network when the cache is
     * stale. Opening the panel is not a moment to do that. Both services keep
     * such a cache, so a Steam-linked vault opens on its Steam set the same way.
     */
    override suspend fun awards(vaultId: String): FoldAwards = when (val link = linkFor(vaultId)) {
        is AwardsLink.Steam -> steamGameCacheRepository.getCachedProgress(vaultId).toAwards()
        is AwardsLink.Ra -> raGameCacheRepository.getCachedProgress(vaultId).toAwards(linked = true)
        null -> raGameCacheRepository.getCachedProgress(vaultId).toAwards(linked = false)
    }

    /**
     * The same question `MainViewModel.handleOpenLastPlayed` asks on resume,
     * asked from a window the resume never reaches.
     *
     * Gated on the app's own two settings, so one place governs whether the
     * app follows what you launch. The launch marker is read first and full
     * detection only runs when it has changed: detection resolves, creates and
     * scrapes, and this is happening over a running game.
     *
     * The marker is NOT recorded here. The app's pass marks at attempt time so
     * a broken log cannot retry forever; this one marks when the user answers,
     * so folding away without answering leaves the launch for the app.
     */
    override suspend fun detectNewlyPlayedGame(currentVaultId: String?): FoldDetectedGame? {
        if (!settingsRepository.openLastPlayedOnLaunch.first()) return null
        val source = settingsRepository.lastPlayedSource.first()
        if (source == LastPlayedSource.NOT_CONFIGURED) return null

        val folderUri = folderUriFor(source)
        val isFileSource = source == LastPlayedSource.ES_DE ||
            source == LastPlayedSource.COCOON ||
            source == LastPlayedSource.RETROARCH
        // File sources only. The Achievements source has no launch file, so
        // the app's pass runs full detection every time and has no marker to
        // record an answer against. Here that would mean a RetroAchievements
        // request on every panel open, over a running game, and a Stay that
        // could not settle anything: the app would ask again on return
        // regardless. Saying nothing beats asking a question that cannot be
        // answered.
        if (!isFileSource) return null
        val marker = contentUriFileWatcher.readLaunchMarker(folderUri, source)
        run {
            // Nothing was launched since the last answer, so there is nothing
            // to ask about.
            if (marker == null) return null
            if (marker == settingsRepository.lastHandledLaunchMarker.first()) return null
        }

        val candidate = runCatching {
            syncLatestPlayedGameUseCase.execute(uriOverride = folderUri)
        }.getOrNull() ?: return null
        // An unconfirmed guess is the library's to confirm, in a dialog this
        // window cannot host. Saying nothing beats naming the wrong game.
        if (syncLatestPlayedGameUseCase.pendingFuzzyMatch.value != null) return null
        if (candidate == currentVaultId) return null
        val title = vaultRepository.getVaultById(candidate)?.name?.takeIf { it.isNotBlank() }
            ?: return null
        return FoldDetectedGame(
            vaultId = candidate,
            title = title,
            launchMarker = marker,
            artworkUri = artworkUriFor(candidate),
        )
    }

    override suspend fun markLaunchHandled(marker: String?) {
        if (marker == null) return
        settingsRepository.setLastHandledLaunchMarker(marker)
    }

    private suspend fun folderUriFor(source: LastPlayedSource): String? = when (source) {
        LastPlayedSource.ES_DE -> settingsRepository.esdeFolderUri.first()
        LastPlayedSource.COCOON -> settingsRepository.cocoonFolderUri.first()
        LastPlayedSource.RETROARCH -> settingsRepository.retroArchHistoryFolder.first()
        else -> null
    }

    override suspend fun markOpened(vaultId: String) {
        vaultRepository.updateLastOpened(vaultId)
    }

    /** The vault's artwork as something Coil can load, or null when it has none. */
    private suspend fun artworkUriFor(vaultId: String): String? =
        vaultRepository.getVaultById(vaultId)?.artworkResourceId
            ?.let { resourceResolver.resolve(it)?.toString() }

    override suspend fun refreshAwards(vaultId: String): FoldAwards? {
        // An unlinked vault has nothing to fetch, and that is not a failure to
        // report: it is the state the empty message already describes.
        //
        // runCatching matters most for Steam, which reports a private profile, an
        // unowned game and a game without stats by throwing.
        return when (val link = linkFor(vaultId) ?: return null) {
            is AwardsLink.Ra -> runCatching {
                raGameCacheRepository.getGameProgress(vaultId, link.gameId, forceRefresh = true)
            }.getOrNull()?.toAwards(linked = true)
            is AwardsLink.Steam -> runCatching {
                steamGameCacheRepository.getGameProgress(vaultId, link.appId, forceRefresh = true)
            }.getOrNull()?.toAwards()
        }
    }

    /**
     * The same cached thread the app's drawer reads, so it reads identically in
     * both places and a thread opened in one costs no request in the other. A
     * failure with nothing cached comes back empty: the panel says there are no
     * comments rather than putting an HTTP code over a running game.
     */
    override suspend fun awardComments(achievementId: Int): List<FoldComment> =
        runCatching {
            raCommentsRepository.getComments(achievementId)
                .map {
                    FoldComment(
                        author = it.user.orEmpty(),
                        text = it.commentText.orEmpty(),
                        submitted = it.submitted.orEmpty(),
                    )
                }
        }.getOrDefault(emptyList())

    /**
     * `addFile` then `addMap`, which is what MapViewerScreenViewModel's own
     * assign path does. Going through VaultRepository rather than copying the
     * file somewhere of the panel's own choosing is what makes the result a
     * real vault image rather than a second kind of one.
     */
    override suspend fun saveScreenshot(vaultId: String, file: java.io.File): FoldImage? {
        if (!file.exists()) return null
        val title = file.nameWithoutExtension.replace("_", " ")
        val added = runCatching {
            vaultRepository.addFile(
                vaultId,
                file,
                com.twinlore.app.domain.ResourceKind.MAP,
                "image/png",
            )
        }.getOrNull()
        val resourceFileId = (added as? com.twinlore.app.domain.AddFileResult.Copied)
            ?.resourceFileId ?: return null
        val map = runCatching { savedMapRepository.addMap(vaultId, title, resourceFileId) }
            .getOrNull() ?: return null
        // The app's assign path points the vault at the newest image too, so
        // the two agree about which one is current.
        runCatching { vaultRepository.updateMapResourceId(vaultId, resourceFileId) }
        return FoldImage(
            id = map.id,
            title = map.title,
            uri = resourceResolver.resolve(resourceFileId)?.toString(),
        )
    }

    /**
     * MapViewerScreenViewModel.deleteSavedMap, step for step: markers, row,
     * thumbnail, then the selection moves off the deleted image if it was
     * there. The presenter points the selection at the neighbour it shows
     * next; this only makes sure it never points at nothing that exists.
     */
    override suspend fun deleteImage(vaultId: String, imageId: String) {
        val maps = savedMapRepository.getMapsByVaultIdOnce(vaultId)
        // Entry, markers, thumbnail and the file itself (it used to stay on disk).
        imageCleaner.deleteImage(vaultId, imageId)
        if (mapViewStateManager.getSelectedMapId(vaultId) == imageId) {
            val remaining = maps.firstOrNull { it.id != imageId }?.id
            mapViewStateManager.saveSelectedMapId(vaultId, remaining)
        }
    }

    private sealed interface AwardsLink {
        data class Ra(val gameId: Int) : AwardsLink
        data class Steam(val appId: Int) : AwardsLink
    }

    /** RetroAchievements first, as the app's own session screen picks its tab. */
    private suspend fun linkFor(vaultId: String): AwardsLink? {
        val vault = vaultRepository.getVaultById(vaultId) ?: return null
        vault.raGameId?.toIntOrNull()?.let { return AwardsLink.Ra(it) }
        vault.steamAppId?.toIntOrNull()?.let { return AwardsLink.Steam(it) }
        return null
    }

    /** Only called for a Steam-linked vault, so it is linked whether or not anything is cached. */
    private fun SteamGameProgress?.toAwards(): FoldAwards {
        val progress = this ?: return FoldAwards(source = FoldAwardSource.STEAM, linked = true)
        return FoldAwards(
            source = FoldAwardSource.STEAM,
            steamAchievements = progress.achievements,
            gameTitle = progress.gameTitle,
            // The store banner the app's Steam drawer heads its list with.
            gameIconUrl = progress.headerImageUrl?.takeIf { it.isNotBlank() },
            unlocked = progress.numUnlocked,
            total = progress.numAchievements,
            playtimeMinutes = progress.playtimeMinutes,
            linked = true,
        )
    }

    private fun RaGameProgress?.toAwards(linked: Boolean): FoldAwards {
        val progress = this ?: return FoldAwards(linked = linked)
        return FoldAwards(
            achievements = progress.achievements,
            gameTitle = progress.gameTitle,
            gameIconUrl = progress.gameImageIcon
                ?.takeIf { it.isNotBlank() }
                ?.let { com.twinlore.app.data.retroachievements.RaUrls.gameImage(it) },
            totalPlayers = progress.numDistinctPlayers ?: 0,
            unlocked = progress.userNumAchieved,
            unlockedHardcore = progress.userNumHardcore,
            total = progress.numAchievements,
            points = progress.userPoints,
            totalPoints = progress.totalPoints,
            highestAwardKind = progress.highestAwardKind,
            rank = progress.userRank,
            playtimeMinutes = progress.userTotalPlaytimeMinutes,
            // The caller's answer, not an assumption from having a snapshot. A
            // vault can hold cached progress and still have lost its link, and
            // "open the app to link this game" is the wrong thing to say to
            // someone whose game is linked.
            linked = linked,
        )
    }
}
