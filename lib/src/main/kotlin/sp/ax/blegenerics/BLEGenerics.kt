package sp.ax.blegenerics

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BLEGenerics {
    sealed interface State {
        val address: String

        data class Connecting(override val address: String) : State
        data class Connected(override val address: String, val isPaired: Boolean) : State
        data class Pairing(override val address: String) : State
        data class Searching(override val address: String) : State
        data class Waiting(override val address: String) : State
        data class Disconnecting(override val address: String) : State
    }

    sealed interface Event {
        val address: String

        data class OnConnect(override val address: String) : Event
        data class OnDisconnect(override val address: String) : Event
        data class OnPairing(override val address: String, val isSuccess: Boolean) : Event
    }

    val states: StateFlow<State?>
    val events: SharedFlow<Event>

    fun connect(address: String)
    fun disconnect(address: String)
    fun pair(address: String, pin: String?)
}
