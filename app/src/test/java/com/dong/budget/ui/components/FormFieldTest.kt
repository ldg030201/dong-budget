package com.dong.budget.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

// 글자 칸의 길이 한도. 한도는 저장할 때의 길이 검사(name.length)와 같은 UTF-16 단위로 센다.
class FormFieldTest {
    @Test
    fun `한도 안이면 그대로 둔다`() {
        assertEquals("카페", clipToLength("카페", 20))
        assertEquals("카페", clipToLength("카페", 2))
        assertEquals("카페", clipToLength("카페", Int.MAX_VALUE))
    }

    @Test
    fun `한도를 넘으면 뒤를 자른다`() {
        assertEquals("가나다", clipToLength("가나다라", 3))
        assertEquals("", clipToLength("가", 0))
    }

    @Test
    fun `두 단위 글자가 경계에 걸리면 반쪽을 남기지 않고 통째로 뺀다`() {
        // '😀' 는 UTF-16 두 단위다. 세 단위로 자르면 take 는 앞 반쪽(외톨이 서로게이트)을 남긴다.
        val clipped = clipToLength("가나😀", 3)
        assertEquals("가나", clipped)
        assertEquals(false, clipped.last().isSurrogate())
    }

    @Test
    fun `두 단위 글자가 한도 안에 다 들어가면 남긴다`() {
        assertEquals("가😀", clipToLength("가😀나", 3))
        assertEquals("😀😀", clipToLength("😀😀😀", 5))
    }
}
