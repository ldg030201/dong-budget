package com.dong.budget.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.dong.budget.data.update.StoreUpdates
import com.dong.budget.data.update.UpdateNotice

/**
 * 홈 알림 줄을 눌렀을 때 할 일(Play 배포).
 *  - 새 버전이 나왔어요: 그 자리에서 Play 의 업데이트 확인 창을 띄운다. 받기를 고르면 Play 가 뒤에서 받는다.
 *  - 받고 있어요: 앱 정보에서 얼마나 받았는지 보여 준다.
 *  - 다 받았어요: 다시 시작해서 새 버전으로 바꾼다.
 */
@Composable
fun rememberUpdateOpener(updates: StoreUpdates, onOpenAppInfo: () -> Unit): (UpdateNotice) -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            updates.onUpdateFlowResult(result.resultCode)
        }
    val openAppInfo by rememberUpdatedState(onOpenAppInfo)
    return remember(updates, launcher) {
        { notice ->
            when (notice) {
                is UpdateNotice.Available -> updates.startUpdate(launcher)
                is UpdateNotice.Downloading -> openAppInfo()
                UpdateNotice.Downloaded -> updates.completeUpdate()
            }
        }
    }
}
