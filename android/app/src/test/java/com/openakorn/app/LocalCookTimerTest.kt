package com.openakorn.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCookTimerTest {
    @Test
    fun countsDownUsingMonotonicElapsedTimeAndFinishesAtZero() {
        val timer = LocalCookTimer()
        timer.start(120_000L, 50_000L)

        assertEquals(90_000L, timer.remainingMillis(80_000L))
        assertFalse(timer.isFinished(169_999L))
        assertEquals(0L, timer.remainingMillis(170_000L))
        assertTrue(timer.isFinished(170_000L))
    }

    @Test
    fun cancelClearsTimer() {
        val timer = LocalCookTimer()
        timer.start(60_000L, 0L)
        timer.cancel()
        assertNull(timer.remainingMillis(1L))
        assertFalse(timer.isFinished(60_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTimerOutsideSupportedRange() {
        LocalCookTimer().start(59_999L, 0L)
    }
}
