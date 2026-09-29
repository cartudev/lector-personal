package dev.cartu.lector

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.util.BaseActionModeCallback
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.toUri

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : FragmentActivity() {
    private val containerId = R.id.reader_container
    private lateinit var root: FrameLayout
    private lateinit var currentPublication: Publication
    private lateinit var currentBookFile: File
    private lateinit var bookNotes: BookNotesStore
    private lateinit var notesController: BookNotesController
    private val dictionary by lazy { PersonalDictionary(applicationContext) }
    private val matchCache by lazy { BookMatchCache(File(filesDir, "dictionary-match-cache")) }
    private val publicationOpener by lazy { ReadiumPublicationOpener(this) }
    private val highlights by lazy {
        DictionaryHighlightCoordinator(lifecycleScope, dictionary, matchCache, ::showToast)
    }

    private val dictionaryDecorationListener = object : DecorableNavigator.Listener {
        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
            val text = event.decoration.extras["text"] as? String ?: return false
            val entry = dictionary.entry(text) ?: return false
            AlertDialog.Builder(this@ReaderActivity)
                .setTitle(entry.text)
                .setMessage(entry.translation ?: "Sin traducción.")
                .setPositiveButton("Cerrar", null)
                .setNeutralButton("Editar") { _, _ ->
                    val navigator =
                        supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? SelectableNavigator
                    if (navigator != null) {
                        showDictionaryEditor(entry.text, dictionary) { changed ->
                            if (changed) reapplyHighlights()
                        }
                    }
                }
                .show()
            return true
        }
    }

    private val selectionActionModeCallback = object : BaseActionModeCallback() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(Menu.NONE, MENU_COPY, 0, "Copiar")
            menu.add(Menu.NONE, MENU_SAVE_WORD, 1, "Diccionario")
            menu.add(Menu.NONE, MENU_NOTE, 2, "Nota")
            menu.add(Menu.NONE, MENU_TRANSLATE, 3, "Traducir con Translator")
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId !in setOf(MENU_COPY, MENU_SAVE_WORD, MENU_NOTE, MENU_TRANSLATE)) return false
            lifecycleScope.launch { handleSelectionAction(item.itemId) }
            mode.finish()
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this)
        root.id = containerId
        setContentView(root)

        val path = intent.getStringExtra(EXTRA_BOOK_PATH)
        if (path == null) {
            showMessage("No se indicó el archivo EPUB.")
        } else {
            openBook(File(path))
        }
    }

    private fun openBook(file: File) {
        val initialLocator = intent.getStringExtra(EXTRA_LOCATOR_JSON)?.let { serialized ->
            runCatching { Locator.fromJSON(JSONObject(serialized)) }.getOrNull()
        } ?: BookReadHistory(applicationContext).locator(file)
        lifecycleScope.launch {
            showMessage("Abriendo ${file.nameWithoutExtension}...")
            val result = withContext(Dispatchers.IO) { publicationOpener.open(file) }
            result.fold(
                onSuccess = { openedBook ->
                    currentPublication = openedBook.publication
                    currentBookFile = file
                    bookNotes = BookNotesStore(applicationContext, file)
                    notesController = BookNotesController(bookNotes, ::showBookNote)
                    val chrome = ReaderChromeController(
                        activity = this@ReaderActivity,
                        root = root,
                        book = file,
                        publication = currentPublication,
                        navigatorFactory = openedBook.navigatorFactory,
                        navigatorProvider = ::navigator,
                        showToast = ::showToast,
                    )
                    supportFragmentManager.fragmentFactory =
                        openedBook.navigatorFactory.createFragmentFactory(
                            initialLocator = initialLocator,
                            initialPreferences = chrome.initialPreferences,
                            listener = object : EpubNavigatorFragment.Listener {
                                override fun onExternalLinkActivated(url: AbsoluteUrl) {
                                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                }
                            },
                            paginationListener = chrome.paginationListener,
                            configuration = EpubNavigatorFragment.Configuration {
                                selectionActionModeCallback =
                                    this@ReaderActivity.selectionActionModeCallback
                            }
                        )
                    root.removeAllViews()
                    supportFragmentManager.commitNow {
                        replace(containerId, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
                    }
                    val navigator =
                        supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment
                    (navigator as? DecorableNavigator)?.addDecorationListener(
                        DictionaryHighlighter.DECORATION_GROUP,
                        dictionaryDecorationListener
                    )
                    (navigator as? DecorableNavigator)?.addDecorationListener(
                        BookNotesController.DECORATION_GROUP,
                        notesController
                    )
                    chrome.attach()
                    highlights.start(currentPublication, currentBookFile, navigator) { locator ->
                        notesController.applyWindow(navigator, locator)
                    }
                },
                onFailure = { error -> showMessage("No se pudo abrir el EPUB: ${error.message}") }
            )
        }
    }

    private suspend fun handleSelectionAction(actionId: Int) {
        val navigator =
            supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? SelectableNavigator
                ?: return
        val selection = navigator.currentSelection()
        val selectedText = selection?.locator?.text?.highlight.orEmpty()
        if (selectedText.isBlank()) {
            showToast("No se pudo leer la selección.")
            return
        }

        if (actionId == MENU_COPY) {
            getSystemService(android.content.ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Texto seleccionado", selectedText))
            showToast("Texto copiado.")
            navigator.clearSelection()
            return
        }

        if (actionId == MENU_TRANSLATE) {
            if (!launchOfflineTranslator(selectedText)) {
                showToast("Instala Offline Translator desde F-Droid para traducir la selección.")
            }
            navigator.clearSelection()
            return
        }

        if (actionId == MENU_NOTE) {
            val selected = selection ?: return
            val existingNote = bookNotes.notes().firstOrNull {
                it.locator.href == selected.locator.href &&
                    it.locator.text.highlight == selected.locator.text.highlight
            }
            if (existingNote != null) {
                showBookNote(existingNote)
                navigator.clearSelection()
            } else {
                showBookNoteEditor(
                    bookNotes,
                    NoteEditContext(selectedText, selected.locator)
                ) { changed ->
                    navigator.clearSelection()
                    if (changed != null) {
                        val epubNavigator = this@ReaderActivity.navigator()
                        lifecycleScope.launch {
                            notesController.applyWindow(epubNavigator, epubNavigator.currentLocator.value)
                        }
                    }
                }
            }
            return
        }

        val text = normalizeDictionaryText(selectedText)
        if (text == null) {
            showToast("Selección no válida para el diccionario.")
            navigator.clearSelection()
            return
        }

        showDictionaryEditor(text, dictionary) { changed ->
            navigator.clearSelection()
            if (changed) reapplyHighlights()
        }
    }

    private fun editBookNote(note: BookNote) {
        val epubNavigator = navigator()
        showBookNoteEditor(
            bookNotes,
            NoteEditContext(note.selectedText, note.locator, existing = note)
        ) { changed ->
            if (changed != null) {
                lifecycleScope.launch {
                    notesController.applyWindow(epubNavigator, epubNavigator.currentLocator.value)
                }
            }
        }
    }

    private fun showBookNote(note: BookNote) {
        AlertDialog.Builder(this)
            .setTitle(note.selectedText)
            .setMessage(note.content)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Editar") { _, _ -> editBookNote(note) }
            .setNegativeButton("Eliminar") { _, _ ->
                bookNotes.delete(note.id)
                val epubNavigator = navigator()
                lifecycleScope.launch {
                    notesController.applyWindow(epubNavigator, epubNavigator.currentLocator.value)
                }
                showToast("Nota eliminada.")
            }
            .show()
    }

    private fun reapplyHighlights() {
        highlights.start(currentPublication, currentBookFile, navigator())
    }

    private fun navigator(): EpubNavigatorFragment =
        supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showMessage(message: String) {
        val label = TextView(this).apply {
            text = message
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        root.removeAllViews()
        root.addView(label, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    companion object {
        const val EXTRA_BOOK_PATH = "dev.cartu.lector.BOOK_PATH"
        const val EXTRA_LOCATOR_JSON = "dev.cartu.lector.LOCATOR_JSON"
        private const val NAVIGATOR_TAG = "epub-navigator"
        private const val MENU_COPY = 1
        private const val MENU_SAVE_WORD = 2
        private const val MENU_TRANSLATE = 3
        private const val MENU_NOTE = 4
    }
}
