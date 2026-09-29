package dev.cartu.lector

import android.content.Context
import java.util.Locale

internal const val MAX_DICTIONARY_TEXT_LENGTH = 120

internal val DICTIONARY_COLORS = listOf(
    0xFFFFD60A.toInt(),
    0xFF7BE37B.toInt(),
    0xFF6FB8FF.toInt(),
    0xFFFF9EC7.toInt(),
    0xFFFFB35C.toInt(),
    0xFFC49BFF.toInt(),
)

internal const val DICTIONARY_HIGHLIGHT_ALPHA = 90

private val WHITESPACE = Regex("\\s+")

internal fun colorWithAlpha(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

internal fun normalizeDictionaryText(input: String): String? {
    val collapsed = input.trim().replace(WHITESPACE, " ")
    val text = collapsed.trim { character ->
        !(character.isLetterOrDigit() || character == '\'' || character == '\u2019')
    }
    if (text.isEmpty() || text.length > MAX_DICTIONARY_TEXT_LENGTH) return null
    if (text.none(Char::isLetter)) return null
    return text.lowercase(Locale.ROOT)
}

internal fun normalizeDictionaryTranslation(input: String): String? =
    input.trim().takeIf(String::isNotEmpty)

internal data class DictionaryEntry(
    val text: String,
    val translation: String?,
    val colorIndex: Int,
)

internal class PersonalDictionary(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun entries(): List<DictionaryEntry> = texts().sorted().map(::entryOf)

    fun contains(input: String): Boolean =
        normalizeDictionaryText(input)?.let { it in texts() } ?: false

    fun entry(input: String): DictionaryEntry? =
        normalizeDictionaryText(input)?.takeIf { it in texts() }?.let(::entryOf)

    fun saveEntry(input: String, translation: String, colorIndex: Int): String? {
        val text = normalizeDictionaryText(input) ?: return null
        val editor = preferences.edit()
            .putStringSet(WORDS_KEY, texts() + text)
            .putInt(colorKey(text), colorIndex.coerceIn(DICTIONARY_COLORS.indices))
        normalizeDictionaryTranslation(translation)
            ?.let { editor.putString(translationKey(text), it) }
            ?: editor.remove(translationKey(text))
        editor.apply()
        return text
    }

    fun deleteEntry(input: String): Boolean {
        val text = normalizeDictionaryText(input) ?: return false
        if (text !in texts()) return false
        preferences.edit()
            .putStringSet(WORDS_KEY, texts() - text)
            .remove(translationKey(text))
            .remove(colorKey(text))
            .apply()
        return true
    }

    fun nextColorIndex(): Int {
        val usage = IntArray(DICTIONARY_COLORS.size)
        entries().forEach { usage[it.colorIndex.coerceIn(usage.indices)]++ }
        return usage.indexOf(usage.min())
    }

    private fun texts(): Set<String> =
        preferences.getStringSet(WORDS_KEY, emptySet()).orEmpty()

    private fun entryOf(text: String): DictionaryEntry = DictionaryEntry(
        text = text,
        translation = preferences.getString(translationKey(text), null),
        colorIndex = preferences.getInt(colorKey(text), 0),
    )

    companion object {
        private const val PREFERENCES_NAME = "personal_dictionary"
        private const val WORDS_KEY = "words"

        private fun translationKey(text: String): String = "translation:$text"

        private fun colorKey(text: String): String = "color:$text"
    }
}
