package dev.cartu.lector

import android.content.Context
import java.io.File
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

internal fun orderBooksByLastRead(
    books: List<File>,
    lastReadByBook: Map<String, Long>,
): List<File> = books.sortedWith(
    compareByDescending<File> { lastReadByBook[it.name] ?: 0L }
        .thenBy { it.name.lowercase() }
)

internal class BookReadHistory(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun markRead(book: File) {
        val timestamp = maxOf(System.currentTimeMillis(), preferences.getLong(CLOCK_KEY, 0L) + 1L)
        preferences.edit()
            .putLong(CLOCK_KEY, timestamp)
            .putLong(bookKey(book), timestamp)
            .apply()
    }

    fun order(books: List<File>): List<File> = orderBooksByLastRead(
        books,
        books.associate { book -> book.name to preferences.getLong(bookKey(book), 0L) }
    )

    fun locator(book: File): Locator? = preferences.getString(locatorKey(book), null)
        ?.let { serialized -> runCatching { Locator.fromJSON(JSONObject(serialized)) }.getOrNull() }

    fun saveLocator(book: File, locator: Locator) {
        preferences.edit().putString(locatorKey(book), locator.toJSON().toString()).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "book_read_history"
        private const val CLOCK_KEY = "last_read_clock"

        private fun bookKey(book: File): String = "last_read:${book.name}"
        private fun locatorKey(book: File): String = "last_locator:${book.name}"
    }
}
