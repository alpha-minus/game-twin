package com.twinlore.app.ui.screens.offline

/**
 * Smart formatting is a CSS mode over losslessly-emitted guide HTML, not a
 * conversion-time branch.
 *
 * The reason is [com.twinlore.app.data.library.TextGuideSplitter]: once a guide
 * is big enough to become a page group, each chunk's HTML is written to the
 * vault folder as MHT and the original tab is deleted, so the source text is
 * gone. A conversion-time toggle would therefore be silently dead on exactly the
 * biggest, most ASCII-heavy guides. Flipping the mode is instead a class on
 * `<body>` plus a call to the page's own `gtApplyFormat()` hook, which works the
 * same on a freshly converted guide and on a chunk written to disk months ago,
 * with no reload and no scroll loss.
 */

/** Marker `convertTxtToGameFaqHtml` puts on `<body>`. Deliberately NOT
 *  `data-gamefaqs-converted`, which `EpubParser` also sets, on reflowed EPUB
 *  HTML that has none of the runs or scroll-blocks this toggle acts on. */
private const val TEXT_GUIDE_MARKER = "data-gt-textfaq=\"1\""

/** Flip an already-loaded guide between smart and raw formatting. */
fun buildSmartFormatJs(smart: Boolean): String =
    "(function(){if(!document.body)return;" +
        "document.body.classList.toggle('gt-raw',${!smart});" +
        "if(window.gtApplyFormat)window.gtApplyFormat()})();"

/** Mark the body raw before the WebView's first paint, so a guide opened while
 *  smart formatting is off never flashes the reflowed layout first. No-op for
 *  smart mode and for documents that did not come from the text converter. */
fun applySmartFormatPrelude(html: String, smart: Boolean): String {
    if (smart) return html
    if (!html.contains(TEXT_GUIDE_MARKER)) return html
    return html.replaceFirst(TEXT_GUIDE_MARKER, "$TEXT_GUIDE_MARKER class=\"gt-raw\"")
}

/** Reports whether the loaded document is a converted text guide, so the Display
 *  sheet can dim the Smart Formatting row on saved web guides and EPUBs. */
const val DETECT_TEXT_GUIDE_JS: String =
    "(function(){return !!(document.body&&document.body.getAttribute('data-gt-textfaq'))})()"

/**
 * Apply the text-size slider to a converted text guide as an explicit body
 * font-size.
 *
 * `WebView.settings.textZoom` does nothing on these documents. Chromium routes
 * it through the text autosizer, and the converter sets `text-size-adjust:100%`
 * to stop that same autosizer inflating joined paragraph lines (GH #25) — so the
 * opt-out takes the slider down with it. Measured on device: driving textZoom
 * across 50/60/90/170/190 left the computed body font-size pinned at 18.9678px
 * and the document height unchanged at every step.
 *
 * An explicit author font-size is unaffected by `text-size-adjust`, which only
 * gates the automatic adjustment, so this reaches the text where textZoom cannot.
 *
 * Deliberately self-contained, depending on no hook inside the page: page-group
 * chunks are MHT files written to disk at split time, so a guide split last month
 * carries whatever script it was born with. This has to work on those too.
 *
 * The untouched size is captured once into a data attribute before the first
 * override, so repeated calls scale from the document's own default rather than
 * compounding, and 1.0 restores exactly what the page shipped with.
 *
 * The size scales from a fixed [GUIDE_BASE_FONT_PX] rather than from a base
 * measured off the live document. Measuring was tried three ways and each failed
 * differently: reading while an override was applied captured the override itself
 * (17.0682 recorded as the base of an 18.9678px document); reading after clearing
 * the override returned 16px, the specified size the autosizer boosts FROM, even
 * with a forced layout flush; and capturing only at page load left the slider
 * inert whenever that one call did not land. The measured value depends on
 * autosizer timing, so anything derived from it is a race.
 *
 * A constant has none of those failure modes: every call is idempotent, order
 * does not matter, and 120% is always exactly 120% of 100%. The tradeoff is that
 * 100% now means 16px (the size these guides are authored against) rather than
 * whatever the autosizer had boosted it to, so a guide can look slightly smaller
 * than before until the reader moves the slider, which persists per guide.
 */
const val GUIDE_BASE_FONT_PX = 16

fun buildTextZoomJs(fraction: Float): String =
    "(function(){var b=document.body;if(!b)return;" +
        "b.style.fontSize=(${GUIDE_BASE_FONT_PX}*$fraction)+'px';" +
        // Wider/narrower text changes how much fits per ASCII block, so smart
        // mode needs to re-fit. Raw mode's gtApplyFormat is a no-op by design.
        "if(window.gtApplyFormat)window.gtApplyFormat()})();"

/**
 * Sizes the text before the first paint, so the page-finished pass finds it
 * already there.
 *
 * [buildTextZoomJs] runs from onPageFinished, which is after the page has been
 * revealed: the guide painted at the document's own size and then jumped to the
 * saved one, blocks re-fitting as it went. Seen every time the overlay panel
 * reopened, because the panel rebuilds its reader from scratch and reloads the
 * page. The palette and the raw-mode mark both go in before the paint; the size
 * now does too.
 *
 * The same script, spliced straight behind the body's open tag. A script there
 * runs while the parser is still on the tag, with `document.body` present and
 * empty, so the size is set before any content is laid out. The converter's own
 * fit runs at the end of the body and measures at this size, and the later pass
 * from onPageFinished writes the same value again and moves nothing.
 *
 * A document with no body tag is left alone: there is nothing to size before,
 * and the page-finished pass still covers it.
 */
fun applyTextZoomPrelude(html: String, fraction: Float): String {
    val bodyOpen = Regex("<body\\b[^>]*>", RegexOption.IGNORE_CASE).find(html) ?: return html
    val insertAt = bodyOpen.range.last + 1
    return html.substring(0, insertAt) +
        "<script>" + buildTextZoomJs(fraction) + "</script>" +
        html.substring(insertAt)
}
