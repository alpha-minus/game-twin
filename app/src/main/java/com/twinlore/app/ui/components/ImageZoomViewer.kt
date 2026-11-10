package com.twinlore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.rememberZoomableImageState

/**
 * Fullscreen pinch-to-zoom image viewer. Originally introduced for the Guide tab
 * (tap on inlined image in a saved guide page); now shared by any caller that
 * wants a "tap thumbnail → fullscreen zoom" experience.
 *
 * Caller passes [model] as either a `file://…` String (resolved against an MHT
 * manifest or local cache), an `https://…` String (Coil fetches), or any other
 * Coil-acceptable model.
 *
 * Dismiss paths: tap outside the image, single-tap-on-image when at base zoom,
 * the close (X) button, or system back. Single-tap-on-image only dismisses when
 * the user has not zoomed in (zoomFraction <= 0.05f) so panning around a zoomed
 * detail doesn't accidentally close. Pinch / drag / double-tap handled by telephoto.
 */
@Composable
fun ImageZoomViewer(
    model: Any,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            // Outer Box handles tap-outside ourselves so the image area isn't
            // dismiss-on-tap; Dialog's click-outside detector would dismiss on any
            // tap inside the dialog window.
            dismissOnClickOutside = false
        )
    ) {
        ImageZoomSurface(model = model, onDismiss = onDismiss)
    }
}

/**
 * The same viewer without the Dialog around it, drawn in the caller's own
 * space.
 *
 * For windows that cannot host a Dialog. The fold panel's overlay is one:
 * android.app.Dialog asks for a TYPE_APPLICATION window and an overlay's
 * window context refuses it, so the whole viewer would be a crash rather than
 * a zoomed image. Callers draw this last, over everything else.
 */
@Composable
fun ImageZoomSurface(
    model: Any,
    onDismiss: () -> Unit
) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Taps on this Box only reach it when they miss the image;
                // Compose hit-testing means the image absorbs taps in its bounds.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // maxZoomFactor bumped well past the fit-to-screen default of 1f so
            // users can dig into pixel-art game maps and dense screenshots; 8x is
            // generous without being absurd; double-tap toggles between fit and ~2x.
            val zoomableImageState = rememberZoomableImageState(
                rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f))
            )
            ZoomableAsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                state = zoomableImageState,
                // Tap-on-image dismisses only when the user has not zoomed in.
                // zoomFraction is null while content is loading; treat that as
                // "not yet interactive" and ignore the tap.
                onClick = {
                    val zoom = zoomableImageState.zoomableState.zoomFraction
                    if (zoom != null && zoom <= 0.05f) onDismiss()
                }
            )
            // 56dp tap target with a translucent black circular backdrop so the
            // X stays visible against light backgrounds, sat 16dp in from the
            // edge so it's thumb-reachable in landscape (the previous 8dp
            // padding pushed it under the bezel curve on edge-display devices).
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
}
