package sp.ax.blegenerics

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import kotlin.time.Duration

internal abstract class InternalScanCallback : ScanCallback() {
    var isEnabled: Boolean = false
    var timeStart = Duration.ZERO
    var timeLastResult = Duration.ZERO

    protected abstract fun onScanResult(address: String)

    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        if (!isEnabled) return
        val address = result?.device?.address ?: return
        onScanResult(address = address)
    }
}
