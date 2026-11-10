package com.twinlore.app.journey.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import android.view.Surface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CanvasHolder
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.ui.JourneyStripText
import com.twinlore.app.journey.ui.chart.ChartPaths
import com.twinlore.app.journey.ui.chart.ChartStyle
import java.io.FileDescriptor
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Renders a journey to an H.264 MP4 in the shared Movies folder, off screen and off the network.
 *
 * Everything it draws is already in memory: the layout, the frame, the palette the screen is
 * showing and the badge/avatar bitmaps the chart preloaded. The export never fetches anything, so
 * a video is exactly the chart the user is looking at, at 1920x1080 instead of a phone panel.
 *
 * Frames come from [ExportTimeline] rather than from the encoder's own clock, and each written
 * sample's timestamp is stamped from a monotonic frame counter, so the file plays at a steady 30
 * fps no matter how long an individual frame took to draw. The camera a following export moves
 * through comes from [ExportCamera] for the same reason, and the reveal effects - the pop, the
 * confetti, the award flash - from [ExportEffects]: both are arithmetic over frame indices, so
 * neither the encoder's throughput nor a wall clock can reach the pixels.
 *
 * The MediaStore row is created pending and only published once the muxer has closed cleanly. A
 * cancelled or failed export deletes the row, so a half-written video never shows up in the
 * gallery.
 *
 * **One export at a time, on one thread.** An instance carries per-run scratch that is deliberately
 * unsynchronised - the reused canvas wrapper and density below, and the [ExportFrame.RunCache] the
 * encode loop builds - because an encode is a single sequential loop over [ExportTimeline]. Two
 * concurrent `export` calls on the same instance would have them writing over each other. The
 * screen already serialises exports (see `JourneyExportState`); nothing here re-checks it.
 */
class JourneyVideoExporter(private val context: Context) {

    /**
     * One Compose canvas wrapper and one density, reused across every frame of an export.
     *
     * [CanvasHolder] exists precisely to rebind a single `AndroidCanvas` onto a different native
     * canvas, which is what a frame loop does hundreds of times; allocating a wrapper and a
     * `Density` per frame bought nothing.
     */
    private val canvasHolder = CanvasHolder()
    private val frameDensity = Density(1f)

    /**
     * Encodes the journey and returns the published MediaStore uri.
     *
     * [layout] and [frame] come straight from the Ready state, [style] and [bitmaps] from the
     * chart the screen has on display. The exporter builds its own [ChartPaths] rather than
     * borrowing the screen's: paths carry scratch geometry the renderer mutates per frame, and
     * the chart is still drawing with its own copy on the main thread.
     */
    // Internal: the strip facts it takes ([JourneyStripText.Ranks]) are the app's own vocabulary.
    internal suspend fun export(
        layout: ChartLayout,
        frame: JourneyFrame,
        style: ChartStyle,
        bitmaps: Map<String, ImageBitmap>,
        /** Backdrop art per game id; empty simply leaves the video on the plain background. */
        art: Map<Int, ImageBitmap> = emptyMap(),
        userName: String,
        speed: Float,
        onProgress: (Int, Int) -> Unit,
        /** The closing rank and the compare ranks; see [JourneyStripText.Ranks]. */
        ranks: JourneyStripText.Ranks = JourneyStripText.Ranks(),
        /** The ambience the player was showing; see [ExportFrame.drawExportFrame]. */
        ambience: com.twinlore.app.data.ambience.AmbienceAnimation = com.twinlore.app.data.ambience.AmbienceAnimation.NONE,
        /** Whether a flat line carries a sign over the player's face; see [IdleSigns]. */
        idleSigns: Boolean = true,
        /**
         * Whether the video follows the runner the way playback did, at [ExportCamera]'s own fixed
         * cadence. Off by default and off whenever the watcher's follow mode was off: a clip is
         * meant to be the picture that was watched.
         */
        followMode: Boolean = false,
        /** The art alpha the watcher's stage used; see [ExportFrame.drawExportFrame]. */
        backdropDim: Float = 0.25f,
        /** True encodes into the provider-served cache instead of the gallery. See [JourneyShareFiles]. */
        shareOnly: Boolean = false,
        /**
         * The user's library folder. When set, "Save to device" encodes into `journey/` inside it
         * (named by [JourneyShareFiles.exportBaseName]) instead of the MediaStore gallery.
         */
        saveTree: Uri? = null,
    ): Uri = withContext(Dispatchers.Default) {
        if (shareOnly) {
            val file = JourneyShareFiles.file(context, userName, "mp4")
            try {
                java.io.FileOutputStream(file).use { out ->
                    encode(
                        out.fd, layout, frame, style, bitmaps, art, userName, speed,
                        onProgress, ranks, ambience, idleSigns, followMode, backdropDim,
                    )
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
                context, saveTree, MIME_VIDEO, JourneyShareFiles.exportBaseName(frame) + ".mp4",
            ) ?: throw IOException("Could not create the video in the library folder")
            // Encoded into our own cache file and copied across, rather than muxed straight into
            // the document. MediaMuxer needs a SEEKABLE descriptor - it rewrites the moov atom
            // when the track finishes - and a DocumentsProvider is under no obligation to serve
            // one: a library folder on a cloud or network provider hands back a pipe, or refuses
            // "rw" outright, and the failure would land at the END of a minute of encoding. The
            // local file is always seekable, and the copy is a stream the provider cannot object
            // to. The staging file goes either way; only the document survives.
            val staging = JourneyShareFiles.file(context, userName, "mp4")
            try {
                java.io.FileOutputStream(staging).use { out ->
                    encode(
                        out.fd, layout, frame, style, bitmaps, art, userName, speed,
                        onProgress, ranks, ambience, idleSigns, followMode, backdropDim,
                    )
                }
                val sink = resolver.openOutputStream(doc.uri)
                    ?: throw IOException("Could not open the video file for writing")
                sink.use { staging.inputStream().use { source -> source.copyTo(it) } }
                return@withContext doc.uri
            } catch (t: Throwable) {
                runCatching { doc.delete() }
                throw t
            } finally {
                runCatching { staging.delete() }
            }
        }
        val uri = insertPendingRow(userName)
        try {
            val descriptor = resolver.openFileDescriptor(uri, "rw")
                ?: throw IOException("Could not open the video file for writing")
            descriptor.use {
                encode(
                    it.fileDescriptor, layout, frame, style, bitmaps, art, userName, speed,
                    onProgress, ranks, ambience, idleSigns, followMode, backdropDim,
                )
            }
            val published = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
            uri
        } catch (t: Throwable) {
            // Covers cancellation as well as failure: either way the pending row is a truncated
            // file nobody should ever see, and the delete has to happen before the throw
            // propagates out of the (already cancelled) coroutine.
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    // ---- MediaStore ----

    private fun insertPendingRow(userName: String): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis())
        val who = userName.replace(UNSAFE_NAME, "-").trim('-').ifBlank { "player" }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "journey-$who-$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return context.contentResolver.insert(collection, values)
            ?: throw IOException("Could not create the video file")
    }

    // ---- encoder ----

    private suspend fun encode(
        target: FileDescriptor,
        layout: ChartLayout,
        frame: JourneyFrame,
        style: ChartStyle,
        bitmaps: Map<String, ImageBitmap>,
        art: Map<Int, ImageBitmap>,
        userName: String,
        speed: Float,
        onProgress: (Int, Int) -> Unit,
        ranks: JourneyStripText.Ranks,
        ambience: com.twinlore.app.data.ambience.AmbienceAnimation,
        idleSigns: Boolean,
        followMode: Boolean,
        backdropDim: Float,
    ) {
        val paths = ChartPaths(layout)
        val textMeasurer = ExportFrame.textMeasurer(context)
        val drawScope = CanvasDrawScope()
        // Everything a run works out once and reuses: the scaled backdrop and the strip's chips.
        val runCache = ExportFrame.RunCache()
        // The whole camera path, worked out before a single frame is drawn. It has to be: the glide
        // is a recurrence, so frame i is defined by every frame before it, and computing it here
        // rather than as the loop runs is what keeps the encoder's own progress out of it. Empty
        // when the watcher was not following, and every frame then draws the fitted chart.
        val camera = if (!followMode) {
            emptyList()
        } else {
            ExportCamera.path(layout, ExportTimeline.frameCount(speed), speed, paths)
        }
        // When each badge pops, each award bursts and each stage flash fires - worked out over the
        // whole timeline before a frame is drawn, for the same reason the camera is (see
        // [ExportEffects]). A followed clip and a fitted one both get them: they are what the
        // watcher saw, and neither picture is the one that was watched without them.
        val effects = ExportEffects.plan(layout, ExportTimeline.frameCount(speed), speed, paths)

        val format = MediaFormat.createVideoFormat(MIME_VIDEO, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, ExportTimeline.FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MIME_VIDEO)
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var sink: Sink? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()
            codec.start()
            muxer = MediaMuxer(target, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val input = surface
            val output = Sink(codec, muxer)
            sink = output

            ExportTimeline.forEachFrame(speed, onProgress) { index, progress ->
                drawFrame(surface = input, drawScope = drawScope) {
                    with(ExportFrame) {
                        drawExportFrame(
                            layout = layout,
                            paths = paths,
                            frame = frame,
                            style = style,
                            textMeasurer = textMeasurer,
                            bitmaps = bitmaps,
                            art = art,
                            userName = userName,
                            progress = progress,
                            ranks = ranks,
                            cache = runCache,
                            ambience = ambience,
                            idleSigns = idleSigns,
                            view = camera.getOrNull(index),
                            effects = effects.at(index),
                            backdropDim = backdropDim,
                            videoTimeMillis = index * 1_000L / ExportTimeline.FPS,
                        )
                    }
                }
                output.drain(endOfStream = false)
            }

            codec.signalEndOfInputStream()
            output.drain(endOfStream = true)
            // Nothing was ever muxed: the row would publish a zero byte video into the gallery.
            if (!output.started) throw IOException("The encoder produced no video")
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { surface?.release() }
            if (sink?.started == true) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            // The run's frame-sized buffers go now, not at some future GC: their Java shells are
            // too small to raise collector pressure, and a session of repeated exports otherwise
            // parks dead native memory the next run's encoder allocation then dies without.
            runCatching { runCache.release() }
        }
    }

    /**
     * Draws one frame into the encoder's input surface. What a frame *is* - the letterboxed chart,
     * the art behind it, the strip above it - belongs to [ExportFrame], which the still image
     * export draws through as well; this only owns getting it onto a surface.
     *
     * The SOFTWARE canvas ([Surface.lockCanvas]) is mandatory here. Do not "optimise" this back to
     * `lockHardwareCanvas()`: that was a reproducible native crash, SIGSEGV in
     * `SkCanvas::drawTextBlob` on the RenderThread, roughly 30 seconds into every export.
     *
     * A hardware canvas does not rasterise anything at the point it is drawn into. It records a
     * display list and HWUI replays it later, on the RenderThread. The chart's text goes through a
     * [TextMeasurer] whose LRU cache is reused across frames, so by the time the RenderThread got
     * around to replaying frame N, this loop had already measured frame N+1 and evicted the Skia
     * text blobs frame N's display list was still holding pointers to. Replaying it then read freed
     * memory. Nothing about the drawing is wrong; the deferral is.
     *
     * `lockCanvas` rasterises synchronously on this thread instead, so every blob is consumed
     * before the call returns and the next frame can safely reuse the cache. It costs throughput,
     * which an export can afford: it already reports progress per frame and is cancellable.
     */
    private fun drawFrame(
        surface: Surface,
        drawScope: CanvasDrawScope,
        draw: DrawScope.() -> Unit,
    ) {
        val canvas = surface.lockCanvas(null) ?: return
        try {
            // CanvasHolder rather than a fresh ComposeCanvas per frame: it exists to rebind one
            // wrapper onto a different native canvas, which is exactly what a frame loop wants. The
            // Density is hoisted beside the draw scope for the same reason.
            canvasHolder.drawInto(canvas) {
                drawScope.draw(
                    density = frameDensity,
                    layoutDirection = LayoutDirection.Ltr,
                    canvas = this,
                    size = Size(WIDTH.toFloat(), HEIGHT.toFloat()),
                    block = draw,
                )
            }
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    /**
     * The muxer side of the encoder: pulls encoded buffers out and writes them as samples.
     *
     * Timestamps are stamped here from [frameIndex] rather than trusted from the encoder. Surface
     * input carries the wall clock time of the canvas post, so a slow frame would otherwise become
     * a slow frame in the file; counting samples instead makes the video exactly 30 fps.
     */
    private class Sink(private val codec: MediaCodec, private val muxer: MediaMuxer) {
        private val info = MediaCodec.BufferInfo()
        private var trackIndex = -1
        private var frameIndex = 0L
        var started = false
            private set

        suspend fun drain(endOfStream: Boolean) {
            // Mid-encode there is nothing to wait for: whatever the encoder has finished is
            // collected and the loop goes back to drawing. Blocking 10ms per frame on the empty
            // dequeue that ends every drain would add seconds of sleep across a whole export.
            val timeout = if (endOfStream) DEQUEUE_TIMEOUT_US else 0L
            var idleSpins = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                when (val index = codec.dequeueOutputBuffer(info, timeout)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
                        // Draining the tail: the encoder still owes us frames, but never wait
                        // forever for a device that has stopped producing them.
                        if (++idleSpins > MAX_IDLE_SPINS) return
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        idleSpins = 0
                        if (!started) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            started = true
                        }
                    }
                    else -> {
                        if (index < 0) continue
                        idleSpins = 0
                        val buffer = codec.getOutputBuffer(index)
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (buffer != null && info.size > 0 && !isConfig && started) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            info.presentationTimeUs = frameIndex * 1_000_000L / ExportTimeline.FPS
                            frameIndex++
                            muxer.writeSampleData(trackIndex, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
    }

    private companion object {
        const val WIDTH = ExportFrame.WIDTH
        const val HEIGHT = ExportFrame.HEIGHT
        const val BIT_RATE = 12_000_000
        const val MIME_VIDEO = "video/avc"
        const val MIME_TYPE = "video/mp4"
        const val RELATIVE_PATH = "Movies/GameTwin"
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val MAX_IDLE_SPINS = 500
        val UNSAFE_NAME = Regex("[^A-Za-z0-9_-]+")
    }
}
