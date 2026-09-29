package dev.cartu.lector

import android.util.Log
import kotlin.math.abs
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
                val progression = locator.locations.totalProgression ?: return@forEachIndexed
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
        const val MAX_WINDOW_DECORATIONS = 10

        fun windowDecorations(
            matches: List<Match>,
            currentProgression: Double,
        ): List<Decoration> = matches.asSequence()
            .filter { abs(it.progression - currentProgression) <= WINDOW_DELTA }
            .take(MAX_WINDOW_DECORATIONS)
            .map { match ->
                Decoration(
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
            .toList()
    }
}
