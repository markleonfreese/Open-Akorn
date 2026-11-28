package com.openakorn.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScenarioTest {
    private val fixture = AkornProtocol.decodeTcp(
        AkornProtocol.fromHex("006900e110000045006003e81000100010321100"),
    )

    @Test
    fun appliesConfiguredCelsiusTemperaturesAndAlarmToSyntheticReading() {
        val scenario = DemoScenario(
            pitC = 100.0,
            pitTargetC = 121.1,
            probe1C = 20.6,
            probe1TargetC = 35.6,
            probe2C = 75.0,
            probe2TargetC = 90.0,
            alarmActive = true,
        )

        val reading = scenario.applyTo(fixture)

        assertEquals(212, reading.pit.currentF)
        assertEquals(100.0, reading.pit.currentC!!, 0.0)
        assertEquals(250, reading.pit.targetF)
        assertEquals(121.1, reading.pit.targetC!!, 0.0)
        assertEquals(20.6, reading.probe1.currentC!!, 0.0)
        assertEquals(75.0, reading.probe2.currentC!!, 0.0)
        assertTrue(reading.alarm)
        assertEquals(fixture.fanMode, reading.fanMode)
    }

    @Test
    fun configuredProbeDisconnectsClearCurrentAndTarget() {
        val reading = DemoScenario(
            pitC = 100.0,
            pitTargetC = 121.1,
            probe1C = 20.6,
            probe1TargetC = 35.6,
            probe2C = 20.6,
            probe2TargetC = 35.6,
            probe2Disconnected = true,
        ).applyTo(fixture)

        assertNull(reading.probe2.currentC)
        assertNull(reading.probe2.targetC)
        assertFalse(reading.alarm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTemperaturesOutsideProtocolRange() {
        DemoScenario(399.0, 121.1, 20.6, 35.6, 20.6, 35.6)
    }
}
