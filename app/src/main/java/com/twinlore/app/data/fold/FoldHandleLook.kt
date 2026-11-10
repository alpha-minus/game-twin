package com.twinlore.app.data.fold

/**
 * Everything the folded handle is drawn from, in one value.
 *
 * These travelled as loose parameters through [com.twinlore.app.ui.fold.FoldPlatform.showTab]
 * and [com.twinlore.app.ui.fold.FoldController.enterOverlay], and there were
 * two of them, which was tolerable. A third made it three adjacent numbers
 * describing one object, in a signature that already carried a display id and
 * two position fractions: exactly the call site where a transposed pair is
 * both easy to write and invisible to read.
 *
 * Constructed through [FoldHandleStyle], which owns the ranges. The snapping
 * here is belt and braces for anything that builds one by hand.
 */
data class FoldHandleLook(
    /** How solid the handle is drawn, 0f..1f. Constant: it does not fade. */
    val alpha: Float = FoldHandleStyle.DEFAULT_ALPHA,
    /** How big it is in dp, which is also how big its tap target is. */
    val sizeDp: Int = FoldHandleStyle.DEFAULT_SIZE_DP,
    /** What fraction of that it is drawn at while the panel is open. */
    val recessScale: Float = FoldHandleStyle.DEFAULT_RECESS,
) {
    companion object {
        /** The only way in from stored or user input, all three values snapped. */
        fun of(alpha: Float, sizeDp: Int, recessScale: Float) = FoldHandleLook(
            alpha = FoldHandleStyle.alpha(alpha),
            sizeDp = FoldHandleStyle.sizeDp(sizeDp),
            recessScale = FoldHandleStyle.recess(recessScale),
        )
    }
}
