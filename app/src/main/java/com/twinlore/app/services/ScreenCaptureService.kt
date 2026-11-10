package com.twinlore.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 9001

        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_SAVE_DIR = "saveDir"
        const val EXTRA_DISPLAY_ID = "displayId"
        // Identifies which caller requested the capture (e.g. "map", "ai") so the
        // result broadcast — which is sent package-wide and reaches every registered
        // receiver — is only consumed by the tab that asked for it.
        const val EXTRA_REQUEST_TAG = "requestTag"

        const val ACTION_CAPTURE = "com.twinlore.app.ACTION_CAPTURE"
        const val ACTION_STOP = "com.twinlore.app.ACTION_STOP_CAPTURE"
        const val ACTION_SCREENSHOT_TAKEN = "com.twinlore.app.SCREENSHOT_TAKEN"
        const val ACTION_PROJECTION_LOST = "com.twinlore.app.PROJECTION_LOST"
        const val EXTRA_FILE_PATH = "filePath"

        // Reactive projection state so on-screen indicators (the REC pill) and the
        // Settings "Screen capture" row can observe activity without polling.
        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        // Static convenience accessor kept for existing capture-time reads
        // (MapContent / AiAssistantScreen) — backed by the flow so both stay in sync.
        var isRunning: Boolean
            get() = _isRunningFlow.value
            private set(value) { _isRunningFlow.value = value }

        /**
         * Ends the active capture session without closing the app so the user can
         * regain privacy. Releasing the projection means the next screenshot re-prompts
         * for the system grant — that re-prompt is the intended privacy affordance.
         *
         * Delivers ACTION_STOP rather than calling stopService, so the projection
         * is released by an explicit command whatever else holds the service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
                .onFailure { context.stopService(Intent(context, ScreenCaptureService::class.java)) }
        }
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastSaveDir: String? = null
    private var lastDisplayId: Int = Display.DEFAULT_DISPLAY
    private var lastRequestTag: String? = null

    /**
     * The capture display, created once per projection and kept.
     *
     * Android 14 throws a SecurityException if createVirtualDisplay is called
     * more than once on one MediaProjection, and this service used to call it
     * per screenshot: the first shot worked, the second killed the capture. One
     * display now lives for the projection's lifetime and each shot swaps a
     * fresh ImageReader onto it with setSurface, which is the API Android 14
     * added for exactly this. The surface goes back to null after every shot,
     * so nothing is mirrored between them.
     */
    private var captureVirtualDisplay: VirtualDisplay? = null

    /**
     * Volatile: written by whichever thread asked for a shot and read by the
     * image listener on the capture thread, which decides from it whether it
     * still owns the display's surface.
     */
    @Volatile
    private var captureReader: ImageReader? = null
    private var captureWidth = 0
    private var captureHeight = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_STOP) {
            cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val saveDir = intent.getStringExtra(EXTRA_SAVE_DIR)
        val targetDisplayId = intent.getIntExtra(EXTRA_DISPLAY_ID, Display.DEFAULT_DISPLAY)

        if (saveDir != null) {
            lastSaveDir = saveDir
            lastDisplayId = targetDisplayId
            lastRequestTag = intent.getStringExtra(EXTRA_REQUEST_TAG)
        }

        // Subsequent capture — projection already alive
        if (intent.action == ACTION_CAPTURE && mediaProjection != null) {
            captureFrame()
            return START_NOT_STICKY
        }

        // If projection already exists from initial start, capture another frame.
        if (mediaProjection != null) {
            captureFrame()
            return START_NOT_STICKY
        }

        // First call — need to create MediaProjection from token
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }

        if (data == null || lastSaveDir == null) {
            Log.e(TAG, "Missing required extras")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        isRunning = true

        try {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaProjection", e)
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null")
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        handler = Handler(handlerThread!!.looper)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped by system")
                captureVirtualDisplay?.surface = null
                captureVirtualDisplay?.release()
                captureVirtualDisplay = null
                captureReader?.close()
                captureReader = null
                mediaProjection = null
                isRunning = false
                // Re-granting while the service lives on would otherwise
                // allocate a second capture thread and orphan this one.
                handlerThread?.quitSafely()
                handlerThread = null
                handler = null
                sendBroadcast(Intent(ACTION_PROJECTION_LOST).apply { setPackage(packageName) })
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, handler)

        captureFrame()

        return START_NOT_STICKY
    }

    private fun captureFrame() {
        val saveDir = lastSaveDir ?: return
        val projection = mediaProjection ?: return
        val captureHandler = handler ?: return

        val displayManager = getSystemService(DisplayManager::class.java)
        val targetDisplay = displayManager.getDisplay(lastDisplayId)
            ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        targetDisplay?.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        reader.setOnImageAvailableListener({ imgReader ->
            // Inside the try: a reader closed underneath this listener (the
            // next shot swapping in its own) makes acquireLatestImage throw,
            // and thrown from here it would land uncaught on the capture
            // thread rather than on anything that can report it.
            val image = try {
                imgReader.acquireLatestImage()
            } catch (e: Exception) {
                Log.w(TAG, "Frame gone before it could be read", e)
                null
            } ?: return@setOnImageAvailableListener

            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                val croppedBitmap = if (bitmap.width > width) {
                    Bitmap.createBitmap(bitmap, 0, 0, width, height).also {
                        if (it !== bitmap) bitmap.recycle()
                    }
                } else {
                    bitmap
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                val fileName = "${dateFormat.format(Date())}_Screenshot.png"
                val dir = File(saveDir)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)

                FileOutputStream(file).use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                croppedBitmap.recycle()

                Log.d(TAG, "Screenshot saved: ${file.absolutePath}")

                sendBroadcast(Intent(ACTION_SCREENSHOT_TAKEN).apply {
                    putExtra(EXTRA_FILE_PATH, file.absolutePath)
                    putExtra(EXTRA_REQUEST_TAG, lastRequestTag)
                    setPackage(packageName)
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error processing screenshot", e)
            } finally {
                image.close()
                // Detach rather than release: the display is reused for the
                // next shot, and pointing it at nothing is what stops the
                // mirror between them. Releasing it here would mean creating
                // another, which is the call Android 14 refuses.
                //
                // Only if this listener still owns the surface. Two shots close
                // together leave the first listener running after the second
                // has attached its own reader, and detaching then would strand
                // shot two with no surface and no frame - a spinner until the
                // watchdog.
                if (captureReader === imgReader) {
                    captureVirtualDisplay?.surface = null
                    captureReader = null
                }
                imgReader.close()
            }
        }, captureHandler)

        // Published BEFORE the surface is attached, not after.
        //
        // The listener runs on the capture thread and guards its detach on
        // `captureReader === imgReader`. Attaching first and publishing second
        // leaves a window - widened by the attach being an IPC into
        // DisplayManagerService - in which the previous shot's listener still
        // sees itself as the owner, and detaches the surface this shot just
        // attached. That shot then never receives a frame: no broadcast, and a
        // spinner until the watchdog, which is the symptom the guard exists to
        // prevent.
        val previous = captureReader
        captureReader = reader

        if (captureVirtualDisplay == null) {
            captureVirtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                captureHandler
            )
        } else {
            // resize, not a second createVirtualDisplay: the display outlives
            // rotations and display swaps, and recreating it is the call
            // Android 14 refuses.
            if (captureWidth != width || captureHeight != height) {
                captureVirtualDisplay?.resize(width, height, density)
            }
            captureVirtualDisplay?.surface = reader.surface
        }
        captureWidth = width
        captureHeight = height
        // Closed last: the display is pointed at the new reader above before
        // the old one goes, or the display briefly holds a dead surface.
        previous?.close()
    }

    private fun cleanup() {
        captureVirtualDisplay?.surface = null
        captureVirtualDisplay?.release()
        captureVirtualDisplay = null
        captureReader?.close()
        captureReader = null
        mediaProjection?.stop()
        mediaProjection = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        isRunning = false
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Screenshot capture ready")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
