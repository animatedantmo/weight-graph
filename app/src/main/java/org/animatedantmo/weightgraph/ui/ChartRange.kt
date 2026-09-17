package org.animatedantmo.weightgraph.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
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

// Two weeks: recent days are what a daily weigh-in is usually checked for, with enough of them to
// show a trend past day-to-day noise. Used until another default is chosen.
val FACTORY_DEFAULT_RANGE = ChartRange.TWO_WEEKS

// The long label, marked when it is the range the app ships with.
val ChartRange.settingLabel: String
    get() = if (this == FACTORY_DEFAULT_RANGE) "$longLabel (default)" else longLabel

// The range the chart opens on, remembered across launches.
class ChartPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("chart", Context.MODE_PRIVATE)

    fun defaultRange(): ChartRange =
        prefs.getString(KEY_DEFAULT_RANGE, null)
            ?.let { name -> defaultableRanges.firstOrNull { it.name == name } }
            ?: FACTORY_DEFAULT_RANGE

    fun setDefaultRange(range: ChartRange) {
        prefs.edit().putString(KEY_DEFAULT_RANGE, range.name).apply()
    }

    // Null means the theme's own colour, so the default keeps following the theme.
    fun graphColorArgb(): Int? =
        if (prefs.contains(KEY_GRAPH_COLOR_ARGB)) prefs.getInt(KEY_GRAPH_COLOR_ARGB, 0) else null

    fun setGraphColorArgb(argb: Int?) {
        prefs.edit().apply {
            if (argb == null) remove(KEY_GRAPH_COLOR_ARGB) else putInt(KEY_GRAPH_COLOR_ARGB, argb)
            // Left by the earlier preset-only version of this setting.
            remove(KEY_LEGACY_GRAPH_COLOR)
        }.apply()
    }

    private companion object {
        const val KEY_DEFAULT_RANGE = "default_range"
        const val KEY_GRAPH_COLOR_ARGB = "graph_color_argb"
        const val KEY_LEGACY_GRAPH_COLOR = "graph_color"
    }
}

// Quick picks above the wheel. A null colour is the theme's own, which is the default.
private data class ColorPreset(val label: String, val argb: Int?)

private val COLOR_PRESETS = listOf(
    ColorPreset("Default", null),
    ColorPreset("Green", 0xFF34C759.toInt()),
    ColorPreset("Teal", 0xFF30B0C7.toInt()),
    ColorPreset("Purple", 0xFFAF52DE.toInt()),
    ColorPreset("Pink", 0xFFFF2D92.toInt()),
    ColorPreset("Orange", 0xFFFF9500.toInt()),
    ColorPreset("Yellow", 0xFFFFCC00.toInt()),
    ColorPreset("Gray", 0xFF8E8E93.toInt()),
)

private const val SWATCHES_PER_ROW = 4

// The brightness slider stops short of black, which would vanish against the dark background. A
// typed hex code is taken exactly as entered.
private const val MIN_BRIGHTNESS = 0.25f

private val WHEEL_HUES = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

private data class Hsv(val hue: Float, val saturation: Float, val value: Float) {
    fun toColor(): Color = Color.hsv(hue, saturation, value)

    companion object {
        fun of(argb: Int): Hsv {
            val out = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, out)
            return Hsv(out[0], out[1], out[2])
        }
    }
}

private fun Int.toHex(): String = "%06X".format(this and 0xFFFFFF)

// How the Settings screen names a chosen graph colour: the preset's name when it is one, otherwise
// its hex code.
fun graphColorLabel(argb: Int): String =
    COLOR_PRESETS.firstOrNull { it.argb == argb }?.label ?: ("Custom #" + argb.toHex())

// A plain colour name for any colour, by hue. The default graph colour comes from the phone's
// wallpaper-based theme, so it has no fixed name and is described this way instead.
fun colorName(argb: Int): String {
    val hsv = Hsv.of(argb)
    return when {
        hsv.value < 0.2f -> "Black"
        hsv.saturation < 0.15f -> if (hsv.value > 0.85f) "White" else "Gray"
        hsv.hue < 15f -> "Red"
        hsv.hue < 45f -> "Orange"
        hsv.hue < 70f -> "Yellow"
        hsv.hue < 160f -> "Green"
        hsv.hue < 195f -> "Teal"
        hsv.hue < 255f -> "Blue"
        hsv.hue < 290f -> "Purple"
        hsv.hue < 345f -> "Pink"
        else -> "Red"
    }
}

// Same length in and out, so cursor positions map straight across.
private object UppercaseTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(AnnotatedString(text.text.uppercase()), OffsetMapping.Identity)
}

@Composable
fun GraphColorDialog(
    currentArgb: Int?,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
) {
    val themeArgb = MaterialTheme.colorScheme.primary.toArgb()

    // The colour that Save stores, null for the theme default. The wheel position and the hex text
    // are kept alongside it so that each control follows changes made with the others.
    var pickedArgb by remember { mutableStateOf(currentArgb) }
    var hsv by remember { mutableStateOf(Hsv.of(currentArgb ?: themeArgb)) }
    var hexText by remember { mutableStateOf((currentArgb ?: themeArgb).toHex()) }

    fun pickHsv(newHsv: Hsv) {
        hsv = newHsv
        val argb = newHsv.toColor().toArgb()
        pickedArgb = argb
        hexText = argb.toHex()
    }

    fun pickPreset(preset: ColorPreset) {
        val argb = preset.argb ?: themeArgb
        pickedArgb = preset.argb
        hsv = Hsv.of(argb)
        hexText = argb.toHex()
    }

    // Same pattern as the Add Weight dialog: nothing is flagged while typing, and a Save that
    // cannot go through buzzes and explains itself under the field.
    var saveRejected by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val hexComplete = hexText.length == 6
    val hexError = when {
        hexComplete || !saveRejected -> null
        hexText.isEmpty() -> "Enter a hex color"
        else -> "Enter 6 digits, such as 34C759"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Graph Color", style = MaterialTheme.typography.labelLarge) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "The color of the graph line and its dots.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                GraphColorPreview(color = Color(pickedArgb ?: themeArgb))

                COLOR_PRESETS.chunked(SWATCHES_PER_ROW).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        row.forEach { preset ->
                            // The default swatch is the theme's colour, named by what it looks like
                            // rather than as "Default", with the default marked underneath.
                            ColorSwatch(
                                label = if (preset.argb == null) colorName(themeArgb) else preset.label,
                                note = if (preset.argb == null) "(default)" else null,
                                color = Color(preset.argb ?: themeArgb),
                                selected = preset.argb == pickedArgb,
                                onClick = { pickPreset(preset) },
                            )
                        }
                    }
                }

                ColorWheel(
                    hsv = hsv,
                    onChange = ::pickHsv,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(200.dp),
                )

                Column {
                    Text("Brightness", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = hsv.value.coerceIn(MIN_BRIGHTNESS, 1f),
                        onValueChange = { pickHsv(hsv.copy(value = it)) },
                        valueRange = MIN_BRIGHTNESS..1f,
                    )
                }

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        // Letters stay in the case they were typed and are only shown uppercase.
                        // Rewriting them here fights the keyboard's composing text and drops
                        // characters during fast typing.
                        val cleaned = input.removePrefix("#")
                            .filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
                            .take(6)
                        hexText = cleaned
                        if (cleaned.length == 6) {
                            val argb = cleaned.toInt(16) or 0xFF000000.toInt()
                            pickedArgb = argb
                            hsv = Hsv.of(argb)
                        }
                    },
                    label = { Text("Hex") },
                    prefix = { Text("#") },
                    visualTransformation = UppercaseTransformation,
                    singleLine = true,
                    isError = hexError != null,
                    supportingText = hexError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            // Always enabled, like Add Weight's Save. A half-typed hex code is refused rather than
            // saving whichever colour came before it.
            TextButton(
                onClick = {
                    if (hexComplete) {
                        onSave(pickedArgb)
                    } else {
                        saveRejected = true
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// A few points of line and dots on the chart's own background, to judge the colour in context.
@Composable
private fun GraphColorPreview(color: Color) {
    val background = MaterialTheme.colorScheme.background
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background),
    ) {
        val points = listOf(0.25f, 0.7f, 0.45f, 0.6f, 0.3f).mapIndexed { index, level ->
            Offset(size.width * (0.1f + index * 0.2f), size.height * level)
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
        points.forEach { drawCircle(color, radius = 4.dp.toPx(), center = it) }
    }
}

@Composable
private fun ColorSwatch(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    note: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(2.dp),
    ) {
        // A ring around the chosen swatch, with a gap so it reads against any swatch colour.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .border(
                    width = 3.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape,
                ),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Hue runs around the wheel and saturation from white at the centre to full colour at the rim.
 * Brightness is not part of the wheel's position; it is set with the slider and darkens the wheel
 * to match.
 */
@Composable
private fun ColorWheel(hsv: Hsv, onChange: (Hsv) -> Unit, modifier: Modifier = Modifier) {
    // The gesture handler is installed once, so it reads the latest values through these.
    val latestHsv by rememberUpdatedState(hsv)
    val latestOnChange by rememberUpdatedState(onChange)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            fun pick(position: Offset) {
                val radius = size.width / 2f
                val dx = position.x - radius
                val dy = position.y - radius
                val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                val saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
                latestOnChange(latestHsv.copy(hue = hue, saturation = saturation))
            }
            // Consuming every move keeps the dialog's scrolling from taking over the drag.
            awaitEachGesture {
                val down = awaitFirstDown()
                pick(down.position)
                down.consume()
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    pick(change.position)
                    change.consume()
                }
            }
        },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(WHEEL_HUES), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius)
        drawCircle(Color.Black.copy(alpha = 1f - hsv.value), radius)

        val angle = Math.toRadians(hsv.hue.toDouble())
        val thumb = center + Offset(
            (cos(angle) * hsv.saturation * radius).toFloat(),
            (sin(angle) * hsv.saturation * radius).toFloat(),
        )
        drawCircle(Color.White, radius = 13.dp.toPx(), center = thumb)
        drawCircle(hsv.toColor(), radius = 10.dp.toPx(), center = thumb)
        drawCircle(
            Color.Black.copy(alpha = 0.45f),
            radius = 13.dp.toPx(),
            center = thumb,
            style = Stroke(width = 1.dp.toPx()),
        )
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
                            range.settingLabel,
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
