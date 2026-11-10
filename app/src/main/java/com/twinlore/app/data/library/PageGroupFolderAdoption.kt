package com.twinlore.app.data.library

import android.content.Context
import com.twinlore.app.data.PageGroupRepository
import com.twinlore.app.data.local.ResourceFileDao
import com.twinlore.app.data.local.ResourceFileEntity
import com.twinlore.app.data.local.SavedTabDao
import com.twinlore.app.data.local.VaultDao
import com.twinlore.app.data.local.VaultEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recognises a saved page group by its shape on disk:
 * `<VaultFolder>/<Group Name>/page-1.mht … page-N.mht`.
 *
 * Groups are normally created by the bulk-save pipeline, which writes the pages
 * AND the `saved_tabs` / `page_group_entries` rows that describe them. A group
 * that arrives from another device over Syncthing brings only the files — the
 * rows live in that device's database — so the Guides tab shows nothing. This
 * is the pure half of the repair: given the vault folder's subfolders, decide
 * which ones are groups this device has never registered, and in what order
 * their pages go. [PageGroupFolderAdopter] does the IO and writes the rows.
 *
 * `manifest.json` is deliberately not required. It is the richer source (it
 * carries the original labels, hrefs and scroll anchors), but it never reaches
 * the library folder on SAF homes — see [PageGroupSaveTargets.writeGroupTextFile]
 * — so a group replicated from a SAF device has pages and nothing else. The
 * labels are recovered from each MHT's `Subject:` header instead, which is the
 * page title the browser stamped at save time.
 */
object PageGroupFolderAdoption {

    data class DiskFile(val filename: String, val sizeBytes: Long)

    /** A subfolder that looks like a page group but has no row in this database. */
    data class AdoptableGroup(val folderName: String, val pages: List<Page>) {
        data class Page(val filename: String, val sizeBytes: Long, val number: Int)
    }

    private val PAGE_FILE = Regex("""^page-(\d+)\.(mht|mhtml|txt)$""", RegexOption.IGNORE_CASE)

    fun isGroupPageFilename(filename: String): Boolean = PAGE_FILE.matches(filename)

    private fun pageNumber(filename: String): Int? =
        PAGE_FILE.matchEntire(filename)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * [subfolders] maps each immediate subfolder of the vault folder to its
     * files. A folder qualifies when it holds at least one `page-N.mht`, and is
     * skipped when a group row already claims it (matched case-insensitively,
     * since SAF name matching is case-sensitive while the filesystem is not).
     *
     * Pages are ordered by their NUMBER, not their name — a lexical sort puts
     * page-10 between page-1 and page-2 and silently scrambles the guide.
     */
    fun planGroups(
        subfolders: Map<String, List<DiskFile>>,
        existingGroupFolders: Set<String>,
    ): List<AdoptableGroup> {
        val claimed = existingGroupFolders.map { it.lowercase() }.toSet()
        return subfolders.entries
            .filterNot { (name, _) -> name.lowercase() in claimed }
            .mapNotNull { (name, files) ->
                val pages = files
                    .mapNotNull { f ->
                        pageNumber(f.filename)?.let { n ->
                            AdoptableGroup.Page(f.filename, f.sizeBytes, n)
                        }
                    }
                    .sortedBy { it.number }
                if (pages.isEmpty()) null else AdoptableGroup(name, pages)
            }
            .sortedBy { it.folderName }
    }

    /** Entry label: the saved page's own title, falling back to its position. */
    fun labelFor(subject: String?, pageNumber: Int): String =
        subject?.trim()?.takeIf { it.isNotEmpty() } ?: "Page $pageNumber"

    /** What separates the parts of a browser page title. */
    private val TITLE_SEPARATOR = Regex("""\s+[-\u2013\u2014|\u00b7:]+\s+""")

    /**
     * Drops the title parts every page in a group shares.
     *
     * A `Subject:` header is what the browser called the whole page, so a
     * fifty chapter walkthrough adopted from disk reads "Chrono Trigger -
     * Chapter 1", "Chrono Trigger - Chapter 2", and the contents list is a
     * column of the game's name with the chapter hidden at the end of each
     * line. The save path never has this problem: it labels entries from the
     * site's own link text, which is already just the chapter.
     *
     * So the shared parts are inferred and removed. Whole separated segments
     * only, never a character count: "Chapter 1" and "Chapter 12" share the
     * text "Chapter 1", and trimming by length would leave an entry called
     * "2". Leading and trailing both, since sites put the game at the front
     * and their own name at the back.
     *
     * All or nothing per group, and never everything: at least one segment
     * survives, so a group whose pages are all called the same thing keeps
     * that name rather than becoming a list of blanks. Entries with no
     * subject stay null and take the "Page N" fallback.
     */
    fun withoutSharedAffixes(subjects: List<String?>): List<String?> {
        val titles = subjects.map { it?.trim()?.takeIf { t -> t.isNotEmpty() } }
        val present = titles.filterNotNull()
        if (present.size < 2) return titles

        val spans = present.map { segmentSpans(it) }
        val minSegments = spans.minOf { it.size }
        if (minSegments < 2) return titles

        var leading = 0
        while (leading < minSegments - 1 && sharedSegment(present, spans, leading, fromEnd = false)) {
            leading++
        }
        var trailing = 0
        while (
            leading + trailing < minSegments - 1 &&
            sharedSegment(present, spans, trailing, fromEnd = true)
        ) {
            trailing++
        }
        if (leading == 0 && trailing == 0) return titles

        val trimmed = present.indices.map { i ->
            val span = spans[i]
            present[i].substring(span[leading].first, span[span.size - 1 - trailing].second).trim()
        }
        // A group that trims to nothing keeps what it had: better a repeated
        // game name than an entry with no name at all.
        if (trimmed.any { it.isEmpty() }) return titles

        var next = 0
        return titles.map { if (it == null) null else trimmed[next++] }
    }

    /** Each segment as a [start, end) pair, so trimming keeps the middle verbatim. */
    private fun segmentSpans(title: String): List<Pair<Int, Int>> {
        val spans = mutableListOf<Pair<Int, Int>>()
        var cursor = 0
        for (match in TITLE_SEPARATOR.findAll(title)) {
            spans += cursor to match.range.first
            cursor = match.range.last + 1
        }
        spans += cursor to title.length
        return spans
    }

    private fun sharedSegment(
        titles: List<String>,
        spans: List<List<Pair<Int, Int>>>,
        index: Int,
        fromEnd: Boolean,
    ): Boolean {
        val first = segmentText(titles[0], spans[0], index, fromEnd)
        if (first.isEmpty()) return false
        return titles.indices.all { i ->
            segmentText(titles[i], spans[i], index, fromEnd).equals(first, ignoreCase = true)
        }
    }

    private fun segmentText(
        title: String,
        spans: List<Pair<Int, Int>>,
        index: Int,
        fromEnd: Boolean,
    ): String {
        val span = if (fromEnd) spans[spans.size - 1 - index] else spans[index]
        return title.substring(span.first, span.second).trim()
    }

    /**
     * The `Subject:` header of an MHT archive is the page title the browser
     * recorded. [headerText] is the first few KB of the file — enough to cover
     * the headers without reading a 1.7 MB archive to find one line.
     *
     * Handles the two shapes Blink emits: a folded header (continuation lines
     * start with whitespace) and an RFC 2047 encoded-word for non-ASCII titles.
     */
    fun subjectFrom(headerText: String): String? {
        val lines = headerText.lineSequence().toList()
        val start = lines.indexOfFirst { it.startsWith("Subject:", ignoreCase = true) }
        if (start < 0) return null

        val value = StringBuilder(lines[start].substringAfter(':').trim())
        for (i in (start + 1) until lines.size) {
            val line = lines[i]
            // A blank line ends the header block; an unindented line is the next header.
            if (line.isBlank() || !line.first().isWhitespace()) break
            value.append(' ').append(line.trim())
        }
        return decodeEncodedWords(value.toString()).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * The URL this archive was saved from, out of its own headers.
     *
     * Reading mode picks its per-host filter from the entry's `savedHref`
     * (see `PageGroupViewerScreen`'s `filterForHost`), and a group adopted
     * from the folder has no URL in the database to give it: the pages were
     * found as files. So a set rebuilt after its snapshot was deleted came
     * back with the site's search bar and ad slots still in the page, which
     * the same set downloaded in the app does not have.
     *
     * The archive knows where it came from even when the database does not.
     * This is the same answer [com.twinlore.app.data.MhtCacheManager]'s
     * `source-url.txt` keeps for single adopted guides, read from the header
     * block already in hand rather than by copying the file to parse it.
     *
     * `Snapshot-Content-Location` first, which is what Blink writes for the
     * page as a whole; the first part's `Content-Location` second, which is
     * what [com.twinlore.app.data.MhtParser.extractOriginalUrl] uses. Only
     * http(s): a wrong host picks the wrong filter, which is worse than none.
     */
    fun sourceUrlFrom(headerText: String): String? =
        headerValue(headerText, "Snapshot-Content-Location")
            ?: headerValue(headerText, "Content-Location")

    private fun headerValue(headerText: String, name: String): String? {
        val lines = headerText.lineSequence().toList()
        val start = lines.indexOfFirst { it.startsWith("$name:", ignoreCase = true) }
        if (start < 0) return null
        val value = StringBuilder(lines[start].substringAfter(':').trim())
        for (i in (start + 1) until lines.size) {
            val line = lines[i]
            if (line.isBlank() || !line.first().isWhitespace()) break
            value.append(line.trim())
        }
        return value.toString().trim()
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private val ENCODED_WORD = Regex("""=\?([^?]+)\?([BbQq])\?([^?]*)\?=""")

    private fun decodeEncodedWords(raw: String): String =
        ENCODED_WORD.replace(raw) { m ->
            val charset = runCatching { charset(m.groupValues[1]) }.getOrElse { Charsets.UTF_8 }
            val text = m.groupValues[3]
            runCatching {
                when (m.groupValues[2].uppercase()) {
                    "B" -> String(java.util.Base64.getMimeDecoder().decode(text), charset)
                    else -> decodeQuotedPrintableWord(text, charset)
                }
            }.getOrElse { m.value }
        }

    /** Q-encoding: `_` is a space and `=XX` is a hex byte. */
    private fun decodeQuotedPrintableWord(text: String, charset: java.nio.charset.Charset): String {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            when {
                text[i] == '_' -> { out.write(' '.code); i++ }
                text[i] == '=' && i + 2 < text.length -> {
                    out.write(text.substring(i + 1, i + 3).toInt(16)); i += 3
                }
                else -> { out.write(text[i].code); i++ }
            }
        }
        return String(out.toByteArray(), charset)
    }
}

/**
 * Registers page-group folders that exist on disk but not in this database,
 * so a guide replicated from another device shows up in the Guides tab instead
 * of sitting invisible in the vault folder.
 *
 * Runs at startup after [VaultFolderAdopter] (a vault must be pointing at the
 * right folder before we look inside it) and BEFORE `LocalMediaBinder`, which
 * decides what counts as a group-owned page by reading the group rows this
 * writes — run it after the bind and the same 14 pages get registered as 14
 * standalone guides.
 */
@Singleton
class PageGroupFolderAdopter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDao: VaultDao,
    private val savedTabDao: SavedTabDao,
    private val resourceFileDao: ResourceFileDao,
    private val pageGroupRepository: PageGroupRepository,
    private val folder: LibraryFolderManager,
) {

    /** Returns the number of groups adopted. Idempotent: a registered group is never re-adopted. */
    suspend fun adoptOrphanedGroups(): Int = withContext(Dispatchers.IO) {
        var adopted = 0
        for (vault in vaultDao.getAllVaults()) {
            adopted += runCatching { adoptForVault(vault) }
                .onFailure { android.util.Log.w("PageGroupFolderAdopter", "adoption failed for '${vault.name}'", it) }
                .getOrDefault(0)
        }
        adopted
    }

    private suspend fun adoptForVault(vault: VaultEntity): Int {
        val vaultDirName = LibraryFolderManager.resolveFolderName(vault)
        val subfolders = folder.listSubfolderFiles(vaultDirName)
        if (subfolders.isEmpty()) return 0

        val existing = savedTabDao.getTabsForVaultSync(vault.id)
            .filter { it.fileType == "group" }
            .mapNotNull { it.groupFolderName }
            .toSet()

        val entriesByFolder = subfolders.mapValues { (_, files) ->
            files.map { PageGroupFolderAdoption.DiskFile(it.filename, it.sizeBytes) }
        }
        val plan = PageGroupFolderAdoption.planGroups(entriesByFolder, existing)
        if (plan.isEmpty()) return 0

        for (group in plan) {
            val filesByName = subfolders[group.folderName].orEmpty().associateBy { it.filename }
            // Subjects first, for the whole group: what is redundant in one
            // page's title is only knowable from the others.
            val pageFiles = group.pages.mapNotNull { page ->
                filesByName[page.filename]?.let { page to it }
            }
            // One read per page: the subject and the source URL live in the
            // same header block.
            val headers = pageFiles.map { (_, file) -> readHeaderText(file.uri) }
            val labels = PageGroupFolderAdoption.withoutSharedAffixes(
                headers.map { it?.let(PageGroupFolderAdoption::subjectFrom) }
            )
            val sourceUrls = headers.map { it?.let(PageGroupFolderAdoption::sourceUrlFrom) }
            val newEntries = pageFiles.mapIndexed { index, (page, file) ->
                val rid = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                resourceFileDao.insert(
                    ResourceFileEntity(
                        id = rid,
                        vaultId = vault.id,
                        kind = "group_page",
                        filename = page.filename,
                        sizeBytes = page.sizeBytes,
                        homeRelativePath = file.relativePath,
                        absolutePath = null,
                        mimeType = "multipart/related",
                        source = "browser_bulk",
                        lastSeenAt = now,
                        createdAt = now,
                    )
                )
                PageGroupRepository.NewEntry(
                    label = PageGroupFolderAdoption.labelFor(labels[index], page.number),
                    // Empty when the archive did not record one, which is what
                    // this used to be for every adopted page.
                    savedHref = sourceUrls[index].orEmpty(),
                    fragment = null,
                    mhtResourceId = rid,
                    pageFile = page.filename,
                    textFile = null,
                )
            }
            if (newEntries.isEmpty()) continue

            pageGroupRepository.createGroup(
                vaultId = vault.id,
                title = group.folderName,
                // The set's own address, when its pages recorded one.
                indexUrl = sourceUrls.firstOrNull { it != null }.orEmpty(),
                indexPageTitle = group.folderName,
                groupFolderName = group.folderName,
                entries = newEntries,
            )
            android.util.Log.i(
                "PageGroupFolderAdopter",
                "Adopted page group '${group.folderName}' (${newEntries.size} pages) for vault '${vault.name}'",
            )
        }
        return plan.size
    }

    /** First 8 KB only — enough for the MHT header block, without reading a 1.7 MB archive. */
    private fun readHeaderText(uri: android.net.Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(8 * 1024)
            val read = input.read(buf)
            if (read <= 0) return@use null
            String(buf, 0, read, Charsets.UTF_8)
        }
    }.getOrNull()
}
