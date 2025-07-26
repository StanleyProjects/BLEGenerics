package sp.ax.blegenerics

import android.bluetooth.BluetoothGatt

internal sealed interface InternalState : Comparable<InternalState?> {
    val ordinal: Int
    val address: String

    class Connected(
        override val address: String,
        val isPaired: Boolean,
        val gatt: BluetoothGatt,
        val status: ConnectedStatus,
    ) : InternalState {
        override val ordinal = Ordinal

        fun copy(isPaired: Boolean = this.isPaired, status: ConnectedStatus): Connected {
            return Connected(
                address = address,
                isPaired = isPaired,
                gatt = gatt,
                status = status,
            )
        }

        override fun toString(): String {
            return "Connected(address: $address, isPaired: $isPaired, gatt: ${gatt.hashCode()}, status: $status)"
        }

        companion object : Comparable<InternalState?> {
            const val Ordinal = 32

            override fun compareTo(other: InternalState?): Int {
                if (other == null) return 1
                return Ordinal.compareTo(other.ordinal)
            }
        }
    }

    class Connecting(
        override val address: String,
        val gatt: BluetoothGatt,
    ) : InternalState {
        override val ordinal = 16

        override fun toString(): String {
            return "Connecting(address: $address, gatt: ${gatt.hashCode()})"
        }
    }

    data class Searching(
        override val address: String,
    ) : InternalState {
        override val ordinal = 8
    }

    data class Waiting(
        override val address: String,
    ) : InternalState {
        override val ordinal = 4
    }

    data class Disconnecting(
        override val address: String,
    ) : InternalState {
        override val ordinal = 2
    }

    override fun compareTo(other: InternalState?): Int {
        if (other == null) return 1
        return ordinal.compareTo(other.ordinal)
    }
}
