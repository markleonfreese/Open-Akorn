package com.openakorn.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportSessionGateTest {
    @Test
    fun startingANewTransportInvalidatesCallbacksFromThePreviousOne() {
        val gate = TransportSessionGate()
        val tcpSession = gate.start()

        val bleSession = gate.start()

        assertFalse(gate.isCurrent(tcpSession))
        assertTrue(gate.isCurrent(bleSession))
    }

    @Test
    fun disconnectInvalidatesCallbacksAlreadyQueuedForTheCurrentTransport() {
        val gate = TransportSessionGate()
        val session = gate.start()

        gate.invalidate()

        assertFalse(gate.isCurrent(session))
    }
}
