package sp.sample.blegenerics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
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
import sp.ax.blescanner.BLEDevice
import sp.ax.blescanner.BLEScanner
import sp.ax.blescanner.BLEScannerReceivers
import sp.ax.blescanner.start
import sp.ax.blescanner.states
import sp.ax.blescanner.stop

@Composable
internal fun ScannerScreen(
    onSelectDevice: (BLEDevice) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { BLEScannerReceivers.states(context = context) }
        .collectAsStateWithLifecycle(null, minActiveState = Lifecycle.State.RESUMED)
        .value
    val _devices = remember { mutableStateOf(emptyList<BLEDevice>()) }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            BLEScannerReceivers.devices(context = context).collect { device ->
                val devices = _devices.value
                if (devices.none { it.address == device.address }) {
                    _devices.value = devices + device
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            states<ScannerService>(context = context)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                _devices.value.forEach { device ->
                    item(key = device.address) {
                        val text = """
                            name: ${device.name}
                            address: ${device.address}
                        """.trimIndent()
                        BasicText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    stop<ScannerService>(context = context)
                                    onSelectDevice(device)
                                }
                                .wrapContentHeight(),
                            text = text,
                        )
                    }
                }
            }
            val enabled = state == BLEScanner.State.Started || state == BLEScanner.State.Stopped
            val text = when (state) {
                BLEScanner.State.Started -> "stop"
                BLEScanner.State.Stopped -> "start"
                else -> "..."
            }
            BasicText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = enabled) {
                        when (state) {
                            BLEScanner.State.Started -> stop<ScannerService>(context = context)
                            BLEScanner.State.Stopped -> start<ScannerService>(context = context)
                            else -> {
                                // noop
                            }
                        }
                    }
                    .wrapContentSize(),
                text = text,
            )
        }
    }
}
