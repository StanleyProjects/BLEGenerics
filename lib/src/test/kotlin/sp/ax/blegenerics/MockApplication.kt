package sp.ax.blegenerics

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

internal class MockApplication : Application() {
    private val receivers = mutableMapOf<String, BroadcastReceiver>()

    override fun getBaseContext(): Context {
        return this // todo
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        flags: Int,
    ): Intent? {
        if (filter == null) TODO("MockApplication:registerReceiver($flags):no filter!")
//        if (filter.countActions() != 1) TODO("MockApplication:registerReceiver($flags):${filter.countActions()} actions!")
        if (filter.countActions() == 0) TODO("MockApplication:registerReceiver($flags):${filter.countActions()} actions!")
//        val action = filter.actionsIterator().next()
        val key = filter.actionsIterator().asSequence().joinToString { it }
        if (receiver == null) TODO("MockApplication:registerReceiver($flags):no receiver!")
        receivers[key] = receiver
        return null
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
    ): Intent? {
        if (filter == null) TODO("MockApplication:registerReceiver:no filter!")
//        if (filter.countActions() != 1) TODO("MockApplication:registerReceiver:${filter.countActions()} actions!")
        if (filter.countActions() == 0) TODO("MockApplication:registerReceiver:${filter.countActions()} actions!")
//        val action = filter.actionsIterator().next()
        val key = filter.actionsIterator().asSequence().joinToString { it }
        if (receiver == null) TODO("MockApplication:registerReceiver:no receiver!")
        receivers[key] = receiver
        return null
    }

    override fun sendBroadcast(intent: Intent?) {
        if (intent == null) TODO("MockApplication:sendBroadcast:no intent!")
        if (intent.getPackage() != packageName) TODO("MockApplication:sendBroadcast:package: ${intent.getPackage()}!")
        val receiver = receivers.entries.single { (key, _) -> key.contains(intent.action.orEmpty()) }.value
        receiver.onReceive(this, intent)
    }

    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        for ((action, actual) in receivers) {
            if (actual === receiver) {
                receivers.remove(action)
                return
            }
        }
        TODO("MockApplication:unregisterReceiver(${receiver?.hashCode()}):no receiver!")
    }
}
