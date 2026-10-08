package com.dong.budget.data.update

import android.content.SharedPreferences
import androidx.core.content.edit
import com.dong.budget.BuildConfig
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 새 버전 확인 결과를 앱 전체가 함께 쓴다. 새 버전을 받아오는 곳은 여기 하나다.
 *
 * - 앱을 열 때마다(화면에 나올 때마다) [checkIfDue] 로 확인한다. 백그라운드에서 따로 돌지 않는다.
 *   다만 앱을 빠르게 오가며 여러 번 열면 마지막 확인에서 [INTERVAL_MS] 가 지나기 전까지는 건너뛴다.
 *   GitHub 는 로그인 없이 부를 수 있는 횟수가 시간당 60번으로 정해져 있어서다.
 * - 앱 정보(설정)의 '업데이트 확인' 은 간격과 상관없이 [checkNow] 로 확인한다.
 * - 홈 배너, 설정 화면, 패치노트는 모두 [newer] 를 따라간다. 홈 배너만 닫은 버전·건너뛴 버전을 뺀다([bannerVersion]).
 * - 찾은 새 버전들은 저장해 둔다. 앱을 껐다 켜도 확인 간격 안이면 배너와 패치노트가 바로 보인다.
 *
 * 저장소는 SharedPreferences 다. 자동 백업은 데이터베이스와 datastore 폴더만 담으므로
 * 이 값은 백업되지 않는다. 다른 기기로 옮겼을 때 지난 확인 결과가 따라가지 않는 편이 맞다.
 */
class UpdateChecker(
    /** 실제 확인. 보통은 UpdateRepository.newerReleases 이고, 테스트에서는 가짜로 바꾼다. */
    private val fetch: suspend () -> Result<List<NewerRelease>>,
    private val prefs: SharedPreferences,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val releases = MutableStateFlow(loadSaved())

    /** 아직 설치하지 않은 새 버전들. 최신순. 없으면 빈 목록 */
    val newer: StateFlow<List<NewerRelease>> = releases.asStateFlow()

    /** 이번 실행에서 배너를 닫은 버전. 앱을 다시 켜면 배너가 다시 보인다. */
    private val dismissed = MutableStateFlow<String?>(null)

    /** '이 버전 건너뛰기' 로 건너뛴 버전. 이 버전까지는 홈에 알리지 않고, 더 새 버전이 나오면 다시 알린다. */
    private val skipped = MutableStateFlow(prefs.getString(KEY_SKIPPED, null))

    /** 홈 배너에 보여줄 새 버전. 없거나 닫았거나 건너뛴 버전이면 null */
    val bannerVersion: Flow<String?> =
        combine(releases, dismissed, skipped) { list, closed, skip ->
            list.firstOrNull()?.version?.takeIf { it != closed && !isSkipped(it, skip) }
        }

    private val mutex = Mutex()

    /** 마지막 확인에서 [INTERVAL_MS] 가 지났을 때만 확인한다. 여러 번 겹쳐 불려도 한 번만 돈다. */
    suspend fun checkIfDue() {
        mutex.withLock { if (!checkedRecently()) checkNow() }
    }

    /** 간격과 상관없이 지금 확인한다. 실패는 남기지 않는다. 다음에 앱을 열 때 다시 확인하게 하기 위함이다. */
    suspend fun checkNow(): Result<List<NewerRelease>> = fetch()
        .onSuccess(::record)
        .onSuccess { releases -> DevLog.info(LogTag.UPDATE, "새 버전 확인: ${releases.firstOrNull()?.version ?: "최신"}") }
        .onFailure { DevLog.warn(LogTag.UPDATE, "새 버전을 확인하지 못했어요", it) }

    fun dismissBanner() {
        dismissed.value = releases.value.firstOrNull()?.version
    }

    /**
     * 가장 새 버전을 건너뛴다. 앱을 다시 켜도 그 버전은 홈에 알리지 않는다.
     * 앱 정보와 패치노트에는 그대로 보여서 마음이 바뀌면 거기서 설치할 수 있다.
     */
    fun skipLatest() {
        val version = releases.value.firstOrNull()?.version ?: return
        skipped.value = version
        prefs.edit { putString(KEY_SKIPPED, version) }
        DevLog.info(LogTag.UPDATE, "$version 건너뛰기")
    }

    // 한 번도 확인한 적이 없거나 시계가 뒤로 갔으면(음수) 지난 것으로 본다
    private fun checkedRecently(): Boolean =
        prefs.contains(KEY_CHECKED_AT) && now() - prefs.getLong(KEY_CHECKED_AT, 0L) in 0 until INTERVAL_MS

    private fun record(list: List<NewerRelease>) {
        releases.value = list
        prefs.edit {
            putLong(KEY_CHECKED_AT, now())
            putString(KEY_RELEASES, json.encodeToString(list.map(SavedRelease::of)))
            // 0.1.7 까지는 가장 새 버전 하나를 칸마다 따로 적었다
            LEGACY_KEYS.forEach(::remove)
        }
    }

    /** [version] 이 건너뛴 버전([skip])보다 새롭지 않은지. 버전을 읽을 수 없으면 건너뛰지 않은 것으로 본다. */
    private fun isSkipped(version: String, skip: String?): Boolean {
        val skippedVersion = AppVersion.parse(skip) ?: return false
        val shown = AppVersion.parse(version) ?: return false
        return shown <= skippedVersion
    }

    private fun loadSaved(): List<NewerRelease> {
        val saved = prefs.getString(KEY_RELEASES, null) ?: return emptyList()
        val current = AppVersion.parse(currentVersion) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SavedRelease>>(saved) }
            .getOrDefault(emptyList())
            .map { it.toRelease() }
            // 그사이 업데이트를 마쳤으면 그 버전까지는 이미 지난 것이다
            .filter { release -> AppVersion.parse(release.version)?.let { it > current } == true }
    }

    /** 저장하는 모양. 날짜는 글자로 적는다. */
    @Serializable
    private data class SavedRelease(val version: String, val date: String?, val notes: String, val url: String, val size: Long) {
        fun toRelease() = NewerRelease(version, date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, notes, url, size)

        companion object {
            fun of(release: NewerRelease) =
                SavedRelease(release.version, release.date?.toString(), release.notes, release.downloadUrl, release.sizeBytes)
        }
    }

    companion object {
        /** SharedPreferences 파일 이름 */
        const val PREFS_NAME = "update_check"

        /** 자동 확인 사이 최소 간격. 10분 */
        const val INTERVAL_MS = 10 * 60 * 1000L

        private const val KEY_CHECKED_AT = "checked_at"
        private const val KEY_RELEASES = "releases"
        private const val KEY_SKIPPED = "skipped_version"
        private val LEGACY_KEYS = listOf("version", "notes", "url", "size")

        private val json = Json { ignoreUnknownKeys = true }
    }
}
