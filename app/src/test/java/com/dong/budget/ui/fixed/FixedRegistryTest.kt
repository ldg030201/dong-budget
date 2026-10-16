package com.dong.budget.ui.fixed

import com.dong.budget.data.db.FixedMerchantEntity
import com.dong.budget.data.db.FixedMerchantState
import com.dong.budget.data.db.NO_MERCHANT_KEY
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.testing.day
import com.dong.budget.testing.seoulInstant
import com.dong.budget.testing.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class FixedRegistryTest {
    private val today = day("2026-10-16")
    private val october = YearMonth.of(2026, 10)

    /** 거래 번호 → 적은 때. [pay] 에 적은 때를 주면 여기 적는다(탭은 목록 줄과 따로 읽는다). 없는 번호는 모른다. */
    private val created = mutableMapOf<Long, Instant>()

    private fun pay(at: String, merchant: String?, amount: Long = 17_000, createdAt: String? = null, paymentId: Long? = 10) = tx(
        at,
        amount,
        categoryId = 4,
        paymentId = paymentId,
        merchant = merchant,
        paymentName = paymentId?.let {
            "하나카드"
        },
    ).also { row -> createdAt?.let { created[row.id] = seoulInstant(it) } }

    private fun split(rows: List<TransactionListItem>, registry: Map<String, FixedMerchantEntity>) =
        splitByRegistry(rows, registry, created, today)

    private fun monthly(merchant: String, day: Int, amount: Long, months: IntRange = 5..10) =
        months.map { pay("2026-%02d-%02d".format(it, day), merchant, amount) }

    private fun registered(key: String, at: String = "2026-10-01") =
        key to FixedMerchantEntity(key, FixedMerchantState.REGISTERED, seoulInstant(at))

    private fun declined(key: String, at: String) = key to FixedMerchantEntity(key, FixedMerchantState.DECLINED, seoulInstant(at))

    private fun keysOf(items: List<FixedCandidate>) = items.map { it.key }

    @Test
    fun `정한 적 없는 곳은 묻고 등록한 곳만 판에 넘긴다`() {
        val rows = monthly("넷플릭스", 5, 17_000) + monthly("헬스장", 10, 55_000)

        val split = split(rows, mapOf(registered("넷플릭스")))

        assertEquals(listOf("헬스장"), keysOf(split.candidates))
        assertTrue(split.declined.isEmpty())
        assertTrue(split.registered.all { it.merchant == "넷플릭스" })
        assertEquals(6, split.registered.size)
    }

    @Test
    fun `판과 요약은 등록한 곳만 센다`() {
        val rows = monthly("넷플릭스", 5, 17_000) + monthly("헬스장", 10, 55_000, 5..9) + monthly("통신비", 20, 33_000, 5..9)
        val registry = mapOf(registered("넷플릭스"), registered("헬스장"), declined("통신비", "2026-10-02"))

        val board = buildFixedExpenses(october, today, split(rows, registry).registered)

        assertEquals(setOf("넷플릭스", "헬스장"), (board.due + board.paid + board.notThisMonth + board.stopped).map { it.key }.toSet())
        // 넷플릭스는 냈고 헬스장은 아직이다. 통신비 33,000원은 낼 돈에 들지 않는다.
        assertEquals(2, board.dueCount)
        assertEquals(17_000L, board.paidTotal)
        assertEquals(55_000L, board.dueTotal)
    }

    @Test
    fun `등록 안 한 곳은 다시 묻지 않고 접은 묶음에 둔다`() {
        val rows = monthly("통신비", 20, 33_000, 5..9)

        val split = split(rows, mapOf(declined("통신비", "2026-10-02")))

        assertTrue(split.candidates.isEmpty())
        assertEquals(listOf("통신비"), keysOf(split.declined))
        assertTrue(split.registered.isEmpty())
    }

    @Test
    fun `등록 안 한 뒤에 새로 내면 다시 묻는다`() {
        // 9월에 해지하며 뺐다가 10월 15일에 다시 구독했다
        val rows = monthly("넷플릭스", 5, 17_000, 5..8) + pay("2026-10-15T09:00", "넷플릭스", createdAt = "2026-10-15T09:01")

        val split = split(rows, mapOf(declined("넷플릭스", "2026-09-10")))

        assertEquals(listOf("넷플릭스"), keysOf(split.candidates))
        assertEquals(day("2026-10-15"), split.candidates.single().lastPaidOn)
        assertTrue(split.declined.isEmpty())
    }

    @Test
    fun `정하기 전에 날짜를 앞으로 적어 둔 거래로는 다시 묻지 않는다`() {
        // 10월 1일에 10월 25일 몫을 미리 적어 두고, 10월 16일에 목록에서 뺐다
        val rows = monthly("관리비", 25, 200_000, 5..9) + pay("2026-10-25", "관리비", 200_000, createdAt = "2026-10-01T10:00")

        val split = split(rows, mapOf(declined("관리비", "2026-10-16T08:00")))

        assertTrue(split.candidates.isEmpty())
        assertEquals(listOf("관리비"), keysOf(split.declined))
    }

    @Test
    fun `정한 뒤에 지난 날짜로 적은 거래로도 다시 묻지 않는다`() {
        // 뺀 뒤에 빠뜨렸던 9월 몫을 늦게 적었다
        val rows = monthly("관리비", 25, 200_000, 5..8) + pay("2026-09-25", "관리비", 200_000, createdAt = "2026-10-16T09:00")

        val split = split(rows, mapOf(declined("관리비", "2026-10-01")))

        assertTrue(split.candidates.isEmpty())
    }

    @Test
    fun `적은 때를 모르면 낸 때로만 본다`() {
        val later = pay("2026-10-15", "넷플릭스")
        val split = split(listOf(later), mapOf(declined("넷플릭스", "2026-10-01")))
        assertEquals(listOf("넷플릭스"), keysOf(split.candidates))
        assertFalse(isNewSince(later, null, seoulInstant("2026-10-15T12:00")))
    }

    @Test
    fun `묻는 곳은 탭이 이번 달을 볼 때 읽는 기간의 지출로만 본다`() {
        // 지난 달 화면은 더 오래된 기록까지 읽는다. 27달 앞의 곳은 이번 달 화면이 읽지 않으니 묻지 않는다.
        val old = pay("2024-06-10", "오래된 구독")
        val inWindow = pay("2024-07-01", "헬스장")
        // 오늘이 든 달 뒤로 날짜를 적어 둔 거래도 탭이 읽지 않는다
        val ahead = pay("2026-11-03", "다음 달 구독")

        val split = split(listOf(old, inWindow, ahead), emptyMap())

        assertEquals(listOf("헬스장"), keysOf(split.candidates))
    }

    @Test
    fun `띄어쓰기만 다른 이름은 한 곳으로 묻고 이름과 금액은 가장 최근 지출이다`() {
        val rows =
            listOf(
                pay("2026-09-05", "유튜브 프리미엄", 14_900),
                pay("2026-10-05", " 유튜브프리미엄 ", 16_900, paymentId = 11),
            )

        val candidate = split(rows, emptyMap()).candidates.single()

        assertEquals("유튜브프리미엄", candidate.key)
        assertEquals("유튜브프리미엄", candidate.name)
        assertEquals(16_900L, candidate.lastAmount)
        assertEquals(rows[1].id, candidate.latestId)
        assertEquals(11L, candidate.paymentMethodId)
    }

    @Test
    fun `이름 없이 적은 지출도 한 곳으로 묻는다`() {
        val candidate = split(listOf(pay("2026-10-03", null), pay("2026-09-03", "  ")), emptyMap()).candidates.single()
        assertEquals(NO_MERCHANT_KEY, candidate.key)
        assertEquals(NO_MERCHANT_NAME, candidate.name)
    }

    @Test
    fun `묻는 곳과 등록 안 한 곳은 가장 최근에 낸 곳부터다`() {
        val rows = listOf(pay("2026-08-01", "가"), pay("2026-10-01", "나"), pay("2026-09-01", "다"), pay("2026-09-01", "라"))
        val registry = mapOf(declined("다", "2026-09-10"), declined("라", "2026-09-10"))

        val split = split(rows, registry)

        assertEquals(listOf("나", "가"), keysOf(split.candidates))
        assertEquals(listOf("다", "라"), keysOf(split.declined))
    }

    @Test
    fun `지출이 아닌 행으로는 묻지 않는다`() {
        val refund = tx("2026-10-03", 17_000, type = TransactionType.REFUND, categoryId = 4, merchant = "넷플릭스")
        assertTrue(split(listOf<TransactionListItem>(refund), emptyMap()).candidates.isEmpty())
    }

    @Test
    fun `등록한 곳의 지출은 기간과 상관없이 모두 계산에 넘긴다`() {
        // 지난 달 화면이 읽은 오래된 결제도 일정 짐작에 쓰인다
        val rows = listOf(pay("2024-03-10", "자동차보험", 680_000), pay("2025-03-10", "자동차보험", 680_000))
        assertEquals(rows, split(rows, mapOf(registered("자동차보험"))).registered)
    }
}
