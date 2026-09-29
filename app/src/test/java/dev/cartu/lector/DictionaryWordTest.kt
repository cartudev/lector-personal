package dev.cartu.lector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictionaryWordTest {
    @Test
    fun normalizesAccentedSingleWord() {
        assertEquals("árbol", normalizeDictionaryText("  Árbol, "))
    }

    @Test
    fun normalizesMultiWordPhrase() {
        assertEquals("a small shop", normalizeDictionaryText("  a  Small   shop. "))
    }

    @Test
    fun rejectsSelectionWithoutLetters() {
        assertNull(normalizeDictionaryText("---"))
    }

    @Test
    fun rejectsTooLongSelection() {
        assertNull(normalizeDictionaryText("a".repeat(MAX_DICTIONARY_TEXT_LENGTH + 1)))
    }

    @Test
    fun trimsDictionaryTranslation() {
        assertEquals("vehemente", normalizeDictionaryTranslation("  vehemente  "))
    }

    @Test
    fun rejectsBlankDictionaryTranslation() {
        assertNull(normalizeDictionaryTranslation("   "))
    }

    @Test
    fun colorWithAlphaKeepsRgb() {
        val tint = colorWithAlpha(0xFFFFD60A.toInt(), DICTIONARY_HIGHLIGHT_ALPHA)
        assertEquals(0x5AFFD60A.toInt(), tint)
    }
}
