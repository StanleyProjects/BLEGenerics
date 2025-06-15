package sp.ax.blegenerics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

object BLEProfilesReceivers {
    fun events(context: Context): Flow<BLEProfiles.Event> {
        return callbackFlow {
            val receivers = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val event = when (intent?.getStringExtra("event")) {
                        "OnServices" -> {
                            val services = intent.getStringArrayExtra("services").orEmpty()
                            val characteristics = services.associate { service ->
                                UUID.fromString(service) to intent.getStringArrayExtra("characteristics:$service")
                                    ?.map(UUID::fromString).orEmpty().toSet()
                            }
                            BLEProfiles.Event.OnServices(characteristics = characteristics)
                        }
                        "OnMtuChanged" -> {
                            if (!intent.hasExtra("value")) TODO("BLEProfilesReceivers:events: $intent")
                            val value = intent.getIntExtra("value", -1)
                            BLEProfiles.Event.OnMtuChanged(value = value)
                        }
                        "Characteristic.OnSetNotification" -> {
                            val service = intent.getStringExtra("service") ?: return
                            val characteristic = intent.getStringExtra("characteristic") ?: return
                            val value = intent.getBooleanExtra("value", false)
                            BLEProfiles.Event.Characteristics.OnSetNotification(
                                service = UUID.fromString(service),
                                characteristic = UUID.fromString(characteristic),
                                value = value,
                            )
                        }
                        "Descriptors.OnWrite" -> {
                            val service = intent.getStringExtra("service") ?: return
                            val characteristic = intent.getStringExtra("characteristic") ?: return
                            val descriptor = intent.getStringExtra("descriptor") ?: return
                            val result = when (val bytes = intent.getByteArrayExtra("bytes")) {
                                null -> {
                                    val error = intent.getSerializableExtra("error") as? Throwable ?: return
                                    Result.failure(error)
                                }
                                else -> Result.success(bytes)
                            }
                            BLEProfiles.Event.Descriptors.OnWrite(
                                service = UUID.fromString(service),
                                characteristic = UUID.fromString(characteristic),
                                descriptor = UUID.fromString(descriptor),
                                result = result,
                            )
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
