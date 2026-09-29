package dev.cartu.lector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun NotesLibraryScreen(
    notes: List<LibraryBookNote>,
    onBack: () -> Unit,
    onOpenNote: (LibraryBookNote) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("Biblioteca") }
        Text("Notas", style = MaterialTheme.typography.headlineMedium)
        if (notes.isEmpty()) {
            Text("Todavia no hay notas. Selecciona un fragmento al leer y elige Nota.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { "${it.book.name}:${it.note.id}" }) { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenNote(item) }
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(item.book.nameWithoutExtension, style = MaterialTheme.typography.labelMedium)
                        Text(item.note.selectedText, style = MaterialTheme.typography.titleMedium)
                        Text(item.note.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
