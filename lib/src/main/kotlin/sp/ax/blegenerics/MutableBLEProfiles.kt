package sp.ax.blegenerics

internal interface MutableBLEProfiles : BLEProfiles {
    suspend fun emit(event: BLEProfiles.Event)
    suspend fun clear()
}
