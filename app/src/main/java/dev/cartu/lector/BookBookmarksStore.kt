package dev.cartu.lector

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

internal data class BookBookmark(
    val id: String,
    val label: String,
    val locator: Locator,
)

internal class BookBookmarksStore(context: Context, book: File) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val bookmarksKey = "bookmarks:${bookKey(book)}"

    fun bookmarks(): List<BookBookmark> = runCatching {
        val array = JSONArray(preferences.getString(bookmarksKey, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val locator = Locator.fromJSON(item.optJSONObject("locator")) ?: continue
                add(
                    BookBookmark(
                        id = item.optString("id"),
                        label = item.optString("label"),
                        locator = locator,
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun contains(locator: Locator): Boolean = bookmarks().any { it.locator.toJSON().toString() == locator.toJSON().toString() }

    fun toggle(locator: Locator): Boolean {
        val current = bookmarks()
        val serializedLocator = locator.toJSON().toString()
        val existing = current.firstOrNull { it.locator.toJSON().toString() == serializedLocator }
        val updated = if (existing == null) {
            current + BookBookmark(
                id = UUID.randomUUID().toString(),
                label = locator.text.highlight?.takeIf(String::isNotBlank)
                    ?: locator.href.toString().substringAfterLast('/').ifBlank { "Marcador" },
                locator = locator,
            )
        } else {
            current - existing
        }
        preferences.edit().putString(
            bookmarksKey,
            JSONArray().apply {
                updated.forEach { bookmark ->
                    put(
                        JSONObject()
                            .put("id", bookmark.id)
                            .put("label", bookmark.label)
                            .put("locator", bookmark.locator.toJSON())
                    )
                }
            }.toString()
        ).apply()
        return existing == null
    }

    companion object {
        private const val PREFERENCES_NAME = "book_bookmarks"

        private fun bookKey(book: File): String = MessageDigest.getInstance("SHA-256")
            .digest(book.canonicalPath.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
