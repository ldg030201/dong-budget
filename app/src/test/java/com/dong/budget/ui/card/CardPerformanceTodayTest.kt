package com.dong.budget.ui.card

import androidx.lifecycle.ViewModel
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
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.navigation.CardPerformanceDetailKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * 폰이 깊이 잠든 사이 자정이 지난 경우. 자정까지 기다리는 delay 는 깊은 잠 동안 멈춰서, 벽시계만 넘기고 시간은 흘려보내지 않는 것으로 흉내 낸다.
 * 화면 모델은 사는 동안 계속 구독하므로, 화면이 다시 구독할 때 오늘을 새로 읽어야 한다.
 */
class CardPerformanceTodayTest {
    // 10월 31일 밤 11시(서울). 자정까지 1시간 남았다.
    private val clock = MovableClock(Instant.parse("2026-10-31T14:00:00Z"))
    private val card = PaymentMethodEntity(id = 2, uuid = "u2", name = "하나카드", type = PaymentMethodType.OTHER, performanceTiers = "300000")
    private val screen = CoroutineScope(Job() + Dispatchers.Default)
    private val viewModels = mutableListOf<ViewModel>()

    @After
    fun clear() {
        screen.cancel()
        viewModels.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun `탭은 깊이 잠든 사이 날이 바뀌면 화면이 다시 구독할 때 오늘을 새로 읽는다`() {
        val viewModel =
            CardPerformanceViewModel(
                PaymentMethodRepository(
                    fakeDao(PaymentMethodDao::class.java) { name, _ ->
                        if (name ==
                            "observeAll"
                        ) {
                            flowOf(listOf(card))
                        } else {
                            null
                        }
                    },
                ),
                TransactionRepository(
                    fakeDao(TransactionDao::class.java) { name, _ ->
                        when (name) {
                            "observeCardSpending" -> flowOf(emptyList<CardSpendRow>())
                            "observeFirstUseByPaymentMethod" -> flowOf(emptyList<PaymentMethodFirstUse>())
                            else -> null
                        }
                    },
                ),
                clock,
            ).also { viewModels += it }
        showThenHide(viewModel.uiState)
        val before = viewModel.uiState.await { it.loaded }
        assertEquals(LocalDate.of(2026, 10, 31), before.today)
        assertEquals(1, before.tracked.single().daysLeft)
        // 다음 날 아침 9시 30분에 앱으로 돌아온다. 전에는 계속 구독 중이라 오늘을 다시 읽지 않아 10월 31일에 머물렀다.
        clock.now = Instant.parse("2026-11-01T00:30:00Z")
        show(viewModel.uiState)
        val after = viewModel.uiState.await { it.today == LocalDate.of(2026, 11, 1) }
        assertEquals(YearMonth.of(2026, 11), after.tracked.single().period.month)
        assertEquals(30, after.tracked.single().daysLeft)
    }

    @Test
    fun `상세도 깊이 잠든 사이 날이 바뀌면 화면이 다시 구독할 때 이번 기간을 새로 잡는다`() {
        val viewModel =
            CardPerformanceDetailViewModel(
                PaymentMethodRepository(
                    fakeDao(PaymentMethodDao::class.java) { name, _ ->
                        if (name ==
                            "observeById"
                        ) {
                            flowOf(card)
                        } else {
                            null
                        }
                    },
                ),
                TransactionRepository(
                    fakeDao(TransactionDao::class.java) { name, _ ->
                        when (name) {
                            "observeByPaymentMethod" -> flowOf(emptyList<TransactionListItem>())
                            "observeFirstUseOf" -> flowOf<Instant?>(null)
                            else -> null
                        }
                    },
                ),
                CardPerformanceDetailKey(card.id),
                clock,
            ).also { viewModels += it }
        showThenHide(viewModel.uiState)
        assertEquals(YearMonth.of(2026, 10), viewModel.uiState.await { it.loaded }.currentMonth)
        clock.now = Instant.parse("2026-11-01T00:30:00Z")
        show(viewModel.uiState)
        val after = viewModel.uiState.await { it.currentMonth == YearMonth.of(2026, 11) }
        assertEquals(YearMonth.of(2026, 11), after.period?.month)
    }

    @Test
    fun `같은 날 다시 보이면 오늘을 다시 읽어도 거래를 다시 읽지 않는다`() {
        val reads = AtomicInteger()
        val viewModel =
            CardPerformanceViewModel(
                PaymentMethodRepository(
                    fakeDao(PaymentMethodDao::class.java) { name, _ ->
                        if (name ==
                            "observeAll"
                        ) {
                            flowOf(listOf(card))
                        } else {
                            null
                        }
                    },
                ),
                TransactionRepository(
                    fakeDao(TransactionDao::class.java) { name, _ ->
                        when (name) {
                            "observeCardSpending" -> flowOf(emptyList<CardSpendRow>()).onStart { reads.incrementAndGet() }
                            "observeFirstUseByPaymentMethod" -> flowOf(emptyList<PaymentMethodFirstUse>())
                            else -> null
                        }
                    },
                ),
                clock,
            ).also { viewModels += it }
        viewModel.uiState.await { it.loaded }
        // 몇 시간 뒤에도 같은 날이면 다시 보일 때 시계는 다시 읽지만 같은 날이라 범위도 계산도 그대로다
        clock.now = Instant.parse("2026-10-31T14:50:00Z")
        repeat(3) { showThenHide(viewModel.uiState) }
        Thread.sleep(50)
        assertEquals(1, reads.get())
        assertEquals(LocalDate.of(2026, 10, 31), viewModel.uiState.value.today)
    }

    /**
     * 화면이 그리는 동안처럼 구독했다가 다른 화면으로 가려진 것처럼 끊는다.
     * 구독이 다 끊긴 것(구독 수 0)을 화면 모델이 본 뒤에 돌아온다. 구독 수는 마지막 값만 남는 StateFlow 라,
     * 끊자마자 다시 구독하면 1 → 0 → 1 이 1 → 1 로 보여 다시 보인 것을 모른다(실제 화면은 가려진 채 한동안 있다).
     */
    private fun <T> showThenHide(state: StateFlow<T>) {
        val job = show(state)
        Thread.sleep(50)
        runBlocking { job.cancelAndJoin() }
        Thread.sleep(50)
    }

    /** 화면이 그리는 동안처럼 구독한다 */
    private fun <T> show(state: StateFlow<T>): Job = screen.launch { state.collect {} }
}

/** 벽시계만 옮길 수 있는 시계. 코루틴의 delay 는 실제 시간으로 흐르니 넘겨도 깨지 않는다(깊은 잠과 같다). */
internal class MovableClock(@Volatile var now: Instant) : Clock() {
    override fun getZone(): ZoneId = BudgetTime.ZONE

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}
