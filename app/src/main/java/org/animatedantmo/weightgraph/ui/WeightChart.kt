package org.animatedantmo.weightgraph.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.animatedantmo.weightgraph.data.WeightEntry
import org.animatedantmo.weightgraph.data.date
import org.animatedantmo.weightgraph.data.formatLb
import org.animatedantmo.weightgraph.data.formatUsDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// Room for the axis labels, outside the plotting area.
private val Y_GUTTER = 44.dp
private val X_GUTTER = 22.dp

// Few enough labels that they never collide, even on a narrow screen.
private const val TARGET_Y_TICKS = 4
private const val TARGET_X_TICKS = 3

// Long enough to read as a zoom, short enough not to feel sluggish when tapping through.
private const val RANGE_ANIMATION_MS = 450

// How long the line takes to reach a newly added reading, or to pull back from a deleted one.
private const val LINE_ANIMATION_MS = 1000

private val LABEL_SIZE = 11.sp
private val READOUT_SIZE = 12.sp

// Deliberately not the line colour: the marker has to stand out against the line it sits on.
private val MARKER_COLOR = Color(0xFFFF3B30)

@Composable
fun WeightChart(
    allEntries: List<WeightEntry>,
    visibleEntries: List<WeightEntry>,
    modifier: Modifier = Modifier,
) {
    // The line is always drawn from every reading; only the window onto it changes. That lets a
    // range switch animate as a zoom instead of one point set being swapped for another.
    val entries = visibleEntries
    // Keeps the chart's height rather than collapsing, so a custom range that catches nothing
    // reads as an empty chart instead of the layout jumping.
    if (entries.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No readings in this range",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val measurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val readoutBackground = MaterialTheme.colorScheme.surfaceVariant
    val readoutText = MaterialTheme.colorScheme.onSurfaceVariant
    val chartBackground = MaterialTheme.colorScheme.background

    // Derived once per data change rather than on every frame.
    val stats = remember(entries) { ChartStats.from(entries) }

    // Where the finger is, in canvas pixels. Null when nothing is being touched.
    var touchX by remember { mutableStateOf<Float?>(null) }

    // The line animates at whichever end changed:
    //
    //  - a reading later than the current end appears: growth runs 0 to 1 and the final segment
    //    is drawn only partway, so the line reaches out to the new point.
    //  - the latest reading is deleted: the removed point is held as a ghost and retract runs
    //    1 to 0, so the line pulls back from where it used to end instead of snapping short.
    //
    // Editing an older day, changing range, or the first load leave both at rest.
    val newestDay = allEntries.lastOrNull()?.epochDay
    val growth = remember { Animatable(1f) }
    val retract = remember { Animatable(0f) }
    var ghost by remember { mutableStateOf<WeightEntry?>(null) }
    var previousNewest by remember { mutableStateOf(allEntries.lastOrNull()) }
    LaunchedEffect(newestDay) {
        val previous = previousNewest
        val current = allEntries.lastOrNull()
        previousNewest = current
        if (current == null || previous == null) return@LaunchedEffect
        when {
            current.epochDay > previous.epochDay -> {
                growth.snapTo(0f)
                growth.animateTo(1f, tween(LINE_ANIMATION_MS))
            }
            current.epochDay < previous.epochDay -> {
                ghost = previous
                retract.snapTo(1f)
                retract.animateTo(0f, tween(LINE_ANIMATION_MS))
                ghost = null
            }
        }
    }

    if (stats == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Not enough data to plot", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val spec = tween<Float>(RANGE_ANIMATION_MS)
    val minDay by animateFloatAsState(stats.minDay.toFloat(), spec, label = "minDay")
    val maxDay by animateFloatAsState(stats.maxDay.toFloat(), spec, label = "maxDay")
    val minLb by animateFloatAsState(stats.minLb.toFloat(), spec, label = "minLb")
    val maxLb by animateFloatAsState(stats.maxLb.toFloat(), spec, label = "maxLb")

    val labelStyle = TextStyle(fontSize = LABEL_SIZE, color = labelColor)
    val readoutStyle = TextStyle(
        fontSize = READOUT_SIZE,
        color = readoutText,
        fontWeight = FontWeight.Medium,
    )

    Canvas(
        modifier = modifier.pointerInput(entries) {
            // Tracks from the moment of touch-down and follows the finger, rather than waiting
            // for a drag threshold. Releasing clears the crosshair.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touchX = down.position.x
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    touchX = change.position.x
                    change.consume()
                }
                touchX = null
            }
        }
    ) {
        val plotLeft = Y_GUTTER.toPx()
        val plotRight = size.width
        val plotTop = 6.dp.toPx()
        val plotBottom = size.height - X_GUTTER.toPx()
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        fun xOf(epochDay: Long): Float {
            val span = (maxDay - minDay).coerceAtLeast(1f)
            return plotLeft + plotWidth * ((epochDay - minDay) / span)
        }

        fun yOf(lb: Double): Float {
            val span = (maxLb - minLb).coerceAtLeast(0.001f)
            return plotBottom - plotHeight * ((lb.toFloat() - minLb) / span)
        }

        // Horizontal gridlines, labelled in pounds.
        var tick = ceil(minLb / stats.yStep) * stats.yStep
        while (tick <= maxLb + 1e-6) {
            val y = yOf(tick)
            drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
            val text = formatTick(tick)
            val measured = measurer.measure(AnnotatedString(text), labelStyle)
            drawText(
                textMeasurer = measurer,
                text = text,
                topLeft = Offset(
                    x = plotLeft - measured.size.width - 6.dp.toPx(),
                    y = y - measured.size.height / 2f,
                ),
                style = labelStyle,
            )
            tick += stats.yStep
        }

        drawLine(
            gridColor,
            Offset(plotLeft, plotBottom),
            Offset(plotRight, plotBottom),
            strokeWidth = 1.dp.toPx(),
        )

        // Date labels, kept inside the canvas at both ends so nothing is clipped.
        for (day in stats.xTicks) {
            val x = xOf(day)
            val text = LocalDate.ofEpochDay(day).format(stats.xFormatter)
            val measured = measurer.measure(AnnotatedString(text), labelStyle)
            val left = (x - measured.size.width / 2f)
                .coerceIn(0f, size.width - measured.size.width)
            drawText(
                textMeasurer = measurer,
                text = text,
                topLeft = Offset(left, plotBottom + 4.dp.toPx()),
                style = labelStyle,
            )
        }

        // The weight line, drawn from every reading and clipped to the plot area. Points outside
        // the current window fall off the sides rather than being excluded from the path.
        clipRect(left = plotLeft, top = 0f, right = plotRight, bottom = plotBottom) {
            val path = Path()
            val lastIndex = allEntries.lastIndex
            val grown = growth.value
            allEntries.forEachIndexed { index, entry ->
                val x = xOf(entry.epochDay)
                val y = yOf(entry.weightLb)
                when {
                    index == 0 -> path.moveTo(x, y)
                    // The newest segment stops short while the growth animation runs, so the
                    // line extends toward the new reading rather than appearing at it.
                    index == lastIndex && grown < 1f -> {
                        val previous = allEntries[index - 1]
                        val fromX = xOf(previous.epochDay)
                        val fromY = yOf(previous.weightLb)
                        path.lineTo(fromX + (x - fromX) * grown, fromY + (y - fromY) * grown)
                    }
                    else -> path.lineTo(x, y)
                }
            }

            // Deleted newest reading: keep drawing out to where it was, pulling back to the new
            // end over the animation.
            val removed = ghost
            val retracting = retract.value
            if (removed != null && retracting > 0f) {
                val last = allEntries.last()
                val fromX = xOf(last.epochDay)
                val fromY = yOf(last.weightLb)
                val toX = xOf(removed.epochDay)
                val toY = yOf(removed.weightLb)
                path.lineTo(
                    fromX + (toX - fromX) * retracting,
                    fromY + (toY - fromY) * retracting,
                )
            }
            if (allEntries.size == 1) {
                // A one-point path draws nothing, so show the reading as a dot instead.
                drawCircle(
                    color = lineColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(xOf(allEntries[0].epochDay), yOf(allEntries[0].weightLb)),
                )
            } else {
                drawPath(
                    path,
                    lineColor,
                    style = Stroke(width = strokeWidthFor(entries.size, this@Canvas)),
                )
            }
        }

        // Crosshair, snapped to the nearest real reading so the readout is never interpolated.
        val finger = touchX
        if (finger != null) {
            val fraction = ((finger - plotLeft) / plotWidth).coerceIn(0f, 1f)
            val targetDay = stats.minDay + ((stats.maxDay - stats.minDay) * fraction).toLong()
            val entry = nearestByDay(entries, targetDay)
            val x = xOf(entry.epochDay)
            val y = yOf(entry.weightLb)

            drawLine(
                color = crosshairColor,
                start = Offset(x, plotTop),
                end = Offset(x, plotBottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                ),
            )
            // Knocked out of the line first, so the blue does not show through the marker.
            drawCircle(chartBackground, radius = 6.5.dp.toPx(), center = Offset(x, y))
            drawCircle(MARKER_COLOR, radius = 4.5.dp.toPx(), center = Offset(x, y))

            val text = formatUsDate(entry.date) + "   " + formatLb(entry.weightLb) + " lb"
            val measured = measurer.measure(AnnotatedString(text), readoutStyle)
            val padH = 8.dp.toPx()
            val padV = 5.dp.toPx()
            val boxWidth = measured.size.width + padH * 2
            val boxHeight = measured.size.height + padV * 2
            // Keep the readout on screen at both ends of the chart.
            val boxLeft = (x - boxWidth / 2f).coerceIn(0f, size.width - boxWidth)
            drawRoundRect(
                color = readoutBackground,
                topLeft = Offset(boxLeft, 0f),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawText(
                textMeasurer = measurer,
                text = text,
                topLeft = Offset(boxLeft + padH, padV),
                style = readoutStyle,
            )
        }
    }
}

// Binary search for the reading closest to the given day. Linear scanning four thousand entries
// on every touch move would be wasteful.
private fun nearestByDay(entries: List<WeightEntry>, targetDay: Long): WeightEntry {
    var low = 0
    var high = entries.size - 1
    while (low < high) {
        val mid = (low + high) / 2
        if (entries[mid].epochDay < targetDay) low = mid + 1 else high = mid
    }
    val candidate = entries[low]
    val previous = entries.getOrNull(low - 1) ?: return candidate
    return if (abs(previous.epochDay - targetDay) <= abs(candidate.epochDay - targetDay)) {
        previous
    } else {
        candidate
    }
}

// Dense data needs a thinner line, or twelve years of daily readings turn into a solid block.
private fun strokeWidthFor(pointCount: Int, scope: DrawScope): Float = with(scope) {
    when {
        pointCount > 1500 -> 1.dp.toPx()
        pointCount > 400 -> 1.5.dp.toPx()
        else -> 2.dp.toPx()
    }
}

private fun formatTick(lb: Double): String =
    if (abs(lb - lb.toLong()) < 0.05) lb.toLong().toString() else String.format("%.1f", lb)

private class ChartStats(
    val minDay: Long,
    val maxDay: Long,
    val minLb: Double,
    val maxLb: Double,
    val yStep: Double,
    val xTicks: List<Long>,
    val xFormatter: DateTimeFormatter,
) {
    companion object {
        fun from(entries: List<WeightEntry>): ChartStats? {
            if (entries.isEmpty()) return null
            val minDay = entries.first().epochDay
            val maxDay = entries.last().epochDay
            val lows = entries.minOf { it.weightLb }
            val highs = entries.maxOf { it.weightLb }

            // A flat series would divide by zero, so give it an artificial range.
            val rawSpan = (highs - lows).takeIf { it > 0.5 } ?: 2.0
            val padding = rawSpan * 0.08
            val minLb = lows - padding
            val maxLb = highs + padding
            val yStep = niceStep(maxLb - minLb, TARGET_Y_TICKS)

            val daySpan = maxDay - minDay
            // Never "8/14" for August 2014: with M/D/YYYY dates everywhere else in the app that
            // reads as the 14th of August. Multi-year spans get bare years instead.
            val formatter = when {
                daySpan > 1095 -> DateTimeFormatter.ofPattern("yyyy")
                daySpan > 120 -> DateTimeFormatter.ofPattern("MMM yyyy")
                else -> DateTimeFormatter.ofPattern("M/d")
            }

            val ticks = if (daySpan <= 0) {
                listOf(minDay)
            } else {
                (0..TARGET_X_TICKS).map { i -> minDay + (daySpan * i / TARGET_X_TICKS) }.distinct()
            }

            return ChartStats(minDay, maxDay, minLb, maxLb, yStep, ticks, formatter)
        }
    }
}

// Rounds a raw interval up to a 1, 2, 5 or 10 multiple so gridlines land on readable numbers
// rather than values like 13.7 lb.
private fun niceStep(range: Double, targetTicks: Int): Double {
    if (range <= 0.0) return 1.0
    val rough = range / targetTicks
    val magnitude = 10.0.pow(floor(log10(rough)))
    val normalised = rough / magnitude
    val step = when {
        normalised <= 1.0 -> 1.0
        normalised <= 2.0 -> 2.0
        normalised <= 5.0 -> 5.0
        else -> 10.0
    }
    return step * magnitude
}
