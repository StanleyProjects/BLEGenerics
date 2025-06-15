package sp.ax.blegenerics

import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

interface BLEProfiles {
    sealed interface Event {
        data class OnServices(val characteristics: Map<UUID, Set<UUID>>) : Event
        data class OnMtuChanged(val value: Int) : Event
        sealed interface Characteristics : Event {
            data class OnSetNotification(
                val service: UUID,
                val characteristic: UUID,
                val value: Boolean,
            ) : Characteristics
        }
    }

    sealed interface Operation {
        data object Services : Operation
        data class ChangeMTU(val value: Int) : Operation
        sealed interface Characteristics : Operation {
            data class SetNotification(
                val service: UUID,
                val characteristic: UUID,
                val value: Boolean,
            ) : Characteristics
        }
    }

    val events: SharedFlow<Event>

    fun perform(operation: Operation)
}
