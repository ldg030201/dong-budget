package com.dong.budget.data.update

import android.content.SharedPreferences
import androidx.core.content.edit
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex

/**
 * Play 업데이트 상태를 앱 전체가 함께 쓴다. 앱 정보와 홈 알림 줄이 모두 [state] 를 따라간다.
 *
 * - 앱을 열 때마다(화면에 나올 때마다) [check] 로 Play 에 묻는다. GitHub 배포와 달리 확인 간격을 두지 않는다.
 *   기기 안의 Play 스토어에 묻는 것이라 횟수 제한이 없고, 받는 동안 앱을 나갔다 오면 '다 받았어요' 를 바로 알려야 해서다.
 * - 받는 동안의 진행 상황은 Play 가 알려 준다([onInstallState]).
 * - 홈 알림 줄을 닫거나 건너뛴 것은 새 버전의 versionCode 로 기억한다. Play 는 설치 전에 버전 이름을 알려 주지 않는다.
 *   건너뛴 것은 GitHub 배포와 같은 파일(update_check)에 다른 열쇠로 적는다.
 *
 * @param fetch 실제 확인. 보통은 Play 의 AppUpdateManager 에 묻고(StoreUpdates), 테스트에서는 가짜로 바꾼다.
 *   실패의 오류 코드는 [errorCodeOf] 로 꺼낸다.
 * @param fromPlay Play 스토어가 설치한 앱인지
 */
class PlayUpdateChecker(
    private val fetch: suspend () -> Result<PlayUpdateInfo>,
    private val fromPlay: () -> Boolean,
    private val prefs: SharedPreferences,
    private val errorCodeOf: (Throwable) -> Int? = { null },
) {
    private val _state = MutableStateFlow<PlayUpdateState>(PlayUpdateState.Unknown)
    val state: StateFlow<PlayUpdateState> = _state.asStateFlow()

    private val _checking = MutableStateFlow(false)

    /** Play 에 묻는 중인지. 앱 정보가 '확인하고 있어요' 를 보여 준다. 그동안 [state] 는 지난 값 그대로라 알림 줄이 깜빡이지 않는다. */
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** 이번 실행에서 알림 줄을 닫은 새 버전. 앱을 다시 켜면 알림 줄이 다시 보인다. */
    private val dismissed = MutableStateFlow<Int?>(null)

    /** '이 버전 건너뛰기' 로 건너뛴 새 버전. 이 버전까지는 홈에 알리지 않고, 더 새 버전이 나오면 다시 알린다. */
    private val skipped = MutableStateFlow(prefs.getInt(KEY_SKIPPED, NONE).takeIf { it != NONE })

    /** 홈 알림 줄에 보일 것. 없거나 닫았거나 건너뛴 버전이면 null */
    val notice: Flow<UpdateNotice?> =
        combine(_state, dismissed, skipped) { state, closed, skip ->
            val code = state.versionCode
            state.notice?.takeIf { code != closed && (skip == null || code == null || code > skip) }
        }

    private val mutex = Mutex()

    /** 지금 Play 에 묻는다. 이미 묻는 중이면 겹쳐 묻지 않고 넘어간다. */
    suspend fun check() {
        if (!mutex.tryLock()) return
        _checking.value = true
        try {
            fetch().fold(
                onSuccess = ::record,
                onFailure = { error ->
                    val code = errorCodeOf(error)
                    _state.value = PlayUpdateRules.afterFailure(_state.value, PlayUpdateRules.failureOf(code, fromPlay()))
                    DevLog.warn(LogTag.UPDATE, "새 버전을 확인하지 못했어요(Play 오류 ${code ?: "없음"})", error)
                },
            )
        } finally {
            _checking.value = false
            mutex.unlock()
        }
    }

    /** Play 에 물어 받은 정보를 반영한다. 업데이트를 시작하려고 새로 물은 정보([StoreUpdates.startUpdate])도 여기로 온다. */
    fun record(info: PlayUpdateInfo) {
        val next = PlayUpdateRules.stateOf(info, fromPlay())
        _state.value = next
        DevLog.info(LogTag.UPDATE, "새 버전 확인(Play): ${describe(next)}")
    }

    /**
     * 받는 동안 Play 가 알려 주는 진행 상황을 반영한다.
     * @return 반영했는지. 지금 상태가 새 버전을 몰라 반영하지 못했으면 false. 그때는 [check] 로 다시 묻는다.
     */
    fun onInstallState(installStatus: Int, bytesDownloaded: Long, totalBytes: Long, errorCode: Int): Boolean {
        val current = _state.value
        val next = PlayUpdateRules.stateOf(current, installStatus, bytesDownloaded, totalBytes, errorCode)
        if (next == null) return current.versionCode != null
        // 받는 양이 바뀔 때마다 적지 않는다. 단계가 바뀔 때만 적는다.
        if (next::class != current::class) DevLog.info(LogTag.UPDATE, "업데이트 진행(Play): ${describe(next)}")
        _state.value = next
        return true
    }

    /** 업데이트 확인 창(Play)을 닫고 돌아왔다 */
    fun onFlowResult(resultCode: Int) {
        val next = PlayUpdateRules.afterFlow(_state.value, resultCode)
        when (next) {
            is PlayUpdateState.Failed -> DevLog.warn(LogTag.UPDATE, "업데이트를 시작하지 못했어요(결과 $resultCode)")
            null -> DevLog.info(LogTag.UPDATE, "업데이트 확인 창을 닫았어요(결과 $resultCode)")
            else -> DevLog.info(LogTag.UPDATE, "업데이트 받기를 시작했어요")
        }
        if (next != null) _state.value = next
    }

    /** Play 에 묻거나 업데이트를 시작·마치다 실패했다. 받은 업데이트를 마치지 못한 것 등은 [reason] 으로 알린다. */
    fun onFailure(reason: String, error: Throwable?) {
        DevLog.warn(LogTag.UPDATE, reason, error)
        _state.value = PlayUpdateState.Failed(reason)
    }

    fun dismiss() {
        dismissed.value = _state.value.versionCode
    }

    /** 지금 알리는 새 버전을 건너뛴다. 앱 정보에는 그대로 보여서 마음이 바뀌면 거기서 업데이트할 수 있다. */
    fun skip() {
        val code = _state.value.versionCode ?: return
        skipped.value = code
        prefs.edit { putInt(KEY_SKIPPED, code) }
        DevLog.info(LogTag.UPDATE, "새 버전(코드 $code) 건너뛰기")
    }

    private fun describe(state: PlayUpdateState): String = when (state) {
        PlayUpdateState.Unknown -> "모름"
        PlayUpdateState.UpToDate -> "최신"
        is PlayUpdateState.Available -> "새 버전 있음(코드 ${state.versionCode}${if (state.flexibleAllowed) "" else ", 앱 안에서 받을 수 없음"})"
        is PlayUpdateState.Downloading -> "받는 중(코드 ${state.versionCode})"
        is PlayUpdateState.Downloaded -> "다 받음(코드 ${state.versionCode})"
        PlayUpdateState.Installing -> "설치 중"
        PlayUpdateState.NotFromPlay -> "Play 에서 설치한 앱이 아님"
        is PlayUpdateState.Failed -> state.reason
    }

    companion object {
        /** SharedPreferences 파일 이름. GitHub 배포와 같은 파일이다. */
        const val PREFS_NAME = "update_check"

        /** 건너뛴 새 버전의 versionCode. GitHub 배포의 skipped_version(버전 이름)과 따로 둔다. */
        private const val KEY_SKIPPED = "skipped_version_code"
        private const val NONE = -1
    }
}
