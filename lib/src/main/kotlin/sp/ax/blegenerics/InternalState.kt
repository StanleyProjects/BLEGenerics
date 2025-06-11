package sp.ax.blegenerics

import android.bluetooth.BluetoothGatt

internal sealed interface InternalState : Comparable<InternalState?> {
    val ordinal: Int
    val address: String

    fun isPairing(): Boolean = false

    class Connecting(
        override val address: String,
        val gatt: BluetoothGatt,
    ) : InternalState {
        override val ordinal = 4

        override fun toString(): String {
            return "Connecting(address: $address, gatt: ${gatt.hashCode()})"
        }
    }

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

        override fun isPairing(): Boolean {
            return when (status) {
                is ConnectedStatus.Pairing,
                ConnectedStatus.Unpairing -> true
                else -> false
            }
        }

        override fun toString(): String {
            return "Connected(address: $address, isPaired: $isPaired, gatt: ${gatt.hashCode()})"
        }

        companion object : Comparable<InternalState?> {
            const val Ordinal = 10

            override fun compareTo(other: InternalState?): Int {
                if (other == null) return 1
                return Ordinal.compareTo(other.ordinal)
            }
        }
    }

    data class Searching(
        override val address: String,
    ) : InternalState {
        override val ordinal = 2
    }

    data class Waiting(
        override val address: String,
    ) : InternalState {
        override val ordinal = 1
    }

    override fun compareTo(other: InternalState?): Int {
        if (other == null) return 1
        return ordinal.compareTo(other.ordinal)
    }
}
