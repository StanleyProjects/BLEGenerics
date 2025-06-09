package sp.ax.blegenerics

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BLEGenerics {
    sealed interface State {
        data object Connecting : State
        data class Connected(val isPaired: Boolean) : State
        data object Disconnecting : State
        data object Searching : State
        data object Waiting : State
    }

    enum class Event {
        OnConnect,
        OnDisconnect,
    }

    val states: StateFlow<Map<String, State>>
    val events: SharedFlow<Pair<String, Event>>

    fun connect(address: String)
    fun disconnect(address: String)
}
