package org.animatedantmo.weightgraph

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.animatedantmo.weightgraph.data.WeightDao
import org.animatedantmo.weightgraph.data.WeightDatabase
import org.animatedantmo.weightgraph.data.WeightEntry
import org.animatedantmo.weightgraph.data.WeightRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WeightDaoTest {

    private lateinit var db: WeightDatabase
    private lateinit var dao: WeightDao
    private lateinit var repo: WeightRepository

    private val day1 = LocalDate.of(2026, 1, 10)
    private val day2 = LocalDate.of(2026, 1, 11)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WeightDatabase::class.java,
        ).build()
        dao = db.weightDao()
        repo = WeightRepository(dao)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun recordThenReadBack() = runBlocking {
        repo.record(day1, 82.5)
        val found = repo.entryOn(day1)
        assertEquals(82.5, found!!.weightLb, 0.0001)
        assertEquals(day1.toEpochDay(), found.epochDay)
    }

    @Test
    fun secondEntryOnSameDayReplacesTheFirst() = runBlocking {
        repo.record(day1, 82.5)
        repo.record(day1, 81.0)
        assertEquals(1, repo.count())
        assertEquals(81.0, repo.entryOn(day1)!!.weightLb, 0.0001)
    }

    @Test
    fun differentDaysBothPersist() = runBlocking {
        repo.record(day1, 82.5)
        repo.record(day2, 82.1)
        assertEquals(2, repo.count())
    }

    @Test
    fun reimportOverwritesRatherThanDuplicating() = runBlocking {
        val batch = listOf(
            WeightEntry(epochDay = day1.toEpochDay(), weightLb = 82.5),
            WeightEntry(epochDay = day2.toEpochDay(), weightLb = 82.1),
        )
        repo.importAll(batch)
        repo.importAll(batch)
        assertEquals(2, repo.count())
    }

    @Test
    fun observeAllIsSortedByDateAscending() = runBlocking {
        repo.record(day2, 82.1)
        repo.record(day1, 82.5)
        val days = repo.observeAll().first().map { it.epochDay }
        assertEquals(listOf(day1.toEpochDay(), day2.toEpochDay()), days)
    }

    @Test
    fun observeSinceExcludesEarlierDays() = runBlocking {
        repo.record(day1, 82.5)
        repo.record(day2, 82.1)
        val since = repo.observeSince(day2).first()
        assertEquals(1, since.size)
        assertEquals(day2.toEpochDay(), since.single().epochDay)
    }

    @Test
    fun latestReturnsMostRecentDay() = runBlocking {
        repo.record(day1, 82.5)
        repo.record(day2, 82.1)
        assertEquals(day2.toEpochDay(), repo.latest()!!.epochDay)
    }

    @Test
    fun deleteOnRemovesThatDayOnly() = runBlocking {
        repo.record(day1, 82.5)
        repo.record(day2, 82.1)
        repo.deleteOn(day1)
        assertNull(repo.entryOn(day1))
        assertEquals(1, repo.count())
    }
}
