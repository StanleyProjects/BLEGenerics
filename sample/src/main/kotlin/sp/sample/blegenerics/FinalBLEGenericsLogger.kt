package sp.sample.blegenerics

import android.util.Log
import sp.ax.blegenerics.BLEGenericsLogger

internal class FinalBLEGenericsLogger(
    private val level: BLEGenericsLogger.Level,
    private val tag: String,
) : BLEGenericsLogger {
    override fun info(message: String) {
        if (level >= BLEGenericsLogger.Level.Info) {
            Log.i(tag, message)
        }
    }

    override fun debug(message: String) {
        if (level >= BLEGenericsLogger.Level.Debug) {
            Log.i(tag, message)
        }
    }

    override fun warning(message: String) {
        if (level >= BLEGenericsLogger.Level.Warning) {
            Log.i(tag, message)
        }
    }
}
