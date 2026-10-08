package com.dong.budget.ui.permission

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.dong.budget.data.update.GalaxyAutoBlocker

/** GitHub 배포에만 있는 권한. 앱을 켤 때 알림 권한보다 먼저 묻고, 설정의 '권한' 묶음에서는 맨 아래에 보인다. */
internal val STORE_PERMISSIONS: List<AppPermission> = listOf(InstallUpdatesPermission)

/** '출처를 알 수 없는 앱 설치'. 앱 안에서 새 버전을 설치할 때 필요하다. */
object InstallUpdatesPermission : AppPermission {
    // 처음엔 다른 권한과 한 enum 에 있었다. 그때 적힌 '다시 안 보기' 기록과 맞게 같은 이름을 쓴다.
    override val name = "INSTALL_UPDATES"

    override val title = "업데이트 설치를 허용해 주세요"

    /**
     * 갤럭시 One UI 6 이상에는 '보안 위험 자동 차단' 이 있어서, 설치 허용을 켜도 이게 켜져 있으면 업데이트가 막힌다.
     * 켜짐 여부는 앱이 알 수 없으니 그런 기기에만 한 줄 덧붙이고, 끄는 화면으로 가는 버튼([extraLabel])을 둔다.
     */
    override val message: String
        get() = if (GalaxyAutoBlocker.isAvailable) BASE_MESSAGE + GALAXY_AUTO_BLOCKER_NOTE else BASE_MESSAGE

    override val label = "업데이트 설치"

    override val summary = "새 버전을 앱 안에서 바로 설치해요"

    override fun isGranted(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    override val extraLabel: String?
        get() = if (GalaxyAutoBlocker.isAvailable) "보안 위험 자동 차단 열기" else null

    override fun openExtra(context: Context) = GalaxyAutoBlocker.open(context)

    override fun settingsIntents(context: Context): List<Intent> =
        listOf(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri(context)))

    private const val BASE_MESSAGE =
        "새 버전을 앱 안에서 바로 설치하려면 '출처를 알 수 없는 앱 설치'에서 동계부를 허용해야 해요.\n" +
            "설정 화면에서 허용을 켜고 돌아와 주세요."

    /** 자동 차단이 있는 갤럭시에만 덧붙이는 안내 */
    private const val GALAXY_AUTO_BLOCKER_NOTE =
        "\n\n'보안 위험 자동 차단'도 꺼져 있어야 업데이트가 설치돼요. 아래 버튼으로 바로 갈 수 있어요."
}
