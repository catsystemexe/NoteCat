package cz.notecat.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.notecat.app.NoteCatApp
import cz.notecat.app.data.Note
import cz.notecat.app.work.ProcessNoteWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<NoteCatApp>()
    private val repository get() = app.repository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val notes: StateFlow<List<Note>> = _query
        .debounce(150)
        .flatMapLatest { q ->
            if (q.isBlank()) repository.observeAll() else repository.search(q.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun observeNote(id: String): Flow<Note?> = repository.observeById(id)

    fun updateText(id: String, text: String) {
        viewModelScope.launch { repository.updatePolishedText(id, text) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun retryProcessing(id: String) {
        viewModelScope.launch {
            repository.markPending(id)
            ProcessNoteWorker.retry(app, id)
        }
    }
}
