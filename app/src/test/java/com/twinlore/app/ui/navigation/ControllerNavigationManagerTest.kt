package com.twinlore.app.ui.navigation

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as mockWhen

/**
 * Unit tests for ControllerNavigationManager
 * Test Strategy: Unit tests for key event handling, input filtering, and event delegation to navigation components
 */
class ControllerNavigationManagerTest {

    private lateinit var manager: ControllerNavigationManager

    @Before
    fun setup() {
        manager = ControllerNavigationManager()
    }

    @Test
    fun `isNavigationKey returns true for D-pad keys`() {
        assertTrue(manager.isNavigationKey(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(manager.isNavigationKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertTrue(manager.isNavigationKey(KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(manager.isNavigationKey(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(manager.isNavigationKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(manager.isNavigationKey(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test
    fun `isNavigationKey returns false for non-navigation keys`() {
        assertFalse(manager.isNavigationKey(KeyEvent.KEYCODE_BACK))
        assertFalse(manager.isNavigationKey(KeyEvent.KEYCODE_HOME))
        assertFalse(manager.isNavigationKey(KeyEvent.KEYCODE_MENU))
    }

    @Test
    fun `isDirectionKey returns true for D-pad direction keys`() {
        assertTrue(manager.isDirectionKey(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(manager.isDirectionKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertTrue(manager.isDirectionKey(KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(manager.isDirectionKey(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun `isDirectionKey returns false for action keys`() {
        assertFalse(manager.isDirectionKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(manager.isDirectionKey(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test
    fun `isActionKey returns true for confirm keys`() {
        assertTrue(manager.isActionKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(manager.isActionKey(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test
    fun `isActionKey returns false for direction keys`() {
        assertFalse(manager.isActionKey(KeyEvent.KEYCODE_DPAD_UP))
        assertFalse(manager.isActionKey(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun `getDirection returns correct direction for D-pad keys`() {
        assertEquals(NavigationDirection.UP, manager.getDirection(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(NavigationDirection.DOWN, manager.getDirection(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(NavigationDirection.LEFT, manager.getDirection(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(NavigationDirection.RIGHT, manager.getDirection(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun `getDirection returns null for non-direction keys`() {
        assertNull(manager.getDirection(KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(manager.getDirection(KeyEvent.KEYCODE_BUTTON_A))
        assertNull(manager.getDirection(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun `handleKeyEvent returns Navigate action for D-pad direction`() {
        val keyEvent = mock(KeyEvent::class.java)
        mockWhen(keyEvent.action).thenReturn(KeyEvent.ACTION_DOWN)
        mockWhen(keyEvent.keyCode).thenReturn(KeyEvent.KEYCODE_DPAD_UP)
        
        val action = manager.handleKeyEvent(keyEvent)
        
        assertNotNull(action)
        assertTrue(action is NavigationAction.Navigate)
        assertEquals(NavigationDirection.UP, (action as NavigationAction.Navigate).direction)
    }

    @Test
    fun `handleKeyEvent returns Confirm action for action keys`() {
        val keyEvent = mock(KeyEvent::class.java)
        mockWhen(keyEvent.action).thenReturn(KeyEvent.ACTION_DOWN)
        mockWhen(keyEvent.keyCode).thenReturn(KeyEvent.KEYCODE_DPAD_CENTER)
        
        val action = manager.handleKeyEvent(keyEvent)
        
        assertNotNull(action)
        assertTrue(action is NavigationAction.Confirm)
    }

    @Test
    fun `handleKeyEvent returns null for ACTION_UP`() {
        val keyEvent = mock(KeyEvent::class.java)
        mockWhen(keyEvent.action).thenReturn(KeyEvent.ACTION_UP)
        mockWhen(keyEvent.keyCode).thenReturn(KeyEvent.KEYCODE_DPAD_UP)
        
        val action = manager.handleKeyEvent(keyEvent)
        
        assertNull(action)
    }

    @Test
    fun `handleKeyEvent returns null for non-navigation keys`() {
        val keyEvent = mock(KeyEvent::class.java)
        mockWhen(keyEvent.action).thenReturn(KeyEvent.ACTION_DOWN)
        mockWhen(keyEvent.keyCode).thenReturn(KeyEvent.KEYCODE_BACK)
        
        val action = manager.handleKeyEvent(keyEvent)
        
        assertNull(action)
    }

    @Test
    fun `handleKeyEvent handles all D-pad directions`() {
        val directions = mapOf(
            KeyEvent.KEYCODE_DPAD_UP to NavigationDirection.UP,
            KeyEvent.KEYCODE_DPAD_DOWN to NavigationDirection.DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to NavigationDirection.LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to NavigationDirection.RIGHT
        )
        
        directions.forEach { (keyCode, expectedDirection) ->
            val keyEvent = mock(KeyEvent::class.java)
            mockWhen(keyEvent.action).thenReturn(KeyEvent.ACTION_DOWN)
            mockWhen(keyEvent.keyCode).thenReturn(keyCode)
            
            val action = manager.handleKeyEvent(keyEvent)
            
            assertNotNull("Action should not be null for $keyCode", action)
            assertTrue("Action should be Navigate for $keyCode", action is NavigationAction.Navigate)
            assertEquals("Direction should match for $keyCode", expectedDirection, (action as NavigationAction.Navigate).direction)
        }
    }

    @Test
    fun `handleKeyEvent handles both action keys`() {
        val actionKeys = listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A)
        
        actionKeys.forEach { keyCode ->
            val keyEvent = mock(KeyEvent::class.java)
            mockWhen(keyEvent.action).thenReturn(KeyEvent.ACTION_DOWN)
            mockWhen(keyEvent.keyCode).thenReturn(keyCode)
            
            val action = manager.handleKeyEvent(keyEvent)
            
            assertNotNull("Action should not be null for $keyCode", action)
            assertTrue("Action should be Confirm for $keyCode", action is NavigationAction.Confirm)
        }
    }
}
