package sp.ax.blegenerics

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class RealBLEGenericsTest {
    private suspend fun TestScope.onRealBLEGenerics(
        main: CoroutineContext = StandardTestDispatcher(testScheduler, "real:generics:main"),
        default: CoroutineContext = StandardTestDispatcher(testScheduler, "real:generics:default"),
        context: Context,
        block: suspend (BLEGenerics) -> Unit,
    ) {
        val job = SupervisorJob()
        val scanner = RealBLEGenerics(
            coroutineScope = CoroutineScope(main + job),
            default = default + job,
            context = context,
            logger = MockGenericsLogger(),
        )
        block(scanner)
        job.cancel()
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun waitingTest() {
        runTest(timeout = 6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val context: Context = application
            onRealBLEGenerics(context = context) { generics ->
                val address = "foobarbaz"
                assertNull("before connect", generics.states.value)
                val job = launch(CoroutineName("connect")) {
                    generics.states.take(2).collectIndexed { index, state ->
                        when (index) {
                            0 -> assertNull(state)
                            1 -> {
                                check(state is BLEGenerics.State.Waiting) { "$index] state: $state" }
                                assertEquals(address, state.address)
                            }
                            else -> error("Index $index is unexpected!")
                        }
                    }
                }
                generics.connect(address = address)
                job.join()
                assertTrue("after connect", generics.states.value is BLEGenerics.State.Waiting)
            }
        }
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun connectedTest() {
        runTest(timeout = 6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val context: Context = application
            onRealBLEGenerics(context = context) { generics ->
                val address = "foobarbaz"
                assertNull("before connect", generics.states.value)
                var job = launch(CoroutineName("connect")) {
                    generics.states.take(3).collectIndexed { index, state ->
                        when (index) {
                            0 -> assertNull(state)
                            1 -> {
                                check(state is BLEGenerics.State.Connecting) { "$index] state: $state" }
                                assertEquals(address, state.address)
                            }
                            2 -> {
                                check(state is BLEGenerics.State.Connected) { "$index] state: $state" }
                                assertEquals(address, state.address)
                                assertFalse(state.isPaired)
                            }
                            else -> error("Index $index is unexpected!")
                        }
                    }
                }
                generics.connect(address = address)
                job.join()
                assertTrue("after connect", generics.states.value is BLEGenerics.State.Connected)
                job = launch(CoroutineName("states")) {
                    generics.states.take(1).collectIndexed { index, state ->
                        when (index) {
                            0 -> {
                                check(state is BLEGenerics.State.Connected) { "$index] state: $state" }
                                assertEquals(address, state.address)
                                assertFalse(state.isPaired)
                            }
                            else -> error("Index $index is unexpected!")
                        }
                    }
                }
                job.join()
            }
        }
    }
}
