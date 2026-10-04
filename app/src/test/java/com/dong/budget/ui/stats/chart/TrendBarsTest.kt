package com.dong.budget.ui.stats.chart

import androidx.compose.ui.graphics.Color
import com.dong.budget.ui.stats.detail.DetailMonth
import com.dong.budget.ui.stats.detail.trendSlotDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class TrendBarsTest {
    private val highlight = Color.Red
    private val context = Color.Gray

    @Test
    fun `막대 위 금액은 줄여 적고, 환불받은 돈이 더 많은 달은 + 다`() {
        assertEquals("12.3만", trendValueLabel(123_450))
        assertEquals("0", trendValueLabel(0))
        // 통계 상세는 전에 "-1만" 이라 같은 카드·같은 달인데 카드 실적 상세("+1만")와 부호가 반대였다
        assertEquals("+1만", trendValueLabel(-10_000))
        assertEquals("+1,000", trendValueLabel(-1_000))
    }

    @Test
    fun `통계 상세의 막대 글자와 화면 읽기 문장은 같은 부호다`() {
        val refunded = DetailMonth(YearMonth.of(2026, 7), amount = -10_000, count = 1, beforeFirstRecord = false)
        val slot = trendSlots(listOf(bar(refunded)), highlight, context).single()
        assertEquals("+1만", slot.valueLabel)
        assertEquals("2026년 7월, +10,000원, 1건", slot.description)
    }

    @Test
    fun `마지막 칸만 강조색과 굵은 달 이름이고, 기록 전 칸은 막대와 금액이 없다`() {
        val bars =
            listOf(
                bar(DetailMonth(YearMonth.of(2026, 5), amount = 0, count = 0, beforeFirstRecord = true)),
                bar(DetailMonth(YearMonth.of(2026, 6), amount = 83_000, count = 5, beforeFirstRecord = false)),
                bar(DetailMonth(YearMonth.of(2026, 7), amount = 120_000, count = 7, beforeFirstRecord = false)),
            )
        val slots = trendSlots(bars, highlight, context)

        assertEquals(emptyList<Long>(), slots[0].values)
        assertNull(slots[0].valueLabel)
        assertEquals(AxisLabel("5월"), slots[0].label)

        assertEquals(listOf(83_000L), slots[1].values)
        assertEquals("8.3만", slots[1].valueLabel)
        assertEquals(listOf(context), slots[1].colors)
        assertEquals(AxisLabelStyle.NORMAL, slots[1].label?.style)

        assertEquals(listOf(highlight), slots[2].colors)
        assertEquals(AxisLabel("7월", AxisLabelStyle.STRONG), slots[2].label)
    }

    private fun bar(point: DetailMonth) =
        TrendBar(month = point.month, amount = point.amount, recorded = !point.beforeFirstRecord, description = trendSlotDescription(point))
}
