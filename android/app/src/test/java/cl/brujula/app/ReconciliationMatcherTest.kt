package cl.brujula.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconciliationMatcherTest {
    private val day = 1_800_000_000_000L

    @Test
    fun reconcilesOnlyUniqueOneToOneMatchWithinSevenDays() {
        val statement = ReconciliationRecord(1, day, 12_345, "Comercio Ejemplo SpA")
        val capture = ReconciliationRecord(2, day + 7L * 24 * 60 * 60 * 1000, 12_345, "Comercio Ejemplo")

        assertEquals(listOf(1L to 2L), determineReconciliationPairs(listOf(statement), listOf(capture)))
    }

    @Test
    fun leavesAmbiguousStatementUnreconciled() {
        val statement = ReconciliationRecord(1, day, 12_345, "Comercio Ejemplo")
        val captures = listOf(
            ReconciliationRecord(2, day, 12_345, "Comercio Ejemplo"),
            ReconciliationRecord(3, day + 1, 12_345, "Comercio Ejemplo SpA")
        )

        assertEquals(emptyList<Pair<Long, Long>>(), determineReconciliationPairs(listOf(statement), captures))
    }

    @Test
    fun leavesCompetingStatementsUnreconciled() {
        val statements = listOf(
            ReconciliationRecord(1, day, 12_345, "Comercio Ejemplo"),
            ReconciliationRecord(2, day + 1, 12_345, "Comercio Ejemplo")
        )
        val capture = ReconciliationRecord(3, day, 12_345, "Comercio Ejemplo")

        assertEquals(emptyList<Pair<Long, Long>>(), determineReconciliationPairs(statements, listOf(capture)))
    }

    @Test
    fun doesNotMatchDifferentAmountMovementOrOutOfWindowDate() {
        val statement = ReconciliationRecord(1, day, 12_345, "Comercio Ejemplo")
        val captures = listOf(
            ReconciliationRecord(2, day, 12_346, "Comercio Ejemplo"),
            ReconciliationRecord(3, day, 12_345, "Comercio Ejemplo", movementType = "transfer"),
            ReconciliationRecord(4, day + 8L * 24 * 60 * 60 * 1000, 12_345, "Comercio Ejemplo")
        )

        assertEquals(emptyList<Pair<Long, Long>>(), determineReconciliationPairs(listOf(statement), captures))
    }

    @Test
    fun returnsSamePairsRegardlessOfInputOrder() {
        val statements = listOf(
            ReconciliationRecord(10, day, 12_345, "Comercio Uno"),
            ReconciliationRecord(11, day + 1, 23_456, "Comercio Dos")
        )
        val captures = listOf(
            ReconciliationRecord(20, day + 2, 23_456, "Comercio Dos SpA"),
            ReconciliationRecord(21, day + 3, 12_345, "Comercio Uno Ltda")
        )

        val expected = listOf(10L to 21L, 11L to 20L)
        assertEquals(expected, determineReconciliationPairs(statements, captures))
        assertEquals(expected, determineReconciliationPairs(statements.reversed(), captures.reversed()))
    }

    @Test
    fun permitsSeparateUniquePairsInOneReconciliationRun() {
        val statements = listOf(
            ReconciliationRecord(1, day, 12_345, "Comercio Uno"),
            ReconciliationRecord(2, day, 23_456, "Comercio Dos")
        )
        val captures = listOf(
            ReconciliationRecord(3, day + 1, 12_345, "Comercio Uno SpA"),
            ReconciliationRecord(4, day + 1, 23_456, "Comercio Dos SpA")
        )

        assertEquals(listOf(1L to 3L, 2L to 4L), determineReconciliationPairs(statements, captures))
    }
}
