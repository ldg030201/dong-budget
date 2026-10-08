package com.dong.budget.ui.home

import androidx.compose.runtime.Composable
import com.dong.budget.data.update.StoreUpdates
import com.dong.budget.data.update.UpdateNotice

/**
 * 홈 알림 줄을 눌렀을 때 할 일. GitHub 배포는 내려받기와 설치를 앱 정보에서 하므로 그리로 간다.
 * @param updates Play 배포와 모양을 맞추려고 받는다. 여기서는 쓰지 않는다.
 */
@Composable
fun rememberUpdateOpener(updates: StoreUpdates, onOpenAppInfo: () -> Unit): (UpdateNotice) -> Unit = { onOpenAppInfo() }
