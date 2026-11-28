package com.openakorn.app

import kotlin.math.roundToInt

/** Local-only simulated telemetry for exercising the dashboard without a grill. */
data class DemoScenario(
    val pitC: Double,
    val pitTargetC: Double,
    val probe1C: Double,
    val probe1TargetC: Double,
    val probe2C: Double,
    val probe2TargetC: Double,
    val probe1Disconnected: Boolean = false,
    val probe2Disconnected: Boolean = false,
    val alarmActive: Boolean = false,
) {
    init {
        listOf(pitC, pitTargetC, probe1C, probe1TargetC, probe2C, probe2TargetC).forEach {
            require(it.isFinite() && it in MIN_C..MAX_C) {
                "Simulated temperature must be between $MIN_C and $MAX_C °C"
            }
        }
    }

    fun applyTo(base: TelemetryReading): TelemetryReading = base.copy(
        pit = probe(pitC, pitTargetC, disconnected = false),
        probe1 = probe(probe1C, probe1TargetC, probe1Disconnected),
        probe2 = probe(probe2C, probe2TargetC, probe2Disconnected),
        alarm = alarmActive,
    )

    companion object {
        const val MIN_C = -17.8
        const val MAX_C = 398.9

        fun from(reading: TelemetryReading): DemoScenario = DemoScenario(
            pitC = reading.pit.currentC ?: 40.6,
            pitTargetC = reading.pit.targetC ?: 107.2,
            probe1C = reading.probe1.currentC ?: 20.6,
            probe1TargetC = reading.probe1.targetC ?: 35.6,
            probe2C = reading.probe2.currentC ?: 20.6,
            probe2TargetC = reading.probe2.targetC ?: 35.6,
            probe1Disconnected = reading.probe1.currentC == null,
            probe2Disconnected = reading.probe2.currentC == null,
            alarmActive = reading.alarm,
        )

        private fun probe(
            currentC: Double,
            targetC: Double,
            disconnected: Boolean,
        ): ProbeReading {
            if (disconnected) return ProbeReading(null, null, null, null)
            val currentF = fahrenheit(currentC)
            val targetF = fahrenheit(targetC)
            return ProbeReading(currentF, celsius(currentF), targetF, celsius(targetF))
        }

        private fun fahrenheit(celsius: Double): Int = (celsius * 9 / 5 + 32).roundToInt()

        private fun celsius(fahrenheit: Int): Double =
            (((fahrenheit - 32) * 50.0 / 9.0).roundToInt() / 10.0)
    }
}
