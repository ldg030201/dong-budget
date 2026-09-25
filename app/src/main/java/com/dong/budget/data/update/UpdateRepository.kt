package com.dong.budget.data.update

import com.dong.budget.BuildConfig
import com.dong.budget.data.db.BudgetTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate

/** 새 버전 확인 결과 */
sealed interface UpdateStatus {
    /** 지금이 최신이다 */
    data object UpToDate : UpdateStatus

    data class Available(val version: String, val notes: String, val downloadUrl: String, val sizeBytes: Long) : UpdateStatus

    /**
     * 확인에 실패했다.
     *
     * 네트워크가 없거나 아직 배포한 적이 없는 경우가 대부분이라
     * 사용자에게 오류처럼 크게 보여주지 않는다.
     */
    data class Failed(val reason: String) : UpdateStatus
}

/**
 * 아직 설치하지 않은 새 버전 하나. 패치노트 맨 위에 보여준다.
 * @property date 배포한 날. 알 수 없으면 null
 * @property notes 릴리스 본문의 앱용 구간(태그 메시지). 줄 수를 자르지 않는다.
 */
data class NewerRelease(val version: String, val date: LocalDate?, val notes: String)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(val name: String, @SerialName("browser_download_url") val downloadUrl: String, val size: Long = 0)

/**
 * GitHub Releases 에 올라온 최신 APK 를 확인한다.
 *
 * 저장소가 공개여야 한다. 비공개면 첨부파일을 받는 데 인증이 필요한데,
 * 그러려면 토큰을 앱에 넣어야 하고 APK 는 뜯어볼 수 있으므로 토큰이 그대로 새어나간다.
 */
class UpdateRepository(
    private val owner: String = BuildConfig.GITHUB_OWNER,
    private val repo: String = BuildConfig.GITHUB_REPO,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val apiBase: String = BuildConfig.UPDATE_API_BASE,
) {
    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        runCatching { fetchLatest() }
            .fold(
                onSuccess = { it },
                onFailure = { UpdateStatus.Failed(it.message ?: "알 수 없는 오류") },
            )
    }

    /**
     * 지금 버전보다 새로 배포된 버전들을 최신순으로 가져온다. 패치노트에서 아직 설치하지 않은 버전의 바뀐 점을 보여줄 때 쓴다.
     * 여러 버전을 건너뛰었을 때도 그사이 버전의 바뀐 점까지 보이게 최근 배포 목록을 받는다.
     */
    suspend fun newerReleases(): Result<List<NewerRelease>> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = open("$apiBase/repos/$owner/$repo/releases?per_page=$RELEASE_PAGE_SIZE")
            try {
                check(connection.responseCode in HTTP_OK_RANGE) { "서버 응답 ${connection.responseCode}" }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseNewerReleases(body, currentVersion)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("Accept", "application/vnd.github+json")
        // GitHub API 는 User-Agent 가 없으면 403 을 준다.
        setRequestProperty("User-Agent", "dong-budget")
    }

    private fun fetchLatest(): UpdateStatus {
        val connection = open("$apiBase/repos/$owner/$repo/releases/latest")

        try {
            if (connection.responseCode == HTTP_NOT_FOUND) {
                return UpdateStatus.Failed("아직 배포된 버전이 없어요")
            }
            if (connection.responseCode !in HTTP_OK_RANGE) {
                return UpdateStatus.Failed("서버 응답 ${connection.responseCode}")
            }

            val release =
                connection.inputStream.bufferedReader().use { reader ->
                    json.decodeFromString<GithubRelease>(reader.readText())
                }

            if (release.draft || release.prerelease) return UpdateStatus.UpToDate

            val latest = AppVersion.parse(release.tagName)
            val current = AppVersion.parse(currentVersion)
            if (latest == null || current == null) {
                return UpdateStatus.Failed("버전 형식을 알 수 없어요")
            }
            if (latest <= current) return UpdateStatus.UpToDate

            val apk =
                release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return UpdateStatus.Failed("설치 파일이 올라와 있지 않아요")

            return UpdateStatus.Available(
                version = latest.toString(),
                // 본문 전체가 아니라 앱용 구간만. 웹 페이지용 설치 안내는 여기서 걸러진다.
                notes = ReleaseNotes.forApp(release.body),
                downloadUrl = apk.downloadUrl,
                sizeBytes = apk.size,
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
        private const val HTTP_NOT_FOUND = 404
        private val HTTP_OK_RANGE = 200..299

        /** 한 번에 받는 배포 수. 이보다 많이 건너뛴 경우는 오래된 쪽이 빠진다. */
        private const val RELEASE_PAGE_SIZE = 20

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 배포 목록(JSON)에서 [currentVersion] 보다 새 정식 배포만 골라 최신순으로 돌려준다.
         * 초안·시험판과 설치 파일이 없는 배포는 뺀다. 업데이트 확인과 같은 기준이다.
         */
        internal fun parseNewerReleases(body: String, currentVersion: String): List<NewerRelease> {
            val current = AppVersion.parse(currentVersion) ?: return emptyList()
            return json
                .decodeFromString<List<GithubRelease>>(body)
                .asSequence()
                .filter { !it.draft && !it.prerelease }
                .filter { release -> release.assets.any { it.name.endsWith(".apk", ignoreCase = true) } }
                .mapNotNull { release ->
                    val version = AppVersion.parse(release.tagName)?.takeIf { it > current } ?: return@mapNotNull null
                    version to release
                }.sortedByDescending { it.first }
                .map { (version, release) ->
                    NewerRelease(
                        version = version.toString(),
                        date = release.publishedAt?.let {
                            runCatching { Instant.parse(it).atZone(BudgetTime.ZONE).toLocalDate() }.getOrNull()
                        },
                        notes = ReleaseNotes.forApp(release.body, maxLines = Int.MAX_VALUE),
                    )
                }.toList()
        }
    }
}
