package sp.ax.blegenerics

import android.content.Context
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class BLEGenericsReceiversTest {
    @Config(application = MockApplication::class)
    @Test
    fun statesTest() {
        runTest(timeout = 6.seconds) {
            val context: Context = RuntimeEnvironment.getApplication()
            var index = 0
            val states = listOf(
                null,
                BLEGenerics.State.Connecting(address = "address: ${++index}"),
                BLEGenerics.State.Connected(address = "address: ${++index}", isPaired = false),
                BLEGenerics.State.Connected(address = "address: ${++index}", isPaired = true),
                BLEGenerics.State.Pairing(address = "address: ${++index}"),
                BLEGenerics.State.Unpairing(address = "address: ${++index}"),
                BLEGenerics.State.Searching(address = "address: ${++index}"),
                BLEGenerics.State.Waiting(address = "address: ${++index}"),
                BLEGenerics.State.Disconnecting(address = "address: ${++index}"),
            )
            val job = launch(CoroutineName("states")) {
                BLEGenericsReceivers.states(context = context).take(states.size).collectIndexed { index, actual ->
                    if (index !in states.indices) error("Index $index is unexpected!")
                    assertEquals(states[index], actual)
                }
            }
            delay(1.seconds)
            states.forEach { state: BLEGenerics.State? ->
                context.sendBroadcast(context.getBroadcast(state = state))
            }
            job.join()
        }
    }

    @Config(application = MockApplication::class)
    @Test
    fun eventsTest() {
        runTest(timeout = 6.seconds) {
            val context: Context = RuntimeEnvironment.getApplication()
            var index = 0
            val events = listOf(
                BLEGenerics.Event.OnConnect(address = "address: ${++index}"),
                BLEGenerics.Event.OnDisconnect(address = "address: ${++index}"),
                BLEGenerics.Event.OnPairing(address = "address: ${++index}", isSuccess = false),
                BLEGenerics.Event.OnPairing(address = "address: ${++index}", isSuccess = true),
            )
            val job = launch(CoroutineName("states")) {
                BLEGenericsReceivers.events(context = context).take(events.size).collectIndexed { index, actual ->
                    if (index !in events.indices) error("Index $index is unexpected!")
                    assertEquals(events[index], actual)
                }
            }
            delay(1.seconds)
            events.forEach { event: BLEGenerics.Event ->
                context.sendBroadcast(context.getBroadcast(event = event))
            }
            job.join()
        }
    }
}
