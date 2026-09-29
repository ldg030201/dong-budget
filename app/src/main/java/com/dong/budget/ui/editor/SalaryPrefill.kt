package com.dong.budget.ui.editor

import com.dong.budget.data.MAX_AMOUNT_DIGITS
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.SALARY_CATEGORY_CODE
import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.data.salary.salaryKey
import com.dong.budget.navigation.EditorPrefill
import com.dong.budget.navigation.PrefillSource
import java.time.YearMonth

/**
 * [month] 월급을 수입으로 등록할 등록창의 값. 월급날 알림과 월급 탭이 같이 쓴다.
 *
 * - 금액은 그달 받을 월급([SalarySettings.payFor], 실수령을 적었으면 실수령, 입사한 달은 일할)이다. 실제로 들어온 금액과 다르면 사용자가 고친다.
 * - 날짜는 그달 월급날의 출근 시각이다. 누른 때를 쓰면 누를 때마다 값이 달라져 같은 등록창이 두 번 쌓인다.
 * - 가게 이름은 '월급' 하나로 둔다. 달마다 이름이 다르면 거래 상세의 '최근 내역' 에 지난 월급이 모이지 않는다. 몇 월분인지는 메모에 적는다.
 * - 결제수단은 기본 결제수단 '계좌이체' 를 고른다. 사용자가 지웠으면 새로 만들지 않고 비워 둔다.
 * - 같은 가게 분류 짐작은 끈다. 그 짐작은 지출만 보므로 수입에 지출 분류가 들어갈 수 있다. 분류는 급여를 고른다.
 */
fun salaryPrefill(settings: SalarySettings, month: YearMonth): EditorPrefill = EditorPrefill(
    amount = settings.payFor(month).coerceIn(0, MAX_AMOUNT),
    merchant = SALARY_MERCHANT,
    paymentName = SALARY_PAYMENT,
    memo = "${month.year}년 ${month.monthValue}월분",
    occurredAtMillis = settings.paydayAlarmAt(month).atZone(BudgetTime.ZONE).toInstant().toEpochMilli(),
    dedupKey = salaryKey(month),
    guessCategory = false,
    addMissingCard = false,
    source = PrefillSource.PAYDAY,
    categoryCode = SALARY_CATEGORY_CODE,
)

/** 월급 거래의 가게 이름 */
const val SALARY_MERCHANT = "월급"

/** 월급을 받는 결제수단. 기본 결제수단 이름이다. */
private const val SALARY_PAYMENT = "계좌이체"

/** 등록창이 받는 가장 큰 금액(12자리) */
private val MAX_AMOUNT = "9".repeat(MAX_AMOUNT_DIGITS).toLong()
