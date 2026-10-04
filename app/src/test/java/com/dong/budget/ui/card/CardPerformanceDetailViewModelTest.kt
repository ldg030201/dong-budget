package com.dong.budget.ui.card

import androidx.lifecycle.viewModelScope
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.PaymentMethodDao
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionDao
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.navigation.CardPerformanceDetailKey
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger

/**
 * 상세 화면 모델을 가짜 DAO 로 돌려 본다. 기기 없이 돌도록 Main 디스패처가 없으면 화면 모델이 기본 풀에서 돈다.
 * 값은 uiState.value 를 들여다보기만 해서(구독하지 않고) 그리는 화면이 없을 때와 같게 본다.
 */
class CardPerformanceDetailViewModelTest {
    // 10월 3일 낮(서울)
    private val clock = Clock.fixed(Instant.parse("2026-10-03T03:00:00Z"), BudgetTime.ZONE)
    private val card =
        MutableStateFlow<PaymentMethodEntity?>(
            PaymentMethodEntity(id = 2, uuid = "u2", name = "하나카드", type = PaymentMethodType.OTHER, performanceTiers = "300000"),
        )
    private val firstUseReads = AtomicInteger()
    private val viewModel =
        CardPerformanceDetailViewModel(
            PaymentMethodRepository(fakeDao(PaymentMethodDao::class.java) { name, _ -> if (name == "observeById") card else null }),
            TransactionRepository(
                fakeDao(TransactionDao::class.java) { name, _ ->
                    when (name) {
                        "observeByPaymentMethod" -> flowOf(emptyList<TransactionListItem>())

                        "observeFirstUseOf" ->
                            flow<Instant?> {
                                firstUseReads.incrementAndGet()
                                emit(null)
                            }

                        else -> null
                    }
                },
            ),
            CardPerformanceDetailKey(2),
            clock,
        )

    @After
    fun clear() = viewModel.viewModelScope.cancel()

    @Test
    fun `기간을 넘기거나 실적을 고쳐도 처음 쓴 날은 다시 읽지 않는다`() {
        // 전에는 기간을 넘길 때마다 모든 결제수단의 첫 사용일을 묶어 세는 조회를 새로 구독했다
        viewModel.uiState.await { it.loaded }
        repeat(3) { viewModel.showPreviousPeriod() }
        viewModel.uiState.await { it.period?.month == YearMonth.of(2026, 7) }
        card.value = card.value?.copy(performanceTiers = "300000,700000")
        viewModel.uiState.await { it.tiers.size == 2 }
        assertEquals(1, firstUseReads.get())
    }

    @Test
    fun `지난 기간을 고른 뒤 시작일을 바꿔 그 기간이 이번 기간이 되면 다시 이번 기간을 따라간다`() {
        viewModel.uiState.await { it.loaded }
        viewModel.showPreviousPeriod()
        viewModel.uiState.await { it.period?.month == YearMonth.of(2026, 9) }
        // 시작일을 15일로 바꾸면 10월 3일은 9월 실적 기간이라 고른 9월이 이번 기간이 된다
        card.value = card.value?.copy(performanceStartDay = 15)
        viewModel.uiState.await { it.currentMonth == YearMonth.of(2026, 9) }
        // 다시 1일로 돌리면 이번 기간 10월을 보여 준다. 전에는 고른 9월이 남아 지난 기간 9월이 나왔다
        card.value = card.value?.copy(performanceStartDay = 1)
        val state = viewModel.uiState.await { it.currentMonth == YearMonth.of(2026, 10) }
        assertEquals(YearMonth.of(2026, 10), state.period?.month)
    }

    @Test
    fun `그리는 화면이 없어도 실적을 고치면 바로 새 값을 받는다`() {
        // 편집 화면이 위에 떠 있는 동안 상세는 구독되지 않는다. 전에는 5초 뒤 멈춰 돌아왔을 때 옛 값을 먼저 그렸다
        viewModel.uiState.await { it.loaded }
        card.value = card.value?.copy(performanceStartDay = 15)
        assertEquals(YearMonth.of(2026, 9), viewModel.uiState.await { it.currentMonth == YearMonth.of(2026, 9) }.period?.month)
    }
}
