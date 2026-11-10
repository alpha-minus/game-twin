package com.twinlore.app.ui.screens.guide

/**
 * What the window hosting a guide viewer can put on screen.
 *
 * Measured on the Thor, SDK 33, in the fold panel's own overlay window: Popup
 * and DropdownMenu compose fine, because a TYPE_APPLICATION_PANEL sub-window
 * accepts an overlay window as its parent token. Dialog, AlertDialog and
 * ModalBottomSheet do not, because android.app.Dialog asks for
 * TYPE_APPLICATION (2) and createWindowContext will not grant it: "Window type
 * mismatch. Window Context's window type is 2038, while LayoutParams' type is
 * set to 2".
 *
 * Text selection was assumed to be the same problem by another route, on the
 * grounds that a floating ActionMode asks its host for an application window
 * token. Measured on the Thor, SDK 33, in the panel's own overlay window: a
 * long press selects, the floating toolbar appears, and nothing throws. The
 * flag stays because it is the honest shape of the question, but the overlay
 * answers true to it.
 */
data class GuideViewerCapabilities(
    /** Dialogs, sheets, and the menus built from them. */
    val subWindows: Boolean = true,
    /**
     * Long-press selection, which opens an ActionMode.
     *
     * True in the overlay too. See the note above: the reason it was false
     * turned out not to be a reason.
     */
    val textSelection: Boolean = true,
    /**
     * Whether a link may leave the reader: the Browser tab for http(s), and
     * `startActivity` for `intent:`, `mailto:`, `tel:` and the video hosts.
     *
     * False in the fold panel, and that is a safety property rather than a
     * limitation. Game Twin is backgrounded behind a running game there, so a
     * link that starts an activity drops the player out of their game and into
     * YouTube, and a link routed to the Browser tab reaches a null
     * `mainViewModel` and does nothing at all. Both are worse than saying so.
     * With this false every leaving branch is handed to the viewer's
     * `onExternalLink` instead, and the host decides what to do about it.
     */
    val externalNavigation: Boolean = true,
) {
    companion object {
        /** A normal screen in the app. Everything works. */
        val ACTIVITY = GuideViewerCapabilities()

        /** The fold panel's TYPE_APPLICATION_OVERLAY window. */
        val OVERLAY = GuideViewerCapabilities(
            subWindows = false,
            textSelection = true,
            externalNavigation = false,
        )
    }
}
