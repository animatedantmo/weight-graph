package org.animatedantmo.weightgraph.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    @Query("SELECT * FROM weight_entries ORDER BY epochDay ASC")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries WHERE epochDay >= :fromEpochDay ORDER BY epochDay ASC")
    fun observeFrom(fromEpochDay: Long): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries WHERE epochDay = :epochDay LIMIT 1")
    suspend fun findByDay(epochDay: Long): WeightEntry?

    @Query("SELECT * FROM weight_entries ORDER BY epochDay DESC LIMIT 1")
    suspend fun latest(): WeightEntry?

    /**
     * Insert, replacing any existing row for the same day. REPLACE relies on the unique index
     * on [WeightEntry.epochDay], so re-importing a CSV overwrites days rather than duplicating
     * them. Note that REPLACE is a delete-then-insert, so the row's [WeightEntry.id] changes.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<WeightEntry>)

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("DELETE FROM weight_entries WHERE epochDay = :epochDay")
    suspend fun deleteByDay(epochDay: Long)

    @Query("DELETE FROM weight_entries")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM weight_entries")
    suspend fun count(): Int
}
