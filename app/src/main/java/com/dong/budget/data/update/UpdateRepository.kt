package com.dong.budget.data.update

import com.dong.budget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

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

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
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
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        runCatching { fetchLatest() }
            .fold(
                onSuccess = { it },
                onFailure = { UpdateStatus.Failed(it.message ?: "알 수 없는 오류") },
            )
    }

    private fun fetchLatest(): UpdateStatus {
        val url = URL("$apiBase/repos/$owner/$repo/releases/latest")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub API 는 User-Agent 가 없으면 403 을 준다.
            setRequestProperty("User-Agent", "dong-budget")
        }

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
                notes = release.body.orEmpty().trim(),
                downloadUrl = apk.downloadUrl,
                sizeBytes = apk.size,
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val HTTP_NOT_FOUND = 404
        val HTTP_OK_RANGE = 200..299
    }
}
