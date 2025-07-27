package sp.ax.blegenerics

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
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
            else -> { /* noop */ }
        }
    }

    private suspend fun toDisconnected(address: String) {
        when (val state = _states.value) {
            is InternalState.Connecting -> {
                _states.value = InternalState.Searching(address = address)
            }
            is InternalState.Disconnecting -> {
                onDisconnect(address = address)
            }
            is InternalState.Connected -> {
                when (state.status) {
                    is ConnectedStatus.Idling,
                    is ConnectedStatus.Pairing,
                    -> {
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
            // https://stackoverflow.com/a/45235163
            val textStatus = when (status) {
                BluetoothGatt.GATT_SUCCESS -> "success"
                0x08 -> "connection timeout"
                0x13 -> "disconnected by device ${gatt.device.address}"
                0x16 -> "disconnected by local host"
//                62, 133 -> "device not found"
                else -> status.toString()
            }
            val textNewState = when (newState) {
                BluetoothGatt.STATE_CONNECTED -> "connected"
                BluetoothGatt.STATE_DISCONNECTED -> "disconnected"
                else -> newState.toString()
            }
            logger.info("gatt callback [ status: $textStatus | new state: $textNewState ]")
            coroutineScope.launch {
                mutex.withLock {
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> toConnected(gatt = gatt)
                        BluetoothGatt.STATE_DISCONNECTED -> {
                            toDisconnected(address = gatt.device.address)
                            try {
                                gatt.close()
                            } catch (error: Throwable) {
                                logger.warning("close gatt error: $error")
                            }
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            coroutineScope.launch {
                mutex.withLock {
                    when (status) {
                        BluetoothGatt.GATT_SUCCESS -> {
                            if (gatt == null) TODO("RealBLEGenerics:onServicesDiscovered($status):no gatt!")
                            val characteristics = gatt.services.associate { s ->
                                s.uuid to s.characteristics.map { c -> c.uuid }.toSet()
                            }
                            val event = BLEProfiles.Event.OnServices(characteristics = characteristics)
                            _profiles.emit(event)
                        }
                        else -> {
                            logger.warning("on services discovered: [ status: $status ]")
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            coroutineScope.launch {
                mutex.withLock {
                    when (status) {
                        BluetoothGatt.GATT_SUCCESS -> {
                            val event = BLEProfiles.Event.OnMtuChanged(value = mtu)
                            _profiles.emit(event)
                        }
                        else -> {
                            logger.warning("on MTU changed: [ status: $status | mtu: $mtu ]")
                        }
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int,
        ) {
            coroutineScope.launch {
                mutex.withLock {
                    when (status) {
                        BluetoothGatt.GATT_SUCCESS -> {
                            if (descriptor == null) TODO("RealBLEGenerics:onDescriptorWrite($status):no descriptor!")
                            val event = BLEProfiles.Event.Descriptors.OnWrite(
                                service = descriptor.characteristic.service.uuid,
                                characteristic = descriptor.characteristic.uuid,
                                descriptor = descriptor.uuid,
                                result = Result.success(descriptor.value.copyOf()),
                            )
                            _profiles.emit(event)
                        }
                        else -> {
                            logger.warning("on write: [ status: $status | descriptor: ${descriptor?.uuid} ]")
                        }
                    }
                }
            }
        }

        @Deprecated("?")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
        ) {
            if (gatt == null) return
            if (characteristic == null) return
            onCharacteristicChanged(
                gatt = gatt,
                characteristic = characteristic,
                value = characteristic.value.copyOf(),
            )
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            coroutineScope.launch {
                mutex.withLock {
                    val event = BLEProfiles.Event.Characteristics.OnChange(
                        service = characteristic.service.uuid,
                        characteristic = characteristic.uuid,
                        bytes = value,
                    )
                    _profiles.events.emit(event)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            coroutineScope.launch {
                mutex.withLock {
                    when (status) {
                        BluetoothGatt.GATT_SUCCESS -> {
                            if (characteristic == null) TODO("RealBLEGenerics:onDescriptorWrite($status):no descriptor!")
                            val event = BLEProfiles.Event.Characteristics.OnWrite(
                                service = characteristic.service.uuid,
                                characteristic = characteristic.uuid,
                                result = Result.success(characteristic.value.copyOf()),
                            )
                            _profiles.emit(event)
                        }
                        else -> {
                            logger.warning("on write: [ status: $status | characteristic: ${characteristic?.uuid} ]")
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
                    is ConnectedStatus.Idling -> {
                        BLEGenerics.State.Connected(address = state.address, isPaired = state.status.isPaired)
                    }
                    is ConnectedStatus.Pairing -> {
                        BLEGenerics.State.Pairing(address = state.address)
                    }
                    ConnectedStatus.Unpairing -> {
                        BLEGenerics.State.Unpairing(address = state.address)
                    }
                }
            }
            is InternalState.Disconnecting -> BLEGenerics.State.Disconnecting(address = state.address)
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

    private data class Managers(
        val btGranted: Boolean,
        val btEnabled: Boolean,
        val gpsGranted: Boolean,
        val gpsEnabled: Boolean,
    ) {
        fun isReady(): Boolean {
            return btGranted && btEnabled && gpsGranted && gpsEnabled
        }
    }

    private fun checkManagers(
        btEnabled: Boolean = context.getSystemService(BluetoothManager::class.java)
            .adapter
            ?.isEnabled
            ?: false,
        gpsEnabled: Boolean = context.getSystemService(LocationManager::class.java)
            .isProviderEnabled(LocationManager.GPS_PROVIDER),
    ) {
        val managers = Managers(
            btGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED,
            btEnabled = btEnabled,
            gpsGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
            gpsEnabled = gpsEnabled,
        )
        when (val state = _states.value) {
            is InternalState.Waiting -> {
                if (managers.isReady()) {
                    _states.value = InternalState.Searching(address = state.address)
                }
            }
            is InternalState.Connected -> {
                if (!managers.isReady()) {
                    _states.value = InternalState.Waiting(address = state.address)
                }
            }
            is InternalState.Searching -> {
                if (!managers.isReady()) {
                    _states.value = InternalState.Waiting(address = state.address)
                }
            }
            else -> {
                // todo
            }
        }
    }

    private val receivers = object : BroadcastReceiver() {
        private fun onReceive(intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val text = when (state) {
                        BluetoothAdapter.STATE_OFF -> "off"
                        BluetoothAdapter.STATE_ON -> "on"
                        BluetoothAdapter.STATE_TURNING_OFF -> "turning off"
                        BluetoothAdapter.STATE_TURNING_ON -> "turning on"
                        else -> state.toString()
                    }
                    logger.info("bluetooth adapter state: $text")
                    when (state) {
                        BluetoothAdapter.STATE_ON -> checkManagers(btEnabled = true)
                        BluetoothAdapter.STATE_TURNING_OFF -> checkManagers(btEnabled = false)
                    }
                }
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val name = intent.getStringExtra(LocationManager.EXTRA_PROVIDER_NAME)
                        if (name != LocationManager.GPS_PROVIDER) return
                    }
                    val lm = context.getSystemService(LocationManager::class.java)
                    val isLocationEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    logger.info("gps enabled: $isLocationEnabled")
                    checkManagers()
                }
            }
        }

        override fun onReceive(context: Context?, intent: Intent?) {
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

//    private val receiversPairing = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            if (intent == null) return
//            when (intent.action) {
//                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
//                    val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
//                    when (variant) {
//                        BluetoothDevice.PAIRING_VARIANT_PIN -> {
//                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
//                                ?: TODO("RealBLEGenerics:onReceive($intent):no device!")
//                            val state = _states.value
//                            if (state !is InternalState.Connected) TODO("RealBLEGenerics:onReceive($intent):state $state!")
//                            if (state.status !is ConnectedStatus.Pairing) TODO("RealBLEGenerics:onReceive($intent):state $state!")
//                            if (state.address != device.address) return
//                            abortBroadcast()
//                            val pin = state.status.pin ?: TODO("RealBLEGenerics:onReceive($intent):no pin!")
//                            logger.debug("set pin ${state.address} $pin")
//                            if (!device.setPin(pin.toByteArray())) TODO("RealBLEGenerics:onReceive($intent):set pin error!")
//                        }
//                    }
//                }
//            }
//        }
//    }
//    private val intentFiltersPairing = IntentFilter().also {
//        it.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
//        it.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
//    }

    private suspend fun onBondStateChanged(intent: Intent, state: InternalState.Connected) {
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            ?: TODO("RealBLEGenerics:receivers:connected(${intent.action}):bonding:no device!")
        if (state.address != device.address) return
        val oldState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
        val newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
        logger.info("receivers connected ${device.address}: $oldState -> $newState")
        when (newState) {
            BluetoothDevice.BOND_NONE -> {
                when (state.status) {
                    is ConnectedStatus.Pairing -> {
                        val reason = intent.getIntExtra("android.bluetooth.device.extra.REASON", BluetoothDevice.ERROR) // todo
                        logger.debug("device ${device.address} unpaired $reason")
                        _states.value = InternalState.Searching(address = state.address)
                        _events.emit(BLEGenerics.Event.OnPairing(address = state.address, isSuccess = false))
                    }
                    is ConnectedStatus.Idling -> if (state.status.isPaired) {
                        val reason = intent.getIntExtra("android.bluetooth.device.extra.REASON", BluetoothDevice.ERROR) // todo
                        logger.warning("device ${device.address} unpaired $reason externally")
                        _states.value = InternalState.Searching(address = state.address)
                    }
                    else -> { /* noop */ }
                }
            }
            BluetoothDevice.BOND_BONDED -> {
                when (state.status) {
                    is ConnectedStatus.Idling -> if (!state.status.isPaired) {
                        logger.debug("device ${device.address} bonded externally")
                        _states.value = state.copy(status = ConnectedStatus.Idling(isPaired = true))
                    }
                    is ConnectedStatus.Pairing -> {
                        logger.debug("device ${device.address} bonded")
                        _states.value = state.copy(status = ConnectedStatus.Idling(isPaired = true))
                        _events.emit(BLEGenerics.Event.OnPairing(address = state.address, isSuccess = true))
                    }
                    else -> TODO("RealBLEGenerics:receivers:connected(${intent.action}):bonding($oldState:$newState):state: $state")
                }
            }
            BluetoothDevice.BOND_BONDING -> {
                when (state.status) {
                    is ConnectedStatus.Idling -> {
                        if (state.status.isPaired) {
                            logger.debug("device ${device.address} is unpairing externally")
                        } else {
                            logger.debug("device ${device.address} is pairing externally")
                        }
                    }
                    is ConnectedStatus.Pairing -> {
                        logger.debug("device ${device.address} is pairing")
                    }
                    is ConnectedStatus.Unpairing -> {
                        logger.debug("device ${device.address} is unpairing")
                    }
                    else -> TODO("RealBLEGenerics:receivers:connected(${intent.action}):bonding($oldState:$newState):state: $state")
                }
            }
        }
    }

    private val receiversConnected = object : BroadcastReceiver() {
        private fun onReceive(intent: Intent, state: InternalState.Connected) {
            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    coroutineScope.launch {
                        mutex.withLock {
                            withContext(default) {
                                onBondStateChanged(intent = intent, state = state)
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: TODO("RealBLEGenerics:receivers:connected($intent):disconnected:no device!")
                    if (state.address != device.address) return
                    if (state.status !is ConnectedStatus.Unpairing) TODO("RealBLEGenerics:receivers:connected($intent):disconnected:state: $state")
                    logger.debug("device ${device.address} disconnected")
                    _states.value = InternalState.Searching(address = state.address)
                }
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
                    when (variant) {
                        BluetoothDevice.PAIRING_VARIANT_PIN -> {
                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                ?: TODO("RealBLEGenerics:onReceive($intent):no device!")
                            if (state.address != device.address) return
                            abortBroadcast()
                            if (state.status is ConnectedStatus.Pairing) {
                                logger.debug("manual pairing")
                            } else {
                                logger.debug("status ${state.status} so no manual pairing")
                                runCatching {
                                    device.cancelBondProcess()
                                }.fold(
                                    onSuccess = {
                                        _states.value = InternalState.Searching(address = state.address)
                                    },
                                    onFailure = { error ->
                                        logger.warning("cancel bonding ${device.address} error: $error ${error.cause}")
                                        connecting(address = state.address)
                                    },
                                )
                                return
                            }
                            val pin = state.status.pin ?: TODO("RealBLEGenerics:onReceive($intent):no pin!")
                            logger.debug("set pin ${state.address} $pin")
                            if (!device.setPin(pin.toByteArray())) TODO("RealBLEGenerics:onReceive($intent):set pin error!")
                        }
                    }
                }
            }
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val state = _states.value
            if (state !is InternalState.Connected) {
                logger.warning("receivers(${intent.action}) connected state: $state")
                return
            }
            onReceive(intent = intent, state = state)
        }
    }
    private val intentFiltersConnected = IntentFilter().also {
        it.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        it.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        it.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        it.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
    }

    private val _profiles = object : MutableBLEProfiles {
        override val events = MutableSharedFlow<BLEProfiles.Event>()
        private val operations: Queue<BLEProfiles.Operation> = ConcurrentLinkedQueue()
        private val performing = AtomicBoolean(false)

        override fun perform(operation: BLEProfiles.Operation) {
            coroutineScope.launch {
                mutex.withLock {
                    withContext(default) {
                        operations += operation
                        if (performing.compareAndSet(false, true)) perform()
                    }
                }
            }
        }

        override suspend fun emit(event: BLEProfiles.Event) {
            if (!performing.get()) return
            events.emit(event)
            perform()
        }

        override suspend fun clear() {
            mutex.withLock {
                performing.set(false)
                operations.clear()
            }
        }

        private suspend fun perform() {
            val state = _states.value
            if (state !is InternalState.Connected || state.status !is ConnectedStatus.Idling) {
                performing.set(false)
                operations.clear()
                return
            }
            val operation = operations.poll()
            if (operation == null) {
                performing.set(false)
                return
            }
            when (operation) {
                is BLEProfiles.Operation.ChangeMTU -> {
                    if (!state.gatt.requestMtu(operation.value)) {
                        TODO("RealBLEGenerics:profiles:perform($operation):request MTU error!")
                    }
                }
                BLEProfiles.Operation.Services -> {
                    if (!state.gatt.discoverServices()) {
                        TODO("RealBLEGenerics:profiles:perform($operation):discover services error!")
                    }
                }
                is BLEProfiles.Operation.Characteristics.SetNotification -> {
                    val service = state.gatt.getService(operation.service) ?: TODO("No service ${operation.service}!")
                    val characteristic = service.getCharacteristic(operation.characteristic) ?: TODO("No characteristic ${operation.characteristic}!")
                    if (!state.gatt.setCharacteristicNotification(characteristic, operation.value)) {
                        TODO("RealBLEGenerics:profiles:perform($operation):NOTIFICATION_STATUS_WAS_NOT_SUCCESSFULLY_SET!")
                    }
                    val event = BLEProfiles.Event.Characteristics.OnSetNotification(
                        service = operation.service,
                        characteristic = operation.characteristic,
                        value = operation.value,
                    )
                    emit(event)
                }
                is BLEProfiles.Operation.Characteristics.Write -> {
                    val service = state.gatt.getService(operation.service) ?: TODO("No service ${operation.service}!")
                    val characteristic = service.getCharacteristic(operation.characteristic) ?: TODO("No characteristic ${operation.characteristic}!")
                    if (!characteristic.setValue(operation.bytes)) {
                        TODO("RealBLEGenerics:profiles:perform($operation):set value error!")
                    }
                    if (!state.gatt.writeCharacteristic(characteristic)) {
                        val event = BLEProfiles.Event.Characteristics.OnWrite(
                            service = service.uuid,
                            characteristic = characteristic.uuid,
                            result = Result.failure(IllegalStateException("CHARACTERISTIC_WRITING_WAS_NOT_INITIATED!")),
                        )
                        emit(event)
                    }
                }
                is BLEProfiles.Operation.Descriptors.Write -> {
                    try {
                        val service = state.gatt.getService(operation.service) ?: error("No service ${operation.service}!")
                        val characteristic = service.getCharacteristic(operation.characteristic) ?: error("No characteristic ${operation.characteristic}!")
                        val descriptor = characteristic.getDescriptor(operation.descriptor) ?: error("No descriptor ${operation.descriptor}!")
                        if (!descriptor.setValue(operation.bytes)) error("RealBLEGenerics:profiles:perform($operation):set value error!")
                        if (!state.gatt.writeDescriptor(descriptor)) error("Descriptor ${operation.descriptor} writing was not initiated!")
                    } catch (error: Throwable) {
                        val event = BLEProfiles.Event.Descriptors.OnWrite(
                            service = operation.service,
                            characteristic = operation.characteristic,
                            descriptor = operation.descriptor,
                            result = Result.failure(error),
                        )
                        emit(event)
                    }
                }
            }
        }
    }
    override val profiles: BLEProfiles = _profiles

    private fun toString(state: InternalState?): String {
        return when (state) {
            is InternalState.Connected -> when (state.status) {
                is ConnectedStatus.Idling -> if (state.status.isPaired) {
                    "Paired"
                } else {
                    "Unpaired"
                }
                is ConnectedStatus.Pairing -> "Pairing"
                ConnectedStatus.Unpairing -> "Unpairing"
            }
            is InternalState.Connecting -> "Connecting"
            is InternalState.Disconnecting -> "Disconnecting"
            is InternalState.Searching -> "Searching"
            is InternalState.Waiting -> "Waiting"
            null -> "Disconnected"
        }
    }

    private fun onStates(oldState: InternalState?, newState: InternalState?) {
        if (oldState == null) {
            logger.info("new state: ${toString(newState)}")
        } else if (newState == null) {
            logger.info("old state: ${toString(oldState)}")
        } else if (oldState > newState) {
            val message = String.format("%-16S < %s", toString(newState), toString(oldState).lowercase())
            logger.info(message)
        } else if (oldState < newState) {
            val message = String.format("%-16s > %S", toString(oldState).lowercase(), toString(newState))
            logger.info(message)
        } else if (oldState is InternalState.Connected && newState is InternalState.Connected) {
            if (oldState.status > newState.status) {
                val message = String.format("%-16S < %s", toString(newState), toString(oldState).lowercase())
                logger.info(message)
            } else {
                val message = String.format("%-16s > %S", toString(oldState).lowercase(), toString(newState))
                logger.info(message)
            }
        } else {
            val message = String.format("%-16s > %s", toString(oldState), toString(newState))
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
                        register(context, receivers, intentFilters)
                    } else if (oldState != null && newState == null) {
                        context.unregisterReceiver(receivers)
                    }
                    if (newState is InternalState.Connected && newState.status is ConnectedStatus.Pairing) {
                        if (oldState !is InternalState.Connected || oldState.status !is ConnectedStatus.Pairing) {
//                            register(context, receiversPairing, intentFiltersPairing, exported = true) // todo
                            try {
                                onPairing(address = newState.address)
                            } catch (error: Throwable) {
                                TODO("RealBLEGenerics:pair(${newState.address}):$error")
                            }
                        }
                    } else if (oldState is InternalState.Connected && oldState.status is ConnectedStatus.Pairing) {
//                        context.unregisterReceiver(receiversPairing) // todo
                    }
                    if (oldState is InternalState.Connected && oldState.status is ConnectedStatus.Idling) {
                        if (newState !is InternalState.Connected || newState.status !is ConnectedStatus.Idling) {
                            _profiles.clear()
                        }
                    }
                    if (newState is InternalState.Connecting && newState > oldState) {
                        launch(default) {
                            val timeMax = 4.seconds
                            val timeDelay = 250.milliseconds
                            val timeStart = now()
                            while (true) {
                                val _state = _states.value
                                if (_state !is InternalState.Connecting) break
                                val timeDiff = now() - timeStart
                                if (timeDiff > timeMax) {
                                    logger.warning("connecting timeout: $timeDiff")
                                    _states.value = InternalState.Searching(address = _state.address)
                                    break
                                }
                                delay(timeDelay)
                            }
                        }
                    } else if (oldState is InternalState.Connecting && oldState > newState) {
                        try {
                            oldState.gatt.disconnect()
                        } catch (error: Throwable) {
                            TODO("RealBLEGenerics:init($oldState -> $newState):$error")
                        }
                    }
                    if (newState is InternalState.Connected && newState > oldState) {
                        register(context, receiversConnected, intentFiltersConnected, exported = true)
                    } else if (oldState is InternalState.Connected && oldState > newState) {
                        context.unregisterReceiver(receiversConnected)
                        try {
                            oldState.gatt.disconnect()
                        } catch (error: Throwable) {
                            TODO("RealBLEGenerics:init($oldState -> $newState):$error")
                        }
                    }
                    if (oldState !is InternalState.Searching && newState is InternalState.Searching) {
                        runCatching {
                            startScan()
                        }.fold(
                            onFailure = { error ->
                                logger.warning("start scan error: $error")
                                when (error) {
                                    is SecurityException, is BLEGenericsException -> {
                                        _states.value = InternalState.Waiting(address = newState.address)
                                    }
                                    else -> {
                                        TODO("RealBLEGenerics:init($oldState -> $newState):start scan error: $error")
                                    }
                                }
                            },
                            onSuccess = {
                                launch(default) {
                                    val timeDelay = 250.milliseconds
                                    while (true) {
                                        val actual = _states.value
                                        if (actual !is InternalState.Searching) break
                                        val timeNow = now()
                                        val fromStart = timeNow - scanCallback.timeStart
                                        val fromLast = timeNow - scanCallback.timeLastResult
                                        if (fromLast > 4.seconds) {
                                            logger.warning("searching timeout from last: $fromLast")
                                            _states.value = InternalState.Waiting(address = actual.address)
                                            break
                                        } else if (fromStart > 16.seconds) {
                                            logger.warning("searching timeout: $fromStart")
                                            _states.value = InternalState.Waiting(address = actual.address)
                                            break
                                        }
                                        delay(timeDelay)
                                    }
                                }
                            },
                        )
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
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEGenerics:startScan:no adapter!")
        if (!adapter.isEnabled) throw BLEGenericsException(BLEGenericsException.Type.BTDisabled)
        val lm = context.getSystemService(LocationManager::class.java)
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) throw BLEGenericsException(BLEGenericsException.Type.GPSDisabled)
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
        scanCallback.isEnabled = false
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("RealBLEScanner:stopScan:no adapter!")
        if (!adapter.isEnabled) return // todo
        val scanner = adapter.bluetoothLeScanner ?: TODO("RealBLEScanner:stopScan:no scanner!")
        scanner.stopScan(scanCallback)
    }

    private suspend fun onConnect(gatt: BluetoothGatt) {
        val address = gatt.device.address
        val isPaired = gatt.device.bondState == BluetoothDevice.BOND_BONDED
        _states.value = InternalState.Connected(
            address = address,
            gatt = gatt,
            status = ConnectedStatus.Idling(isPaired = isPaired),
        )
        _events.emit(BLEGenerics.Event.OnConnect(address = address))
    }

    private suspend fun onDisconnect(address: String) {
        _states.value = null
        _events.emit(BLEGenerics.Event.OnDisconnect(address = address))
    }

    private fun connectGatt(address: String): BluetoothGatt {
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
                    when (val state = _states.value) {
                        is InternalState.Connected -> {
                            if (state.status !is ConnectedStatus.Idling) TODO("RealBLEGenerics:disconnect:state: $state")
                            _states.value = InternalState.Disconnecting(address = state.address)
                        }
                        is InternalState.Connecting,
                        is InternalState.Searching,
                        is InternalState.Waiting,
                        -> onDisconnect(address = state.address)
                        is InternalState.Disconnecting,
                        null,
                        -> { /* noop */ }
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
                    if (state.status.isPaired) TODO("RealBLEGenerics:pair(${state.address}):already paired!")
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

    private fun BluetoothDevice.cancelBondProcess(): Boolean {
        val result = javaClass.getMethod("cancelBondProcess").invoke(this)
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
                    if (!state.status.isPaired) TODO("RealBLEGenerics:unpair($address):already unpaired!")
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
