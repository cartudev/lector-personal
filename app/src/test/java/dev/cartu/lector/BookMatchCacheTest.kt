package dev.cartu.lector

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BookMatchCacheTest {
    @Test
    fun returnsCachedMatchesForUnchangedBookAndQuery() {
        val root = Files.createTempDirectory("book-match-cache").toFile()
        val book = File(root, "book.epub").apply { writeText("initial EPUB bytes") }
        val cache = BookMatchCache(File(root, "cache"))

        cache.write(book, "word", BookMatchCache.CachedMatches(emptyList(), capped = true))

        val cached = cache.read(book, "word")
        assertNotNull(cached)
        assertEquals(0, cached?.locators?.size)
        assertEquals(true, cached?.capped)
    }

    @Test
    fun invalidatesMatchesWhenBookContentsChange() {
        val root = Files.createTempDirectory("book-match-cache").toFile()
        val book = File(root, "book.epub").apply { writeText("old bytes") }
        val cache = BookMatchCache(File(root, "cache"))
        cache.write(book, "word", BookMatchCache.CachedMatches(emptyList(), capped = false))

        book.writeText("different and longer EPUB bytes")

        assertNull(cache.read(book, "word"))
    }
}
