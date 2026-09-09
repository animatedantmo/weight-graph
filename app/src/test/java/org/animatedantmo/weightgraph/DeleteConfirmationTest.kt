package org.animatedantmo.weightgraph

import org.animatedantmo.weightgraph.ui.isDeleteConfirmation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteConfirmationTest {

    @Test
    fun acceptsAnyCapitalisation() {
        for (text in listOf("delete", "Delete", "DELETE", "DeLeTe")) {
            assertTrue("should have accepted: " + text, isDeleteConfirmation(text))
        }
    }

    @Test
    fun toleratesSurroundingWhitespace() {
        for (text in listOf(" delete", "delete ", "  Delete  ")) {
            assertTrue("should have accepted: " + text, isDeleteConfirmation(text))
        }
    }

    @Test
    fun rejectsAnythingElse() {
        for (text in listOf("", "   ", "del", "deleted", "delete all", "remove", "d elete")) {
            assertFalse("should have rejected: " + text, isDeleteConfirmation(text))
        }
    }
}
