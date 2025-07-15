package sp.ax.blegenerics

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.coroutines.CoroutineContext

internal object Mocks {
    var main: CoroutineContext = UnconfinedTestDispatcher()
    var generics: BLEGenerics? = null
}
