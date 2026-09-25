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
        // 설치가 끝나 이 버전이 켜졌다면 받아둔 설치 파일은 더 필요 없다. 캐시에 남기지 않는다.
        thread(name = "update-cleanup") { container.apkInstaller.deleteStaleDownloads(BuildConfig.VERSION_NAME) }
    }
}
