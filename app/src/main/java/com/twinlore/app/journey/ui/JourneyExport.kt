package com.twinlore.app.journey.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.export.ExportTimeline
import com.twinlore.app.journey.export.JourneyImageExporter
import com.twinlore.app.journey.export.JourneyVideoExporter
import com.twinlore.app.journey.ui.chart.ChartStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Screen-owned state for the video export.
 *
 * Export lives here rather than in [JourneyViewModel] because everything a frame needs beyond the
 * Ready state is composition state: the palette resolved from the theme and the badge/avatar
 * bitmaps the chart preloaded. Pushing those through the ViewModel would mean handing it Compose
 * types for no gain, so the ViewModel keeps owning data and the screen owns the render.
 *
 * The job runs on the screen's coroutine scope, so leaving the journey cancels an export in
 * flight, and the exporter deletes its pending MediaStore row on the way out.
 */
/**
 * Which RetroAchievements art the stage and its exports rest on: one field of the game's art by
 * name, or [NONE] for no backdrop at all. [TITLE] is the default - a title screen is the game's
 * own idea of what it looks like. Chosen in the player's settings sheet, per session like the
 * ambience; the live player, the video and the still all follow the same choice through the same
 * decoded art map, so a shared file always shows what the watcher saw.
 */
// Menu order = declaration order, None first (owner request); persisted by name, so the
// reorder cannot re-map anyone's saved choice. TITLE stays the default (see JourneyViewModel).
enum class ExportBackground(val label: String) {
    NONE("None"),
    TITLE("Title screen"),
    INGAME("In-game shot"),
    BOX_ART("Box art"),
}

/**
 * Everything one export needs, snapshotted at the moment the button is pressed. Bundling them
 * keeps the button a single stable lambda rather than one that closes over six changing values.
 */
internal data class JourneyExportRequest(
    val layout: ChartLayout,
    val frame: JourneyFrame,
    val style: ChartStyle,
    val bitmaps: Map<String, ImageBitmap>,
    /** Backdrop art per game id, already decoded; the exporter never fetches. */
    val art: Map<Int, ImageBitmap>,
    val userName: String,
    val speed: Float,
    /**
     * The closing rank and the per-player ranks the strip draws. Carried on the request rather
     * than looked up at render time: an export outlives the screen it was started from, and a
     * shared picture has to say what the watcher saw.
     */
    val ranks: JourneyStripText.Ranks = JourneyStripText.Ranks(),
    /** The ambience the player was showing, already resolved past RANDOM. */
    val ambience: com.twinlore.app.data.ambience.AmbienceAnimation = com.twinlore.app.data.ambience.AmbienceAnimation.NONE,
    /** Whether a flat line carries a sign over the player's face; see [IdleSigns]. */
    val idleSigns: Boolean = true,
    /**
     * Whether the watcher was following the runner. The video then follows too (see
     * [com.twinlore.app.journey.export.ExportCamera]); the still image never does, whatever this
     * says, because a crop is not a summary of a journey.
     *
     * Defaults to false rather than to the chart's own [com.twinlore.app.journey.ui.chart
     * .FollowCamera.defaultOn] rule: a request built without one is a request that does not know
     * what the player was showing, and the fitted whole chart is the honest answer to that.
     */
    val followMode: Boolean = false,
    /**
     * The art alpha the watcher's stage used, so a shared file always shows what was watched. See
     * [com.twinlore.app.data.SettingsRepository.journeyBackdropDim] - the default here is that
     * setting's own default (15% art alpha, an 85% dim).
     */
    val backdropDim: Float = 0.15f,
)

@Stable
internal class JourneyExportState(
    /** Renders the video, reporting frames written; [rememberJourneyExportState] wires the real
     *  one. The Boolean is share-only: cache file for the share sheet, never the gallery. */
    private val exportVideo: suspend (JourneyExportRequest, Boolean, (Int, Int) -> Unit) -> Uri,
    /** Renders the still. Lambdas rather than the exporters themselves so that what this class is
     *  actually responsible for - which file the notice is about, and which one "Share again"
     *  hands over - can be driven by a test without a MediaStore to write into. */
    private val exportImage: suspend (JourneyExportRequest, Boolean) -> Uri,
    private val scope: CoroutineScope,
    /** True when "Save to device" writes the library's journey folder; the notices say where. */
    private val savesToLibrary: Boolean = false,
    /** Fired once per successfully finished export of either kind; the screen
     *  uses it to count value events for the support prompt. Never fired for a
     *  cancelled, failed or superseded run. */
    private val onExportSucceeded: () -> Unit = {},
) {
    /** Frames written so far and the total, or null when no export is running. */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** The finished video, kept so the notice can offer to share it. */
    var savedUri by mutableStateOf<Uri?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /**
     * Set when an export finishes, cleared by [consumeAutoShare]. There is one share affordance on
     * the screen and it is the same button that starts the export, so a finished export offers the
     * share sheet by itself; the flag makes that a one-shot rather than something that re-fires on
     * every recomposition of the row holding the effect.
     */
    private var autoShare by mutableStateOf(false)

    /**
     * Whether the file the notice is about went through the share sheet. The button beside the
     * notice re-offers the sheet either way; the label must not claim an "again" that never
     * happened - a save-to-device has not been shared yet.
     */
    var noticeWasShare by mutableStateOf(false)
        private set

    private var job: Job? = null

    /**
     * Which run of each kind the visible state belongs to.
     *
     * Cancelling flips a job's `isActive` at once but its catch and finally blocks only land a
     * dispatch later, by which time the user may already have started a second export; without this
     * token the dead job would overwrite the live one's progress with "Export cancelled." and leave
     * a running encode with no way to stop it.
     *
     * One token per KIND, emphatically not one shared between them. The two starters are two
     * buttons with no lockout between them, so either can be pressed while the other kind is in
     * flight - and a shared counter would be bumped out from under the running job, whose progress
     * writes, completion notice and finally-block cleanup would then all quietly no-op. That is a
     * frame counter or a "Saving..." that never goes away and a finished file whose notice is
     * simply dropped: worse than the mid-encode overwrite the token was added to prevent, because
     * it strands the UI rather than briefly lying to it. A job may only ever be superseded by
     * another of its own kind.
     *
     * Volatile because the frame callback reads it from the encoder's background thread.
     */
    @Volatile
    private var videoRunId = 0L

    @Volatile
    private var imageRunId = 0L

    fun start(request: JourneyExportRequest, share: Boolean = true) {
        if (job?.isActive == true) return
        val mine = ++videoRunId
        val speed = request.speed
        // Both, not just this one's. The notice carries a single message and a single "Share
        // again", so leaving the other kind's uri behind is what lets the button hand over a video
        // while the message beside it says an image was saved.
        clearSaved()
        progress = 1 to ExportTimeline.frameCount(speed)
        job = scope.launch {
            try {
                val uri = exportVideo(request, share) { done, total ->
                    // One state write per frame would recompose while the encoder is busy; a
                    // coarser counter reads the same to a human.
                    if (videoRunId == mine && (done == total || done % PROGRESS_STEP == 0)) {
                        progress = done to total
                    }
                }
                if (videoRunId == mine) {
                    claimNotice()
                    savedUri = uri
                    // Sharing does not save any more: the share-mode file lives in the app's own
                    // cache and goes straight to the sheet, and only "Save to device" writes the
                    // gallery (and does not open the sheet).
                    message = when {
                        share -> "Video ready to share."
                        savesToLibrary -> "Video saved to the library's journey folder."
                        else -> "Video saved to Movies/GameTwin."
                    }
                    noticeWasShare = share
                    autoShare = share
                    onExportSucceeded()
                }
            } catch (e: CancellationException) {
                if (videoRunId == mine) {
                    claimNotice()
                    message = "Export cancelled."
                }
                throw e
            } catch (t: Throwable) {
                // Throwable, not Exception: the codec path can hand back an Error (an OOM on a
                // 1920x1080 frame buffer, say), and letting that escape would take the screen's
                // whole coroutine scope down with it.
                if (videoRunId == mine) {
                    claimNotice()
                    message = "Could not export the video."
                }
            } finally {
                if (videoRunId == mine) progress = null
            }
        }
    }

    // ---- the still image ----

    /** True while a picture is being rendered and written; the action reads it to stand down. */
    var imageRunning by mutableStateOf(false)
        private set

    /** The finished picture, cleared once the share sheet has been offered it. */
    var imageUri by mutableStateOf<Uri?>(null)
        private set

    private var autoShareImage by mutableStateOf(false)
    private var imageJob: Job? = null

    /**
     * Renders the journey's finished frame as a PNG and, when it lands, hands it to the share
     * sheet the same way a finished video is handed over.
     *
     * Its own job rather than the video's: an image takes a moment where a video takes a minute,
     * and the two have no reason to be exclusive. The request is the video's, since a still is one
     * frame of the same export - the compact chart at the end of playback.
     */
    fun startImage(request: JourneyExportRequest, share: Boolean = true) {
        if (imageJob?.isActive == true) return
        val mine = ++imageRunId
        imageRunning = true
        clearSaved()
        imageJob = scope.launch {
            try {
                val uri = exportImage(request, share)
                if (imageRunId != mine) return@launch
                claimNotice()
                imageUri = uri
                message = when {
                    share -> "Image ready to share."
                    savesToLibrary -> "Image saved to the library's journey folder."
                    else -> "Image saved to Pictures/GameTwin."
                }
                noticeWasShare = share
                autoShareImage = share
                onExportSucceeded()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Throwable rather than Exception: a 1920x1080 ARGB frame is an allocation that can
                // hand back an Error, and letting that escape takes the screen's whole scope down.
                if (imageRunId == mine) {
                    claimNotice()
                    message = "Could not save the image."
                }
            } finally {
                if (imageRunId == mine) imageRunning = false
            }
        }
    }

    /** True once per finished picture, for the caller that opens the share sheet. */
    fun consumeImageShare(): Boolean {
        if (!autoShareImage) return false
        autoShareImage = false
        return true
    }

    fun cancel() {
        job?.cancel()
    }

    fun dismiss() {
        message = null
        clearSaved()
    }

    /**
     * Takes the notice for whatever just happened, of whichever kind.
     *
     * Called at every terminal state - saved, failed, cancelled - immediately before the message
     * that describes it. Starting an export is not the only moment the notice changes hands: the
     * two kinds run on separate jobs with no lockout, so an image asked for during an encode
     * finishes *after* the video that superseded it, and without this the older file would still be
     * sitting there behind a message about the newer one. There is one message and one "Share
     * again" beside it, so a leftover uri of either kind is not an extra affordance, it is a button
     * that hands over the wrong file.
     */
    private fun claimNotice() = clearSaved()

    /** Forgets whatever the notice was about, of either kind. */
    private fun clearSaved() {
        message = null
        savedUri = null
        imageUri = null
        autoShare = false
        autoShareImage = false
        noticeWasShare = false
    }

    /** True once per finished export, for the caller that opens the share sheet. */
    fun consumeAutoShare(): Boolean {
        if (!autoShare) return false
        autoShare = false
        return true
    }

    private companion object {
        const val PROGRESS_STEP = 10
    }
}

/**
 * Builds the export state on the caller's scope. Call it from the *screen*, never from inside a
 * branch of the state `when`: the scope dies with whatever composed it, and an encode that dies
 * because the user pressed Load is a minute of work thrown away without a word on screen.
 */
@Composable
internal fun rememberJourneyExportState(
    /** The library folder saves go into (under `journey/`), or null for the MediaStore gallery. */
    saveTreeUri: Uri? = null,
    onExportSucceeded: () -> Unit = {},
): JourneyExportState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope, saveTreeUri) {
        val video = JourneyVideoExporter(context)
        val image = JourneyImageExporter(context)
        JourneyExportState(
            savesToLibrary = saveTreeUri != null,
            exportVideo = { request, shareOnly, onProgress ->
                video.export(
                    shareOnly = shareOnly,
                    saveTree = saveTreeUri,
                    layout = request.layout,
                    frame = request.frame,
                    style = request.style,
                    bitmaps = request.bitmaps,
                    art = request.art,
                    userName = request.userName,
                    speed = request.speed,
                    onProgress = onProgress,
                    ranks = request.ranks,
                    ambience = request.ambience,
                    idleSigns = request.idleSigns,
                    followMode = request.followMode,
                    backdropDim = request.backdropDim,
                )
            },
            exportImage = { request, shareOnly ->
                image.export(
                    shareOnly = shareOnly,
                    saveTree = saveTreeUri,
                    layout = request.layout,
                    frame = request.frame,
                    style = request.style,
                    bitmaps = request.bitmaps,
                    art = request.art,
                    userName = request.userName,
                    ranks = request.ranks,
                    ambience = request.ambience,
                    idleSigns = request.idleSigns,
                    backdropDim = request.backdropDim,
                )
            },
            scope = scope,
            onExportSucceeded = onExportSucceeded,
        )
    }
}

/**
 * The one share action in the playback row: a button that opens a two-item menu, "Share video" and
 * "Share image". Either renders and then hands the file straight to the share sheet, so there is
 * still one affordance rather than an export button and a share button. A running video export
 * replaces the whole thing in place with its progress ring and a cancel.
 *
 * One button rather than two, and no second copy of it anywhere else. The picture used to be a row
 * inside the settings sheet, which put the two ways of sending a journey on in two unrelated places
 * - and the one most people want first was the one behind two taps of furniture that auto-hides.
 *
 * This is its own composable purely to own the read of [JourneyExportState.progress]: the chart and
 * the overview strip have nothing to say about an export, and recomposing them dozens of times
 * while the encoder is saturating the device is exactly what a handheld cannot afford.
 */
@Composable
internal fun ExportControls(
    export: JourneyExportState,
    canExport: Boolean,
    onShareVideo: () -> Unit,
    onShareImage: () -> Unit = {},
    onSaveVideo: () -> Unit = {},
    onSaveImage: () -> Unit = {},
    emphasized: Boolean = false,
    /** The bar's per-button size modifier, so this button shrinks with its whole row. */
    buttonModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uri = export.savedUri
    // A finished export opens the share sheet by itself. If the user backs out of the chooser the
    // notice below still carries the file, so nothing is lost by not asking first.
    LaunchedEffect(uri) {
        if (uri != null && export.consumeAutoShare()) shareJourneyVideo(context, uri)
    }

    val running = export.progress
    if (running != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ExportProgressRing(running, size = BAR_RING_SIZE)
            FocusableIconButton(
                icon = { Icon(Icons.Filled.Close, contentDescription = "Cancel export") },
                onClick = export::cancel,
                modifier = buttonModifier,
            )
        }
        return
    }
    if (!canExport) return

    var menuOpen by remember { mutableStateOf(false) }
    Box {
        // A finished journey is the moment worth sending on, so the share takes the accent then.
        // A colour rather than an animation: the end state already has three cards arriving, and a
        // pulsing button on top of that is a screen that will not settle.
        val accent = accentColor()
        FocusableIconButton(
            icon = { Icon(Icons.Filled.Share, contentDescription = SHARE_LABEL) },
            onClick = { menuOpen = true },
            // Size first, then the emphasis wash: the background fills whatever the size left.
            modifier = if (emphasized) {
                buttonModifier.background(accent.copy(alpha = 0.26f), CircleShape)
            } else {
                buttonModifier
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            // Share hands a cache file straight to the sheet; Save writes the gallery and stops
            // there. They were one action that always did both.
            DropdownMenuItem(
                text = { Text(SHARE_VIDEO_LABEL) },
                onClick = {
                    menuOpen = false
                    onShareVideo()
                },
            )
            DropdownMenuItem(
                // The picture is one frame of the same export, so it renders in a moment where the
                // video takes a minute; while one is being written the entries stand down rather
                // than starting a second.
                text = { Text(if (export.imageRunning) SHARING_IMAGE_LABEL else SHARE_IMAGE_LABEL) },
                enabled = !export.imageRunning,
                onClick = {
                    menuOpen = false
                    onShareImage()
                },
            )
            DropdownMenuItem(
                text = { Text(SAVE_VIDEO_LABEL) },
                onClick = {
                    menuOpen = false
                    onSaveVideo()
                },
            )
            DropdownMenuItem(
                text = { Text(SAVE_IMAGE_LABEL) },
                enabled = !export.imageRunning,
                onClick = {
                    menuOpen = false
                    onSaveImage()
                },
            )
        }
    }
}

/** What the one share button on the bar is called, and the two things it can do. */
internal const val SHARE_LABEL = "Share"
internal const val SHARE_VIDEO_LABEL = "Share video"
internal const val SHARE_IMAGE_LABEL = "Share image"
internal const val SAVE_VIDEO_LABEL = "Save video to device"
internal const val SAVE_IMAGE_LABEL = "Save image to device"
internal const val SHARING_IMAGE_LABEL = "Saving image"

/**
 * An export in flight, and then what became of it, with a way to send the file on. Reads
 * [JourneyExportState] for the same reason [ExportControls] does: to keep those reads out of the
 * Ready subtree.
 *
 * The in-flight half exists because an export outlives the player it was started from: closing the
 * player would otherwise leave an encode saturating the device with nothing on screen saying so
 * and no way to stop it. [showProgress] turns that half off for the player itself, where the bar's
 * own ring is already saying it.
 */
@Composable
internal fun ExportNotice(export: JourneyExportState, showProgress: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    // Composited, not raw: this app's `surfaceVariant` is the foreground colour at 12% alpha, so a
    // Surface painted with it directly reads as a near-invisible wash rather than a panel. Same
    // fix, same reason, as every other notice in this feature. See `chartPanelColor`.
    val panel = chartPanelColor(scheme.surfaceVariant, scheme.background)
    val running = export.progress
    if (showProgress && running != null) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = panel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExportProgressRing(running, size = NOTICE_RING_SIZE, describe = false)
                Text(
                    EXPORTING_LABEL,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                FocusableButton("Cancel", export::cancel, outlined = true)
            }
        }
        return
    }

    val message = export.message
    // Whichever kind of file this notice is about. Both auto-open the share sheet once when they
    // land, so this is only ever the fallback for a chooser that was backed out of.
    val video = export.savedUri
    val image = export.imageUri
    if (message != null) {
        val context = LocalContext.current
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = panel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                // "Share again" only when the sheet was already offered once; a save-to-device
                // has not been shared yet, and its button says the plain thing it does.
                val shareLabel = if (export.noticeWasShare) "Share again" else "Share"
                if (video != null) {
                    FocusableButton(shareLabel, { shareJourneyVideo(context, video) }, outlined = true)
                } else if (image != null) {
                    FocusableButton(shareLabel, { shareJourneyImage(context, image) }, outlined = true)
                }
                FocusableIconButton(
                    icon = { Icon(Icons.Filled.Close, contentDescription = "Dismiss") },
                    onClick = export::dismiss,
                )
            }
        }
    }
}

/**
 * How far an export has got, as a ring rather than a count.
 *
 * The frame numbers were exact and unreadable: "Exporting frame 43 of 481" is a pair of figures
 * about the encoder's internals, on a control bar where the only question is whether the thing is
 * still going. The ring fills with the same fraction and says it at a glance, and it is the same
 * shape as every other wait in the app. Determinate, because the total is genuinely known.
 *
 * [describe] is what a screen reader gets. The ring is the only thing the player's bar shows for a
 * running export, so there it carries the label; in the notice the very next line reads it out
 * already, and describing both is the same sentence twice.
 */
@Composable
private fun ExportProgressRing(progress: Pair<Int, Int>, size: Dp, describe: Boolean = true) {
    val (done, total) = progress
    val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    CircularProgressIndicator(
        progress = fraction,
        strokeWidth = 2.dp,
        color = accentColor(),
        modifier = Modifier.size(size)
            .then(if (describe) Modifier.semantics { contentDescription = EXPORTING_LABEL } else Modifier),
    )
}

/** What the ring means, for the notice beside it and for a screen reader on the bar. */
internal const val EXPORTING_LABEL = "Exporting the video"

/** Small enough to sit in the player's button row; large enough to read as a ring. */
private val BAR_RING_SIZE = 20.dp
private val NOTICE_RING_SIZE = 16.dp

/** Hands the finished video to whatever the user wants to send it with. */
internal fun shareJourneyVideo(context: Context, uri: Uri) =
    share(context, uri, "video/mp4", "Share journey video")

/** The same, for the still image. */
internal fun shareJourneyImage(context: Context, uri: Uri) =
    share(context, uri, JourneyImageExporter.MIME_TYPE, "Share journey image")

private fun share(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, chooserTitle)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
