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
                context.startActivity(confirmIntent)
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "설치 완료")
                InstallEvents.publish(InstallEvent.Succeeded)
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w(TAG, "설치가 끝나지 않았다 (상태 $status): $message")
                InstallEvents.publish(
                    InstallEvent.Failed(
                        reason = describe(status, message),
                        detail = listOfNotNull("상태 $status", message?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        canTryOtherWays = !isDeadEnd(message),
                    ),
                )
            }
        }
    }

    /**
     * 시스템이 주는 영문 메시지를 사용자가 행동할 수 있는 안내로 바꾼다.
     *
     * 특히 검증 실패는 Play Protect 가 막은 경우가 대부분인데,
     * 그냥 두면 사용자는 아무 일도 일어나지 않은 것으로 느낀다.
     */
    private fun describe(status: Int, message: String?): String = when {
        // 사용자가 취소한 경우뿐 아니라, 갤럭시 등 제조사 설치기가 사용자가 '설치'를 눌렀는데도
        // 세션을 스스로 중단하는 경우에도 이 상태가 온다. 둘을 구별할 수 없으므로 '취소' 로 단정하지 않는다.
        status == PackageInstaller.STATUS_FAILURE_ABORTED ->
            "설치가 중단됐어요. 아래 '받은 파일로 직접 설치'로 다시 해보세요"

        // 서명 불일치를 가장 먼저 본다.
        // 안드로이드가 서명이 다를 때 주는 코드는 INSTALL_FAILED_UPDATE_INCOMPATIBLE 이라
        // 아래의 일반적인 INCOMPATIBLE 검사보다 반드시 앞에 있어야 한다.
        // 순서가 뒤바뀌면 서명 문제인데 "이 기기에서는 설치할 수 없다"고 잘못 안내하게 된다.
        message.containsAny("UPDATE_INCOMPATIBLE", "INCONSISTENT_CERTIFICATES", "SIGNATURE") ->
            "설치된 앱과 서명이 달라요. 기존 앱을 지우고 새로 설치해야 하는데 그러면 기록이 사라져요"

        message.containsAny("VERIFICATION_FAILURE") ->
            "기기 보안 검사에 막혔어요. 설치 화면에서 '무시하고 설치'를 눌러주세요"

        message.containsAny("VERSION_DOWNGRADE") ->
            "지금 쓰는 버전이 더 최신이에요"

        message.containsAny("INSUFFICIENT_STORAGE") ->
            "저장 공간이 부족해요"

        message.containsAny("INCOMPATIBLE", "INVALID_APK", "NO_MATCHING_ABIS") ->
            "이 기기에서는 설치할 수 없는 파일이에요"

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

    private companion object {
        const val TAG = "DongBudgetInstall"
    }
}
