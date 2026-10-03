package com.dong.budget.data

import com.dong.budget.data.db.CategoryScope
import com.dong.budget.data.db.FIXED_CATEGORY_CODE
import com.dong.budget.data.db.SALARY_CATEGORY_CODE
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
}
