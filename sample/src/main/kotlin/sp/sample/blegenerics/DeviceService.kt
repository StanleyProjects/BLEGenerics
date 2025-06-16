package sp.sample.blegenerics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import sp.ax.blegenerics.BLEGenerics
import sp.ax.blegenerics.BLEGenericsService

internal class DeviceService : BLEGenericsService(
    main = Dispatchers.Main,
    generics = App.generics,
    channel = NotificationChannel(
        "1de45173-0faf-4ce8-97b6-01517880d6a0",
        "${BuildConfig.APPLICATION_ID}:generics",
        NotificationManager.IMPORTANCE_HIGH,
    ),
) {
    override fun onStateNotification(channel: NotificationChannel, state: BLEGenerics.State?): Notification {
        val context: Context = this
        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentText("$state")
            .setAutoCancel(false)
            .setOngoing(false)
            .setSilent(true)
        when (state) {
            is BLEGenerics.State.Connected,
            is BLEGenerics.State.Searching,
            is BLEGenerics.State.Waiting -> {
                val intent = Intent(context, DeviceService::class.java)
                intent.action = BLEGenericsDisconnectAction
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
        if (state is BLEGenerics.State.Connected) {
            if (state.isPaired) {
                val intent = Intent(context, DeviceService::class.java)
                intent.action = BLEGenericsUnpairAction
                intent.putExtra("address", state.address)
                val stopIntent = PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
                val action = NotificationCompat.Action.Builder(-1, "unpair", stopIntent)
                    .build()
                builder.addAction(action)
            }
        }
        return builder.build()
    }
}
