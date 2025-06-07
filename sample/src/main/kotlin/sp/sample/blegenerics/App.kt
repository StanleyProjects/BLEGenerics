package sp.sample.blegenerics

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import sp.ax.blescanner.BLEScanner
import sp.ax.blescanner.RealBLEScanner
import kotlin.time.Duration.Companion.seconds

internal class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val job = SupervisorJob()
        _scanner = RealBLEScanner(
            coroutineScope = CoroutineScope(Dispatchers.Main + job),
            default = Dispatchers.Default,
            context = this,
            timeout = 3.seconds,
        )
    }

    companion object {
        private var _scanner: BLEScanner? = null
        val scanner: BLEScanner get() = checkNotNull(_scanner) { "No scanner!" }
    }
}
