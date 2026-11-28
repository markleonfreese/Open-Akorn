package com.openakorn.app

import java.util.ArrayDeque

data class TemperatureSample(
    val elapsedMillis: Long,
    val pit: ProbeReading?,
    val probe1: ProbeReading?,
    val probe2: ProbeReading?,
)

/** Bounded, in-memory history for the current app connection only. */
class TemperatureHistory(
    private val capacity: Int = 720,
    private val minimumIntervalMillis: Long = 5_000L,
) {
    private val samples = ArrayDeque<TemperatureSample>()
    private var lastRecordedAt: Long? = null

    init {
        require(capacity > 0)
        require(minimumIntervalMillis > 0)
    }

    @Synchronized
    fun record(reading: TelemetryReading?, nowElapsedMillis: Long): Boolean {
        val last = lastRecordedAt
        if (last != null && nowElapsedMillis - last < minimumIntervalMillis) return false
        if (last != null && nowElapsedMillis < last) return false
        val sample = reading?.let {
            TemperatureSample(nowElapsedMillis, it.pit, it.probe1, it.probe2)
        } ?: TemperatureSample(nowElapsedMillis, null, null, null)
        samples.addLast(sample)
        while (samples.size > capacity) samples.removeFirst()
        lastRecordedAt = nowElapsedMillis
        return true
    }

    @Synchronized
    fun snapshot(): List<TemperatureSample> = samples.toList()

    @Synchronized
    fun clear() {
        samples.clear()
        lastRecordedAt = null
    }
}
