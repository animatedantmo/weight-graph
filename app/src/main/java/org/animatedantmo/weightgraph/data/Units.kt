package org.animatedantmo.weightgraph.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// A pound is defined as exactly 0.45359237 kg.
const val KG_PER_LB = 0.45359237

// Decimal places shown for a weight. One is enough to match a bathroom scale.
const val LB_DECIMALS = 1

// Only needed if a kilogram display is ever added; storage is pounds.
fun kgFromLb(lb: Double): Double = lb * KG_PER_LB

fun lbFromKg(kg: Double): Double = kg / KG_PER_LB

// Formats a stored weight for display, e.g. 178.8 -> "178.8".
fun formatLb(lb: Double): String = String.format(Locale.US, "%.${LB_DECIMALS}f", lb)

// Formats with the unit attached, e.g. "178.8 lb".
fun formatLbWithUnit(lb: Double): String = "${formatLb(lb)} lb"

/**
 * Parses user or CSV input in decimal pounds. Tolerates surrounding whitespace, a trailing
 * "lb"/"lbs"/"#", and thousands separators. Returns null if the text is not a usable number.
 */
fun parseLb(text: String): Double? {
    val cleaned = text.trim()
        .removeSuffix("s")
        .removeSuffix("lb")
        .removeSuffix("LB")
        .removeSuffix("#")
        .trim()
        .replace(",", "")
    return cleaned.toDoubleOrNull()
}

// Dates are entered, displayed and exported as M/D/YYYY throughout the app.
private val US_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yyyy")

fun formatUsDate(date: LocalDate): String = date.format(US_DATE)
