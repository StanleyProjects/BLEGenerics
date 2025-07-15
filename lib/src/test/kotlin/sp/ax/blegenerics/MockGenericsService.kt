package sp.ax.blegenerics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager

private fun mockNotificationChannel(
    id: String = "MockNotificationChannel:id",
    name: CharSequence = "MockNotificationChannel:name",
    importance: Int = NotificationManager.IMPORTANCE_HIGH,
): NotificationChannel {
    return NotificationChannel(id, name, importance)
}

internal class MockGenericsService : BLEGenericsService(
    main = Mocks.main,
    generics = Mocks.generics ?: error("No generics!"),
    channel = mockNotificationChannel(),
) {
    override fun onStartForeground(): Notification {
        return Notification()
    }
}
