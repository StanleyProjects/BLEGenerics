package sp.ax.blegenerics

import android.content.Context
import android.content.Intent

inline fun <reified T : BLEGenericsService> states(context: Context) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsStatesAction
    context.startService(intent)
}

inline fun <reified T : BLEGenericsService> connect(context: Context, address: String) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsConnectAction
    intent.putExtra("address", address)
    context.startService(intent)
}

inline fun <reified T : BLEGenericsService> disconnect(context: Context, address: String) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsDisconnectAction
    intent.putExtra("address", address)
    context.startService(intent)
}

inline fun <reified T : BLEGenericsService> pair(context: Context, address: String, pin: String?) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsPairAction
    intent.putExtra("address", address)
    intent.putExtra("pin", pin)
    context.startService(intent)
}
