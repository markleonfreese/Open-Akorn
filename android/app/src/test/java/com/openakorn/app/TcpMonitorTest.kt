package com.openakorn.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TcpMonitorTest {
    @Test
    fun readsFragmentedAndCoalescedFramesFromLocalReadOnlyEndpoint() {
        val fixture = AkornProtocol.fromHex("006900e110000045006003e81000100010321100")
        val listener = ServerSocket(0)
        val accepted = CountDownLatch(1)
        val stop = AtomicBoolean(false)
        val readings = AtomicInteger(0)
        val values = mutableListOf<TelemetryReading>()
        val unavailable = AtomicInteger(0)
        val endpointError = AtomicBoolean(false)

        val endpoint = Thread {
            try {
                listener.accept().use { connection ->
                    accepted.countDown()
                    connection.getOutputStream().use { output ->
                        output.write(fixture, 0, 7)
                        output.flush()
                        Thread.sleep(10)
                        output.write(fixture, 7, fixture.size - 7)
                        output.write(fixture + fixture)
                        output.flush()
                    }
                }
            } catch (_: Exception) {
                endpointError.set(true)
            } finally {
                listener.close()
            }
        }
        endpoint.start()

        val monitor = TcpMonitor(
            privateIpv4 = byteArrayOf(127, 0, 0, 1),
            port = listener.localPort,
            connectTimeoutMillis = 2_000,
            readTimeoutMillis = 100,
        )
        val consumer = Thread {
            monitor.run(
                shouldStop = { stop.get() },
                onReading = { reading ->
                    synchronized(values) { values.add(reading) }
                    if (readings.incrementAndGet() == 3) stop.set(true)
                },
                onUnavailable = { unavailable.incrementAndGet() },
            )
        }
        consumer.start()

        assertTrue("endpoint did not accept TCP session", accepted.await(3, TimeUnit.SECONDS))
        consumer.join(3_000)
        endpoint.join(3_000)
        monitor.stop()

        assertTrue("TCP monitor did not stop", !consumer.isAlive)
        assertTrue("fake endpoint did not stop", !endpoint.isAlive)
        assertTrue("fake endpoint failed", !endpointError.get())
        assertEquals(3, readings.get())
        assertEquals(0, unavailable.get())
        synchronized(values) {
            assertEquals(listOf(40.6, 40.6, 40.6), values.map { it.pit.currentC })
        }
    }

    @Test
    fun invalidFrameMakesSessionUnavailableWithoutPublishingAReading() {
        val listener = ServerSocket(0)
        val unavailable = CountDownLatch(1)
        val readings = AtomicInteger(0)
        val endpoint = Thread {
            listener.accept().use { connection ->
                connection.getOutputStream().use {
                    val invalid = ByteArray(20).also { frame -> frame[5] = 1 }
                    it.write(invalid)
                }
            }
            listener.close()
        }
        endpoint.start()
        TcpMonitor(byteArrayOf(127, 0, 0, 1), listener.localPort, connectTimeoutMillis = 2_000)
            .run(
                shouldStop = { false },
                onReading = { readings.incrementAndGet() },
                onUnavailable = { unavailable.countDown() },
            )
        endpoint.join(3_000)

        assertTrue("invalid frame was not reported unavailable", unavailable.await(0, TimeUnit.MILLISECONDS))
        assertEquals(0, readings.get())
    }

    @Test
    fun stoppingAConnectedSessionClosesTheSocketWithoutRetrying() {
        val listener = ServerSocket(0)
        val accepted = CountDownLatch(1)
        val stopSignal = CountDownLatch(1)
        val unavailable = AtomicInteger(0)
        val endpoint = Thread {
            listener.accept().use {
                accepted.countDown()
                stopSignal.await(3, TimeUnit.SECONDS)
            }
            listener.close()
        }
        endpoint.start()
        val monitor = TcpMonitor(
            privateIpv4 = byteArrayOf(127, 0, 0, 1),
            port = listener.localPort,
            connectTimeoutMillis = 2_000,
            readTimeoutMillis = 2_000,
        )
        val consumer = Thread {
            monitor.run(
                shouldStop = { stopSignal.count == 0L },
                onReading = { throw AssertionError("no fixture was sent") },
                onUnavailable = { unavailable.incrementAndGet() },
            )
        }
        consumer.start()
        assertTrue("endpoint did not accept TCP session", accepted.await(3, TimeUnit.SECONDS))
        stopSignal.countDown()
        monitor.stop()
        consumer.join(3_000)
        endpoint.join(3_000)

        assertTrue("stopped monitor did not exit", !consumer.isAlive)
        assertTrue("fake endpoint did not exit", !endpoint.isAlive)
        assertEquals(0, unavailable.get())
    }

    @Test
    fun stopBeforeWorkerStartsPreventsTheConnection() {
        val listener = ServerSocket(0).apply { soTimeout = 150 }
        val monitor = TcpMonitor(byteArrayOf(127, 0, 0, 1), listener.localPort)
        monitor.stop()
        monitor.run(
            shouldStop = { false },
            onReading = { throw AssertionError("no status frame should arrive") },
            onUnavailable = { throw AssertionError("a stopped monitor should not report an error") },
        )
        try {
            listener.accept().close()
            throw AssertionError("stopped monitor unexpectedly opened a TCP session")
        } catch (_: java.net.SocketTimeoutException) {
            // Expected: a lifecycle stop that races worker startup must not connect.
        } finally {
            listener.close()
        }
    }
}
