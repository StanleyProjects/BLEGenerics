package sp.ax.blegenerics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
}
