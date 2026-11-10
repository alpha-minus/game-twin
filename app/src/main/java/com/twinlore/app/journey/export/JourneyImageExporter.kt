package com.twinlore.app.journey.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.ui.JourneyStripText
import com.twinlore.app.journey.ui.chart.ChartStyle
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The finished journey as a single PNG in the shared Pictures folder.
 *
 * A video is a minute of somebody's attention; a picture is a glance, and most of what gets sent on
 * is a glance. This renders the same frame the video would end on - the compact chart, whole,
 * letterboxed into 1920x1080, the game art behind it and the overview strip above it - through the
 * very same [ExportFrame] the encoder draws through, so the still cannot quietly become a different
 * picture from the film.
 *
 * With one deliberate exception, and it is about what a glance is: the video animates its reveals
 * ([ExportEffects]) and this passes no effects at all, so a picture shows settled icons rather than
 * whichever badge happened to be half way through popping in on the frame it was taken.
 *
 * Off the network entirely, like the video: everything it draws is already decoded and in memory.
 *
 * The MediaStore row is created pending and published only once the bytes are written, so a failed
 * or cancelled export never leaves a half-written image in the gallery.
 */
class JourneyImageExporter(private val context: Context) {

    /** Renders the journey at [progress] and returns the published MediaStore uri. */
    // Internal: the strip facts it takes ([JourneyStripText.Ranks]) are the app's own vocabulary.
    internal suspend fun export(
        layout: ChartLayout,
        frame: JourneyFrame,
        style: ChartStyle,
        bitmaps: Map<String, ImageBitmap>,
        /** Backdrop art per game id; empty simply leaves the image on the plain background. */
        art: Map<Int, ImageBitmap> = emptyMap(),
        userName: String,
        /** The finished journey by default: a still of a half-drawn chart is not a summary of it. */
        progress: Float = 1f,
        /** The closing rank and the compare ranks; see [JourneyStripText.Ranks]. */
        ranks: JourneyStripText.Ranks = JourneyStripText.Ranks(),
        /** The ambience the player was showing; see [ExportFrame.drawExportFrame]. */
        ambience: com.twinlore.app.data.ambience.AmbienceAnimation = com.twinlore.app.data.ambience.AmbienceAnimation.NONE,
        /** Whether a flat line carries a sign over the player's face; see [IdleSigns]. */
        idleSigns: Boolean = true,
        /** The art alpha the watcher's stage used; see [ExportFrame.drawExportFrame]. */
        backdropDim: Float = 0.25f,
        /** True renders into the provider-served cache instead of the gallery. See [JourneyShareFiles]. */
        shareOnly: Boolean = false,
        /**
         * The user's library folder. When set, "Save to device" writes `journey/` inside it
         * (named by [JourneyShareFiles.exportBaseName]) instead of the MediaStore gallery.
         */
        saveTree: Uri? = null,
    ): Uri = withContext(Dispatchers.Default) {
        val image = ExportFrame.render(
            context = context,
            layout = layout,
            frame = frame,
            style = style,
            bitmaps = bitmaps,
            art = art,
            userName = userName,
            progress = progress,
            ranks = ranks,
            ambience = ambience,
            idleSigns = idleSigns,
            backdropDim = backdropDim,
        )
        try {
            writeOut(image, userName, frame, shareOnly, saveTree)
        } finally {
            // An 8 MB native buffer behind a tiny Java shell: recycle it the moment the bytes are
            // written rather than waiting for a GC that feels no pressure from it - repeated
            // exports otherwise stack these up against the next export's own allocations.
            runCatching { image.asAndroidBitmap().recycle() }
        }
    }

    private suspend fun writeOut(
        image: ImageBitmap,
        userName: String,
        frame: JourneyFrame,
        shareOnly: Boolean,
        saveTree: Uri?,
    ): Uri = withContext(Dispatchers.Default) {
        if (shareOnly) {
            val file = JourneyShareFiles.file(context, userName, "png")
            try {
                java.io.FileOutputStream(file).use { out ->
                    if (!image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("Could not encode the image")
                    }
                }
                return@withContext JourneyShareFiles.uri(context, file)
            } catch (t: Throwable) {
                runCatching { file.delete() }
                throw t
            }
        }
        val resolver = context.contentResolver
        if (saveTree != null) {
            val doc = JourneyShareFiles.libraryFile(
                context, saveTree, MIME_TYPE, JourneyShareFiles.exportBaseName(frame) + ".png",
            ) ?: throw IOException("Could not create the image in the library folder")
            try {
                val stream = resolver.openOutputStream(doc.uri)
                    ?: throw IOException("Could not open the image file for writing")
                stream.use {
                    if (!image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) {
                        throw IOException("Could not encode the image")
                    }
                }
                return@withContext doc.uri
            } catch (t: Throwable) {
                runCatching { doc.delete() }
                throw t
            }
        }
        val uri = insertPendingRow(userName)
        try {
            val stream = resolver.openOutputStream(uri)
                ?: throw IOException("Could not open the image file for writing")
            stream.use {
                if (!image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) {
                    throw IOException("Could not encode the image")
                }
            }
            val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
            uri
        } catch (t: Throwable) {
            // Covers cancellation as well as failure, exactly as the video export does: either way
            // the pending row is a truncated file nobody should ever see.
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun insertPendingRow(userName: String): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis())
        val who = userName.replace(UNSAFE_NAME, "-").trim('-').ifBlank { "player" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "journey-$who-$stamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return context.contentResolver.insert(collection, values)
            ?: throw IOException("Could not create the image file")
    }

    companion object {
        const val MIME_TYPE = "image/png"
        private const val RELATIVE_PATH = "Pictures/GameTwin"
        private val UNSAFE_NAME = Regex("[^A-Za-z0-9_-]+")
    }
}
