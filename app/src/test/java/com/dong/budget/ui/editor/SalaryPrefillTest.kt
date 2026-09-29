package com.dong.budget.ui.editor

import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.SALARY_CATEGORY_CODE
import com.dong.budget.data.salary.PayBasis
import com.dong.budget.data.salary.SalarySettings
import com.dong.budget.data.salary.salaryMonthOf
import com.dong.budget.navigation.PrefillSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth

class SalaryPrefillTest {
    private val settings = SalarySettings(basis = PayBasis.YEARLY, amount = 40_000_000)

    @Test
    fun `월급날 등록창은 그달 월급으로 채운 수입이다`() {
        // 2026년 10월 25일은 일요일이라 23일(금)에 받는다
        val prefill = salaryPrefill(settings, YearMonth.of(2026, 10))
        assertEquals(PrefillSource.PAYDAY, prefill.source)
        assertEquals(SALARY_CATEGORY_CODE, prefill.categoryCode)
        // 연봉은 12로 나눠 원 아래를 버린다
        assertEquals(3_333_333L, prefill.amount)
        assertEquals("월급", prefill.merchant)
        assertEquals("2026년 10월분", prefill.memo)
        assertEquals("salary:2026-10", prefill.dedupKey)
        assertEquals(YearMonth.of(2026, 10), salaryMonthOf(prefill.dedupKey))
        assertEquals(
            LocalDateTime.of(2026, 10, 23, 9, 0),
            Instant.ofEpochMilli(prefill.occurredAtMillis).atZone(BudgetTime.ZONE).toLocalDateTime(),
        )
        // 지출만 보는 같은 가게 분류 짐작과 없는 결제수단 만들기는 끈다
        assertFalse(prefill.guessCategory)
        assertFalse(prefill.addMissingCard)
    }

    @Test
    fun `같은 달이면 몇 번을 만들어도 같은 값이다`() {
        assertEquals(salaryPrefill(settings, YearMonth.of(2026, 9)), salaryPrefill(settings, YearMonth.of(2026, 9)))
    }

    @Test
    fun `월급 열쇠가 아니면 달을 읽지 않는다`() {
        assertEquals(null, salaryMonthOf("toss:1:1000:가게"))
        assertEquals(null, salaryMonthOf("salary:언젠가"))
    }
}
