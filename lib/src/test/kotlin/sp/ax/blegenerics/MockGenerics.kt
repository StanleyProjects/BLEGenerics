package sp.ax.blegenerics

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

internal class MockGenerics(
    private val coroutineScope: CoroutineScope,
    private val default: CoroutineContext,
    defaultState: BLEGenerics.State? = null,
) : BLEGenerics {
    private val _states = MutableStateFlow<BLEGenerics.State?>(defaultState)
    override val states = _states.asStateFlow()
    private val _events = MutableSharedFlow<BLEGenerics.Event>()
    override val events = _events.asSharedFlow()
    private class MockProfiles : BLEProfiles {
        private val _events = MutableSharedFlow<BLEProfiles.Event>()
        override val events = _events.asSharedFlow()

        override fun perform(operation: BLEProfiles.Operation) {
            TODO("Not yet implemented: perform")
        }
    }

    override val profiles: BLEProfiles = MockProfiles()

    override fun connect(address: String) {
        coroutineScope.launch(CoroutineName("MockGenerics:connect")) {
            withContext(default) {
                val state = _states.value
                if (state != null) TODO("MockGenerics:connect($address):state: $state")
                _states.value = BLEGenerics.State.Connecting(address = address)
                delay(1.seconds)
                _states.value = BLEGenerics.State.Connected(address = address, isPaired = false)
            }
        }
    }

    override fun disconnect() {
        coroutineScope.launch(CoroutineName("MockGenerics:disconnect")) {
            withContext(default) {
                val state = _states.value
                if (state == null) TODO("MockGenerics:disconnect:no state")
                _states.value = BLEGenerics.State.Disconnecting(address = state.address)
                delay(1.seconds)
                _states.value = null
            }
        }
    }

    override fun pair(pin: String?) {
        TODO("Not yet implemented: pair")
    }

    override fun unpair() {
        TODO("Not yet implemented: unpair")
    }
}
