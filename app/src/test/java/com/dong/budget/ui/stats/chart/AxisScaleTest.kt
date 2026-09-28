package com.dong.budget.ui.stats.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AxisScaleTest {
    @Test
    fun `그릴 값이 없으면 눈금이 없다`() {
        assertNull(niceAxis(0))
        // 막대는 0 아래가 없어서 최댓값이 0 이하면 기준선만 긋는다
        assertNull(niceAxis(-5_000))
    }

    @Test
    fun `작은 값도 눈금 간격은 1,000원 아래로 내려가지 않는다`() {
        val axis = niceAxis(800)!!
        assertEquals(1_000L, axis.step)
        assertEquals(listOf(0L, 1_000L), axis.ticks)
    }

    @Test
    fun `간격은 1·2·5 에 10의 거듭제곱을 곱한 값이고 꼭대기는 최댓값 위의 첫 눈금이다`() {
        val small = niceAxis(87_000)!!
        assertEquals(50_000L, small.step)
        assertEquals(listOf(0L, 50_000L, 100_000L), small.ticks)

        val large = niceAxis(520_000)!!
        assertEquals(200_000L, large.step)
        assertEquals(listOf(0L, 200_000L, 400_000L, 600_000L), large.ticks)
    }

    @Test
    fun `최댓값이 눈금에 딱 맞으면 그 눈금이 꼭대기다`() {
        val axis = niceAxis(3_000)!!
        assertEquals(1_000L, axis.step)
        assertEquals(3_000L, axis.top)
    }

    @Test
    fun `음수 최솟값이 있으면 0 아래로도 같은 간격의 눈금을 둔다`() {
        val axis = niceAxis(87_000, min = -30_000)!!
        assertEquals(50_000L, axis.step)
        assertEquals(-50_000L, axis.bottom)
        assertEquals(listOf(-50_000L, 0L, 50_000L, 100_000L), axis.ticks)
    }

    @Test
    fun `모두 0 아래면 간격은 0 에서 먼 쪽을 기준으로 잡고 꼭대기는 0 이다`() {
        val axis = niceAxis(0, min = -40_000)!!
        assertEquals(20_000L, axis.step)
        assertEquals(listOf(-40_000L, -20_000L, 0L), axis.ticks)
    }

    @Test
    fun `만 원 아래는 쉼표만 찍는다`() {
        assertEquals("0", formatAxisWon(0))
        assertEquals("5,000", formatAxisWon(5_000))
        assertEquals("9,999", formatAxisWon(9_999))
    }

    @Test
    fun `만 단위는 두 자리까지 소수 한 자리, 세 자리부터는 정수다`() {
        assertEquals("1만", formatAxisWon(10_000))
        assertEquals("1.5만", formatAxisWon(15_000))
        assertEquals("8.3만", formatAxisWon(83_000))
        assertEquals("81.2만", formatAxisWon(812_000))
        assertEquals("150만", formatAxisWon(1_500_000))
        assertEquals("1,234만", formatAxisWon(12_340_000))
    }

    @Test
    fun `적지 못한 아래 자리는 버리고 윗 단위로 올리지 않는다`() {
        assertEquals("9.9만", formatAxisWon(99_960))
        assertEquals("99.9만", formatAxisWon(999_960))
        assertEquals("9,999만", formatAxisWon(99_996_000))
        // 통계 머리의 전체 금액(2,076,048원)보다 커 보이지 않는다
        assertEquals("207만", formatAxisWon(2_076_048))
    }

    @Test
    fun `억 단위`() {
        assertEquals("1.5억", formatAxisWon(150_000_000))
        assertEquals("12.3억", formatAxisWon(1_230_000_000))
        assertEquals("150억", formatAxisWon(15_000_000_000))
    }

    @Test
    fun `음수는 앞에 - 를 붙인다`() {
        assertEquals("-1만", formatAxisWon(-10_000))
        assertEquals("-5,000", formatAxisWon(-5_000))
    }
}
