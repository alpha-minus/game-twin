package com.twinlore.app.ui.fold

/** An [OverlayMemory] that holds the mode in a field. */
class FakeOverlayMemory(var on: Boolean = false) : OverlayMemory {
    override fun setOverlayMode(on: Boolean) { this.on = on }
    override suspend fun wasOverlayMode(): Boolean = on
}
