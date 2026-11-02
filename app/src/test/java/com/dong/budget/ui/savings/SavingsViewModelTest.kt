package com.dong.budget.ui.savings

import com.dong.budget.data.CategoryRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryDao
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.SAVINGS_CATEGORY_CODE
import com.dong.budget.data.db.TransactionDao
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.tx
import com.dong.budget.ui.card.MovableClock
import com.dong.budget.ui.card.await
import com.dong.budget.ui.card.fakeDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth

/**
 * 저축 화면 모델이 '저축' 분류 거래를 읽어 고른 달(통계) · 고른 해(내역)로 계산하는지 가짜 DAO 로 본다. 기기 없이 돌도록 화면 모델을 기본 풀
 * 범위([scope])에서 돌린다(Main 디스패처가 없다). 분류 표는 [categories] 하나로, 바꾸면 observeByScope 가 따라간다(Room 처럼).
 */
class SavingsViewModelTest {
    // 10월 16일 낮(서울)
    private val clock = Clock.fixed(Instant.parse("2026-10-16T03:00:00Z"), BudgetTime.ZONE)
    private val october = YearMonth.of(2026, 10)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val savingsCategory =
        CategoryEntity(id = 7, uuid = "savings", scope = CategoryScope.EXPENSE, name = "저축", code = SAVINGS_CATEGORY_CODE)
    private val categories = MutableStateFlow(listOf(savingsCategory))
    private val rows =
        listOf(
            tx("2026-10-05", 300_000, categoryId = 7, merchant = "적금", categoryCode = SAVINGS_CATEGORY_CODE),
            tx("2026-09-05", 200_000, categoryId = 7, merchant = "적금", categoryCode = SAVINGS_CATEGORY_CODE),
            tx("2025-12-05", 100_000, categoryId = 7, merchant = "적금", categoryCode = SAVINGS_CATEGORY_CODE),
        )

    private fun open(clock: Clock = this.clock): SavingsViewModel {
        val model =
            SavingsViewModel(
                TransactionRepository(
                    fakeDao(TransactionDao::class.java) { name, args ->
                        if (name == "observeWithCategoryCode" &&
                            args[0] == SAVINGS_CATEGORY_CODE
                        ) {
                            MutableStateFlow<List<TransactionListItem>>(rows)
                        } else {
                            null
                        }
                    },
                ),
                CategoryRepository(
                    fakeDao(CategoryDao::class.java) { name, args ->
                        if (name == "observeByScope" && args[0] == CategoryScope.EXPENSE) categories else null
                    },
                ),
                clock,
                scope,
            )
        // 그리는 동안만 계산한다(그리는 화면처럼 구독한다)
        scope.launch { model.uiState.collect {} }
        model.uiState.await { it.loaded }
        return model
    }

    @After
    fun clear() = scope.cancel()

    @Test
    fun `이번 달부터 보고, 지난 달로 갔다가 이번 달로 돌아온다`() {
        val model = open()
        assertEquals(october, model.uiState.value.month)
        assertEquals(300_000L, model.uiState.value.stats.total)

        model.showPreviousMonth()
        val september = model.uiState.await { it.month == october.minusMonths(1) }
        assertEquals(200_000L, september.stats.total)

        model.showThisMonth()
        model.uiState.await { it.month == october }
    }

    @Test
    fun `이번 달보다 뒤로는 넘기지 않는다`() {
        val model = open()
        model.showNextMonth()
        model.showPreviousMonth()
        // 다음 달로 넘어갔다면 앞으로 한 달 돌아와 이번 달이다. 막았으니 이번 달에서 한 달 앞이다.
        model.uiState.await { it.month == october.minusMonths(1) }
    }

    @Test
    fun `내역은 올해부터 보고, 지난해로 갔다가 올해로 돌아온다`() {
        val model = open()
        val thisYear = model.uiState.value
        assertEquals(2026, thisYear.history.year)
        assertTrue(thisYear.isThisYear)
        assertEquals(500_000L, thisYear.history.total)
        assertEquals(300_000L, thisYear.history.thisMonth)

        model.showPreviousYear()
        val lastYear = model.uiState.await { it.history.year == 2025 }
        assertFalse(lastYear.isThisYear)
        assertEquals(100_000L, lastYear.history.total)
        assertNull(lastYear.history.thisMonth)
        // 해를 넘겨도 통계 탭의 달은 그대로다
        assertEquals(october, lastYear.month)

        model.showPreviousYear()
        assertTrue(model.uiState.await { it.history.year == 2024 }.history.yearIsEmpty)

        model.showThisYear()
        model.uiState.await { it.history.year == 2026 }
    }

    @Test
    fun `올해보다 뒤로는 넘기지 않는다`() {
        val model = open()
        model.showNextYear()
        model.showPreviousYear()
        // 다음 해로 넘어갔다면 한 해 돌아와 올해다. 막았으니 올해에서 한 해 앞이다.
        model.uiState.await { it.history.year == 2025 }
        // 지난해에서는 다음 해로 넘어가 올해로 돌아온다
        model.showNextYear()
        assertTrue(model.uiState.await { it.history.year == 2026 }.isThisYear)
    }

    @Test
    fun `올해를 보는 중에 해가 바뀌면 새해로 넘어간다`() {
        // 12월 31일 자정 0.2초 전(서울). 오늘은 자정마다 다시 읽고, 그 전에는 0.2초마다 다시 읽는다(벽시계를 옮기기 전까지).
        val clock = MovableClock(Instant.parse("2026-12-31T14:59:59.800Z"))
        val model = open(clock)
        assertEquals(2026, model.uiState.value.history.year)

        clock.now = Instant.parse("2027-01-01T00:00:00Z")
        val newYear = model.uiState.await { it.history.year == 2027 }
        assertTrue(newYear.isThisYear)
        // 새해에는 아직 저축이 없지만 지난해까지의 저축이 있어 빈 화면이 아니다
        assertTrue(newYear.history.yearIsEmpty)
        assertTrue(newYear.hasAny)
    }

    @Test
    fun `지난해를 보는 중에 해가 바뀌면 보던 해를 그대로 둔다`() {
        val clock = MovableClock(Instant.parse("2026-12-31T14:59:59.800Z"))
        val model = open(clock)
        model.showPreviousYear()
        model.uiState.await { it.history.year == 2025 }

        clock.now = Instant.parse("2027-01-01T00:00:00Z")
        val newYear = model.uiState.await { it.today.year == 2027 }
        assertEquals(2025, newYear.history.year)
        assertFalse(newYear.isThisYear)
    }

    @Test
    fun `저축 분류를 지우면 분류가 없다고 알린다`() {
        val model = open()
        categories.value = emptyList()
        assertFalse(model.uiState.await { !it.hasCategory }.hasCategory)
    }
}
