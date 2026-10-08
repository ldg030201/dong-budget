package com.dong.budget.data.update

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.net.toUri
import com.dong.budget.startFirst
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.ktx.requestCompleteUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

/**
 * Play 배포의 새 버전 확인과 업데이트. Google Play 인앱 업데이트(FLEXIBLE)를 쓴다.
 * 받기와 설치는 Play 스토어가 한다. 앱은 새 버전이 있는지 묻고, 받기를 시작하고, 다 받으면 다시 시작하게 할 뿐이다.
 * 받는 동안 앱을 계속 쓸 수 있다.
 *
 * 앱 정보(AppInfoRoute)와 홈 알림 줄의 누름(rememberUpdateOpener)이 [checker]·[startUpdate]·[completeUpdate] 를 직접 쓴다.
 *
 * @param scope 앱이 살아 있는 동안 도는 범위. 화면을 나가도 확인과 시작이 끊기지 않게 여기서 한다.
 */
class StoreUpdates(private val context: Context, private val scope: CoroutineScope) : AppUpdates {
    private val manager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(context) }

    val checker by lazy {
        PlayUpdateChecker(
            fetch = { runCatching { infoOf(manager.requestAppUpdateInfo()) } },
            fromPlay = { isFromPlay },
            prefs = context.getSharedPreferences(PlayUpdateChecker.PREFS_NAME, Context.MODE_PRIVATE),
            errorCodeOf = { (it as? InstallException)?.errorCode },
        )
    }

    /** Play 스토어가 설치한 앱인지. 앱이 켜 있는 동안 바뀌지 않는다(Play 가 업데이트하면 앱이 다시 시작된다). */
    private val isFromPlay: Boolean by lazy {
        runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName == PLAY_STORE_PACKAGE }
            .getOrDefault(false)
    }

    /** 받는 동안 Play 가 알려 주는 진행 상황. 새 버전을 모르는 상태에서 오면(앱을 다시 켜 아직 확인 전) Play 에 다시 묻는다. */
    private val installListener = InstallStateUpdatedListener { state ->
        val applied = checker.onInstallState(
            state.installStatus(),
            state.bytesDownloaded(),
            state.totalBytesToDownload(),
            state.installErrorCode(),
        )
        if (!applied) scope.launch { checker.check() }
    }

    override fun start(application: Application) {
        thread(name = "update-startup") {
            // 건너뛴 버전 기록을 미리 읽어 둔다. 첫 화면을 그리는 메인 스레드가 파일 읽기를 기다리지 않게 하기 위함이다.
            checker
            // 앱이 꺼져 있던 사이 시작한 받기가 이어지고 있을 수 있다. 앱이 켜져 있는 동안 늘 진행 상황을 듣는다.
            manager.registerListener(installListener)
        }
    }

    override suspend fun checkIfDue() = checker.check()

    /** 앱 정보의 '업데이트 확인'. 화면을 나가도 확인이 끊기지 않게 앱 범위에서 한다. */
    fun check() {
        scope.launch { checker.check() }
    }

    // 처음 볼 때 만든다. 앱이 켜질 때 만들면 메인 스레드가 건너뛴 버전 기록(파일)을 읽게 된다.
    override val newer: StateFlow<UpdateNotice?> by lazy {
        checker.state.map { it.notice }.stateIn(scope, SharingStarted.Eagerly, checker.state.value.notice)
    }

    override val notice: Flow<UpdateNotice?>
        get() = checker.notice

    // Play 는 설치하기 전에 새 버전의 바뀐 점을 알려 주지 않는다. 업데이트한 뒤 앱에 든 패치노트에서 본다.
    override val newerNotes: StateFlow<List<NewerNotes>> = MutableStateFlow<List<NewerNotes>>(emptyList()).asStateFlow()

    override fun dismissNotice() = checker.dismiss()

    override fun skipNotice() = checker.skip()

    /**
     * 업데이트를 시작한다. Play 의 확인 창이 뜨고, 사용자가 받기를 고르면 Play 가 뒤에서 받는다.
     * 확인 창의 결과는 [launcher] 를 만든 화면이 [onUpdateFlowResult] 로 넘긴다.
     * 앱 안에서 받을 수 없는 업데이트면 Play 스토어의 동계부 화면을 연다.
     */
    fun startUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        scope.launch(Dispatchers.Main) {
            // 업데이트는 새로 받은 정보로만 시작할 수 있다(한 번 쓴 정보는 다시 못 쓴다). 그사이 바뀐 상태도 함께 맞춘다.
            val info =
                try {
                    manager.requestAppUpdateInfo()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    checker.onFailure(PlayUpdateRules.START_FAILED_MESSAGE, e)
                    return@launch
                }
            checker.record(infoOf(info))
            // 그사이 받기 시작했거나 다 받았으면 새로 시작하지 않는다. 알림 줄과 앱 정보가 바뀐 상태를 보여 준다.
            val available = checker.state.value as? PlayUpdateState.Available ?: return@launch
            if (!available.flexibleAllowed) {
                openPlayStore()
                return@launch
            }
            runCatching { manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()) }
                .onFailure { checker.onFailure(PlayUpdateRules.START_FAILED_MESSAGE, it) }
        }
    }

    /** Play 의 업데이트 확인 창을 닫고 돌아왔다 */
    fun onUpdateFlowResult(resultCode: Int) = checker.onFlowResult(resultCode)

    /** 다 받은 새 버전을 설치한다. Play 가 앱을 닫고 새 버전으로 다시 연다. */
    fun completeUpdate() {
        scope.launch {
            try {
                manager.requestCompleteUpdate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                checker.onFailure(PlayUpdateRules.COMPLETE_FAILED_MESSAGE, e)
            }
        }
    }

    /** Play 스토어의 동계부 화면. 스토어 앱이 없으면 웹으로 연다. */
    private fun openPlayStore() {
        val id = context.packageName
        context.startFirst(
            listOf(
                Intent(Intent.ACTION_VIEW, "market://details?id=$id".toUri()).setPackage(PLAY_STORE_PACKAGE),
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$id".toUri()),
            ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }

    private fun infoOf(info: AppUpdateInfo) = PlayUpdateInfo(
        availability = info.updateAvailability(),
        installStatus = info.installStatus(),
        versionCode = info.availableVersionCode(),
        flexibleAllowed = info.isFlexibleUpdateAllowed,
        bytesDownloaded = info.bytesDownloaded(),
        totalBytes = info.totalBytesToDownload(),
    )

    private companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
