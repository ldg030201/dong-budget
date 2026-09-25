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
    fun `업데이트 화면용 바뀐 점은 여전히 짧게 자른다`() {
        val long = (1..10).joinToString("\n") { "- 줄 $it" } + "\n\n$marker"
        assertEquals(7, ReleaseNotes.forApp(long).lines().size)
        assertEquals(10, ReleaseNotes.forApp(long, maxLines = Int.MAX_VALUE).lines().size)
    }
}
