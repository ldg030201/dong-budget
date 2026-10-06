package com.dong.budget.ui.history

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import com.dong.budget.ui.home.DayGroup
import com.dong.budget.ui.home.Totals
import com.dong.budget.ui.home.groupByDay
import com.dong.budget.ui.home.totals
import java.time.LocalDate
import java.time.YearMonth

// ─────────────────────────────────────────────────────────────────────
// 내역 화면에 보여줄 목록을 모든 거래와 검색어로 만든다.
// 화면과 떼어 둔 이유는 단위 테스트로 검색 규칙을 확인하기 위해서다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 내역 화면의 상태.
 * @property query 검색어. 비었으면 모든 거래를 보여 준다.
 * @property months 달마다 묶은 거래(최근 달부터). 달 안은 날짜별로 묶는다.
 * @property count 보여 주는 거래 수(검색했으면 맞은 수)
 * @property totals 보여 주는 거래의 지출·수입 합. 검색 결과 줄에 적는다.
 * @property hasAny 거래가 하나라도 있는지. 없으면 '아직 거래가 없어요', 있는데 검색에 안 맞으면 '맞는 내역이 없어요'
 */
data class HistoryUiState(
    val loaded: Boolean,
    val query: String,
    val months: List<HistoryMonth>,
    val count: Int,
    val totals: Totals,
    val hasAny: Boolean,
    val today: LocalDate,
) {
    val searching: Boolean get() = query.isNotBlank()

    companion object {
        /** 첫 조회 전. 빈 화면 안내가 잠깐 비치지 않게 아무것도 그리지 않는다. */
        fun loading(query: String, today: LocalDate): HistoryUiState =
            HistoryUiState(loaded = false, query = query, months = emptyList(), count = 0, totals = Totals(), hasAny = false, today = today)
    }
}

/** 내역의 한 달. 달 머리에 그 달의 지출·수입 합을 적는다. */
data class HistoryMonth(val month: YearMonth, val days: List<DayGroup>, val totals: Totals)

/** [items](모든 거래) 중 [query] 에 맞는 것을 달·날짜로 묶는다 */
fun buildHistory(items: List<TransactionListItem>, query: String, today: LocalDate): HistoryUiState {
    val terms = searchTerms(query)
    val matched = items.filter { it.matches(terms) }
    val months =
        groupByDay(matched)
            .groupBy { YearMonth.from(it.date) }
            .map { (month, days) -> HistoryMonth(month, days, days.flatMap { it.items }.totals()) }
    return HistoryUiState(
        loaded = true,
        query = query,
        months = months,
        count = matched.size,
        totals = matched.totals(),
        hasAny = items.isNotEmpty(),
        today = today,
    )
}

/** 검색어를 빈칸으로 나눈 낱말들(작은 글자로). 모든 낱말이 한 거래 안 어딘가에 들어 있어야 맞는다. */
internal fun searchTerms(query: String): List<String> = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }

/**
 * 거래가 검색 낱말([terms])에 모두 맞는지. 낱말마다 가게·메모·분류·결제수단·거래 종류(지출·수입·이체·환불) 중 한 곳에 들어 있으면 된다.
 * 숫자만 있는 낱말('45,000', '45000원' 처럼 쉼표와 '원' 은 빼고 본다)은 금액에 들어 있어도 맞는다.
 */
internal fun TransactionListItem.matches(terms: List<String>): Boolean {
    if (terms.isEmpty()) return true
    val texts = listOfNotNull(merchant, memo, categoryName, paymentMethodName, type.word).map { it.lowercase() }
    val amountText = amount.toString()
    return terms.all { term ->
        texts.any { term in it } || term.amountDigits()?.let { it in amountText } == true
    }
}

/** 금액으로 찾는 낱말이면 숫자만, 아니면 null. '3월' 처럼 숫자 밖의 글자가 섞이면 금액으로 보지 않는다. */
private fun String.amountDigits(): String? = removeSuffix("원").replace(",", "").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

/** 거래 종류를 가리키는 말. '환불' 로 찾으면 환불 거래가 나온다. */
private val TransactionType.word: String
    get() = when (this) {
        TransactionType.EXPENSE -> "지출"
        TransactionType.INCOME -> "수입"
        TransactionType.TRANSFER -> "이체"
        TransactionType.REFUND -> "환불"
    }

private val WHITESPACE = Regex("\\s+")
