package sp.sample.blegenerics

import android.content.Context
import sp.ax.blescanner.BLEDevice

internal class FinalLocals(context: Context) : Locals {
    private val prefs = context.getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE)

    override var selectedDevice: BLEDevice?
        get() {
            val exists = prefs.getBoolean("selectedDevice", false)
            if (!exists) return null
            return BLEDevice(
                name = prefs.getString("selectedDevice:name", null)!!,
                address = prefs.getString("selectedDevice:address", null)!!,
                bytes = ByteArray(0), // todo
            )
        }
        set(value) {
            if (value == null) {
                prefs.edit()
                    .putBoolean("selectedDevice", false)
                    .remove("selectedDevice:name")
                    .remove("selectedDevice:address")
                    .commit()
            } else {
                prefs.edit()
                    .putBoolean("selectedDevice", true)
                    .putString("selectedDevice:name", value.name)
                    .putString("selectedDevice:address", value.address)
                    .commit()
            }
        }
}
