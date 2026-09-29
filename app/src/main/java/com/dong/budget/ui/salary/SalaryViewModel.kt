package com.dong.budget.ui.salary

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.SALARY_CATEGORY_CODE
import com.dong.budget.data.salary.SalaryRepository
import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.data.salary.salaryKey
import com.dong.budget.ui.home.totals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 월급 탭이 그릴 느린 값. 초마다 바뀌는 번 돈은 화면이 이 설정과 지금 시각으로 그때그때 계산한다(뷰모델이 매초 내보내면 탭 전체가 매초 다시 그려진다).
 * @property loaded 저장된 설정을 읽었는지. 읽기 전에는 '월급을 정해 주세요' 가 잠깐 비치지 않게 아무것도 그리지 않는다.
 * @property spentToday 오늘 쓴 돈(지출 − 환불). 환불이 더 많으면 음수다.
 * @property registerMonth 월급날이 막 지났는데(일주일 안) 아직 등록하지 않은 달. 있으면 '월급 등록하기' 를 보여 준다.
 */
@Immutable
data class SalaryUiState(
    val loaded: Boolean = false,
    val settings: SalarySettings = SalarySettings(),
    val today: LocalDate,
    val spentToday: Long = 0,
    val registerMonth: YearMonth? = null,
)

/** 월급 탭. 설정과 오늘 쓴 돈, 이번 달 월급을 등록했는지를 따라간다. 날이 바뀌면 오늘을 새로 잡는다. */
class SalaryViewModel(
    salaryRepository: SalaryRepository,
    private val transactions: TransactionRepository,
    clock: Clock = Clock.system(BudgetTime.ZONE),
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SalaryUiState> =
        combine(salaryRepository.settings, BudgetTime.today(clock)) { settings, today -> settings to today }
            .distinctUntilChanged()
            .flatMapLatest { (settings, today) ->
                combine(
                    transactions.observeDay(today).map { it.totals().expense },
                    registerMonth(settings, today),
                ) { spent, month ->
                    SalaryUiState(loaded = true, settings = settings, today = today, spentToday = spent, registerMonth = month)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SalaryUiState(today = BudgetTime.toLocalDate(clock.instant())),
            )

    /** 월급날부터 일주일 동안, 그달 월급을 아직 등록하지 않았으면 그달. 그 뒤로는 조르지 않는다. */
    private fun registerMonth(settings: SalarySettings, today: LocalDate) = if (!settings.isReady) {
        flowOf(null)
    } else {
        val month = settings.latestPayMonth(today)
        if (ChronoUnit.DAYS.between(settings.paydayIn(month), today) >= REGISTER_DAYS) {
            flowOf(null)
        } else {
            val (from, until) = settings.salaryPeriod(month)
            transactions
                .observeSalaryRegistered(salaryKey(month), SALARY_CATEGORY_CODE, from, until)
                .map { registered -> month.takeUnless { registered } }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** 월급날부터 이 날 수 동안 '월급 등록하기' 를 보여 준다 */
        const val REGISTER_DAYS = 7L
    }
}
