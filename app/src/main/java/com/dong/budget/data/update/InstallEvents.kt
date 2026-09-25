package com.dong.budget.data.update

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 설치 세션의 최종 결과 */
sealed interface InstallEvent {
    data object Succeeded : InstallEvent

    /**
     * @property reason 사용자가 읽을 안내
     * @property detail 시스템이 알려준 원래 사유(상태 코드와 메시지). 기기마다 설치기가 달라서
     *   왜 실패했는지 알아내려면 이 원문이 필요하다.
     * @property canTryOtherWays 받은 파일이나 브라우저로 설치하면 풀릴 수 있는 실패인지.
     *   서명이 다르거나 저장 공간이 없으면 어느 길로 설치해도 똑같이 막히므로 권하지 않는다.
     */
    data class Failed(val reason: String, val detail: String? = null, val canTryOtherWays: Boolean = true) : InstallEvent
}

/**
 * 설치 결과를 화면까지 전달하는 통로.
 *
 * 설치 결과는 앱이 아니라 시스템이 브로드캐스트로 알려주기 때문에
 * 받는 곳과 보여주는 곳이 떨어져 있다. 그 사이를 잇는다.
 *
 * replay 를 1로 둔 이유: 설치 확인창이 떠 있는 동안 우리 화면이 잠시 가려지는데,
 * 그때 도착한 결과가 버려지면 사용자는 아무 안내도 못 받는다.
 */
object InstallEvents {
    private val _events = MutableSharedFlow<InstallEvent>(replay = 1, extraBufferCapacity = 4)
    val events: SharedFlow<InstallEvent> = _events.asSharedFlow()

    fun publish(event: InstallEvent) {
        _events.tryEmit(event)
    }

    /** 새 설치를 시작하기 전에 지난 결과를 비운다. */
    fun clear() {
        _events.resetReplayCache()
    }
}
