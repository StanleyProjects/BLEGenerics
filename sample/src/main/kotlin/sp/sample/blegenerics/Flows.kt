package sp.sample.blegenerics

import kotlinx.coroutines.flow.MutableStateFlow
import sp.ax.blescanner.BLEDevice

internal class Flows(
    val themes: MutableStateFlow<ThemeState>,
    val devices: MutableStateFlow<BLEDevice?>,
)
