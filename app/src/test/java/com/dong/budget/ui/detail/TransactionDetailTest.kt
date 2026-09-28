package com.dong.budget.ui.detail

import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.data.db.TransactionType.TRANSFER
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class TransactionDetailTest {
    private val today = day("2026-09-28")
    private val start = samePlaceStart(today)

    @Test
    fun `같은 곳 내역은 오늘을 넣어 딱 1년이다`() {
        assertEquals(day("2025-09-29"), samePlaceStart(day("2026-09-28")))
        // 윤날의 1년 전은 2월 28일이라 그다음 날부터다
        assertEquals(day("2023-03-01"), samePlaceStart(day("2024-02-29")))
    }

    @Test
    fun `거래가 없으면 사라진 상태다`() {
        assertEquals(TransactionDetailUiState.Gone, transactionDetail(null, emptyList(), today))
    }

    @Test
    fun `띄어쓰기와 대소문자만 다른 가게를 한 곳으로 모으고 보는 거래도 넣는다`() {
        val current = tx("2026-09-20", 5_000, merchant = "스타벅스")
        val rows =
            listOf(
                current,
                tx("2026-09-01", 6_000, merchant = "스타 벅스 "),
                tx("2026-08-15", 4_000, merchant = "스타벅스"),
                tx("2026-09-10", 9_000, merchant = "스타벅스 강남점"),
                tx("2026-09-11", 7_000, merchant = "이디야"),
            )
        val place = requireNotNull(samePlace(current, rows, start))
        assertEquals(3, place.count)
        assertEquals(15_000L, place.amount)
        assertEquals(5_000L, place.averageTicket)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8)), place.months.map { it.month })
        // 달 안에서는 최근 거래가 먼저
        assertEquals(listOf(current, rows[1]), place.months.first().items)
    }

    @Test
    fun `1년보다 오래된 거래는 빼고 앞으로의 날짜로 적은 거래는 넣는다`() {
        val current = tx("2026-09-20", 5_000, merchant = "헬스장")
        val rows =
            listOf(
                current,
                tx("2025-09-28T23:59", 50_000, merchant = "헬스장"),
                tx("2025-09-29T00:00", 50_000, merchant = "헬스장"),
                tx("2026-10-05", 50_000, merchant = "헬스장"),
            )
        val place = requireNotNull(samePlace(current, rows, start))
        assertEquals(listOf(rows[3], current, rows[2]), place.months.flatMap { it.items })
    }

    @Test
    fun `지출 쪽은 환불을 빼고 세며 수입은 넣지 않는다`() {
        val current = tx("2026-09-20", 20_000, merchant = "쿠팡")
        val rows =
            listOf(
                current,
                tx("2026-09-10", 10_000, merchant = "쿠팡"),
                tx("2026-09-12", 12_000, REFUND, merchant = "쿠팡"),
                tx("2026-09-13", 3_000, INCOME, merchant = "쿠팡"),
            )
        val place = requireNotNull(samePlace(current, rows, start))
        assertEquals(false, place.income)
        // 순지출 = 20,000 + 10,000 − 12,000. 횟수는 지출만, 한 번에 평균은 환불 전 금액이다.
        assertEquals(18_000L, place.amount)
        assertEquals(2, place.count)
        assertEquals(15_000L, place.averageTicket)
        assertEquals(3, place.months.single().items.size)
    }

    @Test
    fun `수입이면 같은 곳의 수입만 모으고 평균은 내지 않는다`() {
        val current = tx("2026-09-25", 3_000_000, INCOME, merchant = "회사")
        val rows =
            listOf(
                current,
                tx("2026-08-25", 3_000_000, INCOME, merchant = "회사"),
                tx("2026-09-01", 10_000, merchant = "회사"),
            )
        val place = requireNotNull(samePlace(current, rows, start))
        assertEquals(true, place.income)
        assertEquals(6_000_000L, place.amount)
        assertEquals(2, place.count)
        assertNull(place.averageTicket)
    }

    @Test
    fun `한 번뿐이면 한 번에 평균을 내지 않는다`() {
        val current = tx("2026-09-20", 5_000, merchant = "스타벅스")
        val place = requireNotNull(samePlace(current, listOf(current), start))
        assertEquals(1, place.count)
        assertNull(place.averageTicket)
    }

    @Test
    fun `가게 이름이 없거나 이체면 같은 곳 내역이 없다`() {
        assertNull(samePlace(tx("2026-09-20", 5_000), emptyList(), start))
        assertNull(samePlace(tx("2026-09-20", 5_000, merchant = "  "), emptyList(), start))
        assertNull(samePlace(tx("2026-09-20", 5_000, TRANSFER, merchant = "토스"), emptyList(), start))
    }

    @Test
    fun `보는 거래가 1년보다 오래됐고 그 뒤로 없으면 달이 비어 있다`() {
        val current = tx("2024-05-01", 5_000, merchant = "스타벅스")
        val place = requireNotNull(samePlace(current, emptyList(), start))
        assertTrue(place.months.isEmpty())
        assertEquals(0, place.count)
        assertEquals(0L, place.amount)
    }

    @Test
    fun `상세 상태는 오늘 기준 1년으로 같은 곳을 계산한다`() {
        val current = tx("2026-09-20", 5_000, merchant = "스타벅스")
        val old = tx("2025-09-01", 5_000, merchant = "스타벅스")
        val state = transactionDetail(current, listOf(current, old), today) as TransactionDetailUiState.Shown
        assertEquals(current, state.item)
        assertEquals(today, state.today)
        assertEquals(listOf(current), state.samePlace?.months?.flatMap { it.items })
    }
}
