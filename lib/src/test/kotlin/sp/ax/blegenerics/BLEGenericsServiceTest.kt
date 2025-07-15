package sp.ax.blegenerics

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectIndexed
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
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

    internal suspend fun Job.join(delay: Duration = 1.seconds, preJoin: suspend () -> Unit) {
        delay(delay)
        preJoin()
        join()
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun connectTest() {
        runTest(timeout = 6.seconds) {
            onMockGenerics { generics ->
                val application = RuntimeEnvironment.getApplication()
                onService<MockGenericsService>(generics = generics, application = application) { context, controller, intent ->
                    val address = "foobarbaz"
                    assertNull("before connect", generics.states.value)
                    launch(CoroutineName("connect")) {
                        BLEGenericsReceivers.states(context = context).take(2).collectIndexed { index, state ->
                            when (index) {
                                0 -> {
                                    check(state is BLEGenerics.State.Connecting)
                                    assertEquals(address, state.address)
                                }
                                1 -> {
                                    check(state is BLEGenerics.State.Connected)
                                    assertEquals(address, state.address)
                                    assertFalse(state.isPaired)
                                }
                                else -> error("Index $index is unexpected!")
                            }
                        }
                    }.join {
                        intent.action = BLEGenericsService.BLEGenericsConnectAction
                        intent.putExtra("address", address)
                        controller.startCommand(intent)
                    }
                    assertTrue("after connect", generics.states.value is BLEGenerics.State.Connected)
                }
            }
        }
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun disconnectTest() {
        runTest(timeout = 6.seconds) {
            onMockGenerics { generics ->
                val application = RuntimeEnvironment.getApplication()
                onService<MockGenericsService>(generics = generics, application = application) { context, controller, intent ->
                    val address = "foobarbaz"
                    assertNull("before connect", generics.states.value)
                    launch(CoroutineName("connect")) {
                        BLEGenericsReceivers.states(context = context).take(2).collectIndexed { index, state ->
                            when (index) {
                                0 -> {
                                    check(state is BLEGenerics.State.Connecting)
                                    assertEquals(address, state.address)
                                }
                                1 -> {
                                    check(state is BLEGenerics.State.Connected)
                                    assertEquals(address, state.address)
                                    assertFalse(state.isPaired)
                                }
                                else -> error("Index $index is unexpected!")
                            }
                        }
                    }.join {
                        intent.action = BLEGenericsService.BLEGenericsConnectAction
                        intent.putExtra("address", address)
                        controller.startCommand(intent)
                    }
                    assertTrue("after connect", generics.states.value is BLEGenerics.State.Connected)
                    launch(CoroutineName("connect")) {
                        BLEGenericsReceivers.states(context = context).take(2).collectIndexed { index, state ->
                            when (index) {
                                0 -> {
                                    check(state is BLEGenerics.State.Disconnecting)
                                    assertEquals(address, state.address)
                                }
                                1 -> assertNull(state)
                                else -> error("Index $index is unexpected!")
                            }
                        }
                    }.join {
                        intent.action = BLEGenericsService.BLEGenericsDisconnectAction
                        controller.startCommand(intent)
                    }
                    assertNull("after disconnect", generics.states.value)
                }
            }
        }
    }
}
