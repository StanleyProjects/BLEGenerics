package sp.ax.blegenerics

import kotlinx.coroutines.flow.MutableSharedFlow

internal interface MutableBLEProfiles : BLEProfiles {
    override val events: MutableSharedFlow<BLEProfiles.Event>
    suspend fun emit(event: BLEProfiles.Event)
    suspend fun clear()
}
