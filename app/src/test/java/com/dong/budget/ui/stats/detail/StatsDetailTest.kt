package com.dong.budget.ui.stats.detail

import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType.EXPENSE
import com.dong.budget.data.db.TransactionType.INCOME
import com.dong.budget.data.db.TransactionType.REFUND
import com.dong.budget.data.db.TransactionType.TRANSFER
import com.dong.budget.navigation.StatsDimension
import com.dong.budget.testing.day
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.ComparisonScope
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.stats.EntryChange
import com.dong.budget.ui.stats.GroupKey
import com.dong.budget.ui.stats.calc.Grouping
import com.dong.budget.ui.stats.calc.Measure
import com.dong.budget.ui.stats.calc.breakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsDetailTest {
    private val today = day("2026-09-27")
    private val september = YearMonth.of(2026, 9)
    private val entity = DetailEntity(name = "이름", icon = null, color = null, missing = false)

    // 식비(1)·교통(2)은 지출 분류, 월급(9)은 수입 분류. 결제수단은 10·11
    private val rows = listOf(
        tx("2026-09-03T12:00", 20_000, categoryId = 1, paymentId = 10, merchant = "스타벅스"),
        tx("2026-09-05T09:00", 10_000, categoryId = 1, paymentId = 11, merchant = "GS25"),
        tx("2026-09-06T18:00", 5_000, REFUND, categoryId = 1, paymentId = 10),
        tx("2026-09-04T08:00", 25_000, categoryId = 2, paymentId = 10),
        tx("2026-09-01T10:00", 3_000_000, INCOME, categoryId = 9, paymentId = 11),
        tx("2026-09-07T10:00", 50_000, TRANSFER, categoryId = 1, paymentId = 10),
        // 오늘 뒤 날짜로 미리 적은 지출. 금액에는 들고 지난달 이맘때 비교에서는 빠진다.
        tx("2026-09-30T10:00", 7_000, categoryId = 1),
        tx("2026-08-10T10:00", 15_000, categoryId = 1, paymentId = 10),
        tx("2026-07-15T10:00", 8_000, categoryId = 1, paymentId = 11),
    )

    private fun detail(
        dimension: StatsDimension,
        id: Long?,
        month: YearMonth = september,
        today: LocalDate = this.today,
        rows: List<TransactionListItem> = this.rows,
        firstRecord: LocalDate? = day("2026-07-15"),
    ) = buildDetail(dimension, id, month, today, rows, firstRecord, entity)

    @Test
    fun `지출 분류 상세는 그 분류의 지출과 환불만 모으고 이체와 수입은 뺀다`() {
        val state = detail(StatsDimension.EXPENSE_CATEGORY, 1)
        assertTrue(state.loaded)
        assertEquals(GroupKey.Id(1), state.key)
        // 20,000 + 10,000 − 5,000 + 7,000(미리 적은 것)
        assertEquals(32_000L, state.amount)
        assertEquals(4, state.count)
        assertEquals(4, state.days.sumOf { it.items.size })
        assertTrue(state.days.flatMap { it.items }.all { it.categoryId == 1L && it.type != TRANSFER })
    }

    @Test
    fun `머리의 비율·건수·한 번에 평균은 탭의 같은 줄과 같다`() {
        val state = detail(StatsDimension.EXPENSE_CATEGORY, 1)
        val sameInTab = breakdown(
            rows.filter {
                YearMonth.from(it.localDate()) == september
            },
            Measure.EXPENSE,
            Grouping.CATEGORY,
            window = null,
        )
            .entries.first { it.key == GroupKey.Id(1) }
        val entry = state.entry!!
        assertEquals(sameInTab.share, entry.share)
        assertEquals(32_000.0 / (32_000 + 25_000), entry.share!!, 1e-12)
        assertEquals(4, entry.count)
        // 환불로 줄기 전 지출 3건의 평균
        assertEquals(12_333L, entry.averageTicket)
    }

    @Test
    fun `증감은 탭과 같은 범위(지난달 이맘때)로 잰다`() {
        val state = detail(StatsDimension.EXPENSE_CATEGORY, 1)
        // 이번 달은 오늘까지(미리 적은 7,000 빼고), 지난달은 같은 날까지
        assertEquals(EntryChange(ComparisonScope.SAME_DAY, previous = 15_000, current = 25_000), state.change)
    }

    @Test
    fun `이 달에 없어도 지난달에 있었으면 증감을 낸다`() {
        val october = rows + tx("2026-10-02T10:00", 3_000, categoryId = 1)
        val state = detail(StatsDimension.EXPENSE_CATEGORY, 2, month = YearMonth.of(2026, 10), today = day("2026-10-05"), rows = october)
        assertEquals(0L, state.amount)
        assertNull(state.entry)
        assertEquals(EntryChange(ComparisonScope.SAME_DAY, previous = 25_000, current = 0), state.change)
        assertTrue(state.days.isEmpty())
    }

    @Test
    fun `최근 6개월은 오래된 달이 앞이고 마지막 칸이 고른 달이다`() {
        val trend = detail(StatsDimension.EXPENSE_CATEGORY, 1).trend
        assertEquals((4..9).map { YearMonth.of(2026, it) }, trend.map { it.month })
        assertEquals(listOf(0L, 0L, 0L, 8_000L, 15_000L, 32_000L), trend.map { it.amount })
        assertEquals(listOf(0, 0, 0, 1, 1, 4), trend.map { it.count })
        // 7월 15일에 기록을 시작했다
        assertEquals(listOf(true, true, true, false, false, false), trend.map { it.beforeFirstRecord })
    }

    @Test
    fun `최근 6개월은 해를 넘어 이어진다`() {
        val trend = detail(StatsDimension.EXPENSE_CATEGORY, 1, month = YearMonth.of(2027, 2), today = day("2027-02-10")).trend
        assertEquals(YearMonth.of(2026, 9), trend.first().month)
        assertEquals(YearMonth.of(2027, 2), trend.last().month)
        assertEquals(32_000L, trend.first().amount)
    }

    @Test
    fun `기록 시작일 조회가 늦게 와도 보이는 가장 이른 거래부터 기록한 것으로 본다`() {
        val trend = detail(StatsDimension.EXPENSE_CATEGORY, 1, firstRecord = null).trend
        assertEquals(listOf(true, true, true, false, false, false), trend.map { it.beforeFirstRecord })
    }

    @Test
    fun `분류 상세의 교차 비중은 그 분류 거래를 결제수단별로 나눈 것이다`() {
        val cross = detail(StatsDimension.EXPENSE_CATEGORY, 1).cross!!
        assertEquals(listOf(GroupKey.Id(10), GroupKey.Id(11), GroupKey.None), cross.entries.map { it.key })
        assertEquals(listOf(15_000L, 10_000L, 7_000L), cross.entries.map { it.amount })
        assertEquals(32_000L, cross.total)
        // 비중만 본다
        assertTrue(cross.entries.all { it.change == null })
    }

    @Test
    fun `결제수단 상세는 그 결제수단의 지출만 모으고 교차 비중은 분류별이다`() {
        val state = detail(StatsDimension.PAYMENT_METHOD, 10)
        // 식비 20,000 − 5,000, 교통 25,000. 같은 결제수단의 이체는 뺀다.
        assertEquals(40_000L, state.amount)
        assertEquals(3, state.count)
        assertEquals(listOf(GroupKey.Id(2), GroupKey.Id(1)), state.cross!!.entries.map { it.key })
        assertEquals(listOf(25_000L, 15_000L), state.cross!!.entries.map { it.amount })
    }

    @Test
    fun `결제수단 상세에 수입은 들지 않는다`() {
        val state = detail(StatsDimension.PAYMENT_METHOD, 11)
        assertEquals(10_000L, state.amount)
        assertTrue(state.days.flatMap { it.items }.none { it.type == INCOME })
    }

    @Test
    fun `수입 분류 상세는 교차 비중과 많이 쓴 곳이 없다`() {
        val state = detail(StatsDimension.INCOME_CATEGORY, 9)
        assertEquals(3_000_000L, state.amount)
        assertEquals(1, state.count)
        assertNull(state.cross)
        assertTrue(state.merchants.isEmpty())
        assertNull(state.entry!!.averageTicket)
    }

    @Test
    fun `id 가 없으면 분류를 비운 거래를 모은다`() {
        val withNone = rows + tx("2026-09-08", 3_000, categoryId = null, paymentId = 11)
        val state = detail(StatsDimension.EXPENSE_CATEGORY, null, rows = withNone)
        assertEquals(GroupKey.None, state.key)
        assertEquals(3_000L, state.amount)
        assertEquals(1, state.count)
        // 결제수단을 비운 거래는 미리 적은 식비 한 건이다
        assertEquals(7_000L, detail(StatsDimension.PAYMENT_METHOD, null).amount)
    }

    @Test
    fun `많이 쓴 곳은 그 항목의 가게를 5곳까지 모은다`() {
        val many = rows + (1..6).map { tx("2026-09-10", 1_000L * it, EXPENSE, categoryId = 1, merchant = "가게$it") }
        val merchants = detail(StatsDimension.EXPENSE_CATEGORY, 1, rows = many).merchants
        assertEquals(DETAIL_MERCHANTS, merchants.size)
        assertEquals(listOf("스타벅스", "GS25", "가게6", "가게5", "가게4"), merchants.map { it.name })
    }

    @Test
    fun `거래는 날짜별로 최근 날이 먼저다`() {
        val days = detail(StatsDimension.EXPENSE_CATEGORY, 1).days
        assertEquals(listOf(30, 6, 5, 3), days.map { it.date.dayOfMonth })
    }

    @Test
    fun `이름은 목록에서 id 로 찾고 없으면 지운 것으로 본다`() {
        val categories = listOf(
            CategoryEntity(id = 1, uuid = "c1", scope = CategoryScope.EXPENSE, name = "식비", icon = "restaurant", color = "orange"),
        )
        assertEquals(
            DetailEntity(name = "식비", icon = "restaurant", color = "orange", missing = false),
            detailEntity(StatsDimension.EXPENSE_CATEGORY, 1, categories),
        )
        assertEquals(
            DetailEntity(name = "지운 분류", icon = null, color = null, missing = true),
            detailEntity(StatsDimension.EXPENSE_CATEGORY, 2, categories),
        )
        assertEquals(
            DetailEntity(name = "분류 없음", icon = null, color = null, missing = false),
            detailEntity(StatsDimension.INCOME_CATEGORY, null, categories),
        )

        val methods =
            listOf(
                PaymentMethodEntity(
                    id = 10,
                    uuid = "p10",
                    name = "하나카드",
                    type = PaymentMethodType.CARD,
                    icon = "credit_card",
                    color = "teal",
                ),
            )
        assertEquals("하나카드", detailEntity(StatsDimension.PAYMENT_METHOD, 10, methods).name)
        assertEquals(
            DetailEntity(name = "지운 결제수단", icon = null, color = null, missing = true),
            detailEntity(StatsDimension.PAYMENT_METHOD, 11, methods),
        )
        assertFalse(detailEntity(StatsDimension.PAYMENT_METHOD, null, methods).missing)
        assertEquals("결제수단 없음", detailEntity(StatsDimension.PAYMENT_METHOD, null, methods).name)
    }
}
