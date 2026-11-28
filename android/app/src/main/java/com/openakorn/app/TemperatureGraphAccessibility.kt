package com.openakorn.app

import java.util.Locale

/** Screen-reader description of the same current and target values plotted by the graph. */
object TemperatureGraphAccessibility {
    fun describe(samples: List<TemperatureSample>): String {
        val latest = samples.lastOrNull() ?: return "Temperature graph. No samples yet."
        val values = listOf(
            "Pit" to latest.pit,
            "Probe 1" to latest.probe1,
            "Probe 2" to latest.probe2,
        ).flatMap { (name, probe) ->
            listOfNotNull(
                probe?.currentC?.asGraphTemperature()?.let { "$name current $it degrees Celsius" },
                probe?.targetC?.asGraphTemperature()?.let { "$name target $it degrees Celsius" },
            )
        }
        return if (values.isEmpty()) {
            "Temperature graph. Latest sample has no available temperatures."
        } else {
            "Temperature graph, current session. ${values.joinToString("; ")}."
        }
    }

    private fun Double.asGraphTemperature(): String? =
        takeIf { isFinite() && this in 0.0..400.0 }
            ?.let { String.format(Locale.ROOT, "%.1f", it) }
}
