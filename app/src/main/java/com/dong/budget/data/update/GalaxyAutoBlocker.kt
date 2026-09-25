package com.dong.budget.data.update

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * 갤럭시의 '보안 위험 자동 차단'(Auto Blocker) 설정.
 *
 * 이 기능이 켜져 있으면 삼성이 스토어 밖에서 설치한 앱의 설치와 업데이트를 막는다.
 * 앱이 자기 자신을 업데이트하면 'Self update is blocked by unknown source package' 로 중단되고,
 * 받은 파일을 설치 화면으로 열거나 브라우저로 받아도 막힌다. 켜짐 여부는 앱이 읽을 수 없어서
 * 설치가 막혔을 때와 설치 허용을 안내할 때 이 화면으로 가는 버튼을 둔다.
 */
object GalaxyAutoBlocker {
    /**
     * 이 기기에 자동 차단 기능이 있는지. 갤럭시 One UI 6(Android 14)부터 생긴 기능이다.
     * 다른 기기나 그 전 갤럭시에는 이 설정이 없으므로 안내와 버튼을 보이지 않는다.
     */
    val isAvailable: Boolean
        get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * 자동 차단 스위치가 있는 화면을 연다. 앞의 것이 안 열리면 다음 것을 연다.
     * 1. One UI 7 이후: 자동 차단이 설정 앱에서 떨어져 나온 별도 앱의 설정 화면(스위치가 바로 보인다)
     * 2. 그 전 One UI: 설정 앱 안의 자동 차단 화면
     * 3. 그래도 안 되면 보안 설정 화면. 여기서 '보안 위험 자동 차단' 을 찾아 들어가면 된다.
     *
     * @return 어느 화면이든 열었는지
     */
    fun open(context: Context): Boolean = candidates().any { runCatching { context.startActivity(it) }.isSuccess }

    private fun candidates(): List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.samsung.android.rampart", "com.samsung.android.rampart.ui.MainSettingActivity")),
        Intent("com.samsung.android.rampart.action.MAIN_SETTING_ACTIVITY"),
        Intent("com.samsung.android.settings.AUTO_BLOCKER"),
        Intent().setComponent(
            ComponentName("com.android.settings", "com.samsung.android.settings.autoblocker.AutoBlockerSettingsActivity"),
        ),
        Intent("android.settings.SECURITY_ADVANCED_SETTINGS"),
        Intent(Settings.ACTION_SECURITY_SETTINGS),
    )
}
