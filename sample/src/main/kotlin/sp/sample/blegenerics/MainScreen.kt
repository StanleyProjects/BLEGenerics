package sp.sample.blegenerics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sp.ax.blescanner.BLEDevice

@Composable
internal fun MainScreen() {
    val themeState = App.flows.themes.collectAsState().value
    val device = App.flows.devices.collectAsState().value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeState.background),
    ) {
        if (device == null) {
            ScannerScreen(
                onSelectDevice = {
                    App.locals.selectedDevice = it
                    App.flows.devices.value = it
                },
            )
        } else {
            DeviceScreen(
                device = device,
                onDisconnect = {
                    App.locals.selectedDevice = null
                    App.flows.devices.value = null
                },
            )
        }
        Spacer(
            modifier = Modifier
                .size(64.dp)
                .background(color = themeState.text)
                .clickable {
                    App.flows.themes.value = when (themeState) {
                        ThemeState.Dark -> ThemeState.Light
                        else -> ThemeState.Dark
                    }
                }
                .align(Alignment.CenterEnd),
        )
    }
}
