package dev.cartu.lector

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryPageWindowTest {
    @Test
    fun includesOnlyMatchesOnCurrentResourceWithinThreePages() {
        val totalPages = 20
        val chapter = "chapter.xhtml"
        val matches = listOf(
            match("too-far-before", chapter, page = 6, totalPages),
            match("lower-edge", chapter, page = 7, totalPages),
            match("current", chapter, page = 10, totalPages),
            match("upper-edge", chapter, page = 13, totalPages),
            match("too-far-after", chapter, page = 14, totalPages),
            match("other-resource", "next.xhtml", page = 10, totalPages),
        )

        val visible = DictionaryHighlighter.pageWindowMatches(
            matches = matches,
            currentResourceHref = chapter,
            pageIndex = 10,
            totalPages = totalPages,
        )

        assertEquals(
            listOf("lower-edge", "current", "upper-edge"),
            visible.map { it.id },
        )
    }

    @Test
    fun doesNotTruncateMatchesThatFallInsideTheSevenPageWindow() {
        val chapter = "chapter.xhtml"
        val matches = (0 until 32).map { index ->
            DictionaryHighlighter.PageMatch(
                id = "term-$index",
                resourceHref = chapter,
                progression = 0.5,
            )
        }

        val visible = DictionaryHighlighter.pageWindowMatches(
            matches = matches,
            currentResourceHref = chapter,
            pageIndex = 10,
            totalPages = 20,
        )

        assertEquals(32, visible.size)
    }

    private fun match(id: String, href: String, page: Int, totalPages: Int) =
        DictionaryHighlighter.PageMatch(
            id = id,
            resourceHref = href,
            progression = page.toDouble() / totalPages,
        )
}
