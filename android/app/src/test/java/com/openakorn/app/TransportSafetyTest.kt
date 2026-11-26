package com.openakorn.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.net.SocketTimeoutException

class TransportSafetyTest {
    @Test
    fun explicitPrivateIpv4Only() {
        assertArrayEquals(byteArrayOf(10, 1, 2, 3), PrivateAddress.parse("10.1.2.3"))
        assertArrayEquals(byteArrayOf(172.toByte(), 31, 2, 3), PrivateAddress.parse("172.31.2.3"))
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 2, 3), PrivateAddress.parse("192.168.2.3"))
        listOf("127.0.0.1", "169.254.1.2", "172.32.0.1", "192.169.0.1", "8.8.8.8", "grill.local", "10.0.0.256")
            .forEach { assertNull("$it must not be resolved or connected", PrivateAddress.parse(it)) }
    }

    @Test
    fun frameReaderPreservesPartialFramesAcrossTimeoutsAndCoalescing() {
        val frame = AkornProtocol.fromHex("006900e110000045006003e81000100010321100")
        val reader = FixedFrameReader(20)
        val intermittent = object : InputStream() {
            private var cursor = 0
            private var step = 0
            override fun read(): Int = -1

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (step++ == 0) {
                    frame.copyInto(buffer, offset, 0, 7)
                    cursor = 7
                    return 7
                }
                if (step == 2) throw SocketTimeoutException("simulated timeout")
                val count = frame.size - cursor
                frame.copyInto(buffer, offset, cursor, frame.size)
                cursor = frame.size
                return count
            }
        }
        assertNull(reader.next(intermittent))
        assertArrayEquals(frame, reader.next(intermittent))

        val coalesced = ByteArrayInputStream(frame + frame)
        val secondReader = FixedFrameReader(20)
        assertArrayEquals(frame, secondReader.next(coalesced))
        assertArrayEquals(frame, secondReader.next(coalesced))
        try {
            secondReader.next(ByteArrayInputStream(frame.copyOfRange(0, 3)))
            throw AssertionError("truncated stream must fail")
        } catch (_: EOFException) {
            // Expected: partial bytes must never be returned as a valid status frame.
        }
    }
}
