package com.openakorn.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryStoreTest {
    @Test
    fun staleOrDisconnectedStatusNeverReturnsAReading() {
        val store = TelemetryStore(freshnessMillis = 5_000)
        val reading = AkornProtocol.decodeTcp(
            AkornProtocol.fromHex("006900e110000045006003e81000100010321100"),
        )
        store.connecting("tcp")
        assertEquals(LinkState.CONNECTING, store.snapshot(0).state)
        store.publish(reading, "tcp", demo = false, nowMillis = 1_000)
        assertEquals(LinkState.LIVE, store.snapshot(5_999).state)
        assertEquals(LinkState.STALE, store.snapshot(6_001).state)
        assertNull(store.snapshot(6_001).reading)
        store.disconnect()
        assertEquals(LinkState.DISCONNECTED, store.snapshot(6_001).state)
        assertNull(store.snapshot(6_001).reading)
    }

    @Test
    fun demoSourceIsExplicitAndDistinctFromLive() {
        val store = TelemetryStore()
        val reading = AkornProtocol.decodeTcp(
            AkornProtocol.fromHex("006900e110000045006003e81000100010321100"),
        )
        store.publish(reading, "demo", demo = true, nowMillis = 10)
        val snapshot = store.snapshot(11)
        assertEquals("demo", snapshot.source)
        assertEquals(true, snapshot.demo)
        assertEquals(LinkState.LIVE, snapshot.state)
    }

    @Test
    fun startingANewConnectionClearsAgeFromThePreviousSource() {
        val store = TelemetryStore()
        val reading = AkornProtocol.decodeTcp(
            AkornProtocol.fromHex("006900e110000045006003e81000100010321100"),
        )
        store.publish(reading, "tcp", demo = false, nowMillis = 1_000)

        store.connecting("demo", demo = true)

        val snapshot = store.snapshot(20_000)
        assertEquals(LinkState.CONNECTING, snapshot.state)
        assertNull(snapshot.ageSeconds)
        assertNull(snapshot.reading)
    }
}
