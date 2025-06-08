package sp.sample.blegenerics

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import sp.ax.blegenerics.BLEGenerics

internal class DeviceService : Service() {
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)
    private val generics = App.generics // todo

    private fun getBroadcast(states: Map<String, BLEGenerics.State>): Intent {
        val broadcast = Intent("states") // todo
        broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
        val entries = states.entries.toList()
        broadcast.putExtra("addresses", entries.map { (address, _) -> address }.toTypedArray())
        val names = entries.map { (_, value) ->
            when (value) {
                is BLEGenerics.State.Connected -> "Connected"
                BLEGenerics.State.Connecting -> "Connecting"
                BLEGenerics.State.Disconnecting -> "Disconnecting"
            }
        }
        broadcast.putExtra("names", names.toTypedArray())
        entries.forEach { (address, value) ->
            when (value) {
                is BLEGenerics.State.Connected -> broadcast.putExtra("$address:paired", value.isPaired)
                else -> {
                    // noop
                }
            }
        }
        return broadcast
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "1de45173-0faf-4ce8-97b6-01517880d6a0",
            "${BuildConfig.APPLICATION_ID}:generics",
            NotificationManager.IMPORTANCE_HIGH,
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channel.id) == null) {
            nm.createNotificationChannel(channel)
        }
        coroutineScope.launch {
            generics.states.collect { states ->
                sendBroadcast(getBroadcast(states = states))
                // todo notifications
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "connect" -> {
                val address = intent.getStringExtra("address")
                if (address.isNullOrBlank()) TODO("DeviceService:onStartCommand($intent)")
                generics.connect(address = address)
            }
            "disconnect" -> {
                val address = intent.getStringExtra("address")
                if (address.isNullOrBlank()) TODO("DeviceService:onStartCommand($intent)")
                generics.disconnect(address = address)
            }
            "states" -> {
                sendBroadcast(getBroadcast(states = generics.states.value))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        fun states(context: Context): Flow<Map<String, BLEGenerics.State>> {
            return callbackFlow {
                val receivers = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val addresses = intent?.getStringArrayExtra("addresses") ?: return
                        val names = intent.getStringArrayExtra("names") ?: return
                        if (addresses.size != names.size) return
                        val states = mutableMapOf<String, BLEGenerics.State>()
                        for (index in addresses.indices) {
                            val address = addresses[index]
                            val name = names[index]
                            val state = when (name) {
                                "Connected" -> {
                                    val isPaired = intent.getBooleanExtra("$address:paired", false)
                                    BLEGenerics.State.Connected(isPaired = isPaired)
                                }
                                "Connecting" -> BLEGenerics.State.Connecting
                                "Disconnecting" -> BLEGenerics.State.Disconnecting
                                else -> continue
                            }
                            states[address] = state
                        }
                        trySend(states)
                    }
                }
                val filters = IntentFilter("states") // todo
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        receivers,
                        filters,
                        RECEIVER_NOT_EXPORTED,
                    )
                } else {
                    context.registerReceiver(receivers, filters)
                }
                awaitClose {
                    context.unregisterReceiver(receivers)
                }
            }
        }
    }
}
