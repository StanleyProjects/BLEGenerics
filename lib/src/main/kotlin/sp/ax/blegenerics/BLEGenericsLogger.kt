package sp.ax.blegenerics

interface BLEGenericsLogger {
    enum class Level {
        Warning,
        Debug,
        Info,
    }

    fun warning(message: String)
    fun debug(message: String)
    fun info(message: String)
}
