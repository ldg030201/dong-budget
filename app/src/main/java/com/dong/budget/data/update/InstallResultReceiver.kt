package com.dong.budget.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat

/**
 * 설치 세션의 진행 상황을 받는다.
 *
 * 가장 중요한 경우는 STATUS_PENDING_USER_ACTION 이다.
 * 이때 시스템이 건네준 인텐트를 띄워야 사용자에게 설치 확인창이 보인다.
 * 이걸 띄우지 않으면 내려받기만 끝나고 아무 일도 일어나지 않는다.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 지난 시도의 세션 결과가 늦게 도착하면 이번 시도의 결과처럼 보인다. 지금 세션의 것만 받는다.
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, InstallEvents.NO_SESSION)
        val active = InstallEvents.activeSessionId
        if (active != InstallEvents.NO_SESSION && sessionId != InstallEvents.NO_SESSION && sessionId != active) {
            Log.i(TAG, "지난 세션($sessionId)의 결과라 무시한다. 지금 세션은 $active")
            return
        }
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent =
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmIntent == null) {
                    Log.w(TAG, "설치 확인 인텐트가 없다")
                    return
                }
                // 브로드캐스트에서 화면을 띄우려면 새 작업으로 시작해야 한다.
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 동계부 화면이 안 보이면(받는 동안 다른 앱으로 나감) 안드로이드가 확인창을 조용히 막는다.
                // 그때는 맡겨 두었다가 동계부로 돌아올 때 띄운다(MainActivity.onResume).
                if (InstallEvents.appVisible) {
                    runCatching { context.startActivity(confirmIntent) }.onFailure { InstallEvents.holdConfirm(confirmIntent) }
                } else {
                    InstallEvents.holdConfirm(confirmIntent)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "설치 완료")
                InstallEvents.publish(InstallEvent.Succeeded)
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                // 공개 상태값은 여러 원인을 하나로 뭉친다(보안 검사 거부도 '중단' 으로 온다).
                // 숨은 추가 정보인 내부 코드가 있으면 원인을 더 정확히 가를 수 있다.
                val legacy = intent.getIntExtra(EXTRA_LEGACY_STATUS, 0)
                Log.w(TAG, "설치가 끝나지 않았다 (상태 $status, 내부 $legacy): $message")
                InstallEvents.publish(failureOf(status, legacy, message, autoBlocker = GalaxyAutoBlocker.isAvailable))
            }
        }
    }

    companion object {
        private const val TAG = "DongBudgetInstall"

        /** 공개되지 않은 추가 정보. 공개 상태값보다 자세한 내부 코드가 들어 있다. */
        private const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"

        // PackageManager 의 내부 설치 실패 코드
        private const val LEGACY_VERIFICATION_TIMEOUT = -21
        private const val LEGACY_VERIFICATION_FAILURE = -22

        /**
         * 설치 실패를 사용자 안내로 바꾼다.
         * @param autoBlocker 자동 차단 기능이 있는 갤럭시인지(One UI 6+). 중단된 설치에 '보안 위험 자동 차단' 안내와 버튼을 붙인다.
         */
        internal fun failureOf(status: Int, legacy: Int, message: String?, autoBlocker: Boolean): InstallEvent.Failed = InstallEvent.Failed(
            reason = describe(status, legacy, message, autoBlocker),
            detail =
            listOfNotNull(
                "상태 $status",
                legacy.takeIf { it != 0 }?.let { "내부 코드 $it" },
                message?.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            canTryOtherWays = !isDeadEnd(message),
            // 갤럭시에서 중단된 설치는 자동 차단 때문일 수 있다. 보안 검사 거부처럼 원인이 따로 분명한 경우는 뺀다.
            suggestGalaxySecurity =
            isBlockedByGalaxySecurity(message) ||
                (autoBlocker && status == PackageInstaller.STATUS_FAILURE_ABORTED && legacy !in VERIFICATION_CODES),
        )

        private val VERIFICATION_CODES = setOf(LEGACY_VERIFICATION_TIMEOUT, LEGACY_VERIFICATION_FAILURE)

        /** 삼성 자동 차단이 막았을 때의 문구. 'Self update is blocked by unknown source package' */
        private fun isBlockedByGalaxySecurity(message: String?): Boolean = message.containsAny("unknown source", "Self update is blocked")

        /**
         * 시스템이 주는 영문 메시지를 사용자가 행동할 수 있는 안내로 바꾼다.
         *
         * 특히 검증 실패는 Play Protect 가 막은 경우가 대부분인데,
         * 그냥 두면 사용자는 아무 일도 일어나지 않은 것으로 느낀다.
         */
        private fun describe(status: Int, legacy: Int, message: String?, autoBlocker: Boolean): String = when {
            // 보안 검사(Play 프로텍트 등) 거부와 시간 초과는 공개 상태값으로는 '중단(ABORTED)' 으로 온다.
            // 그래서 '중단' 보다 먼저 가려내야 한다. 순서가 바뀌면 이 안내에 영영 닿지 못한다.
            // 시간 초과도 내부 코드는 거부와 같은 -22 로 오고 메시지에만 'timed out' 이 붙으므로 거부보다 먼저 본다.
            legacy == LEGACY_VERIFICATION_TIMEOUT || message.containsAny("VERIFICATION_TIMEOUT", "timed out") ->
                "보안 검사가 오래 걸려 설치가 멈췄어요. 잠시 뒤 다시 해보세요"

            legacy == LEGACY_VERIFICATION_FAILURE || message.containsAny("VERIFICATION_FAILURE") ->
                "Play 프로텍트 검사에서 설치가 멈췄어요. 다시 설치하고 '앱 검사 권장됨' 창이 뜨면 " +
                    "'앱 설치 안함' 대신 '앱 검사'를 눌러주세요"

            // 서명 불일치를 가장 먼저 본다.
            // 안드로이드가 서명이 다를 때 주는 코드는 INSTALL_FAILED_UPDATE_INCOMPATIBLE 이라
            // 아래의 일반적인 INCOMPATIBLE 검사보다 반드시 앞에 있어야 한다.
            // 순서가 뒤바뀌면 서명 문제인데 "이 기기에서는 설치할 수 없다"고 잘못 안내하게 된다.
            message.containsAny("UPDATE_INCOMPATIBLE", "INCONSISTENT_CERTIFICATES", "SIGNATURE") ->
                "설치된 앱과 서명이 달라요. 기존 앱을 지우고 새로 설치해야 하는데 그러면 기록이 사라져요"

            message.containsAny("VERSION_DOWNGRADE") ->
                "지금 쓰는 버전이 더 최신이에요"

            message.containsAny("INSUFFICIENT_STORAGE") ->
                "저장 공간이 부족해요"

            message.containsAny("INCOMPATIBLE", "INVALID_APK", "NO_MATCHING_ABIS") ->
                "이 기기에서는 설치할 수 없는 파일이에요"

            // 갤럭시 '보안 위험 자동 차단' 이 켜져 있으면 삼성이 앱의 자기 업데이트를 이 문구로 막는다.
            // 받은 파일로 설치하거나 브라우저로 받아도 똑같이 막히므로 자동 차단부터 끄게 한다.
            isBlockedByGalaxySecurity(message) ->
                "갤럭시 '보안 위험 자동 차단'이 켜져 있어서 업데이트가 막혔어요. 아래 버튼으로 자동 차단을 끄고 " +
                    "다시 설치해 주세요"

            // 사용자가 취소한 경우뿐 아니라, 갤럭시 등 제조사 설치기가 사용자가 '설치'를 눌렀는데도
            // 세션을 스스로 중단하는 경우에도 이 상태가 온다. 둘을 구별할 수 없으므로 '취소' 로 단정하지 않는다.
            status == PackageInstaller.STATUS_FAILURE_ABORTED && autoBlocker ->
                "설치가 중단됐어요. 갤럭시는 '보안 위험 자동 차단'이 켜져 있으면 설치가 막혀요. " +
                    "아래 버튼으로 꺼져 있는지 확인하고 다시 해보세요"

            status == PackageInstaller.STATUS_FAILURE_ABORTED ->
                "설치가 중단됐어요. 아래 '받은 파일로 설치'로 다시 해보세요"

            else -> "설치를 마치지 못했어요"
        }

        /** 다른 설치 길로도 풀리지 않는 실패. 서명 불일치, 낮은 버전, 기기와 맞지 않는 파일, 저장 공간 부족 */
        private fun isDeadEnd(message: String?): Boolean = message.containsAny(
            "UPDATE_INCOMPATIBLE",
            "INCONSISTENT_CERTIFICATES",
            "SIGNATURE",
            "VERSION_DOWNGRADE",
            "INSUFFICIENT_STORAGE",
            "INCOMPATIBLE",
            "INVALID_APK",
            "NO_MATCHING_ABIS",
        )

        private fun String?.containsAny(vararg needles: String): Boolean = this != null && needles.any { contains(it) }
    }
}
