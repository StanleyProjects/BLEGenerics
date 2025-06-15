package sp.ax.blegenerics

import kotlinx.coroutines.flow.SharedFlow

interface BLEProfiles {
    sealed interface Event {
        data object OnServices : Event
        data class OnMtuChanged(val size: Int) : Event
    }

    val events: SharedFlow<Event>

    fun services()
    fun requestMTU(size: Int)

    companion object
}
