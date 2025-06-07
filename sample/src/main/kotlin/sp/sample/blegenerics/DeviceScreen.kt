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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sp.ax.blescanner.BLEDevice

@Composable
internal fun DeviceScreen(
    device: BLEDevice,
    onDisconnect: () -> Unit,
) {
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
            Spacer(Modifier.weight(1f)) // todo
            BasicText(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        onDisconnect()
                    }
                    .wrapContentSize(),
                text = "disconnect",
            )
        }
    }
}
