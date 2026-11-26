package com.openakorn.app

import kotlin.math.roundToInt
import java.io.EOFException
import java.io.InputStream
import java.net.SocketTimeoutException

data class ProbeReading(
    val currentF: Int?,
    val currentC: Double?,
    val targetF: Int?,
    val targetC: Double?,
)

data class TelemetryReading(
    val pit: ProbeReading,
    val probe1: ProbeReading,
    val probe2: ProbeReading,
    val alarm: Boolean,
    val fanMode: String?,
    val fanPercent: Int?,
    val frameSize: Int,
)

object AkornProtocol {
    const val STATUS_SERVICE_UUID = "0000aaaa-0000-1000-8000-00805f9b34fb"
    const val STATUS_CHARACTERISTIC_UUID = "0000bbb1-0000-1000-8000-00805f9b34fb"
    const val BLE_STATUS_HEADER_HEX = "00191400"
    const val TCP_PORT = 3333
    const val DEFAULT_FRAME_SIZE = 20
    const val MIN_BLE_MTU = 27

    private val bleHeader = byteArrayOf(0x00, 0x19, 0x14, 0x00)

    fun decodeTcp(frame: ByteArray, frameSize: Int = DEFAULT_FRAME_SIZE): TelemetryReading =
        decodeFrame(frame, frameSize)

    fun decodeBle(notification: ByteArray): TelemetryReading {
        require(notification.size == 24) { "Unexpected BLE status length" }
        require(notification.copyOfRange(0, bleHeader.size).contentEquals(bleHeader)) {
            "Unexpected BLE status header"
        }
        return decodeFrame(notification.copyOfRange(bleHeader.size, notification.size), 20)
    }

    private fun decodeFrame(frame: ByteArray, frameSize: Int): TelemetryReading {
        require(frameSize == 16 || frameSize == 20) { "Unsupported status frame size" }
        require(frame.size == frameSize) { "Unexpected status frame length" }
        require(unsigned(frame[4]) == 0 || unsigned(frame[4]) == 16) { "Unexpected status alarm byte" }
        require(unsigned(frame[5]) == 0) { "Unexpected reserved status byte" }

        val fanMode: String?
        val fanPercent: Int?
        if (frameSize == 20) {
            fanMode = when (unsigned(frame[16])) {
                0 -> "manual"
                16 -> "auto"
                else -> throw IllegalArgumentException("Unexpected reported fan mode")
            }
            fanPercent = if (fanMode == "manual") unsigned(frame[17]).also {
                require(it <= 100) { "Unexpected reported fan speed" }
            } else {
                null
            }
        } else {
            fanMode = null
            fanPercent = null
        }

        return TelemetryReading(
            pit = probe(frame, 0),
            probe1 = probe(frame, 6),
            probe2 = probe(frame, 10),
            alarm = unsigned(frame[4]) == 0,
            fanMode = fanMode,
            fanPercent = fanPercent,
            frameSize = frameSize,
        )
    }

    private fun probe(frame: ByteArray, offset: Int): ProbeReading {
        val current = temperature(readUnsignedShort(frame, offset), sentinel = 1000)
        val target = temperature(readUnsignedShort(frame, offset + 2), sentinel = 4096)
        return ProbeReading(
            currentF = current,
            currentC = celsius(current),
            targetF = target,
            targetC = celsius(target),
        )
    }

    private fun temperature(value: Int, sentinel: Int): Int? {
        if (value == sentinel) return null
        require(value in 0..750) { "Unexpected reported temperature" }
        return value
    }

    private fun celsius(value: Int?): Double? = value?.let {
        (((it - 32) * 50.0 / 9.0).roundToInt() / 10.0)
    }

    private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int =
        (unsigned(bytes[offset]) shl 8) or unsigned(bytes[offset + 1])

    private fun unsigned(value: Byte): Int = value.toInt() and 0xff

    fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex fixture must have an even length" }
        return ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}

/** Fixed-length stream reader that preserves a partial frame across socket timeouts. */
class FixedFrameReader(private val frameSize: Int) {
    private val buffer = ByteArray(frameSize)
    private var buffered = 0

    init {
        require(frameSize == 16 || frameSize == 20) { "Unsupported status frame size" }
    }

    @Throws(EOFException::class)
    fun next(input: InputStream): ByteArray? {
        while (buffered < frameSize) {
            val read = try {
                input.read(buffer, buffered, frameSize - buffered)
            } catch (_: SocketTimeoutException) {
                return null
            }
            if (read < 0) {
                if (buffered == 0) throw EOFException("Status stream closed")
                throw EOFException("Truncated status frame")
            }
            if (read == 0) continue
            buffered += read
        }
        val complete = buffer.copyOf()
        buffered = 0
        return complete
    }
}
