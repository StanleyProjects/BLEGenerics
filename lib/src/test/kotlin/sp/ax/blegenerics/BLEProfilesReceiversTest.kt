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
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class BLEProfilesReceiversTest {
    @Config(application = MockApplication::class)
    @Test
    fun eventsTest() {
        runTest(timeout = 6.seconds) {
            val context: Context = RuntimeEnvironment.getApplication()
            var index = 0
            var bits: Long = 0
            var byte: Byte = 0
            val events = listOf(
                BLEProfiles.Event.OnServices(characteristics = emptyMap()),
                BLEProfiles.Event.OnServices(characteristics = mapOf(UUID(0, ++bits) to setOf())),
                BLEProfiles.Event.OnServices(characteristics = mapOf(UUID(0, ++bits) to setOf(UUID(0, ++bits)))),
                BLEProfiles.Event.OnMtuChanged(value = ++index),
                BLEProfiles.Event.Descriptors.OnWrite(service = UUID(0, ++bits), characteristic = UUID(0, ++bits), descriptor = UUID(0, ++bits), result = Result.success(byteArrayOf(++byte))),
                BLEProfiles.Event.Descriptors.OnWrite(service = UUID(0, ++bits), characteristic = UUID(0, ++bits), descriptor = UUID(0, ++bits), result = Result.failure(IllegalStateException("${++index}"))),
                BLEProfiles.Event.Characteristics.OnSetNotification(service = UUID(0, ++bits), characteristic = UUID(0, ++bits), value = false),
                BLEProfiles.Event.Characteristics.OnSetNotification(service = UUID(0, ++bits), characteristic = UUID(0, ++bits), value = true),
                BLEProfiles.Event.Characteristics.OnWrite(service = UUID(0, ++bits), characteristic = UUID(0, ++bits), result = Result.success(byteArrayOf(++byte))),
                BLEProfiles.Event.Characteristics.OnWrite(service = UUID(0, ++bits), characteristic = UUID(0, ++bits), result = Result.failure(IllegalStateException("${++index}"))),
                BLEProfiles.Event.Characteristics.OnChange(service = UUID(0, ++bits), characteristic = UUID(0, ++bits), bytes = byteArrayOf(++byte)),
            )
            val job = launch(CoroutineName("BLEProfiles.Event")) {
                BLEProfilesReceivers.events(context = context).take(events.size).collectIndexed { index, actual ->
                    if (index !in events.indices) error("Index $index is unexpected!")
                    assertEquals(events[index], actual)
                }
            }
            delay(1.seconds)
            events.forEach { event: BLEProfiles.Event ->
                context.sendBroadcast(context.getBroadcast(event = event))
            }
            job.join()
        }
    }
}
