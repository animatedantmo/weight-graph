package org.animatedantmo.weightgraph.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.animatedantmo.weightgraph.R
import org.animatedantmo.weightgraph.data.WeightEntry
import org.animatedantmo.weightgraph.data.parseDate
import org.animatedantmo.weightgraph.data.formatUsDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// days = null means the window is not a fixed length: ALL takes everything, CUSTOM uses the
// explicit start and end dates the user picked.
enum class ChartRange(val label: String, val longLabel: String, val days: Long?) {
    WEEK("1W", "1 week", 7),
    TWO_WEEKS("2W", "2 weeks", 14),
    MONTH("1M", "1 month", 30),
    YEAR("1Y", "1 year", 365),
    ALL("All", "All entries", null),
    CUSTOM("Dates", "Chosen dates", null),
}

// Dates is not offered as a default: it needs a start and end picked, which a fresh launch has not.
val defaultableRanges: List<ChartRange> = ChartRange.entries.filter { it != ChartRange.CUSTOM }

// The range the chart opens on, remembered across launches.
class ChartPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("chart", Context.MODE_PRIVATE)

    fun defaultRange(): ChartRange =
        prefs.getString(KEY_DEFAULT_RANGE, null)
            ?.let { name -> defaultableRanges.firstOrNull { it.name == name } }
            ?: FALLBACK_RANGE

    fun setDefaultRange(range: ChartRange) {
        prefs.edit().putString(KEY_DEFAULT_RANGE, range.name).apply()
    }

    private companion object {
        const val KEY_DEFAULT_RANGE = "default_range"

        // Two weeks: recent days are what a daily weigh-in is usually checked for, with enough of
        // them to show a trend past day-to-day noise.
        val FALLBACK_RANGE = ChartRange.TWO_WEEKS
    }
}

@Composable
fun DefaultRangeDialog(
    current: ChartRange,
    onDismiss: () -> Unit,
    onSelect: (ChartRange) -> Unit,
) {
    // Tapping an option only marks it; nothing is saved until Save.
    var picked by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Graph View", style = MaterialTheme.typography.labelLarge) },
        text = {
            Column {
                Text(
                    "The range the graph shows when the app opens.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                defaultableRanges.forEach { range ->
                    // The whole row is the tap target, not just the small radio circle.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = range == picked,
                                onClick = { picked = range },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = range == picked, onClick = null)
                        Text(
                            range.longLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(picked) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Windows [entries] to the selected range.
 *
 * Fixed-length windows are measured back from the most recent reading rather than from today, so
 * the chart still shows something after a gap in logging. Entries are already sorted by date, so
 * filtering preserves their order.
 */
fun applyRange(
    entries: List<WeightEntry>,
    range: ChartRange,
    customStart: LocalDate? = null,
    customEnd: LocalDate? = null,
): List<WeightEntry> {
    if (entries.isEmpty()) return entries

    if (range == ChartRange.CUSTOM) {
        // Until both ends are chosen there is nothing to narrow to.
        if (customStart == null || customEnd == null) return entries
        val from = minOf(customStart, customEnd).toEpochDay()
        val to = maxOf(customStart, customEnd).toEpochDay()
        return entries.filter { it.epochDay in from..to }
    }

    val days = range.days ?: return entries
    val cutoff = entries.last().epochDay - days
    return entries.filter { it.epochDay >= cutoff }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartRangeSelector(
    selected: ChartRange,
    onSelect: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        ChartRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ChartRange.entries.size,
                ),
                // No check icon: with six segments the tick crowds out the labels, and the
                // selected segment is already distinguished by its container colour.
                icon = {},
            ) {
                Text(text = range.label, maxLines = 1)
            }
        }
    }
}

@Composable
fun CustomRangeDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    // Laid out by hand rather than with AlertDialog so the mode toggle can sit at the far left of
    // the button row, the way the system time picker does. AlertDialog only offers trailing
    // button slots.
    //
    // State holds digits only; the slashes are painted on by DateMaskTransformation, so there is
    // never a separator in the text to delete or type around. The fields open on the range that
    // is currently applied, with Reset to clear them.
    var startDigits by remember { mutableStateOf(initialStart.toDigits()) }
    var endDigits by remember { mutableStateOf(initialEnd.toDigits()) }
    var showCalendar by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }
    // Set by an Apply that could not go through, so a blank or half-typed field is only
    // called out once the user has actually tried to apply the range.
    var applyRejected by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current

    val start = startDigits.toDateOrNull()
    val end = endDigits.toDateOrNull()
    // A full date that is not a real one is wrong the moment it is complete; anything still
    // blank or half-typed is only wrong once Apply has been refused.
    val startError = start == null && (startDigits.length == DATE_DIGITS || applyRejected)
    val endError = end == null && (endDigits.length == DATE_DIGITS || applyRejected)
    val rangeError = when {
        !startError && !endError -> null
        startDigits.length == DATE_DIGITS && endDigits.length == DATE_DIGITS ->
            "That is not a real date"
        else -> "Enter both dates as M/D/YYYY"
    }

    Dialog(
        onDismissRequest = onDismiss,
        // The calendar grid needs more width than the default dialog allows.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            // Padded vertically only: the calendar grid needs the full dialog width or its last
            // column is clipped, so the horizontal inset is applied per child instead.
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                Text(
                    "Select Date Range",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.padding(top = 12.dp))

                if (showCalendar) {
                    CalendarRange(
                        start = start,
                        end = end,
                        editingEnd = editingEnd,
                        onEditingEndChange = { editingEnd = it },
                        onDatePicked = { picked ->
                            if (editingEnd) {
                                endDigits = picked.toDigits()
                            } else {
                                startDigits = picked.toDigits()
                            }
                        },
                        // Capped so the button row below stays on screen; the grid scrolls.
                        modifier = Modifier.heightIn(max = 340.dp),
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) {
                        DateField(
                            digits = startDigits,
                            onDigitsChange = { startDigits = it },
                            label = "Start",
                            isError = startError,
                            modifier = Modifier.weight(1f),
                        )
                        DateField(
                            digits = endDigits,
                            onDigitsChange = { endDigits = it },
                            label = "End",
                            isError = endError,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rangeError != null) {
                        Text(
                            rangeError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showCalendar = !showCalendar }) {
                        Icon(
                            painter = painterResource(
                                if (showCalendar) R.drawable.ic_keyboard else R.drawable.ic_calendar
                            ),
                            contentDescription = if (showCalendar) {
                                "Switch to typing dates"
                            } else {
                                "Switch to the calendar"
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            startDigits = ""
                            endDigits = ""
                        },
                        enabled = startDigits.isNotEmpty() || endDigits.isNotEmpty(),
                    ) {
                        Text("Reset")
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    // Always enabled, for the same reason as Save in the entry dialog: a
                    // refused tap that buzzes and names the problem beats one that does nothing.
                    TextButton(
                        onClick = {
                            if (start != null && end != null) {
                                onConfirm(start, end)
                            } else {
                                applyRejected = true
                                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                                // Nothing explains itself on the calendar, so fall back to the
                                // fields where the errors are actually visible.
                                showCalendar = false
                            }
                        },
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

/**
 * Calendar half of the dialog.
 *
 * Uses a single DatePicker with a Start/End switch rather than DateRangePicker, because only the
 * single picker offers the year selector - the tappable month header that opens a grid of years.
 * DateRangePicker is a continuous scroll of months, which means reaching 2014 from here would be
 * a very long drag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarRange(
    start: LocalDate?,
    end: LocalDate?,
    editingEnd: Boolean,
    onEditingEndChange: (Boolean) -> Unit,
    onDatePicked: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            FilterChip(
                selected = !editingEnd,
                onClick = { onEditingEndChange(false) },
                label = { Text("Start: " + (start?.let(::formatUsDate) ?: "not set")) },
            )
            FilterChip(
                selected = editingEnd,
                onClick = { onEditingEndChange(true) },
                label = { Text("End: " + (end?.let(::formatUsDate) ?: "not set")) },
            )
        }
        // Rebuilt when the switch flips so the grid opens on the date being edited.
        key(editingEnd) {
            SingleDatePicker(
                initial = if (editingEnd) end else start,
                onPicked = onDatePicked,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePicker(
    initial: LocalDate?,
    onPicked: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The stock range is 1900-2100, which makes the year grid a very long scroll. Weight history
    // does not plausibly start before 2000, and one year ahead covers entering a future date.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toUtcMillis(),
        yearRange = 2000..(LocalDate.now().year + 1),
    )
    LaunchedEffect(state.selectedDateMillis) {
        state.selectedDateMillis?.let { onPicked(it.toLocalDateUtc()) }
    }
    // title and headline are dropped; the dialog supplies its own, and the chips above already
    // show what is selected. The month header with its year selector is separate and stays.
    DatePicker(
        state = state,
        modifier = modifier,
        title = null,
        headline = null,
        showModeToggle = false,
    )
}

@Composable
private fun DateField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = digits,
        // Anything that is not a digit is discarded, so pasted text still lands correctly.
        onValueChange = { onDigitsChange(it.filter(Char::isDigit).take(DATE_DIGITS)) },
        label = { Text(label) },
        placeholder = { Text("MM/DD/YYYY") },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = DateMaskTransformation,
        modifier = modifier,
    )
}

private const val DATE_DIGITS = 8

private fun LocalDate?.toDigits(): String =
    this?.let { String.format("%02d%02d%04d", it.monthValue, it.dayOfMonth, it.year) }.orEmpty()

private fun String.toDateOrNull(): LocalDate? {
    if (length != DATE_DIGITS) return null
    return parseDate(substring(0, 2) + "/" + substring(2, 4) + "/" + substring(4))
}

// The calendar works in UTC millis while the app works in LocalDate. Converting through UTC on
// both sides keeps a date from drifting a day either way.
private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Displays eight raw digits as MM/DD/YYYY. The separators exist only in what is drawn, so the
 * cursor never has to step over them and backspace always removes a digit.
 */
private object DateMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(DATE_DIGITS)
        val masked = buildString {
            digits.forEachIndexed { index, c ->
                append(c)
                if (index == 1 || index == 3) append('/')
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 1 -> offset
                offset <= 3 -> offset + 1
                else -> offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }
        }
        return TransformedText(AnnotatedString(masked), mapping)
    }
}
