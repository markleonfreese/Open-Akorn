package com.openakorn.app

import java.util.concurrent.atomic.AtomicLong

/** Rejects callbacks queued by a transport after a newer session has started or the app disconnected. */
internal class TransportSessionGate {
    private val generation = AtomicLong(0L)

    fun start(): Long = generation.incrementAndGet()

    fun invalidate(): Long = generation.incrementAndGet()

    fun isCurrent(session: Long): Boolean = generation.get() == session
}
