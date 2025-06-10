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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
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
    private sealed interface InternalState {
        val ordinal: Int
        val address: String

        data class Connecting(
            override val address: String,
        ) : InternalState {
            override val ordinal = 9
        }

        data class Connected(
            override val address: String,
            val isPaired: Boolean,
            val gatt: BluetoothGatt,
        ) : InternalState {
            override val ordinal = 10
        }

        data class Pairing(
            override val address: String,
            val pin: String?,
        ) : InternalState {
            override val ordinal = 11
        }

        data class Searching(
            override val address: String,
        ) : InternalState {
            override val ordinal = 8
        }

        data class Waiting(
            override val address: String,
        ) : InternalState {
            override val ordinal = 7
        }

        data class Disconnecting(
            override val address: String,
        ) : InternalState {
            override val ordinal = 6
        }

        infix fun below(newState: InternalState?): Boolean {
            if (newState == null) return false
            return ordinal < newState.ordinal
        }

        infix fun higher(newState: InternalState?): Boolean {
            if (newState == null) return true
            return ordinal > newState.ordinal
        }
    }

    private suspend fun toConnected(gatt: BluetoothGatt) {
        when (_states.value) {
            is InternalState.Connecting -> onConnect(gatt = gatt)
            else -> {
                // todo
            }
        }
    }

    private suspend fun toDisconnected(address: String) {
        when (_states.value) {
            is InternalState.Disconnecting -> onDisconnect(address = address)
            is InternalState.Pairing,
            is InternalState.Connecting,
            is InternalState.Connected -> onSearching(address = address)
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

    private val _states = MutableStateFlow<InternalState?>(null)

    private val _allStates = _states.runningFold<InternalState?, Pair<InternalState?, InternalState?>>(
        initial = null to null,
    ) { (_, oldState), newState ->
        oldState to newState
    }

    override val states = _states.map { state ->
        when (state) {
            is InternalState.Connected -> BLEGenerics.State.Connected(address = state.address, isPaired = state.isPaired)
            is InternalState.Connecting -> BLEGenerics.State.Connecting(address = state.address)
            is InternalState.Disconnecting -> BLEGenerics.State.Disconnecting(address = state.address)
            is InternalState.Pairing -> BLEGenerics.State.Pairing(address = state.address)
            is InternalState.Searching -> BLEGenerics.State.Searching(address = state.address)
            is InternalState.Waiting -> BLEGenerics.State.Waiting(address = state.address)
            null -> null
        }
    }.stateIn(coroutineScope, SharingStarted.Lazily, initialValue = null)

    private val _events = MutableSharedFlow<BLEGenerics.Event>()
    override val events = _events.asSharedFlow()

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
                is InternalState.Searching -> {
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
            is InternalState.Waiting -> {
                if (!adapter.isEnabled) return
                if (!isLocationEnabled) return
                onSearching(address = state.address)
            }
            is InternalState.Connecting,
            is InternalState.Connected -> {
                if (!adapter.isEnabled || !isLocationEnabled) {
                    _states.value = InternalState.Waiting(address = state.address)
                }
            }
            is InternalState.Searching -> {
                if (!adapter.isEnabled || !isLocationEnabled) {
                    try {
                        stopScan()
                    } catch (error: Throwable) {
                        TODO("RealBLEGenerics:fromWaiting(${state.address}):$error")
                    }
                    _states.value = InternalState.Waiting(address = state.address)
                }
            }
            else -> {
                // todo
            }
        }
    }

    private val receivers = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            println("[RealBLEGenerics]:receivers:(${intent?.action} ${intent?.extras?.keySet()?.toList()})") // todo
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

    private val receiversPairing = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            println("[RealBLEGenerics]:receivers:pairing:(${intent?.action} ${intent?.extras?.keySet()?.toList()})") // todo
            if (context == null) return
            if (intent == null) return
            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
                    when (variant) {
                        BluetoothDevice.PAIRING_VARIANT_PIN -> {
                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                ?: TODO("RealBLEGenerics:onReceive($intent):no device!")
                            val state = _states.value
                            if (state !is InternalState.Pairing) TODO("RealBLEGenerics:onReceive($intent):state $state!")
                            if (state.address != device.address) return
                            abortBroadcast()
                            val pin = state.pin ?: TODO("RealBLEGenerics:onReceive($intent):no pin!")
                            if (!device.setPin(pin.toByteArray())) TODO("RealBLEGenerics:onReceive($intent):set pin error!")
                        }
                    }
                }
            }
        }
    }
    private val intentFiltersPairing = IntentFilter().also {
        it.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
    }

    init {
        coroutineScope.launch {
            withContext(default) {
                _allStates.collect { (oldState, newState) ->
                    when (oldState) {
                        is InternalState.Pairing -> {
                            context.unregisterReceiver(receiversPairing)
                        }
                        is InternalState.Connected -> {
                            if (oldState higher newState) {
                                try {
                                    oldState.gatt.close()
                                } catch (error: Throwable) {
                                    TODO("RealBLEGenerics:init(${newState?.address}):$error")
                                }
                            }
                        }
                        else -> {
                            // noop
                        }
                    }
                    when (newState) {
                        is InternalState.Disconnecting -> {
                            context.unregisterReceiver(receivers)
                        }
                        is InternalState.Connecting -> {
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
                        is InternalState.Pairing -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                context.registerReceiver(
                                    receiversPairing,
                                    intentFiltersPairing,
                                    Context.RECEIVER_NOT_EXPORTED,
                                )
                            } else {
                                context.registerReceiver(receiversPairing, intentFiltersPairing)
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
        _states.value = InternalState.Searching(address = address)
        try {
            startScan()
        } catch (error: Throwable) {
            TODO("RealBLEGenerics:onSearching($address):$error")
        }
    }

    private suspend fun onConnect(gatt: BluetoothGatt) {
        val address = gatt.device.address
        println("[RealBLEGenerics]:onConnect($address)") // todo
        _states.value = InternalState.Connected(
            address = address,
            isPaired = gatt.device.bondState == BluetoothDevice.BOND_BONDED,
            gatt = gatt,
        )
        _events.emit(BLEGenerics.Event.OnConnect(address = address))
    }

    private suspend fun onDisconnect(address: String) {
        println("[RealBLEGenerics]:onDisconnect($address)") // todo
        _states.value = null
        _events.emit(BLEGenerics.Event.OnDisconnect(address = address))
    }

    private fun connectGatt(address: String) {
        println("[RealBLEGenerics]:connectGatt($address)") // todo
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEGenerics:connectGatt($address):no adapter!")
        if (!adapter.isEnabled) {
            throw BLEGenericsException(type = BLEGenericsException.Type.BTDisabled)
        }
        val autoConnect = false
        val transport = BluetoothDevice.TRANSPORT_LE
        val device = adapter.getRemoteDevice(address) ?: TODO("RealBLEGenerics:connectGatt($address):no device!")
        device.connectGatt(context, autoConnect, gattCallback, transport)
    }

    override fun connect(address: String) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    when (val state = _states.value) {
                        is InternalState.Searching -> {
                            if (state.address != address) TODO("RealBLEGenerics:connect($address):state: $state")
                        }
                        null -> {
                            // noop
                        }
                        else -> TODO("RealBLEGenerics:connect($address):state: $state")
                    }
                    _states.value = InternalState.Connecting(address = address)
                    try {
                        connectGatt(address = address)
                    } catch (error: BLEGenericsException) {
                        _states.value = InternalState.Waiting(address = address)
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
                        is InternalState.Connected -> {
                            _states.value = InternalState.Disconnecting(address = address)
                            try {
                                val bm = context.getSystemService(BluetoothManager::class.java)
                                when (bm.getConnectionState(state.gatt.device, BluetoothGatt.GATT)) {
                                    BluetoothGatt.STATE_DISCONNECTED -> onDisconnect(address = address)
                                    else -> state.gatt.disconnect()
                                }
                            } catch (error: Throwable) {
                                TODO("RealBLEGenerics:disconnect($address):$error")
                            }
                        }
                        is InternalState.Searching,
                        is InternalState.Waiting -> {
                            if (state.address != address) TODO("RealBLEGenerics:disconnect($address):state: $state")
                            onDisconnect(address = address)
                        }
                        else -> TODO("RealBLEGenerics:disconnect($address):state: $state")
                    }
                }
            }
        }
    }

    override fun pair(address: String, pin: String?) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    when (val state = _states.value) {
                        is InternalState.Connected -> {
                            if (state.address != address) TODO("RealBLEGenerics:pair($address):state: $state")
                            if (state.isPaired) TODO("RealBLEGenerics:pair($address):already paired!")
                        }
                        else -> TODO("RealBLEGenerics:pair($address):state: $state")
                    }
                    _states.value = InternalState.Pairing(address = address, pin = pin)
                    try {
                        val bm = context.getSystemService(BluetoothManager::class.java)
                        val adapter = bm.adapter ?: TODO("RealBLEGenerics:pair($address):no adapter!")
                        val device = adapter.getRemoteDevice(address) ?: TODO("RealBLEGenerics:pair($address):no device!")
                        when (val bondState = device.bondState) {
                            BluetoothDevice.BOND_NONE -> {
                                if (!device.createBond()) TODO("RealBLEGenerics:pair($address):create bond error!")
                            }
                            BluetoothDevice.BOND_BONDING -> TODO("RealBLEGenerics:pair($address):bond state $bondState")
                            BluetoothDevice.BOND_BONDED -> TODO("RealBLEGenerics:pair($address):already bonded!")
                            else -> TODO("RealBLEGenerics:pair($address):bond state $bondState is not supported!")
                        }
                    } catch (error: Throwable) {
                        TODO("RealBLEGenerics:pair($address):$error")
                    }
                }
            }
        }
    }
}
