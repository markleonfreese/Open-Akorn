package com.openakorn.app

/** A foreground UI timer. It has no controller or alarm-command integration. */
class LocalCookTimer {
    private var deadlineElapsedRealtime: Long? = null

    fun start(durationMillis: Long, nowElapsedRealtime: Long) {
        require(durationMillis in 60_000L..86_400_000L) { "Timer must be between 1 minute and 24 hours" }
        deadlineElapsedRealtime = nowElapsedRealtime + durationMillis
    }

    fun cancel() {
        deadlineElapsedRealtime = null
    }

    fun remainingMillis(nowElapsedRealtime: Long): Long? =
        deadlineElapsedRealtime?.let { (it - nowElapsedRealtime).coerceAtLeast(0L) }

    fun isFinished(nowElapsedRealtime: Long): Boolean =
        deadlineElapsedRealtime?.let { nowElapsedRealtime >= it } ?: false
}
