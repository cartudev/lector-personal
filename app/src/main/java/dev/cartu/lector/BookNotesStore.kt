package dev.cartu.lector

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

internal data class BookNote(
    val id: String,
    val selectedText: String,
    val content: String,
    val locator: Locator,
)

internal data class LibraryBookNote(val book: File, val note: BookNote)

internal class BookNotesStore(context: Context, book: File) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val notesKey = "notes:${bookKey(book)}"

    fun notes(): List<BookNote> = runCatching {
        val array = JSONArray(preferences.getString(notesKey, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val locator = Locator.fromJSON(item.optJSONObject("locator")) ?: continue
                add(
                    BookNote(
                        id = item.optString("id"),
                        selectedText = item.optString("selectedText"),
                        content = item.optString("content"),
                        locator = locator,
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun note(id: String): BookNote? = notes().firstOrNull { it.id == id }

    fun save(note: BookNote) {
        val updated = notes().filterNot { it.id == note.id } + note
        preferences.edit().putString(
            notesKey,
            JSONArray().apply {
                updated.forEach { item ->
                    put(
                        JSONObject()
                            .put("id", item.id)
                            .put("selectedText", item.selectedText)
                            .put("content", item.content)
                            .put("locator", item.locator.toJSON())
                    )
                }
            }.toString()
        ).apply()
    }

    fun delete(id: String): Boolean {
        val current = notes()
        val updated = current.filterNot { it.id == id }
        if (updated.size == current.size) return false
        preferences.edit().putString(
            notesKey,
            JSONArray().apply {
                updated.forEach { item ->
                    put(
                        JSONObject()
                            .put("id", item.id)
                            .put("selectedText", item.selectedText)
                            .put("content", item.content)
                            .put("locator", item.locator.toJSON())
                    )
                }
            }.toString()
        ).apply()
        return true
    }

    companion object {
        private const val PREFERENCES_NAME = "book_notes"

        private fun bookKey(book: File): String = MessageDigest.getInstance("SHA-256")
            .digest(book.canonicalPath.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
