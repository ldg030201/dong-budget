package com.dong.budget.data.db

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
    fun `목록 정리는 고정지출 지출이 하나도 남지 않은 곳만 고른다`() {
        // 목록은 가게 열쇠(띄어쓰기를 빼고 소문자)로 적혀 있고, 지출의 가게 이름은 적은 그대로다
        val stored = listOf("오래된구독", "netflix", "헬스장", NO_MERCHANT_KEY, "지운곳")
        val merchants = listOf(" 오래된 구독", "Netflix", null)
        assertEquals(listOf("헬스장", "지운곳"), staleFixedMerchants(stored, merchants))
        assertEquals(emptyList<String>(), staleFixedMerchants(emptyList(), merchants))
    }

    @Test
    fun `탭이 읽는 기간은 고른 달 앞 27달부터 오늘이 든 달까지다`() {
        val today = LocalDate.of(2026, 10, 16)
        assertEquals(YearMonth.of(2024, 7), fixedHistoryStart(YearMonth.of(2026, 10)))
        // 지난 달을 봐도 오늘이 든 달까지 읽는다(다음 달로 밀려 낸 몫을 센다)
        assertEquals(YearMonth.of(2026, 10), fixedHistoryEnd(YearMonth.of(2026, 8), today))
    }
}
