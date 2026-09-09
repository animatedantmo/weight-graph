package org.animatedantmo.weightgraph.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import kotlinx.coroutines.delay
import org.animatedantmo.weightgraph.data.parseLb
import org.animatedantmo.weightgraph.data.formatUsDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val MIN_LB = 20.0
private const val MAX_LB = 1000.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(
    initialDate: LocalDate,
    initialWeightLb: Double?,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var date by remember { mutableStateOf(initialDate) }
    var text by remember {
        mutableStateOf(initialWeightLb?.let { formatForField(it) } ?: "")
    }
    var showDatePicker by remember { mutableStateOf(false) }

    // Open straight into the weight field with the keyboard up, so adding a reading is one tap
    // and then typing. The short delay lets the sheet finish animating in; requesting focus
    // mid-animation is dropped on the floor.
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val parsed = parseLb(text)
    val error = when {
        text.isBlank() -> null
        parsed == null -> "Enter a number, like 178.8"
        parsed < MIN_LB || parsed > MAX_LB -> "That is outside " + MIN_LB.toInt() + "-" + MAX_LB.toInt() + " lb"
        else -> null
    }
    val canSave = parsed != null && error == null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add a weight", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)

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
                Text(formatUsDate(date))
                TextButton(onClick = { showDatePicker = true }) { Text("Change date") }
            }

            Button(
                onClick = { parsed?.let { onSave(date, it) } },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// Drops a trailing ".0" so editing an existing entry does not show "178.0" for a whole number.
private fun formatForField(lb: Double): String =
    if (lb == lb.toLong().toDouble()) lb.toLong().toString() else lb.toString()
