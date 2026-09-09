package org.animatedantmo.weightgraph.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.animatedantmo.weightgraph.data.formatUsDate
import org.animatedantmo.weightgraph.data.parseLb
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val MIN_LB = 20.0
private const val MAX_LB = 1000.0

/**
 * Modal rather than a bottom sheet: the keyboard resizes the window around a centred dialog,
 * where a sheet is shoved upward by it. That kept the sheet's entrance and the keyboard's
 * entrance as two separate movements no matter how the focus request was timed.
 */
@Composable
fun EntryDialog(
    initialDate: LocalDate,
    initialWeightLb: Double?,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double) -> Unit,
) {
    var date by remember { mutableStateOf(initialDate) }
    var text by remember {
        mutableStateOf(initialWeightLb?.let { formatForField(it) } ?: "")
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val parsed = parseLb(text)
    val error = when {
        text.isBlank() -> null
        parsed == null -> "Enter a number, like 178.8"
        parsed < MIN_LB || parsed > MAX_LB ->
            "That is outside " + MIN_LB.toInt() + "-" + MAX_LB.toInt() + " lb"
        else -> null
    }
    val canSave = parsed != null && error == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Weight", style = MaterialTheme.typography.labelLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Weight") },
                    suffix = { Text("lb") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatUsDate(date), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { showDatePicker = true }) { Text("Change date") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onSave(date, it) } },
                enabled = canSave,
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        EntryDatePickerDialog(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPicked = {
                date = it
                showDatePicker = false
            },
        )
    }
}

// Same compact shape as the chart's range picker: own title, no reserved header band.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 2000..(LocalDate.now().year + 1),
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                Text(
                    "Select Date",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                DatePicker(
                    state = state,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    modifier = Modifier.heightIn(max = 380.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            state.selectedDateMillis?.let {
                                onPicked(
                                    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                                )
                            }
                        },
                        enabled = state.selectedDateMillis != null,
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

// Drops a trailing ".0" so editing an existing entry does not show "178.0" for a whole number.
private fun formatForField(lb: Double): String =
    if (lb == lb.toLong().toDouble()) lb.toLong().toString() else lb.toString()
