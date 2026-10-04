package com.dong.budget.ui.editor

import com.dong.budget.data.MAX_AMOUNT_DIGITS
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.FIXED_CATEGORY_CODE
import com.dong.budget.navigation.EditorPrefill
import com.dong.budget.navigation.PrefillSource
import com.dong.budget.ui.fixed.FixedExpenseItem
import java.time.LocalDate
import java.time.YearMonth

/**
 * 고정지출 탭 '등록하기' 의 등록창 값. 지난번에 낸 대로 채우고, 사용자가 확인하고 저장해야 거래가 된다.
 *
 * - 금액은 지난번에 낸 달의 금액, 가게는 지난번 이름, 결제수단은 지난번 결제수단(번호로 고른다. 그사이 지웠으면 비워 둔다).
 * - 분류는 '고정지출' 을 코드로 고른다. 같은 가게 분류 짐작은 끈다(두 고르기가 겨루면 어느 쪽이 들어갈지 정해지지 않는다).
 *   없는 카드 새로 만들기도 끈다. 이름이 아니라 번호로 고르므로 만들 카드가 없다.
 * - 날짜는 이번 달 평소 날짜인데, 아직 오지 않았으면 오늘이다(앞날로 적힌 거래가 되지 않게). 시각은 지난번 거래의 시각이다.
 *   누른 때를 쓰면 누를 때마다 값이 달라져 같은 등록창이 두 번 쌓인다. 같은 날 몇 번을 눌러도 같은 값이 나온다.
 *   평소 날짜가 쉬는 날이어도 낼 날(다음 영업일, [FixedExpenseItem.dueDateIn])로 밀지 않는다. 카드 결제 · 구독은 쉬는 날에도 그날 나가고
 *   은행 자동이체만 밀리는데 기록으로는 어느 쪽인지 모른다. '지났어요' 는 늦게 알려도 덜 성가셔 낼 날로 세지만, 적는 날짜는 평소 날짜 그대로 두고
 *   사용자가 등록창에서 고친다.
 * - 같은 결제를 막을 열쇠는 두지 않는다. 한 달에 두 번 내는 것(밀린 달 몫 등)도 등록할 수 있어야 하고,
 *   냈는지는 열쇠가 아니라 그 달 '고정지출' 분류 지출로 가린다.
 */
fun fixedExpensePrefill(item: FixedExpenseItem, today: LocalDate): EditorPrefill {
    val date = minOf(today, item.usualDateIn(YearMonth.from(today)))
    val time = BudgetTime.toLocalTime(item.latestAt)
    return EditorPrefill(
        amount = item.amount.coerceIn(0, MAX_AMOUNT),
        merchant = item.merchant.orEmpty(),
        paymentName = null,
        memo = null,
        occurredAtMillis = date.atTime(time).atZone(BudgetTime.ZONE).toInstant().toEpochMilli(),
        dedupKey = null,
        guessCategory = false,
        addMissingCard = false,
        source = PrefillSource.FIXED_EXPENSE,
        categoryCode = FIXED_CATEGORY_CODE,
        paymentMethodId = item.paymentMethodId,
    )
}

/** 등록창이 받는 가장 큰 금액(12자리). 한 달에 여러 번 낸 합이 넘을 수 있다. */
private val MAX_AMOUNT = "9".repeat(MAX_AMOUNT_DIGITS).toLong()
