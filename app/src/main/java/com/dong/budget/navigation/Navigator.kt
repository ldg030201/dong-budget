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
     * 같은 키를 두 번 쌓지 않는다. 이미 맨 위면 그대로 두고, 아래에 있으면 새로 쌓지 않고 맨 위로 올린다.
     *
     * NavEntry 는 키 값으로 내부 식별자를 만들기 때문에 같은 키가 두 번 쌓이면
     * 화면 상태 저장소와 ViewModel 이 서로 섞인다. 결제 알림을 A → B → A 순서로 누르면
     * 같은 등록창 키가 떨어진 자리에 다시 들어올 수 있어서, 맨 위만 볼 게 아니라 전체를 본다.
     * 올린 화면은 입력하던 내용을 그대로 가진다.
     */
    fun go(key: AppNavKey) {
        if (backStack.lastOrNull() == key) return
        backStack.remove(key)
        backStack.add(key)
    }

    /**
     * 한 칸 뒤로. 첫 화면(셸)만 남았으면 아무것도 하지 않는다.
     *
     * 시스템 뒤로가기는 NavDisplay 가 하나만 남으면 막아 주지만, 화면 안의 뒤로·닫기 버튼은 그 보호를 거치지 않는다.
     * 나가는 화면은 전환 애니메이션(약 0.7초) 동안 위에 남아 터치를 받기 때문에, 뒤로 버튼을 빠르게 두 번 누르면
     * 두 번째 탭이 셸까지 지워 백스택이 비고 NavDisplay 가 앱을 종료시킨다.
     */
    fun goBack() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    /** 화면이 스스로 닫을 때 쓴다. 이미 다른 화면이 위에 쌓였다면 아무것도 하지 않는다. */
    fun closeIfTop(key: AppNavKey) {
        if (backStack.size > 1 && backStack.lastOrNull() == key) backStack.removeLastOrNull()
    }
}
