package dev.bananz0.opentvbridge.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentOpenGuardTest {
    @Test fun `duplicate is blocked until cooldown expires`() {
        var now = 1_000L
        val guard = RecentOpenGuard(8_000L) { now }
        assertTrue(guard.shouldOpen("MOVIE:tt1234567"))
        now += 7_999L
        assertFalse(guard.shouldOpen("movie TT1234567"))
        now += 1L
        assertTrue(guard.shouldOpen("movie tt1234567"))
    }

    @Test fun `different content opens immediately`() {
        val guard = RecentOpenGuard { 5_000L }
        assertTrue(guard.shouldOpen("tt1"))
        assertTrue(guard.shouldOpen("tt2"))
    }

    @Test fun `clock rollback does not permanently block`() {
        var now = 10_000L
        val guard = RecentOpenGuard { now }
        assertTrue(guard.shouldOpen("tt1"))
        now = 1_000L
        assertTrue(guard.shouldOpen("tt1"))
    }

    @Test fun `reset rearms same content`() {
        val guard = RecentOpenGuard { 1_000L }
        assertTrue(guard.shouldOpen("tt1"))
        assertFalse(guard.shouldOpen("tt1"))
        guard.reset()
        assertTrue(guard.shouldOpen("tt1"))
    }
}
