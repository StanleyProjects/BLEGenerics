package sp.ax.blegenerics

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class RealBLEGenerics(
    private val coroutineScope: CoroutineScope,
    private val default: CoroutineContext,
    private val context: Context,
) : BLEGenerics {
    private suspend fun toConnected(gatt: BluetoothGatt) {
        when (_states.value) {
            is BLEGenerics.State.Connecting -> onConnect(gatt = gatt)
            else -> {
                // todo
            }
        }
    }

    private suspend fun toDisconnected(address: String) {
        when (_states.value) {
            is BLEGenerics.State.Disconnecting -> onDisconnect(address = address)
            is BLEGenerics.State.Connecting, is BLEGenerics.State.Connected -> onSearching(address = address)
            else -> {
                // todo
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int,
        ) {
            if (gatt == null) TODO("RealBLEGenerics:callback:no gatt!")
            println("[RealBLEGenerics]:onConnectionStateChange(${gatt.hashCode()}, $status, $newState)") // todo
            coroutineScope.launch {
                mutex.withLock {
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> toConnected(gatt = gatt)
                        BluetoothGatt.STATE_DISCONNECTED -> toDisconnected(address = gatt.device.address)
                    }
                }
            }
        }
    }

    private val _states = MutableStateFlow<BLEGenerics.State?>(null)
    override val states = _states.asStateFlow()

    private val _events = MutableSharedFlow<BLEGenerics.Event>()
    override val events = _events.asSharedFlow()

    private var gatt: BluetoothGatt? = null

    private val mutex = Mutex()

    private val scanSettings = ScanSettings
        .Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
        .setReportDelay(0L)
        .build()
    private val scanFilters = listOf(ScanFilter.Builder().build())
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val address = result?.device?.address ?: return
            println("[RealBLEGenerics]:onScanResult($address)") // todo
            when (val state = _states.value) {
                is BLEGenerics.State.Searching -> {
                    if (state.address != address) return
                    connect(address = address)
                    try {
                        stopScan()
                    } catch (error: Throwable) {
                        TODO("RealBLEGenerics:onScanResult($address)")
                    }
                }
                else -> {
                    // noop
                }
            }
        }
    }

    private fun fromWaiting() {
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEGenerics:fromWaiting:no adapter!")
        val lm = context.getSystemService(LocationManager::class.java)
        val isLocationEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        println("[RealBLEGenerics]:fromWaiting(${adapter.isEnabled}, $isLocationEnabled)") // todo
        when (val state = _states.value) {
            is BLEGenerics.State.Waiting -> {
                if (!adapter.isEnabled) return
                if (!isLocationEnabled) return
                onSearching(address = state.address)
            }
            is BLEGenerics.State.Connected, is BLEGenerics.State.Connecting -> {
                if (!adapter.isEnabled || !isLocationEnabled) {
                    try {
                        gatt?.close()
                    } catch (error: Throwable) {
                        TODO("RealBLEGenerics:fromWaiting(${state.address}):$error")
                    }
                    gatt = null
                    _states.value = BLEGenerics.State.Waiting(address = state.address)
                }
            }
            is BLEGenerics.State.Searching -> {
                if (!adapter.isEnabled || !isLocationEnabled) {
                    try {
                        stopScan()
                    } catch (error: Throwable) {
                        TODO("RealBLEGenerics:fromWaiting(${state.address}):$error")
                    }
                    _states.value = BLEGenerics.State.Waiting(address = state.address)
                }
            }
            else -> {
                // todo
            }
        }
    }

    private val receivers = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            println("[RealBLEGenerics]:onReceive(${intent?.action} ${intent?.extras?.keySet()?.toList()})") // todo
            if (context == null) return
            if (intent == null) return
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> fromWaiting()
                        BluetoothAdapter.STATE_TURNING_OFF -> fromWaiting()
                    }
                }
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    val name = intent.getStringExtra(LocationManager.EXTRA_PROVIDER_NAME)
                    if (name != LocationManager.GPS_PROVIDER) return
                    fromWaiting()
                }
            }
        }
    }
    private val intentFilters = IntentFilter().also {
        it.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        it.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
    }

    init {
        coroutineScope.launch {
            withContext(default) {
                states.collect { state ->
                    when (state) {
                        is BLEGenerics.State.Disconnecting -> {
                            context.unregisterReceiver(receivers)
                        }
                        is BLEGenerics.State.Connecting -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                context.registerReceiver(
                                    receivers,
                                    intentFilters,
                                    Context.RECEIVER_NOT_EXPORTED,
                                )
                            } else {
                                context.registerReceiver(receivers, intentFilters)
                            }
                        }
                        else -> {
                            // noop
                        }
                    }
                }
            }
        }
    }

    private fun startScan() {
        println("[RealBLEGenerics]:start scan...") // todo
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEGenerics:startScan:no adapter!")
        if (!adapter.isEnabled) TODO("RealBLEGenerics:startScan:adapter disabled!")
        val lm = context.getSystemService(LocationManager::class.java)
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) TODO("RealBLEGenerics:startScan:gps disabled!")
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("no permission: ${Manifest.permission.ACCESS_FINE_LOCATION}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("no permission: ${Manifest.permission.BLUETOOTH_SCAN}")
            }
        }
        val scanner = adapter.bluetoothLeScanner ?: TODO("RealBLEGenerics:startScan:no scanner!")
        scanner.startScan(scanFilters, scanSettings, scanCallback)
    }

    private fun stopScan() {
        println("[RealBLEGenerics]:stop scan...") // todo
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEScanner:stopScan:no adapter!")
        if (!adapter.isEnabled) return // todo
        val scanner = adapter.bluetoothLeScanner ?: TODO("RealBLEScanner:stopScan:no scanner!")
        scanner.stopScan(scanCallback)
    }

    private fun onSearching(address: String) {
        println("[RealBLEGenerics]:onSearching($address)") // todo
        _states.value = BLEGenerics.State.Searching(address = address)
        try {
            startScan()
        } catch (error: Throwable) {
            TODO("RealBLEGenerics:onSearching($address):$error")
        }
    }

    private suspend fun onConnect(gatt: BluetoothGatt) {
        println("[RealBLEGenerics]:onConnect(${gatt.device.address})") // todo
        val address = gatt.device.address
        this.gatt = gatt
        _states.value = BLEGenerics.State.Connected(
            address = address,
            isPaired = gatt.device.bondState == BluetoothDevice.BOND_BONDED,
        )
        _events.emit(BLEGenerics.Event.OnConnect(address = address))
    }

    private suspend fun onDisconnect(address: String) {
        println("[RealBLEGenerics]:onDisconnect($address)") // todo
        gatt = null
        _states.value = null
        _events.emit(BLEGenerics.Event.OnDisconnect(address = address))
    }

    override fun connect(address: String) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    when (val state = _states.value) {
                        is BLEGenerics.State.Searching -> {
                            if (state.address != address) TODO("RealBLEGenerics:connect($address):state: $state")
                        }
                        null -> {
                            // noop
                        }
                        else -> TODO("RealBLEGenerics:connect($address):state: $state")
                    }
                    _states.value = BLEGenerics.State.Connecting(address = address)
                    try {
                        val bm = context.getSystemService(BluetoothManager::class.java)
                        val adapter = bm.adapter ?: TODO("RealBLEGenerics:connect($address):no adapter!")
                        if (!adapter.isEnabled) TODO("RealBLEGenerics:connect($address):adapter disabled!")
                        val autoConnect = false
                        val transport = BluetoothDevice.TRANSPORT_LE
                        val device = adapter.getRemoteDevice(address) ?: TODO("RealBLEGenerics:connect($address):no device!")
                        device.connectGatt(context, autoConnect, gattCallback, transport)
                    } catch (error: Throwable) {
                        TODO("RealBLEGenerics:connect($address):$error")
                    }
                }
            }
        }
    }

    override fun disconnect(address: String) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    when (val state = _states.value) {
                        is BLEGenerics.State.Searching, is BLEGenerics.State.Connected -> {
                            if (state.address != address) TODO("RealBLEGenerics:disconnect($address):state: $state")
                        }
                        else -> TODO("RealBLEGenerics:disconnect($address):state: $state")
                    }
                    val gatt = gatt ?: TODO("RealBLEGenerics:disconnect($address):no gatt!")
                    _states.value = BLEGenerics.State.Disconnecting(address = address)
                    try {
                        val bm = context.getSystemService(BluetoothManager::class.java)
                        when (bm.getConnectionState(gatt.device, BluetoothGatt.GATT)) {
                            BluetoothGatt.STATE_DISCONNECTED -> onDisconnect(address = address)
                            else -> gatt.disconnect()
                        }
                    } catch (error: Throwable) {
                        TODO("RealBLEGenerics:disconnect($address):$error")
                    }
                }
            }
        }
    }
}
