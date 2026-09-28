package com.dong.budget.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSettingsTest {
    @Test
    fun `처음에는 모두 켜져 있다`() {
        val settings = AutoSettings()
        AutoOption.entries.forEach { option ->
            assertTrue(option.name, settings.chosen(option))
            assertTrue(option.name, settings[option])
        }
    }

    @Test
    fun `기대는 스위치를 끄면 고른 값은 남고 동작만 멈춘다`() {
        val settings = AutoSettings().with(AutoOption.FILL_PAYMENT, false)
        assertTrue(settings.chosen(AutoOption.FILL_NEW_CARD))
        assertFalse(settings[AutoOption.FILL_NEW_CARD])
        // 다시 켜면 전에 고른 대로 돌아간다
        assertTrue(settings.with(AutoOption.FILL_PAYMENT, true)[AutoOption.FILL_NEW_CARD])
    }

    @Test
    fun `묻기를 끄면 같은 결제 한 번만과 다시 살피기도 멈추고 채우기 스위치는 그대로다`() {
        val settings = AutoSettings().with(AutoOption.CAPTURE_PROMPT, false)
        assertFalse(settings[AutoOption.CAPTURE_DEDUPE])
        assertFalse(settings[AutoOption.CAPTURE_RESCAN])
        // 알림 목록에서 지난 결제를 열어 등록할 때는 채우기 스위치를 따른다
        assertTrue(settings[AutoOption.FILL_CATEGORY])
        assertTrue(settings[AutoOption.FILL_PAYMENT])
        assertTrue(settings[AutoOption.FILL_INSTALLMENT])
    }

    @Test
    fun `스위치 하나만 바꾼다`() {
        val settings = AutoSettings().with(AutoOption.EDITOR_KEYPAD, false).with(AutoOption.STATS_DAY, false)
        assertEquals(setOf(AutoOption.EDITOR_KEYPAD, AutoOption.STATS_DAY), settings.off)
        assertEquals(setOf(AutoOption.STATS_DAY), settings.with(AutoOption.EDITOR_KEYPAD, true).off)
    }

    @Test
    fun `저장 열쇠는 겹치지 않고 기대는 스위치는 앞에 있다`() {
        assertEquals(AutoOption.entries.size, AutoOption.entries.map { it.key }.toSet().size)
        AutoOption.entries.forEach { option ->
            option.parent?.let { parent -> assertTrue("${option.name} 은 ${parent.name} 뒤에 둔다", parent.ordinal < option.ordinal) }
        }
    }
}
