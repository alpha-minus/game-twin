package com.twinlore.app.data.esde

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locates a game's ES-DE artwork under `<ES-DE>/downloaded_media/<system>/<mediaFolder>/<rom>.<ext>`.
 * The path-building logic is pure and unit-tested; [resolve] performs the SAF existence checks.
 */
@Singleton
class ESDEArtworkResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Tree URI to its one-shot listing. See [folderFile]. */
    private val folderIndex = java.util.concurrent.ConcurrentHashMap<String, Map<String, Uri>>()

    companion object {
        private val EXTS = listOf("png", "jpg", "webp")
        private val VIDEO_EXTS = listOf("mp4", "webm", "mkv", "avi", "mov")
        /** Override-folder lookups (es-de-companion's extension order). */
        private val LOGO_EXTS = listOf("svg", "png", "webp", "jpg", "jpeg")

        /**
         * Platform backgrounds from a user folder. `gif` is included where the scraped-media
         * scans deliberately do not accept one: a backdrop the user chose by hand is the one
         * place an animated file is actually wanted.
         */
        private val BACKGROUND_EXTS = listOf("webp", "png", "jpg", "jpeg", "gif")

        fun romNameWithoutExt(romPath: String): String {
            val base = romPath.substringAfterLast('/')
            val dot = base.lastIndexOf('.')
            return if (dot > 0) base.substring(0, dot) else base
        }

        /**
         * Media base-name candidates for a rom, most specific first. ES-DE keys media on the
         * gamelist ENTRY, not the file: a multi-disc game living in its own folder (cue/bin +
         * m3u, "directories interpreted as files") stores art at
         * `<mediaFolder>/<gameFolder>/<rom>.<ext>` for the per-file entry and
         * `<mediaFolder>/<gameFolder>.<ext>` for the folder entry — the bare rom name only
         * covers the flat single-file layout. The subpath under the system folder is recovered
         * from the rom path itself (`…/<systemShort>/<sub/dirs>/<rom>`); events carry no rom root.
         */
        fun mediaNameCandidates(systemShort: String, romPath: String): List<String> {
            val bare = romNameWithoutExt(romPath)
            val segments = romPath.split('/').filter { it.isNotEmpty() }
            val sysIdx = segments.indexOfLast { it.equals(systemShort, ignoreCase = true) }
            if (sysIdx >= 0 && sysIdx < segments.size - 2) {
                val subPath = segments.subList(sysIdx + 1, segments.size - 1).joinToString("/")
                return listOf("$subPath/$bare", subPath, bare)
            }
            // No system segment — typical for GAMELIST-relative paths ("./Game Dir/Disc 1.bin"),
            // where the immediate parent folder is the multi-disc game directory that ES-DE
            // names the media after. Without this branch, multi-disc gamelist entries found no
            // art.
            val parent = segments.getOrNull(segments.size - 2)
                ?.takeIf { it != "." && it != ".." && !it.equals(systemShort, ignoreCase = true) }
                ?: return listOf(bare)
            return listOf("$parent/$bare", parent, bare)
        }

        /**
         * Both spellings of a system's media folder, verbatim first and lower-cased second.
         *
         * Android storage is case-sensitive and ES-DE names these folders in lower case, but the
         * short name arrives from an event or a gamelist and is not guaranteed to be. The two
         * paths used to disagree — `resolve()` took the name verbatim while the collage lower-cased
         * it — so exactly one of them worked for any mixed-case system. Trying both costs one
         * extra `stat` on a miss and cannot be wrong either way.
         */
        private fun systemMediaDirs(systemShort: String, folder: String): List<String> {
            val verbatim = "downloaded_media/$systemShort/$folder"
            val lower = "downloaded_media/${systemShort.lowercase()}/$folder"
            return if (verbatim == lower) listOf(verbatim) else listOf(verbatim, lower)
        }

        /** Pure: relative candidate paths (under the ES-DE folder root) per slot. */
        fun candidateRelativePaths(systemShort: String, romPath: String): Map<MediaSlot, List<String>> {
            val names = mediaNameCandidates(systemShort, romPath)
            return MediaSlot.values().associateWith { slot ->
                systemMediaDirs(systemShort, slot.esdeFolder).flatMap { dir ->
                    names.flatMap { name -> EXTS.map { ext -> "$dir/$name.$ext" } }
                }
            }
        }

        /** Pure: relative candidate paths for one game in a named media folder. */
        fun mediaFolderRelativePaths(
            systemShort: String,
            romPath: String,
            folder: String,
        ): List<String> {
            val names = mediaNameCandidates(systemShort, romPath)
            return systemMediaDirs(systemShort, folder).flatMap { dir ->
                names.flatMap { name -> EXTS.map { ext -> "$dir/$name.$ext" } }
            }
        }

        /** Pure: relative candidate paths for the preview video. */
        fun videoRelativePaths(systemShort: String, romPath: String): List<String> {
            val names = mediaNameCandidates(systemShort, romPath)
            return systemMediaDirs(systemShort, "videos").flatMap { dir ->
                names.flatMap { name -> VIDEO_EXTS.map { "$dir/$name.$it" } }
            }
        }
    }

    suspend fun resolve(folderUri: Uri, systemShort: String, romPath: String): GameMedia =
        withContext(Dispatchers.IO) {
            val candidates = candidateRelativePaths(systemShort, romPath)
            val videoCandidates = videoRelativePaths(systemShort, romPath)

            // Fastest path: direct java.io.File when All-Files access is granted (es-de-companion
            // parity) — a bare stat() per candidate, and Coil loads the file:// URI directly.
            val fsRoot = if (EsdeFsAccess.isGranted()) EsdeFsAccess.rootPath(folderUri) else null
            if (fsRoot != null) {
                fun firstExistingFile(rels: List<String>): Uri? {
                    for (rel in rels) {
                        val f = File(fsRoot, rel)
                        if (f.exists()) return Uri.fromFile(f)
                    }
                    return null
                }
                return@withContext GameMedia(
                    hero = firstExistingFile(candidates.getValue(MediaSlot.HERO)),
                    logo = firstExistingFile(candidates.getValue(MediaSlot.LOGO)),
                    screenshot = firstExistingFile(candidates.getValue(MediaSlot.SCREENSHOT)),
                    titleScreen = firstExistingFile(candidates.getValue(MediaSlot.TITLE)),
                    boxFront = firstExistingFile(candidates.getValue(MediaSlot.BOX_FRONT)),
                    box3d = firstExistingFile(candidates.getValue(MediaSlot.BOX_3D)),
                    mix = firstExistingFile(candidates.getValue(MediaSlot.MIX)),
                    physicalMedia = firstExistingFile(candidates.getValue(MediaSlot.PHYSICAL_MEDIA)),
                    video = firstExistingFile(videoCandidates),
                )
            }

            // Fast path: build the document URI directly from the tree doc id and probe it with a
            // single existence query — O(1) per candidate, no directory enumeration. This is the
            // difference between snappy and sluggish when downloaded_media folders hold hundreds
            // of images (DocumentFile.findFile lists the whole directory on every call).
            val treeDocId = try {
                DocumentsContract.getTreeDocumentId(folderUri)
            } catch (_: Throwable) { null }

            if (treeDocId != null) {
                fun firstExistingFast(rels: List<String>): Uri? {
                    for (rel in rels) {
                        val childDocId = "$treeDocId/$rel"
                        val uri = try {
                            DocumentsContract.buildDocumentUriUsingTree(folderUri, childDocId)
                        } catch (_: Throwable) { null } ?: continue
                        if (documentExists(uri)) return uri
                    }
                    return null
                }
                return@withContext GameMedia(
                    hero = firstExistingFast(candidates.getValue(MediaSlot.HERO)),
                    logo = firstExistingFast(candidates.getValue(MediaSlot.LOGO)),
                    screenshot = firstExistingFast(candidates.getValue(MediaSlot.SCREENSHOT)),
                    titleScreen = firstExistingFast(candidates.getValue(MediaSlot.TITLE)),
                    boxFront = firstExistingFast(candidates.getValue(MediaSlot.BOX_FRONT)),
                    box3d = firstExistingFast(candidates.getValue(MediaSlot.BOX_3D)),
                    mix = firstExistingFast(candidates.getValue(MediaSlot.MIX)),
                    physicalMedia = firstExistingFast(candidates.getValue(MediaSlot.PHYSICAL_MEDIA)),
                    video = firstExistingFast(videoCandidates),
                )
            }

            // Fallback (non-externalstorage providers): traverse via DocumentFile.
            val root = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext GameMedia()
            fun firstExisting(rels: List<String>): Uri? {
                for (rel in rels) {
                    val f = traverse(root, rel)
                    if (f != null && f.exists()) return f.uri
                }
                return null
            }
            GameMedia(
                hero = firstExisting(candidates.getValue(MediaSlot.HERO)),
                logo = firstExisting(candidates.getValue(MediaSlot.LOGO)),
                screenshot = firstExisting(candidates.getValue(MediaSlot.SCREENSHOT)),
                titleScreen = firstExisting(candidates.getValue(MediaSlot.TITLE)),
                boxFront = firstExisting(candidates.getValue(MediaSlot.BOX_FRONT)),
                box3d = firstExisting(candidates.getValue(MediaSlot.BOX_3D)),
                mix = firstExisting(candidates.getValue(MediaSlot.MIX)),
                physicalMedia = firstExisting(candidates.getValue(MediaSlot.PHYSICAL_MEDIA)),
                video = firstExisting(videoCandidates),
            )
        }

    /** Locally-resolved art for a system/collection card (all fields optional). */
    data class SystemArt(val logo: Uri? = null, val background: Uri? = null)

    /**
     * Platform-screen art for one system, resolved local-first.
     *
     * Logo chain: the user's chosen logo folder, then the legacy `GameTwin/system_logos` path
     * (kept so anyone already using it does not silently lose their art), then the
     * theme-customization scan. A null result means the caller shows the system's name as text -
     * the remote monochrome-logo fallback that used to sit at the end of this chain is gone.
     *
     * Background chain: the user's chosen background folder, then legacy `GameTwin/system_images`.
     * There is deliberately no automatic fallback to random scraped media any more; that is what
     * the library collage does, configurably, instead of as a hidden default.
     *
     * The chosen folders are SAF trees, so they work with no All-Files access at all. Only the
     * two legacy paths and the theme scan need it, and they are skipped when it is absent.
     *
     * Auto-collection names are normalized (`favorites` → `auto-favorites`, …) so override files
     * follow the same naming as ES-DE theme folders and es-de-companion.
     */
    suspend fun resolveSystemArt(
        folderUri: Uri,
        systemShort: String,
        logoFolder: Uri? = null,
        backgroundFolder: Uri? = null,
    ): SystemArt = withContext(Dispatchers.IO) {
        val name = normalizeEsdeSystemName(systemShort)
        val fsRoot = if (EsdeFsAccess.isGranted()) EsdeFsAccess.rootPath(folderUri) else null

        val logo = logoFolder?.let { folderFile(it, name, LOGO_EXTS) }
            ?: fsRoot?.let { firstExisting(File(it, "GameTwin/system_logos"), name, LOGO_EXTS) }
            ?: fsRoot?.let { themeCustomizationLogo(it, systemShort) }
        val background = backgroundFolder?.let { folderFile(it, name, BACKGROUND_EXTS) }
            ?: fsRoot?.let { firstExisting(File(it, "GameTwin/system_images"), name, BACKGROUND_EXTS) }
        SystemArt(logo = logo, background = background)
    }

    /**
     * Best collage image for one game, walking [chain] in order and taking the first slot that
     * was actually scraped. Direct-file only, so the collage is All-Files-gated the same way the
     * gamelists it selects from are.
     */
    fun libraryImageFor(
        fsRoot: String,
        systemShort: String,
        romPath: String,
        chain: List<MediaSlot>,
    ): Uri? {
        for (slot in chain) {
            mediaFolderImage(fsRoot, systemShort, romPath, slot.esdeFolder)?.let { return it }
        }
        return null
    }

    /**
     * One game's image from a named `downloaded_media` subfolder. Direct-file only.
     *
     * Takes the folder rather than a [MediaSlot] because ES-DE scrapes more of them than this
     * resolver has semantic slots for — `physicalmedia`, for one, which the stats screen uses
     * as a backdrop but nothing else needs.
     */
    fun mediaFolderImage(
        fsRoot: String,
        systemShort: String,
        romPath: String,
        folder: String,
    ): Uri? {
        for (rel in mediaFolderRelativePaths(systemShort, romPath, folder)) {
            val f = File(fsRoot, rel)
            if (f.exists()) return Uri.fromFile(f)
        }
        return null
    }

    /** ES-DE's `physicalmedia` folder: the cartridge or disc the game shipped on. */
    fun physicalMediaFor(fsRoot: String, systemShort: String, romPath: String): Uri? =
        mediaFolderImage(fsRoot, systemShort, romPath, MediaSlot.PHYSICAL_MEDIA.esdeFolder)

    /**
     * `<basename>.<ext>` from a user-picked SAF tree, taking [exts] in order.
     *
     * The whole folder is listed once and held in [folderIndex]: SYSTEM_SELECT fires on every
     * step of ES-DE's carousel, and probing five extensions per step against a content provider
     * is a lot of IPC for a folder that holds a few hundred files and changes almost never.
     */
    private fun folderFile(tree: Uri, baseName: String, exts: List<String>): Uri? {
        val index = folderIndex.getOrPut(tree.toString()) { listFolder(tree) }
        for (ext in exts) index["$baseName.$ext"]?.let { return it }
        return null
    }

    /** Display name to document URI for every file directly inside [tree]; empty if unreadable. */
    private fun listFolder(tree: Uri): Map<String, Uri> = try {
        val treeDocId = DocumentsContract.getTreeDocumentId(tree)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)
        val out = HashMap<String, Uri>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val display = c.getString(1) ?: continue
                out[display.lowercase()] =
                    DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            }
        }
        out
    } catch (_: Exception) {
        // Permission revoked, provider gone, or not a tree we can enumerate. An unreadable
        // override folder must behave exactly like an unset one, never like a crash.
        emptyMap()
    }

    private fun firstExisting(dir: File, baseName: String, exts: List<String>): Uri? {
        if (!dir.isDirectory) return null
        for (ext in exts) {
            val f = File(dir, "$baseName.$ext")
            if (f.exists()) return Uri.fromFile(f)
        }
        return null
    }

    /**
     * A logo from `themes/<theme>/theme-customizations/{logos,artwork}/<name>.*` (or a plain theme
     * system folder) — for custom collections whose art the user placed inside a theme.
     */
    private fun themeCustomizationLogo(fsRoot: String, systemShort: String): Uri? {
        val themesDir = File(fsRoot, "themes")
        if (!themesDir.isDirectory) return null
        val rels = listOf(
            "theme-customizations/logos/$systemShort.svg",
            "theme-customizations/artwork/$systemShort.png",
            "theme-customizations/artwork/$systemShort.jpg",
            "$systemShort/images/logo.svg",
            "$systemShort/logo.svg",
        )
        themesDir.listFiles()?.forEach { theme ->
            if (theme.isDirectory) {
                for (rel in rels) {
                    val f = File(theme, rel)
                    if (f.exists()) return Uri.fromFile(f)
                }
            }
        }
        return null
    }

    /** Single-column existence query — no directory listing. */
    private fun documentExists(uri: Uri): Boolean = try {
        context.contentResolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
        )?.use { it.moveToFirst() } ?: false
    } catch (_: Exception) {
        false
    }

    private fun traverse(root: DocumentFile, relative: String): DocumentFile? {
        var cur: DocumentFile? = root
        for (segment in relative.split('/')) {
            cur = cur?.findFile(segment) ?: return null
        }
        return cur
    }
}
