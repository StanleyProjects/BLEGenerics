package sp.sample.blegenerics

import android.content.Intent
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
import sp.ax.blescanner.BLEDevice

@Composable
internal fun DeviceScreen(
    device: BLEDevice,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { DeviceService.states(context = context) }
        .collectAsStateWithLifecycle(emptyMap(), minActiveState = Lifecycle.State.RESUMED)
        .value[device.address]
    LaunchedEffect(Unit) {
        DeviceService.states(context = context).take(1).collect { states ->
            if (!states.containsKey(device.address)) {
                val intent = Intent(context, DeviceService::class.java)
                intent.action = "connect"
                intent.putExtra("address", device.address)
                context.startService(intent)
            }
        }
    }
    LaunchedEffect(Unit) {
        val intent = Intent(context, DeviceService::class.java)
        intent.action = "states"
        context.startService(intent)
    }
    LaunchedEffect(Unit) {
        DeviceService.events(context = context).collect { (address, event) ->
            if (address == device.address) {
                when (event) {
                    BLEGenerics.Event.OnConnect -> {
                        // todo
                    }
                    BLEGenerics.Event.OnDisconnect -> onDisconnect()
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
            BasicText(text = text)
            BasicText(text = "state: $state")
            Spacer(Modifier.weight(1f)) // todo
            BasicText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = state is BLEGenerics.State.Connected) {
                        val intent = Intent(context, DeviceService::class.java)
                        intent.action = "disconnect"
                        intent.putExtra("address", device.address)
                        context.startService(intent)
                    }
                    .wrapContentSize(),
                text = "disconnect",
            )
        }
    }
}
