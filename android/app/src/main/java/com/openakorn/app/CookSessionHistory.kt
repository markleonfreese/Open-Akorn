package com.openakorn.app

data class CookSession(
    val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val source: String,
    val samples: List<TemperatureSample>,
)

/** Local, bounded session graph archive. Callers must pass real, fresh readings only. */
class CookSessionHistory(
    private val persist: (String) -> Unit = {},
    encoded: String? = null,
    private val maxSessions: Int = MAX_SESSIONS,
    private val maxSamplesPerSession: Int = MAX_SAMPLES_PER_SESSION,
    private val minimumIntervalMillis: Long = SAMPLE_INTERVAL_MILLIS,
) {
    private val sessions = decode(encoded).toMutableList()

    init {
        require(maxSessions > 0)
        require(maxSamplesPerSession > 0)
        require(minimumIntervalMillis > 0)
        // A process restart ends an open session at its last recorded sample.
        sessions.indices.forEach { index ->
            val session = sessions[index]
            if (session.endedAtEpochMillis == null) {
                val lastSampleAt = session.samples.lastOrNull()?.let {
                    session.startedAtEpochMillis + it.elapsedMillis
                } ?: session.startedAtEpochMillis
                sessions[index] = session.copy(endedAtEpochMillis = lastSampleAt)
            }
        }
        trimSessions()
    }

    @Synchronized
    fun record(source: String, reading: TelemetryReading, nowEpochMillis: Long, demo: Boolean): Boolean {
        if (demo) {
            finish(nowEpochMillis)
            return false
        }
        require(source == "tcp" || source == "ble")
        var active = sessions.lastOrNull()?.takeIf { it.endedAtEpochMillis == null }
        if (active != null && active.source != source) {
            finish(nowEpochMillis)
            active = null
        }
        if (active == null) {
            active = CookSession(
                id = "$nowEpochMillis-$source",
                startedAtEpochMillis = nowEpochMillis,
                endedAtEpochMillis = null,
                source = source,
                samples = emptyList(),
            )
            sessions += active
        }
        val lastSampleAt = active.samples.lastOrNull()?.let { active.startedAtEpochMillis + it.elapsedMillis }
        if (lastSampleAt != null && (nowEpochMillis < lastSampleAt || nowEpochMillis - lastSampleAt < minimumIntervalMillis)) {
            return false
        }
        if (nowEpochMillis < active.startedAtEpochMillis) return false
        val point = TemperatureSample(
            elapsedMillis = nowEpochMillis - active.startedAtEpochMillis,
            pit = reading.pit,
            probe1 = reading.probe1,
            probe2 = reading.probe2,
        )
        sessions[sessions.lastIndex] = active.copy(samples = (active.samples + point).takeLast(maxSamplesPerSession))
        trimSessions()
        save()
        return true
    }

    @Synchronized
    fun finish(nowEpochMillis: Long) {
        val index = sessions.indexOfLast { it.endedAtEpochMillis == null }
        if (index < 0) return
        val session = sessions[index]
        val lastSampleAt = session.samples.lastOrNull()?.let { session.startedAtEpochMillis + it.elapsedMillis }
            ?: session.startedAtEpochMillis
        sessions[index] = session.copy(endedAtEpochMillis = maxOf(nowEpochMillis, lastSampleAt))
        save()
    }

    @Synchronized
    fun snapshot(): List<CookSession> = sessions.map { it.copy(samples = it.samples.toList()) }

    @Synchronized
    fun clear() {
        sessions.clear()
        persist("")
    }

    private fun trimSessions() {
        while (sessions.size > maxSessions) sessions.removeAt(0)
    }

    private fun save() = persist(encode(sessions))

    companion object {
        const val MAX_SESSIONS = 8
        const val MAX_SAMPLES_PER_SESSION = 720
        const val SAMPLE_INTERVAL_MILLIS = 60_000L
        private const val FORMAT = "open-akorn-cooks-v1"

        fun encode(sessions: List<CookSession>): String = buildString {
            append(FORMAT)
            sessions.forEach { session ->
                append('\n').append(listOf(
                    "session", session.id, session.startedAtEpochMillis.toString(),
                    session.endedAtEpochMillis?.toString() ?: "~", session.source,
                ).joinToString("|"))
                session.samples.forEach { sample ->
                    append("\nsample|").append(sample.elapsedMillis)
                    listOf(
                        sample.pit.currentC, sample.pit.targetC,
                        sample.probe1.currentC, sample.probe1.targetC,
                        sample.probe2.currentC, sample.probe2.targetC,
                    ).forEach { append('|').append(it.encodeNumber()) }
                }
            }
        }

        fun decode(encoded: String?): List<CookSession> {
            if (encoded.isNullOrBlank() || encoded.length > 1_500_000) return emptyList()
            return try {
                val lines = encoded.lineSequence().iterator()
                if (!lines.hasNext() || lines.next() != FORMAT) return emptyList()
                val output = mutableListOf<CookSession>()
                var current: CookSession? = null
                while (lines.hasNext()) {
                    val fields = lines.next().split('|')
                    when (fields.firstOrNull()) {
                        "session" -> {
                            current?.let(output::add)
                            if (fields.size != 5) return emptyList()
                            val start = fields[2].toLongOrNull()?.takeIf { it >= 0 } ?: return emptyList()
                            val end = fields[3].takeUnless { it == "~" }?.toLongOrNull()
                            if (fields[3] != "~" && (end == null || end < start)) return emptyList()
                            if (fields[4] !in setOf("tcp", "ble")) return emptyList()
                            current = CookSession(fields[1], start, end, fields[4], emptyList())
                        }
                        "sample" -> {
                            val session = current ?: return emptyList()
                            if (fields.size != 8) return emptyList()
                            val offset = fields[1].toLongOrNull()?.takeIf { it >= 0 } ?: return emptyList()
                            val numbers = fields.drop(2).map { it.decodeNumber() }
                            fun probe(index: Int) = ProbeReading(
                                currentF = null,
                                currentC = numbers[index],
                                targetF = null,
                                targetC = numbers[index + 1],
                            )
                            current = session.copy(samples = session.samples + TemperatureSample(
                                elapsedMillis = offset,
                                pit = probe(0),
                                probe1 = probe(2),
                                probe2 = probe(4),
                            ))
                        }
                        else -> return emptyList()
                    }
                    if (output.size > MAX_SESSIONS) return emptyList()
                    if ((current?.samples?.size ?: 0) > MAX_SAMPLES_PER_SESSION) return emptyList()
                }
                current?.let(output::add)
                if (output.size > MAX_SESSIONS || output.any { it.samples.size > MAX_SAMPLES_PER_SESSION }) emptyList() else output
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun Double?.encodeNumber(): String = this?.takeIf { it.isFinite() }?.toString() ?: "~"
        private fun String.decodeNumber(): Double? = takeUnless { it == "~" }?.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
