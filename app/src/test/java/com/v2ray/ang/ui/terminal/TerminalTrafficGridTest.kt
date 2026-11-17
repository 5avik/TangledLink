package com.v2ray.ang.ui.terminal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerminalTrafficGridTest {
    @Test fun noMeasurementsAndZeroTrafficNeverInventBars() {
        for (samples in listOf(emptyList(), listOf(0f, 0f), listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f))) {
            val grid = terminalTrafficGrid(samples, 12)
            assertEquals(4, grid.size)
            assertTrue(grid.all { it == " ".repeat(12) })
        }
    }

    @Test fun readingsAreRightAlignedAndScaleToTheMeasuredPeak() {
        val grid = terminalTrafficGrid(listOf(0f, 1f, 2f), 5, rows = 2)
        assertEquals(listOf("    █", "   ██"), grid)
        assertEquals(listOf("▄"), terminalTrafficGrid(listOf(1f, 4f), 2, rows = 1).map { it.take(1) })
    }

    @Test fun downsamplingPreservesABurstAndDropsReadingsOutsideTheHistoryWindow() {
        val burst = MutableList(60) { 0f }.also { it[23] = 100f }
        val grid = terminalTrafficGrid(burst, 10)
        assertTrue(grid.all { row -> row.count { it == '█' } == 1 })
        val stale = listOf(999f) + List(60) { 0f }
        assertTrue(terminalTrafficGrid(stale, 10).all { it.isBlank() })
    }

    @Test fun chartBoundsAreExplicitAndHugeValidReadingsStayFinite() {
        val grid = terminalTrafficGrid(listOf(Float.MAX_VALUE, Float.MAX_VALUE / 2), 8, 8)
        assertTrue(grid.all { it.length == 8 })
        assertEquals('█', grid.first()[6])
        assertEquals(' ', grid.first()[7])
        assertThrows(IllegalArgumentException::class.java) { terminalTrafficGrid(emptyList(), 0) }
    }
}
