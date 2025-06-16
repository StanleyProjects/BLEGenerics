package sp.sample.blegenerics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import sp.ax.blescanner.BLEScannerService

internal class ScannerService : BLEScannerService(
    main = Dispatchers.Main,
    scanner = App.scanner,
    channel = NotificationChannel(
        "df31b3a7-656f-4ac3-87f2-300e21f6b8e1",
        "${BuildConfig.APPLICATION_ID}:scanner",
        NotificationManager.IMPORTANCE_HIGH,
    ),
) {
    override fun onStartNotification(channel: NotificationChannel): Notification {
        val context: Context = this
        val intent = Intent(context, ScannerService::class.java)
        intent.action = BLEScannerStopAction
        val stopIntent = PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val action = NotificationCompat.Action.Builder(-1, "stop", stopIntent)
            .build()
        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentText("scanner:text")
            .setAutoCancel(false)
            .setOngoing(false)
            .setSilent(true)
            .addAction(action)
            .build()
    }
}
