package com.dong.budget.data.update

import com.dong.budget.BuildConfig
import com.dong.budget.data.db.BudgetTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant
import java.time.LocalDate

/**
 * 아직 설치하지 않은 새 버전 하나. 업데이트 화면, 홈 배너, 패치노트 맨 위가 모두 이것을 본다.
 * @property date 배포한 날. 알 수 없으면 null
 * @property notes 릴리스 본문의 앱용 구간(태그 메시지). 줄 수를 자르지 않는다. 짧게 보여줄 곳에서 자른다.
 * @property downloadUrl 설치 파일 주소
 * @property sizeBytes 설치 파일 크기
 */
data class NewerRelease(val version: String, val date: LocalDate?, val notes: String, val downloadUrl: String, val sizeBytes: Long)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GithubAsset> = emptyList(),
) {
    /** 설치 파일. 없으면(올리는 중이거나 빠뜨림) 아직 설치할 수 없는 배포다. */
    fun apk(): GithubAsset? = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

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
    /**
     * 지금 버전보다 새로 배포된 버전들을 최신순으로 가져온다. 비어 있으면 지금이 최신이다.
     * 가장 새 버전만이 아니라 최근 배포 목록을 받는다. 여러 버전을 건너뛰었을 때 패치노트가 그사이 버전의 바뀐 점까지 보여줄 수 있게.
     *
     * 실패하면 사용자에게 보여줄 사유는 [describeNetworkError] 로 만든다.
     */
    suspend fun newerReleases(): Result<List<NewerRelease>> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = open("$apiBase/repos/$owner/$repo/releases?per_page=$RELEASE_PAGE_SIZE")
            try {
                check(connection.responseCode in HTTP_OK_RANGE) { describeHttpError(connection.responseCode) }
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

    companion object {
        private const val TIMEOUT_MS = 10_000
        private val HTTP_OK_RANGE = 200..299

        /** 한 번에 받는 배포 수. 이보다 많이 건너뛴 경우는 오래된 쪽이 빠진다. */
        private const val RELEASE_PAGE_SIZE = 20

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * 배포 목록(JSON)에서 [currentVersion] 보다 새 정식 배포만 골라 최신순으로 돌려준다.
         * 초안·시험판과 설치 파일이 없는 배포는 뺀다.
         */
        internal fun parseNewerReleases(body: String, currentVersion: String): List<NewerRelease> {
            val current = AppVersion.parse(currentVersion) ?: return emptyList()
            return json
                .decodeFromString<List<GithubRelease>>(body)
                .asSequence()
                .filter { !it.draft && !it.prerelease }
                .mapNotNull { release ->
                    val apk = release.apk() ?: return@mapNotNull null
                    val version = AppVersion.parse(release.tagName)?.takeIf { it > current } ?: return@mapNotNull null
                    Triple(version, release, apk)
                }.sortedByDescending { it.first }
                // 'v0.1.8' 과 'v0.1.8-hotfix' 처럼 같은 버전으로 읽히는 배포가 둘이면 하나만 남긴다.
                // 둘 다 남기면 패치노트 목록의 항목 이름(버전)이 겹쳐 화면이 죽는다.
                .distinctBy { it.first }
                .map { (version, release, apk) ->
                    NewerRelease(
                        version = version.toString(),
                        date = release.publishedAt?.let {
                            runCatching { BudgetTime.toLocalDate(Instant.parse(it)) }.getOrNull()
                        },
                        notes = ReleaseNotes.forApp(release.body),
                        downloadUrl = apk.downloadUrl,
                        sizeBytes = apk.size,
                    )
                }.toList()
        }

        /** 서버가 요청을 거절했을 때 사용자에게 보여줄 말 */
        internal fun describeHttpError(code: Int): String = when (code) {
            // 로그인 없이 부를 수 있는 횟수(시간당 60번)를 넘겼을 때 GitHub 가 주는 응답
            403, 429 -> "잠시 뒤에 다시 확인해 주세요 (확인 요청이 너무 많았어요)"

            in 500..599 -> "배포 서버에 문제가 있어요. 잠시 뒤에 다시 확인해 주세요"

            else -> "새 버전을 확인하지 못했어요 (서버 응답 $code)"
        }

        /**
         * 네트워크 예외를 사용자에게 보여줄 말로 바꾼다. 예외의 영문 원문(예: 'Unable to resolve host')을 그대로 보여주지 않는다.
         * 원문은 화면의 '시스템 메시지' 로 따로 보여줄 수 있다.
         */
        fun describeNetworkError(error: Throwable): String = when (error) {
            is UnknownHostException, is ConnectException, is NoRouteToHostException ->
                "인터넷에 연결되어 있지 않아요. 연결을 확인하고 다시 해 주세요"

            is SocketTimeoutException -> "서버 응답이 늦어요. 잠시 뒤에 다시 해 주세요"

            is IOException -> "연결이 끊겼어요. 잠시 뒤에 다시 해 주세요"

            // check() 로 만든 한국어 사유(서버 응답 등)는 그대로 쓴다
            is IllegalStateException -> error.message ?: "새 버전을 확인하지 못했어요"

            else -> "새 버전을 확인하지 못했어요"
        }
    }
}
