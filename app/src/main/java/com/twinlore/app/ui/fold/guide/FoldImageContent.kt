package com.twinlore.app.ui.fold.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twinlore.app.ui.screens.MapZoomableImage

const val FOLD_IMAGE_NONE = "No images in this vault. Open the app to add one."
const val FOLD_IMAGE_MISSING = "This image is not on the device"

/**
 * The file is there but nothing could be decoded from it.
 *
 * Said out loud because the surface behind the picture is black: a decode that
 * failed used to look exactly like a picture that happens to be dark, and the
 * panel sat there showing nothing with no way to tell which it was.
 */
const val FOLD_IMAGE_UNREADABLE = "This image could not be opened here. Open the app to view it."
const val FOLD_IMAGE_PREVIOUS = "Previous image"
const val FOLD_IMAGE_NEXT = "Next image"
const val FOLD_IMAGE_SCREENSHOT = "Take a screenshot"
const val FOLD_IMAGE_DELETE = "Delete this image"

/**
 * The vault's images, one at a time, with the same pinch, double-tap and pan
 * the app gives them.
 *
 * Deliberately not the app's `MapTabContent`: that is 3300 lines built around a
 * non-null `MainViewModel` and three `hiltViewModel()` handles, and marker
 * editing, picker sheets and artwork assignment are all things this window
 * cannot host anyway. What survives the trip is what people fold to use, which
 * is looking at a map and stepping to the next one, so that is what this is.
 * The zoom itself is the same `telephoto` surface the guide's image viewer
 * uses, at the same 8x ceiling.
 */
@Composable
fun FoldImageContent(
    images: List<FoldImage>,
    index: Int,
    /**
     * The vault these images belong to, handed to the app's viewer so its
     * decoded-bitmap cache is keyed the same way here as in the app. Without
     * it the panel asked under a different key, missed everything the app had
     * already decoded, and decoded each picture again at full size next to a
     * running game.
     */
    vaultId: String?,
    chromeVisible: Boolean,
    /**
     * How tall the panel's bottom bar is, so the shutter can sit above it.
     *
     * On this surface the bars float over the picture rather than shrinking
     * it, so a shutter pinned to the bottom edge ended up beside the gesture
     * bar and half under the panel's own bar: a button in the one strip of
     * screen the system also wants. Lifted clear of the bar it is back where
     * it was, above the bar on the right, which is where a thumb already is.
     */
    bottomBarHeight: Dp = 0.dp,
    /**
     * How far in from the right edge the shutter sits, so it lands at the
     * bar's end rather than the screen's. The bar is a centred pill and the
     * gap either side of it is what this carries.
     */
    shutterEndPadding: Dp = 16.dp,
    capturing: Boolean,
    view: FoldImageView?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleChrome: () -> Unit,
    onOpenApp: () -> Unit,
    onTakeScreenshot: () -> Unit,
    /** The bin opposite the shutter. Asks first; see [FoldDeleteImagePrompt]. */
    onDeleteImage: () -> Unit = {},
    onViewChanged: (FoldImageView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = images.getOrNull(index)
    if (current == null) {
        // Not EmptyWithApp on its own: a vault with no images is the state the
        // camera button is most useful in, since the first shot is what ends it.
        //
        // The tap is the same one the picture itself carries: an empty surface
        // has nothing that takes a touch, so without it a bar hidden here could
        // not be brought back. The app button and the shutter consume their own
        // taps and so are not this one.
        Box(modifier = modifier.fillMaxSize().tapOnUnhandled(onToggleChrome)) {
            EmptyWithApp(FOLD_IMAGE_NONE, onOpenApp)
            // With the chrome, exactly as on a picture. It used to show here
            // whatever the chrome was doing, which meant that with the bar
            // hidden it had nothing to sit above and fell back to the bottom
            // edge: the strip the gesture bar wants, and where a handle parked
            // in the corner lands on top of it. One rule for both states is
            // also one place to look for it.
            if (chromeVisible) {
                ShutterButton(
                    capturing = capturing,
                    onClick = onTakeScreenshot,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = shutterEndPadding, bottom = bottomBarHeight + 8.dp),
                )
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (current.uri == null) {
            // The row exists but the file behind it does not: a vault synced
            // without its images, or a file deleted underneath. Saying so beats
            // a black rectangle that looks like a failed decode.
            Text(
                text = FOLD_IMAGE_MISSING,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        } else {
            // The app's own MapZoomableImage, not telephoto's ZoomableAsyncImage.
            //
            // Two reasons, and the second is the one that forced it. The pan and
            // zoom now feel identical in both places because they are the same
            // gesture code. And telephoto 0.7.1 exposes no way to restore a
            // pan: its ZoomableState is read-only for position, its GestureState
            // is internal and its Saver is private, so an image opened in the
            // panel could only ever start centred - which is exactly the
            // complaint. MapZoomableImage takes its position as three floats
            // and hands them back, which is all persistence needs.
            // Reset per picture: a failure belongs to the image that failed,
            // and stepping to the next one asks the question again.
            var unreadable by remember(current.id) { mutableStateOf(false) }
            if (unreadable) {
                Text(
                    text = FOLD_IMAGE_UNREADABLE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            key(current.id) {
                MapZoomableImage(
                    imagePath = current.uri,
                    vaultId = vaultId,
                    initialZoom = view?.zoom ?: 1f,
                    initialOffsetX = view?.offsetX ?: 0f,
                    initialOffsetY = view?.offsetY ?: 0f,
                    onLoadResult = { ok -> unreadable = !ok },
                    onStateChange = { zoom, x, y ->
                        onViewChanged(FoldImageView(zoom, x, y))
                    },
                    // A tap clears the chrome off the picture, the same way a
                    // tap on a guide page clears it off the text. Whatever the
                    // zoom: a zoomed-in map is exactly when the bar is most in
                    // the way, and a tap is not a pan.
                    onTouch = onToggleChrome,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // The same chevrons the app's Image tab carries, and in the same
        // places: at the edges, vertically centred, where a thumb already is.
        if (images.size > 1 && chromeVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
            ) {
                StepButton(
                    icon = Icons.Default.ChevronLeft,
                    description = FOLD_IMAGE_PREVIOUS,
                    enabled = index > 0,
                    onClick = onPrevious,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                StepButton(
                    icon = Icons.Default.ChevronRight,
                    description = FOLD_IMAGE_NEXT,
                    enabled = index < images.lastIndex,
                    onClick = onNext,
                )
            }
        }

        // Bottom right, clear of both chevrons, and it goes with the rest of
        // the chrome: the point of hiding the chrome is to see the picture.
        if (chromeVisible) {
            ShutterButton(
                capturing = capturing,
                onClick = onTakeScreenshot,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = shutterEndPadding, bottom = bottomBarHeight + 8.dp),
            )
            // Opposite the shutter: the bar's other end, at the same height.
            // It used to sit stacked above the shutter, which built a toolbar
            // over the picture and put the button you almost never want one
            // slip away from the one you use most. Only on a picture; the
            // empty surface has nothing to remove. It never deletes on its
            // own: the tap raises a question the panel draws.
            DeleteButton(
                onClick = onDeleteImage,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = shutterEndPadding, bottom = bottomBarHeight + 8.dp),
            )
        }
    }
}

/**
 * The bin. The shutter's backing and size, so the two corners read as one set
 * of controls, but an outlined glyph in the plain foreground colour rather than
 * the error colour: red on every picture drew the eye to the one button nobody
 * wants most of the time. The warning lives in the prompt's Delete, which is
 * where the decision is made.
 */
@Composable
private fun DeleteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .background(
                MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                CircleShapeSmall,
            ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = FOLD_IMAGE_DELETE,
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * The shutter.
 *
 * What it captures is the screen behind the panel, which is the game: that is
 * the whole reason to have one here rather than in the app, where the app is
 * what would be in the picture.
 *
 * The theme's colours, not the chevrons' fixed white on black. The chevrons
 * only ever sit on a photo, where white is the safe answer whatever the theme;
 * this one also appears on the empty surface, which is drawn in the app's
 * background, and white on white is a button nobody can find. Backed by the
 * theme's own background at most of its opacity so it stays legible over a
 * bright photo too.
 */
@Composable
private fun ShutterButton(
    capturing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.primary
    IconButton(
        onClick = onClick,
        enabled = !capturing,
        modifier = modifier
            .size(48.dp)
            .background(
                MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                CircleShapeSmall,
            ),
    ) {
        if (capturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = tint,
            )
        } else {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = FOLD_IMAGE_SCREENSHOT,
                tint = tint,
            )
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = if (enabled) 0.5f else 0.2f), CircleShapeSmall),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
        )
    }
}

private val CircleShapeSmall = androidx.compose.foundation.shape.CircleShape
