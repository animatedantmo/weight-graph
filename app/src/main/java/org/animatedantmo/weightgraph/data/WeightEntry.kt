package org.animatedantmo.weightgraph.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A single weight reading, keyed to one calendar day.
 *
 * The date is stored as [LocalDate.toEpochDay] rather than a millisecond timestamp so the
 * unique index below genuinely means "one entry per day" — two readings on the same date
 * cannot differ by a few milliseconds and both slip in. It also gives the chart a clean
 * integer x-axis.
 *
 * Weight is stored in pounds, the same unit it is entered and displayed in, so nothing is
 * converted on the way in or out. [kgFromLb] exists only for a possible future kg display.
 */
@Entity(
    tableName = "weight_entries",
    indices = [Index(value = ["epochDay"], unique = true)],
)
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val weightLb: Double,
    val note: String? = null,
)

val WeightEntry.date: LocalDate
    get() = LocalDate.ofEpochDay(epochDay)
