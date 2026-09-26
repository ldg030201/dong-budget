package com.dong.budget.testing

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

private val nextId = AtomicLong(1)

/**
 * 테스트용 거래 한 건.
 *
 * @param at 서울 시각 "2026-09-03T12:30". 날짜만 주면("2026-09-03") 낮 12시로 본다.
 * @param categoryId 분류 id. 이름을 따로 주지 않으면 "분류{id}", 색은 id 로 돌려 고른다.
 * @param paymentId 결제수단 id. 이름을 따로 주지 않으면 "결제{id}" 다.
 * @param id 주지 않으면 만든 순서대로 커진다. 같은 시각이면 나중에 만든 것이 '나중에 적은' 거래다.
 */
fun tx(
    at: String,
    amount: Long,
    type: TransactionType = TransactionType.EXPENSE,
    categoryId: Long? = null,
    paymentId: Long? = null,
    merchant: String? = null,
    categoryName: String? = categoryId?.let { "분류$it" },
    paymentName: String? = paymentId?.let { "결제$it" },
    id: Long = nextId.getAndIncrement(),
): TransactionListItem {
    val instant = LocalDateTime.parse(if (at.length == DATE_ONLY) "${at}T12:00" else at).atZone(BudgetTime.ZONE).toInstant()
    return TransactionListItem(
        id = id,
        type = type,
        amount = amount,
        occurredAt = instant,
        occurredDate = BudgetTime.toDateKey(instant),
        merchant = merchant,
        memo = null,
        categoryName = categoryName,
        categoryIcon = categoryId?.let { CategoryStyle.ICONS[(it % CategoryStyle.ICONS.size).toInt()] },
        categoryColor = categoryId?.let { colorOf(it) },
        paymentMethodName = paymentName,
        categoryId = categoryId,
        paymentMethodId = paymentId,
        paymentMethodIcon = paymentId?.let { "credit_card" },
        paymentMethodColor = paymentId?.let { colorOf(it) },
    )
}

/** 테스트 거래의 분류·결제수단 색. id 로 돌려 고른다. */
fun colorOf(id: Long): String = CategoryStyle.COLORS[(id % CategoryStyle.COLORS.size).toInt()]

/** "2026-09-03" → LocalDate */
fun day(text: String): LocalDate = LocalDate.parse(text)

private const val DATE_ONLY = 10
