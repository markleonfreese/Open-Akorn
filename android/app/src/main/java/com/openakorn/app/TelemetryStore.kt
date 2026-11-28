package com.openakorn.app

enum class LinkState(val wireValue: String) {
    WAITING("waiting"),
    CONNECTING("connecting"),
    LIVE("live"),
    STALE("stale"),
    DISCONNECTED("disconnected"),
    UNAVAILABLE("unavailable"),
}

data class StatusEnvelope(
    val schemaVersion: Int = 1,
    val model: String = "akorn-auto",
    val source: String = "none",
    val demo: Boolean = false,
    val state: LinkState = LinkState.WAITING,
    val ageSeconds: Double? = null,
    val reading: TelemetryReading? = null,
)

class TelemetryStore(private val freshnessMillis: Long = 5_000L) {
    private var source = "none"
    private var demo = false
    private var state = LinkState.WAITING
    private var reading: TelemetryReading? = null
    private var lastSampleAt: Long? = null

    @Synchronized
    fun connecting(source: String, demo: Boolean = false) {
        this.source = source
        this.demo = demo
        state = LinkState.CONNECTING
        reading = null
        lastSampleAt = null
    }

    @Synchronized
    fun publish(value: TelemetryReading, source: String, demo: Boolean, nowMillis: Long) {
        this.source = source
        this.demo = demo
        reading = value
        lastSampleAt = nowMillis
        state = LinkState.LIVE
    }

    @Synchronized
    fun invalidate() {
        reading = null
        state = LinkState.UNAVAILABLE
    }

    @Synchronized
    fun disconnect(source: String = this.source, demo: Boolean = this.demo) {
        this.source = source
        this.demo = demo
        reading = null
        state = LinkState.DISCONNECTED
    }

    @Synchronized
    fun snapshot(nowMillis: Long): StatusEnvelope {
        val age = lastSampleAt?.let { ((nowMillis - it).coerceAtLeast(0L)) / 1000.0 }
        val stale = state == LinkState.LIVE && age != null && age * 1000.0 > freshnessMillis
        return StatusEnvelope(
            source = source,
            demo = demo,
            state = if (stale) LinkState.STALE else state,
            ageSeconds = age,
            reading = reading.takeUnless { stale || state != LinkState.LIVE },
        )
    }
}
