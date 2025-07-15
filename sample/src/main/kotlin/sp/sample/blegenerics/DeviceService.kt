package sp.sample.blegenerics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sp.ax.blegenerics.BLEGenerics
import sp.ax.blegenerics.BLEGenericsService
import sp.ax.blescanner.BLEDevice

internal class DeviceService : BLEGenericsService(
    main = Dispatchers.Main,
    generics = App.generics,
    channel = NotificationChannel(
        "1de45173-0faf-4ce8-97b6-01517880d6a0",
        "${BuildConfig.APPLICATION_ID}:generics",
        NotificationManager.IMPORTANCE_HIGH,
    ),
) {
    override fun onCreate() {
        super.onCreate()
        coroutineScope.launch {
            App.flows.themes.collect { themeState ->
                val notification = buildNotification(themeState = themeState)
                notify(notification)
            }
        }
        coroutineScope.launch {
            App.flows.devices.collect { device ->
                println("[DeviceService]:device: $device") // todo
                val notification = buildNotification(device = device)
                notify(notification)
            }
        }
        coroutineScope.launch {
            states.collect { state ->
                println("[DeviceService]:state: $state") // todo
                val notification = buildNotification(state = state)
                notify(notification)
            }
        }
    }

    override fun onStartForeground(): Notification {
        return buildNotification()
    }

    private fun buildNotification(
        themeState: ThemeState = App.flows.themes.value,
        device: BLEDevice? = App.flows.devices.value,
        state: BLEGenerics.State? = states.value,
    ): Notification {
        val context: Context = this
        val text = if (device == null) {
            "no device"
        } else when (state) {
            is BLEGenerics.State.Connected -> "${state::class.java.simpleName} ${device.address} paired ${state.isPaired}"
            null -> "Disconnected ${device.address}"
            else -> "${state::class.java.simpleName} ${device.address}"
        }
        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentText(text)
            .setAutoCancel(false)
            .setOngoing(false)
            .setSilent(true)
            .setColor(themeState.background.toArgb())
            .setColorized(true)
        when (state) {
            is BLEGenerics.State.Connected,
            is BLEGenerics.State.Searching,
            is BLEGenerics.State.Waiting -> {
                val intent = Intent(context, DeviceService::class.java)
                intent.action = BLEGenericsDisconnectAction
                intent.putExtra("address", state.address)
                val disconnectIntent = PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
                val action = NotificationCompat.Action.Builder(-1, "disconnect", disconnectIntent)
                    .build()
                builder.addAction(action)
            }
            else -> {
                // noop
            }
        }
        if (state is BLEGenerics.State.Connected) {
            if (state.isPaired) {
                val intent = Intent(context, DeviceService::class.java)
                intent.action = BLEGenericsUnpairAction
                intent.putExtra("address", state.address)
                val unpairIntent = PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
                val action = NotificationCompat.Action.Builder(-1, "unpair", unpairIntent)
                    .build()
                builder.addAction(action)
            }
        }
        return builder.build()
    }
}
