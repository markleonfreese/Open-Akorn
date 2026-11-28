package com.openakorn.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureHistoryTest {
    private val reading = TelemetryReading(
        pit = ProbeReading(225, 107.2, 225, 107.2),
        probe1 = ProbeReading(70, 21.1, 160, 71.1),
        probe2 = ProbeReading(null, null, null, null),
        alarm = false,
        fanMode = "auto",
        fanPercent = null,
        frameSize = 20,
    )

    @Test
    fun throttlesSamplesAndKeepsMissingSamplesAsGaps() {
        val history = TemperatureHistory(capacity = 4, minimumIntervalMillis = 5_000)
        assertTrue(history.record(reading, 10_000))
        assertFalse(history.record(reading, 12_000))
        assertTrue(history.record(null, 15_000))
        assertEquals(2, history.snapshot().size)
        assertEquals(null, history.snapshot().last().pit)
    }

    @Test
    fun boundsSamplesAndCanStartANewConnectionSession() {
        val history = TemperatureHistory(capacity = 2, minimumIntervalMillis = 1)
        history.record(reading, 1)
        history.record(reading, 2)
        history.record(reading, 3)
        assertEquals(listOf(2L, 3L), history.snapshot().map { it.elapsedMillis })
        history.clear()
        assertTrue(history.snapshot().isEmpty())
        assertTrue(history.record(reading, 0))
    }
}
