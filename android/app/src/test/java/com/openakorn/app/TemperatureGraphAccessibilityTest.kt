package com.openakorn.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureGraphAccessibilityTest {
    @Test
    fun describesCurrentAndTargetValuesShownByTheGraph() {
        val sample = TemperatureSample(
            elapsedMillis = 10_000,
            pit = ProbeReading(225, 107.2, 250, 121.1),
            probe1 = ProbeReading(70, 21.1, 160, 71.1),
            probe2 = ProbeReading(null, null, null, null),
        )

        assertEquals(
            "Temperature graph, current session. Pit current 107.2 degrees Celsius; " +
                "Pit target 121.1 degrees Celsius; Probe 1 current 21.1 degrees Celsius; " +
                "Probe 1 target 71.1 degrees Celsius.",
            TemperatureGraphAccessibility.describe(listOf(sample)),
        )
    }

    @Test
    fun omitsMissingAndUnplottedValues() {
        val sample = TemperatureSample(
            elapsedMillis = 10_000,
            pit = ProbeReading(null, null, 250, 121.1),
            probe1 = ProbeReading(70, 21.1, null, null),
            probe2 = ProbeReading(null, null, null, null),
        )

        val summary = TemperatureGraphAccessibility.describe(listOf(sample))
        assertTrue(summary.contains("Pit target 121.1 degrees Celsius"))
        assertTrue(summary.contains("Probe 1 current 21.1 degrees Celsius"))
        assertFalse(summary.contains("Probe 2"))
    }

    @Test
    fun providesUsefulEmptyAndUnavailableDescriptions() {
        assertEquals(
            "Temperature graph. No samples yet.",
            TemperatureGraphAccessibility.describe(emptyList()),
        )
        val missing = TemperatureSample(10_000, null, null, null)
        assertEquals(
            "Temperature graph. Latest sample has no available temperatures.",
            TemperatureGraphAccessibility.describe(listOf(missing)),
        )
    }

    @Test
    fun omitsValuesOutsideThePlottedRange() {
        val sample = TemperatureSample(
            elapsedMillis = 10_000,
            pit = ProbeReading(225, Double.NaN, 250, 121.1),
            probe1 = ProbeReading(70, 21.1, null, null),
            probe2 = ProbeReading(null, null, null, null),
        )

        val summary = TemperatureGraphAccessibility.describe(listOf(sample))
        assertTrue(summary.contains("Pit target 121.1 degrees Celsius"))
        assertFalse(summary.contains("Pit current"))
    }
}
