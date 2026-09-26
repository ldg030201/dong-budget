package com.dong.budget

import android.app.Application
import com.dong.budget.data.AppContainer
import kotlin.concurrent.thread

class BudgetApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        thread(name = "update-startup") {
            // 설치가 끝나 이 버전이 켜졌다면 받아둔 설치 파일은 더 필요 없다. 캐시에 남기지 않는다.
            container.apkInstaller.deleteStaleDownloads(BuildConfig.VERSION_NAME)
            // 지난 확인 결과를 미리 읽어 둔다. 첫 화면(홈 배너)을 그리는 메인 스레드가 파일 읽기를 기다리지 않게 하기 위함이다.
            container.updateChecker
        }
        // 결제 등록 알림의 채널을 미리 만든다. 기기 설정의 알림 목록에 처음부터 보이게 하기 위함이다.
        thread(name = "notification-channel") { container.captureNotifier.ensureChannel() }
    }
}
