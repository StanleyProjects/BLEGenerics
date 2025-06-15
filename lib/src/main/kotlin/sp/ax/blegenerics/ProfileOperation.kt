package sp.ax.blegenerics

internal sealed interface ProfileOperation {
    data object Services : ProfileOperation
    data class ChangeMTU(val size: Int) : ProfileOperation
}
