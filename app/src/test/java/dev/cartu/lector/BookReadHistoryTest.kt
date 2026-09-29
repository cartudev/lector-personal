package dev.cartu.lector

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class BookReadHistoryTest {
    @Test
    fun sortsMostRecentlyReadBookFirst() {
        val books = listOf(File("vol-03.epub"), File("vol-20.epub"))

        assertEquals(
            listOf(File("vol-20.epub"), File("vol-03.epub")),
            orderBooksByLastRead(books, mapOf("vol-03.epub" to 10L, "vol-20.epub" to 20L))
        )
    }

    @Test
    fun placesUnreadBooksAfterReadBooksInStableNameOrder() {
        val books = listOf(File("zeta.epub"), File("beta.epub"), File("alpha.epub"))

        assertEquals(
            listOf(File("zeta.epub"), File("alpha.epub"), File("beta.epub")),
            orderBooksByLastRead(books, mapOf("zeta.epub" to 1L))
        )
    }
}
