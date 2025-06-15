package sp.ax.blegenerics

import kotlinx.coroutines.flow.SharedFlow

interface BLEProfiles {
    sealed interface Event {
        data object OnServices : Event
    }

    val events: SharedFlow<Event>

    fun services()

    companion object
}
