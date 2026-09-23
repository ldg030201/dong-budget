package com.dong.budget.data.update

/**
 * GitHub 릴리스 본문에서 앱에 보여줄 부분만 꺼낸다.
 *
 * 릴리스 본문은 웹 페이지용이라 처음 설치하는 사람을 위한 안내와 전체 변경 기록 링크가 붙어 있다.
 * 이미 앱을 쓰는 사람에게는 필요 없는 내용이고, 길어서 설치 버튼을 화면 밖으로 밀어낸다.
 *
 * 배포 워크플로(.github/workflows/release.yml)가 본문 맨 위에 태그 메시지를 넣고
 * 그 아래에 [APP_NOTES_END] 표시를 둔다. 앱은 그 표시 위쪽만 보여준다.
 * 표시는 HTML 주석이라 웹 페이지에는 보이지 않는다.
 * 이 문자열을 바꾸면 워크플로 쪽도 반드시 같이 바꿔야 한다.
 */
object ReleaseNotes {
    const val APP_NOTES_END = "<!-- dong-budget:app-notes-end -->"

    /** 설치 버튼을 밀어내지 않도록 줄 수를 제한한다. */
    private const val MAX_LINES = 6

    fun forApp(body: String?): String {
        if (body.isNullOrBlank()) return ""
        // 표시가 없으면 이 형식 이전의 릴리스다. 어디까지가 앱용인지 알 수 없으므로
        // 설치 안내 같은 엉뚱한 글을 보여주느니 아무것도 보여주지 않는다.
        if (!body.contains(APP_NOTES_END)) return ""

        val lines =
            body
                .substringBefore(APP_NOTES_END)
                .replace("\r\n", "\n")
                .replace(HTML_COMMENT, "")
                .lines()
                .map { stripMarkdown(it).trimEnd() }

        // 빈 줄은 문단 구분용으로 하나만 남긴다.
        val compact = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                if (compact.lastOrNull()?.isNotEmpty() == true) compact += ""
            } else {
                compact += line
            }
        }
        while (compact.lastOrNull()?.isEmpty() == true) compact.removeAt(compact.lastIndex)

        return if (compact.size <= MAX_LINES) {
            compact.joinToString("\n")
        } else {
            (compact.take(MAX_LINES) + "…").joinToString("\n")
        }
    }

    private val HTML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val HEADING = Regex("^\\s{0,3}#{1,6}\\s*")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+")
    private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val EMPHASIS = Regex("\\*\\*|__|`")

    /** 마크다운 기호를 걷어내고 글자만 남긴다. 앱은 마크다운을 그리지 않는다. */
    private fun stripMarkdown(line: String): String {
        var s = HEADING.replace(line, "")
        s = BULLET.replace(s) { m -> "${m.groupValues[1]}· " }
        s = LINK.replace(s) { it.groupValues[1] }
        s = EMPHASIS.replace(s, "")
        return s
    }
}
