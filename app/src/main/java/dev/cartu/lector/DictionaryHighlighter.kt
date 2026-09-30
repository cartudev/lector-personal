package dev.cartu.lector

import android.util.Log
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.getOrElse

@OptIn(ExperimentalReadiumApi::class)
internal class DictionaryHighlighter(
    private val publication: Publication,
    private val book: java.io.File,
    private val cache: BookMatchCache,
) {
    data class Match(
        val text: String,
        val colorIndex: Int,
        val id: String,
        val locator: Locator,
        val progression: Double,
    )

    data class PageMatch(
        val id: String,
        val resourceHref: String,
        val progression: Double,
    )

    data class PageDecorationGroup(
        val pageIndex: Int,
        val decorations: List<Decoration>,
    )

    data class IndexResult(
        val matches: List<Match>,
        val cappedTexts: List<String>,
        val errors: List<String>,
    )

    suspend fun index(entries: List<DictionaryEntry>): IndexResult {
        val matches = mutableListOf<Match>()
        val cappedTexts = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (entry in entries) {
            val startedAt = System.currentTimeMillis()
            val found = cache.read(book, entry.text)?.let { cached ->
                Log.d(TAG, "Match cache hit for '${entry.text}' (${cached.locators.size})")
                Matches(cached.locators, cached.capped)
            } ?: try {
                findMatches(entry.text).also { result ->
                    cache.write(book, entry.text, BookMatchCache.CachedMatches(result.locators, result.capped))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Search failed for '${entry.text}'", error)
                errors += entry.text
                continue
            }
            Log.d(
                TAG,
                "Search '${entry.text}': ${found.locators.size} matches " +
                    "in ${System.currentTimeMillis() - startedAt}ms (capped=${found.capped})"
            )
            if (found.capped) cappedTexts += entry.text

            var indexed = 0
            found.locators.forEachIndexed { index, locator ->
                val progression = locator.locations.progression ?: return@forEachIndexed
                indexed++
                matches += Match(
                    text = entry.text,
                    colorIndex = entry.colorIndex,
                    id = "${entry.text}-$index",
                    locator = locator,
                    progression = progression,
                )
            }
            val skipped = found.locators.size - indexed
            if (skipped > 0) {
                Log.w(TAG, "'${entry.text}': $skipped matches without progression skipped")
            }
        }

        return IndexResult(matches = matches, cappedTexts = cappedTexts, errors = errors)
    }

    private data class Matches(val locators: List<Locator>, val capped: Boolean)

    private suspend fun findMatches(query: String): Matches {
        val iterator = publication.search(
            query,
            SearchService.Options(caseSensitive = false, wholeWord = false)
        ) ?: run {
            Log.w(TAG, "Publication is not searchable; skipping '$query'")
            return Matches(emptyList(), capped = false)
        }

        val matches = mutableListOf<Locator>()
        var capped = false
        try {
            while (true) {
                val page = iterator.next().getOrElse {
                    error("Falló la búsqueda de '$query': ${it.message}")
                } ?: break
                for (locator in page.locators) {
                    if (matches.size == MAX_MATCHES_PER_ENTRY) {
                        capped = true
                        break
                    }
                    matches += locator
                }
                if (capped) break
            }
        } finally {
            iterator.close()
        }
        return Matches(matches, capped)
    }

    companion object {
        private const val TAG = "LectorPersonal"
        const val DECORATION_GROUP = "personal-dictionary"
        const val MAX_MATCHES_PER_ENTRY = 400
        const val WINDOW_DELTA = 0.001
        const val PAGES_AROUND_CURRENT = 3
        const val DECORATION_SLOT_COUNT = PAGES_AROUND_CURRENT * 2 + 1

        fun pageWindowMatches(
            matches: List<PageMatch>,
            currentResourceHref: String,
            pageIndex: Int,
            totalPages: Int,
        ): List<PageMatch> = matches.asSequence()
            .filter { match ->
                isInsidePageWindow(
                    matchResourceHref = match.resourceHref,
                    matchProgression = match.progression,
                    currentResourceHref = currentResourceHref,
                    pageIndex = pageIndex,
                    totalPages = totalPages,
                )
            }
            .sortedWith(compareBy<PageMatch>({ it.progression }, { it.id }))
            .toList()

        fun pageWindowMatchesByPage(
            matches: List<PageMatch>,
            currentResourceHref: String,
            pageIndex: Int,
            totalPages: Int,
        ): Map<Int, List<PageMatch>> = pageWindowMatches(
            matches = matches,
            currentResourceHref = currentResourceHref,
            pageIndex = pageIndex,
            totalPages = totalPages,
        ).groupBy { pageForProgression(it.progression, totalPages) }.toSortedMap()

        fun decorationSlotForPage(pageIndex: Int): Int = pageIndex % DECORATION_SLOT_COUNT

        fun decorationGroupForSlot(slot: Int): String {
            require(slot in 0 until DECORATION_SLOT_COUNT)
            return "$DECORATION_GROUP-slot-$slot"
        }

        private fun pageForProgression(progression: Double, totalPages: Int): Int =
            (progression * totalPages).roundToInt().coerceIn(0, totalPages - 1)

        fun isInsidePageWindow(
            matchResourceHref: String,
            matchProgression: Double,
            currentResourceHref: String,
            pageIndex: Int,
            totalPages: Int,
        ): Boolean {
            if (totalPages <= 0 || matchResourceHref != currentResourceHref) return false
            val currentPage = pageIndex.coerceIn(0, totalPages - 1)
            val firstPage = (currentPage - PAGES_AROUND_CURRENT).coerceAtLeast(0)
            val lastPage = (currentPage + PAGES_AROUND_CURRENT).coerceAtMost(totalPages - 1)
            val matchPage = (matchProgression * totalPages).roundToInt().coerceIn(0, totalPages - 1)
            return matchPage in firstPage..lastPage
        }

        fun windowDecorationsByPage(
            matches: List<Match>,
            currentLocator: Locator,
            pageIndex: Int,
            totalPages: Int,
        ): List<PageDecorationGroup> {
            if (totalPages <= 0) return emptyList()
            val matchesById = matches.associateBy { it.id }
            val pageMatches = pageWindowMatchesByPage(
                matches = matches.map { PageMatch(it.id, it.locator.href.toString(), it.progression) },
                currentResourceHref = currentLocator.href.toString(),
                pageIndex = pageIndex,
                totalPages = totalPages,
            )
            return pageMatches.map { (visiblePage, pageItems) ->
                PageDecorationGroup(
                    pageIndex = visiblePage,
                    decorations = pageItems.mapNotNull { matchesById[it.id] }.map(::decoration),
                )
            }
        }

        private fun decoration(match: Match): Decoration = Decoration(
            id = match.id,
            locator = match.locator,
            style = Decoration.Style.Highlight(
                tint = colorWithAlpha(
                    DICTIONARY_COLORS[match.colorIndex.coerceIn(DICTIONARY_COLORS.indices)],
                    DICTIONARY_HIGHLIGHT_ALPHA
                )
            ),
            extras = mapOf("text" to match.text),
        )
    }
}
