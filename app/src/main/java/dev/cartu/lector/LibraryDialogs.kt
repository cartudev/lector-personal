package dev.cartu.lector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal fun paletteColor(colorIndex: Int): Color =
    Color(DICTIONARY_COLORS[colorIndex.coerceIn(DICTIONARY_COLORS.indices)])

@Composable
internal fun DictionaryListDialog(
    entries: List<DictionaryEntry>,
    onEntrySelected: (DictionaryEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Diccionario personal") },
        text = {
            if (entries.isEmpty()) {
                Text("Todavia no guardaste palabras. Selecciona texto dentro de un libro para agregarlo.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(entries, key = { it.text }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEntrySelected(entry) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(paletteColor(entry.colorIndex))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(entry.text, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    entry.translation ?: "Sin traducción",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
internal fun DictionaryEntryDialog(
    entry: DictionaryEntry,
    translation: String,
    onTranslationChange: (String) -> Unit,
    colorIndex: Int,
    onColorSelected: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = translation,
                    onValueChange = onTranslationChange,
                    label = { Text("Traducción (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DICTIONARY_COLORS.forEachIndexed { index, color ->
                        val selected = index == colorIndex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) Color.Black else Color(0x33000000),
                                    shape = CircleShape,
                                )
                                .clickable { onColorSelected(index) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Guardar") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Eliminar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
