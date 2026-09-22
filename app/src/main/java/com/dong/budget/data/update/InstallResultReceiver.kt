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

            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "설치 완료")

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w(TAG, "설치가 끝나지 않았다 (상태 $status): $message")
            }
        }
    }

    private companion object {
        const val TAG = "DongBudgetInstall"
    }
}
