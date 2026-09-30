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

    @Test
    fun groupsTheWindowIntoIndividualPagesForSlotUpdates() {
        val chapter = "chapter.xhtml"
        val matches = listOf(
            match("edge-a", chapter, page = 7, totalPages = 20),
            match("middle", chapter, page = 10, totalPages = 20),
            match("edge-b", chapter, page = 13, totalPages = 20),
            match("other-resource", "next.xhtml", page = 10, totalPages = 20),
        )

        val groups = DictionaryHighlighter.pageWindowMatchesByPage(
            matches = matches,
            currentResourceHref = chapter,
            pageIndex = 10,
            totalPages = 20,
        )

        assertEquals(listOf(7, 10, 13), groups.keys.toList())
        assertEquals("edge-a", groups[7]?.single()?.id)
        assertEquals("middle", groups[10]?.single()?.id)
        assertEquals("edge-b", groups[13]?.single()?.id)
    }

    @Test
    fun sortsTheWindowByReadingProgressionForStableUpdates() {
        val chapter = "chapter.xhtml"
        val matches = listOf(
            match("later-page", chapter, page = 13, totalPages = 20),
            match("earlier-page", chapter, page = 7, totalPages = 20),
        )

        val visible = DictionaryHighlighter.pageWindowMatches(
            matches = matches,
            currentResourceHref = chapter,
            pageIndex = 10,
            totalPages = 20,
        )

        assertEquals(listOf("earlier-page", "later-page"), visible.map { it.id })
    }

    @Test
    fun advancingOnePageReusesSixOfSevenDecorationSlots() {
        val firstWindow = (4..10).associateBy(DictionaryHighlighter::decorationSlotForPage)
        val nextWindow = (5..11).associateBy(DictionaryHighlighter::decorationSlotForPage)
        val unchangedSlots = firstWindow.count { (slot, page) -> nextWindow[slot] == page }

        assertEquals(7, firstWindow.size)
        assertEquals(7, nextWindow.size)
        assertEquals(6, unchangedSlots)
        assertEquals(
            DictionaryHighlighter.decorationSlotForPage(4),
            DictionaryHighlighter.decorationSlotForPage(11),
        )
    }

    private fun match(id: String, href: String, page: Int, totalPages: Int) =
        DictionaryHighlighter.PageMatch(
            id = id,
            resourceHref = href,
            progression = page.toDouble() / totalPages,
        )
}
