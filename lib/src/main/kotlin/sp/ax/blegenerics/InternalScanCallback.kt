package sp.ax.blegenerics

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult

internal abstract class InternalScanCallback(var isEnabled: Boolean = false) : ScanCallback() {
    protected abstract fun onScanResult(address: String)

    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        if (!isEnabled) return
        val address = result?.device?.address ?: return
        onScanResult(address = address)
    }
}
