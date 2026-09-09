package org.animatedantmo.weightgraph.data

/**
 * Builds a CSV of every entry, using the same date and weight formatting shown on screen:
 * M/D/YYYY and pounds to one decimal place.
 *
 * Rows run newest first, matching the order of the on-screen list.
 */
fun buildWeightCsv(entries: List<WeightEntry>): String = buildString {
    append("Date,Weight\n")
    entries.sortedByDescending { it.epochDay }.forEach { entry ->
        append(formatUsDate(entry.date))
        append(',')
        append(formatLb(entry.weightLb))
        append('\n')
    }
}
