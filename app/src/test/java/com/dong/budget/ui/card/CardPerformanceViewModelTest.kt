package com.dong.budget.ui.card

import androidx.lifecycle.viewModelScope
import com.dong.budget.data.PaymentMethodRepository
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CardSpendRow
import com.dong.budget.data.db.PaymentMethodDao
import com.dong.budget.data.db.PaymentMethodEntity
import com.dong.budget.data.db.PaymentMethodFirstUse
import com.dong.budget.data.db.PaymentMethodType
import com.dong.budget.data.db.TransactionDao
import com.dong.budget.data.db.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth

class CardPerformanceViewModelTest {
    @Test
    fun `그리는 화면이 없어도 화면 모델이 사는 동안 새 값을 받는다`() {
        // 전에는 그리는 동안만 구독해서(5초 유예) 편집 화면에 오래 있다 돌아오면 옛 시작일로 센 기간을 먼저 그렸다
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val startDay = MutableStateFlow(1)
        val state = startDay.map { "매달 ${it}일" }.stateWhileAlive(scope, "읽는 중")
        assertEquals("매달 1일", state.value)
        startDay.value = 15
        assertEquals("매달 15일", state.value)
        scope.cancel()
    }

    @Test
    fun `탭은 카드별 지출과 환불만 시작일에 맞춘 달 범위로 읽고 실적을 고칠 때마다 다시 읽지 않는다`() {
        // 전에는 넉 달치 모든 거래를 분류·결제수단 이름까지 붙여 읽었다
        val methods =
            MutableStateFlow(
                listOf(
                    PaymentMethodEntity(id = 2, uuid = "u2", name = "하나카드", type = PaymentMethodType.OTHER, performanceTiers = "300000"),
                ),
            )
        val reads = mutableListOf<Pair<Instant, Instant>>()
        val rows = listOf(CardSpendRow(2, TransactionType.EXPENSE, 120_000, Instant.parse("2026-10-02T03:00:00Z")))
        val transactions =
            fakeDao(TransactionDao::class.java) { name, args ->
                when (name) {
                    "observeCardSpending" -> {
                        synchronized(reads) { reads += args[0] as Instant to args[1] as Instant }
                        flowOf(rows)
                    }

                    "observeFirstUseByPaymentMethod" -> flowOf(emptyList<PaymentMethodFirstUse>())

                    else -> null
                }
            }
        val viewModel =
            CardPerformanceViewModel(
                PaymentMethodRepository(fakeDao(PaymentMethodDao::class.java) { name, _ -> if (name == "observeAll") methods else null }),
                TransactionRepository(transactions),
                Clock.fixed(Instant.parse("2026-10-03T03:00:00Z"), BudgetTime.ZONE),
            )
        assertEquals(120_000L, viewModel.uiState.await { it.loaded }.tracked.single().progress.spent)
        // 실적 금액만 바꾸면 읽는 범위가 그대로라 다시 읽지 않는다
        methods.value = listOf(methods.value.single().copy(performanceTiers = "300000,700000"))
        viewModel.uiState.await { it.tracked.single().progress.tiers.size == 2 }
        // 시작일이 1일인 실적 카드는 지난달(9월) 1일부터 이번 달 말일까지만 읽는다
        val september = BudgetTime.monthRange(YearMonth.of(2026, 9)).first
        val november = BudgetTime.monthRange(YearMonth.of(2026, 10)).second
        assertEquals(listOf(september to november), synchronized(reads) { reads.toList() })
        viewModel.viewModelScope.cancel()
    }
}
