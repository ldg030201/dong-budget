package com.dong.budget.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * 백스택 조작 창구.
 *
 * 화면 컴포저블에는 이 객체를 넘기지 않는다. 화면은 콜백만 받는다.
 */
@Stable
class Navigator(private val backStack: NavBackStack<NavKey>) {
    /**
     * 같은 키를 연속으로 쌓지 않는다.
     *
     * NavEntry 는 키 값으로 내부 식별자를 만들기 때문에 같은 키가 두 번 쌓이면
     * 화면 상태 저장소와 ViewModel 이 서로 섞인다.
     */
    fun go(key: AppNavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    /**
     * 한 칸 뒤로.
     *
     * '마지막 화면에서 뒤로 누르면 앱 종료' 같은 판단은 여기서 하지 않는다.
     * NavDisplay 가 백스택이 하나만 남으면 뒤로가기 처리를 아예 비활성화하므로
     * 그 분기를 여기 두면 도달할 수 없는 코드가 된다.
     */
    fun goBack() {
        backStack.removeLastOrNull()
    }

    /** 화면이 스스로 닫을 때 쓴다. 이미 다른 화면이 위에 쌓였다면 아무것도 하지 않는다. */
    fun closeIfTop(key: AppNavKey) {
        if (backStack.lastOrNull() == key) backStack.removeLastOrNull()
    }
}
