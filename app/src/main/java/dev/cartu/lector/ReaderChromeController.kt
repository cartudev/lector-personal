package dev.cartu.lector

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesEditor
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import kotlin.math.roundToInt

internal data class ReaderPageState(
    val title: String,
    val page: Int = 0,
    val pageCount: Int = 0,
    val progress: Float = 0f,
    val fontPercent: Int = 100,
    val theme: String = "Claro",
    val bookmarked: Boolean = false,
    val canChangeText: Boolean = true,
)

internal data class ReaderDestination(
    val id: String,
    val label: String,
    val depth: Int = 0,
    val locator: Locator? = null,
    val link: Link? = null,
)

internal data class ReaderChromeActions(
    val onBack: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onFontSmaller: () -> Unit,
    val onFontLarger: () -> Unit,
    val onTheme: () -> Unit,
    val onNavigate: (ReaderDestination) -> Unit,
)

@OptIn(ExperimentalReadiumApi::class)
internal class ReaderChromeController(
    private val activity: FragmentActivity,
    private val root: FrameLayout,
    private val book: java.io.File,
    private val publication: Publication,
    private val navigatorFactory: EpubNavigatorFactory,
    private val navigatorProvider: () -> EpubNavigatorFragment,
    private val showToast: (String) -> Unit,
) {
    private val navigator: EpubNavigatorFragment
        get() = navigatorProvider()
    private val bookmarksStore = BookBookmarksStore(activity, book)
    private val readingHistory = BookReadHistory(activity)
    private val settings = activity.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
    private val settingsPrefix = "book:${book.canonicalPath.hashCode()}"
    private val preferencesEditor: EpubPreferencesEditor =
        navigatorFactory.createPreferencesEditor(
            EpubPreferences(
                fontSize = settings.getFloat("$settingsPrefix:font-size", 1f).toDouble(),
                theme = settings.getString("$settingsPrefix:theme", null)
                    ?.let { runCatching { Theme.valueOf(it) }.getOrNull() },
            )
        )
    val initialPreferences: EpubPreferences
        get() = preferencesEditor.preferences
    private var pageState by mutableStateOf(
        ReaderPageState(
            title = publication.metadata.title ?: book.nameWithoutExtension,
            fontPercent = (preferencesEditor.fontSize.effectiveValue * 100).roundToInt(),
            theme = preferencesEditor.theme.effectiveValue.label(),
            canChangeText = preferencesEditor.fontSize.isEffective || preferencesEditor.theme.isEffective,
        )
    )
    private var contentsOpen by mutableStateOf(false)
    private val tableOfContents = flattenLinks(publication.tableOfContents)
    private val actions = ReaderChromeActions(
        onBack = activity::finish,
        onToggleBookmark = ::toggleBookmark,
        onPrevious = { (navigator as? OverflowableNavigator)?.goBackward() },
        onNext = { (navigator as? OverflowableNavigator)?.goForward() },
        onFontSmaller = { adjustFont(increase = false) },
        onFontLarger = { adjustFont(increase = true) },
        onTheme = ::cycleTheme,
        onNavigate = ::navigate,
    )

    val paginationListener = object : EpubNavigatorFragment.PaginationListener {
        override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
            val progression = locator.locations.totalProgression?.toFloat()?.coerceIn(0f, 1f) ?: 0f
            pageState = pageState.copy(
                page = pageIndex + 1,
                pageCount = totalPages,
                progress = progression,
                bookmarked = bookmarksStore.contains(locator),
            )
            readingHistory.saveLocator(book, locator)
        }
    }

    fun attach() {
        val topBar = ComposeView(activity).apply {
            setContent {
                MaterialTheme {
                    ReaderTopBar(
                        state = pageState,
                        tableOfContents = tableOfContents,
                        bookmarks = bookmarksStore.bookmarks().map { bookmark ->
                            ReaderDestination(
                                id = bookmark.id,
                                label = bookmark.label,
                                locator = bookmark.locator,
                            )
                        },
                        contentsOpen = contentsOpen,
                        onContentsOpenChange = { contentsOpen = it },
                        actions = actions,
                    )
                }
            }
        }
        val bottomBar = ComposeView(activity).apply {
            setContent {
                MaterialTheme {
                    ReaderBottomBar(pageState, actions)
                }
            }
        }
        root.addView(
            topBar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP),
        )
        root.addView(
            bottomBar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
        )
    }

    private fun toggleBookmark() {
        val saved = bookmarksStore.toggle(navigator.currentLocator.value)
        pageState = pageState.copy(bookmarked = saved)
        showToast(if (saved) "Marcador guardado." else "Marcador eliminado.")
    }

    private fun navigate(destination: ReaderDestination) {
        when {
            destination.locator != null -> navigator.go(destination.locator)
            destination.link != null -> navigator.go(destination.link)
        }
        contentsOpen = false
    }

    private fun adjustFont(increase: Boolean) {
        if (!preferencesEditor.fontSize.isEffective) {
            showToast("El tamaño de texto no se puede cambiar en este EPUB.")
            return
        }
        if (increase) preferencesEditor.fontSize.increment() else preferencesEditor.fontSize.decrement()
        settings.edit()
            .putFloat("$settingsPrefix:font-size", preferencesEditor.fontSize.effectiveValue.toFloat())
            .apply()
        navigator.submitPreferences(preferencesEditor.preferences)
        pageState = pageState.copy(fontPercent = (preferencesEditor.fontSize.effectiveValue * 100).roundToInt())
    }

    private fun cycleTheme() {
        if (!preferencesEditor.theme.isEffective) {
            showToast("El tema no se puede cambiar en este EPUB.")
            return
        }
        val themes = preferencesEditor.theme.supportedValues
        val current = preferencesEditor.theme.effectiveValue
        val next = themes[(themes.indexOf(current) + 1) % themes.size]
        preferencesEditor.theme.set(next)
        settings.edit().putString("$settingsPrefix:theme", next.name).apply()
        navigator.submitPreferences(preferencesEditor.preferences)
        pageState = pageState.copy(theme = next.label())
    }

    private fun flattenLinks(links: List<Link>): List<ReaderDestination> = buildList {
        fun addLinks(items: List<Link>, depth: Int) {
            items.forEachIndexed { index, link ->
                add(
                    ReaderDestination(
                        id = "${depth}:${link.href}:$index",
                        label = link.title?.takeIf(String::isNotBlank) ?: link.href.toString(),
                        depth = depth,
                        link = link,
                    )
                )
                addLinks(link.children, depth + 1)
            }
        }
        addLinks(links, 0)
    }

    private fun Theme.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)

    private companion object {
        const val SETTINGS_NAME = "reader_preferences"
    }
}

@Composable
private fun ReaderTopBar(
    state: ReaderPageState,
    tableOfContents: List<ReaderDestination>,
    bookmarks: List<ReaderDestination>,
    contentsOpen: Boolean,
    onContentsOpenChange: (Boolean) -> Unit,
    actions: ReaderChromeActions,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = actions.onBack) { Text("Atrás") }
            Text(
                text = state.title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(
                onClick = { onContentsOpenChange(true) },
                enabled = tableOfContents.isNotEmpty() || bookmarks.isNotEmpty(),
            ) { Text("Índice") }
            TextButton(
                onClick = actions.onToggleBookmark,
                modifier = Modifier.semantics {
                    contentDescription = if (state.bookmarked) "Eliminar marcador" else "Guardar marcador"
                    stateDescription = if (state.bookmarked) "Guardado" else "No guardado"
                },
            ) { Text(if (state.bookmarked) "Guardado" else "Marcar") }
        }
    }

    if (contentsOpen) {
        AlertDialog(
            onDismissRequest = { onContentsOpenChange(false) },
            title = { Text("Índice y marcadores") },
            text = {
                if (tableOfContents.isEmpty() && bookmarks.isEmpty()) {
                    Text("Este libro todavía no tiene índice ni marcadores.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                        if (tableOfContents.isNotEmpty()) {
                            item { Text("Contenido", style = MaterialTheme.typography.labelLarge) }
                            items(tableOfContents, key = { it.id }) { destination ->
                                DestinationRow(destination, actions.onNavigate)
                            }
                        }
                        if (bookmarks.isNotEmpty()) {
                            item {
                                Text(
                                    "Marcadores",
                                    modifier = Modifier.padding(top = 12.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            items(bookmarks, key = { it.id }) { destination ->
                                DestinationRow(destination, actions.onNavigate)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onContentsOpenChange(false) }) { Text("Cerrar") }
            },
        )
    }
}

@Composable
private fun DestinationRow(destination: ReaderDestination, onNavigate: (ReaderDestination) -> Unit) {
    TextButton(
        onClick = { onNavigate(destination) },
        modifier = Modifier.fillMaxWidth().padding(start = (destination.depth * 12).dp),
    ) {
        Text(destination.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start, maxLines = 2)
    }
}

@Composable
private fun ReaderBottomBar(state: ReaderPageState, actions: ReaderChromeActions) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = actions.onPrevious,
                    enabled = state.page > 1 || state.progress > 0f,
                    modifier = Modifier.semantics { contentDescription = "Página anterior" },
                ) { Text("Anterior") }
                Text(
                    text = "${pageLabel(state)} · ${(state.progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = actions.onNext,
                    enabled = state.pageCount == 0 || state.page < state.pageCount || state.progress < 1f,
                    modifier = Modifier.semantics { contentDescription = "Página siguiente" },
                ) { Text("Siguiente") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = actions.onFontSmaller, enabled = state.canChangeText) { Text("A−") }
                Text("${state.fontPercent}%", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = actions.onFontLarger, enabled = state.canChangeText) { Text("A+") }
                TextButton(onClick = actions.onTheme, enabled = state.canChangeText) { Text("Tema: ${state.theme}") }
            }
        }
    }
}

private fun pageLabel(state: ReaderPageState): String = when {
    state.pageCount > 0 -> "Pág. ${state.page}/${state.pageCount}"
    state.page > 0 -> "Pág. ${state.page}"
    else -> "Leyendo"
}
