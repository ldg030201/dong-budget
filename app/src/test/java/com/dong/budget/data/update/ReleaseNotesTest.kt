package com.dong.budget.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    private val marker = ReleaseNotes.APP_NOTES_END

    @Test
    fun `표시 위쪽의 바뀐 점만 보여주고 설치 안내와 변경 기록 링크는 숨긴다`() {
        val body =
            """
            |업데이트 화면을 다듬었어요
            |
            |- 설치 버튼이 항상 바로 보여요
            |- 바뀐 점만 짧게 보여줘요
            |
            |$marker
            |
            |### 처음 설치하는 경우
            |1. **(갤럭시)** 설정 → **자동 차단 끄기**
            |
            |**Full Changelog**: https://github.com/ldg030201/dong-budget/compare/v0.1.1...v0.1.2
            """.trimMargin()

        assertEquals(
            "업데이트 화면을 다듬었어요\n\n· 설치 버튼이 항상 바로 보여요\n· 바뀐 점만 짧게 보여줘요",
            ReleaseNotes.forApp(body),
        )
    }

    @Test
    fun `표시가 없는 옛 형식 본문은 아무것도 보여주지 않는다`() {
        val oldBody = "아래 apk 를 받아서 설치하세요.\n\n### 처음 설치하는 경우\n1. 자동 차단 끄기"
        assertEquals("", ReleaseNotes.forApp(oldBody))
    }

    @Test
    fun `본문이 없으면 빈 문자열`() {
        assertEquals("", ReleaseNotes.forApp(null))
        assertEquals("", ReleaseNotes.forApp("   "))
        assertEquals("", ReleaseNotes.forApp("\n\n$marker\n설치 안내"))
    }

    @Test
    fun `마크다운 기호를 걷어낸다`() {
        val body = "## 제목\n**굵게** 와 `코드` 와 [링크](https://example.com)\n* 목록\n$marker"
        assertEquals("제목\n굵게 와 코드 와 링크\n· 목록", ReleaseNotes.forApp(body))
    }

    @Test
    fun `윈도우 줄바꿈과 겹친 빈 줄을 정리한다`() {
        val body = "첫 줄\r\n\r\n\r\n\r\n둘째 줄\r\n\r\n$marker"
        assertEquals("첫 줄\n\n둘째 줄", ReleaseNotes.forApp(body))
    }

    @Test
    fun `본문 안의 다른 HTML 주석도 지운다`() {
        val body = "<!-- 작성자 메모 -->\n바뀐 점\n$marker"
        assertEquals("바뀐 점", ReleaseNotes.forApp(body))
    }

    @Test
    fun `패치노트를 옮긴 태그 메시지는 메뉴 이름과 항목을 그대로 보여준다`() {
        val body = "[거래 등록]\n- 추가: 하나\n- 오류수정: 둘\n\n[설정]\n- 개선: 셋\n$marker\n설치 안내"
        assertEquals("[거래 등록]\n· 추가: 하나\n· 오류수정: 둘\n\n[설정]\n· 개선: 셋", ReleaseNotes.forApp(body))
    }
}
