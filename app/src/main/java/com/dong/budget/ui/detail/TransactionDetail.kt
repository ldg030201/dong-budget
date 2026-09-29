package com.dong.budget.ui.detail

import androidx.compose.runtime.Immutable
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.ui.home.localDate
import com.dong.budget.ui.salary.workValue
import com.dong.budget.ui.stats.calc.Measure
import com.dong.budget.ui.stats.calc.averageTicket
import com.dong.budget.ui.stats.calc.merchantKey
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 거래 상세 계산. 같은 곳 내역은 통계의 '많이 쓴 곳' 과 같은 규칙으로 묶는다.
// 가게 열쇠(merchantKey: 띄어쓰기·대소문자 무시)가 같고, 지출 쪽(지출·환불)이나 수입 쪽이 같아야 같은 곳 내역이다.
// ─────────────────────────────────────────────────────────────────────

/** 거래 상세 화면의 상태 */
sealed interface TransactionDetailUiState {
    /** 첫 조회 전. 상단 바만 그린다. */
    data object Loading : TransactionDetailUiState

    /** 거래가 없다(지웠다). 화면을 치운다. */
    data object Gone : TransactionDetailUiState

    /**
     * @property samePlace 같은 곳 내역. 가게 이름이 없거나 이체면 null 이라 그 섹션이 없다.
     * @property workTime 이 지출을 벌려면 일해야 하는 시간("약 47분"). 월급을 정하지 않았거나 지출이 아니면 null
     */
    @Immutable
    data class Shown(val item: TransactionListItem, val today: LocalDate, val samePlace: SamePlace?, val workTime: String? = null) :
        TransactionDetailUiState
}

/**
 * 같은 곳에서 최근 1년 동안 쓴(받은) 내역.
 * @property income 수입 쪽인지. 지출 쪽이면 지출과 환불을 모은다.
 * @property amount 모두 합친 금액. 지출 쪽은 순지출(지출 − 환불)이라 음수일 수 있다.
 * @property count 쓴(받은) 횟수. 지출 쪽은 지출만 센다(환불은 횟수에 넣지 않는다, 많이 쓴 곳과 같다).
 * @property averageTicket 한 번에 평균. 지출 쪽에서 지출이 두 번 이상일 때만 있다(한 번이면 금액과 같다).
 * @property months 달별 내역. 최근 달이 먼저고, 달 안에서도 최근 거래가 먼저다. 비었으면 1년 안에 내역이 없다.
 */
@Immutable
data class SamePlace(val income: Boolean, val amount: Long, val count: Int, val averageTicket: Long?, val months: List<SamePlaceMonth>)

/** 같은 곳 내역의 한 달 */
data class SamePlaceMonth(val month: YearMonth, val items: List<TransactionListItem>)

/** 같은 곳 내역을 보는 첫날. 오늘을 넣어 딱 1년이다. 오늘이 2026-09-28 이면 2025-09-29 부터. */
fun samePlaceStart(today: LocalDate): LocalDate = today.minusYears(1).plusDays(1)

/**
 * 거래 상세 상태를 만든다.
 * @param item 보는 거래. 없으면(지웠으면) [TransactionDetailUiState.Gone]
 * @param rows [samePlaceStart] 부터 가게 이름이 있는 거래 전부
 */
fun transactionDetail(
    item: TransactionListItem?,
    rows: List<TransactionListItem>,
    today: LocalDate,
    salary: SalarySettings = SalarySettings(),
): TransactionDetailUiState {
    item ?: return TransactionDetailUiState.Gone
    return TransactionDetailUiState.Shown(
        item = item,
        today = today,
        samePlace = samePlace(item, rows, samePlaceStart(today)),
        workTime = workTimeOf(item, salary),
    )
}

/**
 * 이 지출을 벌려면 일해야 하는 시간. 달마다 다르지 않은 평균 시급으로 센다(같은 금액이 달마다 다르게 보이지 않게).
 * 지출만 센다. 환불·수입·이체를 '일한 값' 으로 말하면 어색하다.
 */
private fun workTimeOf(item: TransactionListItem, salary: SalarySettings): String? {
    if (item.type != TransactionType.EXPENSE) return null
    val seconds = salary.secondsToEarn(item.amount) ?: return null
    return workValue(seconds, salary.workSecondsPerDay)
}

/**
 * [current] 와 같은 곳의 [start] 날부터의 내역. 보고 있는 거래도 1년 안이면 들어간다(화면에서 따로 표시한다).
 * 앞으로의 날짜로 적어 둔 거래도 넣는다.
 * @return 가게 이름이 없거나(공백뿐 포함) 이체라서 같은 곳을 말할 수 없으면 null
 */
fun samePlace(current: TransactionListItem, rows: List<TransactionListItem>, start: LocalDate): SamePlace? {
    val key = merchantKey(current.merchant) ?: return null
    val measure = current.type.measure() ?: return null
    val mine =
        rows
            .filter { measure.includes(it) && merchantKey(it.merchant) == key && !it.localDate().isBefore(start) }
            .sortedWith(compareByDescending<TransactionListItem> { it.occurredAt }.thenByDescending { it.id })
    val income = measure == Measure.INCOME
    val count = mine.count { it.type == if (income) TransactionType.INCOME else TransactionType.EXPENSE }
    return SamePlace(
        income = income,
        amount = measure.amountOf(mine),
        count = count,
        averageTicket = if (income || count < 2) null else averageTicket(mine),
        months = mine.groupBy { YearMonth.from(it.localDate()) }.map { (month, items) -> SamePlaceMonth(month, items) },
    )
}

/** 이 거래가 어느 쪽에 드는지. 지출·환불은 지출 쪽, 수입은 수입 쪽, 이체는 어느 쪽도 아니다(null). */
private fun TransactionType.measure(): Measure? = when (this) {
    TransactionType.EXPENSE, TransactionType.REFUND -> Measure.EXPENSE
    TransactionType.INCOME -> Measure.INCOME
    TransactionType.TRANSFER -> null
}
