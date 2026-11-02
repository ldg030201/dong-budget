package com.dong.budget.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dong.budget.data.db.BudgetDatabase
import com.dong.budget.data.db.FixedKind
import com.dong.budget.data.db.FixedMerchantEntity
import com.dong.budget.data.db.FixedMerchantState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * 고정 목록 저장소가 해지 · 되돌리기 · 해지한 곳에 '안 함' 을 실제 SQLite 로 맞게 적는지, 고정지출과 고정저축이 한 표에서 서로의
 * 줄을 건드리지 않는지 본다. 기기에서 돈다. 메모리 DB 를 써서 기기에 있는 실제 가계부(dong-budget.db)는 건드리지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class FixedMerchantRepositoryTest {
    private lateinit var database: BudgetDatabase
    private lateinit var expenses: FixedMerchantRepository
    private lateinit var savings: FixedMerchantRepository

    private val decided = Instant.parse("2026-09-01T00:00:00Z")
    private val ended = Instant.parse("2026-10-01T00:00:00Z")
    private val later = Instant.parse("2026-11-01T00:00:00Z")

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, BudgetDatabase::class.java).build()
        expenses = FixedMerchantRepository(database.fixedMerchantDao(), FixedKind.EXPENSE)
        savings = FixedMerchantRepository(database.fixedMerchantDao(), FixedKind.SAVINGS)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `해지하면 해지한 때가 정한 때와 같고 다시 등록하면 해지가 풀린다`() = runBlocking {
        expenses.register("넷플릭스", decided)
        expenses.end("넷플릭스", ended)
        assertEquals(
            FixedMerchantEntity("넷플릭스", FixedMerchantState.ENDED, ended, FixedKind.EXPENSE, endedAt = ended),
            expenses.observe().first().getValue("넷플릭스"),
        )

        expenses.register("넷플릭스", later)
        assertEquals(
            FixedMerchantEntity("넷플릭스", FixedMerchantState.REGISTERED, later, FixedKind.EXPENSE),
            expenses.observe().first().getValue("넷플릭스"),
        )
    }

    @Test
    fun `해지한 곳에 다시 물은 것에 안 함을 누르면 정한 때만 바뀌고 해지한 때는 그대로다`() = runBlocking {
        expenses.end("넷플릭스", ended)

        expenses.keepEnded("넷플릭스", later)

        assertEquals(
            FixedMerchantEntity("넷플릭스", FixedMerchantState.ENDED, later, FixedKind.EXPENSE, endedAt = ended),
            expenses.observe().first().getValue("넷플릭스"),
        )
    }

    @Test
    fun `해지한 곳이 아니면 안 함은 등록 안 한 곳으로 둔다`() = runBlocking {
        // 묻는 사이 복원 등으로 해지한 곳이 아니게 됐다(등록했거나 목록에서 사라졌다)
        expenses.register("넷플릭스", decided)

        expenses.keepEnded("넷플릭스", later)
        expenses.keepEnded("헬스장", later)

        assertEquals(
            mapOf(
                "넷플릭스" to FixedMerchantEntity("넷플릭스", FixedMerchantState.DECLINED, later, FixedKind.EXPENSE),
                "헬스장" to FixedMerchantEntity("헬스장", FixedMerchantState.DECLINED, later, FixedKind.EXPENSE),
            ),
            expenses.observe().first(),
        )
    }

    @Test
    fun `고정지출과 고정저축은 같은 가게를 따로 정하고 서로의 줄을 건드리지 않는다`() = runBlocking {
        expenses.register("적금", decided)
        savings.register("적금", decided)

        savings.end("적금", ended)
        expenses.keepEnded("적금", later)

        assertEquals(
            mapOf("적금" to FixedMerchantEntity("적금", FixedMerchantState.DECLINED, later, FixedKind.EXPENSE)),
            expenses.observe().first(),
        )
        assertEquals(
            mapOf("적금" to FixedMerchantEntity("적금", FixedMerchantState.ENDED, ended, FixedKind.SAVINGS, endedAt = ended)),
            savings.observe().first(),
        )
    }
}
