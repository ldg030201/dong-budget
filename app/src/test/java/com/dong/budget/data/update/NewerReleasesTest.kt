package com.dong.budget.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NewerReleasesTest {
    private val marker = ReleaseNotes.APP_NOTES_END

    private fun release(
        tag: String,
        body: String,
        publishedAt: String? = "2026-09-25T05:40:00Z",
        draft: Boolean = false,
        prerelease: Boolean = false,
        apk: Boolean = true,
    ) = """
        {"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,
         "published_at":${publishedAt?.let { "\"$it\"" } ?: "null"},
         "body":${body.toJsonString()},
         "assets":[${if (apk) {
        """{"name":"dong-budget-${tag.removePrefix(
            "v",
        )}.apk","browser_download_url":"https://example.com/a.apk","size":1}"""
    } else {
        ""
    }}]}
    """.trimIndent()

    private fun String.toJsonString() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test
    fun `지금 버전보다 새 정식 배포만 최신순으로 고른다`() {
        val body =
            "[" +
                listOf(
                    release("v0.1.8", "0.1.8 요약\n\n- 여덟\n\n$marker\n설치 안내"),
                    release("v0.1.6", "0.1.6 요약\n\n$marker"),
                    release("v0.1.7", "0.1.7 요약\n\n$marker"),
                    release("v0.1.5", "지금 버전\n\n$marker"),
                    release("v0.1.9", "시험판\n\n$marker", prerelease = true),
                    release("v0.2.0", "초안\n\n$marker", draft = true),
                    release("v0.1.10", "설치 파일 없음\n\n$marker", apk = false),
                ).joinToString(",") + "]"

        val newer = UpdateRepository.parseNewerReleases(body, currentVersion = "0.1.5")

        assertEquals(listOf("0.1.8", "0.1.7", "0.1.6"), newer.map { it.version })
        // 앱용 구간만, 줄 수를 자르지 않고
        assertEquals("0.1.8 요약\n\n· 여덟", newer.first().notes)
        // 배포 시각은 한국 날짜로 바꾼다(2026-09-25 05:40 UTC = 14:40 KST)
        assertEquals(LocalDate.of(2026, 9, 25), newer.first().date)
    }

    @Test
    fun `배포 시각이 없거나 이상하면 날짜 없이 보여준다`() {
        val body =
            "[" + release("v0.1.6", "요약\n\n$marker", publishedAt = null) + "," + release("v0.1.7", "요약\n\n$marker", publishedAt = "어제") +
                "]"
        val newer = UpdateRepository.parseNewerReleases(body, currentVersion = "0.1.5")
        assertTrue(newer.all { it.date == null })
    }

    @Test
    fun `새 버전이 없으면 빈 목록이다`() {
        val body = "[" + release("v0.1.5", "요약\n\n$marker") + "]"
        assertTrue(UpdateRepository.parseNewerReleases(body, currentVersion = "0.1.5").isEmpty())
    }

    @Test
    fun `같은 버전으로 읽히는 배포가 둘이면 하나만 남긴다`() {
        // 둘 다 남기면 패치노트 목록의 항목 이름이 겹쳐 화면이 죽는다
        val body = "[" + release("v0.1.8", "정식\n\n$marker") + "," + release("v0.1.8-hotfix", "고침\n\n$marker") + "]"
        val newer = UpdateRepository.parseNewerReleases(body, currentVersion = "0.1.7")
        assertEquals(listOf("0.1.8"), newer.map { it.version })
    }

    @Test
    fun `설치 파일 주소와 크기도 함께 담는다`() {
        val newer = UpdateRepository.parseNewerReleases("[" + release("v0.1.8", "요약\n\n$marker") + "]", currentVersion = "0.1.7")
        assertEquals("https://example.com/a.apk", newer.single().downloadUrl)
        assertEquals(1L, newer.single().sizeBytes)
    }

    @Test
    fun `네트워크 오류는 영어 원문 대신 한국어로 알려준다`() {
        assertEquals(
            "인터넷에 연결되어 있지 않아요. 연결을 확인하고 다시 해 주세요",
            UpdateRepository.describeNetworkError(java.net.UnknownHostException("Unable to resolve host \"api.github.com\"")),
        )
        assertEquals("서버 응답이 늦어요. 잠시 뒤에 다시 해 주세요", UpdateRepository.describeNetworkError(java.net.SocketTimeoutException("timeout")))
        // 앱이 만든 한국어 사유는 그대로 둔다
        assertEquals("설치 파일을 끝까지 받지 못했어요", UpdateRepository.describeNetworkError(IllegalStateException("설치 파일을 끝까지 받지 못했어요")))
        assertTrue(UpdateRepository.describeHttpError(403).contains("잠시 뒤"))
    }
}
