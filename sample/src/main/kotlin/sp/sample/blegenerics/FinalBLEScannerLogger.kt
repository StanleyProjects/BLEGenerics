package sp.sample.blegenerics

import android.util.Log
import sp.ax.blescanner.BLEScannerLogger

internal class FinalBLEScannerLogger(
    private val tag: String,
) : BLEScannerLogger {
    override fun info(message: String) {
        Log.i(tag, message)
    }

    override fun debug(message: String) {
        Log.d(tag, message)
    }

    override fun warning(message: String) {
        Log.w(tag, message)
    }
}
