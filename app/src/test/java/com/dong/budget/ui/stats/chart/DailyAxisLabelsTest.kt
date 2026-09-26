package com.dong.budget.ui.stats.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyAxisLabelsTest {
    /** 글자가 있는 날과 그 모양만 뽑는다 */
    private fun List<AxisLabel?>.shown(): Map<Int, AxisLabelStyle> = buildMap {
        this@shown.forEachIndexed { index, label -> if (label != null) put(index + 1, label.style) }
    }

    private val normal = AxisLabelStyle.NORMAL

    @Test
    fun `고정 눈금은 1·5·10·15·20·25일이고 칸 수는 그 달 날수다`() {
        val labels = dailyAxisLabels(lastDay = 30)
        assertEquals(30, labels.size)
        assertEquals(mapOf(1 to normal, 5 to normal, 10 to normal, 15 to normal, 20 to normal, 25 to normal, 30 to normal), labels.shown())
        assertEquals("25", labels[24]?.text)
    }

    @Test
    fun `말일은 25일보다 3일 이상 뒤일 때만 적는다`() {
        assertEquals(setOf(1, 5, 10, 15, 20, 25, 28), dailyAxisLabels(lastDay = 28).shown().keys)
        assertEquals(setOf(1, 5, 10, 15, 20, 25, 31), dailyAxisLabels(lastDay = 31).shown().keys)
        assertEquals(setOf(1, 5, 10, 15, 20, 25), dailyAxisLabels(lastDay = 27).shown().keys)
    }

    @Test
    fun `오늘과 고른 날은 늘 적고 둘레 2일 안의 고정 눈금은 뺀다`() {
        val shown = dailyAxisLabels(lastDay = 30, today = 12, selected = 23).shown()
        assertEquals(
            mapOf(
                1 to normal,
                5 to normal,
                // 10일은 오늘(12일)에서 2일, 25일은 고른 날(23일)에서 2일이라 빠진다
                12 to AxisLabelStyle.BRAND,
                15 to normal,
                20 to normal,
                23 to AxisLabelStyle.STRONG,
                30 to normal,
            ),
            shown,
        )
    }

    @Test
    fun `오늘을 고르면 오늘 모양이다`() {
        val shown = dailyAxisLabels(lastDay = 30, today = 15, selected = 15).shown()
        assertEquals(AxisLabelStyle.BRAND, shown[15])
        assertEquals(setOf(1, 5, 10, 15, 20, 25, 30), shown.keys)
    }

    @Test
    fun `고정 눈금 날을 고르면 굵게 적는다`() {
        assertEquals(AxisLabelStyle.STRONG, dailyAxisLabels(lastDay = 30, selected = 5).shown()[5])
    }
}
