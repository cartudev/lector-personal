package dev.cartu.lector

import android.app.AlertDialog
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import java.util.UUID
import org.readium.r2.shared.publication.Locator

internal data class NoteEditContext(
    val selectedText: String,
    val locator: Locator,
    val existing: BookNote? = null,
)

internal fun FragmentActivity.showBookNoteEditor(
    store: BookNotesStore,
    context: NoteEditContext,
    onChanged: (BookNote?) -> Unit,
) {
    val content = EditText(this).apply {
        hint = "Escribe tu nota"
        minLines = 2
        maxLines = 6
        setText(context.existing?.content.orEmpty())
    }
    val excerpt = TextView(this).apply {
        text = context.selectedText
        setPadding(0, 0, 0, 16)
    }
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 8, 48, 0)
        addView(excerpt, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    AlertDialog.Builder(this)
        .setTitle(if (context.existing == null) "Nota" else "Editar nota")
        .setView(layout)
        .setNegativeButton(if (context.existing == null) "Cancelar" else "Eliminar") { _, _ ->
            if (context.existing == null) {
                onChanged(null)
            } else {
                store.delete(context.existing.id)
                onChanged(context.existing)
            }
        }
        .setPositiveButton("Guardar") { _, _ ->
            val noteText = content.text.toString().trim()
            if (noteText.isEmpty()) {
                Toast.makeText(this, "Escribe una nota para guardarla.", Toast.LENGTH_SHORT).show()
                onChanged(null)
            } else {
                val note = context.existing?.copy(content = noteText) ?: BookNote(
                    id = UUID.randomUUID().toString(),
                    selectedText = context.selectedText,
                    content = noteText,
                    locator = context.locator,
                )
                store.save(note)
                onChanged(note)
            }
        }
        .show()
}
