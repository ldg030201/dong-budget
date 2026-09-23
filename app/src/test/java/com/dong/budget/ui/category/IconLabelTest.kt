package com.dong.budget.ui.category

import com.dong.budget.data.db.CategoryStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class IconLabelTest {
    @Test
    fun `모든 아이콘에 화면 읽기용 이름이 있다`() {
        // 짝을 빠뜨리면 그 아이콘이 화면 읽기에서 '기타' 로 읽힌다
        val unnamed = CategoryStyle.ICONS.filter { it != CategoryStyle.FALLBACK_ICON && iconLabel(it) == "기타" }
        assertEquals("이름이 없는 아이콘", emptyList<String>(), unnamed)
    }
}
