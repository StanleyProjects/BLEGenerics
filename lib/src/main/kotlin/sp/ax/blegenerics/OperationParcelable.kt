package sp.ax.blegenerics

import android.os.Parcel
import android.os.Parcelable
import java.util.UUID

internal class OperationParcelable(
    val delegate: BLEProfiles.Operation,
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        return when (other) {
            is BLEProfiles.Operation -> delegate == other
            is OperationParcelable -> delegate == other.delegate
            else -> false
        }
    }

    override fun hashCode(): Int {
        return delegate.hashCode()
    }

    override fun toString(): String {
        return delegate.toString()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        when (delegate) {
            BLEProfiles.Operation.Services -> {
                dest.writeString("Services")
            }
            is BLEProfiles.Operation.ChangeMTU -> {
                dest.writeString("ChangeMTU")
                dest.writeInt(delegate.value)
            }
            is BLEProfiles.Operation.Characteristics.SetNotification -> {
                dest.writeString("Characteristics.SetNotification")
                dest.writeString(delegate.service.toString())
                dest.writeString(delegate.characteristic.toString())
                dest.writeInt(if (delegate.value) 1 else 0)
            }
            is BLEProfiles.Operation.Characteristics.Write -> {
                dest.writeString("Characteristics.Write")
                dest.writeString(delegate.service.toString())
                dest.writeString(delegate.characteristic.toString())
                dest.writeInt(delegate.bytes.size)
                dest.writeByteArray(delegate.bytes)
            }
            is BLEProfiles.Operation.Descriptors.Write -> {
                dest.writeString("Descriptors.Write")
                dest.writeString(delegate.service.toString())
                dest.writeString(delegate.characteristic.toString())
                dest.writeString(delegate.descriptor.toString())
                dest.writeInt(delegate.bytes.size)
                dest.writeByteArray(delegate.bytes)
            }
        }
    }

    companion object CREATOR : Parcelable.Creator<OperationParcelable> {
        override fun createFromParcel(parcel: Parcel): OperationParcelable {
            val delegate = when (parcel.readString()) {
                "Services" -> {
                    BLEProfiles.Operation.Services
                }
                "ChangeMTU" -> {
                    val value = parcel.readInt()
                    BLEProfiles.Operation.ChangeMTU(value = value)
                }
                "Characteristics.SetNotification" -> {
                    val service = UUID.fromString(parcel.readString()!!)
                    val characteristic = UUID.fromString(parcel.readString()!!)
                    val value = parcel.readInt() == 1
                    BLEProfiles.Operation.Characteristics.SetNotification(
                        service = service,
                        characteristic = characteristic,
                        value = value,
                    )
                }
                "Descriptors.Write" -> {
                    val service = UUID.fromString(parcel.readString()!!)
                    val characteristic = UUID.fromString(parcel.readString()!!)
                    val descriptor = UUID.fromString(parcel.readString()!!)
                    val bytes = ByteArray(parcel.readInt())
                    parcel.readByteArray(bytes)
                    BLEProfiles.Operation.Descriptors.Write(
                        service = service,
                        characteristic = characteristic,
                        descriptor = descriptor,
                        bytes = bytes,
                    )
                }
                "Characteristics.Write" -> {
                    val service = UUID.fromString(parcel.readString()!!)
                    val characteristic = UUID.fromString(parcel.readString()!!)
                    val bytes = ByteArray(parcel.readInt())
                    parcel.readByteArray(bytes)
                    BLEProfiles.Operation.Characteristics.Write(
                        service = service,
                        characteristic = characteristic,
                        bytes = bytes,
                    )
                }
                else -> TODO("OperationParcelable:createFromParcel($parcel)")
            }
            return OperationParcelable(delegate = delegate)
        }

        override fun newArray(size: Int): Array<OperationParcelable?> {
            return arrayOfNulls(size)
        }
    }
}
