package com.twinlore.app.data

import android.content.Context
import com.twinlore.app.data.library.LibraryFolderManager
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SyncTombstoneDao
import com.twinlore.app.data.local.SyncTombstoneEntity
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.local.VaultEntityMapper
import androidx.room.withTransaction
import com.twinlore.app.domain.AddFileResult
import com.twinlore.app.domain.ResourceKind
import com.twinlore.app.domain.Result
import com.twinlore.app.domain.Vault
import com.twinlore.app.ui.utils.ImagePathUtils
import com.twinlore.app.ui.utils.PaletteExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.twinlore.app.data.ChecklistRepository

/**
 * Repository for vault operations with business logic
 * Implements vault identity resolution as per PRD:
 * Priority: 1. Full ROM path, 2. ROM filename, 3. User-defined name
 */
@Singleton
open class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDao: VaultDao,
    private val savedTabRepository: SavedTabRepository,
    private val mapMarkerManager: MapMarkerManager,
    private val mapViewStateManager: MapViewStateManager,
    private val guideViewStateManager: GuideViewStateManager,
    private val fuzzyNameMatcher: FuzzyNameMatcher,
    private val checklistRepository: ChecklistRepository,
    private val settingsRepository: SettingsRepository,
    private val resourceFileDao: ResourceFileDao,
    private val libraryFolderManager: LibraryFolderManager,
    private val resourceResolver: ResourceResolver,
    private val artworkThumbnailCache: com.twinlore.app.data.library.ArtworkThumbnailCache,
    private val mapThumbnailCache: com.twinlore.app.data.library.MapThumbnailCache,
    private val syncTombstoneDao: SyncTombstoneDao,
    private val database: VaultDatabase,
) : BaseRepository() {


    /**
     * Seam for unit tests: override to skip the real Room transaction wrapper.
     * Production code delegates to [database.withTransaction].
     */
    protected open suspend fun runDbTransaction(block: suspend () -> Unit) {
        database.withTransaction { block() }
    }

    /**
     * Get all vaults sorted by last opened date (descending)
     */
    fun getAllVaultsSortedByLastOpened(): Flow<List<Vault>> {
        return vaultDao.getAllVaultsSortedByLastOpened()
            .map { entities -> VaultEntityMapper.toDomainList(entities) }
    }

    /**
     * Get all vaults (unsorted, suspend function)
     */
    suspend fun getAllVaults(): List<Vault> {
        val entities = vaultDao.getAllVaults()
        return VaultEntityMapper.toDomainList(entities)
    }

    /**
     * Get vault by ID
     */
    suspend fun getVaultById(id: String): Vault? {
        val entity = vaultDao.getVaultById(id)
        return entity?.let { VaultEntityMapper.toDomain(it) }
    }

    /**
     * Get vault by ID as Flow
     */
    fun getVaultByIdFlow(id: String): Flow<Vault?> {
        return vaultDao.getVaultByIdFlow(id)
            .map { entity -> entity?.let { VaultEntityMapper.toDomain(it) } }
    }

    /**
     * Resolve vault identity using priority order:
     * 1. Full ROM path (if available)
     * 2. ROM filename
     * 3. User-defined name
     *
     * Returns existing vault if found, null if not found
     */
    suspend fun resolveVaultIdentity(
        romPath: String? = null,
        romFilename: String? = null,
        name: String? = null
    ): Vault? {
        // Priority 1: Full ROM path
        if (!romPath.isNullOrBlank()) {
            val existing = vaultDao.findVaultByRomPath(romPath)
            if (existing != null) {
                val vault = VaultEntityMapper.toDomain(existing)
                android.util.Log.d("VaultRepository", "Matched by ROM path: '$romPath' -> vault ${vault.id} (${vault.displayName}, romPath=${vault.romPath}, romFilename=${vault.romFilename})")
                return vault
            } else {
                android.util.Log.d("VaultRepository", "No match by ROM path: '$romPath'")
            }
        }

        // Priority 2: ROM filename
        if (!romFilename.isNullOrBlank()) {
            val existing = vaultDao.findVaultByRomFilename(romFilename)
            if (existing != null) {
                val vault = VaultEntityMapper.toDomain(existing)
                android.util.Log.d("VaultRepository", "Matched by ROM filename: '$romFilename' -> vault ${vault.id} (${vault.displayName}, romPath=${vault.romPath}, romFilename=${vault.romFilename})")
                return vault
            } else {
                android.util.Log.d("VaultRepository", "No match by ROM filename: '$romFilename'")
            }
        }

        // Priority 3: User-defined name (not implemented in DAO as it's less reliable)
        // For now, we'll create a new vault if not found by path/filename
        android.util.Log.d("VaultRepository", "No vault match found for romPath='$romPath', romFilename='$romFilename', name='$name'")

        return null
    }

    /**
     * The vault a game maps to, by the same chain that binds one: exact ROM path, exact ROM
     * filename, then normalized name via [fuzzyNameMatcher]. Read-only, so it never creates.
     *
     * This exists because the two directions used to disagree. [createVaultWithFuzzyCheck] binds
     * by name when path and filename miss, which is how a library-scanned vault (whose ROM path
     * ES-DE does not know) gets matched to an ES-DE game. [resolveVaultIdentity] stops after
     * filename, so reading that binding back found nothing, and achievements assigned in the
     * vault never showed up against the matched game. A read that can't see what the write bound
     * is the bug, so both sides now walk the same three tiers and the same similarity threshold.
     */
    suspend fun findVaultForGame(
        romPath: String? = null,
        romFilename: String? = null,
        name: String? = null,
    ): Vault? {
        resolveVaultIdentity(romPath, romFilename, name)?.let { return it }
        val candidate = name?.takeIf { it.isNotBlank() } ?: return null
        return fuzzyNameMatcher.findBestMatch(candidate, getAllVaults())?.vault
    }

    /**
     * Resolve vault by RetroAchievements game ID (for last-played sync from RA API).
     * Returns existing vault if the row is in the database, null otherwise.
     */
    suspend fun resolveVaultByRaGameId(raGameId: String): Vault? {
        if (raGameId.isBlank()) return null
        val entity = vaultDao.findVaultByRaGameId(raGameId) ?: return null
        return VaultEntityMapper.toDomain(entity)
    }

    /**
     * Create a new vault
     * If identity resolution finds an existing vault, returns that instead
     */
    suspend fun createVault(
        name: String,
        romPath: String? = null,
        romFilename: String? = null,
        systemeid: String? = null,
        gameId: String? = null,
        source: String? = null,
        raGameId: String? = null,
        steamAppId: String? = null,
        launchIntentJson: String? = null
    ): Result<Vault> {
        return try {
            // Try to resolve existing vault (skip when creating from RA last-played with no path/filename)
            val existing = resolveVaultIdentity(romPath, romFilename, name)
            if (existing != null) {
                return Result.success(existing)
            }

            // Collision guard: two distinct display names can sanitize to the
            // same on-disk folder (e.g. "Game/Name" and "Game:Name" both
            // become "Game Name"), and without this check the second vault
            // would silently share a folder with the first, causing data loss
            // when either is deleted. Reject the second name; UI surfaces the
            // failure so the user picks a non-colliding name. resolveVaultIdentity
            // above already handles exact name matches, so we only need to
            // catch the sanitization-collision case here.
            val proposedFolder = LibraryFolderManager.sanitizeForPath(name)
            val collision = vaultDao.getAllVaults().firstOrNull { existing ->
                LibraryFolderManager.resolveFolderName(existing) == proposedFolder
            }
            if (collision != null) {
                return Result.failure(
                    IllegalStateException(
                        "Vault name '$name' collides with existing vault " +
                            "'${collision.name}' (both map to folder '$proposedFolder'). " +
                            "Pick a name that doesn't share the same sanitized folder."
                    )
                )
            }

            // Create new vault. folderName captures the stable on-disk identity
            // so save + delete sites don't drift when the user later renames
            // the vault (which is metadata-only — the folder stays put).
            val vaultId = UUID.randomUUID().toString()
            val vault = Vault(
                id = vaultId,
                name = name,
                romPath = romPath,
                romFilename = romFilename,
                systemeid = systemeid,
                gameId = gameId,
                source = source ?: "Manual",
                raGameId = raGameId,
                steamAppId = steamAppId,
                launchIntentJson = launchIntentJson,
                lastOpened = null,
                createdAt = Date(),
                folderName = com.twinlore.app.data.library.LibraryFolderManager
                    .sanitizeForPath(name),
            )

            val entity = VaultEntityMapper.toEntity(vault)
            vaultDao.insertVault(entity)

            // A vault that has just been created is the game the app should
            // reopen. Without this the record only ever moved on detection, so
            // adding a game by hand left it pointing at the last game actually
            // played, and the next return to the foreground pulled the user out
            // of what they had just added and back into that one. Deliberately
            // after the identity resolution above: a creation that resolved to
            // an existing vault returned early and is not a new game.
            runCatching { settingsRepository.setNowPlayingVaultId(vaultId) }

            Result.success(vault)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create vault with fuzzy name checking.
     * Returns Result.FuzzyMatch if a similar vault (>=95% match) is found — caller should
     * show a confirmation dialog. If user confirms, call [renameVault] and use the existing vault.
     * Returns Result.Success with existing vault on 100% normalized match.
     * Returns Result.Success with new vault if no match found.
     */
    suspend fun createVaultWithFuzzyCheck(
        name: String,
        romPath: String? = null,
        romFilename: String? = null,
        systemeid: String? = null,
        gameId: String? = null,
        source: String? = null,
        raGameId: String? = null,
        steamAppId: String? = null
    ): Result<Vault> {
        // First try exact identity resolution
        val existing = resolveVaultIdentity(romPath, romFilename, name)
        if (existing != null) {
            return Result.success(existing)
        }

        // Fuzzy match against existing vaults
        val allVaults = getAllVaults()
        val fuzzyMatch = fuzzyNameMatcher.findBestMatch(name, allVaults)
        if (fuzzyMatch != null) {
            if (fuzzyMatch.similarity >= 1.0) {
                // Exact normalized match — use existing without asking
                return Result.success(fuzzyMatch.vault)
            }
            // 95-99% match — return FuzzyMatch for UI confirmation
            return Result.FuzzyMatch(fuzzyMatch.vault, fuzzyMatch.similarity)
        }

        // No match — create new vault
        return createVault(name, romPath, romFilename, systemeid, gameId, source, raGameId, steamAppId)
    }

    /**
     * Insert vault (for imports, etc.)
     */
    suspend fun insertVault(vault: Vault): Result<Unit> {
        return try {
            val entity = VaultEntityMapper.toEntity(vault)
            vaultDao.insertVault(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update vault
     */
    suspend fun updateVault(vault: Vault): Result<Unit> {
        return try {
            android.util.Log.d("VaultRepository", "updateVault called: vaultId=${vault.id}")
            val entity = VaultEntityMapper.toEntity(vault)
            vaultDao.updateVault(entity)
            android.util.Log.d("VaultRepository", "Vault updated in database: ${vault.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("VaultRepository", "Exception updating vault: ${vault.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete vault. Also removes the vault's dedicated folder under the library
     * home (the artwork / guides / maps the app wrote to `<home>/<folderName>/`)
     * so a same-name recreate can't resurrect the old files. Linked media that
     * lives in OTHER folders (source `local_link`, i.e. the user's own files) is
     * outside this dir and is left untouched. Returns the vault name on success.
     */
    suspend fun deleteVault(vaultId: String): Result<String?> {
        return try {
            val entity = vaultDao.getVaultById(vaultId)
            val name = entity?.name
            val folderName = entity?.let { LibraryFolderManager.resolveFolderName(it) }
            val mapIds = resourceFileDao.getForVaultAndKindOnce(vaultId, com.twinlore.app.domain.ResourceKind.MAP.raw).map { it.id }
            // Fetch deviceId before the transaction — it's a DataStore suspend call, not DB work.
            val deviceId = settingsRepository.getOrCreateDeviceId()
            runDbTransaction {
                vaultDao.deleteVaultById(vaultId)
                syncTombstoneDao.upsert(
                    SyncTombstoneEntity(
                        vaultId = vaultId,
                        deletedAt = System.currentTimeMillis(),
                        deviceId = deviceId,
                    )
                )
            }
            withContext(Dispatchers.IO) {
                artworkThumbnailCache.invalidate(vaultId)
                mapThumbnailCache.invalidateAll(mapIds)
                // Remove the on-disk vault folder. Best-effort: a failure here must
                // not fail the delete (the DB row is already gone).
                if (!folderName.isNullOrBlank()) {
                    runCatching { libraryFolderManager.deleteChildDir(folderName) }
                        .onFailure {
                            android.util.Log.w("VaultRepository", "deleteChildDir failed for '$folderName'", it)
                        }
                }
            }
            Result.success(name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete all vaults from the database. Used when storage folder changes
     * (non-migration path) to wipe stale entries before re-discovering.
     */
    suspend fun deleteAllVaults() {
        vaultDao.deleteAllVaults()
    }

    suspend fun getVaultCount(): Int = vaultDao.count()

    /**
     * Update last opened timestamp
     */
    suspend fun updateLastOpened(vaultId: String): Result<Unit> {
        return try {
            vaultDao.updateLastOpened(vaultId, Date())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update only the artwork vibrant color (ARGB) for a vault.
     */
    suspend fun updateArtworkVibrantArgb(vaultId: String, argb: Int?): Result<Unit> {
        return try {
            vaultDao.updateArtworkVibrantArgb(vaultId, argb)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateArtworkResourceId(vaultId: String, resourceId: String?): Result<Unit> {
        return try {
            // The thumb is refreshed BEFORE the pointer write, on purpose. The library
            // observes the vault row and rebuilds the tile the moment the new resource
            // id lands, and the tile reads the thumb by vault id alone. With the write
            // first, that rebuild found a thumb still holding the previous artwork and
            // Coil memory-cached the old image under the NEW resource id's key; it
            // resurfaced whenever the tile's cache buster reset (assigning artwork to
            // any other vault does that), until the next process start.
            // Best-effort: a thumb failure must not block the pointer write, it just
            // drops the stale thumb so the tile falls back to the source image.
            val uri: android.net.Uri? = if (resourceId != null) {
                withContext(Dispatchers.IO) {
                    val resolved = try {
                        resourceResolver.resolve(resourceId)
                    } catch (_: Exception) { null }
                    val generated = resolved != null && try {
                        artworkThumbnailCache.generate(vaultId, resolved) != null
                    } catch (_: Exception) { false }
                    if (!generated) artworkThumbnailCache.invalidate(vaultId)
                    resolved
                }
            } else {
                // Cheap path: just drop the cached thumb. No IO hop needed.
                artworkThumbnailCache.invalidate(vaultId)
                null
            }
            vaultDao.updateArtworkResourceId(vaultId, resourceId)
            if (uri != null) {
                // Palette is its own full decode and only feeds the focus tint, so it
                // stays behind the pointer write rather than delaying the new artwork.
                withContext(Dispatchers.IO) {
                    try {
                        val data = ImagePathUtils.toImageData(uri.toString(), vaultId)
                        val color = PaletteExtractor.extractVibrantColor(context, data)
                        val argb = color?.let { c ->
                            ((c.alpha * 255).toInt() shl 24) or
                                ((c.red * 255).toInt() shl 16) or
                                ((c.green * 255).toInt() shl 8) or
                                (c.blue * 255).toInt()
                        }
                        if (argb != null) {
                            vaultDao.updateArtworkVibrantArgb(vaultId, argb)
                        }
                    } catch (_: Exception) { }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMapResourceId(vaultId: String, resourceId: String?): Result<Unit> {
        return try {
            vaultDao.updateMapResourceId(vaultId, resourceId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGuideResourceId(vaultId: String, resourceId: String?): Result<Unit> {
        return try {
            vaultDao.updateGuideResourceId(vaultId, resourceId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically clear any vault slot (artwork, guide, map) that references
     * [resourceId]. Used by media-bind cleanup when a search path is removed
     * or disabled and its resource rows are being deleted.
     */
    suspend fun clearAnySlotPointingAt(resourceId: String): Result<Unit> {
        return try {
            vaultDao.clearAnySlotPointingAt(resourceId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rename vault
     */
    suspend fun renameVault(vaultId: String, newName: String): Result<Unit> {
        return try {
            val vault = getVaultById(vaultId)
            if (vault != null) {
                val updated = vault.copy(name = newName)
                updateVault(updated)
            } else {
                Result.failure(Exception("Vault not found: $vaultId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set RetroAchievements Game ID for a vault (from matching or manual entry).
     */
    suspend fun updateRaGameId(vaultId: String, raGameId: String?): Result<Unit> {
        return try {
            val vault = getVaultById(vaultId) ?: return Result.failure(Exception("Vault not found: $vaultId"))
            val updated = vault.copy(raGameId = raGameId)
            updateVault(updated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveVaultBySteamAppId(steamAppId: String): Vault? {
        if (steamAppId.isBlank()) return null
        val entity = vaultDao.findVaultBySteamAppId(steamAppId) ?: return null
        return VaultEntityMapper.toDomain(entity)
    }

    suspend fun updateSteamAppId(vaultId: String, steamAppId: String?): Result<Unit> {
        return try {
            val vault = getVaultById(vaultId) ?: return Result.failure(Exception("Vault not found: $vaultId"))
            val updated = vault.copy(steamAppId = steamAppId)
            updateVault(updated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Store the launch intent JSON for a vault.
     */
    suspend fun updateLaunchIntent(
        vaultId: String,
        intentJson: String,
        romPath: String?,
        romFilename: String?
    ) {
        android.util.Log.d("VaultRepository", "updateLaunchIntent: vaultId=$vaultId, json=$intentJson")
        vaultDao.updateLaunchIntent(vaultId, intentJson, romPath, romFilename)
    }

    suspend fun updateLaunchIntentByMatch(
        name: String,
        romPath: String?,
        romFilename: String?,
        intentJson: String
    ) {
        android.util.Log.d("VaultRepository", "updateLaunchIntentByMatch: name=$name, romPath=$romPath")
        vaultDao.updateLaunchIntentByMatch(name, romPath, romFilename, intentJson)
    }

    /**
     * Check if vault exists
     */
    suspend fun vaultExists(vaultId: String): Boolean {
        return vaultDao.vaultExists(vaultId)
    }

    /**
     * Vault directories no longer exist on disk. Discovery now means
     * "return what's already in Room".
     */
    suspend fun discoverVaultsFromFileSystem(): Int {
        return 0
    }

    /**
     * Add [sourceFile] to the vault by copying its bytes into the storage
     * folder under the vault's directory. Inserts a `resource_files` row
     * referencing the new copy and returns its id. The source file is left
     * untouched.
     *
     * Returns [AddFileResult.Failed] when the home folder is unset, the
     * source file is missing, or the DB insert fails. On insert failure the
     * just-copied file is rolled back when the home folder is a real path
     * (not an SAF tree).
     */
    suspend fun addFile(
        vaultId: String,
        sourceFile: File,
        kind: ResourceKind,
        mimeType: String? = null
    ): AddFileResult = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists()) {
                return@withContext AddFileResult.Failed(
                    java.io.FileNotFoundException(
                        "Source file does not exist: ${sourceFile.absolutePath}"
                    )
                )
            }

            val now = System.currentTimeMillis()
            val vault = getVaultById(vaultId)
                ?: return@withContext AddFileResult.Failed(
                    IllegalStateException("Vault not found: $vaultId")
                )

            val outcome = libraryFolderManager.copyInto(
                sourceFile = sourceFile,
                vaultDirName = LibraryFolderManager.resolveFolderName(vault),
                suggestedFilename = sourceFile.name,
                mimeType = mimeType
            ) ?: return@withContext AddFileResult.Failed(
                IllegalStateException("Storage folder is not set or could not be written to")
            )

            val id = UUID.randomUUID().toString()
            val entity = ResourceFileEntity(
                id = id,
                vaultId = vaultId,
                kind = kind.raw,
                filename = outcome.finalFilename,
                sizeBytes = outcome.sizeBytes,
                homeRelativePath = outcome.homeRelativePath,
                absolutePath = outcome.absolutePath,
                mimeType = mimeType,
                source = "user_add",
                lastSeenAt = now,
                createdAt = now
            )
            // If the DB insert fails after the copy succeeded, the file at the
            // new location is an unindexed orphan. We can only roll it back
            // when the home folder is a real filesystem path; for SAF trees
            // the orphan stays.
            try {
                resourceFileDao.insert(entity)
            } catch (t: Throwable) {
                outcome.absolutePath?.let { runCatching { File(it).delete() } }
                throw t
            }

            if (kind == com.twinlore.app.domain.ResourceKind.MAP) {
                mapThumbnailCache.warmGenerateAsync(id) { resourceResolver.resolve(id) }
            }

            AddFileResult.Copied(id)
        } catch (t: Throwable) {
            AddFileResult.Failed(t)
        }
    }

}
