package sp.ax.blegenerics

internal class MockGenericsLogger : BLEGenericsLogger {
    override fun warning(message: String) {
        println("[warning] $message")
    }

    override fun debug(message: String) {
        println("[debug] $message")
    }

    override fun info(message: String) {
        println("[info] $message")
    }
}
