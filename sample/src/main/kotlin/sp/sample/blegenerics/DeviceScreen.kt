package sp.sample.blegenerics

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
import sp.ax.blegenerics.requestMTU
import sp.ax.blegenerics.services
import sp.ax.blegenerics.states
import sp.ax.blegenerics.unpair
import sp.ax.blescanner.BLEDevice

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
                BLEGenerics.connect<DeviceService>(context = context, address = device.address)
            }
        }
    }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            BLEGenerics.states<DeviceService>(context = context)
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
    LaunchedEffect(Unit) {
        BLEProfilesReceivers.events(context = context).collect { event ->
            when (event) {
                BLEProfiles.Event.OnServices -> {
                    context.showToast("on services discovered")
                }
                is BLEProfiles.Event.OnMtuChanged -> {
                    context.showToast("on mtu changed: ${event.size}")
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
                BasicText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            BLEProfiles.requestMTU<DeviceService>(context = context, size = 200)
                        }
                        .wrapContentSize(),
                    text = "request MTU 200",
                )
                BasicText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            BLEProfiles.services<DeviceService>(context = context)
                        }
                        .wrapContentSize(),
                    text = "services",
                )
                if (state.isPaired) {
                    BasicText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                BLEGenerics.unpair<DeviceService>(context = context)
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
                                BLEGenerics.pair<DeviceService>(context = context, pin = pin)
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
                        BLEGenerics.disconnect<DeviceService>(context = context)
                    }
                    .wrapContentSize(),
                text = "disconnect",
            )
        }
    }
}
