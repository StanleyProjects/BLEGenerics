package sp.ax.blegenerics

import kotlinx.coroutines.flow.MutableSharedFlow

internal interface MutableBLEProfiles : BLEProfiles {
    override val events: MutableSharedFlow<BLEProfiles.Event>
}
