package org.animatedantmo.weightgraph

import org.animatedantmo.weightgraph.data.formatLb
import org.animatedantmo.weightgraph.data.formatLbWithUnit
import org.animatedantmo.weightgraph.data.kgFromLb
import org.animatedantmo.weightgraph.data.lbFromKg
import org.animatedantmo.weightgraph.data.parseLb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightConversionTest {

    @Test
    fun displayFormatsToOneDecimal() {
        assertEquals("178.8", formatLb(178.8))
        assertEquals("180.0", formatLb(180.0))
        assertEquals("178.8 lb", formatLbWithUnit(178.8))
    }

    @Test
    fun displayRoundsAtTheDecimal() {
        assertEquals("178.9", formatLb(178.85))
        assertEquals("178.8", formatLb(178.84))
    }

    @Test
    fun parsesPlainNumbers() {
        assertEquals(178.8, parseLb("178.8")!!, 1e-9)
        assertEquals(178.0, parseLb("178")!!, 1e-9)
    }

    @Test
    fun parsesUnitSuffixesAndWhitespace() {
        for (text in listOf(" 178.8 ", "178.8lb", "178.8 lbs", "178.8#", "178.8 LB")) {
            assertEquals("failed on '$text'", 178.8, parseLb(text)!!, 1e-9)
        }
    }

    @Test
    fun parsesThousandsSeparator() {
        assertEquals(1078.8, parseLb("1,078.8")!!, 1e-9)
    }

    @Test
    fun rejectsGarbage() {
        for (text in listOf("", "   ", "abc", "lb", "--", "1.2.3")) {
            assertNull("should have rejected '$text'", parseLb(text))
        }
    }

    /** Kept only for a possible future kilogram display; storage itself is pounds. */
    @Test
    fun kilogramHelpersRoundTrip() {
        assertEquals(178.8, lbFromKg(kgFromLb(178.8)), 1e-9)
    }
}
