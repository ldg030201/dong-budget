package com.dong.budget.ui.settings

import com.dong.budget.data.settings.AutoOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoOptionTextTest {
    @Test
    fun `모든 스위치가 설정 화면에 한 번씩 나온다`() {
        // 새 스위치를 만들고 설정 화면에 두는 것을 빠뜨리면 여기서 걸린다. 새 버전 확인은 '앱 정보' 에 따로 있다.
        val shown = AutoGroup.entries.flatMap { it.options } + AutoOption.UPDATE_CHECK
        assertEquals(AutoOption.entries.sorted(), shown.sorted())
        assertEquals(shown.size, shown.toSet().size)
    }

    @Test
    fun `기대는 스위치는 같은 묶음에서 그 줄보다 앞에 있다`() {
        AutoGroup.entries.forEach { group ->
            group.options.forEachIndexed { index, option ->
                option.parent?.let { parent ->
                    val parentIndex = group.options.indexOf(parent)
                    assertTrue("${option.name} 의 위 스위치가 같은 묶음 앞에 없다", parentIndex in 0 until index)
                }
            }
        }
    }

    @Test
    fun `이름과 설명이 비어 있지 않고 이름은 겹치지 않는다`() {
        AutoOption.entries.forEach { option ->
            assertTrue(option.name, option.title.isNotBlank())
            assertTrue(option.name, option.description.isNotBlank())
        }
        assertEquals(AutoOption.entries.size, AutoOption.entries.map { it.title }.toSet().size)
    }
}
