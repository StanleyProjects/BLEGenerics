package sp.sample.blegenerics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import sp.ax.blegenerics.BLEGenerics

internal class RealBLEGenerics(
    private val context: Context,
) : BLEGenerics {
    private val _states = MutableStateFlow<Map<String, BLEGenerics.State>>(emptyMap())
    override val states = _states.asStateFlow()

    private val gatts = mutableMapOf<String, BluetoothGatt>()

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
        if (_states.value.containsKey(address)) return // todo
        _states.value += address to BLEGenerics.State.Connecting
        try {
            val callback = object : BluetoothGattCallback() {
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
                                    gatts[address] = gatt
                                    _states.value += address to BLEGenerics.State.Connected(
                                        isPaired = gatt.device.bondState == BluetoothDevice.BOND_BONDED,
                                    )
                                }
                                BluetoothGatt.STATE_DISCONNECTED -> {
                                    gatts.remove(address)
                                    _states.value -= address
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
            connect(address = address, callback = callback)
        } catch (error: Throwable) {
            // todo errors
            _states.value -= address
        }
    }

    override fun disconnect(address: String) {
        if (!_states.value.containsKey(address)) return // todo
        _states.value += address to BLEGenerics.State.Disconnecting
        try {
            val gatt = gatts[address]
            if (gatt == null) TODO("RealBLEGenerics:disconnect($address):no gatt!")
            gatt.disconnect()
        } catch (error: Throwable) {
            _states.value -= address
        }
    }
}
