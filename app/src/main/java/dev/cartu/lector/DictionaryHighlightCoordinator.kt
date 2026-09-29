package dev.cartu.lector

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
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
    private val onMessage: (String) -> Unit,
) {
    private var matchIndex: List<DictionaryHighlighter.Match> = emptyList()
    private var indexJob: Job? = null
    private var windowJob: Job? = null

    fun start(
        publication: Publication,
        book: File,
        navigator: EpubNavigatorFragment,
        onLocatorChanged: suspend (Locator) -> Unit = {},
    ) {
        indexJob?.cancel()
        windowJob?.cancel()

        windowJob = scope.launch {
            navigator.currentLocator
                .debounce(WINDOW_DEBOUNCE_MS)
                .conflate()
                .collect { locator ->
                    applyWindow(navigator, locator)
                    onLocatorChanged(locator)
                }
        }

        indexJob = scope.launch {
            try {
                Log.d(TAG, "Indexing dictionary (${dictionary.entries().size} entries)")
                val startedAt = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) {
                    DictionaryHighlighter(publication, book, cache).index(dictionary.entries())
                }
                matchIndex = result.matches
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
                applyWindow(navigator, navigator.currentLocator.value)
                onLocatorChanged(navigator.currentLocator.value)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to index dictionary", error)
                onMessage("No se pudieron preparar los resaltados: ${error.localizedMessage}")
            }
        }
    }

    private suspend fun applyWindow(navigator: EpubNavigatorFragment, locator: Locator) {
        val progression = locator.locations.totalProgression ?: return
        val decorations = DictionaryHighlighter.windowDecorations(matchIndex, progression)
        val startedAt = System.currentTimeMillis()
        (navigator as? DecorableNavigator)?.applyDecorations(
            decorations,
            DictionaryHighlighter.DECORATION_GROUP
        )
        Log.d(
            TAG,
            "Window: ${decorations.size} decorations in " +
                "${System.currentTimeMillis() - startedAt}ms (progression=$progression)"
        )
    }

    companion object {
        private const val TAG = "LectorPersonal"
        private const val WINDOW_DEBOUNCE_MS = 300L
    }
}
