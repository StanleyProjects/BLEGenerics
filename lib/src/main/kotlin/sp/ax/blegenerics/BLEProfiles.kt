package sp.ax.blegenerics

import kotlinx.coroutines.flow.SharedFlow
import java.util.Objects
import java.util.UUID

private fun getHashCode(result: Result<ByteArray>): Int {
    return result.fold(
        onSuccess = { it.contentHashCode() },
        onFailure = { it.hashCode() },
    )
}

private fun Result<ByteArray>.eq(other: Result<ByteArray>): Boolean {
    if (isSuccess) {
        if (other.isFailure) return false
        return getOrThrow().contentEquals(other.getOrThrow())
    } else {
        if (other.isSuccess) return false
        return exceptionOrNull() == other.exceptionOrNull()
    }
}

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
            class OnWrite(
                val service: UUID,
                val characteristic: UUID,
                val result: Result<ByteArray>,
            ) : Characteristics {
                override fun equals(other: Any?): Boolean {
                    return when (other) {
                        is OnWrite -> {
                            service == other.service &&
                                characteristic == other.characteristic &&
                                result.eq(other.result)
                        }
                        else -> false
                    }
                }

                override fun hashCode(): Int {
                    return Objects.hash(service, characteristic, getHashCode(result))
                }

                override fun toString(): String {
                    return "Characteristics.OnWrite($service/$characteristic/, result: ${result.map { it.size }})"
                }
            }
            class OnChange(
                val service: UUID,
                val characteristic: UUID,
                val bytes: ByteArray,
            ) : Characteristics {
                override fun equals(other: Any?): Boolean {
                    return when (other) {
                        is OnChange -> {
                            service == other.service &&
                                characteristic == other.characteristic &&
                                bytes.contentEquals(other.bytes)
                        }
                        else -> false
                    }
                }

                override fun hashCode(): Int {
                    return Objects.hash(service, characteristic, bytes.contentHashCode())
                }

                override fun toString(): String {
                    return "Characteristics.OnChange($service/$characteristic, bytes: ${bytes.size})"
                }
            }
        }
        sealed interface Descriptors : Event {
            class OnWrite(
                val service: UUID,
                val characteristic: UUID,
                val descriptor: UUID,
                val result: Result<ByteArray>,
            ) : Descriptors {
                override fun equals(other: Any?): Boolean {
                    return when (other) {
                        is OnWrite -> {
                            service == other.service &&
                                characteristic == other.characteristic &&
                                descriptor == other.descriptor &&
                                result.eq(other.result)
                        }
                        else -> false
                    }
                }

                override fun hashCode(): Int {
                    return Objects.hash(service, characteristic, descriptor, getHashCode(result))
                }

                override fun toString(): String {
                    return "Descriptors.OnWrite($service/$characteristic/$descriptor, result: ${result.map { it.size }})"
                }
            }
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
            class Write(
                val service: UUID,
                val characteristic: UUID,
                val bytes: ByteArray,
            ) : Characteristics {
                override fun equals(other: Any?): Boolean {
                    return when (other) {
                        is Write -> {
                            service == other.service &&
                                characteristic == other.characteristic &&
                                bytes.contentEquals(other.bytes)
                        }
                        else -> false
                    }
                }

                override fun hashCode(): Int {
                    return Objects.hash(service, characteristic, bytes.contentHashCode())
                }

                override fun toString(): String {
                    return "Characteristics.Write($service/$characteristic, bytes: ${bytes.size})"
                }
            }
        }
        sealed interface Descriptors : Operation {
            class Write(
                val service: UUID,
                val characteristic: UUID,
                val descriptor: UUID,
                val bytes: ByteArray,
            ) : Descriptors {
                override fun equals(other: Any?): Boolean {
                    return when (other) {
                        is Write -> {
                            service == other.service &&
                                characteristic == other.characteristic &&
                                descriptor == other.descriptor &&
                                bytes.contentEquals(other.bytes)
                        }
                        else -> false
                    }
                }

                override fun hashCode(): Int {
                    return Objects.hash(service, characteristic, descriptor, bytes.contentHashCode())
                }

                override fun toString(): String {
                    return "Descriptors.Write($service/$characteristic/$descriptor, bytes: ${bytes.size})"
                }
            }
        }
    }

    val events: SharedFlow<Event>

    fun perform(operation: Operation)
}
