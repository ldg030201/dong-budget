package com.dong.budget.data.db

import com.dong.budget.ui.fixed.fixedHistoryEnd
import com.dong.budget.ui.fixed.fixedHistoryStart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FixedMerchantsTest {
    @Test
    fun `고정지출 목록의 열쇠는 탭이 묶는 열쇠와 같고 이름이 없으면 빈 열쇠다`() {
        assertEquals("넷플릭스", fixedMerchantKey(" 넷 플릭스 "))
        assertEquals(fixedMerchantKey("Netflix"), fixedMerchantKey("netflix"))
        assertEquals(NO_MERCHANT_KEY, fixedMerchantKey(null))
        assertEquals(NO_MERCHANT_KEY, fixedMerchantKey("  "))
    }

    @Test
    fun `보이던 곳을 등록하는 기간은 탭이 이번 달을 볼 때 읽는 기간과 같다`() {
        listOf(
            LocalDate.of(2026, 10, 16),
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2028, 2, 29),
            LocalDate.of(2026, 12, 31),
        ).forEach { today ->
            val month = YearMonth.from(today)
            val tab = BudgetTime.monthRange(fixedHistoryStart(month)).first to BudgetTime.monthRange(fixedHistoryEnd(month, today)).second
            assertEquals("$today", tab, fixedShownRange(today))
        }
    }
}
