package sp.ax.blegenerics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object BLEGenericsReceivers {
    fun states(context: Context): Flow<BLEGenerics.State?> {
        return callbackFlow {
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val address = intent?.getStringExtra("address")
                    if (address == null) {
                        trySend(null)
                    } else {
                        val state = when (intent.getStringExtra("state")) {
                            "Connecting" -> BLEGenerics.State.Connecting(address = address)
                            "Connected" -> {
                                BLEGenerics.State.Connected(
                                    address = address,
                                    isPaired = intent.getBooleanExtra("isPaired", false),
                                )
                            }
                            "Searching" -> BLEGenerics.State.Searching(address = address)
                            "Waiting" -> BLEGenerics.State.Waiting(address = address)
                            "Disconnecting" -> BLEGenerics.State.Disconnecting(address = address)
                            "Pairing" -> BLEGenerics.State.Pairing(address = address)
                            "Unpairing" -> BLEGenerics.State.Unpairing(address = address)
                            else -> return
                        }
                        trySend(state)
                    }
                }
            }
            register(
                context = context,
                receivers = receivers,
                filters = IntentFilter(BLEGenericsService.BLEGenericsStatesAction),
            )
            awaitClose {
                context.unregisterReceiver(receivers)
            }
        }
    }

    fun events(context: Context): Flow<BLEGenerics.Event> {
        return callbackFlow {
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val address = intent?.getStringExtra("address") ?: return
                    val event = when (intent.getStringExtra("event")) {
                        "OnConnect" -> BLEGenerics.Event.OnConnect(address = address)
                        "OnDisconnect" -> BLEGenerics.Event.OnDisconnect(address = address)
                        "OnPairing" -> BLEGenerics.Event.OnPairing(
                            address = address,
                            isSuccess = intent.getBooleanExtra("isSuccess", false),
                        )
                        else -> return
                    }
                    trySend(event)
                }
            }
            register(
                context = context,
                receivers = receivers,
                filters = IntentFilter(BLEGenericsService.BLEGenericsEventsAction),
            )
            awaitClose {
                context.unregisterReceiver(receivers)
            }
        }
    }

    internal fun register(
        context: Context,
        receivers: BroadcastReceiver,
        filters: IntentFilter,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receivers,
                filters,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.registerReceiver(receivers, filters)
        }
    }
}

internal fun Context.getBroadcast(state: BLEGenerics.State?): Intent {
    val broadcast = Intent(BLEGenericsService.BLEGenericsStatesAction)
    broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
    broadcast.putExtra("address", state?.address)
    if (state != null) {
        val value = when (state) {
            is BLEGenerics.State.Connected -> "Connected"
            is BLEGenerics.State.Connecting -> "Connecting"
            is BLEGenerics.State.Disconnecting -> "Disconnecting"
            is BLEGenerics.State.Searching -> "Searching"
            is BLEGenerics.State.Waiting -> "Waiting"
            is BLEGenerics.State.Pairing -> "Pairing"
            is BLEGenerics.State.Unpairing -> "Unpairing"
        }
        broadcast.putExtra("state", value)
        when (state) {
            is BLEGenerics.State.Connected -> {
                broadcast.putExtra("isPaired", state.isPaired)
            }
            else -> {
                // noop
            }
        }
    }
    return broadcast
}

internal fun Context.getBroadcast(event: BLEGenerics.Event): Intent {
    val broadcast = Intent(BLEGenericsService.BLEGenericsEventsAction)
    broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
    broadcast.putExtra("address", event.address)
    val value = when (event) {
        is BLEGenerics.Event.OnConnect -> "OnConnect"
        is BLEGenerics.Event.OnDisconnect -> "OnDisconnect"
        is BLEGenerics.Event.OnPairing -> "OnPairing"
    }
    broadcast.putExtra("event", value)
    when (event) {
        is BLEGenerics.Event.OnPairing -> {
            broadcast.putExtra("isSuccess", event.isSuccess)
        }
        else -> {
            // noop
        }
    }
    return broadcast
}
