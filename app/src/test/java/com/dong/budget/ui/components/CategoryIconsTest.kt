package com.dong.budget.ui.components

import com.dong.budget.R
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.DEFAULT_CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIconsTest {
    private val fallback = R.drawable.ic_sym_more_horiz

    @Test
    fun `저장 가능한 아이콘 이름은 전부 그림이 짝지어져 있다`() {
        // 이름만 추가하고 짝을 빠뜨리면 그 아이콘을 고른 분류가 '...' 로 보인다
        val missing = CategoryStyle.ICONS.filter { it != CategoryStyle.FALLBACK_ICON && categoryIconRes(it) == fallback }
        assertEquals("그림이 없는 아이콘 이름", emptyList<String>(), missing)
    }

    @Test
    fun `기본 분류의 아이콘과 색은 모두 허용 목록 안에 있다`() {
        DEFAULT_CATEGORIES.forEach {
            assertTrue("${it.name} 아이콘 ${it.icon}", it.icon in CategoryStyle.ICONS)
            assertTrue("${it.name} 색 ${it.color}", it.color in CategoryStyle.COLORS)
        }
    }

    @Test
    fun `모르는 이름이나 빈 값은 기본 아이콘으로 떨어진다`() {
        assertEquals(fallback, categoryIconRes("없는_아이콘"))
        assertEquals(fallback, categoryIconRes(null))
    }
}
