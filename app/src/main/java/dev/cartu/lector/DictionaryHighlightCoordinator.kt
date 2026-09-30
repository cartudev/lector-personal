package dev.cartu.lector

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalReadiumApi::class, FlowPreview::class)
internal class DictionaryHighlightCoordinator(
    private val scope: CoroutineScope,
    private val dictionary: PersonalDictionary,
    private val cache: BookMatchCache,
    private val decorationQueue: NavigatorDecorationQueue,
    private val onMessage: (String) -> Unit,
) {
    private data class PageContext(
        val pageIndex: Int,
        val totalPages: Int,
        val locator: Locator,
    )

    private val matchIndex = MutableStateFlow<Map<String, List<DictionaryHighlighter.Match>>>(emptyMap())
    private val pageContext = MutableStateFlow<PageContext?>(null)
    private var indexJob: Job? = null
    private var windowJob: Job? = null

    fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
        pageContext.value = PageContext(pageIndex, totalPages, locator)
    }

    fun start(
        publication: Publication,
        book: File,
        navigator: EpubNavigatorFragment,
        onLocatorChanged: suspend (Locator) -> Unit = {},
    ) {
        indexJob?.cancel()

        if (windowJob?.isActive != true) {
            windowJob = scope.launch {
                combine(pageContext, matchIndex) { page, matches -> page?.let { it to matches } }
                    .filterNotNull()
                    .debounce(WINDOW_DEBOUNCE_MS)
                    .conflate()
                    .collect { (page, matches) ->
                        try {
                            applyWindow(navigator, page, matches)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Log.e(TAG, "Failed to update dictionary decorations", error)
                            onMessage("No se pudieron actualizar los resaltados de esta página.")
                        }
                        try {
                            onLocatorChanged(page.locator)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Log.e(TAG, "Failed to update note decorations", error)
                            onMessage("No se pudieron actualizar las notas de esta página.")
                        }
                    }
            }
        }

        matchIndex.value = emptyMap()
        indexJob = scope.launch {
            try {
                Log.d(TAG, "Indexing dictionary (${dictionary.entries().size} entries)")
                val startedAt = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) {
                    DictionaryHighlighter(publication, book, cache).index(dictionary.entries())
                }
                matchIndex.value = result.matches.groupBy { it.locator.href.toString() }
                Log.d(
                    TAG,
                    "Indexed ${result.matches.size} matches in ${System.currentTimeMillis() - startedAt}ms"
                )
                if (result.errors.isNotEmpty()) {
                    onMessage("No se pudo buscar: ${result.errors.joinToString(", ")}")
                }
                if (result.cappedTexts.isNotEmpty()) {
                    onMessage(
                        "Demasiadas coincidencias de ${result.cappedTexts.joinToString(", ")}; " +
                            "se resaltan las cercanas a tu lectura."
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to index dictionary", error)
                onMessage("No se pudieron preparar los resaltados: ${error.localizedMessage}")
            }
        }
    }

    private suspend fun applyWindow(
        navigator: EpubNavigatorFragment,
        page: PageContext,
        matches: Map<String, List<DictionaryHighlighter.Match>>,
    ) {
        val decorations = DictionaryHighlighter.windowDecorations(
            matches = matches[page.locator.href.toString()].orEmpty(),
            currentLocator = page.locator,
            pageIndex = page.pageIndex,
            totalPages = page.totalPages,
        )
        val startedAt = System.currentTimeMillis()
        (navigator as? DecorableNavigator)?.let { decorable ->
            decorationQueue.replace(
                clear = {
                    decorable.applyDecorations(emptyList(), DictionaryHighlighter.DECORATION_GROUP)
                },
                update = {
                    if (decorations.isNotEmpty()) {
                        decorable.applyDecorations(decorations, DictionaryHighlighter.DECORATION_GROUP)
                    }
                },
            )
        }
        Log.d(
            TAG,
            "Page window: ${decorations.size} decorations in " +
                "${System.currentTimeMillis() - startedAt}ms " +
                "(page=${page.pageIndex + 1}/${page.totalPages}, href=${page.locator.href})"
        )
    }

    companion object {
        private const val TAG = "LectorPersonal"
        private const val WINDOW_DEBOUNCE_MS = 120L
    }
}
