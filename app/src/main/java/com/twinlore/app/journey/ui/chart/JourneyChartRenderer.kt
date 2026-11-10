package com.twinlore.app.journey.ui.chart

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.twinlore.app.data.retroachievements.RaUrls
import com.twinlore.app.journey.domain.AwardGrading
import com.twinlore.app.journey.domain.ChartLayout
import com.twinlore.app.journey.domain.CurveLayout
import com.twinlore.app.journey.domain.IdleSigns
import com.twinlore.app.journey.domain.JourneyStats
import com.twinlore.app.journey.domain.LayoutEngine
import com.twinlore.app.journey.domain.MedalLayout
import com.twinlore.app.journey.domain.NodeLayout
import com.twinlore.app.journey.domain.TickGenerator
import com.twinlore.app.journey.domain.pluralize
import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.GameRef
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.ui.JourneyStripText
import com.twinlore.app.journey.ui.JourneyUrls
import com.twinlore.app.journey.ui.colorForRivalIndex
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Every colour and font the chart draws with, resolved once from the theme by the screen. */
data class ChartStyle(
    val background: Color,
    val panel: Color,
    val grid: Color,
    val ink: Color,
    val inkDim: Color,
    val accent: Color,
    /**
     * The per-game palette. Nothing on the chart itself draws with it any more - a badge's colour
     * is its owner's - and it is kept for the furniture that names a game rather than a player:
     * the now-playing chip's ring in the overview strip. See [rivalColors] for identity.
     */
    val seriesColors: List<Color>,
    val rivalColors: List<Color>,
    val typeColors: Map<AchievementType, Color>,
    val silver: Color,
    val gold: Color,
    val labelFont: FontFamily,
    /**
     * Whether [labelFont] is the pixel font (Press Start 2P).
     *
     * The chart's text sizes are raw numbers rather than `Typography` styles, so the 0.7 scale
     * `buildTypography` applies to every pixel-font text style in the app never reached them - and
     * that font's glyphs are wide enough that the axis ticks ran into each other (user report).
     * Only what actually collides is scaled; see [PIXEL_TICK_LABEL].
     *
     * A flag rather than the font itself, because "is this the pixel one" is the whole question and
     * a `FontFamily` cannot answer it without knowing where it came from. Defaults false so the
     * many callers that build a style for a test keep drawing what they drew.
     */
    val pixelFont: Boolean = false,
)

/**
 * The ring around a badge that was earned hardcore, and the accent the tooltip says "Hardcore" in.
 *
 * Mirrors RetroAchievements' hardcore badge border: a warm saturated yellow, sampled from the site
 * itself. It is close in hue to [ChartStyle.gold], and that is the point rather than a collision -
 * matching the site is what makes the ring mean something to somebody who already knows what a
 * hardcore badge looks like there. The two are still separate hexes, and they never share a place
 * on an icon: this is the ring around the whole plate, gold is the kind dot in an award's corner.
 *
 * On its own this is a pale colour on a pale surface in a light theme (about 1.6:1 against white),
 * so nothing draws it bare - see [HARDCORE_EDGE] for the ring and [hardcoreTextColor] for the word.
 *
 * One constant, on purpose: everything that has to agree with it (the chart's ring, the tooltip's
 * accent, the legend's swatch) reads it from here, so retuning it against the site is one edit. Not
 * part of [ChartStyle] because it does not follow the theme - like the series and rival palettes, it
 * has to stay the same colour in every one.
 */
val HARDCORE_RING: Color = Color(0xFFF0C64B)

/**
 * The streak flame's own two-tone orange: outer and inner/core.
 *
 * Fixed, like [HARDCORE_RING], rather than drawn from [ChartStyle.gold] - the flame used to draw
 * in the theme's gold, which was the last place on the chart gold meant something other than an
 * award or the hardcore ring. Two distinct hexes, checked against every series, rival, type,
 * gold, silver and [HARDCORE_RING] colour in `ChartColorsTest`, so a future palette change cannot
 * quietly land the flame back on gold's family.
 */
val FLAME_OUTER: Color = Color(0xFFFF8A3C)
val FLAME_INNER: Color = Color(0xFFFFB25E)

/**
 * How much of [ChartStyle.ink] the hairline around the hardcore ring carries.
 *
 * [HARDCORE_RING] is a light yellow, and a light yellow on a light theme's background is a ring you
 * cannot see: about 1.6:1 against white, well under any legibility floor. A one-unit edge in the
 * theme's own ink, just outside the yellow, gives it a boundary in both themes - definition on a
 * light one, and depth on a dark one, where it reads as the shadow a raised plate would cast.
 */
private const val HARDCORE_EDGE = 0.3f

/**
 * The colour the tooltip says "Hardcore" in: the ring's own yellow on a dark theme, and the same
 * yellow pulled [HARDCORE_TEXT_INK] of the way toward the ink on a light one.
 *
 * The ring gets an edge to solve this; a word cannot, so the word darkens instead. It stays
 * recognisably the ring's colour, which is the whole reason it is coloured at all - somebody who has
 * learned "yellow ring means hardcore" on the chart gets that reinforced in the one place that
 * spells the word out.
 */
internal fun hardcoreTextColor(style: ChartStyle): Color =
    if (style.background.luminance() > 0.5f) {
        lerp(HARDCORE_RING, style.ink, HARDCORE_TEXT_INK)
    } else {
        HARDCORE_RING
    }

/**
 * How far toward the ink the tooltip's "Hardcore" is pulled on a light theme.
 *
 * The value the product owner asked for. Worth knowing what it buys: on a light theme's panel it
 * takes the word from roughly 1.6:1 to roughly 2.2:1, which is better and is still under the 4.5:1
 * usually wanted of body text. Raising this one number is the whole fix if that matters more than
 * staying close to the site's yellow.
 */
private const val HARDCORE_TEXT_INK = 0.2f

private enum class Anchor { LEFT, CENTER, RIGHT }

/**
 * Draws the journey chart into a [DrawScope] that has already been transformed into the
 * 1060x600 virtual space of [com.twinlore.app.journey.domain.LayoutEngine], and answers the
 * playback questions the composable needs (what is revealed, which nodes are on screen).
 *
 * Everything here is pure: no composables, no state, no time. Playback is a single [progress]
 * value, and the badge pop-in is supplied by the caller as [nodeAppearProgress], so a frame
 * costs exactly one pass over the layout.
 */
object JourneyChartRenderer {

    // Text sizes are in virtual units and deliberately whole numbers: the TextMeasurer cache is
    // keyed on the whole text style, so a fractional (or animated) size misses it on every frame.
    // They were roughly two thirds of these values, which read fine on a monitor and not at all on
    // a 1080p handheld held at arm's length.
    private const val LABEL = 14f            // rival names, tooltip game title
    private const val TICK_LABEL = 14f

    /**
     * The same label in the pixel font, which needs to be smaller to fit.
     *
     * Its glyphs are wide and fixed-width, so a date that fits between two gridlines in every other
     * font overruns the next one and the axis reads as one smeared row (user report). Ten is the
     * app's own 0.7 pixel scale (`buildTypography`) on [TICK_LABEL], rounded to a whole number for
     * the reason the note above gives: a fractional size misses the measurer's cache every frame.
     *
     * Only the ticks. The endpoint dates and the span label share a line under the axis but there
     * are three of them across the whole width, and they were not what collided.
     */
    private const val PIXEL_TICK_LABEL = 10f
    private const val AXIS_VALUE = 14f

    /** [AXIS_VALUE] at the app's own 0.7 pixel scale, rounded whole like [PIXEL_TICK_LABEL]. */
    private const val PIXEL_AXIS_VALUE = 10f
    private const val ENDPOINT_DATE = 14f
    /**
     * The chart's own furniture along the top of the stage: the caption in the left corner and the
     * running date in the right, with the y each is drawn at.
     *
     * Internal rather than private because the exports draw a counter strip into the band under
     * them, and where that band starts is a function of these. A test pins the two against each
     * other, so growing the caption fails loudly instead of quietly overlapping it.
     */
    internal const val CAPTION = 14f
    internal const val CAPTION_Y = 16f
    internal const val OVERLAY = 18f
    internal const val OVERLAY_Y = 12f

    /**
     * How many steps the running-date overlay's clock is quantised to: the same hundred
     * [com.twinlore.app.journey.ui.chart.JourneyPlaybackController.REPORT_STEP] divides playback
     * into, and the same the player's own strip reads at.
     */
    private const val OVERLAY_STEPS = 100

    /** How far down the stage the chart's own chrome reaches, in virtual units. */
    internal val chromeBottom: Float get() = max(CAPTION_Y + CAPTION, OVERLAY_Y + OVERLAY)
    private const val TIP_TITLE = 15f
    private const val TIP_BODY = 13f

    /**
     * The main player's line, and the glow under it.
     *
     * Thicker than it was (3.5 with a 12-unit glow). The line is the whole story the chart tells and
     * it was the thinnest mark on a stage full of icons; the glow is raised in the same proportion so
     * the halo still reads as a halo rather than as a second, wider line.
     */
    private const val MAIN_STROKE = 4.5f
    private const val MAIN_GLOW = 15f

    private const val TIP_MIN_WIDTH = 320f
    private const val TIP_TEXT_WIDTH = 300f
    private const val TIP_PAD = 10f
    private const val TIP_BORDER = 4f
    private const val TIP_ICON_R = 16f
    /** How far a tooltip box stays clear of the edge of what is on screen. */
    private const val TIP_MARGIN = 6f
    private const val TIP_GAP = 4f
    /** Reveal slack, shared with the backdrop and the HUD so all three agree. */
    private val PLAYBACK_EPSILON = ChartConstants.REVEAL_TOLERANCE

    /**
     * Extra virtual units a viewport-culled icon's bounding circle is padded by before it counts
     * as "on screen": enough to cover the leader line back to its anchor and the caption beside it,
     * so culling only ever drops something with no visible trace, never a badge whose label alone
     * would have peeked into the viewport.
     */
    private const val CULL_ALLOWANCE = 40f

    // ---- the badge's ring and its owner dot (see [drawNodes]) ----
    /** How thick the [HARDCORE_RING] ring around a hardcore badge is drawn. */
    private const val HARDCORE_RING_WIDTH = 2.4f
    /** How much of the owner's colour a boundary takes on a compare chart; see [ownerBoundary]. */
    private const val OWNER_BOUNDARY_MIX = 0.5f
    /** The plate outline every other badge keeps, so a softcore badge still has an edge. */
    private const val PLATE_EDGE = 1.8f
    private const val PLATE_EDGE_ALPHA = 0.8f

    // ---- the zoomed-in date (see [drawZoomDate]) ----
    /**
     * Smaller than it was (13f, user request), and it can afford to be: this is now the ONLY place
     * a followed chart says where the sweep is in time - the single game's header and the compare
     * plate both used to carry a date and no longer do - so it is read on purpose rather than
     * caught out of the corner of the eye, and a whisper in the corner is what it was always
     * described as.
     */
    private const val ZOOM_DATE_TEXT = 11f
    private const val ZOOM_DATE_MARGIN = 12f
    private const val ZOOM_DATE_ALPHA = 0.85f

    // ---- streak fire (see [drawStreakFire]) ----
    /** How much orange shows either side of the main line: the fire band is this much wider per side. */
    private const val FIRE_RIM = 2.6f
    /** The glow under a burning stretch, as a fraction of full alpha. */
    private const val FIRE_GLOW_ALPHA = 0.3f


    // ---- the medallion's kind dot (see [drawKindDot]) ----
    private const val MEDAL_DOT_SCALE = 0.34f
    private const val MEDAL_DOT_MIN_R = 4.5f
    /** How far a kind dot's glow reaches past the dot itself, as a multiple of its radius. */
    private const val MEDAL_DOT_GLOW = 2.6f
    /** How thick a hollow (beaten) kind dot's ring is drawn. */
    private const val MEDAL_DOT_RING = 2.2f

    /**
     * The medallion dot's glow, for the readouts that draw the same award in a line of text: the
     * strip's breakdown chips, the single-game header's own award, the exported strip's dots.
     *
     * An award circle was a flat disc everywhere except on the chart, so the one mark the whole
     * feature is about was the only one that looked different depending on which surface drew it.
     * The alpha is the medallion's exactly ([drawKindDot]); the reach is shorter, because a readout
     * draws its award inside a box of its own with words either side of it, and 2.6 radii of glow
     * would wash the count beside it. Same falloff, bounded.
     */
    internal const val AWARD_GLOW_ALPHA = 0.55f
    internal const val AWARD_GLOW_SPAN = 1.8f

    // ---- reveal effects (see [drawFloatingPoints], [drawMedalBursts], [drawMedalWash]) ----
    /** How long a "+N" rises off a badge, in seconds. */
    const val FLOATING_POINTS_SECONDS = 1.2f
    /** How long a medal's confetti burst lives, in seconds. */
    const val MEDAL_BURST_SECONDS = 0.95f
    /** How long the full-stage flash behind an award lives, in seconds. */
    const val MEDAL_WASH_SECONDS = 2f
    /**
     * How long the stage stays washed out to a single flash. A span with clustered awards reveals
     * several medals within a second of each other, and one full-stage tint per medal turns the
     * end of a journey into a strobe; the awards that lose the flash still pop and still throw
     * their confetti, which is what says "this one is an award" at the icon itself.
     *
     * One pool shared across every player on the chart, main or rival - not one cooldown per player.
     * Two players mastering games close together is exactly the clustered-award case this constant
     * exists to quiet, and judging it by the calendar rather than by whose curve it landed on is
     * what keeps a two-player finale from doubling the strobe a one-player one would have thrown.
     * The cost is real: a rival's award can spend the quiet time the main player's next one was
     * "owed", so on a busy compare chart it is not always the main player's own flash that survives
     * a cluster. That is judged the better trade than the alternative - a cooldown per player would
     * mean two players in that same busy stretch flash independently, doubling the strobing this
     * constant was sized against, for a chart whose whole premise is putting two players' pace
     * side by side and inviting exactly this kind of near-simultaneous finish. See
     * [JourneyEffectClock.medalWashSeconds] and `ExportEffects.plan`, whose bookkeeping is one map
     * keyed by medal index rather than by owner, for the same reason.
     *
     * `MASTERED` is the one carve-out from "whichever award landed first". A mastery is the peak
     * moment the whole feature exists to celebrate, rarer than the other three kinds put together,
     * and losing it because a rival's beaten-casual award happened to fire a second earlier is the
     * single worst trade this cooldown can make - it is what a user reported not seeing at all. A
     * mastery is therefore judged against its own cooldown clock rather than the shared one: no
     * lesser award, from any player, can consume the mastery's turn.
     *
     * It still respects a cooldown against OTHER masteries, though - "always" would go too far.
     * Three games mastered in one sitting is not a hypothetical: the export test suite's own fixture
     * shows two real events an hour apart in calendar time land within one video *second* of each
     * other once a span's reveal is compressed to its playback window (see `ExportEffectsTest`'s
     * "not actually clustered" checks), so a session-length string of masteries is comfortably
     * reachable within a strobe's distance of one another. Unconditionally
     * flashing every mastery would reintroduce the exact strobe this constant exists to prevent, just
     * restricted to the one kind that flashes hardest ([drawMedalWash]'s peak alpha, 0.55). So a
     * mastery beats a lesser award's cooldown, but not another mastery's - the shared pool still
     * governs mastery-versus-mastery, on its own separate clock.
     */
    const val WASH_COOLDOWN_SECONDS = 6f
    /**
     * Quiet time between two "+N" pops, shared across the whole stage - the same noise guard as
     * [WASH_COOLDOWN_SECONDS], sized for a much smaller effect.
     *
     * The flash earns six seconds of silence because it is a full-stage tint and three of them in
     * a second reads as a strobe; a "+N" is two characters beside one badge, cheap to read and part
     * of what makes a chart that is actually moving feel alive. Throttling it as hard as the flash
     * would leave a sparse journey - badges more than a second or two apart - showing almost none
     * of its points, which is worse than the clutter this exists to fix. What a "+N" does need is
     * room to be read rather than room to be forgotten: at [FLOATING_POINTS_SECONDS] a number is
     * still rising and fading when the next badge could throw its own, and two of them stacked over
     * the same patch of stage are no more legible than the dozen-in-two-seconds flood the ticket
     * complained about. A second is comfortably under that 1.2s rise - a fresh pop can start as the
     * last one is finishing rather than waiting through a beat of nothing - and comfortably above
     * the sub-second gaps a dense span actually throws its badges in, so the span that used to
     * throw a dozen numbers in two seconds throws two, not zero.
     *
     * Shares the wash's shape now, not just its noise-guard idea: every badge already throws its own
     * "+N" regardless of whose it is (drawFloatingPoints has no owner check), and every award now
     * throws its own flash the same way, so both pools are one shared cooldown across the whole
     * stage rather than one per player. Splitting either per player would let two simultaneous
     * binges on a compare chart each keep their own dozen numbers - or their own flash - live at
     * once, exactly the density these constants exist to cap, for a collision that is rare enough
     * (two players unlocking within the same second) that the simpler, stage-wide rule is the one
     * worth having for both.
     */
    const val FLOATING_POINTS_COOLDOWN_SECONDS = 1f
    /** How long a badge takes to grow to full size as it lands, in seconds. */
    const val POP_IN_SECONDS = 0.45f
    private const val FLOATING_POINTS_RISE = 26f
    private const val BURST_PIECES = 20
    private const val BURST_PIECE = 4f
    private const val BURST_MIN = 28f
    private const val BURST_MAX = 76f
    private const val WASH_FLARE = 0.18f
    /**
     * Above this many drawn icons a chart counts as dense, and its award bursts thin out to
     * [BURST_PIECES_DENSE]. Sized off what the stage actually carries rather than off the
     * journey's length: a hundred badges on one screenful is the crowding this is about.
     */
    private const val DENSE_ICON_COUNT = 150
    private const val BURST_PIECES_DENSE = 8
    private const val TWO_PI = 6.2831855f

    /**
     * How much of a font's size the ink of a capital letter or a symbol takes up above the
     * baseline, near enough for centring a single glyph. See [glyphTopLeft].
     */
    private const val CAP_HEIGHT = 0.7f

    /**
     * Corner radius of a badge or medallion, as a fraction of its full width.
     *
     * Zero: badges and medallions are sharp squares. Badge and game art is native square art, and
     * the rounded plate cropped a slice off each corner of it - the owner's word for the result was
     * "curved square", which is neither the art's shape nor a deliberate one. One constant because
     * every square draw goes through [fillIconSquare], [strokeIconSquare] or [drawSquareImage]: the
     * plate, the clip, the hardcore ring and its hairline, the award ring, the cursor ring, the
     * badge fallback disc and the legend's own swatches all take their corner from here, so they
     * cannot disagree about what shape an icon is.
     *
     * Profile pictures are deliberately not among them. A face is a circle everywhere in this app
     * and on RetroAchievements itself, so the runner and the rival end markers draw through
     * [drawCircularImage] and are unaffected by this constant.
     *
     * No antialiasing allowance is needed at zero: the sides are axis-aligned, so a square corner
     * lands on whole pixels rather than on a stair.
     */
    private const val ICON_CORNER_FRACTION = 0f

    /**
     * Every player's face, and the halo behind it: the main runner and the rivals ride and land at
     * the same size. The rival face used to be smaller (10 riding, 13 landed), which made the other
     * player read as a lesser participant in the race the chart exists to show.
     *
     * A ceiling, not the size: see [avatarRadius]. The halo keeps the same proportion around
     * whatever radius the chart lands on.
     */
    private const val RUNNER_AVATAR_R = 17f
    private const val AVATAR_HALO_RATIO = 1.3f

    // ---- the idle sign: what a player is doing while their line is flat (see [idleSignAt]) ----
    /** How wide a stretch of empty line has to be before it is worth remarking on, in virtual x. */
    private const val IDLE_GAP_X = 46f
    /** How much of each end of a gap the sign stays out of, as a fraction of the gap. */
    private const val IDLE_GAP_EDGE = 0.18f

    /**
     * How far the sweep travels before a long wait changes its sign, in virtual x.
     *
     * Playback crosses one screenful (1060 units) in fifteen seconds at 1x whatever width the
     * journey earned, so this is around a second and a half of watching - long enough to read the
     * first sign, short enough that a wide gap tells a small story instead of holding one frame.
     * "Around", because a long calendar span plays back slower than a short one (see the span
     * slowdown in the playback controller), which stretches this to roughly three seconds at the
     * far end of the range. It reads fine either way; the interval is a legibility floor, not a
     * beat anything else is timed against.
     */
    private const val IDLE_SIGN_SWITCH_X = 110f
    private const val IDLE_SIGN_SIZE = 22f
    private const val IDLE_SIGN_LIFT = 12f
    /** How much clear air a sign wants around it before a badge counts as in the way. */
    private const val IDLE_SIGN_CLEARANCE = 4f

    /**
     * How much of the chart's fitting radius a face may take. Under 1 on purpose: the face rides
     * among the icons, and it must always read as smaller than a mastery medallion (which is drawn
     * at the full fitting radius) - a runner bigger than the award it is passing was the eye's
     * first read on a dense chart, where the grid had sized every icon down and the fixed-size
     * face had not followed.
     */
    private const val AVATAR_FIT_FRACTION = 0.85f

    /** The face's radius on this chart: scaled with the icons, never above [RUNNER_AVATAR_R]. */
    private fun avatarRadius(layout: ChartLayout): Float =
        min(RUNNER_AVATAR_R, layout.fitRadius * AVATAR_FIT_FRACTION)


    /**
     * How much of a badge's or a medallion's own art survives on a chart with rivals on it.
     *
     * On a compare chart the question is who did what, and that is said by the curves, the faces and
     * the owner-coloured rings; the art inside an icon is the loudest thing on the stage and it says
     * nothing about whose icon it is. Knocked back so the identity marks read over it. Only the art:
     * the plate, the rings and the kind dots stay at full strength.
     */
    private const val COMPARE_ART_ALPHA = 0.75f

    /**
     * How much of the owner's colour is washed over an icon's art on a compare chart; see
     * [ownerTint].
     *
     * 0.3: enough that three players' badges sort themselves into three colour families at a
     * glance, and light enough that the game's own art is still the picture underneath. Above about
     * 0.4 every badge becomes a coloured square and the chart loses the art it took two hundred
     * downloads to draw.
     */
    private const val OWNER_TINT_ALPHA = 0.3f

    /** One unlock on a rival's line. */
    private const val RIVAL_DOT_R = 3f

    // Hoisted: PathEffect is immutable, and rebuilding these per frame allocated for nothing.
    // 6-on/4-off, up from 3-on/6-off: with only a third of each vertical inked, the x
    // gridlines vanished under the icon field however bright their colour was (owner
    // feedback, after an alpha bump alone did not read).
    private val gridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))

    /**
     * "This icon has no reveal effect", shared so callers do not allocate a lambda per frame.
     *
     * Internal rather than private because the identity of this exact instance is load-bearing:
     * [drawFloatingPoints] tests against it to skip the whole pass, so a caller that turns effects
     * off for a frame ([com.twinlore.app.journey.export.ExportFrame], for the still image) has to
     * be able to hand back this one object rather than an equivalent `{ -1f }` of its own.
     */
    internal val NO_EFFECT: (Int) -> Float = { -1f }

    /**
     * Badge pop-in, as a fraction of full size, from a reveal clock's seconds.
     *
     * A negative value - a clock with effects off, an icon that has not been revealed - is a
     * settled icon. A paused chart, a scrubbed one and a still image all want exactly that: never
     * a badge frozen half grown, and never a frame drawn only to finish an animation.
     */
    fun popProgress(seconds: Float): Float =
        if (seconds < 0f) 1f else (seconds / POP_IN_SECONDS).coerceIn(0f, 1f)

    /**
     * Compare mode: at least one rival actually has a curve, so the sweep runs on the x axis.
     *
     * Remembered for the layout it was last asked about. A frame asks four to six times over
     * (`revealedLength`, `sweepX`, the draw gate, the chart's own camera), each time an O(rivals)
     * scan with a list read per rival, for a fact that is fixed for the life of a layout.
     */
    fun isCompare(layout: ChartLayout): Boolean {
        compareA?.let { if (it.layout === layout) return it.answer }
        compareB?.let { if (it.layout === layout) return it.answer }
        val entry = CompareAnswer(layout, layout.rivals.any { it.bgTrack.isNotEmpty() })
        compareB = compareA
        compareA = entry
        return entry.answer
    }

    /**
     * One immutable object, not a pair of fields.
     *
     * Two separate volatiles are two separate writes, and a thread that interleaved between them
     * would read one layout's reference with the other's answer - which is not a slow frame, it is
     * the wrong reveal model (an arc-length sweep judged as a compare sweep, or the reverse). A
     * single reference write is atomic, so a reader sees a whole entry or the previous one.
     */
    private class CompareAnswer(val layout: ChartLayout, val answer: Boolean)

    // Two slots, because two layouts are live at once whenever an export runs while the player is
    // still on screen: the exporter draws the compact chart on a background thread while the chart
    // draws the wide one on the main thread. A single slot would miss on every call from both.
    @Volatile private var compareA: CompareAnswer? = null
    @Volatile private var compareB: CompareAnswer? = null

    /** Arc length of the main curve that playback has revealed at [progress]. */
    fun revealedLength(layout: ChartLayout, paths: ChartPaths, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        if (!isCompare(layout)) return paths.mainLength * p
        val x = layout.padL + p * innerWidth(layout)
        return if (x >= layout.main.maxX) paths.mainLength else paths.lengthAtX(ChartPaths.MAIN, x)
    }

    /**
     * The x the reveal has reached. In compare mode this is the shared sweep line across the plot
     * area (so a rival who kept unlocking after the main player still finishes), in solo mode it
     * is simply the runner's position on the curve.
     */
    fun sweepX(layout: ChartLayout, paths: ChartPaths, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        if (isCompare(layout)) return layout.padL + p * innerWidth(layout)
        return paths.positionAt(ChartPaths.MAIN, revealedLength(layout, paths, p)).x
    }

    /** Arc length of rival [index]'s curve revealed by a sweep sitting at [sweepX]. */
    fun rivalRevealedLength(
        layout: ChartLayout,
        paths: ChartPaths,
        index: Int,
        sweepX: Float,
    ): Float {
        val rival = layout.rivals.getOrNull(index) ?: return 0f
        val full = paths.lengthOf(index)
        return if (sweepX >= rival.maxX) full else paths.lengthAtX(index, sweepX)
    }

    /**
     * The x a *drawn* icon has to be behind before it may appear.
     *
     * Two gates, not one. An icon's anchor says when it was won, which is what playback follows;
     * its drawn x is where the collision pass ended up putting the disc, which can be most of a
     * hundred units to the right of the anchor in a cluster. Judged only by the anchor, that disc
     * pops in over blank stage well before the line arrives at it, which is what the chart was
     * reported for. Past the end of playback the gate opens completely: an icon the relaxation
     * pass pushed beyond the end of the curve must not be left permanently hidden.
     */
    private fun drawGateX(layout: ChartLayout, paths: ChartPaths, progress: Float): Float =
        gateFor(progress, sweepX(layout, paths, progress))

    /**
     * The same gate, for a caller that has already worked out where the sweep is.
     *
     * One expression, three callers. Below progress 1 the gate simply *is* the sweep, and measuring
     * it a second time costs two dozen path bisections on a compare chart - so everything that has
     * the sweep in hand derives the gate from it, and only [drawGateX] (which does not) measures.
     * They must not be allowed to drift: the two numbers being different by so much as an epsilon
     * is an icon that pops in over stage the line has not reached.
     */
    private fun gateFor(progress: Float, sweep: Float): Float =
        if (progress >= 1f) Float.MAX_VALUE else sweep

    /** Badge node indices the reveal has reached *and* the sweep has drawn past, left to right. */
    fun visibleNodeIndices(layout: ChartLayout, paths: ChartPaths, progress: Float): List<Int> {
        if (layout.nodes.isEmpty()) return emptyList()
        val revealed = revealedLength(layout, paths, progress)
        val sweep = sweepX(layout, paths, progress)
        // Derived rather than measured again - see [gateFor]. Same number, one third of the "where
        // has the sweep got to" work a frame used to do.
        val gate = gateFor(progress, sweep)
        return layout.nodes.indices
            .filter { nodeShown(layout.nodes[it], paths.nodeLengths[it], revealed, sweep, gate) }
            .sortedBy { layout.nodes[it].x }
    }

    /**
     * A badge is shown once the moment it was won has been reached and the sweep has passed where
     * the collision pass drew it.
     *
     * Which of those two clocks measures the moment depends on whose badge it is. The main player's
     * rides the arc length of their own curve, exactly as it always has, so a vertical climb of
     * several unlocks at one instant lands them together. A rival's has no length on that curve at
     * all - it belongs to a different line - so it is gated on the shared sweep by its anchor,
     * which is what the medallions have always done for the same reason.
     */
    private fun nodeShown(
        node: NodeLayout,
        nodeLength: Float,
        revealed: Float,
        sweepX: Float,
        gateX: Float,
    ): Boolean {
        val reached = if (node.ownerName == null) {
            nodeLength <= revealed + PLAYBACK_EPSILON
        } else {
            node.anchorX <= sweepX + PLAYBACK_EPSILON
        }
        return reached && node.x <= gateX + PLAYBACK_EPSILON
    }

    /** Visible node whose centre is nearest to ([x], [y]) within [maxDistance] virtual units. */
    fun nodeAt(
        layout: ChartLayout,
        paths: ChartPaths,
        progress: Float,
        x: Float,
        y: Float,
        maxDistance: Float,
    ): Int? = visibleNodeIndices(layout, paths, progress)
        .map { it to hypot(layout.nodes[it].x - x, layout.nodes[it].y - y) }
        .filter { it.second <= max(maxDistance, layout.nodes[it.first].r) }
        .minByOrNull { it.second }
        ?.first

    /**
     * Medal indices the reveal has reached, ordered by where they belong on the curve. Both the
     * gate and the order use the anchor rather than the relaxed centre: a fanned-out cluster draws
     * far from the moment it was won, and playback has to follow the moment.
     */
    fun visibleMedalIndices(layout: ChartLayout, paths: ChartPaths, progress: Float): List<Int> {
        if (layout.medals.isEmpty()) return emptyList()
        val sweep = sweepX(layout, paths, progress)
        return layout.medals.indices
            .filter { layout.medals[it].anchorX <= sweep + PLAYBACK_EPSILON }
            .sortedBy { layout.medals[it].anchorX }
    }

    /**
     * Medal indices whose disc is actually painted at [progress]: revealed by the anchor, and
     * behind the sweep where the collision pass drew them. The award's *effects* stay on
     * [visibleMedalIndices] so the confetti and the flash fire at the moment the award was won;
     * only the disc and its caption wait for the line to reach where they sit.
     */
    fun drawnMedalIndices(layout: ChartLayout, paths: ChartPaths, progress: Float): List<Int> {
        if (layout.medals.isEmpty()) return emptyList()
        val gate = drawGateX(layout, paths, progress)
        return visibleMedalIndices(layout, paths, progress)
            .filter { layout.medals[it].x <= gate + PLAYBACK_EPSILON }
    }

    /**
     * Every badge and every award whose reveal *effects* are live at [progress], reported in the
     * order the effect passes themselves walk them.
     *
     * The gates here are the draw pass's own rather than a second opinion about the same question.
     * A badge is [nodeShown] - the test [drawNodes] and [drawFloatingPoints] share - so a pop and
     * its "+N" start on exactly the frame the badge is first painted. An award is behind the sweep
     * by its ANCHOR, which is what [drawMedalBursts] and [drawMedalWash] gate on, and deliberately
     * not [drawnMedalIndices]'s stricter pair: an award's effects fire at the moment it was won,
     * and in a fanned-out cluster the disc is not painted at that moment.
     *
     * Exists for the video export, which has no clock to discover reveals with and so works out
     * the frame each icon appears on before it draws anything (`ExportEffects`). Callbacks in
     * layout order rather than the sorted lists [visibleNodeIndices] and [visibleMedalIndices]
     * hand back, for two reasons: a caller walking four hundred and fifty frames should not pay
     * for four hundred and fifty sorts, and the order awards arrive in is what decides which one
     * of a cluster wins the throttled flash - so it has to be the order the wash pass iterates in.
     */
    internal fun forEachEffectReveal(
        layout: ChartLayout,
        paths: ChartPaths,
        progress: Float,
        onNode: (Int) -> Unit,
        onMedal: (Int) -> Unit,
    ) {
        val revealed = revealedLength(layout, paths, progress)
        val sweep = sweepX(layout, paths, progress)
        // Derived rather than measured again - see [gateFor].
        val gate = gateFor(progress, sweep)
        layout.nodes.forEachIndexed { i, node ->
            if (nodeShown(node, paths.nodeLengths[i], revealed, sweep, gate)) onNode(i)
        }
        layout.medals.forEachIndexed { i, medal ->
            if (medal.anchorX <= sweep + PLAYBACK_EPSILON) onMedal(i)
        }
    }

    /**
     * Painted medal whose centre is nearest to ([x], [y]) within [maxDistance] virtual units.
     *
     * Hit testing rides [drawnMedalIndices], not [visibleMedalIndices]: a tap lands on a disc, so
     * a medallion the sweep has not reached yet is not there to be tapped. Badge nodes work the
     * same way through [nodeAt].
     */
    fun medalAt(
        layout: ChartLayout,
        paths: ChartPaths,
        progress: Float,
        x: Float,
        y: Float,
        maxDistance: Float,
    ): Int? = drawnMedalIndices(layout, paths, progress)
        .map { it to hypot(layout.medals[it].x - x, layout.medals[it].y - y) }
        .filter { it.second <= max(maxDistance, layout.medals[it.first].radius) }
        .minByOrNull { it.second }
        ?.first

    fun DrawScope.drawJourney(
        layout: ChartLayout,
        paths: ChartPaths,
        frame: JourneyFrame,
        style: ChartStyle,
        textMeasurer: TextMeasurer,
        bitmaps: Map<String, ImageBitmap>,
        userName: String,
        progress: Float,
        cursorNodeIndex: Int?,
        nodeAppearProgress: (Int) -> Float,
        focused: Boolean,
        scale: Float,
        cursorMedalIndex: Int? = null,
        /**
         * The chart's own in-canvas furniture: the caption in the top left ("Points over time") and
         * the running date in the top right. On by default, and the video export and the summary's
         * preview keep both - they are the chart on its own, and nothing else says what it is.
         *
         * The full-screen player turns it off. Its overview strip sits in exactly that band and
         * already carries the date, so the two collided into unreadable overlapping text.
         */
        showChartChrome: Boolean = true,
        // Seconds since a node/medal was first revealed, or a negative value when effects are off
        // (paused, reduced motion, an exported still image). Every effect below is a pure function
        // of these, so a still frame costs exactly what it did before they existed - and a video
        // export, which has no clock, can drive them from arithmetic over its own frame index
        // instead (`ExportEffects`).
        nodeEffectSeconds: (Int) -> Float = NO_EFFECT,
        medalEffectSeconds: (Int) -> Float = NO_EFFECT,
        // Seconds since a medal's award flash fired, which is not the same question as
        // [medalEffectSeconds]: a flash suppressed by the cooldown never starts one. See
        // [JourneyEffectClock.medalWashSeconds].
        medalWashSeconds: (Int) -> Float = NO_EFFECT,
        // The current pan/zoom, purely to bound draw cost: at identity every icon the layout
        // placed is already on screen, so there is nothing to cull and no rect to compute. Zoomed
        // in, only badges and medals actually inside the viewport get drawn - the reveal, the hit
        // testing and the HUD all keep reasoning about the full layout regardless (see
        // [ViewportCull]).
        view: ChartZoom = ChartZoom.NONE,
        /**
         * The size in pixels of the canvas [view] is being drawn into, which is what says how much
         * stage the letterboxing leaves on screen around the fitted chart. [Size.Zero] (the export
         * and the render tests) means "a canvas that fits the stage exactly". Getting this wrong
         * does not move a single pixel of the drawing; it only makes the cull rect describe a
         * different region than the one the user is looking at, which is how the culling came to
         * hide icons that were plainly on screen.
         */
        canvasSize: Size = Size.Zero,
        /**
         * Whether the corner date readout may draw at all. The player passes true; a fitted export
         * frame passes false, because its strip already carries a date chip and the two run on
         * different clocks (tick-quantized here, continuous there). A *followed* export frame
         * passes true and accepts that, exactly as the followed on-screen view does: a zoomed clip
         * has no axis on screen for most of its run, so the readout is the only thing in the
         * picture that says which week this is.
         */
        showZoomDate: Boolean = true,
        /**
         * The settled-icon layer, when the chart is in a view whose transform holds still (the
         * overview). Null - the export, the tests, any zoomed or following view - draws every
         * icon live, bounded by the viewport cull. See [IconLayer].
         */
        iconLayer: IconLayer? = null,
        /**
         * Whether a player's face carries a small sign while their line is flat; see [idleSignAt].
         * Decoration, and off by request in the player's settings.
         */
        showIdleSigns: Boolean = true,
    ) {
        val revealed = revealedLength(layout, paths, progress)
        val sweep = sweepX(layout, paths, progress)
        // Derived rather than measured again - see [gateFor]. Same number, one third of the "where
        // has the sweep got to" work a frame used to do.
        val gate = gateFor(progress, sweep)
        val hair = max(1f / max(scale, 0.01f), 0.6f)
        // What the viewer can actually see of the chart, in virtual units. The viewport mapped
        // through the view is the unstretched stage the letterbox fits, not the (possibly wider)
        // layout: a wide chart's extra width is content overflowing that box, which [view] already
        // describes through its offset and zoom.
        val visible = visibleBounds(layout, view, canvasSize)
        // Culling is skipped entirely at the overview, where by definition nothing is off screen.
        val cullRect = if (view.isOverview) null else visible

        // Z-order, bottom to top:
        //   grid -> award wash -> badge nodes -> medal discs -> curves (glow under the main line)
        //   -> rival dots and end avatars -> runner -> confetti and floating points -> tooltip
        //   -> date overlay and focus ring.
        //
        // The curves deliberately draw ABOVE the icons. The line is the story the chart tells, and
        // a badge or a medallion parked on top of it reads as a break in it; drawn under the icons
        // it simply passes behind them. The runner and the rivals' end avatars are who the chart is
        // about, so they sit above everything.
        //
        // The award wash is the exception among the effects: the confetti and the "+N" are above
        // the curves, but the wash paints second, straight after the grid and below all of the
        // data. At its peak alpha (0.55 for a mastered game) a full-stage tint over the top would
        // hide the chart it is celebrating.
        // Read off the frame rather than off the layout: "is this a chart with other players on
        // it" is a fact about what was loaded, and a rival whose fetch failed has a roster entry
        // and no curve.
        val rivalsPresent = frame.rivals.any { it.unlocks.isNotEmpty() }
        drawGrid(layout, frame, style, textMeasurer, hair, showChartChrome)
        // The flash fills everything on screen, letterbox included - the raw viewport rect, not
        // [visible], which is intersected with the chart's own bounds. Clamped, a celebration on a
        // portrait canvas stopped dead at the plot's edge, which read as the flash being cropped
        // to the graph.
        drawMedalWash(
            style, sweep, medalWashSeconds, layout,
            ViewportCull.visibleRect(view, LayoutEngine.W, LayoutEngine.H, canvasSize.width, canvasSize.height),
        )
        if (iconLayer != null) {
            // Retire newly settled icons into the layer, then blit it where the icons would have
            // been drawn - after the wash, under the curves. The live passes below skip whatever
            // the layer holds and draw only icons still mid-pop, plus the cursor rings.
            iconLayer.update(Density(density, fontScale), layoutDirection) {
                val glows = medalGlowsFor(layout, frame, style)
                layout.nodes.forEachIndexed { i, node ->
                    if (iconLayer.nodeDrawn[i]) return@forEachIndexed
                    if (!nodeShown(node, paths.nodeLengths[i], revealed, sweep, gate)) return@forEachIndexed
                    // The gate-then-clock order matters exactly as it does in the live pass; see
                    // drawFloatingPoints. An unsettled icon keeps drawing live until its pop ends.
                    if (nodeAppearProgress(i) < 1f) return@forEachIndexed
                    // Nothing is baked before its art has decoded. The layer is permanent for the
                    // life of the journey, so an icon composited while its image was still in
                    // flight would keep the plate it was drawn with for good - which is what put
                    // black squares where award art belongs. Missing art simply keeps drawing
                    // live, and joins the layer on the frame its image lands.
                    if (!nodeArtReady(frame, style, bitmaps, paths, i, node)) return@forEachIndexed
                    drawOneNode(
                        layout, paths, frame, style, textMeasurer, bitmaps, i, node,
                        appear = 1f, rivalsPresent = rivalsPresent,
                    )
                    iconLayer.nodeDrawn[i] = true
                }
                layout.medals.forEachIndexed { index, medal ->
                    if (iconLayer.medalDrawn[index]) return@forEachIndexed
                    if (medal.anchorX > sweep + PLAYBACK_EPSILON) return@forEachIndexed
                    if (medal.x > gate + PLAYBACK_EPSILON) return@forEachIndexed
                    // See the badges above: an award baked before its icon decoded is a black
                    // square that never repaints.
                    if (gameIconUrl(frame, medal.medal.gameId)?.let { bitmaps[it] } == null) {
                        return@forEachIndexed
                    }
                    drawOneMedal(
                        layout, paths.clipScratch, frame, style, bitmaps, index, medal,
                        rivalsPresent, glows,
                    )
                    iconLayer.medalDrawn[index] = true
                }
            }
            iconLayer.bitmap?.let { layerBitmap ->
                // The scope is transformed into virtual space; the layer is canvas pixels with
                // that same transform baked in, so it blits back under the inverse.
                withTransform({
                    scale(1f / iconLayer.k, 1f / iconLayer.k, pivot = Offset.Zero)
                    translate(-iconLayer.tx, -iconLayer.ty)
                }) { drawImage(layerBitmap) }
            }
        }
        drawNodes(
            layout, paths, frame, style, textMeasurer, bitmaps, revealed, sweep, gate,
            cursorNodeIndex, nodeAppearProgress, cullRect, rivalsPresent, iconLayer,
        )
        drawMedals(
            layout, paths.clipScratch, frame, style, bitmaps, sweep, gate,
            cursorMedalIndex, cullRect, rivalsPresent, iconLayer,
        )
        drawCurves(layout, paths, style, revealed, sweep)
        drawRivalMarkers(layout, paths, style, textMeasurer, bitmaps, sweep, cullRect, showIdleSigns)
        drawRunner(layout, paths, style, textMeasurer, bitmaps, userName, revealed, showIdleSigns)
        drawMedalBursts(layout, style, sweep, medalEffectSeconds)
        drawFloatingPoints(layout, paths, style, textMeasurer, revealed, sweep, gate, nodeEffectSeconds)

        // The running date, on the stage's own corner: only zoomed in, and only while the journey
        // RUNS - at the overview the axis says it better, in every mode. (A compare chart used to
        // keep this readout at the overview too, because its bar gave the date up for the
        // per-player counters; it was taken out because, fully zoomed out, the readout read as
        // a leftover.) A landed journey's date is the whole window, and the chrome modes (export,
        // the preview) have their own overlays. Quantized to the half-gridline the sweep has
        // passed, so this readout and the axis can never disagree.
        if (showZoomDate && !showChartChrome && !view.isOverview && progress < 0.999f) {
            zoomDateLabel(layout, sweep)?.let { drawZoomDate(visible, layout, style, textMeasurer, it, view.zoom) }
        }

        val cursorNode = cursorNodeIndex
            ?.takeIf {
                it in layout.nodes.indices &&
                    nodeShown(layout.nodes[it], paths.nodeLengths[it], revealed, sweep, gate)
            }
        // The tooltip points at a disc, so it waits for the disc: both gates, exactly like a badge.
        val cursorMedal = cursorMedalIndex
            ?.takeIf {
                it in layout.medals.indices &&
                    layout.medals[it].anchorX <= sweep + PLAYBACK_EPSILON &&
                    layout.medals[it].x <= gate + PLAYBACK_EPSILON
            }
        if (cursorNode != null) {
            drawTooltip(visible, style, textMeasurer, bitmaps, paths.clipScratch, tipFor(layout, frame, style, cursorNode, -1), view.zoom)
        } else if (cursorMedal != null) {
            drawTooltip(visible, style, textMeasurer, bitmaps, paths.clipScratch, tipFor(layout, frame, style, -1, cursorMedal), view.zoom)
        }
        // The running date is part of the chart's chrome: wherever the caption is drawn, so is it.
        // It used to be a switch of its own, which only ever let someone turn off the one thing
        // that says where in the journey a still frame is.
        if (showChartChrome) {
            drawDateOverlay(layout, style, textMeasurer, progress)
        }
        if (focused && showChartChrome) {
            // Around what is on screen, not around the layout: on a widened chart the layout's own
            // edges are most of a timeline away, and a focus ring nobody can see is not a focus
            // ring. On an unstretched chart at the overview the two are the same rectangle.
            // Chrome-gated because the full-screen player force-focuses the chart at open: a
            // permanent accent boundary around a full-screen picture said nothing (there is no
            // second focusable it distinguishes from) and framed the stage like a widget.
            drawRoundRect(
                color = style.accent,
                topLeft = Offset(visible.left + 3f, visible.top + 3f),
                size = Size(
                    max(0f, visible.right - visible.left - 6f),
                    max(0f, visible.bottom - visible.top - 6f),
                ),
                cornerRadius = CornerRadius(14f, 14f),
                style = Stroke(2.5f),
            )
        }
    }

    // ---- grid, axes, caption ----

    /**
     * The grid's text, which none of it depends on progress: five axis values off the ceiling, the
     * two endpoint dates, and the caption.
     *
     * All of it was rebuilt every frame - and the endpoint dates are a `DateTimeFormatter` over a
     * freshly built `Instant`/`ZonedDateTime`, twice, for two strings that are fixed for the whole
     * life of a chart and for the whole of an export.
     */
    private class GridText(val layout: ChartLayout, val frame: JourneyFrame) {
        /**
         * The y gridline values: 0 and round multiples of a 1/2/5-shaped step, instead of the
         * ceiling divided into quarters. "17646, 35293, 52939" is the arithmetic showing; nobody
         * reads a points axis to five significant figures, and the exact final
         * score leads the strip's end panel now.
         */
        val axisTicks: List<Int> = niceAxisValues(
            layout.ceiling,
            // The compact chart shows three y intervals (owner request); the expanded keeps five.
            maxIntervals = if (layout.width > LayoutEngine.W + 0.5f) 5 else 3,
        )
        val startDate: String = if (layout.raceMode) "" else TickGenerator.fmtDate(layout.axisT0)
        val endDate: String = if (layout.raceMode) "" else TickGenerator.fmtDate(layout.axisT1)

        /**
         * How long the run of unlocks under this axis took, centred between the two dates that
         * bound it: "21 days, 4 hours".
         *
         * The dates at the ends of the axis say when the journey happened without ever saying how
         * long it took - which is the fact a finished journey is asked about first. It used to be a
         * clause on the single-game header's cost line, sharing a sentence with the hours the game
         * was open for: two durations in one phrase, one about the player and one about the axis,
         * and only one of them is a fact about the picture.
         *
         * The main player's own first-to-last unlock in both modes, which is the same number the
         * header used to print. Not in race mode, where the axis already *is* elapsed time and a
         * duration under it would be labelling the axis with its own width.
         */
        val spanLabel: String = when {
            layout.raceMode -> ""
            else -> JourneyStats.playerSpanMillis(frame.main.unlocks)
                ?.let(JourneyStripText::fmtSpanDaysHours)
                .orEmpty()
        }
        val caption: String = when {
            layout.raceMode -> "Points gained and time since each player's first unlock"
            frame.mode == JourneyMode.SPAN -> "Points and time for all games in the span"
            else -> "Points over time"
        }
    }

    // Two slots, for the same reason [isCompare] has two: an export renders the compact chart on a
    // background thread while the player draws the wide one, so a single slot would be missed by
    // both threads on every single call and rebuild about thirty-seven times a second.
    @Volatile private var gridTextA: GridText? = null
    @Volatile private var gridTextB: GridText? = null

    private fun gridTextFor(layout: ChartLayout, frame: JourneyFrame): GridText {
        gridTextA?.let { if (it.layout === layout && it.frame === frame) return it }
        gridTextB?.let { if (it.layout === layout && it.frame === frame) return it }
        val built = GridText(layout, frame)
        gridTextB = gridTextA
        gridTextA = built
        return built
    }

    private fun DrawScope.drawGrid(
        layout: ChartLayout,
        frame: JourneyFrame,
        style: ChartStyle,
        tm: TextMeasurer,
        hair: Float,
        showCaption: Boolean,
    ) {
        val innerH = layout.height - layout.padT - layout.padB
        val right = layout.width - layout.padR
        val axisY = layout.height - layout.padB
        val text = gridTextFor(layout, frame)
        // Axis text draws a step larger in BOTH views (owner request, round two: the expanded
        // chart's labels were still small in landscape). The compact flag below only governs
        // gridline thinning and the y-interval cap, not the type size.
        val compact = layout.width <= LayoutEngine.W + 0.5f
        val fontScale = 1.3f
        val tickSize = tickLabelSize(style) * fontScale
        // Two rows of scaled text have to share padB: ticks tight under the axis, the
        // endpoint dates and span duration anchored under them, the whole stack sized to
        // stay inside layout.height (the 1.3x scale used to push the date row off-screen).
        val dateRowY = axisY + 4f + tickSize * 1.25f + 2f

        // The ceiling hairline frames the top of the plot AND names it: the round-valued
        // gridlines rarely land on the ceiling, so the most interesting number on the chart -
        // where the curve actually tops out - used to be readable only off the header strip
        // while the line at exactly that height stayed mute (owner request). The ceiling is the
        // max player total to the point, so the label is the real number, not a rounding.
        drawLine(
            color = style.grid.copy(alpha = 0.55f),
            start = Offset(layout.padL, layout.padT),
            end = Offset(right, layout.padT),
            strokeWidth = hair,
        )
        val axisValueSize = axisValueSize(style) * fontScale
        // The journey's own final total (the end strip no longer repeats it), styled like the
        // top bar's numbers - bright ink, bold, same size as the other axis values. The inline
        // " pts" suffix did not fit portrait and the accent read as a foreign colour here
        // (owner feedback, round two); the unit lives on the rotated axis caption instead.
        drawLabel(tm, layout.ceiling.toString(), layout.padL - 8f, layout.padT - axisValueSize * 0.72f, axisValueSize, style.ink, style.labelFont, Anchor.RIGHT, FontWeight.Bold)
        // The unit sits stacked ABOVE the total in the same right-aligned column, in dim ink.
        // The rotated along-the-axis caption was tried first and clashed with the value
        // column - five-digit labels fill the whole left gutter, so there is no vertical lane
        // to spare; the headroom above the ceiling is the one place nothing else claims.
        drawLabel(tm, "pts", layout.padL - 8f, layout.padT - axisValueSize * 1.85f, axisValueSize * 0.85f, style.inkDim, style.labelFont, Anchor.RIGHT)
        text.axisTicks.forEach { value ->
            val y = axisY - innerH * value.toFloat() / layout.ceiling.toFloat()
            // Gridline alphas up from 0.4/0.3: at those they disappeared under a dense icon
            // field (owner feedback). The zero baseline keeps its stronger ink.
            drawLine(
                color = style.grid.copy(alpha = if (value == 0) 0.85f else 0.55f),
                start = Offset(layout.padL, y),
                end = Offset(right, y),
                strokeWidth = hair,
            )
            // A round label that would stack into the ceiling's own stands down - the exact
            // value beats the round one where the two collide.
            if (y - layout.padT > axisValueSize * 1.4f) {
                drawLabel(tm, value.toString(), layout.padL - 8f, y - axisValueSize * 0.72f, axisValueSize, style.inkDim, style.labelFont, Anchor.RIGHT)
            }
        }

        // The compact chart DRAWS every other gridline when the layout generated its usual
        // ~11 (owner request: six, not twelve). Deliberately a draw-time thinning rather than
        // a sparser TickGenerator target: the tick grid is also the grid the icons are
        // rationed by (see LayoutEngine), and regenerating it reshaped icon layout across the
        // chart. Stepping by two keeps the survivors on the same calendar-aligned stamps.
        val drawnTicks = if (compact && layout.ticks.size > 7) {
            val step = kotlin.math.ceil(layout.ticks.size / 6.0).toInt().coerceAtLeast(1)
            layout.ticks.filterIndexed { i, _ -> i % step == 0 }
        } else layout.ticks
        drawnTicks.forEach { tick ->
            val x = tickX(layout, tick.tMillis)
            if (x < layout.padL - 0.5f || x > right + 0.5f) return@forEach
            drawLine(
                color = style.grid.copy(alpha = 0.55f),
                start = Offset(x, layout.padT),
                end = Offset(x, axisY),
                strokeWidth = hair * 1.8f,
                pathEffect = gridDash,
            )
            if (tick.label.isNotEmpty()) {
                drawLabel(tm, tick.label, x, axisY + 4f, tickSize, style.inkDim, style.labelFont, Anchor.CENTER)
            }
        }

        if (!layout.raceMode) {
            drawLabel(tm, text.startDate, layout.padL, dateRowY, ENDPOINT_DATE * fontScale, style.inkDim, style.labelFont, Anchor.LEFT)
            drawLabel(tm, text.endDate, right, dateRowY, ENDPOINT_DATE * fontScale, style.inkDim, style.labelFont, Anchor.RIGHT)
        }
        // How long the axis covers, in the same dim ink and on the same line as the dates that bound
        // it - centred between them, which is the one place under the axis nothing else claims.
        if (text.spanLabel.isNotEmpty()) {
            drawLabel(
                tm, text.spanLabel, (layout.padL + right) / 2f, dateRowY,
                ENDPOINT_DATE * fontScale, style.inkDim, style.labelFont, Anchor.CENTER,
            )
        }

        if (!showCaption) return
        drawLabel(tm, text.caption, layout.padL, CAPTION_Y, CAPTION, style.ink, style.labelFont, Anchor.LEFT, FontWeight.Medium)
    }

    /**
     * The y-axis gridline values for a chart whose top is [ceiling]: zero plus ascending round
     * multiples of a 1-, 2- or 5-shaped step (10, 20, 50, 100, ... 500, 5000) - the SMALLEST such
     * step that keeps the count of intervals to five or fewer, which is the roundest step the
     * ceiling can be divided by without the axis reading as arithmetic. Six values at most, then,
     * counting the zero at the bottom.
     *
     * Internal so a test can pin the roundness without rendering a bitmap.
     */
    internal fun niceAxisValues(ceiling: Int, maxIntervals: Int = 5): List<Int> {
        if (ceiling <= 5) return (0..ceiling.coerceAtLeast(1)).toList()
        val raw = ceiling / (maxIntervals - 1).coerceAtLeast(1).toDouble()
        val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)))
        // The 2.5-shaped step only exists for tight caps at large magnitudes (where it is still
        // an integer): with three intervals and no 2.5, a 70k ceiling would jump from a 20k step
        // straight to 50k and draw a single faraway gridline. The default five-interval axis
        // never selects it, so the pinned default values are unchanged.
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
            .filter { it != 2.5 || magnitude >= 10.0 }
            .map { it * magnitude }
            .first { ceiling / it <= maxIntervals.toDouble() }
            .toInt().coerceAtLeast(1)
        return (0..(ceiling / step)).map { it * step }
    }

    private fun tickX(layout: ChartLayout, t: Long): Float {
        val innerW = innerWidth(layout)
        return if (layout.raceMode) {
            layout.padL + innerW * (t.toFloat() / layout.eMaxMillis.coerceAtLeast(1).toFloat())
        } else {
            val span = (layout.axisT1 - layout.axisT0).coerceAtLeast(1)
            layout.padL + innerW * ((t - layout.axisT0).toFloat() / span.toFloat())
        }
    }

    // ---- badge nodes ----

    private fun DrawScope.drawNodes(
        layout: ChartLayout,
        paths: ChartPaths,
        frame: JourneyFrame,
        style: ChartStyle,
        tm: TextMeasurer,
        bitmaps: Map<String, ImageBitmap>,
        revealed: Float,
        sweep: Float,
        gateX: Float,
        cursorNodeIndex: Int?,
        nodeAppearProgress: (Int) -> Float,
        cullRect: VirtualRect?,
        rivalsPresent: Boolean,
        /** Icons already composited into the settled layer; drawn there, skipped here. */
        settled: IconLayer? = null,
    ) {
        if (layout.nodes.isEmpty()) return
        layout.nodes.forEachIndexed { i, node ->
            if (!nodeShown(node, paths.nodeLengths[i], revealed, sweep, gateX)) return@forEachIndexed
            // Viewport culling: skip a badge (halo, leader line, plate, art, ring, label) whose
            // whole drawn footprint sits outside the current zoom. Only reached when the view is
            // not identity - see drawJourney - so a fitted chart never pays for this check at all.
            if (cullRect != null && !ViewportCull.intersects(cullRect, node.x, node.y, node.r, CULL_ALLOWANCE)) {
                return@forEachIndexed
            }
            val appear = nodeAppearProgress(i).coerceIn(0f, 1f)
            if (appear <= 0.01f) return@forEachIndexed
            if (settled == null || !settled.nodeDrawn[i]) {
                drawOneNode(layout, paths, frame, style, tm, bitmaps, i, node, appear, rivalsPresent)
            }
            // The cursor ring is drawn live even for a layered icon: which icon is cursored
            // changes without the layer changing.
            //
            // In the icon OWNER's colour rather than in the main player's accent. Accent means
            // "this is mine" everywhere else on a compare chart - the curve, the badge fill, the
            // medal ring, the floating points - so ringing a rival's badge in it said the wrong
            // thing in the one mode where whose-icon-is-it is the whole point.
            if (cursorNodeIndex == i) {
                strokeIconSquare(
                    playerColor(node.ownerName, layout, style),
                    Offset(node.x, node.y), node.r * popScale(appear) + 4f, 2.5f,
                )
            }
        }
    }

    /**
     * Whether [i]'s badge has an image to draw right now: its own art, or the game icon it falls
     * back to. A badge with neither is *not* pending - it draws a typed disc in its owner's colour
     * by design - so it is ready to bake either way; what must never be baked is an icon whose
     * image simply has not arrived yet.
     */
    private fun nodeArtReady(
        frame: JourneyFrame,
        style: ChartStyle,
        bitmaps: Map<String, ImageBitmap>,
        paths: ChartPaths,
        i: Int,
        node: NodeLayout,
    ): Boolean {
        val badgeUrl = paths.nodeBadgeUrls[i]
        if (bitmaps[badgeUrl] != null) return true
        val iconUrl = gameIconUrl(frame, node.unlock.gameId)
        if (iconUrl != null && bitmaps[iconUrl] != null) return true
        // Neither url decoded, so the fallback disc is what this badge draws - and it is safe to
        // bake, because a later decode replaces the whole map: [JourneyBitmaps.preload] and
        // [JourneyBitmaps.retryMissing] both hand back a NEW map instance whenever anything
        // arrived, and the layer is keyed on that instance (see JourneyChart), so a layer built
        // against incomplete art is discarded wholesale rather than left holding a stale plate.
        //
        // This used to also require the map to contain the url, which is unreachable: the map
        // never holds nulls, so containment already implied the non-null hit above. The effect was
        // that a badge whose art failed never entered the layer at all - and with an unreachable
        // media host, where every url fails, the layer stayed empty and cost the frame its whole
        // budget for nothing, in exactly the degraded case it was written to survive.
        return true
    }

    /**
     * One badge, in full: halo, plate, art (or fallback disc), ring and type chip.
     *
     * No leader line back to the anchor any more (user request). The line existed to tie a
     * fanned-out badge to the moment it was won, but the charts dense enough to fan icons out are
     * exactly the charts where a one-unit translucent stroke disappears behind the badges it runs
     * between - clutter that cost a draw call per icon and said nothing.
     */
    private fun DrawScope.drawOneNode(
        layout: ChartLayout,
        paths: ChartPaths,
        frame: JourneyFrame,
        style: ChartStyle,
        tm: TextMeasurer,
        bitmaps: Map<String, ImageBitmap>,
        i: Int,
        node: NodeLayout,
        appear: Float,
        rivalsPresent: Boolean,
    ) {
        val center = Offset(node.x, node.y)
        val r = node.r * popScale(appear)
        val color = playerColor(node.ownerName, layout, style)

            // What goes inside the plate, in order of preference: the achievement's own badge, the
            // game's icon, or nothing at all. A badge whose art never decoded (a 404, a request
            // that failed) used to leave the third case drawing the bare panel plate, which is the
            // empty circle the chart was reported for; it now falls back to a solid disc in the
            // node's own colour with the type glyph in it, so a missing image reads as a marker
            // rather than a hole.
            val art = bitmaps[paths.nodeBadgeUrls[i]]
                ?: gameIconUrl(frame, node.unlock.gameId)?.let { bitmaps[it] }
            if (art != null) {
                fillIconSquare(style.panel.copy(alpha = appear), center, r)
                // Knocked back on a compare chart so the curves and the faces dominate, and washed
                // in the owner's colour so the badge itself says whose it is; see
                // [COMPARE_ART_ALPHA] and [ownerTint]. The plate under it and the ring around it
                // are untouched.
                drawSquareImage(
                    clip = paths.clipScratch, image = art, centerX = node.x, centerY = node.y,
                    radius = r - 1.5f, alpha = appear * artAlpha(rivalsPresent),
                    tint = ownerTint(color, rivalsPresent),
                )
            } else {
                fillIconSquare(color.copy(alpha = appear), center, r)
                when (val mark = ChartTypeIcons.markFor(node.unlock.type)) {
                    is ChartTypeIcons.TypeMark.Icon ->
                        drawIconMark(mark.vector, center, r * DISC_MARK, style.panel, appear)
                    is ChartTypeIcons.TypeMark.Glyph -> drawGlyph(
                        tm = tm, text = mark.text, center = center,
                        sizePx = (r * 1.2f).roundToInt().toFloat(), color = style.panel,
                        font = ChartTypeIcons.GLYPH_FONT, alpha = appear,
                    )
                    // Standard has no mark of its own, but the disc standing in for missing art
                    // still needs *something* in it rather than reading as a hole. Drawn in the
                    // stamp font too: it is standing in for a stamp, at a size derived the same way
                    // theirs are, so it wants the same font pin for the same reason.
                    null -> drawGlyph(
                        tm = tm, text = "•", center = center,
                        sizePx = (r * 1.2f).roundToInt().toFloat(), color = style.panel,
                        font = ChartTypeIcons.GLYPH_FONT, alpha = appear,
                    )
                }
            }

            // The ring says one thing and one thing only: whether the achievement was earned
            // hardcore. [HARDCORE_RING] for yes, and for no a plain dark outline of the plate - not
            // "no ring at all", because a badge on top of the backdrop art needs an edge to stop it
            // bleeding into the picture. What used to be here was a ring in the badge's type colour
            // (or, on a span, its game colour), which spent the loudest thing on an icon on a fact
            // the corner chip already carries. The ring was gold until the awards took gold for
            // themselves; see [HARDCORE_RING].
            if (node.unlock.hardcore) {
                drawHardcoreRing(center, r, appear, style, ownerBoundary(HARDCORE_RING, color, rivalsPresent))
            } else {
                strokeIconSquare(style.background.copy(alpha = PLATE_EDGE_ALPHA * appear), center, r, PLATE_EDGE)
            }

            // The corner chip repeats the type mark, so it is left off a fallback disc that is
            // already showing it at full size.
            val mark = if (art == null) null else ChartTypeIcons.markFor(node.unlock.type)
            // A circle keeps its original colour in every mode: the owner dim
            // belongs to the SQUARE boundaries, and the chip is the round mark that stays true
            // to the type it names.
            val chipColor = style.typeColors[node.unlock.type]
            if (mark != null && chipColor != null) {
                val chipR = max(4.5f, r * 0.42f)
                val chipCenter = Offset(node.x + r * 0.72f, node.y - r * 0.72f)
                drawCircle(chipColor.copy(alpha = appear), chipR, chipCenter)
                drawCircle(style.panel.copy(alpha = appear), chipR, chipCenter, style = Stroke(1.4f))
                when (mark) {
                    is ChartTypeIcons.TypeMark.Icon ->
                        drawIconMark(mark.vector, chipCenter, chipR * CHIP_MARK, style.panel, appear)
                    is ChartTypeIcons.TypeMark.Glyph -> drawGlyph(
                        tm = tm, text = mark.text, center = chipCenter,
                        sizePx = (chipR * 1.5f).roundToInt().toFloat(), color = style.panel,
                        font = ChartTypeIcons.GLYPH_FONT, alpha = appear,
                    )
                }
            }
    }

    /**
     * The hardcore ring: [HARDCORE_RING] around the plate, with a hairline of the theme's own ink
     * immediately outside it.
     *
     * The hairline is what makes the ring survive a light theme. RA's hardcore yellow is a light
     * colour, and a light ring on a light background is not a ring; the edge gives it a boundary to
     * read against, and on a dark theme the same stroke reads as the shadow of a raised plate rather
     * than as a correction. Drawn just outside the yellow rather than over it, so the colour the
     * owner picked is the colour that gets drawn.
     *
     * Internal because the legend draws the same ring beside the words explaining it.
     */
    internal fun DrawScope.drawHardcoreRing(
        center: Offset,
        r: Float,
        alpha: Float,
        style: ChartStyle,
        ring: Color = HARDCORE_RING,
    ) {
        strokeIconSquare(ring.copy(alpha = alpha), center, r, HARDCORE_RING_WIDTH)
        strokeIconSquare(
            color = style.ink.copy(alpha = HARDCORE_EDGE * alpha),
            center = center,
            radius = r + HARDCORE_RING_WIDTH / 2f + 0.5f,
            width = 1f,
        )
    }

    /**
     * An icon boundary's colour on this chart: [base] untouched on a solo chart, and blended
     * toward its owner's colour on a compare chart, the same way the art is washed ([ownerTint]).
     *
     * Applied to every mark an icon wears - the badge's hardcore ring and type chip, the
     * medallion's award ring and kind dot. They all wore their identical bright solo colours,
     * which on a chart whose whole point is whose-is-whose left them saying nothing about the
     * owner and shouting over the dimmed art. The legend keeps the pure colours on record.
     */
    internal fun ownerBoundary(base: Color, owner: Color, rivalsPresent: Boolean): Color =
        if (!rivalsPresent) base else lerp(base, owner, OWNER_BOUNDARY_MIX)

    /**
     * How big an icon-shaped type mark's box is on the corner chip, as a multiple of the chip's
     * RADIUS. Only [ChartTypeIcons.TypeMark.Icon] reads this - a
     * [glyph-shaped][ChartTypeIcons.TypeMark.Glyph] mark draws through [drawGlyph] instead, at the
     * sizes those always drew at (1.5 times the chip's radius, since a glyph's box is its set size
     * rather than its ink).
     *
     * A Material icon's mark used to be a text glyph set at 1.5 times that radius, whose ink - cap
     * height, so about 0.7 of the set size - stood a little over one radius tall. A Material icon
     * fills roughly 90% of its box, so a box of 1.15 radii puts the same weight of ink in the chip:
     * the mark changed alphabet without the badge changing how loud it is. The box's corners stay
     * inside the chip's panel-coloured hairline even at the 4.5-unit floor a dense chart's chips
     * take (a half-diagonal of 0.81 radii against an inner radius of 0.85 at that size).
     */
    private const val CHIP_MARK = 1.15f

    /**
     * The same, on the disc a badge with no art at all falls back to, as a multiple of the badge's
     * radius. Icon-shaped marks only, like [CHIP_MARK]. A shade larger than the chip's share: the
     * disc has nothing else on it, and this is the one place the mark is doing the whole job of
     * saying what the achievement was.
     */
    private const val DISC_MARK = 1.2f

    /**
     * An icon-shaped mark's own path, in a unit box centred on the origin (x and y both in
     * -0.5..0.5), parsed once per [ImageVector] and kept - so a frame that stamps two hundred
     * progression badges parses nothing, and a second icon-shaped type would cost one more cache
     * entry rather than a second cache.
     *
     * This used to be a general-purpose vector-path parser (`ChartTypeIcons`) built to carry all
     * three achievement types' Material icons: nested groups, clip paths, per-path fill alpha, a
     * cache keyed by [AchievementType]. Missable and win condition went back to their text glyphs
     * ("!" and "★" - see [ChartTypeIcons.markFor]) once the user saw the Material marks at the
     * chart's chip size and asked for the two smaller-reading ones back, which leaves exactly one
     * icon of known, fixed shape drawn this way. `Icons.Default.TaskAlt` is, like every
     * `materialIcon`, a single filled path in a 24x24 viewport with no groups and no clip, so the
     * general walk collapsed into the one transform that shape actually needs: normalise the
     * 24-unit viewport onto the unit box. Keyed by the vector itself rather than hardcoded to
     * progression, so this stays correct - not silently stale - if [ChartTypeIcons.markFor] ever
     * hands a different type an icon.
     *
     * `ConcurrentHashMap` rather than the renderer's own two-slot volatile caches ([gridTextFor],
     * [isCompare]): those exist because their inputs change from frame to frame and a stale hit
     * would be wrong; an [ImageVector]'s path never changes, so a plain memoized map is the whole
     * requirement. Thread-safe because the video export draws through this on a background thread
     * while the chart is free to draw on the main one.
     */
    private val iconMarks = ConcurrentHashMap<ImageVector, Path>()

    private fun iconMarkPath(vector: ImageVector): Path = iconMarks.getOrPut(vector) {
        val normalize = Matrix().apply {
            scale(1f / vector.viewportWidth, 1f / vector.viewportHeight)
            translate(-vector.viewportWidth / 2f, -vector.viewportHeight / 2f)
        }
        val out = Path()
        vector.root.forEach { node ->
            if (node is VectorPath) {
                val path = PathParser().addPathNodes(node.pathData).toPath()
                path.fillType = node.pathFillType
                path.transform(normalize)
                out.addPath(path)
            }
        }
        out
    }

    /**
     * Stamps [vector]'s mark ([iconMarkPath]) centred on [center], sized so the icon's own 24-unit
     * box spans [box], in [color] at [alpha]. One save/restore and one filled path: the transform
     * is applied to the canvas rather than to the path, so the same immutable [Path] instance is
     * what every badge of that type on the stage draws.
     */
    private fun DrawScope.drawIconMark(vector: ImageVector, center: Offset, box: Float, color: Color, alpha: Float) {
        if (box <= 0f || alpha <= 0f) return
        withTransform({
            translate(center.x, center.y)
            scale(box, box, Offset.Zero)
        }) {
            drawPath(iconMarkPath(vector), color, alpha = alpha.coerceIn(0f, 1f))
        }
    }

    /** Badge pop-in: overshoot to 1.18 by 70% of the way in, then settle back to 1. */
    private fun popScale(p: Float): Float =
        if (p < 0.7f) 0.2f + (1.18f - 0.2f) * (p / 0.7f) else 1.18f - 0.18f * ((p - 0.7f) / 0.3f)

    // ---- curves ----

    /** Every player's revealed line, and nothing else: the markers on them draw a layer up. */
    private fun DrawScope.drawCurves(
        layout: ChartLayout,
        paths: ChartPaths,
        style: ChartStyle,
        revealed: Float,
        sweep: Float,
    ) {
        val scratch = paths.revealScratch
        // Under the curves, deliberately: the streak is a bed of fire the player's own line runs
        // OVER (UI feedback) - a rim of orange either side of the accent, not a repaint of it.
        drawStreakFire(layout, paths, style, revealed, sweep)
        // A fully revealed curve draws the prebuilt path itself rather than a scratch copy of all
        // of it. The copy was a per-frame native walk over every segment, and - because a rewritten
        // scratch path is a new path as far as the GPU's stroke cache is concerned - a per-frame
        // re-tessellation of the longest path the chart will ever stroke. The end of a journey is
        // exactly when every curve is at full length AND the end glide is redrawing every frame.
        layout.rivals.forEachIndexed { i, rival ->
            if (rival.bgTrack.isEmpty()) return@forEachIndexed
            val color = style.colorForRivalIndex(i)
            val length = rivalRevealedLength(layout, paths, i, sweep)
            if (length <= PLAYBACK_EPSILON) return@forEachIndexed
            val path = if (length >= paths.lengthOf(i) - PLAYBACK_EPSILON) {
                paths.rivalPaths[i].first
            } else {
                if (!paths.revealInto(i, length, scratch)) return@forEachIndexed
                scratch
            }
            // The same weight and the same glow as the main line: a compare chart is two players'
            // journeys side by side, and the thinner bare stroke made the rival's read as an
            // annotation on the main player's rather than a line of equal standing.
            drawPath(
                path = path,
                color = color.copy(alpha = 0.16f),
                style = Stroke(MAIN_GLOW, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = path,
                color = color,
                style = Stroke(MAIN_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        if (revealed > PLAYBACK_EPSILON) {
            val path = if (revealed >= paths.mainLength - PLAYBACK_EPSILON) {
                paths.mainPath
            } else {
                if (!paths.revealInto(ChartPaths.MAIN, revealed, scratch)) return
                scratch
            }
            drawPath(
                path = path,
                color = style.accent.copy(alpha = 0.16f),
                style = Stroke(MAIN_GLOW, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = path,
                color = style.accent,
                style = Stroke(MAIN_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }

    /**
     * Every streak's stretch of the main line as a bed of fire UNDER it: a soft glow and a band
     * of the flame's two-tone orange, drawn before the curves so the player's own line runs over
     * the top and a rim of orange shows either side of it. The revealed part of each run burns
     * and KEEPS burning once the sweep is past it, so a finished chart shows every streak.
     *
     * Deliberately static (UI feedback: the animated shimmer was more show than the device's
     * budget wanted). A still stroke costs nothing at rest - nothing invalidates for it - and the
     * fixed orange stays the streak's own colour, distinct from every player's.
     */
    private fun DrawScope.drawStreakFire(
        layout: ChartLayout,
        paths: ChartPaths,
        style: ChartStyle,
        revealed: Float,
        sweep: Float,
    ) {
        if (revealed > PLAYBACK_EPSILON) {
            drawFireRuns(
                paths, ChartPaths.MAIN, paths.streakLengths, paths.streakEnds, revealed,
            ) { paths.completeFireStretch(it) }
        }
        // A rival's line burns exactly the same way: the streak is a fact about a run of days,
        // whoever's days they were. Their reveal is the shared sweep's, like everything on their
        // curve.
        paths.rivalStreakLengths.forEachIndexed { i, bounds ->
            if (bounds.isEmpty()) return@forEachIndexed
            val revealedLen = rivalRevealedLength(layout, paths, i, sweep)
            if (revealedLen <= PLAYBACK_EPSILON) return@forEachIndexed
            drawFireRuns(
                paths, i, bounds, paths.rivalStreakEnds[i], revealedLen,
            ) { paths.completeRivalFireStretch(i, it) }
        }
    }

    /** One curve's worth of streak fire; see [drawStreakFire] for what and why. */
    private inline fun DrawScope.drawFireRuns(
        paths: ChartPaths,
        curve: Int,
        bounds: List<Pair<Float, Float>>,
        ends: List<Pair<Offset, Offset>>,
        revealed: Float,
        complete: (Int) -> Pair<Path, Brush>?,
    ) {
        bounds.forEachIndexed { index, (from, to) ->
            val end = min(revealed, to)
            if (end - from <= PLAYBACK_EPSILON) return@forEachIndexed
            // At most one run is ever partially revealed - the one the sweep is inside; every run
            // behind it draws its cached shape and gradient rather than re-measuring the curve.
            val path: Path
            val brush: Brush
            if (end >= to) {
                val stretch = complete(index) ?: return@forEachIndexed
                path = stretch.first
                brush = stretch.second
            } else {
                if (!paths.segmentInto(curve, from, end, paths.fireScratch)) return@forEachIndexed
                path = paths.fireScratch
                // Orange into gold along the burning stretch itself, so the gradient describes
                // the run rather than the stage.
                brush = Brush.linearGradient(
                    colors = listOf(FLAME_OUTER, FLAME_INNER),
                    start = ends[index].first,
                    end = paths.positionAt(curve, end),
                )
            }
            drawPath(
                path = path,
                brush = brush,
                alpha = FIRE_GLOW_ALPHA,
                style = Stroke(MAIN_GLOW, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = path,
                brush = brush,
                style = Stroke(MAIN_STROKE + FIRE_RIM * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }

    
    /**
     * A rival's unlock dots and the face riding the tip of their curve. Above the curves: a dot
     * painted under the line it belongs to disappears into it, and a face is a face, which nothing
     * on the chart should be drawn over.
     *
     * The face follows the reveal rather than waiting for it. It used to be drawn only once the
     * rival's line was *complete*, which on a span is the last moment of playback and on a compare
     * chart is the whole of the reported bug: for the entire animation the only player on the stage
     * with an identity was the main one, and everybody else was a coloured line with dots on it. It
     * now rides the tip at the runner's own size ([RUNNER_AVATAR_R]) exactly as the runner rides
     * the main curve, and stays there when the line lands.
     *
     * The avatar and nothing else. The name used to be printed beside it, which is a caption the
     * main player's own runner never had, and on a three-way compare it put two or three names into
     * the busiest corner of the stage. Who is who is said by the ring's colour, by the face inside
     * it, and in words on the roster and the compare row.
     *
     * The bitmap key is [JourneyUrls.userPic] of the name on the *curve*, which is the name on the
     * frame the images were preloaded from - so the canonical spelling the load adopted (the media
     * host's UserPic path is case sensitive) is the spelling both sides of this lookup use.
     */
    private fun DrawScope.drawRivalMarkers(
        layout: ChartLayout,
        paths: ChartPaths,
        style: ChartStyle,
        tm: TextMeasurer,
        bitmaps: Map<String, ImageBitmap>,
        sweep: Float,
        cullRect: VirtualRect?,
        showIdleSigns: Boolean,
    ) {
        layout.rivals.forEachIndexed { i, rival ->
            if (rival.bgTrack.isEmpty()) return@forEachIndexed
            val color = style.colorForRivalIndex(i)
            val length = rivalRevealedLength(layout, paths, i, sweep)
            // Ascending in x by construction, and the predicate is monotone, so the walk stops at
            // the sweep instead of testing every dot behind it - and the dots get the same viewport
            // cull the badges and medals have always had. On a compare span this list has one entry
            // per rival unlock, which without either was thousands of draw calls per vsync.
            val dots = paths.dotsOf(i)
            for (index in dots.indices) {
                val (x, position) = dots[index]
                if (x > sweep + PLAYBACK_EPSILON) break
                if (cullRect != null && !ViewportCull.intersects(cullRect, position.x, position.y, RIVAL_DOT_R)) {
                    continue
                }
                drawCircle(color, RIVAL_DOT_R, position)
            }
            // Nothing before the sweep reaches their first unlock: a face parked on a rival's
            // starting point while the stage in front of it is still blank is a player who has not
            // started yet, drawn as though they had.
            val startX = rival.points.firstOrNull()?.x ?: Float.MAX_VALUE
            if (sweep + PLAYBACK_EPSILON < startX) return@forEachIndexed
            val at = paths.positionAt(i, length)
            // The runner's own size and halo, riding and landing alike: a compare chart is a race
            // between equals, and the smaller face made the rival read as a lesser participant.
            val radius = avatarRadius(layout)
            // Their own streaks, not the main player's: the fire under a rival's face is a fact
            // about their run of days.
            if (burningAt(paths.rivalStreakLengths.getOrElse(i) { emptyList() }, length)) {
                drawBurningHalo(at, radius)
            }
            drawCircle(color.copy(alpha = 0.2f), radius * AVATAR_HALO_RATIO, at)
            if (showIdleSigns) {
                idleSignAt(layout, rival, at.x)?.let {
                    drawIdleSign(layout, tm, style, it, at, radius, sweep)
                }
            }
            drawAvatar(
                clip = paths.clipScratch,
                image = bitmaps[JourneyUrls.userPic(rival.playerName.orEmpty())],
                center = at,
                radius = radius,
                ring = color,
                style = style,
                tm = tm,
                initial = rival.playerName,
            )
        }
    }

    /**
     * The date the zoomed-in player shows in the stage's bottom-right corner: the label of the
     * last axis tick the sweep has passed, formatted the way the readouts format dates ([Tick]
     * labels themselves elide repeats). Quantizing to the grid is the point - the overlay ticks
     * over exactly when the runner passes a gridline, so it and the axis are one clock. Null on a
     * race chart (the axis is elapsed time) and before the first tick.
     */
    /**
     * How big an x-axis tick label is drawn: smaller in the pixel font, which cannot fit the
     * ordinary size between two gridlines. See [PIXEL_TICK_LABEL].
     *
     * Exposed so a test can pin the pair without a canvas, and so the one rule lives in one place -
     * the live chart and the export share this renderer, so both follow it by construction.
     */
    internal fun tickLabelSize(style: ChartStyle): Float =
        if (style.pixelFont) PIXEL_TICK_LABEL else TICK_LABEL

    /**
     * How big a y-axis point value is drawn - [tickLabelSize]'s rule for the other axis (owner
     * report, with an exported frame: the ceiling's five pixel-font digits are right-anchored
     * against the plot's left gutter, and at full size they overran the frame's own edge and were
     * clipped to their trailing digits, every round value below them the same). The compact
     * chart's 1.3x font scale multiplies AFTER this, exactly as it does for the ordinary size.
     */
    internal fun axisValueSize(style: ChartStyle): Float =
        if (style.pixelFont) PIXEL_AXIS_VALUE else AXIS_VALUE

    internal fun zoomDateLabel(layout: ChartLayout, sweepX: Float): String? {
        if (layout.raceMode) return null
        val inner = layout.width - layout.padL - layout.padR
        val span = layout.axisT1 - layout.axisT0
        if (inner <= 0f || span <= 0L) return null
        val frac = ((sweepX - layout.padL) / inner).coerceIn(0f, 1f)
        val t = layout.axisT0 + (frac * span).toLong()
        val tick = layout.ticks.lastOrNull { it.tMillis <= t } ?: return null
        // Twice the grid's resolution: the readout also ticks over halfway between two gridlines
        // ("1 Jan, 15 Jan, 1 Feb" rather than month-to-month), which on a zoomed-in wide chart is
        // the difference between the date feeling live and feeling stuck. Still quantized - to the
        // half-grid - so it and the axis can never disagree about which stretch the sweep is in.
        val next = layout.ticks.firstOrNull { it.tMillis > tick.tMillis }
        val half = next?.let { (tick.tMillis + it.tMillis) / 2 }
        val shown = if (half != null && t >= half) half else tick.tMillis
        // The readout only changes when the sweep crosses a half-gridline, but this runs per
        // frame: uncached it was a formatter pass (a fresh temporal object and a string) per
        // vsync of a zoomed playback, for the label it produced last frame.
        zoomDate?.let { if (it.layout === layout && it.tickMillis == shown) return it.label }
        val label = if (TickGenerator.showsTimeOfDay(span)) {
            TickGenerator.fmtDateTime(shown)
        } else {
            TickGenerator.fmtDate(shown)
        }
        zoomDate = ZoomDate(layout, shown, label)
        return label
    }

    /** The last zoomed-date readout, remembered per tick; see [zoomDateLabel]. */
    private class ZoomDate(val layout: ChartLayout, val tickMillis: Long, val label: String)

    @Volatile private var zoomDate: ZoomDate? = null

    /** Past this magnification the corner date stops growing; see drawZoomDate's zoom param. */
    private const val ZOOM_DATE_MAX_SCALE = 2.5f

    private fun DrawScope.drawZoomDate(
        visible: VirtualRect,
        layout: ChartLayout,
        style: ChartStyle,
        tm: TextMeasurer,
        label: String,
        /**
         * The view's magnification. The readout grows with the zoom the way it always did -
         * that is the size the owner judged right at ordinary follow depth - but only up to
         * [ZOOM_DATE_MAX_SCALE]: past that it holds, because deep in a landscape follow the
         * un-capped text was a banner across the corner (owner report; a fully constant size
         * was tried next and read too small at ordinary depth - round two).
         */
        zoom: Float,
    ) {
        val z = zoom.coerceAtLeast(1f)
        // Virtual sizes multiply by this: 1.0 up to the cap (natural growth), then shrinking
        // in proportion so the on-screen size pins at ZOOM_DATE_TEXT * ZOOM_DATE_MAX_SCALE.
        val s = minOf(z, ZOOM_DATE_MAX_SCALE) / z
        val text = measureText(
            tm, label, ZOOM_DATE_TEXT * s, style.labelFont, FontWeight.Bold,
            TIP_TEXT_WIDTH * s, 1,
        )
        val margin = ZOOM_DATE_MARGIN * s
        val left = visible.right - margin - text.size.width
        // Above the axis line, never on it: when the zoomed view includes the bottom of the
        // stage, the readout hovering over the tick labels was a collision, not a corner.
        val axisY = layout.height - layout.padB
        val bottomEdge = min(visible.bottom - margin, axisY - margin)
        val top = bottomEdge - text.size.height
        // Bare ink on the stage, no plate: the readout is a whisper in the corner, and a small
        // shadow copy underneath is what keeps it legible over bright badge art.
        drawText(
            textLayoutResult = text,
            color = style.background.copy(alpha = ZOOM_DATE_ALPHA),
            // The shadow's offset divides by the zoom like everything else here, or it drifts
            // away from the (now screen-constant) text as the view magnifies.
            topLeft = Offset(left + s, top + s),
        )
        drawText(
            textLayoutResult = text,
            color = style.ink.copy(alpha = ZOOM_DATE_ALPHA),
            topLeft = Offset(left, top),
        )
    }

    // ---- medallions ----

    private fun DrawScope.drawMedals(
        layout: ChartLayout,
        clip: Path,
        frame: JourneyFrame,
        style: ChartStyle,
        bitmaps: Map<String, ImageBitmap>,
        sweep: Float,
        gateX: Float,
        cursorMedalIndex: Int?,
        cullRect: VirtualRect?,
        rivalsPresent: Boolean,
        /** Icons already composited into the settled layer; drawn there, skipped here. */
        settled: IconLayer? = null,
    ) {
        if (layout.medals.isEmpty()) return
        val glows = medalGlowsFor(layout, frame, style)
        // No captions, in any mode. The kind used to be spelled out beside a single game's
        // medallions; what it bought was a word in the owner's colour hanging off the busiest part
        // of the chart, saying what the ring and the dot on the disc itself now say at a glance (see
        // [medalRingColor] and [drawKindDot]). The tooltip still names every award in words.
        layout.medals.forEachIndexed { index, medal ->
            // Two gates. The anchor is when the award was won, which is what playback and the
            // award's own effects follow; a cluster of medals can be fanned out by up to ~90
            // virtual units, so judging the *moment* by the drawn x would fire the confetti early
            // or late and point the leader line at curve the reveal has not drawn yet. The disc
            // itself then waits for the sweep to reach where it was drawn, so a fanned-out
            // medallion never lands on stage ahead of the line.
            if (medal.anchorX > sweep + PLAYBACK_EPSILON) return@forEachIndexed
            if (medal.x > gateX + PLAYBACK_EPSILON) return@forEachIndexed
            // Viewport culling, same rule and same reason as the badge nodes above: a medallion
            // (halo, leader, plate, art, ring) entirely outside the zoom costs nothing.
            if (cullRect != null && !ViewportCull.intersects(cullRect, medal.x, medal.y, medal.radius, CULL_ALLOWANCE)) {
                return@forEachIndexed
            }
            if (settled == null || !settled.medalDrawn[index]) {
                drawOneMedal(layout, clip, frame, style, bitmaps, index, medal, rivalsPresent, glows)
            }
            // Same rule as the badge cursor: the ring is live state, drawn over the layer, and the
            // highlight belongs to whoever won the award.
            if (cursorMedalIndex == index) {
                strokeIconSquare(
                    playerColor(medal.medal.ownerName, layout, style),
                    Offset(medal.x, medal.y), medal.radius + 5f, 2.5f,
                )
            }
        }
    }

    /** One medallion, in full: halo, plate, art, award ring and kind dot. The dashed leader back
     *  to the anchor is gone with the badges' lines (user request). */
    private fun DrawScope.drawOneMedal(
        layout: ChartLayout,
        clip: Path,
        frame: JourneyFrame,
        style: ChartStyle,
        bitmaps: Map<String, ImageBitmap>,
        index: Int,
        medal: MedalLayout,
        rivalsPresent: Boolean,
        glows: MedalGlows,
    ) {
            // Whose award it is: the halo behind the disc and the leader line back to the point on
            // their curve where it was won. The RING is now the award's own kind rather than the
            // owner's colour (see [medalRingColor]) - which award it is is what the site says with a
            // medal, and it is what the eye is asked to read across a whole span.
            val color = playerColor(medal.medal.ownerName, layout, style)
            val center = Offset(medal.x, medal.y)

            fillIconSquare(style.panel, center, medal.radius)
            gameIconUrl(frame, medal.medal.gameId)?.let { url ->
                bitmaps[url]?.let {
                    // Dimmed into its owner's colour family on a compare chart, exactly as their
                    // badges are (see [ownerTint]): a medallion's ART is an icon with an owner
                    // like any other. The metal marks over it are what stay pure.
                    drawSquareImage(
                        clip = clip, image = it, centerX = medal.x, centerY = medal.y,
                        radius = medal.radius - 2f, alpha = artAlpha(rivalsPresent),
                        tint = ownerTint(color, rivalsPresent),
                    )
                }
            }
            // The rule an icon's marks follow on a compare chart:
            // every SQUARE boundary takes the owner dim - the award's metal ring here, exactly as
            // the badges' rings do - while every CIRCLE keeps its original colour, so the kind dot
            // stays pure metal with its plain dark hairline.
            strokeIconSquare(
                ownerBoundary(medalRingColor(medal.medal.kind, style), color, rivalsPresent),
                center, medal.radius, 2.8f,
            )
            val dotColor = medalColor(medal.medal.kind, style)
            drawKindDot(
                medal.medal.kind, center, medal.radius, style,
                dotColor = dotColor,
                glow = glows.brushes[index]
                    ?: kindDotGlowBrush(dotColor, center, medal.radius).also { glows.brushes[index] = it },
            )
    }

    /** How much of an icon's own art is drawn; see [COMPARE_ART_ALPHA]. */
    private fun artAlpha(rivalsPresent: Boolean): Float =
        if (rivalsPresent) COMPARE_ART_ALPHA else 1f

    /**
     * The wash over an icon's art on a compare chart: its owner's own colour, at [OWNER_TINT_ALPHA],
     * and nothing at all on a solo one.
     *
     * Whose icon is this is the question a compare chart exists to answer, and until now the art
     * itself was the one part of a badge that answered it in the wrong voice: forty native RA badge
     * images, all of them loud, all of them the same regardless of who earned them. A corner dot was
     * tried first and taken back out - a seven-pixel disc on a badge among two hundred badges is not
     * a thing anybody reads, and it collided with the type chip's corner on the small ones.
     *
     * A tint reads at a glance because it colours the *whole* icon: combined with the knock-back in
     * [COMPARE_ART_ALPHA] a rival's badges become a family of their colour rather than forty
     * unrelated pictures. It is deliberately light, because the art still has to be recognisable as
     * that game's art; and it is applied to the art only, so the marks that carry meaning of their
     * own - the hardcore ring, the award ring, the kind dot, the type chip - keep their exact
     * colours over it.
     */
    private fun ownerTint(color: Color, rivalsPresent: Boolean): Color? =
        if (rivalsPresent) color.copy(alpha = OWNER_TINT_ALPHA) else null

    /**
     * The award kind, as a glowing dot in the medallion's top-right corner - the same corner and the
     * same proportions the badge nodes hang their type chip on, so the two read as one idea.
     *
     * Three states, graded exactly as the rest of this app grades a RetroAchievements award (see
     * [com.twinlore.app.journey.domain.AwardGrading], which is where the pairing is decided and
     * where the mapping is set out against the library's own `HighestAwardIcon`), read together
     * with the ring ([medalRingColor]) - which is now the same metal as the dot:
     *
     * - `MASTERED`: gold ring, FILLED gold dot.
     * - `COMPLETED`: gold ring, HOLLOW gold dot. Same tier as a mastery - every achievement in the
     *   game - and the hollow dot is what separates the two.
     * - `BEATEN`: silver ring, filled silver dot. The metal is what says this is the lesser award.
     *
     * This used to be the other way round below the top award: a completion got a filled *silver*
     * dot and beaten got the hollow one, so the chart contradicted the tile, the tab and the drawer
     * showing the same game elsewhere in the app.
     *
     * All three glow now. The glow used to be mastery's alone, which made it the only award with any
     * presence on a stage of forty games; what tells them apart is the metal and whether the dot has
     * a middle, not whether it is lit.
     *
     * Internal so the legend draws the dot it explains rather than a second, hand-made version.
     */
    internal fun DrawScope.drawKindDot(
        kind: AwardKind,
        center: Offset,
        radius: Float,
        style: ChartStyle,
        /** The dot's colour; the legend and solo charts keep the pure metal default. */
        dotColor: Color = medalColor(kind, style),
        /**
         * The glow's brush, when the caller has one cached. A gradient brush creates its native
         * shader on first draw and keeps it only for as long as the brush lives, so building one
         * here per call meant one shader creation per medal per frame - which on a span of
         * hundreds of games was the single hottest allocation in the draw pass. [drawMedals]
         * passes its per-layout cache; the legend, which draws one dot once, builds inline.
         */
        glow: Brush? = null,
    ) {
        val edgeColor = style.panel
        val dotR = max(MEDAL_DOT_MIN_R, radius * MEDAL_DOT_SCALE)
        val dotCenter = Offset(center.x + radius * 0.72f, center.y - radius * 0.72f)
        drawCircle(
            brush = glow ?: kindDotGlowBrush(dotColor, center, radius),
            radius = dotR * MEDAL_DOT_GLOW,
            center = dotCenter,
        )
        if (!AwardGrading.filledDot(kind)) {
            // Hollow: the game art behind it shows through the middle, which is the whole of what
            // separates a completion from the mastery it shares a metal with.
            drawCircle(dotColor, dotR, dotCenter, style = Stroke(MEDAL_DOT_RING))
            // The hairline goes OUTSIDE the ring rather than on it, so it cannot eat the outline
            // this dot is made of.
            drawCircle(edgeColor, dotR + MEDAL_DOT_RING / 2f + 0.5f, dotCenter, style = Stroke(1f))
            return
        }
        drawCircle(dotColor, dotR, dotCenter)
        // The same hairline the type chips carry, so the dot stays legible on top of game art.
        drawCircle(edgeColor, dotR, dotCenter, style = Stroke(1.4f))
    }

    /** The kind dot's glow gradient, from the same geometry [drawKindDot] draws the dot at. */
    internal fun kindDotGlowBrush(dotColor: Color, center: Offset, radius: Float): Brush {
        val dotR = max(MEDAL_DOT_MIN_R, radius * MEDAL_DOT_SCALE)
        val dotCenter = Offset(center.x + radius * 0.72f, center.y - radius * 0.72f)
        return Brush.radialGradient(
            colors = listOf(dotColor.copy(alpha = 0.55f), Color.Transparent),
            center = dotCenter,
            radius = dotR * MEDAL_DOT_GLOW,
        )
    }

    /**
     * One glow brush per medal, built lazily and kept for the life of the chart it belongs to;
     * see the note on [drawKindDot]'s glow parameter. Keyed on frame and style as well as layout
     * because the dot's colour follows the owner mix ([ownerBoundary]) and the theme's metals.
     * Two slots, same reason as [gridTextFor]: an export renders the compact chart on a
     * background thread while the player draws the wide one.
     */
    private class MedalGlows(
        val layout: ChartLayout,
        val frame: JourneyFrame,
        val style: ChartStyle,
    ) {
        val brushes = arrayOfNulls<Brush>(layout.medals.size)
    }

    @Volatile private var medalGlowsA: MedalGlows? = null
    @Volatile private var medalGlowsB: MedalGlows? = null

    private fun medalGlowsFor(layout: ChartLayout, frame: JourneyFrame, style: ChartStyle): MedalGlows {
        medalGlowsA?.let { if (it.layout === layout && it.frame === frame && it.style === style) return it }
        medalGlowsB?.let { if (it.layout === layout && it.frame === frame && it.style === style) return it }
        val built = MedalGlows(layout, frame, style)
        medalGlowsB = medalGlowsA
        medalGlowsA = built
        return built
    }

    /**
     * Whose icon this is, in that player's own colour: the main player's accent (the colour their
     * curve is drawn in), or the rival's slot in the rival palette (the colour theirs is).
     *
     * The one thing the palettes are for now. Badges used to be coloured by their achievement type
     * and, on a span, by their game, which spent the chart's colour on facts that already have a
     * shape of their own (the type chip, the backdrop and the now-playing tag). Colour on this
     * chart means a player.
     */
    private fun playerColor(ownerName: String?, layout: ChartLayout, style: ChartStyle): Color {
        if (ownerName == null || style.rivalColors.isEmpty()) return style.accent
        val index = layout.rivals.indexOfFirst { it.playerName == ownerName }
        if (index < 0) return style.accent
        return style.rivalColors[index % style.rivalColors.size]
    }

    /**
     * The metal an award is drawn in: gold for the two awards that mean the whole game
     * (`MASTERED`, `COMPLETED`), silver for `BEATEN`. Graded by
     * [com.twinlore.app.journey.domain.AwardGrading], which mirrors the library's own award badge.
     *
     * Everything about one award draws with this one colour - its ring, its kind dot, its confetti,
     * its wash and its tooltip border - because one award has to be one colour. `BEATEN` used to be
     * [ChartStyle.inkDim] here while its own disc was silver, so a beaten award threw dull grey
     * confetti and opened a grey-bordered tooltip two centimetres from its silver dot, with nothing
     * to reconcile the two. `COMPLETED` used to be silver here while its ring was gold, which was
     * the same split one step up.
     *
     * Internal so the legend and the strip's own breakdown draw the colour they explain rather than
     * a second, hand-made version.
     */
    internal fun medalColor(kind: AwardKind, style: ChartStyle): Color =
        if (AwardGrading.goldTier(kind)) style.gold else style.silver

    /**
     * The ring around a medallion, which says which award it is rather than whose it is: the award's
     * own metal, the same [medalColor] its dot is drawn in.
     *
     * It used to be the owner's colour for everything except a mastery, which meant a chart of forty
     * games said "somebody's award" forty times and left which award it was to a dot a third the
     * size. What separates the two gold ones is the dot's fill, not a second colour (see
     * [drawKindDot]).
     *
     * Whose it is has not gone anywhere: the halo behind the disc and the dashed leader back to the
     * point on their curve where it was won are both drawn in the owner's colour, in every mode.
     *
     * Internal so the legend draws the ring it explains rather than a second, hand-made version.
     */
    internal fun medalRingColor(kind: AwardKind, style: ChartStyle): Color = medalColor(kind, style)

    private fun awardTitle(kind: AwardKind): String = AwardGrading.title(kind)

    // ---- reveal effects ----

    /**
     * A full-stage radial tint behind an award, flaring in and fading out - the main player's or a
     * rival's alike. Drawn under the data rather than over it: at peak alpha a wash over the curves
     * would hide the chart.
     *
     * Used to skip every medal with a non-null `ownerName` before it ever reached [washSeconds], so
     * a rival's mastery threw its confetti and popped its ring but never earned the one effect that
     * says "this mattered" to somebody scrubbing past it. The tint stays the award's own metal
     * ([medalColor]) rather than switching to the rival's colour: everything else about one award -
     * its ring, its kind dot, its confetti, its tooltip border - already draws in that one colour
     * (see [medalColor]'s own doc), and whose award it is is already said by the halo behind the
     * disc and the leader line back to their curve, in every mode. A flash that changed colour by
     * owner would say two different things depending on whose game it was celebrating; this says
     * the same thing every time - "somebody just mastered a game" - which is the point of it.
     *
     * [washSeconds] is not [drawMedalBursts]'s clock: awards that arrive inside the cooldown of the
     * previous flash never start one (see [JourneyEffectClock.medalWashSeconds]), so a cluster of
     * medals - now drawn from every player on the chart, not just one - flashes once instead of
     * strobing, except that a `MASTERED` award always wins its turn over a lesser one (see
     * [WASH_COOLDOWN_SECONDS]'s own doc for why). The suppressed awards keep their confetti and
     * their pop, which is what marks them at the icon.
     */
    private fun DrawScope.drawMedalWash(
        style: ChartStyle,
        sweep: Float,
        washSeconds: (Int) -> Float,
        layout: ChartLayout,
        /**
         * What is actually on screen. The flash fills this rather than the stage rect (UI
         * feedback): filling the stage left the wash's edges visible inside the letterbox, so
         * the celebration looked cropped to the graph.
         */
        visible: VirtualRect,
    ) {
        if (layout.medals.isEmpty()) return
        layout.medals.forEachIndexed { index, medal ->
            if (medal.anchorX > sweep + PLAYBACK_EPSILON) return@forEachIndexed
            val t = washSeconds(index)
            if (t < 0f || t >= MEDAL_WASH_SECONDS) return@forEachIndexed
            // How loud the celebration is, by how much of the game the award means: a mastery
            // flashes hardest, the casual beaten softest.
            val peak = when (medal.medal.kind) {
                AwardKind.MASTERED -> 0.55f
                AwardKind.COMPLETED -> 0.42f
                AwardKind.BEATEN -> 0.28f
                AwardKind.BEATEN_CASUAL -> 0.22f
            }
            val alpha = if (t < WASH_FLARE) {
                peak * (t / WASH_FLARE)
            } else {
                peak * (1f - (t - WASH_FLARE) / (MEDAL_WASH_SECONDS - WASH_FLARE))
            }
            if (alpha <= 0.004f) return@forEachIndexed
            val tint = medalColor(medal.medal.kind, style)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = alpha), Color.Transparent),
                    // Centred on the anchor, not on the disc. The flash fires when the sweep
                    // reaches the moment the award was won, and in a fanned-out cluster the disc
                    // is not painted yet at that moment: a wash centred there would bloom out of
                    // a patch of empty stage.
                    center = Offset(medal.anchorX, medal.anchorY),
                    radius = hypot(visible.right - visible.left, visible.bottom - visible.top)
                        .coerceAtLeast(hypot(layout.width, layout.height)) * 0.6f,
                ),
                topLeft = Offset(visible.left, visible.top),
                size = Size(visible.right - visible.left, visible.bottom - visible.top),
            )
        }
    }

    /**
     * Confetti off a freshly revealed award. Directions come from the index, never a RNG.
     *
     * Thrown from the anchor - the point on the curve where the award was won - rather than from
     * the medallion. The burst is gated on the anchor so that it fires at the right moment, and at
     * that moment a medallion the collision pass fanned to the right has not been painted yet;
     * confetti bursting out of that empty patch of stage is the same bug the disc's own gate fixes.
     */
    private fun DrawScope.drawMedalBursts(
        layout: ChartLayout,
        style: ChartStyle,
        sweep: Float,
        medalEffectSeconds: (Int) -> Float,
    ) {
        if (layout.medals.isEmpty()) return
        layout.medals.forEachIndexed { index, medal ->
            if (medal.anchorX > sweep + PLAYBACK_EPSILON) return@forEachIndexed
            val t = medalEffectSeconds(index)
            if (t < 0f || t >= MEDAL_BURST_SECONDS) return@forEachIndexed
            val k = t / MEDAL_BURST_SECONDS
            val alpha = 1f - k * k
            val eased = 1f - (1f - k) * (1f - k)
            val color = medalColor(medal.medal.kind, style)
            // Fewer pieces on a crowded stage. The burst is thrown from the point
            // on the curve where the award was won, which is exactly where the runner and the
            // rivals' faces ride - on a dense chart the full spray buries them under confetti at
            // the very moment the chart is trying to show who got there. A journey with room to
            // breathe keeps the whole burst.
            val pieces = burstPiecesFor(layout)
            for (piece in 0 until pieces) {
                val seed = index * 7919 + piece
                val angle = noise(seed) * TWO_PI
                val distance = BURST_MIN + (BURST_MAX - BURST_MIN) * noise(seed + 104_729)
                val travel = distance * eased
                val cx = medal.anchorX + cos(angle) * travel
                val cy = medal.anchorY + sin(angle) * travel
                drawRect(
                    color = (if (piece % 2 == 0) color else style.accent).copy(alpha = alpha),
                    topLeft = Offset(cx - BURST_PIECE / 2f, cy - BURST_PIECE / 2f),
                    size = Size(BURST_PIECE, BURST_PIECE),
                )
            }
        }
    }

    /** "+N" rising off a badge as it lands. */
    private fun DrawScope.drawFloatingPoints(
        layout: ChartLayout,
        paths: ChartPaths,
        style: ChartStyle,
        tm: TextMeasurer,
        revealed: Float,
        sweep: Float,
        gateX: Float,
        nodeEffectSeconds: (Int) -> Float,
    ) {
        if (layout.nodes.isEmpty()) return
        // Effects off entirely - a paused chart, reduced motion, an exported still image. An
        // identity check against the shared singleton, which is exactly what that singleton exists
        // to enable: without it a still frame walked all four hundred nodes evaluating their reveal
        // gates in order to draw nothing.
        if (nodeEffectSeconds === NO_EFFECT) return
        layout.nodes.forEachIndexed { index, node ->
            // The gate FIRST, and never the other way round. [nodeEffectSeconds] is not a pure
            // read: an armed [JourneyEffectClock] stamps "this node appeared now" the first time it
            // is asked about one. Asking before the gate would stamp every node in the layout on
            // frame one, so by the time the sweep actually reached a badge its pop-in and its "+N"
            // would both have expired - every reveal effect dead after the first 1.2 seconds of a
            // playback. The gate is also the cheaper test (a few float compares against a lambda
            // call, a HashMap lookup and a boxed key), so there is nothing to trade off here.
            if (!nodeShown(node, paths.nodeLengths[index], revealed, sweep, gateX)) return@forEachIndexed
            val t = nodeEffectSeconds(index)
            if (t < 0f || t >= FLOATING_POINTS_SECONDS) return@forEachIndexed
            val k = t / FLOATING_POINTS_SECONDS
            drawLabel(
                tm = tm,
                text = "+${node.unlock.points}",
                x = node.x,
                y = node.y - node.r - 6f - FLOATING_POINTS_RISE * k,
                sizePx = TIP_BODY,
                color = playerColor(node.ownerName, layout, style),
                font = style.labelFont,
                anchor = Anchor.CENTER,
                weight = FontWeight.Bold,
                alpha = 1f - k,
            )
        }
    }

    /**
     * A stable hash in [0, 1). Draw passes must be a pure function of the layout and the clock, so
     * the confetti's directions are derived from the piece's index rather than from a RNG that
     * would re-scatter every frame.
     */
    /** How many confetti pieces one award throws on this chart; see [DENSE_ICON_COUNT]. */
    private fun burstPiecesFor(layout: ChartLayout): Int =
        if (layout.iconCount > DENSE_ICON_COUNT) BURST_PIECES_DENSE else BURST_PIECES

    private fun noise(seed: Int): Float {
        var h = seed * -1640531527          // 0x9E3779B9 as a signed int
        h = h xor (h ushr 15)
        h *= -2048144789                    // 0x85EBCA6B
        h = h xor (h ushr 13)
        return (h and 0x7FFFFFFF) / 2_147_483_647f
    }

    /**
     * The game-icon keys of one [JourneyFrame], worked out on first ask and then remembered.
     *
     * [JourneyUrls.media] concatenates and trims, and the draw pass called it per medal and per
     * badge-without-art on every frame - a fresh String, and a fresh hash of it for the image-map
     * lookup, sixty times a second for something that cannot change while a journey is on screen.
     * `NONE` stands in for "this game has no icon", because a concurrent map cannot hold a null.
     */
    private class IconUrls(val frame: JourneyFrame) {
        private val byGame = ConcurrentHashMap<Int, String>()

        fun urlFor(gameId: Int?): String? {
            if (gameId == null) return null
            val known = byGame[gameId]
            if (known != null) return known.takeIf { it !== NONE }
            val resolved = gameRef(frame, gameId)?.iconPath?.let { JourneyUrls.media(it) }
            byGame[gameId] = resolved ?: NONE
            return resolved
        }

        private companion object {
            // Identity-compared, so a real url that happened to be empty could not be mistaken for it.
            val NONE = String()
        }
    }

    // Single-slot and volatile, for the same reason the HUD's tables are: the export renders on a
    // background thread while the chart may be drawing on the main one, and one reference write is
    // atomic. A frame lives for as long as a loaded journey does, so the slot never thrashes.
    @Volatile private var iconUrls: IconUrls? = null

    private fun gameIconUrl(frame: JourneyFrame, gameId: Int?): String? {
        if (gameId == null) return null
        val current = iconUrls
        val cache = if (current != null && current.frame === frame) current else IconUrls(frame).also { iconUrls = it }
        return cache.urlFor(gameId)
    }

    /** A game as any player on this chart knows it: the main player's copy first, then the rivals'. */
    private fun gameRef(frame: JourneyFrame, gameId: Int?): GameRef? {
        if (gameId == null) return null
        return frame.main.games[gameId] ?: frame.rivals.firstNotNullOfOrNull { it.games[gameId] }
    }

    // ---- runner ----

    /**
     * The sign for the flat stretch of [curve] the sweep is standing in, or null when the player
     * has just unlocked something (or is about to).
     *
     * The gap is measured on the DRAWN axis first and converted to time second. "Nothing happened
     * for a while" is a visual fact - a stretch of line with no badges on it - and how much time
     * that is depends entirely on the window: an hour is a wide empty gap on a single evening's
     * chart and invisible on a decade's. So the x width decides whether a gap is worth remarking
     * on, and its length in days only decides which sign it gets ([IdleSigns]).
     *
     * The x-to-millis conversion has to use whichever axis THIS chart actually drew, not always
     * the calendar one: a race chart's x is elapsed time from each player's own first unlock (see
     * [LayoutEngine]'s `xLin`), so its full inner width is [ChartLayout.eMaxMillis] worth of time,
     * not [ChartLayout.axisT1] minus [ChartLayout.axisT0] - that pair stays a calendar range even
     * in race mode (see their own doc), a leftover from the calendar axis rather than a statement
     * about the one actually on screen. Reading a race gap against it would answer with someone
     * else's number: a chart with a rival whose own journey ran for months makes eMax months long
     * even when this player's own gap is an evening, so the calendar span would turn "asleep" into
     * "on holiday for a season" - not merely a slightly wrong number, but a wrong tier.
     *
     * [curve] is any player's own [CurveLayout] - the main player's or a rival's - and that is the
     * whole story of how rivals got signs too: nothing here reads [CurveLayout.playerName] or
     * favours the main curve, so the gap-width floor, the edge margin and the tier rules are the
     * same test for everybody on the chart. A rival with only one unlock or a curve too short to
     * open a 46-unit gap answers null for the same reason a short solo journey does - the budget
     * was never a count to divide across players, it was always "at most one sign, for whichever
     * gap THIS curve's own sweep position is inside right now" - so drawing it once per
     * [ChartLayout.rivals] entry (see [drawRivalMarkers]) never multiplies into a crowd: a chart
     * with three players carries at most three signs, one per face, never one per gap on the chart.
     */
    internal fun idleSignAt(layout: ChartLayout, curve: CurveLayout, sweepX: Float): String? {
        val track = curve.bgTrack
        if (track.size < 2) return null
        // The length, in millis, that this chart's full inner width stands for - the race axis in
        // race mode, the calendar span otherwise. See the axis-choice note above the doc comment.
        val axisMillis = if (layout.raceMode) layout.eMaxMillis else layout.axisT1 - layout.axisT0
        val innerW = innerWidth(layout)
        if (axisMillis <= 0L || innerW <= 0f) return null

        // The unlocks either side of the sweep. Ascending in x by construction, so this is the
        // same binary search the backdrop uses to find the game under the sweep.
        var lo = 0
        var hi = track.size - 1
        var before = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (track[mid].first <= sweepX) {
                before = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        // Before the first unlock or after the last one there is no gap, only the ends of the
        // journey: a sign there would be about the window rather than about the player.
        if (before < 0 || before >= track.size - 1) return null
        val from = track[before].first
        val to = track[before + 1].first
        if (to - from < IDLE_GAP_X) return null
        // Not at the very edges of the gap: the sign belongs to the middle of the wait, so it does
        // not appear on top of the badge that has just landed.
        val margin = (to - from) * IDLE_GAP_EDGE
        if (sweepX < from + margin || sweepX > to - margin) return null

        val gapMillis = ((to - from) / innerW * axisMillis.toFloat()).toLong()
        // A long wait moves on rather than holding one picture for all of it (user request). The
        // step counts how far the sweep has travelled INTO the gap, so it advances with playback
        // (and holds still when playback does): the sweep crosses the stage at a constant rate
        // whatever stage the journey earned, so a fixed width is a fixed number of seconds.
        val step = ((sweepX - from) / IDLE_SIGN_SWITCH_X).toInt()
        return IdleSigns.signFor(gapMillis, seed = before, step = step)
    }

    /**
     * The sign itself, floating over a player's face while their line waits - unless there is a
     * badge in the way.
     *
     * Icons are fanned off the curve now, so one can settle exactly where the sign wants to go and
     * the two draw on top of each other. The sign is the decoration of the pair,
     * so it is the one that gives way: nothing is drawn rather than something drawn over a badge.
     * Only icons the sweep has already reached count - one still ahead of the line is not on the
     * stage to clash with.
     *
     * Whose sign this is - the main player's or a rival's - is said by [center]: it is lifted
     * straight off that player's own face, which already carries their identity in the ring around
     * it (the main accent, or [ChartStyle.colorForRivalIndex]). Tried tinting the glyph itself in
     * the owner's colour instead, the way a badge's boundary is; rejected, because these are colour
     * emoji, and a colour-emoji font paints from its own embedded palette and ignores the text
     * paint's colour entirely - passing an owner's hue into [color] would compile and do nothing.
     * Position is the one channel these glyphs actually have.
     */
    private fun DrawScope.drawIdleSign(
        layout: ChartLayout,
        tm: TextMeasurer,
        style: ChartStyle,
        sign: String,
        center: Offset,
        avatarRadius: Float,
        sweepX: Float,
    ) {
        val at = Offset(center.x, center.y - avatarRadius - IDLE_SIGN_LIFT)
        if (iconNear(layout, at, sweepX)) return
        drawGlyph(
            tm = tm,
            text = sign,
            center = at,
            sizePx = IDLE_SIGN_SIZE,
            color = style.ink,
            font = style.labelFont,
        )
    }

    /** Whether any icon already on stage overlaps a sign drawn at [at]; see [drawIdleSign]. */
    private fun iconNear(layout: ChartLayout, at: Offset, sweepX: Float): Boolean {
        val reach = IDLE_SIGN_SIZE / 2f + IDLE_SIGN_CLEARANCE
        // Cheap x test first, and only against icons the line has reached: this runs once per
        // player per frame over a layout that can hold hundreds of badges.
        layout.nodes.forEach { node ->
            if (node.x > sweepX + PLAYBACK_EPSILON) return@forEach
            if (abs(node.x - at.x) > node.r + reach) return@forEach
            if (abs(node.y - at.y) <= node.r + reach) return true
        }
        layout.medals.forEach { medal ->
            if (medal.x > sweepX + PLAYBACK_EPSILON) return@forEach
            if (abs(medal.x - at.x) > medal.radius + reach) return@forEach
            if (abs(medal.y - at.y) <= medal.radius + reach) return true
        }
        return false
    }

    /**
     * Whether a face riding its curve at [length] is inside one of that curve's streak stretches
     * ([ChartPaths.streakLengths]-shaped bounds): the halo turns flame while the line under the
     * face burns (owner request - the fire ran under the line but the face rode it unlit).
     * Inclusive at both ends, with the same tolerance every reveal comparison uses, so the glow
     * lights on the same frame the fire starts.
     */
    internal fun burningAt(bounds: List<Pair<Float, Float>>, length: Float): Boolean =
        bounds.any { (from, to) ->
            length >= from - PLAYBACK_EPSILON && length <= to + PLAYBACK_EPSILON
        }

    /** The flame halo behind a burning face, over the player-coloured one rather than instead of
     * it: the base halo still says whose face this is, the fire says what their line is doing. */
    private fun DrawScope.drawBurningHalo(position: Offset, radius: Float) {
        val glow = radius * AVATAR_HALO_RATIO * 1.45f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(FLAME_OUTER.copy(alpha = 0.55f), Color.Transparent),
                center = position,
                radius = glow,
            ),
            radius = glow,
            center = position,
        )
    }

    private fun DrawScope.drawRunner(
        layout: ChartLayout,
        paths: ChartPaths,
        style: ChartStyle,
        tm: TextMeasurer,
        bitmaps: Map<String, ImageBitmap>,
        userName: String,
        revealed: Float,
        showIdleSigns: Boolean,
    ) {
        if (layout.main.points.isEmpty()) return
        val position = paths.positionAt(ChartPaths.MAIN, revealed)
        val avatar = if (userName.isBlank()) null else bitmaps[JourneyUrls.userPic(userName)]
        val radius = avatarRadius(layout)
        if (burningAt(paths.streakLengths, revealed)) drawBurningHalo(position, radius)
        drawCircle(style.accent.copy(alpha = 0.2f), radius * AVATAR_HALO_RATIO, position)
        if (showIdleSigns) {
            idleSignAt(layout, layout.main, position.x)?.let {
                drawIdleSign(layout, tm, style, it, position, radius, position.x)
            }
        }
        // The initial-letter fallback the rival end markers get, extended to the one avatar the
        // chart is about. The main player's picture goes missing for the same reasons a rival's
        // does (a blank user name, the media host's case-sensitive UserPic path), and without a
        // letter the runner degraded to exactly the dark blob that fallback exists to prevent.
        drawAvatar(paths.clipScratch, avatar, position, radius, style.accent, style, tm, userName)
    }

    /**
     * A player's face at a point on the chart: their picture, or - when it never arrived - a disc
     * in their own colour with the first letter of their name in it.
     *
     * The fallback is the fix for rival end markers that read as absent on the device. A missing
     * bitmap used to leave the bare [ChartStyle.panel] plate with a thin ring around it, which at
     * this radius is a small dark blob on a dark stage, and a rival's picture is missing far more
     * often than a badge's: [JourneyUrls.userPic] interpolates the name exactly as the user typed
     * it into the rival field, while the media host's UserPic path is case sensitive, so a rival
     * added as "somename" instead of "SomeName" simply 404s. The badges gained a typed-disc
     * fallback for the same class of failure; the avatars never did.
     *
     * [initial] is optional only so a caller with no measurer can still draw the plain plate; every
     * avatar the chart draws - the rival end markers and the runner alike - passes one.
     */
    private fun DrawScope.drawAvatar(
        clip: Path,
        image: ImageBitmap?,
        center: Offset,
        radius: Float,
        ring: Color,
        style: ChartStyle,
        tm: TextMeasurer? = null,
        initial: String? = null,
    ) {
        // By code point, not by char: rival names are free-typed, and `firstOrNull()` on a name
        // starting with an emoji or any astral character hands back half a surrogate pair, which
        // draws as tofu.
        val letter = initial?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { it.substring(0, it.offsetByCodePoints(0, 1)) }
            ?.uppercase()
        if (image != null) {
            drawCircle(style.panel, radius, center)
            drawCircularImage(clip, image, center.x, center.y, radius - 1f, 1f)
        } else if (tm != null && letter != null) {
            drawCircle(ring, radius, center)
            // [ChartTypeIcons.GLYPH_FONT] rather than the theme's, for the reason given there: this
            // letter is sized off the disc's radius and centred against an ordinary font's cap
            // height, so under the pixel font - whose caps fill the em box - it overflowed the disc
            // and sat high in it, exactly as the type stamps did. It stands in for a face, which
            // makes it a mark like theirs rather than a piece of the chart's text.
            drawGlyph(
                tm = tm, text = letter, center = center,
                sizePx = (radius * 1.2f).roundToInt().toFloat(), color = style.panel,
                font = ChartTypeIcons.GLYPH_FONT,
            )
        } else {
            drawCircle(style.panel, radius, center)
        }
        drawCircle(ring, radius, center, style = Stroke(2.4f))
    }

    // ---- tooltip ----

    /** Everything a tooltip needs, resolved from a badge node or a medallion before it is drawn. */
    internal class TipContent(
        val originX: Float,
        val originY: Float,
        val originR: Float,
        val accent: Color,
        val iconUrl: String?,
        val title: String,
        val body: List<Pair<String, Int>>,   // text to max lines
        /** See [TipHighlight]. Null for every tooltip that has nothing to pick out. */
        val highlight: TipHighlight? = null,
    )

    /**
     * One run of one body line drawn in a colour of its own, rather than in the dim ink the rest of
     * the line is.
     *
     * One case, and one reason: the chart marks a hardcore unlock with a [HARDCORE_RING] ring, and
     * the tooltip is the only place that spells the word out, so "Hardcore" is said in the ring's
     * own colour and the two agree.
     *
     * [line] is an index into [TipContent.body] rather than a word to go looking for. Searching the
     * whole body would have coloured the word wherever it turned up - and an achievement's own
     * description is body text the site's authors wrote, so "Beat the game in Hardcore mode" would
     * have had a word of it lit up for no reason. The meta row is the row that means it.
     */
    internal class TipHighlight(val line: Int, val word: String, val color: Color)

    /**
     * Internal rather than private so a test can ask which body row the highlight points at. Which
     * row it is cannot be read off a rendered tooltip, and getting it wrong is silent: the word
     * simply lights up in a line it does not belong to.
     */
    /**
     * The tooltip currently on screen, built once for the icon it points at.
     *
     * [nodeTip] and [medalTip] allocate a list, several concatenated strings and a formatted date,
     * and they were being run inside the draw pass on every frame the tooltip was open - which is
     * during playback, exactly when the frame budget is tightest. Nothing they produce changes
     * while the cursor stays on one icon.
     */
    private class OpenTip(
        val layout: ChartLayout,
        val frame: JourneyFrame,
        val style: ChartStyle,
        val nodeIndex: Int,
        val medalIndex: Int,
        val tip: TipContent,
    )

    @Volatile private var openTip: OpenTip? = null

    private fun tipFor(
        layout: ChartLayout,
        frame: JourneyFrame,
        style: ChartStyle,
        nodeIndex: Int,
        medalIndex: Int,
    ): TipContent {
        val current = openTip
        if (current != null && current.layout === layout && current.frame === frame &&
            current.style === style && current.nodeIndex == nodeIndex && current.medalIndex == medalIndex
        ) {
            return current.tip
        }
        val tip = if (nodeIndex >= 0) {
            nodeTip(layout.nodes[nodeIndex], layout, frame, style)
        } else {
            medalTip(layout.medals[medalIndex], frame, style)
        }
        openTip = OpenTip(layout, frame, style, nodeIndex, medalIndex, tip)
        return tip
    }

    internal fun nodeTip(
        node: NodeLayout,
        layout: ChartLayout,
        frame: JourneyFrame,
        style: ChartStyle,
    ): TipContent {
        val unlock = node.unlock
        // The owner's own roster first: on a compare span a rival's badge can belong to a game the
        // main player has never touched, and looking it up in the main player's list alone left the
        // tooltip nameless.
        val game = gameRef(frame, unlock.gameId)
        val body = mutableListOf<Pair<String, Int>>()
        if (game != null) body += game.title to 1
        // "Unlocked by" rather than "Earned by": the rarity line below is also an "Earned by", and
        // the two said the same three words about two unrelated facts (who unlocked this one, and
        // what fraction of the site has it) back to back in the same panel.
        node.ownerName?.let { body += "Unlocked by $it" to 1 }
        if (unlock.description.isNotBlank()) body += unlock.description to 3
        val mode = if (unlock.hardcore) "Hardcore" else "Softcore"
        // Held before the line is appended: the highlight names this row by index, so it cannot
        // drift onto the description above it or the date below.
        val metaLine = body.size
        body += "${typeLabel(unlock.type)} · ${pluralize(unlock.points, "pt")} · $mode" to 1
        // The date gets a line of its own. It used to close the meta row, and on a "Win condition"
        // hardcore unlock that row overran the panel's text width - the ellipsis took the date,
        // which is the one fact a badge on a timeline is most often asked for.
        body += TickGenerator.fmtDate(unlock.earnedAtMillis) to 1
        if (game != null && game.players > 0) {
            val pct = unlock.numAwarded * 100f / game.players
            val shown = if (pct >= 10f) pct.roundToInt().toString() else (Math.round(pct * 10f) / 10f).toString()
            body += "Earned by $shown% of players" to 1
        }
        return TipContent(
            originX = node.x, originY = node.y, originR = node.r,
            accent = playerColor(node.ownerName, layout, style),
            iconUrl = RaUrls.badge(unlock.badgeName),
            title = unlock.title,
            body = body,
            // The one word on this panel the chart also says in colour.
            highlight = if (unlock.hardcore) {
                TipHighlight(metaLine, mode, hardcoreTextColor(style))
            } else {
                null
            },
        )
    }

    private fun medalTip(medal: MedalLayout, frame: JourneyFrame, style: ChartStyle): TipContent {
        val award = medal.medal
        val body = mutableListOf<Pair<String, Int>>()
        body += "${awardTitle(award.kind)} · ${TickGenerator.fmtDate(award.whenMillis)}" to 1
        // A rival's medal already reads "rival · Game Title" in the header, so naming the owner
        // again below it just says the same thing twice.
        award.ownerName
            ?.takeIf { !award.title.startsWith("$it · ") }
            ?.let { body += "Award earned by $it" to 1 }
        return TipContent(
            originX = medal.x, originY = medal.y, originR = medal.radius,
            accent = medalColor(award.kind, style),
            iconUrl = gameIconUrl(frame, award.gameId),
            title = award.title,
            body = body,
        )
    }

    /**
     * The part of [layout] the viewer can actually see, in virtual units: what [view] shows of the
     * 1060x600 viewport on a [canvasSize] canvas, intersected with the chart itself.
     *
     * Both halves matter. Without the view, a tooltip or a focus ring on a widened chart is placed
     * against layout edges that are most of a timeline off screen - which is how a tooltip opened
     * near the right of the window ended up drawn past it. Without the intersection, a portrait
     * canvas (which shows a stage and a half of empty letterbox above and below the chart) would
     * put them out in that empty space instead.
     *
     * A canvas of unknown size ([Size.Zero]: a fitted export frame, the render tests) is taken to
     * fit the viewport exactly, which is what those callers draw into. A *followed* export frame
     * passes its real 1920x1080 instead, because there the letterbox and the camera are the same
     * composition the live chart draws through and the cull has to describe it exactly.
     */
    internal fun visibleBounds(layout: ChartLayout, view: ChartZoom, canvasSize: Size): VirtualRect {
        val visible = ViewportCull.visibleRect(
            view = view,
            viewportWidth = LayoutEngine.W,
            viewportHeight = LayoutEngine.H,
            canvasWidth = canvasSize.width,
            canvasHeight = canvasSize.height,
        )
        return VirtualRect(
            left = max(0f, visible.left),
            top = max(0f, visible.top),
            right = min(layout.width, visible.right),
            bottom = min(layout.height, visible.bottom),
        )
    }

    /**
     * Where a tooltip box of [boxWidth] x [boxHeight] goes: beside the icon it belongs to, on
     * whichever side it fits, and always inside [bounds] with [TIP_MARGIN] to spare.
     *
     * Pure and internal so the placement can be tested against a windowed view directly - the
     * failure it exists to prevent (a box drawn off the edge of what is on screen) is one that a
     * rendered bitmap of the *virtual* space cannot see.
     */
    internal fun tooltipTopLeft(
        bounds: VirtualRect,
        tip: TipContent,
        boxWidth: Float,
        boxHeight: Float,
    ): Offset {
        var left = tip.originX + tip.originR + 12f
        if (left + boxWidth > bounds.right - TIP_MARGIN) {
            left = tip.originX - tip.originR - 12f - boxWidth
        }
        val minLeft = bounds.left + TIP_MARGIN
        val minTop = bounds.top + TIP_MARGIN
        return Offset(
            x = left.coerceIn(minLeft, max(minLeft, bounds.right - boxWidth - TIP_MARGIN)),
            y = (tip.originY - boxHeight / 2f)
                .coerceIn(minTop, max(minTop, bounds.bottom - boxHeight - TIP_MARGIN)),
        )
    }

    /**
     * The detail panel for the cursored badge or medallion.
     *
     * The bug this replaces: [ChartStyle.panel] is the theme's `surfaceVariant`, which this app
     * defines as the *foreground* colour at 12% alpha. Repainting it at `alpha = 0.96` produced a
     * near-opaque foreground-coloured panel, so every line drawn in `ink` or `inkDim` - the title,
     * the description, the type, the points, the rarity - was foreground on foreground and simply
     * vanished. Only the game title survived, because it was the one line drawn in a series colour,
     * which is exactly the "it only shows the game name" the chart was reported for. The panel is
     * now opaque by construction (see `rememberChartStyle`) and this draws its own backing rather
     * than trusting whatever alpha the palette happens to carry.
     */
    /**
     * The tooltip's within-stage scale: on screen the box grows naturally with the zoom until
     * [TIP_MAX_SCALE] and then holds that size - constant screen size (1/zoom) was tried first
     * and read as a shrinking box beside badges that had grown huge deep in a follow zoom (owner
     * report), which is exactly the arc the corner date walked before it got the same cure at
     * its own cap. Below 1x the box keeps its authored size: an overview tooltip that grew would
     * be the same illegibility from the other end.
     */
    internal fun tooltipScale(zoom: Float): Float {
        val z = zoom.coerceAtLeast(1f)
        return min(z, TIP_MAX_SCALE) / z
    }

    /**
     * Smaller than the corner date's 2.5: the date is a few characters and earns its headroom;
     * the tooltip is a whole panel, and at 2.5x it swallowed the stage it annotates.
     */
    internal const val TIP_MAX_SCALE = 1.6f

    private fun DrawScope.drawTooltip(
        bounds: VirtualRect,
        style: ChartStyle,
        tm: TextMeasurer,
        bitmaps: Map<String, ImageBitmap>,
        clip: Path,
        tip: TipContent,
        /**
         * The view's magnification, so the panel can hold a constant size on SCREEN.
         *
         * Everything else on this chart is drawn in virtual units and magnifies with the zoom,
         * which is the point - a badge is meant to get bigger when you lean in. A tooltip is not
         * part of the picture, it is a readout about it, and at 8x a 320-unit panel is far wider
         * than the 130 units of stage the viewer can see: the box ran off every edge at once and
         * could not be read at all. Scaling it down by the zoom keeps it the same
         * size to the eye at every magnification.
         */
        zoom: Float,
    ) {
        val icon = tip.iconUrl?.let { bitmaps[it] }
        val iconSpan = if (icon != null) TIP_ICON_R * 2 + TIP_GAP * 2 else 0f
        val titleWidth = TIP_TEXT_WIDTH - iconSpan
        val title = measureText(
            tm, AnnotatedString(tip.title), TIP_TITLE, style.labelFont,
            FontWeight.Bold, titleWidth, 2,
        )
        val lines = tip.body.mapIndexedNotNull { index, (text, maxLines) ->
            if (text.isBlank()) null
            else measureText(
                tm, annotate(text, tip.highlight?.takeIf { it.line == index }), TIP_BODY,
                style.labelFont, FontWeight.Normal, TIP_TEXT_WIDTH, maxLines,
            )
        }

        val headerHeight = max(title.size.height.toFloat(), if (icon != null) TIP_ICON_R * 2 else 0f)
        val contentWidth = max(
            iconSpan + title.size.width,
            lines.maxOfOrNull { it.size.width }?.toFloat() ?: 0f,
        )
        val boxWidth = max(TIP_MIN_WIDTH, TIP_BORDER + TIP_PAD * 2 + contentWidth)
        val boxHeight = TIP_PAD * 2 + headerHeight +
            (if (lines.isEmpty()) 0f else TIP_GAP + lines.sumOf { it.size.height }.toFloat() + TIP_GAP * (lines.size - 1))

        // Grows with the lean-in to a cap, and never below its authored size when the chart is
        // zoomed out past 1 (a wide chart's overview sits below 1, and a tooltip that grew there
        // would be the same illegibility from the other end). See [tooltipScale].
        val scale = tooltipScale(zoom)
        val at = tooltipTopLeft(bounds, tip, boxWidth * scale, boxHeight * scale)
        val left = at.x
        val top = at.y

        // Drawn at the origin inside a scaled, translated frame rather than at absolute
        // coordinates: the text is measured at its authored size (so the measurer's cache still
        // hits at every zoom level) and the whole panel is then shrunk to screen size in one
        // transform.
        withTransform({
            translate(left, top)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
        val left = 0f
        val top = 0f
        // Opaque backing first, then the panel tint over it: the palette's panel colour may be a
        // translucent wash of the foreground, and text has to land on something solid.
        drawRoundRect(
            color = style.background,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(12f, 12f),
        )
        drawRoundRect(
            color = style.panel,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(12f, 12f),
        )
        drawRoundRect(
            color = tip.accent.copy(alpha = 0.5f),
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(1.4f),
        )
        drawRect(
            color = tip.accent,
            topLeft = Offset(left, top + 6f),
            size = Size(TIP_BORDER, max(0f, boxHeight - 12f)),
        )

        val textLeft = left + TIP_BORDER + TIP_PAD
        if (icon != null) {
            // The same square art the chart draws, so the tooltip's copy of an icon looks like the
            // icon it was opened from.
            val iconCenter = Offset(textLeft + TIP_ICON_R, top + TIP_PAD + TIP_ICON_R)
            fillIconSquare(style.background, iconCenter, TIP_ICON_R)
            drawSquareImage(clip, icon, iconCenter.x, iconCenter.y, TIP_ICON_R - 1f, 1f)
            strokeIconSquare(tip.accent, iconCenter, TIP_ICON_R, 1.6f)
        }
        // Colour at draw time, not in the measured style - see [measureStyle]. The highlight's own
        // colour is carried by a span inside the AnnotatedString, and a span beats the paint, so
        // "Hardcore" still comes out in the ring's yellow rather than in the dim ink around it.
        drawText(title, color = style.ink, topLeft = Offset(textLeft + iconSpan, top + TIP_PAD))

        var y = top + TIP_PAD + headerHeight + TIP_GAP
        lines.forEach { line ->
            drawText(line, color = style.inkDim, topLeft = Offset(textLeft, y))
            y += line.size.height + TIP_GAP
        }
        }
    }

    private fun typeLabel(type: AchievementType): String = when (type) {
        AchievementType.MISSABLE -> "Missable"
        AchievementType.PROGRESSION -> "Progression"
        AchievementType.WIN_CONDITION -> "Win condition"
        AchievementType.STANDARD -> "Standard"
    }

    // ---- overlay ----

    private fun DrawScope.drawDateOverlay(
        layout: ChartLayout,
        style: ChartStyle,
        tm: TextMeasurer,
        progress: Float,
    ) {
        // Quantised to the same 1% the shell reports playback at, and for the same reason the HUD
        // beside it is: an unquantised p moves every vsync, so this was the one measure in the file
        // that could never hit the text cache - a fresh date format plus a full text layout, per
        // frame, for a line the reader takes in at about the granularity the counters move.
        val p = (progress.coerceIn(0f, 1f) * OVERLAY_STEPS).toInt() / OVERLAY_STEPS.toFloat()
        val text = if (layout.raceMode) {
            TickGenerator.fmtElapsedApprox((layout.eMaxMillis * p).toLong())
        } else {
            TickGenerator.fmtDate(layout.axisT0 + ((layout.axisT1 - layout.axisT0) * p).toLong())
        }
        drawLabel(
            tm, text, layout.width - layout.padR, OVERLAY_Y, OVERLAY, style.ink, style.labelFont,
            Anchor.RIGHT, FontWeight.Bold,
        )
    }

    // ---- drawing helpers ----

    private fun innerWidth(layout: ChartLayout): Float = layout.width - layout.padL - layout.padR

    /** Corner radius of an icon whose half-width is [radius]: a fifth of its full width. */
    private fun iconCorner(radius: Float): Float = radius * 2f * ICON_CORNER_FRACTION

    /**
     * The opaque plate an icon's art sits on. Square, because badge and game art are square.
     *
     * Internal for the same reason [drawHardcoreRing] is: the legend draws a badge beside the words
     * explaining one, and a badge is a plate with a ring around it.
     */
    internal fun DrawScope.fillIconSquare(color: Color, center: Offset, radius: Float) {
        if (radius <= 0f) return
        val corner = iconCorner(radius)
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            cornerRadius = CornerRadius(corner, corner),
        )
    }

    /**
     * The type ring, the award ring and the cursor ring: the plate's outline at [width].
     *
     * Internal for the same reason [fillIconSquare] is: the legend draws the marks it explains
     * through the renderer's own geometry, so a change of icon shape reaches the key as well.
     */
    internal fun DrawScope.strokeIconSquare(color: Color, center: Offset, radius: Float, width: Float) {
        if (radius <= 0f) return
        val corner = iconCorner(radius)
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width),
        )
    }

    /**
     * Badge and game art, clipped to a rounded square rather than a circle. Every image the chart
     * draws through here is a native square, and a circular crop cut the corners off all of them
     * (which on RetroAchievements badge art is often where the art is). Profile pictures keep the
     * circle: [drawCircularImage] is what the runner and the rival end avatars draw through.
     */
    private fun DrawScope.drawSquareImage(
        clip: Path,
        image: ImageBitmap,
        centerX: Float,
        centerY: Float,
        radius: Float,
        alpha: Float,
        tint: Color? = null,
    ) {
        if (radius <= 0.5f) return
        val corner = iconCorner(radius)
        if (corner <= 0f) {
            // Sharp corners need no clip at all: the dst rect the draw below scales into IS the
            // square the clip would have described. The save/clipPath/restore this skips ran once
            // per badge per frame, which on a dense span (hundreds of drawn icons, every vsync of
            // playback) was the most expensive no-op on the stage.
            drawArt(image, centerX, centerY, radius, alpha, tint)
            return
        }
        clip.reset()
        clip.addRoundRect(
            RoundRect(
                Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
                CornerRadius(corner, corner),
            ),
        )
        clipPath(clip) { drawArt(image, centerX, centerY, radius, alpha, tint) }
    }

    private fun DrawScope.drawCircularImage(
        clip: Path,
        image: ImageBitmap,
        centerX: Float,
        centerY: Float,
        radius: Float,
        alpha: Float,
    ) {
        if (radius <= 0.5f) return
        clip.reset()
        clip.addOval(Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius))
        clipPath(clip) { drawArt(image, centerX, centerY, radius, alpha) }
    }

    /**
     * Draws [image] scaled into the square around ([centerX], [centerY]), washed in [tint] when
     * one is given (see [ownerTint]). Any clipping is the caller's: the square path skips it
     * entirely at sharp corners, the circular one wraps this in its oval.
     *
     * The tint is a `SrcAtop` colour filter rather than a second coloured rectangle over the top:
     * `SrcAtop` composites the colour - at the alpha the colour itself carries - only where the
     * image already has pixels, so a badge with transparent corners does not gain coloured ones,
     * and the whole thing stays one draw call on a stage that can hold two hundred badges.
     */
    private fun DrawScope.drawArt(
        image: ImageBitmap,
        centerX: Float,
        centerY: Float,
        radius: Float,
        alpha: Float,
        tint: Color? = null,
    ) {
        val size = (radius * 2).roundToInt().coerceAtLeast(1)
        drawImage(
            image = image,
            dstOffset = IntOffset((centerX - radius).roundToInt(), (centerY - radius).roundToInt()),
            dstSize = IntSize(size, size),
            alpha = alpha.coerceIn(0f, 1f),
            colorFilter = tint?.let(::tintFilter),
        )
    }

    /**
     * The `SrcAtop` filter for one tint colour, built once per colour rather than per badge.
     *
     * A compare frame draws up to a couple of hundred tinted icons and there are at most four
     * distinct player colours among them, so the filters are cached on the colour. Concurrent
     * because the video export draws through this same object on a background thread while nothing
     * stops the chart from drawing on the main one - the same reason [measureStyles] is.
     */
    private val tintFilters = ConcurrentHashMap<Color, ColorFilter>()

    private fun tintFilter(color: Color): ColorFilter =
        tintFilters.getOrPut(color) { ColorFilter.tint(color, BlendMode.SrcAtop) }

    /**
     * What a measured style is keyed on. Deliberately not the [TextStyle] itself: that class hashes
     * a whole `SpanStyle` and `ParagraphStyle` on every lookup, which is the cost this exists to
     * avoid. Density and font scale are in the key because [sizePx] is a *virtual unit* and the sp
     * it becomes depends on both.
     */
    private data class TextStyleKey(
        val sizePx: Float,
        val weight: Int,
        val font: FontFamily,
        val density: Float,
        val fontScale: Float,
    )

    /**
     * The measuring styles, built once per (size, weight, font, density) and reused for the life of
     * the process.
     *
     * A frame measures up to four hundred badge glyphs plus the furniture, and each of those used to
     * allocate a `TextStyle` (and with it a `SpanStyle` and a `ParagraphStyle`) and then pay
     * `TextStyle.hashCode()` on the measurer's cache lookup - the dominant per-glyph cost even on a
     * cache *hit*. The set of distinct keys is small and bounded: a handful of whole-number sizes
     * (see the note on [LABEL] and friends), three weights, one font, one density.
     *
     * Concurrent because the video export draws through this same object on a background thread
     * while nothing stops the chart from drawing on the main one.
     */
    private val measureStyles = ConcurrentHashMap<TextStyleKey, TextStyle>()

    /**
     * The style a piece of text is *measured* with. Carries no colour, on purpose.
     *
     * Colour has no effect on layout, but baking it into the style makes it part of the
     * TextMeasurer's cache key, so the same glyph in two players' colours is two entries and the
     * constant labels get evicted by node glyphs on every frame. It is applied at `drawText` time
     * instead - the pattern
     * [com.twinlore.app.journey.export.ExportStrip] already follows.
     */
    private fun DrawScope.measureStyle(sizePx: Float, font: FontFamily, weight: FontWeight): TextStyle {
        val key = TextStyleKey(sizePx, weight.weight, font, density, fontScale)
        return measureStyles[key] ?: TextStyle(
            fontSize = sizePx.toSp(),
            fontFamily = font,
            fontWeight = weight,
        ).also { measureStyles[key] = it }
    }

    private fun DrawScope.measureText(
        tm: TextMeasurer,
        text: AnnotatedString,
        sizePx: Float,
        font: FontFamily,
        weight: FontWeight,
        maxWidthPx: Float,
        maxLines: Int,
    ): TextLayoutResult = tm.measure(
        text = text,
        style = measureStyle(sizePx, font, weight),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        constraints = Constraints(maxWidth = maxWidthPx.roundToInt().coerceAtLeast(1)),
    )

    private fun DrawScope.measureText(
        tm: TextMeasurer,
        text: String,
        sizePx: Float,
        font: FontFamily,
        weight: FontWeight,
        maxWidthPx: Float,
        maxLines: Int,
    ): TextLayoutResult =
        measureText(tm, AnnotatedString(text), sizePx, font, weight, maxWidthPx, maxLines)

    /**
     * [text] with [highlight]'s word coloured, and nothing else touched.
     *
     * Only ever called with the highlight already narrowed to the one line it belongs to (see
     * [TipHighlight]), so the search here cannot wander into an achievement's own description.
     *
     * Plain text when there is no highlight, so the TextMeasurer's cache - keyed on the whole
     * annotated string - is not made to hold a second entry per line for a span that is not there.
     */
    private fun annotate(text: String, highlight: TipHighlight?): AnnotatedString {
        if (highlight == null || highlight.word.isEmpty()) return AnnotatedString(text)
        val at = text.indexOf(highlight.word)
        if (at < 0) return AnnotatedString(text)
        return buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(color = highlight.color, fontWeight = FontWeight.Bold),
                at,
                at + highlight.word.length,
            )
        }
    }

    /**
     * One short glyph centred on a point: the type chip's stamp, the missing-art disc's fallback
     * mark, a rival's initial.
     *
     * Centred on the glyph's own baseline rather than on a fraction of the shape it sits in, which
     * is the fix for the star. Back when a stamp still came out of whatever font the theme was
     * setting, `!` was in that font, so a hand-tuned "shift it up by about 0.95 of the chip radius"
     * landed it near enough the middle of its circle; `★` was not, and the fallback it resolved to
     * has a taller box, so the same offset left it visibly low. Measured at an 18-unit size in a
     * 24-unit chip, the star's ink sat 2.3 units below the centre under that rule and 1.2 below it
     * if the measured line box is centred instead; centred on the baseline it sits within 0.2 of
     * the centre. The marks drawn in a shape - the stamps, and the letter standing in for a rival's
     * face - are pinned to one font now ([ChartTypeIcons.GLYPH_FONT]); the idle sign above a face is
     * an emoji and resolves through a fallback whatever the theme says, so the reason to measure
     * rather than assume outlived the case that produced it.
     *
     * See [glyphTopLeft] for why the baseline is the right thing to measure from. Measuring first
     * costs nothing (the measurer caches on the text style, and these are whole-unit sizes at full
     * opacity, faded at draw time) and is right for any glyph out of any font.
     */
    private fun DrawScope.drawGlyph(
        tm: TextMeasurer,
        text: String,
        center: Offset,
        sizePx: Float,
        color: Color,
        font: FontFamily,
        alpha: Float = 1f,
    ) {
        if (text.isEmpty()) return
        val result = measureText(tm, text, sizePx, font, FontWeight.Bold, TIP_TEXT_WIDTH, 1)
        drawText(
            textLayoutResult = result,
            color = color,
            topLeft = glyphTopLeft(center, result.size.width, sizePx, result.firstBaseline),
            alpha = alpha,
        )
    }

    /**
     * Where a glyph measured to [width] at [sizePx], with its baseline [baseline] below the top of
     * its line box, is drawn so that the glyph itself is centred on [center].
     *
     * The line box is the wrong thing to centre. It is sized for the whole font - ascent, descent
     * and leading - so a font with room for descenders puts the ink of a capital or a symbol above
     * the middle of it, and two glyphs resolved from two different fonts (which `!` and `★` are)
     * are offset by different amounts. What is common to all of them is the baseline: ink of cap
     * height sits between the baseline and roughly [CAP_HEIGHT] of the size above it, so centring
     * that band is centring the mark.
     *
     * Internal because "is the stamp in the middle of its chip" is arithmetic, and reading it off a
     * rendered bitmap is not.
     */
    internal fun glyphTopLeft(center: Offset, width: Int, sizePx: Float, baseline: Float): Offset =
        Offset(center.x - width / 2f, center.y + sizePx * CAP_HEIGHT / 2f - baseline)

    private fun DrawScope.drawLabel(
        tm: TextMeasurer,
        text: String,
        x: Float,
        y: Float,
        sizePx: Float,
        color: Color,
        font: FontFamily,
        anchor: Anchor = Anchor.LEFT,
        weight: FontWeight = FontWeight.Normal,
        maxWidthPx: Float = TIP_TEXT_WIDTH * 2,
        alpha: Float = 1f,
    ) {
        if (text.isEmpty()) return
        val result = measureText(tm, text, sizePx, font, weight, maxWidthPx, 1)
        val left = when (anchor) {
            Anchor.LEFT -> x
            Anchor.CENTER -> x - result.size.width / 2f
            Anchor.RIGHT -> x - result.size.width
        }
        drawText(result, color = color, topLeft = Offset(left, y), alpha = alpha)
    }
}
