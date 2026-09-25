package com.dong.budget.ui.permission

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 시스템 '허용' 창을 또 띄울 수 있는지 판단하려고 지난 결과를 적어 둔다.
 *
 * 두 번 거절하면 시스템은 더는 창을 띄우지 않고 바로 거절로 돌려준다. 그때는 설정 화면으로 보내야 한다.
 * 그런데 앱이 받는 결과는 '허용' 과 '거절' 뿐이라서, 창을 뒤로가기로 그냥 닫은 경우와
 * 시스템이 창 없이 거절한 경우가 똑같이 보인다. 그래서 결과 전후의 shouldShowRequestPermissionRationale 를 함께 보고,
 * 그래도 구별할 수 없으면 횟수로 판단한다.
 *
 * 창을 띄우기 전이 아니라 결과를 받은 뒤에 적는다. 창을 그냥 닫은 사람을 '두 번 거절한 사람' 으로 보지 않기 위함이다.
 */
class SystemPromptHistory(private val prefs: SharedPreferences) {
    /**
     * 시스템 창을 띄워 볼 만한지. false 면 설정 화면으로 보낸다.
     * @param rationaleNow 지금의 shouldShowRequestPermissionRationale. true 면 한 번 거절한 상태라 시스템이 창을 다시 띄운다.
     */
    fun canPrompt(permission: String, rationaleNow: Boolean): Boolean = rationaleNow || unanswered(permission) < MAX_UNANSWERED

    /**
     * 시스템 창의 결과를 적는다.
     * @param rationaleBefore 창을 띄우기 직전의 shouldShowRequestPermissionRationale
     * @param rationaleAfter 결과를 받은 직후의 값
     */
    fun record(permission: String, granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean) {
        val next =
            when {
                granted -> 0

                // 한 번 거절했다. 시스템은 다음에도 창을 띄운다.
                rationaleAfter -> 0

                // 한 번 거절한 뒤 또 거절했다. 이제 시스템은 창을 띄우지 않는다.
                rationaleBefore -> MAX_UNANSWERED

                // 창을 그냥 닫았거나, 시스템이 창 없이 거절했다. 구별할 수 없어 횟수로 본다.
                else -> unanswered(permission) + 1
            }
        prefs.edit { putInt(KEY_PREFIX + permission, next) }
    }

    /**
     * 답 없이 끝난 횟수. 0.1.6 은 창을 띄우기 전에 '물어봤음' 만 적었다. 그 기록이 있으면 한 번으로 본다.
     */
    private fun unanswered(permission: String): Int =
        prefs.getInt(KEY_PREFIX + permission, if (prefs.getBoolean(permission, false)) 1 else 0)

    companion object {
        /** SharedPreferences 파일 이름. 0.1.6 과 같은 파일을 쓴다. */
        const val PREFS_NAME = "permission_requests"

        /** 이만큼 답 없이 끝나면 시스템이 창을 띄우지 않는 것으로 보고 설정 화면으로 보낸다 */
        private const val MAX_UNANSWERED = 2

        private const val KEY_PREFIX = "unanswered:"
    }
}
