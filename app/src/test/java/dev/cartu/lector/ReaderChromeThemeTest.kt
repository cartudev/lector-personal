package dev.cartu.lector

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChromeThemeTest {
    @Test
    fun darkReaderChromeUsesReadiumDarkSurfaceAndTextColors() {
        val colors = readerChromePalette(surface = 0xFF000000.toInt(), content = 0xFFFEFEFE.toInt())

        assertEquals(0xFF000000.toInt(), colors.surface)
        assertEquals(0xFFFEFEFE.toInt(), colors.content)
    }

    @Test
    fun sepiaReaderChromeUsesReadiumSepiaSurfaceAndTextColors() {
        val colors = readerChromePalette(surface = 0xFFFAF4E8.toInt(), content = 0xFF121212.toInt())

        assertEquals(0xFFFAF4E8.toInt(), colors.surface)
        assertEquals(0xFF121212.toInt(), colors.content)
    }
}
