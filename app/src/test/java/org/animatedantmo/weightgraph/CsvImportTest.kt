package org.animatedantmo.weightgraph

import org.animatedantmo.weightgraph.data.parseDate
import org.animatedantmo.weightgraph.data.parseWeightCsv
import org.animatedantmo.weightgraph.data.splitCsvLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvImportTest {

    private fun epoch(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun parsesTypicalGoogleSheetsExport() {
        val csv = """
            Date,Weight
            9/1/2026,200.0
            9/2/2026,180.0
            9/3/2026,190.0
            9/4/2026,170.0
        """.trimIndent()
        val result = parseWeightCsv(csv)
        assertTrue(result.hadHeader)
        assertEquals(4, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(epoch(2026, 9, 1), result.entries.first().epochDay)
        assertEquals(200.0, result.entries.first().weightLb, 1e-9)
        assertEquals(170.0, result.entries.last().weightLb, 1e-9)
    }

    @Test
    fun entriesComeBackSortedByDate() {
        val csv = "Date,Weight\n9/4/2026,170\n9/1/2026,200\n9/3/2026,190\n"
        val days = parseWeightCsv(csv).entries.map { it.epochDay }
        assertEquals(days.sorted(), days)
    }

    @Test
    fun findsColumnsWhenThereIsNoHeader() {
        val csv = "9/1/2026,200.0\n9/2/2026,180.0\n"
        val result = parseWeightCsv(csv)
        assertTrue(!result.hadHeader)
        assertEquals(2, result.importedCount)
    }

    @Test
    fun findsColumnsInAnyOrderAndIgnoresExtras() {
        val csv = "Notes,Weight (lbs),Date\nfelt good,178.8,9/1/2026\nrest day,179.2,9/2/2026\n"
        val result = parseWeightCsv(csv)
        assertEquals(2, result.dateColumn)
        assertEquals(1, result.weightColumn)
        assertEquals(2, result.importedCount)
        assertEquals(178.8, result.entries.first().weightLb, 1e-9)
    }

    @Test
    fun acceptsSeveralDateFormats() {
        assertEquals(LocalDate.of(2026, 9, 1), parseDate("9/1/2026"))
        assertEquals(LocalDate.of(2026, 9, 1), parseDate("9/1/26"))
        assertEquals(LocalDate.of(2026, 9, 1), parseDate("2026-09-01"))
        assertEquals(LocalDate.of(2026, 9, 1), parseDate("1-Sep-2026"))
        assertEquals(LocalDate.of(2026, 9, 1), parseDate("Sep 1, 2026"))
    }

    @Test
    fun rejectsUnreadableDates() {
        for (text in listOf("", "   ", "not a date", "13/45/2026")) {
            assertNull("should have rejected: " + text, parseDate(text))
        }
    }

    @Test
    fun acceptsWeightsWithUnitSuffixes() {
        val csv = "Date,Weight\n9/1/2026,178.8 lb\n9/2/2026,\"179.2 lbs\"\n9/3/2026,180#\n"
        val result = parseWeightCsv(csv)
        assertEquals(3, result.importedCount)
        assertEquals(178.8, result.entries[0].weightLb, 1e-9)
        assertEquals(179.2, result.entries[1].weightLb, 1e-9)
    }

    @Test
    fun skipsBadRowsButKeepsGoodOnes() {
        val csv = "Date,Weight\n9/1/2026,200\nbroken,180\n9/3/2026,notanumber\n9/4/2026,170\n"
        val result = parseWeightCsv(csv)
        assertEquals(2, result.importedCount)
        assertEquals(2, result.skippedCount)
        assertTrue(result.skipped[0].reason.contains("date"))
        assertTrue(result.skipped[1].reason.contains("weight"))
    }

    @Test
    fun reportsTheOriginalLineNumberOfSkippedRows() {
        val csv = "Date,Weight\n9/1/2026,200\nbroken,180\n"
        val skipped = parseWeightCsv(csv).skipped.single()
        assertEquals(3, skipped.lineNumber)
    }

    @Test
    fun rejectsImplausibleWeights() {
        val csv = "Date,Weight\n9/1/2026,3\n9/2/2026,5000\n9/3/2026,180\n"
        val result = parseWeightCsv(csv)
        assertEquals(1, result.importedCount)
        assertEquals(2, result.skippedCount)
    }

    @Test
    fun laterRowWinsWhenADateRepeats() {
        val csv = "Date,Weight\n9/1/2026,200\n9/1/2026,195\n"
        val result = parseWeightCsv(csv)
        assertEquals(1, result.importedCount)
        assertEquals(195.0, result.entries.single().weightLb, 1e-9)
    }

    @Test
    fun toleratesBlankLinesAndTrailingNewlines() {
        val csv = "Date,Weight\n\n9/1/2026,200\n\n\n9/2/2026,180\n\n"
        assertEquals(2, parseWeightCsv(csv).importedCount)
    }

    @Test
    fun toleratesWindowsLineEndings() {
        val csv = "Date,Weight\r\n9/1/2026,200\r\n9/2/2026,180\r\n"
        assertEquals(2, parseWeightCsv(csv).importedCount)
    }

    @Test
    fun handlesQuotedFieldsContainingCommas() {
        val fields = splitCsvLine("9/1/2026,\"178.8\",\"felt good, slept well\"")
        assertEquals(3, fields.size)
        assertEquals("felt good, slept well", fields[2])
    }

    @Test
    fun handlesEscapedQuotes() {
        val fields = splitCsvLine("a,\"say \"\"hi\"\" now\",b")
        assertEquals("say \"hi\" now", fields[1])
    }

    @Test
    fun emptyInputProducesNothingRatherThanCrashing() {
        val result = parseWeightCsv("")
        assertEquals(0, result.importedCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun aFileWithNoUsableColumnsReportsEveryRowAsSkipped() {
        val csv = "Name,Colour\nalice,red\nbob,blue\n"
        val result = parseWeightCsv(csv)
        assertEquals(0, result.importedCount)
        assertEquals(2, result.skippedCount)
    }
}
