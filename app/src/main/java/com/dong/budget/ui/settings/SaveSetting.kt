package com.dong.budget.ui.settings

import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import java.io.IOException

/** 설정(테마·한 주 시작·자동 백업·자동 기능 스위치)을 파일에 쓰지 못했을 때 */
internal const val SETTING_SAVE_FAILED_MESSAGE = "설정을 저장하지 못했어요"

/**
 * 설정 하나를 뒤에서 저장한다([save]). 저장 공간이 모자라는 등 파일에 쓰지 못하면(IOException) 앱을 죽이지 않고
 * 로그에 남긴 뒤 [messages](화면 모델의 한 번 띄우는 알림)로 알린다. 화면은 저장소 값을 따라가므로 저장 전 값으로 남는다.
 * 깨진 파일은 저장소가 기본값 파일로 바꿔 두므로(resetWhenCorrupted) 여기 오는 것은 쓰기 자체가 안 될 때다.
 * 취소와 그 밖의 오류(코드 잘못)는 잡지 않는다.
 */
internal fun CoroutineScope.saveSetting(messages: SendChannel<String>, save: suspend () -> Unit): Job = launch {
    try {
        save()
    } catch (e: IOException) {
        DevLog.warn(LogTag.SETTINGS, SETTING_SAVE_FAILED_MESSAGE, e)
        messages.send(SETTING_SAVE_FAILED_MESSAGE)
    }
}
