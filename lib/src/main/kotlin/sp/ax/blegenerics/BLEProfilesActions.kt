package sp.ax.blegenerics

import android.content.Context
import android.content.Intent

inline fun <reified T : BLEGenericsService> BLEProfiles.Companion.services(context: Context) {
    val intent = Intent(context, T::class.java)
    intent.action = BLEGenericsService.BLEProfilesServicesAction
    context.startService(intent)
}
