package sp.ax.blegenerics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Parcelable
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
        coroutineScope.launch {
            generics.profiles.events.collect { event ->
                sendBroadcast(getBroadcast(event = event))
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BLEGenericsConnectAction -> {
                val address = intent.getStringExtra("address")
                if (address.isNullOrBlank()) TODO("DeviceService:onStartCommand($intent)")
                generics.connect(address = address)
            }
            BLEGenericsDisconnectAction -> {
                generics.disconnect()
            }
            BLEGenericsStatesAction -> {
                sendBroadcast(getBroadcast(generics.states.value))
            }
            BLEGenericsPairAction -> {
                val pin = intent.getStringExtra("pin")
                generics.pair(pin = pin)
            }
            BLEGenericsUnpairAction -> {
                generics.unpair()
            }
            BLEProfilesOperationsAction -> {
                val parcelable = intent.getParcelableExtra<Parcelable>("operation")
                if (parcelable !is OperationParcelable) TODO("DeviceService:onStartCommand($intent)")
                generics.profiles.perform(operation = parcelable.delegate)
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
        const val BLEGenericsUnpairAction = "sp.ax.blegenerics.BLEGenericsUnpairAction"
        const val BLEProfilesEventsAction = "sp.ax.blegenerics.BLEProfilesEventsAction"
        const val BLEProfilesOperationsAction = "sp.ax.blegenerics.BLEProfilesOperationsAction"
    }
}
