package com.twinlore.app.ui.fold

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A note left for the app: the next foreground was asked for, by name.
 *
 * The fold panel's `App` button points `StateManager` at a vault and a tab and
 * then brings the activity forward. The activity's resume runs the
 * open-last-played pass, which on the library reopens the recorded game, and
 * that pass has no way to tell a return somebody asked for from a return that
 * merely happened. So it took the panel where it wanted to go and then bounced
 * it to the library showcase of whatever was played last.
 *
 * One boolean, consumed on read, is enough to tell them apart: the panel arms
 * it as it hands over, and the pass that fires immediately afterwards stands
 * down. It is deliberately not persisted. It describes one transition that is
 * already in flight, and a stale flag surviving a process death would suppress
 * a cold start's auto-open, which is the one place the feature earns its keep.
 */
@Singleton
class FoldHandover @Inject constructor() {

    private val armed = AtomicBoolean(false)

    /** The panel is handing over deliberately. */
    fun arm() {
        armed.set(true)
    }

    /** True once per [arm]. */
    fun consume(): Boolean = armed.getAndSet(false)

    private val supportArmed = AtomicBoolean(false)

    /**
     * The panel's Support button: the offer to follow a detected game carries
     * one when the install is unlicensed, and the dialog it leads to is one
     * this window cannot show. So the panel hands over and leaves this note,
     * and the app shows the dialog on arrival. Same shape as [arm], same
     * reasons for not persisting it.
     */
    fun armSupport() {
        supportArmed.set(true)
    }

    /** True once per [armSupport]. */
    fun consumeSupport(): Boolean = supportArmed.getAndSet(false)

    private val coldStartFold = java.util.concurrent.atomic.AtomicReference<String?>(null)

    /**
     * A cold start found overlay mode on and opened [vaultId]'s session to
     * fold it straight back. The session's [OverlayRestore] reads this. Not
     * persisted, for the same reasons as [arm].
     */
    fun armColdStartFold(vaultId: String) {
        coldStartFold.set(vaultId)
    }

    /** True once, for the vault [armColdStartFold] named. */
    fun consumeColdStartFold(vaultId: String): Boolean = coldStartFold.compareAndSet(vaultId, null)
}
