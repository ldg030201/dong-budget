package com.dong.budget.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dong.budget.data.db.BudgetDatabase
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.DEFAULT_CATEGORIES
import com.dong.budget.data.db.DEFAULT_INCOME_SOURCES
import com.dong.budget.data.db.DEFAULT_PAYMENT_METHODS
import com.dong.budget.data.db.FIXED_CATEGORY_CODE
import com.dong.budget.data.db.FixedMerchantEntity
import com.dong.budget.data.db.FixedMerchantState
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionEntity
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.db.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * 백업을 되살린 뒤 들어온 곳 정리(settleIncomeSources)와 고정지출 목록 등록(registerShownFixedMerchants)이 복원 거래 안에서
 * 실제 SQLite 로 DB 5 → 6 · 6 → 7 과 같게 도는지 본다. 데이터 초기화가 고정지출 목록을 비우는지도 본다.
 * 기기에서 돈다. 메모리 DB 를 써서 기기에 있는 실제 가계부(dong-budget.db)는 건드리지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private lateinit var database: BudgetDatabase
    private lateinit var repository: BackupRepository

    private val categories = DEFAULT_CATEGORIES.mapIndexed { i, c -> c.toEntity().copy(id = i + 1L) }
    private val hana = PaymentMethodEntity(id = 201, uuid = "user:hana", name = "하나카드", type = PaymentMethodType.OTHER)
    private val expenseMethods = DEFAULT_PAYMENT_METHODS.mapIndexed { i, m -> m.toEntity().copy(id = i + 101L) } + hana

    private fun method(name: String) = expenseMethods.first { it.name == name }

    private fun tx(uuid: String, type: TransactionType, payment: PaymentMethodEntity?): TransactionEntity {
        val at = Instant.parse("2026-09-25T03:00:00Z")
        return TransactionEntity(
            id = uuid.hashCode().toLong(),
            uuid = uuid,
            type = type,
            amount = 10_000,
            occurredAt = at,
            occurredDate = 20250925,
            paymentMethodId = payment?.id,
            merchant = uuid,
            createdAt = at,
            updatedAt = at,
        )
    }

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, BudgetDatabase::class.java).build()
        repository = BackupRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `1_8_1 까지 만든 백업을 되살리면 기본 들어온 곳이 생기고 현금·계좌이체로 적은 수입이 그리로 간다`() = runBlocking {
        val transactions =
            listOf(
                tx("e-account", TransactionType.EXPENSE, method("계좌이체")),
                tx("i-cash", TransactionType.INCOME, method("현금")),
                tx("i-account", TransactionType.INCOME, method("계좌이체")),
                tx("i-check", TransactionType.INCOME, method("체크카드")),
                tx("i-hana", TransactionType.INCOME, hana),
                tx("i-none", TransactionType.INCOME, null),
            )
        // 판 1 백업에는 목록 칸이 없다. 읽으면 모두 결제수단(지출)이다.
        val old = backupOf(categories, expenseMethods, transactions, appVersion = "1.8.1", exportedAt = Instant.now()).copy(format = 1)

        repository.restore(old)

        val methods = database.backupDao().paymentMethods()
        assertEquals(expenseMethods.size, methods.count { it.scope == CategoryScope.EXPENSE })
        assertEquals(
            DEFAULT_INCOME_SOURCES.map {
                it.name
            }.sorted(),
            methods.filter { it.scope == CategoryScope.INCOME }.map { it.name }.sorted(),
        )
        val byId = methods.associateBy { it.id }
        val restored = database.backupDao().transactions().associate { it.uuid to it.paymentMethodId?.let(byId::getValue) }
        assertEquals(CategoryScope.EXPENSE to "계좌이체", restored.getValue("e-account")?.let { it.scope to it.name })
        assertEquals(CategoryScope.INCOME to "현금", restored.getValue("i-cash")?.let { it.scope to it.name })
        assertEquals(CategoryScope.INCOME to "통장", restored.getValue("i-account")?.let { it.scope to it.name })
        assertEquals(CategoryScope.EXPENSE to "체크카드", restored.getValue("i-check")?.let { it.scope to it.name })
        assertEquals(CategoryScope.EXPENSE to "하나카드", restored.getValue("i-hana")?.let { it.scope to it.name })
        assertNull(restored.getValue("i-none"))
    }

    @Test
    fun `새 백업은 사용자가 지운 들어온 곳을 되살리지 않는다`() = runBlocking {
        // 들어온 곳 '통장' 을 지운 뒤 만든 백업
        val cashOnly = DEFAULT_INCOME_SOURCES.filter { it.name == "현금" }.map { it.toEntity().copy(id = 301) }
        val backup = backupOf(categories, expenseMethods + cashOnly, emptyList(), appVersion = "1.9.0", exportedAt = Instant.now())
        assertEquals(BACKUP_FORMAT, backup.format)

        repository.restore(backup)

        assertEquals(listOf("현금"), database.backupDao().paymentMethods().filter { it.scope == CategoryScope.INCOME }.map { it.name })
    }

    private val fixed = categories.first { it.code == FIXED_CATEGORY_CODE }
    private val food = categories.first { it.code == "FOOD" }

    /** [at] (UTC)에 [merchant] 에서 낸 지출. 분류는 [category] 다. */
    private fun spend(uuid: String, merchant: String, at: String, category: Long = fixed.id): TransactionEntity {
        val instant = Instant.parse(at)
        return TransactionEntity(
            id = uuid.hashCode().toLong(),
            uuid = uuid,
            type = TransactionType.EXPENSE,
            amount = 17_000,
            occurredAt = instant,
            occurredDate = 20260101,
            categoryId = category,
            merchant = merchant,
            createdAt = instant,
            updatedAt = instant,
        )
    }

    private val spends =
        listOf(
            spend("f1", "넷플릭스", "2026-10-05T03:00:00Z"),
            spend("f2", " 넷 플릭스", "2026-09-05T03:00:00Z"),
            spend("f3", "헬스장", "2025-01-10T03:00:00Z"),
            // 탭이 2026년 10월을 볼 때 읽는 27달(2024년 7월부터) 앞
            spend("f4", "오래된 구독", "2024-06-10T03:00:00Z"),
            spend("e1", "스타벅스", "2026-10-05T03:00:00Z", category = food.id),
        )

    @Test
    fun `고정지출 목록이 없는 옛 백업을 되살리면 그날 탭에 보였을 곳을 등록한다`() = runBlocking {
        val now = Instant.parse("2026-10-16T03:00:00Z")
        val old = backupOf(categories, expenseMethods, spends, appVersion = "1.9.0", exportedAt = now).copy(fixedMerchants = null)

        repository.restore(old, now)

        assertEquals(
            listOf(
                FixedMerchantEntity("넷플릭스", FixedMerchantState.REGISTERED, now),
                FixedMerchantEntity("헬스장", FixedMerchantState.REGISTERED, now),
            ),
            database.backupDao().fixedMerchants(),
        )
    }

    @Test
    fun `새 백업은 고정지출 목록을 그대로 되살리고 보이던 곳을 더 등록하지 않는다`() = runBlocking {
        val decided = Instant.parse("2026-10-01T00:00:00Z")
        val list = listOf(FixedMerchantEntity("헬스장", FixedMerchantState.DECLINED, decided))
        val backup = backupOf(categories, expenseMethods, spends, "2.0.0", Instant.parse("2026-10-16T03:00:00Z"), list)

        repository.restore(backup)

        assertEquals(list, database.backupDao().fixedMerchants())
        // 다시 내보내면 같은 목록이다
        assertEquals(backup.fixedMerchants, repository.export("2.0.0").fixedMerchants)
    }

    @Test
    fun `데이터 초기화는 고정지출 목록도 비운다`() = runBlocking {
        repository.restore(backupOf(categories, expenseMethods, spends, "2.0.0", Instant.now()).copy(fixedMerchants = null))
        database.fixedMerchantDao().upsert(FixedMerchantEntity("유튜브", FixedMerchantState.DECLINED, Instant.now()))

        repository.resetToDefaults()

        assertEquals(emptyList<FixedMerchantEntity>(), database.backupDao().fixedMerchants())
    }
}
