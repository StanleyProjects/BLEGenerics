package sp.ax.blegenerics

internal class MockGenericsLogger : BLEGenericsLogger {
    override fun warning(message: String) {
        // noop
    }

    override fun debug(message: String) {
        // noop
    }

    override fun info(message: String) {
        // noop
    }
}
