package org.animatedantmo.weightgraph.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.animatedantmo.weightgraph.data.CsvImportResult
import org.animatedantmo.weightgraph.data.WeightDatabase
import org.animatedantmo.weightgraph.data.WeightEntry
import org.animatedantmo.weightgraph.data.WeightRepository
import org.animatedantmo.weightgraph.data.parseWeightCsv
import java.time.LocalDate

// What the import sheet is currently showing. Nothing reaches the database until the user
// confirms a Preview, so opening the wrong file costs nothing.
sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class Preview(val fileName: String, val result: CsvImportResult) : ImportState
    data class Done(val imported: Int, val skipped: Int) : ImportState
    data class Failed(val message: String) : ImportState
}

class WeightViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = WeightRepository(WeightDatabase.get(app).weightDao())

    val entries: StateFlow<List<WeightEntry>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun record(date: LocalDate, weightLb: Double, note: String? = null) {
        viewModelScope.launch { repository.record(date, weightLb, note) }
    }

    fun delete(entry: WeightEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }

    // Wipes every entry. Irreversible: there is no soft delete or trash behind this.
    fun deleteAll() {
        viewModelScope.launch { repository.clear() }
    }

    // Reads and parses the file, then stops and waits for confirmation.
    fun previewCsv(uri: Uri, fileName: String) {
        _importState.value = ImportState.Reading
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                try {
                    val text = getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: return@withContext ImportState.Failed("Could not open that file.")
                    val result = parseWeightCsv(text)
                    if (result.entries.isEmpty() && result.skipped.isEmpty()) {
                        ImportState.Failed("That file is empty.")
                    } else if (result.entries.isEmpty()) {
                        ImportState.Failed(
                            "No readable weights found. Check the file has a date column and a " +
                                "weight column."
                        )
                    } else {
                        ImportState.Preview(fileName, result)
                    }
                } catch (e: Exception) {
                    ImportState.Failed(e.message ?: "Could not read that file.")
                }
            }
            _importState.value = state
        }
    }

    // Writes the previewed entries. Existing days are overwritten, not duplicated.
    fun confirmImport() {
        val preview = _importState.value as? ImportState.Preview ?: return
        viewModelScope.launch {
            repository.importAll(preview.result.entries)
            _importState.value = ImportState.Done(
                imported = preview.result.importedCount,
                skipped = preview.result.skippedCount,
            )
        }
    }

    fun dismissImport() {
        _importState.value = ImportState.Idle
    }
}
