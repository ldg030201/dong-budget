package com.dong.budget.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * 앱이 잠겨 있는지(설정 > 잠금 > 앱 잠금). 잠금 화면이 앱 위를 덮고 있는 동안 true 다.
 * 확인 창([ConfirmDialog])은 따로 뜨는 창이라 잠금 화면보다 위에 보이므로, 잠긴 동안에는 그리지 않고 풀린 뒤에 다시 띄운다.
 * 잠금 화면 자신의 창(PIN 을 잊었어요 등)은 이 값 밖에서 그려 그대로 뜬다.
 */
val LocalAppLocked = compositionLocalOf { false }
