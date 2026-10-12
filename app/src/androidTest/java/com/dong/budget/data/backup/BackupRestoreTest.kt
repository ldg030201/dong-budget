package com.dong.budget.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dong.budget.data.db.BudgetDatabase
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.DEFAULT_CATEGORIES
import com.dong.budget.data.db.DEFAULT_INCOME_SOURCES
import com.dong.budget.data.db.DEFAULT_PAYMENT_METHODS
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
 * 백업을 되살린 뒤 들어온 곳 정리(BackupDao.settleIncomeSources)가 실제 SQLite 에서 DB 5 → 6 과 같게 도는지 본다.
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
}
