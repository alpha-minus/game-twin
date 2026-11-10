package com.twinlore.app.data.library

import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.data.local.VaultEntity
import com.twinlore.app.data.library.LibraryFolderManager.Companion.normalizeArticle
import com.twinlore.app.data.library.LibraryFolderManager.Companion.resolveFolderName
import com.twinlore.app.data.library.LibraryFolderManager.Companion.sanitizeForPath
import com.twinlore.app.data.library.LibraryFolderManager.Companion.trailingArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-points vaults whose `folder_name` names a directory that does not exist,
 * at the directory that holds their files under the other article convention.
 *
 * A vault imported from a RetroArch playlist is named the No-Intro way
 * ("Legend of Zelda, The: A Link to the Past & Four Swords"); the same game
 * added on another device keeps the article in front. Each sanitizes to its own
 * folder, and Syncthing replicates the whole home tree, so the local vault ends
 * up pointing at a folder that was never created while its artwork, guides and
 * maps sit in the folder next to it. Everything that touches the library
 * resolves through [resolveFolderName], so adopting the existing folder into
 * `folder_name` heals every read AND write path at once — no files move.
 */
object VaultFolderAdoption {

    /**
     * Which vaults should adopt which existing directory. Pure so the matching
     * rules are testable without a home folder; [VaultFolderAdopter] supplies
     * [existingDirs] from disk and writes the result back.
     *
     * A vault is a candidate only when its own folder is absent, so a library
     * where both spellings exist on disk is left exactly as it is. The adopted
     * directory is returned with its on-disk casing (SAF name matching is
     * case-sensitive), and a directory another vault already resolves to is
     * never taken — two vaults sharing one folder would show each other's
     * files.
     */
    fun planAdoptions(
        vaults: List<VaultEntity>,
        existingDirs: Collection<String>,
    ): Map<String, String> {
        val dirByLowerName = existingDirs.associateBy { it.lowercase() }
        val owned = vaults
            .map { resolveFolderName(it).lowercase() }
            .filter { it in dirByLowerName }
            .toSet()

        val plan = mutableMapOf<String, String>()
        for (vault in vaults) {
            val current = resolveFolderName(vault)
            if (current.lowercase() in dirByLowerName) continue // folder is there; nothing to heal

            val alias = listOf(normalizeArticle(vault.name), trailingArticle(vault.name))
                .map { sanitizeForPath(it) }
                .filterNot { it.equals(current, ignoreCase = true) }
                .distinct()
                .firstNotNullOfOrNull { candidate ->
                    dirByLowerName[candidate.lowercase()]?.takeIf { it.lowercase() !in owned }
                }
                ?: continue

            plan[vault.id] = alias
        }
        return plan
    }
}

@Singleton
class VaultFolderAdopter @Inject constructor(
    private val vaultDao: VaultDao,
    private val folder: LibraryFolderManager,
) {

    /**
     * Runs the plan against the library. Idempotent: once a vault's
     * `folder_name` names a real directory it is skipped forever, so this is a
     * pure early-return on a healthy library (one directory listing, no writes).
     * Returns the number of vaults re-pointed.
     */
    suspend fun adoptOrphanedFolders(): Int = withContext(Dispatchers.IO) {
        val vaults = vaultDao.getAllVaults()
        if (vaults.isEmpty()) return@withContext 0
        val dirs = folder.listVaultDirNames()
        if (dirs.isEmpty()) return@withContext 0

        val plan = VaultFolderAdoption.planAdoptions(vaults, dirs)
        for (vault in vaults) {
            val adopted = plan[vault.id] ?: continue
            android.util.Log.i(
                "VaultFolderAdopter",
                "Vault '${vault.name}' had no folder at '${LibraryFolderManager.resolveFolderName(vault)}'; " +
                    "adopting existing '$adopted'",
            )
            vaultDao.updateVault(vault.copy(folderName = adopted))
        }
        plan.size
    }
}
