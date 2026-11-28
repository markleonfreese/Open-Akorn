package com.openakorn.app

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/** One explicit, read-only TCP session. Retry is a user action, never a command replay. */
class TcpMonitor(
    private val privateIpv4: ByteArray,
    private val port: Int = AkornProtocol.TCP_PORT,
    private val frameSize: Int = AkornProtocol.DEFAULT_FRAME_SIZE,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 2_000,
) {
    @Volatile private var socket: Socket? = null
    private val stopped = AtomicBoolean(false)

    init {
        require(privateIpv4.size == 4) { "An explicit IPv4 address is required" }
        require(frameSize == 16 || frameSize == 20) { "Unsupported status frame size" }
    }

    fun run(
        shouldStop: () -> Boolean,
        onReading: (TelemetryReading) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        if (stopped.get() || shouldStop()) return
        val connection = Socket()
        socket = connection
        try {
            if (stopped.get() || shouldStop()) return
            connection.connect(
                InetSocketAddress(InetAddress.getByAddress(privateIpv4), port),
                connectTimeoutMillis,
            )
            connection.soTimeout = readTimeoutMillis
            val input = connection.getInputStream()
            val frameReader = FixedFrameReader(frameSize)
            while (!isStopped(shouldStop)) {
                val frame = frameReader.next(input) ?: continue
                onReading(AkornProtocol.decodeTcp(frame, frameSize))
            }
        } catch (_: Exception) {
            if (!isStopped(shouldStop)) onUnavailable()
        } finally {
            try { connection.close() } catch (_: Exception) { }
            if (socket === connection) socket = null
        }
    }

    fun stop() {
        stopped.set(true)
        try { socket?.close() } catch (_: Exception) { }
    }

    private fun isStopped(shouldStop: () -> Boolean): Boolean = stopped.get() || shouldStop()
}
