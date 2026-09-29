package dev.cartu.lector

import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalReadiumApi::class)
internal class BookNotesController(
    private val store: BookNotesStore,
    private val onNoteActivated: (BookNote) -> Unit,
) : DecorableNavigator.Listener {
    override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
        val noteId = event.decoration.extras["noteId"] as? String ?: return false
        val note = store.note(noteId) ?: return false
        onNoteActivated(note)
        return true
    }

    suspend fun applyWindow(navigator: EpubNavigatorFragment, locator: Locator) {
        val progression = locator.locations.totalProgression ?: return
        val decorations = store.notes().asSequence()
            .filter { note ->
                val noteProgression = note.locator.locations.totalProgression ?: return@filter false
                kotlin.math.abs(noteProgression - progression) <= DictionaryHighlighter.WINDOW_DELTA
            }
            .take(MAX_NOTES_IN_WINDOW)
            .map { note ->
                Decoration(
                    id = note.id,
                    locator = note.locator,
                    style = Decoration.Style.Underline(
                        tint = colorWithAlpha(NOTE_COLOR, DICTIONARY_HIGHLIGHT_ALPHA)
                    ),
                    extras = mapOf("noteId" to note.id)
                )
            }
            .toList()
        navigator.applyDecorations(decorations, DECORATION_GROUP)
    }

    companion object {
        const val DECORATION_GROUP = "book-notes"
        private const val NOTE_COLOR = 0xFF8B5CF6.toInt()
        private const val MAX_NOTES_IN_WINDOW = 10
    }
}
