package com.twinlore.app.ui.screens

import org.json.JSONObject

/**
 * The script that scrolls a loaded guide to the element a `#fragment` names.
 *
 * The fragment is whatever the tapped link carried, so it is untrusted. It is
 * quoted once as a JSON string literal (a valid JS string literal) and the
 * attribute selector goes through CSS.escape, so no fragment can close the
 * literal and run as code.
 */
fun anchorScrollScript(fragment: String): String {
    val id = JSONObject.quote(fragment)
    val userContentId = JSONObject.quote("user-content-$fragment")
    return "(function(){var f=$id;" +
        "var e=document.getElementById(f)" +
        "||document.getElementById(($userContentId).toLowerCase())" +
        "||document.querySelector('[name=\"'+CSS.escape(f)+'\"]');" +
        "if(e){e.scrollIntoView(true);return true;}return false;})()"
}
