package sp.ax.blegenerics

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
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
import java.util.UUID
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

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun servicesTest() = runBlocking {
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
                val expected = BluetoothGattService(UUID(0, 1), BluetoothGattService.SERVICE_TYPE_PRIMARY)
                job = launch(CoroutineName("services")) {
                    generics.profiles.events.take(1).collectIndexed { index, event ->
                        when (index) {
                            0 -> {
                                check(event is BLEProfiles.Event.OnServices)
                                assertEquals(expected.uuid, event.characteristics.keys.single())
                            }
                            else -> error("Index $index is unexpected!")
                        }
                    }
                }
                val device = bm.adapter.getRemoteDevice(address) ?: error("No device!")
                val gatt = Shadows.shadowOf(device).bluetoothGatts.single() ?: error("No gatt!")
                Shadows.shadowOf(gatt).addDiscoverableService(expected)
                val operation = BLEProfiles.Operation.Services
                generics.profiles.perform(operation = operation)
                job.join()
            }
        }
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun descriptorsTest() = runBlocking {
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
                val device = bm.adapter.getRemoteDevice(address) ?: error("No device!")
                val gatt = Shadows.shadowOf(device).bluetoothGatts.single() ?: error("No gatt!")
                val service = BluetoothGattService(UUID(0, 1), BluetoothGattService.SERVICE_TYPE_PRIMARY)
                Shadows.shadowOf(gatt).addDiscoverableService(service)
                val characteristic = BluetoothGattCharacteristic(UUID(1, 1), 0, 0)
                service.addCharacteristic(characteristic)
                val descriptor = BluetoothGattDescriptor(UUID(1, 1), 0)
                characteristic.addDescriptor(descriptor)
                val expected = "foobarbaz".toByteArray()
                job = launch(CoroutineName("services")) {
                    generics.profiles.events.take(2).collectIndexed { index, event ->
                        when (index) {
                            0 -> {
                                check(event is BLEProfiles.Event.OnServices)
                                assertEquals(service.uuid, event.characteristics.keys.single())
                                assertEquals(characteristic.uuid, event.characteristics.entries.single().value.single())
                                val operation = BLEProfiles.Operation.Descriptors.Write(
                                    service = service.uuid,
                                    characteristic = characteristic.uuid,
                                    descriptor = descriptor.uuid,
                                    bytes = expected,
                                )
                                generics.profiles.perform(operation = operation)
                            }
                            1 -> {
                                check(event is BLEProfiles.Event.Descriptors.OnWrite)
                                assertEquals(service.uuid, event.service)
                                assertEquals(characteristic.uuid, event.characteristic)
                                assertEquals(descriptor.uuid, event.descriptor)
                                assertTrue(event.result.isSuccess)
                                val actual = checkNotNull(event.result.getOrNull())
                                assertTrue(expected.contentEquals(actual))
                            }
                            else -> error("Index $index is unexpected!")
                        }
                    }
                }
                generics.profiles.perform(operation = BLEProfiles.Operation.Services)
                job.join()
            }
        }
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun descriptorsFailureTest() = runBlocking {
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
                val device = bm.adapter.getRemoteDevice(address) ?: error("No device!")
                val gatt = Shadows.shadowOf(device).bluetoothGatts.single() ?: error("No gatt!")
                val service = BluetoothGattService(UUID(0, 1), BluetoothGattService.SERVICE_TYPE_PRIMARY)
                val characteristic = BluetoothGattCharacteristic(UUID(1, 1), 0, 0)
                val descriptor = BluetoothGattDescriptor(UUID(1, 1), 0)
                val expected = "foobarbaz".toByteArray()
                check(gatt.services.isEmpty())
                val operation = BLEProfiles.Operation.Descriptors.Write(
                    service = service.uuid,
                    characteristic = characteristic.uuid,
                    descriptor = descriptor.uuid,
                    bytes = expected,
                )
                job = launch(CoroutineName("descriptors")) {
                    generics.profiles.events.take(5).collectIndexed { index, event ->
                        when (index) {
                            0 -> {
                                check(event is BLEProfiles.Event.Descriptors.OnWrite)
                                assertEquals(service.uuid, event.service)
                                assertEquals(characteristic.uuid, event.characteristic)
                                assertEquals(descriptor.uuid, event.descriptor)
                                assertTrue(event.result.isFailure)
                                val actual = checkNotNull(event.result.exceptionOrNull())
                                assertTrue(actual is IllegalStateException)
                                assertEquals("No service ${service.uuid}!", actual.message)
                                Shadows.shadowOf(gatt).addDiscoverableService(service)
                                generics.profiles.perform(operation = BLEProfiles.Operation.Services)
                            }
                            1 -> {
                                check(event is BLEProfiles.Event.OnServices)
                                generics.profiles.perform(operation = operation)
                            }
                            2 -> {
                                check(event is BLEProfiles.Event.Descriptors.OnWrite)
                                assertEquals(service.uuid, event.service)
                                assertEquals(characteristic.uuid, event.characteristic)
                                assertEquals(descriptor.uuid, event.descriptor)
                                assertTrue(event.result.isFailure)
                                val actual = checkNotNull(event.result.exceptionOrNull())
                                assertTrue(actual is IllegalStateException)
                                assertEquals("No characteristic ${characteristic.uuid}!", actual.message)
                                service.addCharacteristic(characteristic)
                                Shadows.shadowOf(gatt).addDiscoverableService(service)
                                generics.profiles.perform(operation = BLEProfiles.Operation.Services)
                            }
                            3 -> {
                                check(event is BLEProfiles.Event.OnServices)
                                generics.profiles.perform(operation = operation)
                            }
                            4 -> {
                                check(event is BLEProfiles.Event.Descriptors.OnWrite)
                                assertEquals(service.uuid, event.service)
                                assertEquals(characteristic.uuid, event.characteristic)
                                assertEquals(descriptor.uuid, event.descriptor)
                                assertTrue(event.result.isFailure)
                                val actual = checkNotNull(event.result.exceptionOrNull())
                                assertTrue(actual is IllegalStateException)
                                assertEquals("No descriptor ${descriptor.uuid}!", actual.message)
                            }
                            else -> error("Index $index is unexpected!")
                        }
                    }
                }
                generics.profiles.perform(operation = operation)
                job.join()
            }
        }
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun pairTest() = runBlocking {
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
                val device = bm.adapter.getRemoteDevice(address) ?: error("No device!")
                val gatt = Shadows.shadowOf(device).bluetoothGatts.single() ?: error("No gatt!")
                check(device.bondState == BluetoothDevice.BOND_NONE)
                Shadows.shadowOf(device).setCreatedBond(true)
                job = launch(CoroutineName("pairing")) {
                    generics.states.take(2).collectIndexed { index, state ->
                        println("$index] $state") // todo
                        when (index) {
                            0 -> {
                                check(state is BLEGenerics.State.Connected)
                                assertFalse(state.isPaired)
                            }
                            1 -> check(state is BLEGenerics.State.Pairing)
                            else -> error("Index $index is unexpected!")
                        }
                    }
                }
                generics.pair(pin = "000000")
                job.join()
                job = launch(CoroutineName("pair")) {
                    generics.events.take(1).collectIndexed { index, event ->
                        println("$index] $event") // todo
                        when (index) {
                            0 -> {
                                check(event is BLEGenerics.Event.OnPairing)
                                assertTrue(event.isSuccess)
                            }
                            else -> error("Index $index is unexpected!")
                        }
                    }
                }
                Shadows.shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED)
                val broadcast = Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                broadcast.setPackage(context.packageName) // https://stackoverflow.com/a/76920719/4398606
                broadcast.putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                broadcast.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                broadcast.putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDED)
                context.sendBroadcast(broadcast)
                job.join()
                val state = generics.states.value
                check(state is BLEGenerics.State.Connected)
                assertEquals(address, state.address)
                assertTrue(state.isPaired)
            }
        }
    }

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun bluetoothTest() = runBlocking {
        withTimeout(6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val context: Context = application
            //
            val bm = context.getSystemService(BluetoothManager::class.java)
            Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_ON)
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
                                val gatt = Shadows.shadowOf(device).bluetoothGatts.lastOrNull() ?: error("No gatt!")
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
                    generics.states.take(4).collectIndexed { index, state ->
                        when (index) {
                            0 -> {
                                check(state is BLEGenerics.State.Connected) { "$index] state: $state" }
                                assertEquals(address, state.address)
                                assertFalse(state.isPaired)
                                Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_TURNING_OFF)
                                var broadcast = Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
                                broadcast.setPackage(context.packageName) // https://stackoverflow.com/a/76920719/4398606
                                broadcast.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_TURNING_OFF)
                                context.sendBroadcast(broadcast)
                                Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_OFF)
                                broadcast = Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
                                broadcast.setPackage(context.packageName) // https://stackoverflow.com/a/76920719/4398606
                                broadcast.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                                context.sendBroadcast(broadcast)
                            }
                            1 -> {
                                check(state is BLEGenerics.State.Waiting) { "$index] state: $state" }
                                assertEquals(address, state.address)
                                Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_TURNING_ON)
                                var broadcast = Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
                                broadcast.setPackage(context.packageName) // https://stackoverflow.com/a/76920719/4398606
                                broadcast.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_TURNING_ON)
                                context.sendBroadcast(broadcast)
                                Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_ON)
                                broadcast = Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
                                broadcast.setPackage(context.packageName) // https://stackoverflow.com/a/76920719/4398606
                                broadcast.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON)
                                context.sendBroadcast(broadcast)
                            }
                            2 -> {
                                check(state is BLEGenerics.State.Connecting) { "$index] state: $state" }
                                assertEquals(address, state.address)
                                val device = bm.adapter.getRemoteDevice(address) ?: error("No device!")
                                val gatt = Shadows.shadowOf(device).bluetoothGatts.lastOrNull() ?: error("No gatt!")
                                val callback = Shadows.shadowOf(gatt).gattCallback ?: error("No callback!")
                                callback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)
                            }
                            3 -> {
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

    @Config(application = MockApplication::class, sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.TIRAMISU])
    @Test
    fun gpsTest() = runBlocking {
        withTimeout(6.seconds) {
            val application = RuntimeEnvironment.getApplication()
            val context: Context = application
            //
            val bm = context.getSystemService(BluetoothManager::class.java)
            Shadows.shadowOf(bm.adapter).setState(BluetoothAdapter.STATE_ON)
            check(bm.adapter.isEnabled)
            //
            val lm = context.getSystemService(LocationManager::class.java)
            check(lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
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
                                val gatt = Shadows.shadowOf(device).bluetoothGatts.lastOrNull() ?: error("No gatt!")
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
                    generics.states.take(4).collectIndexed { index, state ->
                        when (index) {
                            0 -> {
                                check(state is BLEGenerics.State.Connected) { "$index] state: $state" }
                                assertEquals(address, state.address)
                                assertFalse(state.isPaired)
                                Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
                            }
                            1 -> {
                                check(state is BLEGenerics.State.Waiting) { "$index] state: $state" }
                                assertEquals(address, state.address)
                                Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
                            }
                            2 -> {
                                check(state is BLEGenerics.State.Connecting) { "$index] state: $state" }
                                assertEquals(address, state.address)
                                val device = bm.adapter.getRemoteDevice(address) ?: error("No device!")
                                val gatt = Shadows.shadowOf(device).bluetoothGatts.lastOrNull() ?: error("No gatt!")
                                val callback = Shadows.shadowOf(gatt).gattCallback ?: error("No callback!")
                                callback.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED)
                            }
                            3 -> {
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
