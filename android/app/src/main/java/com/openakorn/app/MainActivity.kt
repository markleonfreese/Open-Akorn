package com.openakorn.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.text.InputType
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
    private lateinit var fanStatusText: TextView
    private lateinit var alarmStatusText: TextView
    private lateinit var cookTimerText: TextView
    private lateinit var temperatureGraph: TemperatureGraphView
    private val cookTimer = LocalCookTimer()
    private val temperatureHistory = TemperatureHistory()
    private var timerFinishAnnounced = false
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
    private var pendingBleDiscovery = false
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
        findViewById<Button>(R.id.bleDiscoverButton).setOnClickListener { requestBleDiscovery() }
        findViewById<Button>(R.id.startCookTimerButton).setOnClickListener { promptCookTimer() }
        findViewById<Button>(R.id.cancelCookTimerButton).setOnClickListener {
            cookTimer.cancel()
            timerFinishAnnounced = false
            renderCookTimer()
            Toast.makeText(this, R.string.timer_cancelled, Toast.LENGTH_SHORT).show()
        }
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
        fanStatusText = findViewById(R.id.fanStatusText)
        alarmStatusText = findViewById(R.id.alarmStatusText)
        cookTimerText = findViewById(R.id.cookTimerText)
        temperatureGraph = findViewById(R.id.temperatureGraph)
        ipInput = findViewById(R.id.ipInput)
        bleNameInput = findViewById(R.id.bleNameInput)
    }

    private fun startDemo() {
        stopTransportOnly()
        temperatureHistory.clear()
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
        temperatureHistory.clear()
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

    private fun requestBleDiscovery() {
        pendingBleDiscovery = true
        if (!hasBlePermissions()) {
            requestPermissions(neededBlePermissions(), BLE_PERMISSION_REQUEST)
            return
        }
        pendingBleDiscovery = false
        discoverBleDevices()
    }

    private fun discoverBleDevices() {
        stopTransportOnly()
        activeSource = "ble"
        val session = sessionGate.start()
        store.connecting("ble")
        showDetail("Scanning briefly for nearby AKORN devices. Nothing connects until you choose one.")
        render()
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            finishBleDiscovery(session, emptyMap(), "Bluetooth is unavailable or turned off.")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            finishBleDiscovery(session, emptyMap(), "Bluetooth scanning is unavailable on this device.")
            return
        }
        val found = linkedMapOf<String, BluetoothDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = try {
                    result.device.name
                } catch (_: SecurityException) {
                    null
                }
                if (!AkornProtocol.isAkornDeviceName(name)) return
                handler.post {
                    if (isCurrentSession(session, "ble")) found.putIfAbsent(name!!, result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                handler.post {
                    if (isCurrentSession(session, "ble")) {
                        finishBleDiscovery(session, emptyMap(), "Bluetooth scan failed. Check Bluetooth settings and try again.")
                    }
                }
            }
        }
        bleScanner = scanner
        bleScanCallback = callback
        try {
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(),
                callback,
            )
            val timeout = Runnable {
                if (!isCurrentSession(session, "ble")) return@Runnable
                finishBleDiscovery(session, found.toMap(), null)
            }
            bleScanTimeout = timeout
            handler.postDelayed(timeout, BLE_DISCOVERY_TIMEOUT_MS)
        } catch (_: SecurityException) {
            finishBleDiscovery(session, emptyMap(), "Bluetooth permission was not granted.")
        } catch (_: Exception) {
            finishBleDiscovery(session, emptyMap(), "Bluetooth scanning could not start.")
        }
    }

    private fun finishBleDiscovery(
        session: Long,
        devices: Map<String, BluetoothDevice>,
        failure: String?,
    ) {
        if (!isCurrentSession(session, "ble")) return
        stopBleScan()
        sessionGate.invalidate()
        activeSource = "none"
        store.disconnect("ble", demo = false)
        if (failure != null) {
            showDetail(failure)
            render()
            return
        }
        if (devices.isEmpty()) {
            showDetail("No nearby AKORN device was found. Check that it is advertising or enter its name manually.")
            render()
            return
        }
        showDetail("${devices.size} AKORN device${if (devices.size == 1) "" else "s"} found. Choose one to connect.")
        render()
        val names = devices.keys.toTypedArray()
        val selectedDevices = devices.values.toList()
        AlertDialog.Builder(this)
            .setTitle("Choose an AKORN device")
            .setItems(names) { _, index ->
                bleNameInput.setText(names[index])
                connectSelectedBleDevice(names[index], selectedDevices[index])
            }
            .setNegativeButton("Cancel") { _, _ ->
                showDetail("No device connected. Choose a device or enter its name manually.")
            }
            .show()
    }

    private fun connectSelectedBleDevice(name: String, device: BluetoothDevice) {
        if (!hasBlePermissions()) {
            pendingBleName = name
            requestPermissions(neededBlePermissions(), BLE_PERMISSION_REQUEST)
            return
        }
        stopTransportOnly()
        temperatureHistory.clear()
        activeSource = "ble"
        val session = sessionGate.start()
        store.connecting("ble")
        showDetail("Connecting to $name for read-only status. No pairing or provisioning is performed.")
        render()
        connectBle(device, session)
    }

    private fun startBle(name: String) {
        stopTransportOnly()
        temperatureHistory.clear()
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
        try {
            gatt.disconnect()
        } catch (_: SecurityException) {
            // Permissions can be revoked while an asynchronous GATT callback is pending.
        } catch (_: Exception) {
        }
        try {
            gatt.close()
        } catch (_: SecurityException) {
            // Closing remains best-effort after permission revocation.
        } catch (_: Exception) {
        }
    }

    private fun stopBleScan() {
        bleScanTimeout?.let(handler::removeCallbacks)
        bleScanTimeout = null
        val scanner = bleScanner
        val callback = bleScanCallback
        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // The permission may have been revoked between scan start and stop.
            } catch (_: Exception) {
            }
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
            if (pendingBleDiscovery) {
                pendingBleDiscovery = false
                discoverBleDevices()
                return
            }
            val name = pendingBleName
            pendingBleName = null
            name?.let { startBle(it) }
        } else {
            pendingBleDiscovery = false
            pendingBleName = null
            showDetail("Bluetooth permission was denied; no device was scanned.")
            render()
        }
    }

    private fun render() {
        renderCookTimer()
        val snapshot = store.snapshot(SystemClock.elapsedRealtime())
        val now = SystemClock.elapsedRealtime()
        if (snapshot.state == LinkState.LIVE) temperatureHistory.record(snapshot.reading, now)
        else if (snapshot.state == LinkState.DISCONNECTED || snapshot.state == LinkState.STALE) temperatureHistory.record(null, now)
        temperatureGraph.submit(temperatureHistory.snapshot())
        val stateLabel = getString(
            when (snapshot.state) {
                LinkState.WAITING -> R.string.state_waiting
                LinkState.CONNECTING -> R.string.state_connecting
                LinkState.LIVE -> R.string.state_live
                LinkState.STALE -> R.string.state_stale
                LinkState.DISCONNECTED -> R.string.state_disconnected
                LinkState.UNAVAILABLE -> R.string.state_unavailable
            },
        )
        val statusFormat = when {
            snapshot.demo -> R.string.status_simulation
            snapshot.source == "none" -> R.string.status_idle
            else -> R.string.status_live_monitor
        }
        statusText.setTextIfChanged(getString(statusFormat, stateLabel))
        val statusColor = resources.getColor(
            if (snapshot.state == LinkState.LIVE) R.color.akorn_green else R.color.akorn_amber,
            theme,
        )
        if (statusText.currentTextColor != statusColor) statusText.setTextColor(statusColor)
        val reading = snapshot.reading
        if (reading == null) {
            val unavailable = getString(R.string.temperature_unavailable)
            val targetUnavailable = getString(R.string.target_unavailable)
            val unknown = getString(R.string.unknown)
            pitCurrent.setTextIfChanged(unavailable)
            pitTarget.setTextIfChanged(targetUnavailable)
            probe1Current.setTextIfChanged(unavailable)
            probe1Target.setTextIfChanged(targetUnavailable)
            probe2Current.setTextIfChanged(unavailable)
            probe2Target.setTextIfChanged(targetUnavailable)
            fanStatusText.setTextIfChanged(unknown)
            alarmStatusText.setTextIfChanged(unknown)
            if (alarmStatusText.currentTextColor != getColor(R.color.akorn_ink)) {
                alarmStatusText.setTextColor(getColor(R.color.akorn_ink))
            }
            return
        }
        pitCurrent.setTextIfChanged(celsius(reading.pit.currentC))
        pitTarget.setTextIfChanged(getString(R.string.target_value, celsius(reading.pit.targetC)))
        probe1Current.setTextIfChanged(celsius(reading.probe1.currentC))
        probe1Target.setTextIfChanged(getString(R.string.target_value, celsius(reading.probe1.targetC)))
        probe2Current.setTextIfChanged(celsius(reading.probe2.currentC))
        probe2Target.setTextIfChanged(getString(R.string.target_value, celsius(reading.probe2.targetC)))
        val fan = when (reading.fanMode) {
            "auto" -> getString(R.string.fan_auto_reported)
            "manual" -> reading.fanPercent?.let {
                getString(R.string.fan_manual_reported, it)
            } ?: getString(R.string.fan_mode_reported, getString(R.string.manual))
            else -> getString(R.string.unknown)
        }
        fanStatusText.setTextIfChanged(fan)
        val alarmText = getString(if (reading.alarm) R.string.alarm_active else R.string.alarm_not_reported_active)
        alarmStatusText.setTextIfChanged(alarmText)
        val alarmColor = getColor(if (reading.alarm) R.color.akorn_alarm else R.color.akorn_ink)
        if (alarmStatusText.currentTextColor != alarmColor) alarmStatusText.setTextColor(alarmColor)
    }

    private fun promptCookTimer() {
        val minutesInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.timer_minutes_hint)
            setText("60")
            selectAll()
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, 0, padding, 0)
            addView(minutesInput)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.set_local_timer)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val minutes = minutesInput.text.toString().toLongOrNull()
                if (minutes == null || minutes !in 1L..1440L) {
                    Toast.makeText(this, R.string.timer_minutes_hint, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                cookTimer.start(minutes * 60_000L, SystemClock.elapsedRealtime())
                timerFinishAnnounced = false
                renderCookTimer()
            }
            .show()
    }

    private fun renderCookTimer() {
        val now = SystemClock.elapsedRealtime()
        val remaining = cookTimer.remainingMillis(now)
        if (remaining == null) {
            cookTimerText.setTextIfChanged(getString(R.string.timer_not_running))
            return
        }
        if (cookTimer.isFinished(now)) {
            cookTimerText.setTextIfChanged(getString(R.string.timer_finished))
            if (!timerFinishAnnounced) {
                timerFinishAnnounced = true
                Toast.makeText(this, R.string.timer_finished, Toast.LENGTH_LONG).show()
            }
            return
        }
        val totalSeconds = (remaining + 999L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val formatted = if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
        cookTimerText.setTextIfChanged(getString(R.string.timer_remaining, formatted))
    }

    private fun celsius(value: Double?): String = value?.let {
        getString(R.string.temperature_celsius, String.format(Locale.getDefault(), "%.1f", it))
    } ?: getString(R.string.temperature_unavailable)

    private fun showDetail(message: String) {
        detailText.setTextIfChanged(message)
    }

    private fun TextView.setTextIfChanged(value: CharSequence) {
        if (text.toString() != value.toString()) text = value
    }

    companion object {
        private const val BLE_PERMISSION_REQUEST = 4801
        private const val BLE_SCAN_TIMEOUT_MS = 12_000L
        private const val BLE_DISCOVERY_TIMEOUT_MS = 8_000L
        private const val BLE_CONNECT_TIMEOUT_MS = 15_000L
    }
}
