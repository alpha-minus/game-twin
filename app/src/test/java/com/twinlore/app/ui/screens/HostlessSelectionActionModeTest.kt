package com.twinlore.app.ui.screens

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HostlessSelectionActionModeTest {

    private class Recorder(private val accept: Boolean = true) : ActionMode.Callback {
        var created = 0
        var destroyed = 0
        var clicked: Int? = null
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            created++
            menu.add(Menu.NONE, 7, 1, "Copy")
            return accept
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = true
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            clicked = item.itemId; return true
        }
        override fun onDestroyActionMode(mode: ActionMode) { destroyed++ }
    }

    private val anchor = View(ApplicationProvider.getApplicationContext())

    @Test
    fun `starts as a floating mode carrying the callback's menu`() {
        val cb = Recorder()
        val mode = HostlessSelectionActionMode(anchor, cb).start()
        assertNotNull(mode)
        assertEquals(ActionMode.TYPE_FLOATING, mode!!.type)
        assertEquals("Copy", mode.menu.findItem(7).title)
        assertEquals(1, cb.created)
    }

    @Test
    fun `a declined callback gives no mode, as the platform does`() {
        assertNull(HostlessSelectionActionMode(anchor, Recorder(accept = false)).start())
    }

    @Test
    fun `finish destroys once`() {
        val cb = Recorder()
        val mode = HostlessSelectionActionMode(anchor, cb).start()!!
        mode.finish()
        mode.finish()
        assertEquals(1, cb.destroyed)
    }
}
