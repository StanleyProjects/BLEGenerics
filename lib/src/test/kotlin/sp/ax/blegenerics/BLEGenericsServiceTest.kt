package sp.ax.blegenerics

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class BLEGenericsServiceTest {
    private inline fun <reified T : BLEGenericsService> onService(
        generics: BLEGenerics,
        application: Application = RuntimeEnvironment.getApplication(),
        main: CoroutineContext = Mocks.main,
        block: (context: Context, controller: ServiceController<T>, intent: Intent) -> Unit,
    ) {
        Mocks.generics = generics
        Mocks.main = main
        val context: Context = application
        val controller = Robolectric.buildService(T::class.java)
        controller.create()
        val intent = Intent(context, T::class.java)
        block(context, controller, intent)
    }

    private suspend fun TestScope.onMockGenerics(
        main: CoroutineContext = StandardTestDispatcher(testScheduler, "mock:generics:main"),
        default: CoroutineContext = StandardTestDispatcher(testScheduler, "mock:generics:default"),
        defaultState: BLEGenerics.State? = null,
        block: suspend (BLEGenerics) -> Unit,
    ) {
        val job = SupervisorJob()
        val scanner = MockGenerics(
            coroutineScope = CoroutineScope(main + job),
            default = default + job,
            defaultState = defaultState,
        )
        block(scanner)
        job.cancel()
    }

    private fun <T : Service> ServiceController<T>.startCommand(intent: Intent, flags: Int = 0, startId: Int = 0) {
        withIntent(intent).startCommand(flags, startId)
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun connectTest() {
        runTest(timeout = 6.seconds) {
            onMockGenerics { generics ->
                val application = RuntimeEnvironment.getApplication()
                onService<MockGenericsService>(generics = generics, application = application) { context, controller, intent ->
                    assertNull("before connect", generics.states.value)
                    val job = launch(CoroutineName("connect")) {
                        BLEGenericsReceivers.states(context = context).take(2).collectIndexed { index, state ->
                            when (index) {
                                0 -> assertTrue(state is BLEGenerics.State.Connecting)
                                1 -> assertTrue(state is BLEGenerics.State.Connected)
                                else -> error("Index $index is unexpected!")
                            }
                        }
                    }
                    intent.action = BLEGenericsService.BLEGenericsConnectAction
                    intent.putExtra("address", "foo")
                    controller.startCommand(intent)
                    job.join()
                    assertTrue("after connect", generics.states.value is BLEGenerics.State.Connected)
                }
            }
        }
    }
}
