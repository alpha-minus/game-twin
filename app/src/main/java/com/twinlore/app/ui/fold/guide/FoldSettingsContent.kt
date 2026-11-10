package com.twinlore.app.ui.fold.guide

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.twinlore.app.data.ThemePalette
import com.twinlore.app.ui.theme.toFontFamily
import com.twinlore.app.data.fold.FoldHandleStyle
import kotlinx.coroutines.delay

const val FOLD_SETTINGS_SIZE = "Handle size"
const val FOLD_SETTINGS_READING_SIZE = "Handle size while reading"
const val FOLD_SETTINGS_VISIBILITY = "Handle visibility"
const val FOLD_SETTINGS_READOUT = "Clock and battery"
const val FOLD_SETTINGS_HOLD_NOTE = "Hold the handle to leave Overlay Mode or close Game Twin."
const val FOLD_SETTINGS_TEXT_SIZE = "Text size"
const val FOLD_SETTINGS_HANDLE_ICON = "Handle icon"
const val FOLD_SETTINGS_FONT = "Font"

/** Said under the icons, because the handle changes at once and the launcher does not. */
const val FOLD_SETTINGS_HANDLE_ICON_NOTE =
    "The handle changes now. The app icon on your home screen follows the next time Game Twin closes."

/** The row that leaves the mode for the app's own settings screen. */
const val FOLD_SETTINGS_OPEN_APP = "All settings"

/** What that row costs, said before it is tapped rather than after. */
const val FOLD_SETTINGS_OPEN_APP_NOTE =
    "Leaves Overlay Mode and opens Game Twin's own settings."

/**
 * What the text-size row is set in, so the number has something to mean.
 *
 * The settings used to be a layer that faded while the slider was dragged, so
 * the guide underneath showed the size being chosen. Settings is its own
 * surface now and there is no page behind it, so it carries its own line of
 * prose instead and sets it at the size on the slider.
 *
 * Lines a player would know, because this is a reader for game guides and a
 * lorem ipsum here would be a missed joke. One per panel open, so the setting
 * does not feel like it changes what it is showing while you drag it.
 */
val FOLD_SETTINGS_TEXT_SAMPLES = listOf(
    "The cake is a lie.",
    "It's dangerous to go alone! Take this.",
    "War. War never changes.",
    "Do a barrel roll!",
    "Would you kindly.",
    "A winner is you.",
    "Thank you Mario! But our princess is in another castle!",
    "Hey! Listen!",
    "It's super effective!",
    "You died.",
    "Praise the sun!",
    "Stay awhile and listen.",
    "All your base are belong to us.",
    "You have died of dysentery.",
    "Snake? Snake?! Snaaaake!",
    "Objection!",
    "Finish him!",
    "Stay determined.",
    "Wake up, Mr. Freeman.",
    "Nothing is true, everything is permitted.",
    "The right man in the wrong place can make all the difference in the world.",
    "Endure and survive.",
    "Get over here!",
    "Time to kick ass and chew bubble gum.",
)
const val FOLD_SETTINGS_GUIDE_THEME = "Guide theme"
const val FOLD_SETTINGS_APP_THEME = "Theme"
const val FOLD_SETTINGS_SMART_FORMAT = "Text guide smart formatting"
const val FOLD_SETTINGS_ON = "On"
const val FOLD_SETTINGS_OFF = "Off"

/**
 * The text sizes the panel offers, as the reader's zoom fraction.
 *
 * The same range the app's own guide zoom slider uses, so a guide is the same
 * size wherever you open it, and a size chosen in one place is a size the
 * other can represent. Five preset chips could not: they were a coarse
 * instrument for the one setting people actually adjust, and the only row
 * whose values would not fit the panel's width.
 */
const val FOLD_TEXT_ZOOM_MIN = 0.5f
const val FOLD_TEXT_ZOOM_MAX = 3.0f

/** What the layer fades to while a slider is dragged. The app's display sheet's number. */
const val FOLD_SETTINGS_DRAG_ALPHA = 0.15f

/** How fine the slider is allowed to be. Whole percents read as noise. */
private const val FOLD_TEXT_ZOOM_GRAIN = 0.05f

/**
 * The nearest size the slider is allowed to stop on, clamped to the range.
 *
 * Rounding rather than ticking: Material draws a mark per step, and fifty of
 * them across a panel this narrow is a hatched line, not a scale. The slider
 * stays continuous and the value it reports is the tidy one.
 */
fun foldTextZoomStep(raw: Float): Float {
    val snapped = kotlin.math.round(raw / FOLD_TEXT_ZOOM_GRAIN) * FOLD_TEXT_ZOOM_GRAIN
    return snapped.coerceIn(FOLD_TEXT_ZOOM_MIN, FOLD_TEXT_ZOOM_MAX)
}

/**
 * The fold's own settings, as a layer over the page you were reading.
 *
 * A layer rather than a surface of its own so that a slider drag can fade it
 * out and show the page underneath at the size being chosen, which is what
 * the app's own display sheet does. They used to live in a menu opened by
 * holding the handle, hand-styled and sized to fit beside a 44dp mark; the
 * panel has the app's theme around it, so they belong here.
 */
@Composable
fun FoldSettingsContent(
    handleSizeDp: Int,
    handleAlpha: Float,
    handleRecess: Float,
    textZoom: Float,
    themeInject: Boolean,
    smartFormatting: Boolean,
    onHandleSizePicked: (Int) -> Unit,
    onHandleAlphaPicked: (Float) -> Unit,
    onHandleRecessPicked: (Float) -> Unit,
    onHandleStyleCommitted: () -> Unit,
    onHandleSizePreviewing: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    onTextZoomPicked: (Float) -> Unit,
    onThemeInjectPicked: (Boolean) -> Unit,
    onSmartFormattingPicked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    handleClockBattery: Boolean = false,
    onHandleClockBatteryPicked: (Boolean) -> Unit = {},
    themePalette: ThemePalette? = null,
    onThemePalettePicked: (ThemePalette) -> Unit = {},
    onToggleChrome: () -> Unit = {},
    appIcon: com.twinlore.app.data.AppIcon = com.twinlore.app.data.AppIcon.DEFAULT,
    selectableIcons: List<com.twinlore.app.data.AppIcon> = listOf(com.twinlore.app.data.AppIcon.DEFAULT),
    onAppIconPicked: (com.twinlore.app.data.AppIcon) -> Unit = {},
    appFont: com.twinlore.app.data.AppFont = com.twinlore.app.data.AppFont.DEFAULT,
    onAppFontPicked: (com.twinlore.app.data.AppFont) -> Unit = {},
) {
    val sliderInteraction = remember { MutableInteractionSource() }
    val dragging by sliderInteraction.collectIsDraggedAsState()
    // Any slider drag fades the whole layer to the app's display sheet's 0.15,
    // so the page underneath shows through at the size being chosen.
    val layerAlpha by animateFloatAsState(
        targetValue = if (dragging) FOLD_SETTINGS_DRAG_ALPHA else 1f,
        label = "fold_settings_alpha",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .alpha(layerAlpha)
            // A tap between the rows moves the bars, as it does on every other
            // surface. Watched rather than taken, so the chips, swatches and
            // sliders keep their own taps.
            .tapOnUnhandled(onToggleChrome)
            // Opaque under the rows: this draws over the reader, and the
            // palette's surfaceVariant alone would let it through.
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The guide's own appearance first: it is what somebody folded to read,
        // and the handle's two settings are housekeeping by comparison. The
        // app keeps these in a bottom sheet, which this window cannot host.
        TextZoomRow(textZoom, onTextZoomPicked, sliderInteraction)
        SettingRow(FOLD_SETTINGS_GUIDE_THEME) {
            Chip(FOLD_SETTINGS_ON, themeInject) { onThemeInjectPicked(true) }
            Chip(FOLD_SETTINGS_OFF, !themeInject) { onThemeInjectPicked(false) }
        }
        SettingRow(FOLD_SETTINGS_SMART_FORMAT) {
            Chip(FOLD_SETTINGS_ON, smartFormatting) { onSmartFormattingPicked(true) }
            Chip(FOLD_SETTINGS_OFF, !smartFormatting) { onSmartFormattingPicked(false) }
        }
        ThemePaletteRow(themePalette, onThemePalettePicked)
        FontRow(appFont, onAppFontPicked)
        // The handle's own two, as sliders rather than chips. They are the
        // settings you change while looking at the thing they change, and
        // three named steps could not land between two of themselves.
        //
        // Neither shares [sliderInteraction] with the text-size row above, on
        // purpose: that one fades this whole layer so the page shows through
        // at the size being chosen, and fading out the slider you are dragging
        // the handle with would hide the control and not the obstruction.
        HandleIconRow(appIcon, selectableIcons, onAppIconPicked)
        HandleSizeRow(handleSizeDp, onHandleSizePicked, onHandleStyleCommitted, onHandleSizePreviewing)
        // Directly under the size it is a fraction of, and above visibility,
        // so the two rows about how big the handle is sit together.
        HandleReadingSizeRow(handleRecess, onHandleRecessPicked, onHandleStyleCommitted)
        HandleVisibilityRow(handleAlpha, onHandleAlphaPicked, onHandleStyleCommitted)
        SettingRow(FOLD_SETTINGS_READOUT) {
            Chip(FOLD_SETTINGS_ON, handleClockBattery) { onHandleClockBatteryPicked(true) }
            Chip(FOLD_SETTINGS_OFF, !handleClockBattery) { onHandleClockBatteryPicked(false) }
        }
        Note(FOLD_SETTINGS_HOLD_NOTE)
        OpenAppSettingsRow(onOpenAppSettings)
    }
}

/**
 * The app's palette, as swatches: each one drawn in its own
 * background and foreground, so the choice is seen rather than read. A row of
 * its own and scrolled sideways, because thirty swatches will not fit a panel.
 */
@Composable
private fun ThemePaletteRow(selected: ThemePalette?, onPicked: (ThemePalette) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = FOLD_SETTINGS_APP_THEME,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(ThemePalette.entries) { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(option.background)
                        .border(
                            if (isSelected) 3.dp else 1.dp,
                            if (isSelected) accent else option.foreground,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onPicked(option) }
                        .semantics { contentDescription = option.displayName },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("A", color = option.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * The app's font, each chip set in the face it names, so the choice is seen
 * rather than read. Scrolled sideways: six names will not fit the panel.
 */
@Composable
private fun FontRow(selected: com.twinlore.app.data.AppFont, onPicked: (com.twinlore.app.data.AppFont) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = FOLD_SETTINGS_FONT,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(com.twinlore.app.data.AppFont.entries) { font ->
                val family = remember(font) {
                    font.toFontFamily(context)
                }
                Chip(
                    label = font.displayName,
                    selected = font == selected,
                    // The pixel face stands far bigger at the same size: the
                    // app's own 0.7 scale, so its chip matches its neighbours.
                    fontFamily = family,
                    fontScale = if (font == com.twinlore.app.data.AppFont.PIXEL) 0.7f else 1f,
                ) { onPicked(font) }
            }
        }
    }
}

/**
 * The handle's icon, which is the app's icon: one setting, as in the app's
 * own settings. Only the icons this user already has are offered; the rest
 * are bought or earned in the app.
 */
@Composable
private fun HandleIconRow(
    selected: com.twinlore.app.data.AppIcon,
    icons: List<com.twinlore.app.data.AppIcon>,
    onPicked: (com.twinlore.app.data.AppIcon) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = FOLD_SETTINGS_HANDLE_ICON,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(icons) { icon ->
                val isSelected = icon == selected
                // The launcher mipmap, the very art the handle draws. It is an
                // adaptive icon, which painterResource cannot load, so it goes
                // through the drawable and a bitmap once per icon.
                val bitmap = remember(icon) {
                    androidx.core.content.ContextCompat.getDrawable(context, icon.launcherMipmap)
                        ?.toBitmap(144, 144)
                        ?.asImageBitmap()
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            if (isSelected) 3.dp else 1.dp,
                            if (isSelected) accent else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onPicked(icon) }
                        .semantics { contentDescription = icon.displayName }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        Note(FOLD_SETTINGS_HANDLE_ICON_NOTE)
    }
}

/**
 * Text size, on its own row because a slider needs the width.
 *
 * Dragged live and written throttled, the same shape as the app's guide zoom
 * popup: the reader is a WebView and re-laying it out on every pixel of travel
 * is what makes a slider feel stuck. [onTextZoomPicked] also writes the
 * bookmark, so the throttle is saving a disk write per frame as well.
 */
@Composable
private fun TextZoomRow(
    textZoom: Float,
    onTextZoomPicked: (Float) -> Unit,
    interactionSource: MutableInteractionSource,
) {
    var dragging by remember { mutableStateOf(false) }
    var live by remember { mutableFloatStateOf(textZoom) }
    // Anything that changes the size from elsewhere - opening another guide,
    // the app's own slider - moves the thumb, but never mid-drag.
    LaunchedEffect(textZoom) { if (!dragging) live = textZoom }
    LaunchedEffect(dragging) {
        if (dragging) {
            while (true) {
                delay(TEXT_ZOOM_THROTTLE_MS)
                onTextZoomPicked(foldTextZoomStep(live))
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = FOLD_SETTINGS_TEXT_SIZE,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(foldTextZoomStep(live) * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = live,
            onValueChange = {
                live = it
                dragging = true
            },
            onValueChangeFinished = {
                dragging = false
                live = foldTextZoomStep(live)
                onTextZoomPicked(live)
            },
            valueRange = FOLD_TEXT_ZOOM_MIN..FOLD_TEXT_ZOOM_MAX,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        // The size, shown rather than named. Scaled from the same body style
        // the readers use, off the live slider value, so it moves under the
        // thumb as it is dragged.
        val sample = remember { FOLD_SETTINGS_TEXT_SAMPLES.random() }
        val body = MaterialTheme.typography.bodyMedium
        Text(
            text = sample,
            style = body.copy(
                fontSize = body.fontSize * live,
                lineHeight = body.fontSize * live * 1.35f,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Long enough that the WebView is not re-laying out per frame, short enough to feel live. */
private const val TEXT_ZOOM_THROTTLE_MS = 200L

/**
 * How big the handle is, in dp.
 *
 * Applied on every value the slider reports and written only on release. The
 * apply is a `updateViewLayout` on a window that is already up, which is cheap
 * enough per frame; the write is DataStore, which is not.
 *
 * [onPreviewing] is what makes the slider honest. The handle is drawn at 62%
 * while the panel is up so it does not cover the page, which would make every
 * size on this slider look like 62% of itself. It goes to true size for as
 * long as the slider is held.
 */
@Composable
private fun HandleSizeRow(
    sizeDp: Int,
    onPicked: (Int) -> Unit,
    onCommitted: () -> Unit,
    onPreviewing: (Boolean) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var live by remember { mutableIntStateOf(sizeDp) }
    // Anything that changes the size from elsewhere (the app's own settings
    // row) moves the thumb, but never mid-drag.
    LaunchedEffect(sizeDp) { if (!dragging) live = sizeDp }
    // Tied to the composable's life, not to the release handler alone: a panel
    // closed mid-drag would otherwise leave the handle stuck at true size.
    DisposableEffect(Unit) { onDispose { onPreviewing(false) } }

    SliderRow(
        label = FOLD_SETTINGS_SIZE,
        valueText = FoldHandleStyle.sizeLabel(live),
        value = FoldHandleStyle.sizeFraction(live),
        onValueChange = { fraction ->
            if (!dragging) {
                dragging = true
                onPreviewing(true)
            }
            val next = FoldHandleStyle.sizeDpFromFraction(fraction)
            if (next != live) {
                live = next
                onPicked(next)
            }
        },
        onValueChangeFinished = {
            dragging = false
            onPreviewing(false)
            onCommitted()
        },
    )
}

/**
 * How big the handle is drawn while the panel is open, as a percentage of the
 * size above.
 *
 * The one slider here that needs no preview of any kind. The handle is
 * recessed exactly when the panel is open, and the panel is open whenever this
 * row is on screen, so dragging it moves the thing itself: what you see in the
 * corner is the answer, not a stand-in for it.
 */
@Composable
private fun HandleReadingSizeRow(
    recess: Float,
    onPicked: (Float) -> Unit,
    onCommitted: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var live by remember { mutableFloatStateOf(recess) }
    LaunchedEffect(recess) { if (!dragging) live = recess }

    SliderRow(
        label = FOLD_SETTINGS_READING_SIZE,
        valueText = FoldHandleStyle.recessPercentLabel(live),
        value = FoldHandleStyle.recessFraction(live),
        onValueChange = { fraction ->
            dragging = true
            val next = FoldHandleStyle.recessFromFraction(fraction)
            if (next != live) {
                live = next
                onPicked(next)
            }
        },
        onValueChangeFinished = {
            dragging = false
            onCommitted()
        },
    )
}

/**
 * How solid the handle is, as a percentage.
 *
 * Visibility rather than transparency, so the slider and the handle move the
 * same way: all the way right is a handle you can see. 0% is a handle that is
 * invisible and still tappable, which is a real thing to want over a game and
 * the reason this row and the app's own both exist.
 */
@Composable
private fun HandleVisibilityRow(
    alpha: Float,
    onPicked: (Float) -> Unit,
    onCommitted: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var live by remember { mutableFloatStateOf(alpha) }
    LaunchedEffect(alpha) { if (!dragging) live = alpha }

    SliderRow(
        label = FOLD_SETTINGS_VISIBILITY,
        valueText = FoldHandleStyle.alphaPercentLabel(live),
        value = live,
        onValueChange = { raw ->
            dragging = true
            val next = FoldHandleStyle.alpha(raw)
            if (next != live) {
                live = next
                onPicked(next)
            }
        },
        onValueChangeFinished = {
            dragging = false
            onCommitted()
        },
    )
}

/** The label, the reading, and the slider under both. */
@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The way out to the app's own settings.
 *
 * This surface carries the handful of settings worth changing without leaving
 * the game, and it is deliberately not all of them: the panel is a reader over
 * something else, not a second copy of the app. Everything else is one row
 * away, and the row says what it costs, because the app cannot be shown over a
 * game and coming back is a fold away.
 */
@Composable
private fun OpenAppSettingsRow(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = FOLD_SETTINGS_OPEN_APP,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = FOLD_SETTINGS_OPEN_APP_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingRow(label: String, chips: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(150.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    fontScale: Float = 1f,
    onClick: () -> Unit,
) {
    // Two layers under the unselected chip for the same reason the bar has
    // them: surfaceVariant is a 12%-alpha foreground in this app's palettes,
    // and alone it lets whatever is behind the panel through.
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val base = MaterialTheme.typography.labelLarge
    Text(
        text = label,
        style = if (fontFamily == null) base else base.copy(
            fontFamily = fontFamily,
            fontSize = base.fontSize * fontScale,
        ),
        maxLines = 1,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .background(background)
            .clickable(onClick = onClick)
            // 12dp of vertical padding around a labelLarge clears the minimum
            // touch target without the row having to measure anything.
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
