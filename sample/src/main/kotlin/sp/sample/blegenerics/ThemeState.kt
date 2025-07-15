package sp.sample.blegenerics

import androidx.compose.ui.graphics.Color

internal sealed interface ThemeState {
    val background: Color
    val text: Color

    data object Light : ThemeState {
        override val background = Color.White
        override val text = Color.Black
    }
    data object Dark : ThemeState {
        override val background = Color.Black
        override val text = Color.White
    }
}
