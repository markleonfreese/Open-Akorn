package com.openakorn.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val store = TelemetryStore()
    private val sessionGate = TransportSessionGate()
    private val serviceUuid = UUID.fromString(AkornProtocol.STATUS_SERVICE_UUID)
    private val statusUuid = UUID.fromString(AkornProtocol.STATUS_CHARACTERISTIC_UUID)
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var pitCurrent: TextView
    private lateinit var pitTarget: TextView
    private lateinit var probe1Current: TextView
    private lateinit var probe1Target: TextView
    private lateinit var probe2Current: TextView
    private lateinit var probe2Target: TextView
    private lateinit var fanAlarmText: TextView
    private lateinit var ipInput: EditText
    private lateinit var bleNameInput: EditText

    @Volatile private var tcpMonitor: TcpMonitor? = null
    @Volatile private var bleGatt: BluetoothGatt? = null
    @Volatile private var bleSession = 0L
    private var tcpStop = AtomicBoolean(true)
    private var bleScanner: BluetoothLeScanner? = null
    private var bleScanCallback: ScanCallback? = null
    private var bleScanTimeout: Runnable? = null
    private var pendingBleName: String? = null
    private var demoReading: TelemetryReading? = null
    @Volatile private var activeSource = "none"

    private val renderTick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1_000L)
        }
    }

    private val demoTick = object : Runnable {
        override fun run() {
            demoReading?.let {
                store.publish(it, source = "demo", demo = true, nowMillis = SystemClock.elapsedRealtime())
                render()
                handler.postDelayed(this, 1_000L)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!isCurrentGatt(gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failGatt(gatt, "Bluetooth connection ended. Tap Connect to retry.")
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                handler.post {
                    if (isCurrentGatt(gatt)) showDetail("Bluetooth connected; checking the read-only status service…")
                }
                val requested = try {
                    gatt.requestMtu(64)
                } catch (_: SecurityException) {
                    false
                }
                if (!requested) {
                    failGatt(gatt, "Could not request a large enough Bluetooth status packet. Try Wi-Fi TCP.")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post {
                    if (bleGatt === gatt) {
                        bleGatt = null
                        store.disconnect("ble", demo = false)
                        showDetail("Bluetooth disconnected. Tap Connect to retry.")
                        closeGatt(gatt)
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!isCurrentGatt(gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS || mtu < AkornProtocol.MIN_BLE_MTU) {
                failGatt(gatt, "The controller did not negotiate the packet size needed for status notifications.")
                return
            }
            if (!hasBlePermissions()) {
                failGatt(gatt, "Bluetooth permission was revoked.")
                return
            }
            try {
                if (!gatt.discoverServices()) {
                    failGatt(gatt, "Could not inspect the Bluetooth status service.")
                }
            } catch (_: SecurityException) {
                failGatt(gatt, "Bluetooth permission was revoked.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!isCurrentGatt(gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failGatt(gatt, "Could not inspect the Bluetooth status service.")
                return
            }
            if (!hasBlePermissions()) {
                failGatt(gatt, "Bluetooth permission was revoked.")
                return
            }
            try {
                val service = gatt.getService(serviceUuid)
                val characteristic = service?.getCharacteristic(statusUuid)
                val descriptor = characteristic?.getDescriptor(cccdUuid)
                if (characteristic == null || descriptor == null ||
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0
                ) {
                    failGatt(gatt, "The expected read-only AKORN status characteristic was not found.")
                    return
                }
                if (!gatt.setCharacteristicNotification(characteristic, true)) {
                    failGatt(gatt, "Could not subscribe to the AKORN status notification.")
                    return
                }
                val accepted = if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                        BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                if (!accepted) failGatt(gatt, "Could not enable read-only status notifications.")
            } catch (_: SecurityException) {
                failGatt(gatt, "Bluetooth permission was revoked.")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!isCurrentGatt(gatt)) return
            if (descriptor.uuid == cccdUuid && status != BluetoothGatt.GATT_SUCCESS) {
                failGatt(gatt, "Could not enable read-only status notifications.")
            } else if (descriptor.uuid == cccdUuid) {
                handler.post {
                    if (isCurrentGatt(gatt)) showDetail("Subscribed to status only. Waiting for a fresh reading…")
                }
            }
        }

        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleNotification(gatt, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(gatt, value)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        findViewById<Button>(R.id.demoButton).setOnClickListener { startDemo() }
        findViewById<Button>(R.id.disconnectButton).setOnClickListener { stopCurrentTransport("Disconnected.") }
        findViewById<Button>(R.id.tcpButton).setOnClickListener { startTcpFromInput() }
        findViewById<Button>(R.id.bleButton).setOnClickListener { startBleFromInput() }
        render()
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(renderTick)
        handler.post(renderTick)
    }

    override fun onStop() {
        handler.removeCallbacks(renderTick)
        stopCurrentTransport("The app paused and disconnected. Reconnect when it is open.")
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private fun bindViews() {
        statusText = findViewById(R.id.statusText)
        detailText = findViewById(R.id.detailText)
        pitCurrent = findViewById(R.id.pitCurrent)
        pitTarget = findViewById(R.id.pitTarget)
        probe1Current = findViewById(R.id.probe1Current)
        probe1Target = findViewById(R.id.probe1Target)
        probe2Current = findViewById(R.id.probe2Current)
        probe2Target = findViewById(R.id.probe2Target)
        fanAlarmText = findViewById(R.id.fanAlarmText)
        ipInput = findViewById(R.id.ipInput)
        bleNameInput = findViewById(R.id.bleNameInput)
    }

    private fun startDemo() {
        stopTransportOnly()
        activeSource = "demo"
        store.connecting("demo", demo = true)
        demoReading = try {
            val row = assets.open("status.csv").bufferedReader().useLines { lines ->
                lines.drop(1).first { it.startsWith("akorn_auto_tcp,") }
            }
            val columns = row.split(',')
            AkornProtocol.decodeTcp(AkornProtocol.fromHex(columns[3]), columns[2].toInt())
        } catch (_: Exception) {
            store.invalidate()
            showDetail("The bundled demo fixture could not be read.")
            render()
            return
        }
        handler.removeCallbacks(demoTick)
        handler.post(demoTick)
        showDetail("SIMULATION — shared protocol fixture; no grill is connected.")
        render()
    }

    private fun startTcpFromInput() {
        val rawAddress = ipInput.text.toString().trim()
        val addressBytes = PrivateAddress.parse(rawAddress)
        if (addressBytes == null) {
            Toast.makeText(this, "Enter your grill's private IPv4 address.", Toast.LENGTH_SHORT).show()
            return
        }
        startTcp(addressBytes)
    }

    private fun startTcp(address: ByteArray) {
        stopTransportOnly()
        activeSource = "tcp"
        val session = sessionGate.start()
        val stop = AtomicBoolean(false)
        tcpStop = stop
        val monitor = TcpMonitor(address)
        tcpMonitor = monitor
        store.connecting("tcp")
        showDetail("Connecting to the selected private address on TCP 3333…")
        render()
        Thread({
            monitor.run(
                shouldStop = { stop.get() },
                onReading = { reading ->
                    handler.post {
                        if (!stop.get() && activeSource == "tcp" && sessionGate.isCurrent(session)) {
                            store.publish(reading, "tcp", demo = false, nowMillis = SystemClock.elapsedRealtime())
                            showDetail("Read-only TCP status • frame 20 bytes • local connection")
                            render()
                        }
                    }
                },
                onUnavailable = {
                    handler.post {
                        if (stop.get() || activeSource != "tcp" || !sessionGate.isCurrent(session)) return@post
                        store.disconnect("tcp", demo = false)
                        showDetail("TCP is unavailable or the status frame was invalid. Check the cold-grill guide, then tap Connect to retry.")
                        render()
                    }
                }
            )
            if (tcpMonitor === monitor) tcpMonitor = null
        }, "open-akorn-tcp").apply {
            isDaemon = true
            start()
        }
    }

    private fun startBleFromInput() {
        val name = bleNameInput.text.toString().trim()
        if (!name.startsWith("Akorn-") || name.length <= "Akorn-".length) {
            Toast.makeText(this, "Enter the exact Akorn-* name shown by your controller.", Toast.LENGTH_SHORT).show()
            return
        }
        pendingBleName = name
        if (!hasBlePermissions()) {
            requestPermissions(neededBlePermissions(), BLE_PERMISSION_REQUEST)
            return
        }
        pendingBleName = null
        startBle(name)
    }

    private fun startBle(name: String) {
        stopTransportOnly()
        activeSource = "ble"
        val session = sessionGate.start()
        store.connecting("ble")
        showDetail("Scanning briefly for the exact device name. No pairing or provisioning is performed.")
        render()
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            store.disconnect("ble", demo = false)
            showDetail("Bluetooth is unavailable or turned off.")
            render()
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            store.disconnect("ble", demo = false)
            showDetail("Bluetooth scanning is unavailable on this device.")
            render()
            return
        }
        bleScanner = scanner
        val callback = createBleScanCallback(session, name)
        bleScanCallback = callback
        try {
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
                callback,
            )
            val timeout = Runnable {
                if (!isCurrentSession(session, "ble")) return@Runnable
                stopBleScan()
                if (bleGatt == null) {
                    store.disconnect("ble", demo = false)
                    showDetail("No matching device was found. Check the exact Akorn-* name and try again.")
                    render()
                }
            }
            bleScanTimeout = timeout
            handler.postDelayed(timeout, BLE_SCAN_TIMEOUT_MS)
        } catch (_: SecurityException) {
            stopBleScan()
            store.disconnect("ble", demo = false)
            showDetail("Bluetooth permission was not granted.")
        } catch (_: Exception) {
            stopBleScan()
            store.disconnect("ble", demo = false)
            showDetail("Bluetooth scanning could not start.")
        }
        render()
    }

    private fun createBleScanCallback(session: Long, wantedName: String): ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = try {
                result.device.name
            } catch (_: SecurityException) {
                null
            }
            if (name != wantedName) return
            handler.post {
                if (!isCurrentSession(session, "ble") || bleGatt != null) return@post
                stopBleScan()
                connectBle(result.device, session)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            handler.post {
                if (!isCurrentSession(session, "ble")) return@post
                stopBleScan()
                store.disconnect("ble", demo = false)
                showDetail("Bluetooth scan failed. Check permissions and try again.")
                render()
            }
        }
    }

    private fun connectBle(device: BluetoothDevice, session: Long) {
        if (!isCurrentSession(session, "ble")) return
        showDetail("Connecting to the selected Bluetooth device…")
        try {
            val gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            bleSession = session
            bleGatt = gatt
            handler.postDelayed({
                if (isCurrentSession(session, "ble") && bleGatt === gatt &&
                    store.snapshot(SystemClock.elapsedRealtime()).reading == null
                ) {
                    failGatt(gatt, "Bluetooth did not produce a fresh status reading. Tap Connect to retry.")
                }
            }, BLE_CONNECT_TIMEOUT_MS)
        } catch (_: SecurityException) {
            if (isCurrentSession(session, "ble")) {
                store.disconnect("ble", demo = false)
                showDetail("Bluetooth permission was revoked.")
                render()
            }
        } catch (_: Exception) {
            if (isCurrentSession(session, "ble")) {
                store.disconnect("ble", demo = false)
                showDetail("The selected Bluetooth device could not be opened.")
                render()
            }
        }
    }

    private fun handleNotification(gatt: BluetoothGatt, value: ByteArray) {
        try {
            val reading = AkornProtocol.decodeBle(value)
            handler.post {
                if (isCurrentGatt(gatt)) {
                    store.publish(reading, "ble", demo = false, nowMillis = SystemClock.elapsedRealtime())
                    showDetail("Read-only Bluetooth status • fresh notification received")
                    render()
                }
            }
        } catch (_: IllegalArgumentException) {
            handler.post {
                if (isCurrentGatt(gatt)) {
                    store.invalidate()
                    showDetail("An invalid status notification arrived; the previous reading was cleared.")
                    render()
                }
            }
        }
    }

    private fun failGatt(gatt: BluetoothGatt, message: String) {
        handler.post {
            if (isCurrentGatt(gatt)) {
                bleGatt = null
                store.disconnect("ble", demo = false)
                showDetail(message)
                closeGatt(gatt)
                render()
            }
        }
    }

    private fun closeGatt(gatt: BluetoothGatt) {
        try { gatt.disconnect() } catch (_: Exception) { }
        try { gatt.close() } catch (_: Exception) { }
    }

    private fun stopBleScan() {
        bleScanTimeout?.let(handler::removeCallbacks)
        bleScanTimeout = null
        val scanner = bleScanner
        val callback = bleScanCallback
        if (scanner != null && callback != null) {
            try { scanner.stopScan(callback) } catch (_: Exception) { }
        }
        bleScanner = null
        bleScanCallback = null
    }

    private fun stopTransportOnly() {
        sessionGate.invalidate()
        handler.removeCallbacks(demoTick)
        demoReading = null
        tcpStop.set(true)
        tcpMonitor?.stop()
        tcpMonitor = null
        stopBleScan()
        val gatt = bleGatt
        bleGatt = null
        bleSession = 0L
        if (gatt != null) closeGatt(gatt)
    }

    private fun isCurrentSession(session: Long, source: String): Boolean =
        activeSource == source && sessionGate.isCurrent(session)

    private fun isCurrentGatt(gatt: BluetoothGatt): Boolean =
        activeSource == "ble" && bleGatt === gatt && sessionGate.isCurrent(bleSession)

    private fun stopCurrentTransport(message: String) {
        stopTransportOnly()
        val previous = activeSource
        activeSource = "none"
        store.disconnect(previous, demo = previous == "demo")
        showDetail(message)
        render()
    }

    private fun hasBlePermissions(): Boolean = neededBlePermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun neededBlePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Deprecated("Permission callback is retained for API 29+ compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != BLE_PERMISSION_REQUEST) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            val name = pendingBleName
            pendingBleName = null
            name?.let { startBle(it) }
        } else {
            pendingBleName = null
            showDetail("Bluetooth permission was denied; no device was scanned.")
            render()
        }
    }

    private fun render() {
        val snapshot = store.snapshot(SystemClock.elapsedRealtime())
        val prefix = if (snapshot.demo) "SIMULATION • " else if (snapshot.source == "none") "" else "LIVE MONITOR • "
        statusText.text = prefix + snapshot.state.wireValue.replaceFirstChar { it.uppercase() }
        statusText.setTextColor(
            resources.getColor(
                if (snapshot.state == LinkState.LIVE) R.color.akorn_green else R.color.akorn_amber,
                theme,
            ),
        )
        val reading = snapshot.reading
        if (reading == null) {
            pitCurrent.text = "— °C"
            pitTarget.text = "Target —"
            probe1Current.text = "— °C"
            probe1Target.text = "Target —"
            probe2Current.text = "— °C"
            probe2Target.text = "Target —"
            fanAlarmText.text = "Reported fan: unknown  •  Alarm: unknown"
            return
        }
        pitCurrent.text = celsius(reading.pit.currentC)
        pitTarget.text = "Target ${celsius(reading.pit.targetC)}"
        probe1Current.text = celsius(reading.probe1.currentC)
        probe1Target.text = "Target ${celsius(reading.probe1.targetC)}"
        probe2Current.text = celsius(reading.probe2.currentC)
        probe2Target.text = "Target ${celsius(reading.probe2.targetC)}"
        val fan = when (reading.fanMode) {
            "auto" -> "auto (reported)"
            "manual" -> "manual${reading.fanPercent?.let { " $it%" } ?: ""} (reported)"
            else -> "unknown"
        }
        fanAlarmText.text = "Reported fan: $fan  •  Alarm: ${if (reading.alarm) "ACTIVE" else "not reported active"}"
    }

    private fun celsius(value: Double?): String = value?.let {
        String.format(Locale.getDefault(), "%.1f °C", it)
    } ?: "— °C"

    private fun showDetail(message: String) {
        detailText.text = message
    }

    companion object {
        private const val BLE_PERMISSION_REQUEST = 4801
        private const val BLE_SCAN_TIMEOUT_MS = 12_000L
        private const val BLE_CONNECT_TIMEOUT_MS = 15_000L
    }
}
