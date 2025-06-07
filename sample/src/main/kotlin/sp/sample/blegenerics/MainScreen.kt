package sp.sample.blegenerics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import sp.ax.blescanner.BLEDevice

@Composable
internal fun MainScreen() {
    val _device = remember { mutableStateOf<BLEDevice?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        val device = _device.value
        if (device == null) {
            ScannerScreen(
                onSelectDevice = {
                    _device.value = it
                },
            )
        } else {
            DeviceScreen(
                device = device,
                onDisconnect = {
                    _device.value = null
                },
            )
        }
    }
}
