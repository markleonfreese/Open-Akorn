package com.openakorn.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class ProtocolFixtureTest {
    @Test
    fun bluetoothDiscoveryOnlyOffersNamedAkornDevices() {
        assertEquals(true, AkornProtocol.isAkornDeviceName("Akorn-AutoKamado"))
        assertEquals(false, AkornProtocol.isAkornDeviceName("Akorn-"))
        assertEquals(false, AkornProtocol.isAkornDeviceName("Headphones"))
        assertEquals(false, AkornProtocol.isAkornDeviceName(null))
    }

    @Test
    fun sharedProtocolFixturesMatchTheNormalizedContract() {
        val root = File(requireNotNull(System.getProperty("openAkornRoot")))
        val rows = File(root, "protocol-fixtures/status.csv").readLines().drop(1)
        assertEquals(7, rows.size)

        rows.forEach { line ->
            val columns = line.split(',')
            val name = columns[0]
            val transport = columns[1]
            val frameSize = columns[2].toInt()
            val payload = AkornProtocol.fromHex(columns[3])
            if (columns[4] != "true") {
                assertThrows("$name should be rejected", IllegalArgumentException::class.java) {
                    if (transport == "ble") AkornProtocol.decodeBle(payload)
                    else AkornProtocol.decodeTcp(payload, frameSize)
                }
                return@forEach
            }

            val reading = if (transport == "ble") AkornProtocol.decodeBle(payload)
            else AkornProtocol.decodeTcp(payload, frameSize)
            assertEquals("$name pit current", nullableInt(columns[5]), reading.pit.currentF)
            assertEquals("$name pit target", nullableInt(columns[6]), reading.pit.targetF)
            assertEquals("$name probe 1 current", nullableInt(columns[7]), reading.probe1.currentF)
            assertEquals("$name probe 1 target", nullableInt(columns[8]), reading.probe1.targetF)
            assertEquals("$name probe 2 current", nullableInt(columns[9]), reading.probe2.currentF)
            assertEquals("$name probe 2 target", nullableInt(columns[10]), reading.probe2.targetF)
            assertEquals("$name alarm", columns[11].toBoolean(), reading.alarm)
            assertEquals("$name fan mode", columns[12].ifEmpty { null }, reading.fanMode)
            assertEquals("$name fan percent", nullableInt(columns[13]), reading.fanPercent)
        }
    }

    @Test
    fun celsiusConversionMatchesPythonRoundingForSharedValues() {
        val value = AkornProtocol.decodeTcp(
            AkornProtocol.fromHex("006900e110000045006003e81000100010321100"),
        )
        assertEquals(40.6, value.pit.currentC!!, 0.0)
        assertEquals(107.2, value.pit.targetC!!, 0.0)
        assertEquals(20.6, value.probe1.currentC!!, 0.0)
    }

    private fun nullableInt(value: String): Int? = value.ifEmpty { null }?.toInt()
}
