package com.dong.budget.ui.format

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KOREA = Locale.KOREA
private val dayFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", KOREA)
private val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", KOREA)

/** 12345 -> "12,345" */
fun formatAmount(amount: Long): String = String.format(KOREA, "%,d", amount)

/**
 * 금액에 부호를 붙인다.
 *
 * 지출에는 부호를 붙이지 않는다. 가계부는 지출 항목이 압도적으로 많아서
 * 전부 마이너스를 달면 화면만 지저분해지고 구분에 도움이 안 된다.
 */
fun formatSignedAmount(type: TransactionType, amount: Long): String = when (type) {
    TransactionType.INCOME, TransactionType.REFUND -> "+${formatAmount(amount)}원"
    TransactionType.EXPENSE, TransactionType.TRANSFER -> "${formatAmount(amount)}원"
}

fun formatDay(instant: Instant): String = dayFormatter.format(instant.atZone(BudgetTime.ZONE))

fun formatTime(instant: Instant): String = timeFormatter.format(instant.atZone(BudgetTime.ZONE))

fun formatMonth(month: YearMonth): String = "${month.year}년 ${month.monthValue}월"
