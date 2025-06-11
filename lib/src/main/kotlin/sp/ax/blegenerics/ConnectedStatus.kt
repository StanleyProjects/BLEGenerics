package sp.ax.blegenerics

internal sealed interface ConnectedStatus {
    val ordinal: Int

    data object Idling : ConnectedStatus {
        override val ordinal = 2
    }
    data object Disconnecting : ConnectedStatus {
        override val ordinal = 4
    }
    data object Unpairing : ConnectedStatus {
        override val ordinal = 6
    }
    data class Pairing(val pin: String?) : ConnectedStatus {
        override val ordinal = 8
    }
}
