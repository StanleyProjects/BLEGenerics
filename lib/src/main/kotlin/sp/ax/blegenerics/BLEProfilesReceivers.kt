package sp.ax.blegenerics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object BLEProfilesReceivers {
    fun events(context: Context): Flow<BLEProfiles.Event> {
        return callbackFlow {
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val event = when (intent?.getStringExtra("event")) {
                        "OnServices" -> BLEProfiles.Event.OnServices
                        "OnMtuChanged" -> {
                            if (!intent.hasExtra("size")) TODO("BLEProfilesReceivers:events: $intent")
                            val size = intent.getIntExtra("size", -1)
                            BLEProfiles.Event.OnMtuChanged(size = size)
                        }
                        else -> return
                    }
                    trySend(event)
                }
            }
            register(
                context = context,
                receivers = receivers,
                filters = IntentFilter(BLEGenericsService.BLEProfilesEventsAction),
            )
            awaitClose {
                context.unregisterReceiver(receivers)
            }
        }
    }
}
