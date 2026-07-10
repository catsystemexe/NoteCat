package cz.notecat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import cz.notecat.NoteCatApp
import cz.notecat.audio.AudioRecorder
import cz.notecat.data.NoteRepository
import cz.notecat.data.ProcessingStatus
import cz.notecat.voice.EndCommandRecognizer
import kotlinx.coroutines.launch

@Composable fun repo(): NoteRepository { val c = LocalContext.current; return remember { NoteRepository(c, (c.applicationContext as NoteCatApp).db.noteDao()) } }
fun ProcessingStatus.userText() = when(this) { ProcessingStatus.PENDING -> "Uloženo, zpracovávám."; ProcessingStatus.PROCESSING -> "Zpracovávám audio."; ProcessingStatus.DONE -> "Hotovo"; ProcessingStatus.FAILED -> "Zpracování selhalo" }

@Composable fun CaptureScreen(nav: NavController) {
    val context = LocalContext.current; val repository = repo(); val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(false) }; var stopping by remember { mutableStateOf(false) }
    val recorder = remember { AudioRecorder(context) }
    fun finish() { if (stopping || saved) return; stopping = true; scope.launch { val file = recorder.stopSafely(); repository.createPending(file); saved = true } }
    DisposableEffect(Unit) { recorder.start(); val voice = EndCommandRecognizer(context) { finish() }; voice.start(); onDispose { voice.stop(); if (!saved) runCatching { recorder.stopSafely() } } }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("NoteCat capture", style = MaterialTheme.typography.headlineMedium); Text(if (saved) "Uloženo, zpracovávám." else "Nahrávám. Řekněte „Konec“ nebo použijte tlačítko."); Button(onClick = { if (saved) nav.navigate("list") else finish() }) { Text(if (saved) "Zpět na poznámky" else "Ukončit a uložit") } }
}

@Composable fun NoteListScreen(nav: NavController) {
    val repository = repo(); var query by remember { mutableStateOf("") }; val notes by repository.observeNotes(query).collectAsStateWithLifecycle(emptyList())
    Column(Modifier.padding(16.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ nav.navigate("capture") }) { Text("Nová hlasová poznámka") } }; OutlinedTextField(query, { query = it }, label = { Text("Hledat") }, modifier = Modifier.fillMaxWidth()); LazyColumn { items(notes) { n -> ListItem(headlineContent = { Text(n.noteText.ifBlank { "Bez názvu" }) }, supportingContent = { Text(n.processingStatus.userText()) }, modifier = Modifier.fillMaxWidth().padding(4.dp)); Button({ nav.navigate("detail/${n.id}") }) { Text("Detail") } } } }
}

@Composable fun DetailScreen(nav: NavController, id: String) {
    val repository = repo(); val scope = rememberCoroutineScope(); var note by remember { mutableStateOf<cz.notecat.data.Note?>(null) }
    LaunchedEffect(id) { note = repository.get(id) }
    val n = note ?: return Text("Načítám…")
    var text by remember(n.id, n.noteText) { mutableStateOf(n.noteText) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(n.processingStatus.userText(), style = MaterialTheme.typography.titleMedium); n.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }; OutlinedTextField(text, { text = it }, label = { Text("Poznámka") }, modifier = Modifier.fillMaxWidth().height(220.dp)); Text("Přepis: ${n.transcriptOriginal ?: "čeká se"}"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ scope.launch { repository.update(n.copy(noteText = text)); nav.popBackStack() } }) { Text("Uložit") }; Button({ repository.enqueue(n.id) }, enabled = n.processingStatus == ProcessingStatus.FAILED) { Text("Retry") }; Button({ scope.launch { repository.delete(n); nav.navigate("list") } }) { Text("Smazat") } } }
}
