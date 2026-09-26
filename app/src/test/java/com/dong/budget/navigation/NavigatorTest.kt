package com.dong.budget.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorTest {
    private val a = TransactionEditorKey(prefill = EditorPrefill(1_000, "가게A", "하나카드", null, 1L, "toss:1:1000:가게A"))
    private val b = TransactionEditorKey(prefill = EditorPrefill(2_000, "가게B", "하나카드", null, 2L, "toss:2:2000:가게B"))

    @Test
    fun `맨 위와 같은 키는 다시 쌓지 않는다`() {
        val backStack = NavBackStack<NavKey>(ShellKey)
        val navigator = Navigator(backStack)
        navigator.go(a)
        navigator.go(a)
        assertEquals(listOf(ShellKey, a), backStack.toList())
    }

    @Test
    fun `아래에 있는 키는 새로 쌓지 않고 맨 위로 올린다`() {
        // 결제 알림을 A → B → A 순서로 누른 경우
        val backStack = NavBackStack<NavKey>(ShellKey)
        val navigator = Navigator(backStack)
        navigator.go(a)
        navigator.go(b)
        navigator.go(a)
        assertEquals(listOf(ShellKey, b, a), backStack.toList())
    }

    @Test
    fun `첫 화면만 남으면 뒤로가기를 여러 번 눌러도 지우지 않는다`() {
        // 뒤로 버튼을 빠르게 두 번 누른 경우. 두 번째가 셸까지 지우면 NavDisplay 가 앱을 종료시킨다.
        val backStack = NavBackStack<NavKey>(ShellKey)
        val navigator = Navigator(backStack)
        navigator.go(SettingsKey())
        navigator.goBack()
        navigator.goBack()
        assertEquals(listOf(ShellKey), backStack.toList())
        navigator.closeIfTop(ShellKey)
        assertEquals(listOf(ShellKey), backStack.toList())
    }
}
