package sp.sample.blegenerics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sp.ax.blegenerics.BLEGenerics
import kotlin.coroutines.CoroutineContext

internal class RealBLEGenerics(
    private val coroutineScope: CoroutineScope,
    private val default: CoroutineContext,
    private val context: Context,
) : BLEGenerics {
    inner class InternalCallback : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int,
        ) {
            if (gatt == null) TODO("RealBLEGenerics:callback:no gatt!")
            val GATT_ERROR = 133 // https://stackoverflow.com/a/60849590
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> {
                            val address = gatt.device.address
                            coroutineScope.launch {
                                mutex.withLock {
                                    onConnect(address = address, gatt = gatt)
                                }
                            }
                        }
                        BluetoothGatt.STATE_DISCONNECTED -> {
                            val address = gatt.device.address
                            coroutineScope.launch {
                                mutex.withLock {
                                    onDisconnect(address = address)
                                }
                            }
                        }
                    }
                }
                GATT_ERROR -> {
                    TODO("RealBLEGenerics:callback:onConnectionStateChange(${gatt?.hashCode()}, $status, $newState)")
                }
                else -> {
                    // todo
                }
            }
        }
    }

    private val _states = MutableStateFlow<Map<String, BLEGenerics.State>>(emptyMap())
    override val states = _states.asStateFlow()

    private val _events = MutableSharedFlow<Pair<String, BLEGenerics.Event>>()
    override val events = _events.asSharedFlow()

    private val gatts = mutableMapOf<String, BluetoothGatt>()

    private val mutex = Mutex()

    private suspend fun onConnect(address: String, gatt: BluetoothGatt) {
        gatts[address] = gatt
        _states.value += address to BLEGenerics.State.Connected(
            isPaired = gatt.device.bondState == BluetoothDevice.BOND_BONDED,
        )
        _events.emit(address to BLEGenerics.Event.OnConnect)
    }

    private suspend fun onDisconnect(address: String) {
        gatts.remove(address)
        _states.value -= address
        _events.emit(address to BLEGenerics.Event.OnDisconnect)
    }

    private fun connect(address: String, callback: BluetoothGattCallback) {
        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm.adapter ?: TODO("${this::class.java.simpleName}:onConnect($address):no adapter!")
        if (!adapter.isEnabled) TODO("${this::class.java.simpleName}:onConnect($address):adapter disabled!")
        val autoConnect = false
        val transport = BluetoothDevice.TRANSPORT_LE
        val device = adapter.getRemoteDevice(address) ?: TODO("${this::class.java.simpleName}:onConnect($address):no device!")
        device.connectGatt(context, autoConnect, callback, transport)
    }

    override fun connect(address: String) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    if (_states.value.containsKey(address)) TODO("RealBLEGenerics:connect($address):state: ${_states.value[address]}")
                    _states.value += address to BLEGenerics.State.Connecting
                    try {
                        val callback = InternalCallback()
                        connect(address = address, callback = callback)
                    } catch (error: Throwable) {
                        // todo errors
                        _states.value -= address
                    }
                }
            }
        }
    }

    override fun disconnect(address: String) {
        coroutineScope.launch {
            mutex.withLock {
                withContext(default) {
                    if (!_states.value.containsKey(address)) TODO("RealBLEGenerics:disconnect($address):state: ${_states.value[address]}")
                    val gatt = gatts[address] ?: TODO("RealBLEGenerics:disconnect($address):no gatt!")
                    _states.value += address to BLEGenerics.State.Disconnecting
                    try {
                        gatt.disconnect()
                    } catch (error: Throwable) {
                        TODO("RealBLEGenerics:disconnect($address):$error")
                    }
                }
            }
        }
    }
}
