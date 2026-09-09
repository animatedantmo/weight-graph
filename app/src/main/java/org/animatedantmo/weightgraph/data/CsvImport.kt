package org.animatedantmo.weightgraph.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// One row that could not be turned into an entry, kept so the import screen can explain itself
// rather than silently dropping data.
data class SkippedRow(val lineNumber: Int, val text: String, val reason: String)

data class CsvImportResult(
    val entries: List<WeightEntry>,
    val skipped: List<SkippedRow>,
    val dateColumn: Int,
    val weightColumn: Int,
    val hadHeader: Boolean,
) {
    val importedCount: Int get() = entries.size
    val skippedCount: Int get() = skipped.size
}

// Date formats accepted on import. M/d/yyyy comes first because that is what the app displays and
// what Google Sheets exports in a US locale; the rest are common enough to be worth tolerating.
private val DATE_FORMATS = listOf(
    "M/d/yyyy",
    "M/d/yy",
    "yyyy-MM-dd",
    "d-MMM-yyyy",
    "MMM d, yyyy",
).map { DateTimeFormatter.ofPattern(it) }

private val DATE_HEADER_WORDS = listOf("date", "day", "when", "recorded", "timestamp")

// Checked in this order, so a sheet with both an AM and a PM column always yields the morning
// reading. Morning weights are the consistent ones; evening readings swing with food and fluid.
private val WEIGHT_HEADER_WORDS = listOf("am", "weight", "wt", "lbs", "lb", "pounds", "mass")

// Only unambiguous words are matched as substrings. "am" and "lb" are excluded here because they
// appear inside ordinary words like "name" and "album".
private val WEIGHT_HEADER_SUBSTRINGS = listOf("weight", "pounds", "lbs")

// Anything outside this is far more likely to be a mis-parsed column than a real reading.
private const val MIN_PLAUSIBLE_LB = 20.0
private const val MAX_PLAUSIBLE_LB = 1000.0

fun parseDate(text: String): LocalDate? {
    val cleaned = text.trim().removeSurrounding("\"").trim()
    if (cleaned.isEmpty()) return null
    for (format in DATE_FORMATS) {
        try {
            return LocalDate.parse(cleaned, format)
        } catch (_: DateTimeParseException) {
            // Fall through and try the next format.
        }
    }
    return null
}

// Splits one CSV line, honouring double-quoted fields and "" as an escaped quote.
fun splitCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                current.append('"')
                i++
            }
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> {
                fields.add(current.toString().trim())
                current.clear()
            }
            else -> current.append(c)
        }
        i++
    }
    fields.add(current.toString().trim())
    return fields
}

// A header row is one where nothing parses as a date and nothing as a plausible weight.
private fun looksLikeHeader(fields: List<String>): Boolean {
    val anyDate = fields.any { parseDate(it) != null }
    val anyWeight = fields.any { field ->
        val lb = parseLb(field)
        lb != null && lb in MIN_PLAUSIBLE_LB..MAX_PLAUSIBLE_LB
    }
    return !anyDate && !anyWeight && fields.any { it.isNotBlank() }
}

// Words are tried in their own order rather than the header's, so priority is by meaning, not by
// which column happens to come first. Exact matches always beat substring matches.
private fun columnByHeaderWords(
    header: List<String>,
    words: List<String>,
    substrings: List<String> = words,
): Int {
    for (word in words) {
        header.forEachIndexed { index, field ->
            if (field.lowercase().trim() == word) return index
        }
    }
    for (word in substrings) {
        header.forEachIndexed { index, field ->
            if (field.lowercase().contains(word)) return index
        }
    }
    return -1
}

// Picks the date and weight columns by scanning the data, for files with no usable header.
private fun columnsByContent(rows: List<List<String>>): Pair<Int, Int> {
    val width = rows.maxOfOrNull { it.size } ?: 0
    var dateColumn = -1
    var weightColumn = -1
    var bestDateHits = 0
    var bestWeightHits = 0
    for (column in 0 until width) {
        val values = rows.mapNotNull { it.getOrNull(column) }
        val dateHits = values.count { parseDate(it) != null }
        if (dateHits > bestDateHits) {
            bestDateHits = dateHits
            dateColumn = column
        }
    }
    for (column in 0 until width) {
        if (column == dateColumn) continue
        val values = rows.mapNotNull { it.getOrNull(column) }
        val weightHits = values.count { value ->
            val lb = parseLb(value)
            lb != null && lb in MIN_PLAUSIBLE_LB..MAX_PLAUSIBLE_LB
        }
        if (weightHits > bestWeightHits) {
            bestWeightHits = weightHits
            weightColumn = column
        }
    }
    return dateColumn to weightColumn
}

/**
 * Parses CSV exported from Google Sheets, or anywhere else, into [WeightEntry] rows.
 *
 * Columns are found by header name when there is a header and by inspecting the data otherwise.
 * Rows that cannot be read are collected in [CsvImportResult.skipped] with a reason rather than
 * dropped silently, so the import screen can show what happened. When a date repeats, the later
 * row wins, matching the upsert behaviour of the database.
 */
fun parseWeightCsv(text: String): CsvImportResult {
    val numbered = text.lineSequence()
        .map { it.trimEnd('\r') }
        .withIndex()
        .filter { it.value.isNotBlank() }
        .map { (it.index + 1) to splitCsvLine(it.value) }
        .toList()

    if (numbered.isEmpty()) {
        return CsvImportResult(emptyList(), emptyList(), -1, -1, false)
    }

    val hadHeader = looksLikeHeader(numbered.first().second)
    val header = if (hadHeader) numbered.first().second else emptyList()
    val dataRows = if (hadHeader) numbered.drop(1) else numbered

    var dateColumn = if (hadHeader) columnByHeaderWords(header, DATE_HEADER_WORDS) else -1
    var weightColumn = if (hadHeader) {
        columnByHeaderWords(header, WEIGHT_HEADER_WORDS, WEIGHT_HEADER_SUBSTRINGS)
    } else {
        -1
    }

    if (dateColumn < 0 || weightColumn < 0) {
        val (contentDate, contentWeight) = columnsByContent(dataRows.map { it.second })
        if (dateColumn < 0) dateColumn = contentDate
        if (weightColumn < 0) weightColumn = contentWeight
    }

    if (dateColumn < 0 || weightColumn < 0) {
        val skipped = dataRows.map { (line, fields) ->
            SkippedRow(line, fields.joinToString(","), "could not find a date and a weight column")
        }
        return CsvImportResult(emptyList(), skipped, dateColumn, weightColumn, hadHeader)
    }

    // Keyed by date so a repeated day collapses to the last row seen, as the database would.
    val byDate = LinkedHashMap<LocalDate, WeightEntry>()
    val skipped = mutableListOf<SkippedRow>()

    for ((line, fields) in dataRows) {
        val raw = fields.joinToString(",")
        val dateText = fields.getOrNull(dateColumn).orEmpty()
        val weightText = fields.getOrNull(weightColumn).orEmpty()

        if (dateText.isBlank() && weightText.isBlank()) {
            skipped.add(SkippedRow(line, raw, "empty row"))
            continue
        }
        val date = parseDate(dateText)
        if (date == null) {
            skipped.add(SkippedRow(line, raw, "unreadable date: " + dateText))
            continue
        }
        if (weightText.isBlank()) {
            skipped.add(SkippedRow(line, raw, "no weight recorded for this date"))
            continue
        }
        val weight = parseLb(weightText)
        if (weight == null) {
            skipped.add(SkippedRow(line, raw, "unreadable weight: " + weightText))
            continue
        }
        if (weight < MIN_PLAUSIBLE_LB || weight > MAX_PLAUSIBLE_LB) {
            skipped.add(SkippedRow(line, raw, "weight out of plausible range: " + formatLb(weight)))
            continue
        }
        byDate[date] = WeightEntry(epochDay = date.toEpochDay(), weightLb = weight)
    }

    return CsvImportResult(
        entries = byDate.values.sortedBy { it.epochDay },
        skipped = skipped,
        dateColumn = dateColumn,
        weightColumn = weightColumn,
        hadHeader = hadHeader,
    )
}
