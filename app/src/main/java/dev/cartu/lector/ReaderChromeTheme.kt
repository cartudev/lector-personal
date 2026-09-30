package dev.cartu.lector

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.readium.r2.navigator.preferences.Theme

internal data class ReaderChromePalette(val surface: Int, val content: Int)

internal fun readerChromePalette(surface: Int, content: Int): ReaderChromePalette =
    ReaderChromePalette(surface = surface, content = content)

internal fun readerChromeColorScheme(theme: Theme): ColorScheme {
    val base = if (theme == Theme.DARK) darkColorScheme() else lightColorScheme()
    val palette = readerChromePalette(theme.backgroundColor, theme.contentColor)
    val surface = Color(palette.surface)
    val content = Color(palette.content)
    return base.copy(
        background = surface,
        onBackground = content,
        surface = surface,
        onSurface = content,
    )
}

internal fun Theme.label(): String = when (this) {
    Theme.LIGHT -> "Claro"
    Theme.DARK -> "Oscuro"
    Theme.SEPIA -> "Sepia"
}

@Composable
internal fun ReaderChromeMaterialTheme(theme: Theme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = readerChromeColorScheme(theme), content = content)
}
