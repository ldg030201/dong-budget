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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
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

    /** [at] 에 해지한 곳. 다시 물은 것에 '안 함' 을 눌렀으면 정한 때([decidedAt])만 뒤다. */
    private fun ended(key: String, at: String, decidedAt: String = at) =
        key to FixedMerchantEntity(key, FixedMerchantState.ENDED, seoulInstant(decidedAt), endedAt = seoulInstant(at))

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
        assertEquals(16_900L, candidate.latest.amount)
        assertEquals(rows[1].id, candidate.latest.id)
        assertEquals(11L, candidate.latest.paymentMethodId)
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

    // ── 해지한 곳 ──

    private val september = YearMonth.of(2026, 9)

    private fun board(split: FixedRegistrySplit, month: YearMonth, today: LocalDate = this.today) = split.board(month, today)

    private fun keysOn(board: FixedExpenseBoard) = (board.due + board.paid + board.notThisMonth + board.stopped).map { it.key }

    @Test
    fun `해지한 곳은 마지막으로 낸 몫의 달까지만 판에 넘기고 해지한 곳 묶음에 둔다`() {
        // 9월 5일까지 내고 9월 20일에 해지했다. 등록한 채였으면 10월 판에 '평소보다 11일 지났어요' 로 남았다.
        val rows = monthly("넷플릭스", 5, 17_000, 5..9)

        val split = split(rows, mapOf(ended("넷플릭스", "2026-09-20")))

        assertTrue(split.candidates.isEmpty())
        assertTrue(split.declined.isEmpty())
        assertTrue(split.registered.isEmpty())
        val place = split.ended.single()
        assertEquals(september, place.endMonth)
        assertFalse(place.asking)
        assertEquals(listOf("넷플릭스"), keysOf(split.endedPlaces))
        assertEquals(day("2026-09-05"), split.endedPlaces.single().lastPaidOn)
        // 끝 달인 9월까지는 그대로 냈어요이고, 10월부터는 판에 없다
        assertEquals(listOf("넷플릭스"), board(split, september).paid.map { it.key })
        assertEquals(listOf("넷플릭스"), keysOn(board(split, YearMonth.of(2026, 6))))
        assertTrue(board(split, october).isEmpty)
        assertEquals(setOf("넷플릭스"), split.endedKeysIn(september))
        assertTrue(split.endedKeysIn(october).isEmpty())
    }

    @Test
    fun `해지한 뒤에 지난 날짜로 적은 결제는 해지 전 결제이고 해지한 뒤에 낸 결제는 아니다`() {
        // 9월 20일에 해지한 뒤, 빠뜨렸던 9월 5일 몫을 10월 1일에 적었다. 10월 10일에 낸 것은 다시 물었을 때 '안 함' 을 눌렀다.
        val late = pay("2026-09-05", "넷플릭스", createdAt = "2026-10-01T09:00")
        val after = pay("2026-10-10T09:00", "넷플릭스", createdAt = "2026-10-10T09:00")
        val rows = monthly("넷플릭스", 5, 17_000, 5..8) + late + after

        val place = split(rows, mapOf(ended("넷플릭스", "2026-09-20", decidedAt = "2026-10-11"))).ended.single()

        assertTrue(late in place.rows)
        assertFalse(after in place.rows)
        assertEquals(september, place.endMonth)
        assertEquals(late.id, place.place.latest.id)
    }

    @Test
    fun `해지한 뒤에 새로 내면 다시 묻고 묻는 동안에는 해지한 곳 묶음에 두지 않는다`() {
        // 9월 20일에 해지했다가 10월 15일에 다시 구독했다
        val again = pay("2026-10-15T09:00", "넷플릭스", createdAt = "2026-10-15T09:01")
        val rows = monthly("넷플릭스", 5, 17_000, 5..9) + again

        val split = split(rows, mapOf(ended("넷플릭스", "2026-09-20")))

        // 묻는 줄은 새 결제로 보여 준다
        assertEquals(again.id, split.candidates.single().latest.id)
        assertTrue(split.endedPlaces.isEmpty())
        assertTrue(split.ended.single().asking)
        // 묻는 동안에도 해지 전 결제는 끝 달까지 판에 그대로이고, 새 결제는 판에 넘기지 않는다
        assertEquals(listOf("넷플릭스"), board(split, september).paid.map { it.key })
        assertFalse(again in split.boardRows(september))
        assertTrue(split.boardRows(october).isEmpty())
    }

    @Test
    fun `해지한 곳에 다시 물은 것에 안 함을 누르면 그 결제로는 다시 묻지 않고 그 뒤에 새로 내야 묻는다`() {
        val again = pay("2026-10-15T09:00", "넷플릭스", createdAt = "2026-10-15T09:01")
        val rows = monthly("넷플릭스", 5, 17_000, 5..9) + again
        // 10월 15일 저녁에 '안 함' 을 눌렀다. 해지한 때는 그대로다.
        val registry = mapOf(ended("넷플릭스", "2026-09-20", decidedAt = "2026-10-15T20:00"))

        val kept = split(rows, registry)

        assertTrue(kept.candidates.isEmpty())
        assertEquals(listOf("넷플릭스"), keysOf(kept.endedPlaces))
        // 해지한 곳 줄은 해지 전 마지막 결제로 보여 주고, 끝 달도 그대로다
        assertEquals(day("2026-09-05"), kept.endedPlaces.single().lastPaidOn)
        assertEquals(september, kept.ended.single().endMonth)
        assertTrue(kept.boardRows(october).isEmpty())

        val later = pay("2026-10-16T08:00", "넷플릭스", createdAt = "2026-10-16T08:00")
        val asked = split(rows + later, registry)
        assertEquals(later.id, asked.candidates.single().latest.id)
        assertTrue(asked.endedPlaces.isEmpty())
    }

    @Test
    fun `끝 달은 고른 달과 상관없이 같다`() {
        // 2023년 1월부터 매달 5일에 내다 2026년 9월에 해지했다. 지난 달 화면은 이번 달 화면보다 옛 결제까지 읽는다.
        val all =
            generateSequence(YearMonth.of(2023, 1)) { it.plusMonths(1) }
                .takeWhile { it <= september }
                .map { pay(it.atDay(5).toString(), "넷플릭스") }
                .toList()
        val thisMonthView = all.filter { it.occurredAt >= seoulInstant("2024-07-01") }
        val registry = mapOf(ended("넷플릭스", "2026-09-20"))

        val now = split(thisMonthView, registry).ended.single()
        val old = split(all, registry).ended.single()

        assertEquals(september, now.endMonth)
        assertEquals(now.endMonth, old.endMonth)
        // 판에 넘기는 해지 전 결제는 받은 기간 전부다(지난 달 판도 등록한 곳처럼 옛 결제로 계산한다)
        assertEquals(all, old.rows)
        assertEquals(listOf("넷플릭스"), keysOn(board(split(all, registry), YearMonth.of(2024, 1))))
    }

    @Test
    fun `매년 내는 것의 다음 몫을 미리 내고 해지하면 그 몫의 달까지 판에 보인다`() {
        // 매년 3월에 내는 자동차보험을 2월 22일에 미리 갱신하고 2월 23일에 해지했다
        val rows = listOf("2024-03-15", "2025-03-14", "2026-02-22").map { pay(it, "자동차보험", 800_000) }
        val registry = mapOf(ended("자동차보험", "2026-02-23T09:00"))
        val march = YearMonth.of(2026, 3)

        val february = splitByRegistry(rows, registry, created, day("2026-02-23"))
        val laterInMarch = splitByRegistry(rows, registry, created, day("2026-03-20"))

        assertEquals(march, february.ended.single().endMonth)
        assertEquals(march, laterInMarch.ended.single().endMonth)
        assertTrue(february.ended.single().showsIn(YearMonth.of(2026, 2)))
        assertEquals(FixedStatus.PAID, board(laterInMarch, march, day("2026-03-20")).paid.single().status)
        assertTrue(board(laterInMarch, YearMonth.of(2026, 4), day("2026-04-20")).isEmpty)
    }

    @Test
    fun `해지 전 결제가 날짜를 앞으로 적어 둔 것뿐이면 그 날짜가 든 달이 오기 전까지는 끝 달이 없어 판에 안 보인다`() {
        // 10월 1일에 11월 5일 몫을 미리 적어 두고 10월 10일에 해지했다. 10월 12일에 낸 것은 다시 물었을 때 '안 함' 을 눌렀다.
        val ahead = pay("2026-11-05", "헬스장", 55_000, createdAt = "2026-10-01T09:00")
        val after = pay("2026-10-12T09:00", "헬스장", 55_000, createdAt = "2026-10-12T09:00")
        val registry = mapOf(ended("헬스장", "2026-10-10", decidedAt = "2026-10-13"))

        val split = split(listOf(ahead, after), registry)

        val place = split.ended.single()
        assertNull(place.endMonth)
        assertFalse(place.showsIn(october))
        assertTrue(split.boardRows(october).isEmpty())
        // 해지한 곳 묶음에는 두어 되돌릴 수 있게 한다. 기간 안에 해지 전 결제가 없어 그 기간의 가장 최근 결제로 보여 준다.
        assertEquals(after.id, split.endedPlaces.single().latest.id)

        // 11월이 되면 미리 적어 둔 결제가 이번 달 기준 기간에 들어와 해지 전 결제로 세고, 그 몫의 달인 11월까지 판에 보인다(그날이 오기 전이어도)
        val november = YearMonth.of(2026, 11)
        for (date in listOf("2026-11-01", "2026-11-06")) {
            val inNovember = splitByRegistry(listOf(ahead, after), registry, created, day(date))
            assertEquals(november, inNovember.ended.single().endMonth)
            assertEquals(ahead.id, inNovember.endedPlaces.single().latest.id)
            assertEquals(FixedStatus.PAID, board(inNovember, november, day(date)).itemOf("헬스장")?.status)
        }
        assertTrue(
            board(
                splitByRegistry(listOf(ahead, after), registry, created, day("2026-12-10")),
                YearMonth.of(2026, 12),
                day("2026-12-10"),
            ).isEmpty,
        )
    }

    @Test
    fun `한 달에 두 번 내다 한 번만 내고 해지하면 그 달은 남은 차례를 기다리지 않고 낸 것만으로 냈어요다`() {
        // 5일 · 20일에 30,000원씩 내던 폰을 10월 5일 것만 내고 10월 6일에 해지했다. 전에는 10월 23일까지 남은 차례를 낼 돈에 넣고
        // 21일부터 '2번 중 1번 냈고, 남은 건 평소보다 1일 지났어요'(경고)로 알렸다.
        val rows = (5..10).flatMap { month -> listOf(5, 20).map { "2026-%02d-%02d".format(month, it) } }
            .filterNot { it == "2026-10-20" }
            .map { pay(it, "폰", 30_000) }
        val registry = mapOf(ended("폰", "2026-10-06"))

        for (date in listOf("2026-10-06", "2026-10-20", "2026-10-21", "2026-10-23", "2026-10-24")) {
            val today = day(date)
            val split = splitByRegistry(rows, registry, created, today)
            assertEquals(october, split.ended.single().endMonth)

            val board = board(split, october, today)
            val item = board.paid.single()
            assertTrue(board.due.isEmpty())
            assertEquals(0L, board.dueTotal)
            assertEquals(30_000L, board.paidTotal)
            assertEquals(30_000L, item.amount)
            assertEquals(1, item.paidCount)
            assertEquals(2, item.requiredCount)
            assertEquals(listOf(20), item.droppedDays)
            assertEquals("모두 냈어요", countSentence(board, october, today))
            assertEquals(RowNote("2번 중 1번만 냈어요", NoteTone.PLAIN), rowNote(item, october, today))
        }
        // 9월은 두 번 다 냈다
        assertEquals(2, board(split(rows, registry), september).paid.single().paidCount)
    }

    @Test
    fun `등록한 곳은 한 번만 낸 달의 남은 차례를 그대로 기다린다`() {
        // 해지하지 않았으면 남은 차례가 늦는 것일 수 있어 사흘은 '아직 덜 냈어요' 로 둔다(해지한 곳만 바로 냈어요다)
        val rows = (5..10).flatMap { month -> listOf(5, 20).map { "2026-%02d-%02d".format(month, it) } }
            .filterNot { it == "2026-10-20" }
            .map { pay(it, "폰", 30_000) }
        val today = day("2026-10-21")

        val board = board(splitByRegistry(rows, mapOf(registered("폰")), created, today), october, today)

        assertEquals(30_000L, board.due.single().amount)
        assertEquals(RowNote("2번 중 1번 냈고, 남은 건 평소보다 1일 지났어요", NoteTone.WARNING), rowNote(board.due.single(), october, today))
    }

    @Test
    fun `해지한 곳 묶음은 가장 최근에 낸 곳부터이고 가게끼리 끝 달이 따로다`() {
        val rows = monthly("넷플릭스", 5, 17_000, 5..9) + monthly("헬스장", 10, 55_000, 5..7)
        val registry = mapOf(ended("헬스장", "2026-08-01"), ended("넷플릭스", "2026-09-20"))

        val split = split(rows, registry)

        assertEquals(listOf("넷플릭스", "헬스장"), keysOf(split.endedPlaces))
        assertEquals(mapOf("넷플릭스" to september, "헬스장" to YearMonth.of(2026, 7)), split.ended.associate { it.key to it.endMonth })
        assertEquals(september, split(monthly("넷플릭스", 5, 17_000, 5..9), registry).ended.single().endMonth)
        assertEquals(listOf("넷플릭스"), keysOn(board(split, YearMonth.of(2026, 8))))
    }

    @Test
    fun `해지한 때가 빠진 해지한 곳은 정한 때에 해지한 것으로 본다`() {
        val rows = monthly("넷플릭스", 5, 17_000, 5..9) + pay("2026-10-05", "넷플릭스", createdAt = "2026-10-05T09:00")
        val registry = mapOf("넷플릭스" to FixedMerchantEntity("넷플릭스", FixedMerchantState.ENDED, seoulInstant("2026-09-20")))

        val split = split(rows, registry)

        assertEquals(listOf("넷플릭스"), keysOf(split.candidates))
        assertEquals(september, split.ended.single().endMonth)
    }

    @Test
    fun `해지한 곳도 이번 달 기준 기간에 지출이 없으면 묻지도 두지도 않는다`() {
        val split = split(listOf(pay("2024-06-10", "오래된 구독")), mapOf(ended("오래된 구독", "2024-07-01")))
        assertTrue(split.ended.isEmpty())
        assertTrue(split.candidates.isEmpty())
        assertTrue(split.boardRows(YearMonth.of(2024, 6)).isEmpty())
    }
}
