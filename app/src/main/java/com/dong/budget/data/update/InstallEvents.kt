package com.dong.budget.data.update

import android.content.Intent
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
     * @property suggestGalaxySecurity 갤럭시 '보안 위험 자동 차단' 설정으로 가는 버튼을 보여줄지
     */
    data class Failed(
        val reason: String,
        val detail: String? = null,
        val canTryOtherWays: Boolean = true,
        val suggestGalaxySecurity: Boolean = false,
    ) : InstallEvent
}

/**
 * 설치 결과를 화면까지 전달하는 통로.
 *
 * 설치 결과는 앱이 아니라 시스템이 브로드캐스트로 알려주기 때문에
 * 받는 곳과 보여주는 곳이 떨어져 있다. 그 사이를 잇는다.
 *
 * replay 를 1로 둔 이유: 결과가 설치를 시작한 화면의 구독보다 먼저 도착해도 버려지지 않게 하기 위함이다.
 * 다만 지난 시도의 결과가 남아 있으므로, 받는 쪽은 자기가 시작한 설치의 결과만 반영해야 한다(SettingsViewModel).
 */
object InstallEvents {
    /**
     * 지금 진행 중인 설치 세션. 결과를 받을 때 이 세션의 것만 받는다.
     * 앱이 다시 시작되면 [NO_SESSION] 으로 돌아가고, 그때는 어느 결과든 받는다.
     */
    @Volatile var activeSessionId: Int = NO_SESSION

    const val NO_SESSION = -1

    /** 새 세션을 만드는 중. 세션 번호는 늘 1 이상이라 어떤 결과와도 맞지 않는다. */
    const val SESSION_PENDING = 0

    private val _events = MutableSharedFlow<InstallEvent>(replay = 1, extraBufferCapacity = 4)
    val events: SharedFlow<InstallEvent> = _events.asSharedFlow()

    fun publish(event: InstallEvent) {
        // 결과가 나왔으면 띄우지 못한 확인창도 더는 필요 없다
        pendingConfirm = null
        _events.tryEmit(event)
    }

    /** 새 설치를 시작하기 전에 지난 결과를 비운다. */
    fun clear() {
        pendingConfirm = null
        _events.resetReplayCache()
    }

    /**
     * 동계부 화면이 보이는 중인지. MainActivity 가 onStart/onStop 에서 적는다.
     * 화면이 안 보이면 설치 확인창을 띄워도 안드로이드가 막는다(백그라운드 화면 실행 제한).
     */
    @Volatile var appVisible: Boolean = false

    /**
     * 앱이 안 보일 때 도착해서 띄우지 못한 설치 확인창. 동계부로 돌아오면 [takePendingConfirm] 으로 꺼내 띄운다.
     * 받는 동안 다른 앱으로 나가 있으면 이렇게 된다. 그냥 두면 설치가 확인을 기다린 채 멈춘다.
     */
    @Volatile private var pendingConfirm: Intent? = null

    fun holdConfirm(intent: Intent) {
        pendingConfirm = intent
    }

    /** 띄우지 못한 확인창을 꺼낸다. 한 번 꺼내면 비운다. */
    fun takePendingConfirm(): Intent? = pendingConfirm.also { pendingConfirm = null }
}
