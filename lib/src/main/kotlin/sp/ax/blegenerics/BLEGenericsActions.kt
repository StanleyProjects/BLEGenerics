package sp.ax.blegenerics

import android.content.Context
import android.content.Intent

inline fun <reified T : BLEGenericsService> BLEGenerics.Companion.states(context: Context) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsStatesAction
    context.startService(intent)
}

inline fun <reified T : BLEGenericsService> BLEGenerics.Companion.connect(context: Context, address: String) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsConnectAction
    intent.putExtra("address", address)
    context.startService(intent)
}

inline fun <reified T : BLEGenericsService> BLEGenerics.Companion.disconnect(context: Context) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsDisconnectAction
    context.startService(intent)
}

inline fun <reified T : BLEGenericsService> BLEGenerics.Companion.pair(context: Context, pin: String?) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsPairAction
    intent.putExtra("pin", pin)
    context.startService(intent)
}

inline fun <reified T : BLEGenericsService> BLEGenerics.Companion.unpair(context: Context) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsUnpairAction
    context.startService(intent)
}
