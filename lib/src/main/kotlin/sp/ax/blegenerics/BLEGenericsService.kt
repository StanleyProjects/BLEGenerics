package sp.ax.blegenerics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

abstract class BLEGenericsService(
    main: CoroutineContext,
    private val generics: BLEGenerics,
    protected val channel: NotificationChannel,
) : Service() {
    private val job = SupervisorJob()
    protected val coroutineScope = CoroutineScope(main + job)
    private val N_ID: Int = System.currentTimeMillis().toInt()
    protected val states: StateFlow<BLEGenerics.State?> get() = generics.states

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channel.id) == null) {
            nm.createNotificationChannel(channel)
        }
        coroutineScope.launch {
            var state: BLEGenerics.State? = null
            generics.states.drop(1).collect { newState ->
                val oldState = state
                state = newState
                sendBroadcast(getBroadcast(state = newState))
                if (oldState == null && newState != null) {
                    val notification = onStartForeground()
                    nm.notify(N_ID, notification)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(N_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    } else {
                        startForeground(N_ID, notification)
                    }
                } else if (oldState != null && newState == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
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

    protected abstract fun onStartForeground(): Notification

    protected fun notify(notification: Notification) {
        if (generics.states.value == null) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(N_ID, notification)
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
