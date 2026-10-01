package com.dong.budget.ui.permission

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 앱을 켤 때 뜨는 권한 안내창([PermissionGate])에서 '다시 안 보기' 를 누른 권한.
 * 그 권한은 꺼져 있어도 다시 안내하지 않는다. 설정의 '권한' 묶음에는 그대로 보여서 거기서 켤 수 있다.
 * 직접 누른 버튼(앱 정보의 설치 권한 등)으로 뜨는 안내창은 이 기록과 상관없이 뜬다.
 */
class HiddenPermissionPrompts(private val prefs: SharedPreferences) {
    fun isHidden(permission: AppPermission): Boolean = prefs.getBoolean(KEY_PREFIX + permission.name, false)

    fun hide(permission: AppPermission) {
        prefs.edit { putBoolean(KEY_PREFIX + permission.name, true) }
    }

    companion object {
        /** SharedPreferences 파일 이름 */
        const val PREFS_NAME = "permission_prompts"

        /** 파일을 미리 읽어 둔다. 앱을 켤 때 뒤 스레드에서 불러, 첫 화면의 권한 안내가 파일 읽기를 기다리지 않게 한다. */
        fun warmUp(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(KEY_PREFIX)
        }

        private const val KEY_PREFIX = "hidden:"
    }
}
