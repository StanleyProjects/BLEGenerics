package sp.ax.blegenerics

class BLEGenericsException(val type: Type) : Exception(type.name) {
    enum class Type {
        BTDisabled,
        GPSDisabled,
    }

    override fun toString(): String {
        return "BLEGenericsException($type)"
    }
}
