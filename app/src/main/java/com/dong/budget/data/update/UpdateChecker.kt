package com.dong.budget.data.update

import android.content.SharedPreferences
import androidx.core.content.edit
import com.dong.budget.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 새 버전 확인 결과를 앱 전체가 함께 쓴다.
 *
 * - 앱을 열 때마다(화면에 나올 때마다) [checkIfDue] 로 확인한다. 백그라운드에서 따로 돌지 않는다.
 *   다만 앱을 빠르게 오가며 여러 번 열면 마지막 확인에서 [INTERVAL_MS] 가 지나기 전까지는 건너뛴다.
 *   GitHub 는 로그인 없이 부를 수 있는 횟수가 시간당 60번으로 정해져 있어서다.
 * - 설정 화면에서 직접 확인한 결과와 패치노트가 받은 배포 목록도 [apply] 로 여기에 맞춘다.
 * - 찾은 새 버전은 저장해 둔다. 앱을 껐다 켜도 확인 간격 안이면 홈 배너가 바로 보인다.
 *
 * 저장소는 SharedPreferences 다. 자동 백업은 데이터베이스와 datastore 폴더만 담으므로
 * 이 값은 백업되지 않는다. 다른 기기로 옮겼을 때 지난 확인 결과가 따라가지 않는 편이 맞다.
 */
class UpdateChecker(
    /** 실제 확인. 보통은 UpdateRepository.check 이고, 테스트에서는 가짜로 바꾼다. */
    private val fetch: suspend () -> UpdateStatus,
    private val prefs: SharedPreferences,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * 확인 결과를 기록하기 직전에 할 일. 받아둔 설치 파일을 결과에 맞춰 정리하는 데 쓴다.
     * 기록보다 먼저 한다. 기록하는 순간 화면들이 새 결과를 보고 받아둔 파일을 다시 읽는데, 그때 이미 정리돼 있어야 한다.
     */
    private val beforeRecord: suspend (UpdateStatus) -> Unit = {},
) {
    private val found = MutableStateFlow(loadSaved())

    /** 지금 설치된 것보다 새 버전. 없으면 null */
    val available: StateFlow<UpdateStatus.Available?> = found.asStateFlow()

    /** 이번 실행에서 배너를 닫은 버전. 앱을 다시 켜면 배너가 다시 보인다. */
    private val dismissed = MutableStateFlow<String?>(null)

    /** 홈 배너에 보여줄 새 버전. 없거나 닫았으면 null */
    val bannerVersion: Flow<String?> = combine(found, dismissed) { update, closed -> update?.version?.takeIf { it != closed } }

    private val mutex = Mutex()

    /** 마지막 확인에서 [INTERVAL_MS] 가 지났을 때만 확인한다. 여러 번 겹쳐 불려도 한 번만 돈다. */
    suspend fun checkIfDue() = mutex.withLock {
        // 한 번도 확인한 적이 없으면 바로 확인한다
        if (prefs.contains(KEY_CHECKED_AT)) {
            val elapsed = now() - prefs.getLong(KEY_CHECKED_AT, 0L)
            // 시계가 뒤로 간 경우(음수)에도 다시 확인한다
            if (elapsed in 0 until INTERVAL_MS) return@withLock
        }
        apply(fetch())
    }

    /**
     * 어디서 확인했든(자동 확인, 설정의 '업데이트 확인', 패치노트의 배포 목록) 결과를 이 한 곳으로 반영한다.
     * 받아둔 설치 파일을 먼저 정리하고, 그다음 기록한다. 홈 배너와 설정 화면은 기록된 결과를 따라간다.
     */
    suspend fun apply(status: UpdateStatus) {
        beforeRecord(status)
        record(status)
    }

    /** 마지막 확인에서 [INTERVAL_MS] 가 지나지 않았는지. 그 안이면 같은 정보를 다시 받을 필요가 없다. */
    fun checkedRecently(): Boolean {
        if (!prefs.contains(KEY_CHECKED_AT)) return false
        return now() - prefs.getLong(KEY_CHECKED_AT, 0L) in 0 until INTERVAL_MS
    }

    /** 확인 결과를 반영한다. 실패는 남기지 않는다. 다음에 앱을 열 때 다시 확인하게 하기 위함이다. */
    private fun record(status: UpdateStatus) {
        when (status) {
            is UpdateStatus.Available -> {
                found.value = status.takeIf { isNewer(it.version) }
                save(found.value)
            }

            UpdateStatus.UpToDate -> {
                found.value = null
                save(null)
            }

            is UpdateStatus.Failed -> Unit
        }
    }

    fun dismissBanner() {
        dismissed.value = found.value?.version
    }

    private fun isNewer(version: String): Boolean {
        val candidate = AppVersion.parse(version) ?: return false
        val current = AppVersion.parse(currentVersion) ?: return false
        return candidate > current
    }

    private fun loadSaved(): UpdateStatus.Available? {
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        // 그사이 업데이트를 마쳤으면 저장된 결과는 이미 지난 것이다
        if (!isNewer(version)) return null
        return UpdateStatus.Available(
            version = version,
            notes = prefs.getString(KEY_NOTES, null).orEmpty(),
            downloadUrl = prefs.getString(KEY_URL, null) ?: return null,
            sizeBytes = prefs.getLong(KEY_SIZE, 0L),
        )
    }

    private fun save(update: UpdateStatus.Available?) {
        prefs.edit {
            putLong(KEY_CHECKED_AT, now())
            if (update == null) {
                remove(KEY_VERSION)
                remove(KEY_NOTES)
                remove(KEY_URL)
                remove(KEY_SIZE)
            } else {
                putString(KEY_VERSION, update.version)
                putString(KEY_NOTES, update.notes)
                putString(KEY_URL, update.downloadUrl)
                putLong(KEY_SIZE, update.sizeBytes)
            }
        }
    }

    companion object {
        /** SharedPreferences 파일 이름 */
        const val PREFS_NAME = "update_check"

        /** 자동 확인 사이 최소 간격. 10분 */
        const val INTERVAL_MS = 10 * 60 * 1000L

        private const val KEY_CHECKED_AT = "checked_at"
        private const val KEY_VERSION = "version"
        private const val KEY_NOTES = "notes"
        private const val KEY_URL = "url"
        private const val KEY_SIZE = "size"
    }
}
