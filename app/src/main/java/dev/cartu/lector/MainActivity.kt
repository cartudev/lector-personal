package dev.cartu.lector

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LibraryScreen()
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictionary = remember { PersonalDictionary(context) }
    val readHistory = remember { BookReadHistory(context) }
    val books = remember {
        mutableStateListOf<File>().apply { addAll(readHistory.order(readBooks(context))) }
    }
    val dictionaryEntries = remember {
        mutableStateListOf<DictionaryEntry>().apply { addAll(dictionary.entries()) }
    }
    val libraryNotes = remember { mutableStateListOf<LibraryBookNote>() }
    var dictionaryOpen by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<DictionaryEntry?>(null) }
    var editTranslation by remember { mutableStateOf("") }
    var editColor by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refreshEntries() {
        dictionaryEntries.clear()
        dictionaryEntries.addAll(dictionary.entries())
    }

    fun refreshBooks() {
        books.clear()
        books.addAll(readHistory.order(readBooks(context)))
    }

    fun refreshNotes() {
        libraryNotes.clear()
        books.forEach { book ->
            BookNotesStore(context, book).notes().forEach { note ->
                libraryNotes.add(LibraryBookNote(book, note))
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshEntries()
                refreshBooks()
                refreshNotes()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { importBook(context, uri) }
                result.fold(
                    onSuccess = { file ->
                        if (books.none { it.name == file.name }) books.add(file)
                        refreshBooks()
                        message = "Importado: ${file.nameWithoutExtension}"
                    },
                    onFailure = { message = "No se pudo importar el EPUB: ${it.localizedMessage}" }
                )
            }
        }
    }

    if (notesOpen) {
        NotesLibraryScreen(
            notes = libraryNotes,
            onBack = { notesOpen = false },
            onOpenNote = { item ->
                readHistory.markRead(item.book)
                refreshBooks()
                context.startActivity(
                    Intent(context, ReaderActivity::class.java)
                        .putExtra(ReaderActivity.EXTRA_BOOK_PATH, item.book.absolutePath)
                        .putExtra(ReaderActivity.EXTRA_LOCATOR_JSON, item.note.locator.toJSON().toString())
                )
            }
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Tu biblioteca", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Una lectura que aprende las palabras que marcas.",
                style = MaterialTheme.typography.bodyLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    importer.launch(arrayOf("application/epub+zip", "application/octet-stream"))
                }) {
                    Text("Importar EPUB")
                }
                TextButton(onClick = { dictionaryOpen = true }) {
                    Text("Diccionario (${dictionaryEntries.size})")
                }
                TextButton(onClick = {
                    refreshNotes()
                    notesOpen = true
                }) {
                    Text("Notas (${libraryNotes.size})")
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (books.isEmpty()) {
                Text("Todavia no hay libros. Importa un archivo EPUB para empezar.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(books, key = { it.name }) { book ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                readHistory.markRead(book)
                                refreshBooks()
                                context.startActivity(
                                    Intent(context, ReaderActivity::class.java)
                                        .putExtra(ReaderActivity.EXTRA_BOOK_PATH, book.absolutePath)
                                )
                            }.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(book.nameWithoutExtension, style = MaterialTheme.typography.titleMedium)
                            Text("EPUB", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    if (dictionaryOpen) {
        DictionaryListDialog(
            entries = dictionaryEntries,
            onEntrySelected = { entry ->
                editingEntry = entry
                editTranslation = entry.translation.orEmpty()
                editColor = entry.colorIndex
                dictionaryOpen = false
            },
            onDismiss = { dictionaryOpen = false },
        )
    }

    editingEntry?.let { entry ->
        DictionaryEntryDialog(
            entry = entry,
            translation = editTranslation,
            onTranslationChange = { editTranslation = it },
            colorIndex = editColor,
            onColorSelected = { editColor = it },
            onSave = {
                dictionary.saveEntry(entry.text, editTranslation, editColor)
                refreshEntries()
                editingEntry = null
            },
            onDelete = {
                dictionary.deleteEntry(entry.text)
                refreshEntries()
                editingEntry = null
            },
            onDismiss = { editingEntry = null },
        )
    }
}

private fun readBooks(context: Context): List<File> =
    File(context.filesDir, "books").listFiles()
        ?.filter { it.isFile && it.extension.equals("epub", ignoreCase = true) }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()

private fun importBook(context: Context, uri: Uri): Result<File> = runCatching {
    val directory = File(context.filesDir, "books").apply { mkdirs() }
    val rawName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    } ?: "libro.epub"
    val safeName = rawName.substringAfterLast('/').substringBeforeLast('.')
        .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
        .ifBlank { "libro" }
    val target = File(directory, "$safeName.epub")
    val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
        "No se pudo abrir el archivo."
    }
    input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
    target
}
