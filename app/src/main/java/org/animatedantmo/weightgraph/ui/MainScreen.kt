package org.animatedantmo.weightgraph.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.animatedantmo.weightgraph.data.WeightEntry
import org.animatedantmo.weightgraph.data.date
import org.animatedantmo.weightgraph.data.formatLb
import org.animatedantmo.weightgraph.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val US_DATE = DateTimeFormatter.ofPattern("M/d/yyyy")

fun formatUsDate(date: LocalDate): String = date.format(US_DATE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: WeightViewModel = viewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEntrySheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var scrollToNewestPending by remember { mutableStateOf(false) }

    // asReversed is a view rather than a copy, which matters at four thousand entries.
    val newestFirst = remember(entries) { entries.asReversed() }

    // Saving is asynchronous, so the scroll waits for the new row to actually arrive in the list
    // rather than firing against the old contents.
    LaunchedEffect(entries) {
        if (scrollToNewestPending && entries.isNotEmpty()) {
            listState.animateScrollToItem(0)
            scrollToNewestPending = false
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.previewCsv(uri, displayNameOf(context, uri))
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Weight Graph") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { showEntrySheet = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Add weight") }
                OutlinedButton(
                    // Many providers label CSV as text/comma-separated-values or octet-stream,
                    // so accept those too rather than hiding the file the user is looking for.
                    onClick = {
                        picker.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/vnd.ms-excel",
                                "application/octet-stream",
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Import CSV") }
            }

            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No weights yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add one by hand, or import a CSV exported from your spreadsheet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Text(
                    entries.size.toString() + " entries  ·  latest " +
                        formatLb(newestFirst.first().weightLb) + " lb on " +
                        formatUsDate(newestFirst.first().date),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(newestFirst, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            revealed = revealedId == entry.id,
                            onReveal = { revealedId = entry.id },
                            onHide = { if (revealedId == entry.id) revealedId = null },
                            onDelete = {
                                revealedId = null
                                viewModel.delete(entry)
                            },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }

    if (showEntrySheet) {
        EntrySheet(
            initialDate = LocalDate.now(),
            initialWeightLb = null,
            onDismiss = { showEntrySheet = false },
            onSave = { date, lb ->
                viewModel.record(date, lb)
                scrollToNewestPending = true
                showEntrySheet = false
            },
        )
    }

    ImportDialogs(state = importState, viewModel = viewModel)
}

// How far the row's text slides aside to make room for the delete button.
private val REVEAL_WIDTH = 68.dp

@Composable
private fun EntryRow(
    entry: WeightEntry,
    revealed: Boolean,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    val revealPx = with(LocalDensity.current) { REVEAL_WIDTH.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Closes this row when a different one is opened, so only one delete is ever exposed.
    LaunchedEffect(revealed) {
        if (!revealed && offsetX.value != 0f) offsetX.animateTo(0f)
    }

    // Only the text on the side the button appears gets pushed away; the other stays put, so the
    // date and the weight are both still readable while deciding whether to delete.
    val swipingLeft = offsetX.value < 0f
    val dateShift = if (swipingLeft) 0f else offsetX.value
    val weightShift = if (swipingLeft) offsetX.value else 0f

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .padding(vertical = 18.dp)
                .pointerInput(entry.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(offsetX.value) > revealPx / 2f) {
                                    offsetX.animateTo(if (offsetX.value < 0f) -revealPx else revealPx)
                                    onReveal()
                                } else {
                                    offsetX.animateTo(0f)
                                    onHide()
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f)
                                onHide()
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + drag).coerceIn(-revealPx, revealPx))
                        }
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatUsDate(entry.date),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.offset { IntOffset(dateShift.roundToInt(), 0) },
            )
            Text(
                text = formatLb(entry.weightLb) + " lb",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.offset { IntOffset(weightShift.roundToInt(), 0) },
            )
        }

        // Drawn over the row rather than behind it, since the row no longer slides away to
        // uncover anything.
        if (offsetX.value != 0f) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = if (swipingLeft) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                FilledTonalIconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "Delete this entry",
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportDialogs(state: ImportState, viewModel: WeightViewModel) {
    when (state) {
        is ImportState.Idle -> Unit

        is ImportState.Reading -> AlertDialog(
            onDismissRequest = { },
            title = { Text("Reading file") },
            text = { CircularProgressIndicator() },
            confirmButton = { },
        )

        is ImportState.Preview -> {
            val blank = state.result.skipped.count { it.reason.startsWith("no weight recorded") }
            val bad = state.result.skippedCount - blank
            AlertDialog(
                onDismissRequest = { viewModel.dismissImport() },
                title = { Text("Import " + state.result.importedCount + " entries?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.fileName)
                        val first = state.result.entries.first()
                        val last = state.result.entries.last()
                        Text(formatUsDate(first.date) + " to " + formatUsDate(last.date))
                        if (blank > 0) {
                            Text(blank.toString() + " days had no weight recorded and are skipped.")
                        }
                        if (bad > 0) {
                            Text(bad.toString() + " rows could not be read and are skipped.")
                        }
                        Text("Existing entries on the same dates will be overwritten.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmImport() }) { Text("Import") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissImport() }) { Text("Cancel") }
                },
            )
        }

        is ImportState.Done -> AlertDialog(
            onDismissRequest = { viewModel.dismissImport() },
            title = { Text("Imported " + state.imported + " entries") },
            text = {
                Text(
                    if (state.skipped > 0) {
                        state.skipped.toString() + " rows were skipped."
                    } else {
                        "Every row was read successfully."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImport() }) { Text("Done") }
            },
        )

        is ImportState.Failed -> AlertDialog(
            onDismissRequest = { viewModel.dismissImport() },
            title = { Text("Import failed") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImport() }) { Text("OK") }
            },
        )
    }
}

private fun displayNameOf(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
    }
    return uri.lastPathSegment ?: "selected file"
}
