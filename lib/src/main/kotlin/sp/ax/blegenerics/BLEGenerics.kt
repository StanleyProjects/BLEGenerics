package sp.ax.blegenerics

import kotlinx.coroutines.flow.StateFlow

interface BLEGenerics {
    sealed interface State {
        data object Connecting : State
        data class Connected(val isPaired: Boolean) : State
        data object Disconnecting : State
    }

    val states: StateFlow<Map<String, State>>

    fun connect(address: String)
    fun disconnect(address: String)
}
