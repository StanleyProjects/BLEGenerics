package sp.sample.blegenerics

import android.bluetooth.BluetoothGattDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.take
import sp.ax.blegenerics.BLEGenerics
import sp.ax.blegenerics.BLEGenericsReceivers
import sp.ax.blegenerics.BLEProfiles
import sp.ax.blegenerics.BLEProfilesReceivers
import sp.ax.blegenerics.connect
import sp.ax.blegenerics.disconnect
import sp.ax.blegenerics.pair
import sp.ax.blegenerics.perform
import sp.ax.blegenerics.states
import sp.ax.blegenerics.unpair
import sp.ax.blescanner.BLEDevice
import java.util.UUID

@Composable
internal fun DeviceScreen(
    device: BLEDevice,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { BLEGenericsReceivers.states(context = context) }
        .collectAsStateWithLifecycle(null, minActiveState = Lifecycle.State.RESUMED)
        .value
    LaunchedEffect(Unit) {
        BLEGenericsReceivers.states(context = context).take(1).collect { state ->
            if (state == null) {
                connect<DeviceService>(context = context, address = device.address)
            }
        }
    }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            states<DeviceService>(context = context)
        }
    }
    LaunchedEffect(Unit) {
        BLEGenericsReceivers.events(context = context).collect { event ->
            when (event) {
                is BLEGenerics.Event.OnConnect -> {
                    // todo
                }
                is BLEGenerics.Event.OnDisconnect -> onDisconnect()
                is BLEGenerics.Event.OnPairing -> {
                    if (!event.isSuccess) {
                        context.showToast("Pairing failed!")
                    }
                }
            }
        }
    }
    val discovered = remember { mutableStateOf<Map<UUID, Set<UUID>>?>(null) }
    LaunchedEffect(Unit) {
        BLEProfilesReceivers.events(context = context).collect { event ->
            when (event) {
                is BLEProfiles.Event.OnServices -> {
                    val message = event.characteristics.entries.joinToString(separator = "\n") { (services, characteristics) -> "$services: $characteristics"}
                    println(message)
                    context.showToast("on services discovered")
                    discovered.value = event.characteristics
                }
                is BLEProfiles.Event.OnMtuChanged -> {
                    context.showToast("on mtu changed: ${event.value}")
                }
                is BLEProfiles.Event.Characteristics.OnSetNotification -> {
                    context.showToast("on set notification: ${event.value}")
                }
                is BLEProfiles.Event.Descriptors.OnWrite -> {
                    context.showToast("on write descriptor: ${event.descriptor}")
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(Modifier.fillMaxSize()) {
            val text = """
                name: ${device.name}
                address: ${device.address}
            """.trimIndent()
            val enabled = when (state) {
                is BLEGenerics.State.Connected -> true
                is BLEGenerics.State.Searching -> true
                is BLEGenerics.State.Waiting -> true
                else -> false
            }
            BasicText(text = text)
            BasicText(text = "$state")
            Spacer(Modifier.weight(1f)) // todo
            if (state is BLEGenerics.State.Connected) {
                val characteristics = discovered.value
                if (characteristics == null) {
                    BasicText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                val operation = BLEProfiles.Operation.Services
                                perform<DeviceService>(context = context, operation = operation)
                            }
                            .wrapContentSize(),
                        text = "services",
                    )
                } else {
                    BasicText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                val service = UUID.fromString("00000000-cc7a-482a-984a-7f2ed5b3e58f") // todo
                                val characteristic = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // todo
                                val descriptor = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // todo
                                val operation = BLEProfiles.Operation.Descriptors.Write(
                                    service = service,
                                    characteristic = characteristic,
                                    descriptor = descriptor,
                                    bytes = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                                )
                                perform<DeviceService>(context = context, operation = operation)
                            }
                            .wrapContentSize(),
                        text = "write descriptor",
                    )
                    BasicText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                val service = UUID.fromString("00000000-cc7a-482a-984a-7f2ed5b3e58f") // todo
                                val characteristic = UUID.fromString("00000001-8e22-4541-9d4c-21edae82ed19") // todo
                                val operation = BLEProfiles.Operation.Characteristics.SetNotification(
                                    service = service,
                                    characteristic = characteristic,
                                    value = true,
                                )
                                perform<DeviceService>(context = context, operation = operation)
                            }
                            .wrapContentSize(),
                        text = "set notification true",
                    )
                    BasicText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                val operation = BLEProfiles.Operation.ChangeMTU(value = 200)
                                perform<DeviceService>(context = context, operation = operation)
                            }
                            .wrapContentSize(),
                        text = "change MTU 200",
                    )
                }
                if (state.isPaired) {
                    BasicText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                unpair<DeviceService>(context = context)
                            }
                            .wrapContentSize(),
                        text = "unpair",
                    )
                } else {
                    val pin = "000000"
                    BasicText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                pair<DeviceService>(context = context, pin = pin)
                            }
                            .wrapContentSize(),
                        text = "pair: $pin",
                    )
                }
            }
            BasicText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = enabled) {
                        disconnect<DeviceService>(context = context)
                    }
                    .wrapContentSize(),
                text = "disconnect",
            )
        }
    }
}
