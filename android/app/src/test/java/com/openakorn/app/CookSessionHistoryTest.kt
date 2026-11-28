package com.openakorn.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CookSessionHistoryTest {
    @Test
    fun recordsOneBoundedSamplePerIntervalAndFinishesOnDisconnect() {
        var saved = ""
        val history = CookSessionHistory(persist = { saved = it })

        assertFalse(history.record("tcp", reading(50.0), 1_000, demo = true))
        assertTrue(history.record("tcp", reading(100.0), 1_000, demo = false))
        assertFalse(history.record("tcp", reading(110.0), 30_000, demo = false))
        assertTrue(history.record("tcp", reading(120.0), 61_000, demo = false))
        history.finish(80_000)

        val session = history.snapshot().single()
        assertEquals("tcp", session.source)
        assertEquals(1_000L, session.startedAtEpochMillis)
        assertEquals(80_000L, session.endedAtEpochMillis)
        assertEquals(2, session.samples.size)
        assertEquals(60_000L, session.samples.last().elapsedMillis)
        assertEquals(120.0, session.samples.last().pit?.currentC!!, 0.0)
        assertTrue(saved.startsWith("open-akorn-cooks-v1"))
    }

    @Test
    fun sourceChangeStartsSeparateSessionAndRestoredOpenSessionEndsAtLastSample() {
        var saved = ""
        val history = CookSessionHistory(persist = { saved = it }, minimumIntervalMillis = 1)
        history.record("tcp", reading(100.0), 10_000, demo = false)
        history.record("ble", reading(120.0), 20_000, demo = false)
        val records = CookSessionHistory.decode(saved)
        assertEquals(2, records.size)
        assertEquals(20_000L, records.first().endedAtEpochMillis)
        assertEquals("ble", records.last().source)
        assertNull(records.last().endedAtEpochMillis)

        val restored = CookSessionHistory(encoded = saved)
        assertEquals(20_000L, restored.snapshot().last().endedAtEpochMillis)
    }

    @Test
    fun storageCodecPreservesNullProbeValuesAndRejectsMalformedData() {
        val reading = reading(100.0).copy(probe1 = ProbeReading(null, null, null, null))
        val history = CookSessionHistory(minimumIntervalMillis = 1)
        history.record("ble", reading, 5_000, demo = false)
        val decoded = CookSessionHistory.decode(CookSessionHistory.encode(history.snapshot())).single()

        assertNull(decoded.samples.single().probe1?.currentC)
        assertEquals(100.0, decoded.samples.single().pit?.currentC!!, 0.0)
        assertTrue(CookSessionHistory.decode("unknown-format").isEmpty())
        assertTrue(CookSessionHistory.decode("open-akorn-cooks-v1\\nsession|bad").isEmpty())
    }

    @Test
    fun clearDeletesAllSessionsAndRetentionIsCapped() {
        var saved = ""
        val history = CookSessionHistory(persist = { saved = it }, maxSessions = 2, minimumIntervalMillis = 1)
        history.record("tcp", reading(100.0), 1_000, demo = false)
        history.finish(2_000)
        history.record("ble", reading(110.0), 3_000, demo = false)
        history.finish(4_000)
        history.record("tcp", reading(120.0), 5_000, demo = false)
        assertEquals(2, history.snapshot().size)
        assertEquals(3_000L, history.snapshot().first().startedAtEpochMillis)

        history.clear()
        assertTrue(history.snapshot().isEmpty())
        assertEquals("", saved)
    }

    private fun reading(pitC: Double): TelemetryReading {
        fun probe(current: Double?) = ProbeReading(
            currentF = null,
            currentC = current,
            targetF = null,
            targetC = current?.plus(10),
        )
        return TelemetryReading(
            pit = probe(pitC),
            probe1 = probe(pitC - 20),
            probe2 = probe(pitC - 10),
            alarm = false,
            fanMode = "auto",
            fanPercent = null,
            frameSize = 20,
        )
    }
}
