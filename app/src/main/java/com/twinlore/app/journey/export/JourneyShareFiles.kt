package com.twinlore.app.journey.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Where a share-only export lives: the app's own cache, behind the FileProvider, and never the
 * gallery. Sharing used to mean saving - every "Share video" left a copy in Movies/GameTwin
 * whether the user wanted one or not. A share-only file is handed to the other app by content
 * uri and is the system's to reclaim with the rest of the cache; "Save to device" is its own
 * menu entry now, and the only one that writes MediaStore.
 */
internal object JourneyShareFiles {

    /** A fresh file in the provider-served `shares/` cache directory. */
    fun file(context: Context, userName: String, extension: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(System.currentTimeMillis())
        val who = userName.replace(UNSAFE_NAME, "-").trim('-').ifBlank { "player" }
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        sweepStale(dir)
        return File(dir, "journey-$who-$stamp.$extension")
    }

    /**
     * Deletes share files older than [STALE_MILLIS], on the way to creating a new one.
     *
     * The system reclaims cache when it needs the disk, but on its own schedule: a session of
     * repeated video shares parked a full MP4 apiece - tens of megabytes each - until it felt
     * like it. Files younger than the window stay, because the app the last share was handed to
     * may still be reading its content uri.
     */
    internal fun sweepStale(dir: File, nowMillis: Long = System.currentTimeMillis()) {
        dir.listFiles()?.forEach { stale ->
            if (nowMillis - stale.lastModified() > STALE_MILLIS) stale.delete()
        }
    }

    /** See [sweepStale]: how long a handed-out share stays readable before the sweep takes it. */
    internal const val STALE_MILLIS = 6L * 60 * 60 * 1000

    /** The content uri another app can read [file] through. */
    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * The name a saved export carries: `journey-<what was charted>-<when it was saved>`. The
     * middle is the journey's own settings - the game's title in GAME mode, the window's length
     * (or "all-time") on a span - so a folder of saves reads as a list of journeys rather than
     * a list of timestamps.
     */
    fun exportBaseName(frame: JourneyFrame, nowMillis: Long = System.currentTimeMillis()): String {
        val slug = when (frame.mode) {
            JourneyMode.GAME -> frame.main.games.values.firstOrNull()?.title
                ?.lowercase(Locale.US)?.replace(UNSAFE_NAME, "-")?.trim('-')?.take(28)?.trim('-')
                .orEmpty().ifBlank { "game" }
            JourneyMode.SPAN ->
                if (frame.spanFromMillis <= 0L) {
                    "all-time"
                } else {
                    val days = ((frame.spanToMillis - frame.spanFromMillis) / DAY_MILLIS).coerceAtLeast(1)
                    "${days}d"
                }
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(nowMillis)
        return "journey-$slug-$stamp"
    }

    /**
     * A fresh file under `journey/` in the user's library folder (the SAF tree the app stores its
     * data in), or null when the tree cannot be written - the caller then fails loudly rather
     * than silently saving somewhere else.
     */
    fun libraryFile(context: Context, tree: Uri, mimeType: String, displayName: String): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return null
        if (!root.canWrite()) return null
        val dir = root.findFile(LIBRARY_SUBDIR)?.takeIf { it.isDirectory }
            ?: root.createDirectory(LIBRARY_SUBDIR)
            ?: return null
        return dir.createFile(mimeType, displayName)
    }

    private const val LIBRARY_SUBDIR = "journey"
    private const val DAY_MILLIS = 86_400_000L
    private val UNSAFE_NAME = Regex("[^A-Za-z0-9_-]+")
}
