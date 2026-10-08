package com.dong.budget

import android.app.Application
import com.dong.budget.data.AppContainer
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.ui.permission.HiddenPermissionPrompts
import kotlin.concurrent.thread

class BudgetApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 가장 먼저 둔다. 개발자 모드를 켜 두었다면 이 뒤에 앱이 죽어도 오류가 남는다.
        DevLog.init(this)
        container = AppContainer(this)
        // 자동 기능 스위치를 지금부터 읽어 둔다. 첫 화면(강제 종료 뒤 되살아난 등록창 등)이 그려질 즈음엔 저장된 값이 와 있게 한다.
        container.autoSettings
        container.themeMode
        container.weekStart
        container.backupSchedule
        container.bottomMenu
        // 잠금·백업 기록 파일을 미리 읽어 둔다. 첫 화면(앱 잠금)과 설정을 그리는 메인 스레드가 파일 읽기를 기다리지 않게 한다.
        thread(name = "lock-startup") {
            container.appLock
            container.salaryLock
            container.backupHistory
            HiddenPermissionPrompts.warmUp(this)
        }
        // 새 버전 확인을 준비한다(지난 확인 결과 미리 읽기 등). 배포처마다 하는 일이 다르다.
        container.updates.start(this)
        // 결제 등록·월급날 알림의 채널을 미리 만든다. 기기 설정의 알림 목록에 처음부터 보이게 하기 위함이다.
        thread(name = "notification-channel") {
            container.captureNotifier.ensureChannel()
            container.salaryNotifier.ensureChannel()
        }
        container.startSalaryAlarm()
    }
}
