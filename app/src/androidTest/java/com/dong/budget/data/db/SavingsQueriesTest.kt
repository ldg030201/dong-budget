package com.dong.budget.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * 카드실적 · 월급 탭 · 통계 기록 시작 조회가 '저축' 분류로 적은 돈을 빼는지 실제 SQLite 로 본다(단위 테스트로는 SQL 을 돌릴 수 없다).
 * 분류를 비운 거래는 그대로 남아야 한다(NOT IN 만 쓰면 categoryId 가 NULL 인 거래가 통째로 빠진다).
 * 기기에서 돈다. 메모리 DB 를 써서 기기에 있는 실제 가계부(dong-budget.db)는 건드리지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class SavingsQueriesTest {
    private lateinit var database: BudgetDatabase

    private val categories = DEFAULT_CATEGORIES.mapIndexed { i, c -> c.toEntity().copy(id = i + 1L) }
    private val savings = categories.first { it.code == SAVINGS_CATEGORY_CODE }
    private val food = categories.first { it.code == "FOOD" }
    private val card = PaymentMethodEntity(id = 201, uuid = "user:hana", name = "하나카드", type = PaymentMethodType.CARD)

    private val start = Instant.parse("2026-09-01T00:00:00Z")
    private val end = Instant.parse("2026-11-01T00:00:00Z")

    /** [at] (UTC)에 하나카드로 낸 거래. 가게 이름은 [uuid] 와 같다. */
    private fun tx(uuid: String, type: TransactionType, amount: Long, at: String, category: CategoryEntity?): TransactionEntity {
        val instant = Instant.parse(at)
        return TransactionEntity(
            uuid = uuid,
            type = type,
            amount = amount,
            occurredAt = instant,
            occurredDate = 20261001,
            categoryId = category?.id,
            paymentMethodId = card.id,
            merchant = uuid,
            createdAt = instant,
            updatedAt = instant,
        )
    }

    private val rows =
        listOf(
            // 가장 먼저 낸 것이 적금이라, 카드를 처음 쓴 날은 그다음 지출이어야 한다
            tx("적금", TransactionType.EXPENSE, 300_000, "2026-09-01T03:00:00Z", savings),
            tx("점심", TransactionType.EXPENSE, 10_000, "2026-10-02T03:00:00Z", food),
            tx("적금 해지", TransactionType.REFUND, 50_000, "2026-10-03T03:00:00Z", savings),
            tx("분류 없음", TransactionType.EXPENSE, 5_000, "2026-10-04T03:00:00Z", null),
            tx("점심 환불", TransactionType.REFUND, 2_000, "2026-10-05T03:00:00Z", food),
            tx("청약", TransactionType.EXPENSE, 100_000, "2026-10-06T03:00:00Z", savings),
        )

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, BudgetDatabase::class.java).build()
        runBlocking {
            categories.forEach { database.backupDao().insertCategory(it) }
            database.backupDao().insertPaymentMethod(card)
            rows.forEach { database.transactionDao().insert(it) }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `카드실적 탭은 저축을 빼고 분류를 비운 거래는 센다`() = runBlocking {
        val spending = database.transactionDao().observeCardSpending(start, end).first()
        assertEquals(listOf(10_000L, 5_000L, 2_000L), spending.sortedBy { it.occurredAt }.map { it.amount })
    }

    @Test
    fun `카드를 처음 쓴 날은 저축을 빼고 센다`() = runBlocking {
        val lunch = Instant.parse("2026-10-02T03:00:00Z")
        val firstUse = database.transactionDao().observeFirstUseByPaymentMethod().first()
        assertEquals(listOf(PaymentMethodFirstUse(card.id, lunch)), firstUse)
        assertEquals(lunch, database.transactionDao().observeFirstUseOf(card.id).first())
    }

    @Test
    fun `저축만 낸 카드는 처음 쓴 날이 없다`() = runBlocking {
        val other = PaymentMethodEntity(id = 202, uuid = "user:toss", name = "토스카드", type = PaymentMethodType.CARD)
        database.backupDao().insertPaymentMethod(other)
        val deposit = tx("적금2", TransactionType.EXPENSE, 10_000, "2026-10-07T03:00:00Z", savings)
        database.transactionDao().insert(deposit.copy(paymentMethodId = other.id))
        assertNull(database.transactionDao().observeFirstUseOf(other.id).first())
        assertTrue(database.transactionDao().observeFirstUseByPaymentMethod().first().none { it.paymentMethodId == other.id })
    }

    @Test
    fun `통계 기록 시작은 저축을 빼고 센다`() = runBlocking {
        val dao = database.transactionDao()
        assertEquals(Instant.parse("2026-10-02T03:00:00Z"), dao.observeFirstOccurredAt().first())
        // 이체는 여전히 빼고, 수입은 분류가 없어도 센다
        dao.insert(tx("이체", TransactionType.TRANSFER, 1_000, "2026-08-01T03:00:00Z", null))
        dao.insert(tx("용돈", TransactionType.INCOME, 20_000, "2026-08-20T03:00:00Z", null))
        assertEquals(Instant.parse("2026-08-20T03:00:00Z"), dao.observeFirstOccurredAt().first())
    }

    @Test
    fun `저축뿐이면 통계 기록 시작이 없다`() = runBlocking {
        database.backupDao().deleteTransactions()
        rows.filter { it.categoryId == savings.id }.forEach { database.transactionDao().insert(it) }
        assertNull(database.transactionDao().observeFirstOccurredAt().first())
    }

    @Test
    fun `카드실적 상세는 저축을 빼고 최신순으로 읽는다`() = runBlocking {
        val items = database.transactionDao().observeByPaymentMethod(card.id, start, end).first()
        assertEquals(listOf("점심 환불", "분류 없음", "점심"), items.map { it.merchant })
    }

    @Test
    fun `월급 탭 최근 지출은 저축을 빼고 건수를 채운다`() = runBlocking {
        // 가장 최근 지출이 저축(청약)이어도 저축 아닌 지출로 두 건을 채운다
        val recent = database.transactionDao().observeRecentExpenses(end, limit = 2).first()
        assertEquals(listOf("분류 없음", "점심"), recent.map { it.merchant })
    }

    @Test
    fun `목록 줄은 분류 코드를 읽어 저축을 알아본다`() = runBlocking {
        val all = database.transactionDao().observeAll().first().associateBy { it.merchant }
        assertEquals(SAVINGS_CATEGORY_CODE, all.getValue("적금").categoryCode)
        assertTrue(all.getValue("적금").isSavings)
        assertTrue(all.getValue("적금 해지").isSavings)
        assertEquals("FOOD", all.getValue("점심").categoryCode)
        assertNull(all.getValue("분류 없음").categoryCode)
        assertEquals(listOf("적금", "적금 해지", "청약"), all.values.filter { it.isSavings }.mapNotNull { it.merchant }.sorted())
    }
}
