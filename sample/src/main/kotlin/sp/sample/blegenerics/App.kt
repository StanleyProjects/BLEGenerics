package sp.sample.blegenerics

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import sp.ax.blegenerics.BLEGenerics
import sp.ax.blegenerics.BLEGenericsLogger
import sp.ax.blegenerics.RealBLEGenerics
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
        _generics = RealBLEGenerics(
            coroutineScope = CoroutineScope(Dispatchers.Main + job),
            default = Dispatchers.Default,
            context = this,
            logger = FinalBLEGenericsLogger(
                tag = "[BLEGenerics]",
                level = BLEGenericsLogger.Level.Info,
            ),
        )
        _locals = FinalLocals(context = this)
        _flows = Flows(
            themes = MutableStateFlow(ThemeState.Light),
            devices = MutableStateFlow(locals.selectedDevice),
        )
    }

    companion object {
        private var _scanner: BLEScanner? = null
        val scanner: BLEScanner get() = checkNotNull(_scanner) { "No scanner!" }
        private var _generics: BLEGenerics? = null
        val generics: BLEGenerics get() = checkNotNull(_generics) { "No generics!" }
        private var _locals: Locals? = null
        val locals: Locals get() = checkNotNull(_locals) { "No locals!" }
        private var _flows: Flows? = null
        val flows: Flows get() = checkNotNull(_flows) { "No flows!" }
    }
}
