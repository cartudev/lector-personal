package dev.cartu.lector

import android.util.Log
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

internal class BookMatchCache(private val cacheDirectory: File) {
    data class CachedMatches(val locators: List<Locator>, val capped: Boolean)

    fun read(book: File, query: String): CachedMatches? {
        val file = cacheFile(book, query)
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            if (json.optInt("schema") != SCHEMA_VERSION) return null
            val locators = Locator.fromJSONArray(json.optJSONArray("locators"))
            CachedMatches(locators, json.optBoolean("capped"))
        } catch (error: Exception) {
            Log.w(TAG, "Ignoring invalid match cache for '$query'", error)
            null
        }
    }

    fun write(book: File, query: String, matches: CachedMatches) {
        runCatching {
            cacheDirectory.mkdirs()
            val target = cacheFile(book, query)
            val temporary = File(target.parentFile, "${target.name}.tmp")
            val json = JSONObject()
                .put("schema", SCHEMA_VERSION)
                .put("capped", matches.capped)
                .put("locators", JSONArray().apply {
                    matches.locators.forEach { put(it.toJSON()) }
                })
            temporary.writeText(json.toString())
            if (!temporary.renameTo(target)) {
                temporary.delete()
                target.writeText(json.toString())
            }
        }.onFailure { error -> Log.w(TAG, "Could not cache matches for '$query'", error) }
    }

    private fun cacheFile(book: File, query: String): File {
        val identity = listOf(
            book.canonicalPath,
            book.length().toString(),
            book.lastModified().toString(),
            INDEX_VERSION,
            query,
        ).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(cacheDirectory, "$digest.json")
    }

    companion object {
        private const val TAG = "LectorPersonal"
        private const val SCHEMA_VERSION = 1
        private const val INDEX_VERSION = "substring-case-insensitive-v1"
    }
}
