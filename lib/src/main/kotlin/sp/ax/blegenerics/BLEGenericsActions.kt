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

inline fun <reified T : BLEGenericsService> disconnect(context: Context) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsDisconnectAction
    context.startService(intent)
}

inline fun <reified T : BLEGenericsService> pair(context: Context, pin: String?) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsPairAction
    intent.putExtra("pin", pin)
    context.startService(intent)
}

inline fun <reified T : BLEGenericsService> unpair(context: Context) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEGenericsUnpairAction
    context.startService(intent)
}

fun <T : BLEGenericsService> perform(context: Context, type: Class<T>, operation: BLEProfiles.Operation) {
    val intent = Intent(context, type)
    intent.action = BLEGenericsService.BLEProfilesOperationsAction
    intent.putExtra("operation", OperationParcelable(delegate = operation))
    context.startService(intent)
}

inline fun <reified T : BLEGenericsService> perform(context: Context, operation: BLEProfiles.Operation) {
    perform(context = context, type = T::class.java, operation = operation)
}
