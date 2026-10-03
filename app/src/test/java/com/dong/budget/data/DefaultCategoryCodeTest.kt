package com.dong.budget.data

import com.dong.budget.data.db.CategoryCodeRow
import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.CategoryScope.EXPENSE
import com.dong.budget.data.db.CategoryScope.INCOME
import com.dong.budget.data.db.FIXED_CATEGORY_CODE
import com.dong.budget.data.db.SALARY_CATEGORY_CODE
import com.dong.budget.data.db.reclaimedCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultCategoryCodeTest {
    private val none: (String) -> Boolean = { false }

    @Test
    fun `지운 고정지출 분류를 같은 이름으로 다시 만들면 고정지출 코드를 붙인다`() {
        assertEquals(FIXED_CATEGORY_CODE, defaultCodeFor(CategoryScope.EXPENSE, "고정지출", none))
        // 가운데 띄어쓰기도 무시한다
        assertEquals(FIXED_CATEGORY_CODE, defaultCodeFor(CategoryScope.EXPENSE, "고정 지출", none))
    }

    @Test
    fun `지운 급여 분류를 같은 이름으로 다시 만들면 급여 코드를 붙인다`() {
        assertEquals(SALARY_CATEGORY_CODE, defaultCodeFor(CategoryScope.INCOME, "급여", none))
    }

    @Test
    fun `그 코드를 가진 분류가 아직 있으면 붙이지 않는다`() {
        assertNull(defaultCodeFor(CategoryScope.EXPENSE, "고정지출") { it == FIXED_CATEGORY_CODE })
        assertNull(defaultCodeFor(CategoryScope.INCOME, "급여") { it == SALARY_CATEGORY_CODE })
    }

    @Test
    fun `종류가 다르거나 이름이 다르면 사용자가 만든 분류다`() {
        assertNull(defaultCodeFor(CategoryScope.INCOME, "고정지출", none))
        assertNull(defaultCodeFor(CategoryScope.EXPENSE, "급여", none))
        assertNull(defaultCodeFor(CategoryScope.EXPENSE, "고정지출비", none))
        // 코드로 찾는 기능이 없는 기본 분류는 돌려주지 않는다
        assertNull(defaultCodeFor(CategoryScope.EXPENSE, "식비", none))
    }

    // ── 이미 코드 없이 다시 만들어 둔 분류에 코드 돌려주기(DB 를 열 때 · 백업을 되살린 뒤 · 분류를 지운 뒤) ──

    private fun row(id: Long, scope: CategoryScope, name: String, code: String? = null) = CategoryCodeRow(id, scope, name, code)

    /** 기본 분류 중 코드로 찾지 않는 것과 '기타' */
    private val others = listOf(row(1, EXPENSE, "식비", "FOOD"), row(2, EXPENSE, "기타", "ETC_EXPENSE"), row(3, INCOME, "기타", "ETC_INCOME"))

    @Test
    fun `코드 없이 다시 만든 고정지출·급여 분류에 코드를 돌려준다`() {
        val rows = others + row(10, EXPENSE, "고정지출") + row(11, INCOME, "급여")
        assertEquals(mapOf(10L to FIXED_CATEGORY_CODE, 11L to SALARY_CATEGORY_CODE), reclaimedCodes(rows))
    }

    @Test
    fun `띄어쓰기만 달라도 같은 이름으로 보고, 여럿이면 먼저 만든 하나에만 준다`() {
        val rows = others + row(12, EXPENSE, " 고정 지출") + row(15, EXPENSE, "고 정지출")
        assertEquals(mapOf(12L to FIXED_CATEGORY_CODE), reclaimedCodes(rows))
    }

    @Test
    fun `그 코드를 가진 분류가 이미 있으면 아무것도 바꾸지 않는다`() {
        val rows =
            others + row(4, EXPENSE, "고정지출", FIXED_CATEGORY_CODE) + row(12, EXPENSE, "고정 지출") + row(5, INCOME, "급여", SALARY_CATEGORY_CODE)
        assertEquals(emptyMap<Long, String>(), reclaimedCodes(rows))
        // 이름이 같은 분류가 이미 코드를 갖고 있어도 그대로다
        assertEquals(emptyMap<Long, String>(), reclaimedCodes(others + row(4, EXPENSE, "고정지출", FIXED_CATEGORY_CODE)))
    }

    @Test
    fun `종류가 다르거나 이름이 다르면 돌려주지 않는다`() {
        val rows = others + row(10, INCOME, "고정지출") + row(11, EXPENSE, "급여") + row(12, EXPENSE, "고정지출비")
        assertEquals(emptyMap<Long, String>(), reclaimedCodes(rows))
        // 한 종류만 있어도 그 종류만 돌려준다(수입에 급여 코드가 있고 지출엔 고정지출 코드가 없음)
        val partial = others + row(5, INCOME, "급여", SALARY_CATEGORY_CODE) + row(13, EXPENSE, "고정지출")
        assertEquals(mapOf(13L to FIXED_CATEGORY_CODE), reclaimedCodes(partial))
    }
}
