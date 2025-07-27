package sp.ax.blegenerics

internal sealed interface ConnectedStatus : Comparable<ConnectedStatus> {
    val ordinal: Int

    data object Unpairing : ConnectedStatus {
        override val ordinal = 4
    }

    data class Idling(val isPaired: Boolean) : ConnectedStatus {
        override val ordinal = 8
    }

    data class Pairing(val pin: String?) : ConnectedStatus {
        override val ordinal = 16
    }

    override fun compareTo(other: ConnectedStatus): Int {
        return ordinal.compareTo(other.ordinal)
    }
}
