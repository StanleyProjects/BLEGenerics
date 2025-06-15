package sp.ax.blegenerics

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RealBLEGenerics(
    private val coroutineScope: CoroutineScope,
    private val default: CoroutineContext,
    private val context: Context,
    private val logger: BLEGenericsLogger,
) : BLEGenerics {
    private suspend fun toConnected(gatt: BluetoothGatt) {
        when (_states.value) {
            is InternalState.Connecting -> onConnect(gatt = gatt)
            else -> {
                // todo
            }
        }
    }

    private suspend fun toDisconnected(address: String) {
        when (val state = _states.value) {
            is InternalState.Connecting -> {
                _states.value = InternalState.Searching(address = address)
            }
            is InternalState.Connected -> {
                when (state.status) {
                    ConnectedStatus.Disconnecting -> {
                        onDisconnect(address = address)
                    }
                    ConnectedStatus.Idling, is ConnectedStatus.Pairing -> {
                        _states.value = InternalState.Searching(address = address)
                    }
                    else -> {
                        // noop
                    }
                }
            }
            else -> {
                // noop
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
            logger.info("gatt callback ${gatt.hashCode()} [ status: $status | new state: $newState ]")
            coroutineScope.launch {
                mutex.withLock {
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> toConnected(gatt = gatt)
                        BluetoothGatt.STATE_DISCONNECTED -> toDisconnected(address = gatt.device.address)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            coroutineScope.launch {
                mutex.withLock {
                    when (status) {
                        BluetoothGatt.GATT_SUCCESS -> {
                            _profiles.events.emit(BLEProfiles.Event.OnServices)
                        }
                    }
                }
            }
        }
    }

    private val _states = MutableStateFlow<InternalState?>(null)
    override val states = _states.map { state ->
        when (state) {
            is InternalState.Connected -> {
                when (state.status) {
                    ConnectedStatus.Disconnecting -> {
                        BLEGenerics.State.Disconnecting(address = state.address)
                    }
                    ConnectedStatus.Idling -> {
                        BLEGenerics.State.Connected(address = state.address, isPaired = state.isPaired)
                    }
                    is ConnectedStatus.Pairing -> {
                        BLEGenerics.State.Pairing(address = state.address)
                    }
                    ConnectedStatus.Unpairing -> {
                        BLEGenerics.State.Unpairing(address = state.address)
                    }
                }
            }
            is InternalState.Connecting -> BLEGenerics.State.Connecting(address = state.address)
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

    private val scanCallback = object : InternalScanCallback() {
        override fun onScanResult(address: String) {
            timeLastResult = now()
            val state = _states.value
            if (state !is InternalState.Searching) return
            if (state.address != address) return
            isEnabled = false
            connecting(address = address)
        }
    }

    private fun checkManagers(
        isBluetoothEnabled: Boolean = context.getSystemService(BluetoothManager::class.java)
            .adapter
            ?.isEnabled
            ?: false,
        isLocationEnabled: Boolean = context.getSystemService(LocationManager::class.java)
            .isProviderEnabled(LocationManager.GPS_PROVIDER),
    ) {
        logger.debug("check managers [ bt: $isBluetoothEnabled | gps: $isLocationEnabled ]")
        when (val state = _states.value) {
            is InternalState.Waiting -> {
                if (isBluetoothEnabled && isLocationEnabled) {
                    _states.value = InternalState.Searching(address = state.address)
                }
            }
            is InternalState.Connecting, is InternalState.Connected -> {
                if (!isBluetoothEnabled || !isLocationEnabled) {
                    _states.value = InternalState.Waiting(address = state.address)
                }
            }
            is InternalState.Searching -> {
                if (!isBluetoothEnabled || !isLocationEnabled) {
                    _states.value = InternalState.Waiting(address = state.address)
                }
            }
            else -> {
                // todo
            }
        }
    }

    private val receivers = object : BroadcastReceiver() {
        private suspend fun onReceive(intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> checkManagers(isBluetoothEnabled = true)
                        BluetoothAdapter.STATE_TURNING_OFF -> checkManagers(isBluetoothEnabled = false)
                    }
                }
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    val name = intent.getStringExtra(LocationManager.EXTRA_PROVIDER_NAME)
                    if (name != LocationManager.GPS_PROVIDER) return
                    val isLocationEnabled = intent.getBooleanExtra(LocationManager.EXTRA_PROVIDER_ENABLED, false)
                    checkManagers(isLocationEnabled = isLocationEnabled)
                }
            }
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            logger.info("receivers ${intent?.action} ${intent?.extras?.keySet()?.toList()}")
            if (intent == null) return
            coroutineScope.launch {
                mutex.withLock {
                    withContext(default) {
                        onReceive(intent = intent)
                    }
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
            logger.info("receivers pairing ${intent?.action} ${intent?.extras?.keySet()?.toList()}")
            if (intent == null) return
            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
                    when (variant) {
                        BluetoothDevice.PAIRING_VARIANT_PIN -> {
                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                ?: TODO("RealBLEGenerics:onReceive($intent):no device!")
                            val state = _states.value
                            if (state !is InternalState.Connected) TODO("RealBLEGenerics:onReceive($intent):state $state!")
                            if (state.status !is ConnectedStatus.Pairing) TODO("RealBLEGenerics:onReceive($intent):state $state!")
                            if (state.address != device.address) return
                            abortBroadcast()
                            val pin = state.status.pin ?: TODO("RealBLEGenerics:onReceive($intent):no pin!")
                            logger.debug("set pin ${state.address} $pin")
                            if (!device.setPin(pin.toByteArray())) TODO("RealBLEGenerics:onReceive($intent):set pin error!")
                        }
                    }
                }
            }
        }
    }
    private val intentFiltersPairing = IntentFilter().also {
        it.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        it.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
    }

//    private fun getPairingErrorOrNull(reason: Int): PairException.Error? {
//        val UNBOND_REASON_AUTH_FAILED = 1
//        val UNBOND_REASON_AUTH_REJECTED = 2
//        val UNBOND_REASON_AUTH_CANCELED = 3
//        val UNBOND_REASON_REMOVED = 9
//        return when (reason) {
//            UNBOND_REASON_AUTH_FAILED -> PairException.Error.FAILED
//            UNBOND_REASON_AUTH_REJECTED -> PairException.Error.REJECTED
//            UNBOND_REASON_AUTH_CANCELED -> PairException.Error.CANCELED
//            UNBOND_REASON_REMOVED -> PairException.Error.REMOVED
//            else -> null
//        }
//    }

    private val receiversConnected = object : BroadcastReceiver() {
        private suspend fun onReceive(intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val oldState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                    val newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: TODO("RealBLEGenerics:receivers:connected($intent):no device!")
                    logger.info("receivers connected ${device.address}: $oldState -> $newState")
                    when (newState) {
                        BluetoothDevice.BOND_NONE -> {
                            val state = _states.value ?: TODO("RealBLEGenerics:receivers:connected($intent):no state")
                            if (state.address != device.address) return
                            if (state !is InternalState.Connected) TODO("RealBLEGenerics:receivers:connected($intent):state: $state")
                            if (state.status !is ConnectedStatus.Pairing) TODO("RealBLEGenerics:receivers:connected($intent):state: $state")
                            val reasonKey = "android.bluetooth.device.extra.REASON"
                            val reason = intent.getIntExtra(reasonKey, BluetoothDevice.ERROR) // todo
                            _states.value = InternalState.Searching(address = state.address)
                            _events.emit(BLEGenerics.Event.OnPairing(address = state.address, isSuccess = false))
                        }
                        BluetoothDevice.BOND_BONDED -> {
                            val state = _states.value ?: TODO("RealBLEGenerics:receivers:connected($intent):no state")
                            if (state.address != device.address) return
                            if (state !is InternalState.Connected) TODO("RealBLEGenerics:receivers:connected($intent):state: $state")
                            when (state.status) {
                                ConnectedStatus.Idling -> if (state.isPaired) return
                                is ConnectedStatus.Pairing -> {
                                    // noop
                                }
                                else -> TODO("RealBLEGenerics:receivers:connected($intent):state: $state")
                            }
                            _states.value = state.copy(
                                isPaired = true,
                                status = ConnectedStatus.Idling,
                            )
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    when (val state = _states.value) {
                        is InternalState.Connected -> {
                            if (state.status !is ConnectedStatus.Unpairing) TODO("RealBLEGenerics:receivers:connected($intent):state: $state")
                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                ?: TODO("RealBLEGenerics:receivers:connected($intent):no device!")
                            if (state.address != device.address) return
                            _states.value = InternalState.Searching(address = state.address)
                        }
                        is InternalState.Searching -> {
                            // noop
                        }
                        else -> TODO("RealBLEGenerics:receivers:connected($intent):state: $state")
                    }
                }
            }
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            logger.info("receivers connected ${intent?.action} ${intent?.extras?.keySet()?.toList()}")
            if (intent == null) return
            coroutineScope.launch {
                mutex.withLock {
                    withContext(default) {
                        onReceive(intent = intent)
                    }
                }
            }
        }
    }
    private val intentFiltersConnected = IntentFilter().also {
        it.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        it.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
    }

    private val _profiles = object : MutableBLEProfiles {
        override val events = MutableSharedFlow<BLEProfiles.Event>()

        override fun services() {
            coroutineScope.launch {
                mutex.withLock {
                    withContext(default) {
                        val state = _states.value
                        logger.debug("profiles services ${state?.address}")
                        when (state) {
                            is InternalState.Connected -> {
                                if (state.status !is ConnectedStatus.Idling) TODO("RealBLEGenerics:profiles:services:state: $state")
                                if (!state.gatt.discoverServices()) TODO("RealBLEGenerics:profiles:services:discover services error!")
                            }
                            else -> TODO("RealBLEGenerics:profiles:services:state: $state")
                        }
                    }
                }
            }
        }
    }
    override val profiles: BLEProfiles = _profiles

    private fun onStates(oldState: InternalState?, newState: InternalState?) {
        if (oldState == null) {
            logger.info("new state: $newState")
        } else if (newState == null) {
            logger.info("old state: $oldState")
        } else if (oldState > newState) {
            val message = """
                      * $oldState
                    *
                  *
                * $newState
            """.trimIndent()
            logger.info(message)
        } else if (oldState < newState) {
            val message = """
                * $oldState
                  *
                    *
                      * $newState
            """.trimIndent()
            logger.info(message)
        } else if (oldState is InternalState.Connected && newState is InternalState.Connected) {
            if (oldState.status < newState.status) {
                val message = """
                    * $oldState
                      *
                        * $newState
                """.trimIndent()
                logger.info(message)
            } else {
                val message = """
                        * $oldState
                      *
                    * $newState
                """.trimIndent()
                logger.info(message)
            }
        } else {
            val message = """
                * $oldState
                  * $newState
            """.trimIndent()
            logger.info(message)
        }
    }

    private fun onPairing(address: String) {
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
    }

    init {
        coroutineScope.launch {
            withContext(default) {
                var state: InternalState? = null
                _states.collect { newState ->
                    val oldState = state
                    state = newState
                    onStates(oldState = oldState, newState = newState)
                    if (oldState == null && newState != null) {
                        BLEGenericsReceivers.register(context, receivers, intentFilters)
                    } else if (oldState != null && newState == null) {
                        context.unregisterReceiver(receivers)
                    }
                    if (newState is InternalState.Connected && newState.status is ConnectedStatus.Pairing) {
                        if (oldState !is InternalState.Connected || oldState.status !is ConnectedStatus.Pairing) {
                            BLEGenericsReceivers.register(context, receiversPairing, intentFiltersPairing)
                            try {
                                onPairing(address = newState.address)
                            } catch (error: Throwable) {
                                TODO("RealBLEGenerics:pair(${newState.address}):$error")
                            }
                        }
                    } else if (oldState is InternalState.Connected && oldState.status is ConnectedStatus.Pairing) {
                        context.unregisterReceiver(receiversPairing)
                    }
                    if (newState is InternalState.Connecting && newState > oldState) {
                        launch(default) {
                            val timeMax = 4.seconds
                            val timeDelay = 250.milliseconds
                            val timeStart = now()
                            logger.info("connecting start: ${Date(timeStart.inWholeMilliseconds)}")
                            while (true) {
                                val state = _states.value
                                if (state !is InternalState.Connecting) break
                                val timeDiff = now() - timeStart
                                if (timeDiff > timeMax) {
                                    logger.warning("connecting timeout: $timeDiff")
                                    _states.value = InternalState.Searching(address = state.address)
                                    break
                                }
                                delay(timeDelay)
                            }
                        }
                    } else if (oldState is InternalState.Connecting && oldState > newState) {
                        try {
                            oldState.gatt.close()
                        } catch (error: Throwable) {
                            TODO("RealBLEGenerics:init($oldState -> $newState):$error")
                        }
                    }
                    if (newState is InternalState.Connected && newState > oldState) {
                        BLEGenericsReceivers.register(context, receiversConnected, intentFiltersConnected)
                    } else if (oldState is InternalState.Connected && oldState > newState) {
                        context.unregisterReceiver(receiversConnected)
                        try {
                            oldState.gatt.close()
                        } catch (error: Throwable) {
                            TODO("RealBLEGenerics:init($oldState -> $newState):$error")
                        }
                    }
                    if (oldState !is InternalState.Searching && newState is InternalState.Searching) {
                        try {
                            startScan()
                        } catch (error: Throwable) {
                            TODO("RealBLEGenerics:init($oldState -> $newState):start scan error: $error")
                        }
                        launch(default) {
                            val timeDelay = 250.milliseconds
                            logger.info("searching start: ${Date(scanCallback.timeStart.inWholeMilliseconds)}")
                            while (true) {
                                val state = _states.value
                                if (state !is InternalState.Searching) break
                                val timeNow = now()
                                val fromStart = timeNow - scanCallback.timeStart
                                val fromLast = timeNow - scanCallback.timeLastResult
                                if (fromStart > 16.seconds || fromLast > 4.seconds) {
                                    logger.warning("searching timeout...")
                                    _states.value = InternalState.Waiting(address = state.address)
                                    break
                                }
                                delay(timeDelay)
                            }
                        }
                    } else if (oldState is InternalState.Searching && newState !is InternalState.Searching) {
                        try {
                            stopScan()
                        } catch (error: Throwable) {
                            TODO("RealBLEGenerics:init($oldState -> $newState):stop scan error: $error")
                        }
                    }
                    if (oldState !is InternalState.Waiting && newState is InternalState.Waiting) {
                        checkManagers()
                    }
                }
            }
        }
    }

    private fun now(): Duration {
        return System.currentTimeMillis().milliseconds
    }

    private fun startScan() {
        logger.debug("start scan...")
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
        scanCallback.isEnabled = true
        scanCallback.timeStart = now()
        scanCallback.timeLastResult = now()
        scanner.startScan(scanFilters, scanSettings, scanCallback)
    }

    private fun stopScan() {
        logger.debug("stop scan...")
        scanCallback.isEnabled = false
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEScanner:stopScan:no adapter!")
        if (!adapter.isEnabled) return // todo
        val scanner = adapter.bluetoothLeScanner ?: TODO("RealBLEScanner:stopScan:no scanner!")
        scanner.stopScan(scanCallback)
    }

    private suspend fun onConnect(gatt: BluetoothGatt) {
        val address = gatt.device.address
        logger.debug("on -> connect $address")
        _states.value = InternalState.Connected(
            address = address,
            isPaired = gatt.device.bondState == BluetoothDevice.BOND_BONDED,
            gatt = gatt,
            status = ConnectedStatus.Idling,
        )
        _events.emit(BLEGenerics.Event.OnConnect(address = address))
    }

    private suspend fun onDisconnect(address: String) {
        logger.debug("on -> disconnect $address")
        _states.value = null
        _events.emit(BLEGenerics.Event.OnDisconnect(address = address))
    }

    private fun connectGatt(address: String): BluetoothGatt {
        logger.debug("connect gatt $address...")
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEGenerics:connectGatt($address):no adapter!")
        if (!adapter.isEnabled) {
            throw BLEGenericsException(type = BLEGenericsException.Type.BTDisabled)
        }
        val autoConnect = false
        val transport = BluetoothDevice.TRANSPORT_LE
        val device = adapter.getRemoteDevice(address) ?: TODO("RealBLEGenerics:connectGatt($address):no device!")
        return device.connectGatt(context, autoConnect, gattCallback, transport) ?: TODO("RealBLEGenerics:connectGatt($address):no gatt!")
    }

    private fun connecting(address: String) {
        _states.value = try {
            InternalState.Connecting(address = address, gatt = connectGatt(address = address))
        } catch (error: BLEGenericsException) {
            logger.warning("on -> connecting $address error: $error")
            InternalState.Waiting(address = address)
        } catch (error: Throwable) {
            TODO("RealBLEGenerics:connect($address):$error")
        }
    }

    override fun connect(address: String) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    logger.debug("connect $address")
                    val state = _states.value
                    if (state != null) TODO("RealBLEGenerics:connect($address):state: $state")
                    connecting(address = address)
                }
            }
        }
    }

    override fun disconnect() {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    val state = _states.value
                    logger.debug("disconnect ${state?.address}")
                    when (state) {
                        is InternalState.Connected -> {
                            if (state.status !is ConnectedStatus.Idling) TODO("RealBLEGenerics:disconnect:state: $state")
                            val address = state.address
                            _states.value = state.copy(status = ConnectedStatus.Disconnecting)
                            try {
                                val bm = context.getSystemService(BluetoothManager::class.java)
                                when (bm.getConnectionState(state.gatt.device, BluetoothGatt.GATT)) {
                                    BluetoothGatt.STATE_CONNECTED -> state.gatt.disconnect()
                                    else -> onDisconnect(address = address)
                                }
                            } catch (error: Throwable) {
                                TODO("RealBLEGenerics:disconnect($address):$error")
                            }
                        }
                        is InternalState.Searching, is InternalState.Waiting -> {
                            onDisconnect(address = state.address)
                        }
                        else -> TODO("RealBLEGenerics:disconnect:state: $state")
                    }
                }
            }
        }
    }

    override fun pair(pin: String?) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    val state = _states.value
                    if (state !is InternalState.Connected) TODO("RealBLEGenerics:pair:state: $state")
                    if (state.status !is ConnectedStatus.Idling) TODO("RealBLEGenerics:pair:state: $state")
                    val address = state.address
                    logger.debug("pair $address pin: $pin")
                    if (state.isPaired) TODO("RealBLEGenerics:pair($address):already paired!")
                    _states.value = state.copy(status = ConnectedStatus.Pairing(pin = pin))
                }
            }
        }
    }

    private fun BluetoothDevice.removeBond(): Boolean {
        val result = javaClass.getMethod("removeBond").invoke(this)
        check(result is Boolean)
        return result
    }

    override fun unpair() {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    val state = _states.value
                    if (state !is InternalState.Connected) TODO("RealBLEGenerics:unpair:state: $state")
                    if (state.status !is ConnectedStatus.Idling) TODO("RealBLEGenerics:unpair:state: $state")
                    val address = state.address
                    logger.debug("unpair $address")
                    if (!state.isPaired) TODO("RealBLEGenerics:unpair($address):already unpaired!")
                    _states.value = state.copy(status = ConnectedStatus.Unpairing)
                    try {
                        val bm = context.getSystemService(BluetoothManager::class.java)
                        val adapter = bm.adapter ?: TODO("RealBLEGenerics:unpair($address):no adapter!")
                        val device = adapter.getRemoteDevice(address) ?: TODO("RealBLEGenerics:unpair($address):no device!")
                        when (val bondState = device.bondState) {
                            BluetoothDevice.BOND_NONE -> TODO("RealBLEGenerics:unpair($address):no bond!")
                            BluetoothDevice.BOND_BONDING -> TODO("RealBLEGenerics:unpair($address):bond state $bondState")
                            BluetoothDevice.BOND_BONDED -> {
                                if (!device.removeBond()) TODO("RealBLEGenerics:pair($address):remove bond error!")
                            }
                            else -> TODO("RealBLEGenerics:unpair($address):bond state $bondState is not supported!")
                        }
                    } catch (error: Throwable) {
                        TODO("RealBLEGenerics:unpair($address):$error")
                    }
                }
            }
        }
    }
}
