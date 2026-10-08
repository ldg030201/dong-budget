package com.dong.budget.data.update

import android.app.Activity
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Play 에 물어 받은 새 버전 정보. AppUpdateInfo 에서 쓰는 값만 옮겨 담는다.
 * AppUpdateInfo 는 Play 라이브러리 안에서만 만들 수 있어서, 단위 테스트가 상태 바꾸기를 이 모양으로 확인한다.
 *
 * @property availability UpdateAvailability 값
 * @property installStatus InstallStatus 값. 받기 시작한 적 없으면 UNKNOWN
 * @property versionCode Play 에 올라온 새 버전의 versionCode. 건너뛴 버전을 이 값으로 기억한다.
 * @property flexibleAllowed 받는 동안 앱을 계속 쓰는 업데이트(FLEXIBLE)를 앱 안에서 시작할 수 있는지
 */
data class PlayUpdateInfo(
    val availability: Int,
    val installStatus: Int,
    val versionCode: Int,
    val flexibleAllowed: Boolean,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
)

/** Play 업데이트가 어디까지 왔는지. 앱 정보와 홈 알림 줄이 같이 본다. */
sealed interface PlayUpdateState {
    /** 아직 확인하지 않았다 */
    data object Unknown : PlayUpdateState

    data object UpToDate : PlayUpdateState

    /** @property flexibleAllowed false 면 앱 안에서 받을 수 없어 Play 스토어 화면으로 보낸다 */
    data class Available(val versionCode: Int, val flexibleAllowed: Boolean = true) : PlayUpdateState

    /** @property progress 0~1. 받기 전이라 크기를 아직 모르면 null */
    data class Downloading(val versionCode: Int, val progress: Float?) : PlayUpdateState

    /** 다 받았다. 다시 시작하면 새 버전으로 바뀐다. */
    data class Downloaded(val versionCode: Int) : PlayUpdateState

    /** 다 받은 새 버전을 설치하는 중. 곧 앱이 다시 시작된다. */
    data object Installing : PlayUpdateState

    /** Play 스토어에서 설치한 앱이 아니거나, 이 기기에서 Play 업데이트를 쓸 수 없다 */
    data object NotFromPlay : PlayUpdateState

    /** @property reason 사용자에게 보여 줄 까닭 */
    data class Failed(val reason: String) : PlayUpdateState
}

/** 이 상태가 가리키는 새 버전. 새 버전이 없거나 모르면 null */
val PlayUpdateState.versionCode: Int?
    get() = when (this) {
        is PlayUpdateState.Available -> versionCode
        is PlayUpdateState.Downloading -> versionCode
        is PlayUpdateState.Downloaded -> versionCode
        else -> null
    }

/** 홈 알림 줄과 설정의 앱 정보 줄에 알릴 것. 알릴 것이 없으면 null */
val PlayUpdateState.notice: UpdateNotice?
    get() = when (this) {
        // Play 는 설치하기 전에 새 버전의 이름을 알려 주지 않는다
        is PlayUpdateState.Available -> UpdateNotice.Available(version = null)

        is PlayUpdateState.Downloading -> UpdateNotice.Downloading(progress)

        is PlayUpdateState.Downloaded -> UpdateNotice.Downloaded

        else -> null
    }

/**
 * Play 업데이트의 상태를 정하는 규칙. Play 가 알려 준 값(정수 코드)만으로 정해서 단위 테스트로 확인한다.
 */
object PlayUpdateRules {
    const val NOT_FROM_PLAY_MESSAGE = "Play 스토어에서 설치한 앱에서만 업데이트를 확인할 수 있어요"
    const val CHECK_FAILED_MESSAGE = "새 버전을 확인하지 못했어요. 잠시 뒤에 다시 해 주세요"
    const val NOT_ALLOWED_MESSAGE = "지금은 업데이트를 받을 수 없어요. 저장 공간과 배터리를 확인하고 다시 해 주세요"
    const val DOWNLOAD_FAILED_MESSAGE = "새 버전을 받지 못했어요. 잠시 뒤에 다시 해 주세요"
    const val START_FAILED_MESSAGE = "업데이트를 시작하지 못했어요. 잠시 뒤에 다시 해 주세요"
    const val COMPLETE_FAILED_MESSAGE = "업데이트를 마치지 못했어요. 잠시 뒤에 다시 해 주세요"

    /**
     * Play 에 물어 받은 정보로 상태를 정한다. 받는 중이거나 다 받은 업데이트가 있으면 그것이 먼저다.
     * @param fromPlay Play 스토어가 설치한 앱인지. 스토어 밖(adb·APK)에서 설치한 앱에 Play 는 '새 버전 없음' 으로 답하는데,
     *   그때 '최신 버전' 이라고 단정하지 않고 Play 에서 설치한 앱에서만 확인할 수 있다고 알린다.
     */
    fun stateOf(info: PlayUpdateInfo, fromPlay: Boolean): PlayUpdateState = when (info.installStatus) {
        InstallStatus.DOWNLOADED -> PlayUpdateState.Downloaded(info.versionCode)

        InstallStatus.INSTALLING -> PlayUpdateState.Installing

        InstallStatus.PENDING, InstallStatus.DOWNLOADING ->
            PlayUpdateState.Downloading(info.versionCode, progressOf(info.bytesDownloaded, info.totalBytes))

        // 받다가 실패했거나 취소한 업데이트도 아직 새 버전이 있으면 다시 받을 수 있다
        else ->
            when {
                info.availability == UpdateAvailability.UPDATE_AVAILABLE ->
                    PlayUpdateState.Available(info.versionCode, info.flexibleAllowed)

                !fromPlay -> PlayUpdateState.NotFromPlay

                info.availability == UpdateAvailability.UPDATE_NOT_AVAILABLE -> PlayUpdateState.UpToDate

                // UNKNOWN 이거나, 이 앱이 쓰지 않는 즉시 업데이트(IMMEDIATE)가 진행 중이라는 답
                else -> PlayUpdateState.Failed(CHECK_FAILED_MESSAGE)
            }
    }

    /**
     * 받는 동안 Play 가 알려 주는 진행 상황(InstallState)으로 다음 상태를 정한다. 따를 것이 없으면 null
     * @param current 지금 상태. 진행 상황에는 새 버전의 versionCode 가 없어서 지금 상태에서 가져온다.
     *   지금 상태가 새 버전을 모르면(앱을 다시 켜 아직 확인 전) null 을 돌려준다. 그때는 Play 에 다시 묻는다.
     */
    fun stateOf(current: PlayUpdateState, installStatus: Int, bytesDownloaded: Long, totalBytes: Long, errorCode: Int): PlayUpdateState? {
        if (installStatus == InstallStatus.INSTALLING) return PlayUpdateState.Installing
        val code = current.versionCode ?: return null
        return when (installStatus) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING ->
                PlayUpdateState.Downloading(code, progressOf(bytesDownloaded, totalBytes))

            InstallStatus.DOWNLOADED -> PlayUpdateState.Downloaded(code)

            InstallStatus.INSTALLED -> PlayUpdateState.UpToDate

            InstallStatus.FAILED ->
                PlayUpdateState.Failed(
                    if (errorCode ==
                        InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED
                    ) {
                        NOT_ALLOWED_MESSAGE
                    } else {
                        DOWNLOAD_FAILED_MESSAGE
                    },
                )

            // 받기를 취소했다. 새 버전은 그대로 있다.
            InstallStatus.CANCELED -> PlayUpdateState.Available(code)

            else -> null
        }
    }

    /**
     * Play 에 묻지 못했을 때의 상태
     * @param errorCode Play 가 준 오류 코드(InstallException). Play 의 오류가 아니면 null
     */
    fun failureOf(errorCode: Int?, fromPlay: Boolean): PlayUpdateState = when (errorCode) {
        // Play 계정으로 받은 앱이 아니거나(adb·APK 로 설치), 이 기기에 Play 스토어가 없거나 쓸 수 없다
        InstallErrorCode.ERROR_APP_NOT_OWNED,
        InstallErrorCode.ERROR_API_NOT_AVAILABLE,
        InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND,
        InstallErrorCode.ERROR_INSTALL_UNAVAILABLE,
        -> PlayUpdateState.NotFromPlay

        InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED -> PlayUpdateState.Failed(NOT_ALLOWED_MESSAGE)

        else -> if (fromPlay) PlayUpdateState.Failed(CHECK_FAILED_MESSAGE) else PlayUpdateState.NotFromPlay
    }

    /**
     * 확인에 실패했을 때 남길 상태. 잠깐의 실패로 이미 알던 새 버전(알림 줄·업데이트 버튼)을 지우지 않는다.
     * Play 에서 설치한 앱이 아니라는 답은 잠깐의 실패가 아니라서 그대로 남긴다.
     */
    fun afterFailure(current: PlayUpdateState, failure: PlayUpdateState): PlayUpdateState =
        if (failure !is PlayUpdateState.NotFromPlay && current.versionCode != null) current else failure

    /**
     * 업데이트 확인 창(Play)을 닫고 돌아왔을 때의 상태. 따를 것이 없으면 null
     * 받기를 허락했으면 진행 상황이 따로 온다. 그 전에 알림 줄이 '받는 중' 으로 바로 바뀌게 한다.
     */
    fun afterFlow(current: PlayUpdateState, resultCode: Int): PlayUpdateState? = when (resultCode) {
        // 진행 상황이 먼저 와서 이미 '받는 중' 이면 그대로 둔다
        Activity.RESULT_OK -> (current as? PlayUpdateState.Available)?.let { PlayUpdateState.Downloading(it.versionCode, null) }

        ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> PlayUpdateState.Failed(START_FAILED_MESSAGE)

        // '나중에'(RESULT_CANCELED) 를 눌렀다. 새 버전은 그대로 있다.
        else -> null
    }

    /** 받은 양. 전체 크기를 아직 모르면 null */
    fun progressOf(bytesDownloaded: Long, totalBytes: Long): Float? =
        if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}
