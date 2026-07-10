package cz.notecat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import cz.notecat.NoteCatApp
import cz.notecat.audio.AudioRecorder
import cz.notecat.data.NoteRepository
import cz.notecat.data.ProcessingStatus
import cz.notecat.ui.demo.DemoNote
import cz.notecat.ui.demo.NoteCatDemoState
import cz.notecat.voice.EndCommandRecognizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable fun repo(): NoteRepository { val c = LocalContext.current; return remember { NoteRepository(c, (c.applicationContext as NoteCatApp).db.noteDao()) } }
fun ProcessingStatus.userText() = when(this) { ProcessingStatus.PENDING -> "Uloženo, zpracovávám."; ProcessingStatus.PROCESSING -> "Zpracovávám"; ProcessingStatus.DONE -> "Hotovo"; ProcessingStatus.FAILED -> "Zpracování selhalo" }

@Composable fun CaptureScreen(nav: NavController) {
    val context = LocalContext.current; val repository = repo(); val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(false) }; var stopping by remember { mutableStateOf(false) }
    val recorder = remember { AudioRecorder(context) }
    fun finish() { if (stopping || saved) return; stopping = true; scope.launch { val file = recorder.stopSafely(); repository.createPending(file); saved = true } }
    DisposableEffect(Unit) { recorder.start(); val voice = EndCommandRecognizer(context) { finish() }; voice.start(); onDispose { voice.stop(); if (!saved) runCatching { recorder.stopSafely() } } }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("NoteCat capture", style = MaterialTheme.typography.headlineMedium); Text(if (saved) "Uloženo, zpracovávám." else "Nahrávám. Řekněte „Konec“ nebo použijte tlačítko."); Button(onClick = { if (saved) nav.navigate("list") else finish() }) { Text(if (saved) "Zpět na poznámky" else "Ukončit a uložit") } }
}

@Composable fun NoteListScreen(@Suppress("UNUSED_PARAMETER") nav: NavController) { NoteCatDemoScreen() }

@Composable
fun NoteCatDemoScreen() {
    GlassNightTheme {
        val tokens = GlassNightTokens
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val store = remember { NoteCatDemoState() }
        var notes by remember { mutableStateOf(store.currentNotes()) }
        var detailNote by remember { mutableStateOf<DemoNote?>(null) }
        var editNote by remember { mutableStateOf<DemoNote?>(null) }
        var isRecording by rememberSaveable { mutableStateOf(false) }
        var recordingSeconds by rememberSaveable { mutableIntStateOf(0) }

        LaunchedEffect(isRecording) {
            if (!isRecording) return@LaunchedEffect
            recordingSeconds = 0
            while (isRecording) { delay(1000); recordingSeconds++ }
        }

        fun refresh() { notes = store.currentNotes() }
        fun finishDemoRecording() {
            isRecording = false
            val created = store.insertProcessing(System.currentTimeMillis())
            refresh()
            scope.launch {
                delay(1500)
                store.completeProcessing(
                    created.id,
                    "Nová demo poznámka z nahrávání: zavolat odpoledne veterináři a domluvit preventivní kontrolu.",
                    "zavolat odpoledne veterináři a domluvit preventivní kontrolu",
                    System.currentTimeMillis(),
                )
                refresh()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize().glassBackground(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButtonPosition = FabPosition.Center,
            floatingActionButton = {
                RecordingButton(isRecording, recordingSeconds) {
                    if (isRecording) finishDemoRecording() else isRecording = true
                }
            },
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .systemBarsPadding()
                    .padding(horizontal = tokens.dimensions.screenPadding)
            ) {
                Text("NoteCat", style = tokens.appTitle, modifier = Modifier.padding(top = 18.dp, bottom = 22.dp))
                if (notes.isEmpty()) EmptyState()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(tokens.dimensions.cardSpacing),
                    contentPadding = PaddingValues(bottom = tokens.dimensions.bottomContentPadding)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onOpen = { detailNote = note },
                            onEdit = { editNote = note },
                            onDelete = {
                                val deleted = store.delete(note.id)
                                refresh()
                                if (deleted != null) scope.launch {
                                    val result = snackbarHostState.showSnackbar("Poznámka smazána", "Vrátit zpět", duration = SnackbarDuration.Short)
                                    if (result == SnackbarResult.ActionPerformed) { store.undo(deleted.first, deleted.second); refresh() }
                                }
                            },
                            onRetry = {
                                store.retry(note.id, System.currentTimeMillis()); refresh()
                                scope.launch { delay(1200); store.completeRetry(note.id, System.currentTimeMillis()); refresh() }
                            }
                        )
                    }
                }
            }
        }
        detailNote?.let { NoteDetailDialog(it, onClose = { detailNote = null }) }
        editNote?.let { note ->
            EditNoteDialog(note, onCancel = { editNote = null }, onSave = { text ->
                store.edit(note.id, text, System.currentTimeMillis()); refresh(); editNote = null
            })
        }
    }
}

@Composable
private fun NoteCard(note: DemoNote, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onRetry: () -> Unit) {
    val tokens = GlassNightTokens
    val revealPx = with(LocalDensity.current) { 104.dp.toPx() }
    var rawOffset by remember(note.id) { mutableFloatStateOf(0f) }
    val targetOffset by animateFloatAsState(rawOffset, tween(tokens.durations.cardAnimationDuration), label = "swipe")
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(tokens.dimensions.cardRadius))
                .background(tokens.colors.danger.copy(alpha = 0.22f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            TextButton(onClick = onDelete, modifier = Modifier.padding(end = 16.dp).heightIn(min = 48.dp)) { Text("Smazat", color = tokens.colors.danger, style = tokens.actionText) }
        }
        GlassSurface(
            elevated = note.status != ProcessingStatus.DONE,
            border = androidx.compose.foundation.BorderStroke(1.dp, when (note.status) { ProcessingStatus.FAILED -> tokens.colors.danger.copy(alpha = .65f); ProcessingStatus.PROCESSING, ProcessingStatus.PENDING -> tokens.colors.processing.copy(alpha = .55f); ProcessingStatus.DONE -> tokens.colors.glassBorder }),
            modifier = Modifier
                .offset { IntOffset(targetOffset.roundToInt(), 0) }
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .draggable(orientation = androidx.compose.foundation.gestures.Orientation.Horizontal, state = rememberDraggableState { delta -> rawOffset = (rawOffset + delta).coerceIn(-revealPx, 0f) }, onDragStopped = { rawOffset = if (rawOffset < -revealPx / 2) -revealPx else 0f })
                .pointerInput(note.id) { detectTapGestures(onTap = { onOpen() }, onDoubleTap = { onEdit() }) }
                .semantics { contentDescription = "Poznámka ${note.status.userText()}" },
        ) { padding ->
            Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(note.text, style = tokens.noteText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusPill(note)
                    if (note.status == ProcessingStatus.FAILED) TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Opakovat", color = tokens.colors.accentBlue, style = tokens.actionText) }
                }
            }
        }
    }
}

@Composable private fun StatusPill(note: DemoNote) {
    val tokens = GlassNightTokens
    val color = when (note.status) { ProcessingStatus.FAILED -> tokens.colors.danger; ProcessingStatus.PROCESSING, ProcessingStatus.PENDING -> tokens.colors.processing; ProcessingStatus.DONE -> tokens.colors.accentBlue }
    val alpha = if (note.status == ProcessingStatus.PROCESSING || note.status == ProcessingStatus.PENDING) {
        rememberInfiniteTransition(label = "processingAlpha").animateFloat(.72f, 1f, infiniteRepeatable(tween(tokens.durations.processingAnimationDuration), RepeatMode.Reverse), label = "processing").value
    } else 1f
    Text(note.errorMessage ?: note.status.userText(), color = color.copy(alpha = alpha), style = tokens.noteSecondary)
}

@Composable private fun RecordingButton(isRecording: Boolean, seconds: Int, onClick: () -> Unit) {
    val tokens = GlassNightTokens
    val pulse = rememberInfiniteTransition(label = "recordingPulse").animateFloat(1f, if (isRecording) 1.12f else 1f, infiniteRepeatable(tween(tokens.durations.recordingPulseDuration), RepeatMode.Reverse), label = "pulse")
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(tokens.dimensions.actionButtonSize).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }.semantics { contentDescription = if (isRecording) "Zastavit demo nahrávání" else "Spustit demo nahrávání" },
        shape = CircleShape,
        containerColor = tokens.colors.accentBlue,
        contentColor = tokens.colors.backgroundPrimary,
    ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (isRecording) "■" else "+", style = MaterialTheme.typography.headlineLarge); AnimatedVisibility(isRecording) { Text("${seconds}s", style = MaterialTheme.typography.labelSmall) } } }
}

@Composable private fun EmptyState() { Text("Zatím tu nejsou žádné poznámky. Klepnutím na + vytvoříte demo záznam.", color = GlassNightTokens.colors.textSecondary, style = GlassNightTokens.noteSecondary) }

@Composable private fun NoteDetailDialog(note: DemoNote, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, confirmButton = { TextButton(onClick = onClose) { Text("Zavřít") } }, title = { Text("Detail poznámky") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(note.text); Text("Původní přepis", style = MaterialTheme.typography.labelLarge); Text(note.originalTranscript.ifBlank { "Přepis zatím není dostupný." }); Text("Vytvořeno: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.createdAt))}", color = GlassNightTokens.colors.textSecondary) } })
}

@Composable private fun EditNoteDialog(note: DemoNote, onCancel: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(note.id) { mutableStateOf(note.text) }
    AlertDialog(onDismissRequest = onCancel, confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Uložit") } }, dismissButton = { TextButton(onClick = onCancel) { Text("Zrušit") } }, title = { Text("Upravit poznámku") }, text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), label = { Text("Text poznámky") }) })
}

@Composable fun DetailScreen(nav: NavController, id: String) {
    val repository = repo(); val scope = rememberCoroutineScope(); var note by remember { mutableStateOf<cz.notecat.data.Note?>(null) }
    LaunchedEffect(id) { note = repository.get(id) }
    val n = note ?: return Text("Načítám…")
    var text by remember(n.id, n.noteText) { mutableStateOf(n.noteText) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(n.processingStatus.userText(), style = MaterialTheme.typography.titleMedium); n.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }; OutlinedTextField(text, { text = it }, label = { Text("Poznámka") }, modifier = Modifier.fillMaxWidth().height(220.dp)); Text("Přepis: ${n.transcriptOriginal ?: "čeká se"}"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ scope.launch { repository.update(n.copy(noteText = text)); nav.popBackStack() } }) { Text("Uložit") }; Button({ repository.enqueue(n.id) }, enabled = n.processingStatus == ProcessingStatus.FAILED) { Text("Retry") }; Button({ scope.launch { repository.delete(n); nav.navigate("list") } }) { Text("Smazat") } } }
}
