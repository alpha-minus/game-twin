package com.twinlore.app.util

import android.webkit.WebView

/**
 * Pauses every `<video>`/`<audio>` element in this WebView's top-level document.
 *
 * Call this when a WebView-hosting tab becomes hidden. In this app every vault
 * tab is kept composed for the whole session (see VaultSessionScreen), so a tab
 * the user navigated away from is still attached and drawing. `WebView.onPause()`
 * is documented to pause "extra processing", but it does NOT stop HTML5 media:
 * a video left playing keeps decoding on MediaCodec and compositing through the
 * Viz/RenderThread pipeline, burning ~0.7 of a core indefinitely while invisible
 * (measured on-device: vault idle 10.5% CPU vs 0.08% on the library).
 *
 * Note the limitation: querySelectorAll only reaches the same-origin top document,
 * so a video inside a cross-origin `<iframe>` (e.g. a youtube-nocookie embed in a
 * saved guide) is not paused by this. Full-page video (the browser tab, online
 * guide browsing) is same-origin and is covered.
 *
 * `WebView.pauseTimers()` is deliberately NOT used: it is process-global and would
 * also freeze whichever tab's WebView is currently visible.
 */
fun WebView.pauseHtmlMedia() {
    evaluateJavascript(
        "(function(){try{var m=document.querySelectorAll('video,audio');" +
            "for(var i=0;i<m.length;i++){try{m[i].pause();}catch(e){}}}catch(e){}})();",
        null,
    )
}
