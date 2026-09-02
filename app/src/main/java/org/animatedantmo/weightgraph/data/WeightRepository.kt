package org.animatedantmo.weightgraph.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Everything above this layer works in [LocalDate] and pounds; the epoch-day encoding stays an
 * implementation detail of the database.
 */
class WeightRepository(private val dao: WeightDao) {

    fun observeAll(): Flow<List<WeightEntry>> = dao.observeAll()

    fun observeSince(from: LocalDate): Flow<List<WeightEntry>> =
        dao.observeFrom(from.toEpochDay())

    suspend fun entryOn(date: LocalDate): WeightEntry? = dao.findByDay(date.toEpochDay())

    suspend fun latest(): WeightEntry? = dao.latest()

    suspend fun count(): Int = dao.count()

    /** Records a reading for [date], replacing any existing entry on that day. */
    suspend fun record(date: LocalDate, weightLb: Double, note: String? = null) {
        dao.upsert(WeightEntry(epochDay = date.toEpochDay(), weightLb = weightLb, note = note))
    }

    /**
     * Bulk path for CSV / spreadsheet import. Duplicate days within [entries] collapse to the
     * last one seen, matching the per-row replace behaviour.
     */
    suspend fun importAll(entries: List<WeightEntry>) = dao.upsertAll(entries)

    suspend fun delete(entry: WeightEntry) = dao.delete(entry)

    suspend fun deleteOn(date: LocalDate) = dao.deleteByDay(date.toEpochDay())

    suspend fun clear() = dao.clear()
}
