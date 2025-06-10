package sp.sample.blegenerics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import sp.ax.blegenerics.BLEGenerics

internal class DeviceService : Service() {
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)
    private val generics = App.generics // todo
    private val N_ID: Int = System.currentTimeMillis().toInt()

    private fun getBroadcast(state: BLEGenerics.State?): Intent {
        val broadcast = Intent("states") // todo
        broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
        broadcast.putExtra("address", state?.address)
        if (state != null) {
            val name = when (state) {
                is BLEGenerics.State.Connected -> "Connected"
                is BLEGenerics.State.Connecting -> "Connecting"
                is BLEGenerics.State.Disconnecting -> "Disconnecting"
                is BLEGenerics.State.Searching -> "Searching"
                is BLEGenerics.State.Waiting -> "Waiting"
            }
            broadcast.putExtra("name", name)
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

    private fun getBroadcast(event: BLEGenerics.Event): Intent {
        val broadcast = Intent("events") // todo
        broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
        broadcast.putExtra("address", event.address)
        val name = when (event) {
            is BLEGenerics.Event.OnConnect -> "OnConnect"
            is BLEGenerics.Event.OnDisconnect -> "OnDisconnect"
        }
        broadcast.putExtra("name", name)
        return broadcast
    }

    private fun onStatesNotification(channel: NotificationChannel, state: BLEGenerics.State): Notification {
        val context: Context = this
        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentText("$state")
            .setAutoCancel(false)
            .setOngoing(false)
        when (state) {
            is BLEGenerics.State.Connected, is BLEGenerics.State.Searching, is BLEGenerics.State.Waiting -> {
                val intent = Intent(context, DeviceService::class.java)
                intent.action = "disconnect"
                intent.putExtra("address", state.address)
                val stopIntent = PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
                val action = NotificationCompat.Action.Builder(-1, "disconnect", stopIntent)
                    .build()
                builder.addAction(action)
            }
            else -> {
                // noop
            }
        }
        return builder.build()
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
            generics.states.drop(1).collect { state ->
                sendBroadcast(getBroadcast(state = state))
                when (state) {
                    null -> stopSelf()
                    is BLEGenerics.State.Disconnecting -> stopForeground(STOP_FOREGROUND_REMOVE)
                    else -> {
                        val notification = onStatesNotification(channel = channel, state = state)
                        nm.notify(N_ID, notification)
                        startForeground(N_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    }
                }
            }
        }
        coroutineScope.launch {
            generics.events.collect { event ->
                sendBroadcast(getBroadcast(event = event))
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
                sendBroadcast(getBroadcast(generics.states.value))
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        fun states(context: Context): Flow<BLEGenerics.State?> {
            return callbackFlow {
                val receivers = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val address = intent?.getStringExtra("address")
                        if (address == null) {
                            trySend(null)
                        } else {
                            val state = when (intent.getStringExtra("name")) {
                                "Connecting" -> BLEGenerics.State.Connecting(address = address)
                                "Connected" -> {
                                    BLEGenerics.State.Connected(
                                        address = address,
                                        isPaired = intent.getBooleanExtra("isPaired", false)
                                    )
                                }
                                "Searching" -> BLEGenerics.State.Searching(address = address)
                                "Waiting" -> BLEGenerics.State.Waiting(address = address)
                                "Disconnecting" -> BLEGenerics.State.Disconnecting(address = address)
                                else -> return
                            }
                            trySend(state)
                        }
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

        fun events(context: Context): Flow<BLEGenerics.Event> {
            return callbackFlow {
                val receivers = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val address = intent?.getStringExtra("address") ?: return
                        val event = when (intent.getStringExtra("name")) {
                            "OnConnect" -> BLEGenerics.Event.OnConnect(address = address)
                            "OnDisconnect" -> BLEGenerics.Event.OnDisconnect(address = address)
                            else -> return
                        }
                        trySend(event)
                    }
                }
                val filters = IntentFilter("events") // todo
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
