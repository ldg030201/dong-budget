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
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth

/**
 * 저축 화면 모델이 '저축' 분류 거래를 읽어 고른 달로 계산하는지 가짜 DAO 로 본다. 기기 없이 돌도록 화면 모델을 기본 풀
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
        )

    private fun open(): SavingsViewModel {
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
        assertEquals(500_000L, model.uiState.value.history.total)

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
    fun `저축 분류를 지우면 분류가 없다고 알린다`() {
        val model = open()
        categories.value = emptyList()
        assertFalse(model.uiState.await { !it.hasCategory }.hasCategory)
    }
}
