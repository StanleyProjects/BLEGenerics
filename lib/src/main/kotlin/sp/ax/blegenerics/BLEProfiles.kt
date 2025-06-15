package sp.ax.blegenerics

import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

interface BLEProfiles {
    sealed interface Event {
        data class OnServices(val characteristics: Map<UUID, Set<UUID>>) : Event
        data class OnMtuChanged(val size: Int) : Event
    }

    val events: SharedFlow<Event>

    fun services()
    fun changeMTU(size: Int)

    companion object
}
