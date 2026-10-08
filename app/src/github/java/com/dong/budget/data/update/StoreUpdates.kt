package com.dong.budget.data.update

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.dong.budget.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.concurrent.thread

/**
 * GitHub 배포의 새 버전 확인과 설치. GitHub Releases 에 올라온 APK 를 확인하고([updateChecker]),
 * 내려받아 앱 안에서 설치한다([apkInstaller]). 앱 정보의 업데이트 칸(AppInfoRoute)은 이 둘을 직접 쓴다.
 *
 * @param scope 앱이 살아 있는 동안 도는 범위. 확인 결과를 공통 화면이 보는 모양으로 바꿔 둔다.
 */
class StoreUpdates(private val context: Context, private val scope: CoroutineScope) : AppUpdates {
    private val updateRepository by lazy { UpdateRepository() }

    val apkInstaller by lazy { ApkInstaller(context) }

    val updateChecker by lazy {
        UpdateChecker(
            fetch = updateRepository::newerReleases,
            prefs = context.getSharedPreferences(UpdateChecker.PREFS_NAME, Context.MODE_PRIVATE),
        )
    }

    override fun start(application: Application) {
        application.registerActivityLifecycleCallbacks(ScreenWatcher)
        thread(name = "update-startup") {
            // 설치가 끝나 이 버전이 켜졌다면 받아둔 설치 파일은 더 필요 없다. 캐시에 남기지 않는다.
            apkInstaller.deleteStaleDownloads(BuildConfig.VERSION_NAME)
            // 지난 확인 결과를 미리 읽어 둔다. 첫 화면(홈 배너)을 그리는 메인 스레드가 파일 읽기를 기다리지 않게 하기 위함이다.
            updateChecker
        }
    }

    override suspend fun checkIfDue() = updateChecker.checkIfDue()

    // 처음 볼 때 만든다. 앱이 켜질 때 만들면 메인 스레드가 지난 확인 결과(파일)를 읽게 된다.
    override val newer: StateFlow<UpdateNotice?> by lazy {
        updateChecker.newer.map(::latestOf).stateIn(scope, SharingStarted.Eagerly, latestOf(updateChecker.newer.value))
    }

    override val notice: Flow<UpdateNotice?>
        get() = updateChecker.bannerVersion.map { version -> version?.let(UpdateNotice::Available) }

    override val newerNotes: StateFlow<List<NewerNotes>> by lazy {
        updateChecker.newer.map(::notesOf).stateIn(scope, SharingStarted.Eagerly, notesOf(updateChecker.newer.value))
    }

    override fun dismissNotice() = updateChecker.dismissBanner()

    override fun skipNotice() = updateChecker.skipLatest()

    private fun latestOf(releases: List<NewerRelease>): UpdateNotice? = releases.firstOrNull()?.let { UpdateNotice.Available(it.version) }

    private fun notesOf(releases: List<NewerRelease>): List<NewerNotes> = releases.map { NewerNotes(it.version, it.date, it.notes) }
}

/**
 * 동계부 화면이 보이는지 적고([InstallEvents.appVisible]), 안 보일 때 도착해서 띄우지 못한 설치 확인창을
 * 동계부로 돌아올 때 띄운다. 화면이 안 보이면 설치 확인창을 띄워도 안드로이드가 막기 때문이다(InstallResultReceiver).
 */
private object ScreenWatcher : Application.ActivityLifecycleCallbacks {
    override fun onActivityStarted(activity: Activity) {
        InstallEvents.appVisible = true
    }

    override fun onActivityStopped(activity: Activity) {
        InstallEvents.appVisible = false
    }

    override fun onActivityResumed(activity: Activity) {
        // 받는 동안 다른 앱에 있어서 띄우지 못한 설치 확인창이 있으면 지금 띄운다
        InstallEvents.takePendingConfirm()?.let { runCatching { activity.startActivity(it) } }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
