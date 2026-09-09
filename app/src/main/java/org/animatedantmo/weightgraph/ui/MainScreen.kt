package org.animatedantmo.weightgraph.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.animatedantmo.weightgraph.R
import org.animatedantmo.weightgraph.data.WeightEntry
import org.animatedantmo.weightgraph.data.date
import org.animatedantmo.weightgraph.data.formatLb
import org.animatedantmo.weightgraph.data.buildWeightCsv
import org.animatedantmo.weightgraph.data.formatUsDate
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: WeightViewModel = viewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEntrySheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var chartRange by remember { mutableStateOf(ChartRange.ALL) }
    var customStart by remember { mutableStateOf<LocalDate?>(null) }
    var customEnd by remember { mutableStateOf<LocalDate?>(null) }
    var showRangePicker by remember { mutableStateOf(false) }
    var showExportChoice by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WeightEntry?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var scrollToNewestPending by remember { mutableStateOf(false) }

    // asReversed is a view rather than a copy, which matters at four thousand entries.
    val newestFirst = remember(entries) { entries.asReversed() }

    // Only the chart is windowed; the list below still shows everything.
    val chartEntries = remember(entries, chartRange, customStart, customEnd) {
        applyRange(entries, chartRange, customStart, customEnd)
    }

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

    // Save to device: the system picks the location, the app writes into the URI it returns.
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(buildWeightCsv(entries).toByteArray())
            }
        }
    }

    // No top app bar: the screen is the chart and the list, and a title bar just costs
    // vertical space. Scaffold still supplies the status bar inset through padding.
    Scaffold(
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                WeightChart(
                    allEntries = entries,
                    visibleEntries = chartEntries,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
                ChartRangeSelector(
                    selected = chartRange,
                    onSelect = { picked ->
                        chartRange = picked
                        if (picked == ChartRange.CUSTOM) {
                            // Tapping Custom always reopens the picker, so the dates can be
                            // changed without first switching to another range and back.
                            showRangePicker = true
                        } else {
                            // Moving to a preset abandons the custom window, so Custom opens
                            // empty next time rather than restoring a range you left behind.
                            customStart = null
                            customEnd = null
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    entries.size.toString() + " entries  ·  latest " +
                        formatLb(newestFirst.first().weightLb) + " lb on " +
                        formatUsDate(newestFirst.first().date),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider()
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 88.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(newestFirst, key = { it.id }) { entry ->
                        EntryRow(
                            // Fades a row in or out and slides its neighbours into place, so an
                            // add or delete reads as a change rather than a jump cut.
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                placementSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                ),
                                fadeOutSpec = tween(180),
                            ),
                            entry = entry,
                            revealed = revealedId == entry.id,
                            onReveal = { revealedId = entry.id },
                            onHide = { if (revealedId == entry.id) revealedId = null },
                            onDelete = {
                                revealedId = null
                                pendingDelete = entry
                            },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }

        // Add Weight is the primary action and stays one tap, bottom left. The data actions
        // group under a single button on the right.
        FloatingActionButton(
            onClick = { showEntrySheet = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "Add weight",
            )
        }

        ActionMenu(
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = it },
            hasEntries = entries.isNotEmpty(),
            onImport = {
                // Many providers label CSV as text/comma-separated-values or octet-stream, so
                // accept those too rather than hiding the file the user is looking for.
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
            onExport = { showExportChoice = true },
            onDeleteAll = { showDeleteAllConfirm = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
        }
    }

    if (showEntrySheet) {
        EntryDialog(
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

    if (showRangePicker) {
        CustomRangeDialog(
            initialStart = customStart,
            initialEnd = customEnd,
            onDismiss = {
                showRangePicker = false
                // Backing out without a range would leave an empty Custom view selected.
                if (customStart == null || customEnd == null) chartRange = ChartRange.ALL
            },
            onConfirm = { start, end ->
                customStart = start
                customEnd = end
                showRangePicker = false
            },
        )
    }

    if (showExportChoice) {
        AlertDialog(
            onDismissRequest = { showExportChoice = false },
            title = { Text("Export " + entries.size + " entries") },
            text = { Text("Date and weight, formatted as they appear here.") },
            confirmButton = {
                TextButton(onClick = {
                    showExportChoice = false
                    shareCsv(context, buildWeightCsv(entries))
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportChoice = false
                    saver.launch(exportFileName())
                }) { Text("Save to device") }
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this entry?") },
            text = {
                Text(formatUsDate(entry.date) + "  ·  " + formatLb(entry.weightLb) + " lb")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.delete(entry)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteAllConfirm) {
        DeleteAllDialog(
            entryCount = entries.size,
            onDismiss = { showDeleteAllConfirm = false },
            onConfirm = {
                showDeleteAllConfirm = false
                viewModel.deleteAll()
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
    modifier: Modifier = Modifier,
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

    Box(modifier = modifier.fillMaxWidth()) {
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

/**
 * Confirmation for wiping the database. Requires the word "delete" to be typed, so the action
 * cannot be completed by tapping through: there is no undo behind it.
 */
@Composable
private fun DeleteAllDialog(
    entryCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val matches = isDeleteConfirmation(typed)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all " + entryCount + " entries?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "This permanently deletes every weight in the app, including the "
                        + "history imported from your spreadsheet. It cannot be undone. "
                        + "If you want a copy, cancel and use Export first."
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type delete to confirm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = matches,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete everything")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// Any capitalisation of "delete", ignoring surrounding whitespace, since keyboards like to
// capitalise the first letter and a trailing space is easy to leave behind.
fun isDeleteConfirmation(text: String): Boolean =
    text.trim().equals("delete", ignoreCase = true)
