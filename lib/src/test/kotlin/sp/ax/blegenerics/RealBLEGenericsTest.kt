package sp.ax.blegenerics

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBluetoothDevice
import org.robolectric.shadows.ShadowBluetoothGatt
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class RealBLEGenericsTest {
    private suspend fun onRealBLEGenerics(
        main: CoroutineContext,
        default: CoroutineContext = main,
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

    private suspend fun TestScope.onRealBLEGenerics(
        context: Context,
        block: suspend (BLEGenerics) -> Unit,
    ) {
        onRealBLEGenerics(
            main = StandardTestDispatcher(testScheduler, "real:generics:main"),
            default = StandardTestDispatcher(testScheduler, "real:generics:default"),
            context = context,
            block = block,
        )
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun waitingTest() {
        runTest(timeout = 6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val context: Context = application
            val bm = context.getSystemService(BluetoothManager::class.java)
            check(!bm.adapter.isEnabled)
            check(application.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            check(application.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
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
    fun searchingTest() = runBlocking {
        withTimeout(6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val context: Context = application
            //
            val bm = context.getSystemService(BluetoothManager::class.java)
            check(bm.adapter.enable())
            check(bm.adapter.isEnabled)
            //
            val shadow = Shadows.shadowOf(application)
            shadow.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            check(application.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            shadow.grantPermissions(Manifest.permission.BLUETOOTH_SCAN)
            check(application.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
            //
            onRealBLEGenerics(
                main = coroutineContext,
                context = context,
            ) { generics ->
                val address = "00:00:00:00:00:00"
                assertNull("before connect", generics.states.value)
                val job = launch(CoroutineName("connect")) {
                    generics.states.take(3).collectIndexed { index, state ->
                        println("$index] $state") // todo
                        when (index) {
                            0 -> assertNull(state)
                            1 -> {
                                check(state is BLEGenerics.State.Connecting) { "$index] state: $state" }
                                assertEquals(address, state.address)
                            }
                            2 -> {
                                check(state is BLEGenerics.State.Searching) { "$index] state: $state" }
                                assertEquals(address, state.address)
                            }
                            else -> error("Index $index is unexpected!")
                        }
                    }
                }
                generics.connect(address = address)
                job.join()
                assertTrue("after connect", generics.states.value is BLEGenerics.State.Searching)
            }
        }
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun connectedTest() = runBlocking {
        withTimeout(6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val context: Context = application
            //
            val bm = context.getSystemService(BluetoothManager::class.java)
            check(bm.adapter.enable())
            check(bm.adapter.isEnabled)
            //
            val shadow = Shadows.shadowOf(application)
            shadow.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            check(application.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            shadow.grantPermissions(Manifest.permission.BLUETOOTH_SCAN)
            check(application.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
            //
            onRealBLEGenerics(
                main = coroutineContext,
                context = context,
            ) { generics ->
                val address = "00:00:00:00:00:00"
                assertNull("before connect", generics.states.value)
                var job = launch(CoroutineName("connect")) {
                    generics.states.take(3).collectIndexed { index, state ->
                        println("$index] $state") // todo
                        when (index) {
                            0 -> assertNull(state)
                            1 -> {
                                check(state is BLEGenerics.State.Connecting) { "$index] state: $state" }
                                assertEquals(address, state.address)
                                val device = bm.adapter.getRemoteDevice(address) ?: error("No device!")
                                val gatt = Shadows.shadowOf(device).bluetoothGatts.single() ?: error("No gatt!")
                                val callback = Shadows.shadowOf(gatt).gattCallback ?: error("No callback!")
                                callback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)
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
