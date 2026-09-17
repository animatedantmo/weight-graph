package org.animatedantmo.weightgraph.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.animatedantmo.weightgraph.R
import org.animatedantmo.weightgraph.data.WeightEntry
import org.animatedantmo.weightgraph.data.date
import org.animatedantmo.weightgraph.data.formatLb
import org.animatedantmo.weightgraph.data.buildWeightCsv
import org.animatedantmo.weightgraph.data.formatUsDate
import org.animatedantmo.weightgraph.ui.theme.ThemeDialog
import org.animatedantmo.weightgraph.ui.theme.ThemeMode
import java.time.LocalDate
import kotlin.math.roundToInt

private const val MIN_LOADING_MS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    viewModel: WeightViewModel = viewModel(),
) {
    val loadedEntries by viewModel.entries.collectAsStateWithLifecycle()
    // The database usually answers within a frame or two, which flashed the spinner too briefly
    // to read as anything. Holding it for a minimum time makes it a deliberate beat instead.
    // Saveable, so recreating the activity does not replay it.
    var minimumLoadingElapsed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MIN_LOADING_MS)
        minimumLoadingElapsed = true
    }
    val isLoading = loadedEntries == null || !minimumLoadingElapsed
    val entries = loadedEntries.orEmpty()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showEntrySheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val chartPreferences = remember { ChartPreferences(context) }
    var chartRange by remember { mutableStateOf(chartPreferences.defaultRange()) }
    var showDefaultRange by remember { mutableStateOf(false) }
    var graphColorArgb by remember { mutableStateOf(chartPreferences.graphColorArgb()) }
    val graphColor = graphColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    var showGraphColor by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf<LocalDate?>(null) }
    var customEnd by remember { mutableStateOf<LocalDate?>(null) }
    var showRangePicker by remember { mutableStateOf(false) }
    var showExportChoice by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
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
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
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
                    lineColor = graphColor,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        entries.size.toString() + " entries",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Latest " + formatLb(newestFirst.first().weightLb) + " lb on " +
                            formatUsDate(newestFirst.first().date),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                showEntrySheet = true
            },
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
            onBackup = { showBackup = true },
            onDefaultView = { showDefaultRange = true },
            graphColor = graphColor,
            onGraphColor = { showGraphColor = true },
            onTheme = { showTheme = true },
            onDeleteAll = { showDeleteAllConfirm = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
        }
    }

    if (showBackup) {
        BackupDialog(onDismiss = { showBackup = false })
    }

    if (showTheme) {
        ThemeDialog(
            current = themeMode,
            onDismiss = { showTheme = false },
            onSave = { mode ->
                onThemeModeChange(mode)
                showTheme = false
            },
        )
    }

    if (showGraphColor) {
        GraphColorDialog(
            currentArgb = graphColorArgb,
            onDismiss = { showGraphColor = false },
            onSave = { picked ->
                chartPreferences.setGraphColorArgb(picked)
                graphColorArgb = picked
                showGraphColor = false
            },
        )
    }

    if (showDefaultRange) {
        DefaultRangeDialog(
            current = chartPreferences.defaultRange(),
            onDismiss = { showDefaultRange = false },
            onSelect = { range ->
                chartPreferences.setDefaultRange(range)
                // Switch to it now too, so the choice is visible straight away.
                chartRange = range
                customStart = null
                customEnd = null
                showDefaultRange = false
            },
        )
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
    val haptics = LocalHapticFeedback.current

    // detectTapGestures is launched once per row and keeps the lambdas it was started with, so
    // the tap handler cannot close over `revealed` directly: it would keep the value the row was
    // first composed with, which is false, and tapping would never put the button away. Reading
    // it through a holder gives the running gesture the current value.
    val revealedNow by rememberUpdatedState(revealed)

    // Only the weight is pushed away, and only far enough to clear the button; the date stays
    // put, so both values are still readable while deciding whether to delete.
    val weightShift by animateFloatAsState(
        targetValue = if (revealed) -revealPx else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "weightShift",
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .padding(vertical = 18.dp)
                .pointerInput(entry.id) {
                    // A long press rather than a swipe: a horizontal drag sits on top of the
                    // list's own scrolling and was too easy to trigger by accident.
                    detectTapGestures(
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReveal()
                        },
                        onTap = { if (revealedNow) onHide() },
                    )
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatUsDate(entry.date),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = formatLb(entry.weightLb) + " lb",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.offset { IntOffset(weightShift.roundToInt(), 0) },
            )
        }

        // Drawn over the row rather than behind it, since the row no longer slides away to
        // uncover anything. It springs up from small so the button arrives with some weight
        // instead of blinking on, and leaves quickly once the decision is made.
        AnimatedVisibility(
            visible = revealed,
            enter = scaleIn(
                initialScale = 0.55f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ) + fadeIn(animationSpec = tween(120)),
            exit = scaleOut(targetScale = 0.55f, animationSpec = tween(140)) +
                fadeOut(animationSpec = tween(140)),
            modifier = Modifier.align(Alignment.CenterEnd),
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
    // Set by a confirm that could not go through, so the field only objects once the user has
    // actually tried to delete rather than while they are still typing the word.
    var confirmRejected by remember { mutableStateOf(false) }
    val matches = isDeleteConfirmation(typed)
    val haptics = LocalHapticFeedback.current

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
                    isError = confirmRejected && !matches,
                    supportingText = if (confirmRejected && !matches) {
                        {
                            Text(
                                if (typed.isBlank()) {
                                    "Type delete to confirm"
                                } else {
                                    "That does not say delete"
                                }
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            // Always enabled so a premature tap can say why, but the typed word is still the
            // only thing that lets the delete through: onConfirm runs on a match and nowhere
            // else, so tapping through remains impossible.
            TextButton(
                onClick = {
                    if (matches) {
                        onConfirm()
                    } else {
                        confirmRejected = true
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    }
                },
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
