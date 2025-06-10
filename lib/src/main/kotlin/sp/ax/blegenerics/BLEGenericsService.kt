package sp.ax.blegenerics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

abstract class BLEGenericsService(
    main: CoroutineContext,
    private val generics: BLEGenerics,
    private val channel: NotificationChannel,
) : Service() {
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(main + job)
    private val N_ID: Int = System.currentTimeMillis().toInt()

    private fun getBroadcast(state: BLEGenerics.State?): Intent {
        val broadcast = Intent(BLEGenericsStatesAction)
        broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
        broadcast.putExtra("address", state?.address)
        if (state != null) {
            val name = when (state) {
                is BLEGenerics.State.Connected -> "Connected"
                is BLEGenerics.State.Connecting -> "Connecting"
                is BLEGenerics.State.Disconnecting -> "Disconnecting"
                is BLEGenerics.State.Searching -> "Searching"
                is BLEGenerics.State.Waiting -> "Waiting"
                is BLEGenerics.State.Pairing -> "Pairing"
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
        val broadcast = Intent(BLEGenericsEventsAction)
        broadcast.setPackage(packageName) // https://stackoverflow.com/a/76920719/4398606
        broadcast.putExtra("address", event.address)
        val name = when (event) {
            is BLEGenerics.Event.OnConnect -> "OnConnect"
            is BLEGenerics.Event.OnDisconnect -> "OnDisconnect"
        }
        broadcast.putExtra("name", name)
        return broadcast
    }

    protected abstract fun onStateNotification(channel: NotificationChannel, state: BLEGenerics.State?): Notification

    override fun onCreate() {
        super.onCreate()
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
                        val notification = onStateNotification(channel = channel, state = state)
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
        println("[BLEGenericsService]:onStartCommand(${intent?.action} ${intent?.extras?.keySet()?.toList()})") // todo
        when (intent?.action) {
            BLEGenericsConnectAction -> {
                val address = intent.getStringExtra("address")
                if (address.isNullOrBlank()) TODO("DeviceService:onStartCommand($intent)")
                generics.connect(address = address)
            }
            BLEGenericsDisconnectAction -> {
                val address = intent.getStringExtra("address")
                if (address.isNullOrBlank()) TODO("DeviceService:onStartCommand($intent)")
                generics.disconnect(address = address)
            }
            BLEGenericsStatesAction -> {
                sendBroadcast(getBroadcast(generics.states.value))
            }
            BLEGenericsPairAction -> {
                val address = intent.getStringExtra("address")
                if (address.isNullOrBlank()) TODO("DeviceService:onStartCommand($intent)")
                val pin = intent.getStringExtra("pin")
                generics.pair(address = address, pin = pin)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        const val BLEGenericsStatesAction = "sp.ax.blegenerics.BLEGenericsStatesAction"
        const val BLEGenericsConnectAction = "sp.ax.blegenerics.BLEGenericsConnectAction"
        const val BLEGenericsDisconnectAction = "sp.ax.blegenerics.BLEGenericsDisconnectAction"
        const val BLEGenericsEventsAction = "sp.ax.blegenerics.BLEGenericsEventsAction"
        const val BLEGenericsPairAction = "sp.ax.blegenerics.BLEGenericsPairAction"
    }
}
