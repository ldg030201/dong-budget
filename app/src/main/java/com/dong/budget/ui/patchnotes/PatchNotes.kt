package com.dong.budget.ui.patchnotes

import java.time.LocalDate

// ─────────────────────────────────────────────────────────────────────
// 버전별 바뀐 점. '전체 > 패치노트' 에 그대로 나온다.
//
// 새 버전을 낼 때는 맨 위에 한 덩어리를 추가한다. 개발 중인 버전은 date 를 null 로 두고,
// 버전을 올릴 때 날짜를 적는다. 지금 버전의 기록이 빠지면 단위 테스트가 실패한다.
//
// 글은 개발 기록이 아니라 쓰는 사람 눈높이로 적는다. 화면에 보이는 변화만 적는다.
// ─────────────────────────────────────────────────────────────────────

/** 바뀐 점의 종류. 화면에서 색 꼬리표로 보인다. */
enum class ChangeKind(val label: String) {
    /** 없던 기능이 생김 */
    ADDED("추가"),

    /** 있던 기능이 더 좋아짐 */
    IMPROVED("개선"),

    /** 동작이나 규칙을 바꿈. 좋고 나쁨보다 '달라졌다' 는 것을 알려야 할 때 */
    CHANGED("수정"),

    /** 잘못 동작하던 것을 고침 */
    FIXED("오류수정"),
}

data class Change(val kind: ChangeKind, val text: String)

/** 한 메뉴에서 바뀐 것들 */
data class MenuChanges(val menu: String, val changes: List<Change>)

/** @property date 낸 날. 아직 내지 않은 버전은 null */
data class Release(val version: String, val date: LocalDate?, val menus: List<MenuChanges>)

private fun added(text: String) = Change(ChangeKind.ADDED, text)

private fun improved(text: String) = Change(ChangeKind.IMPROVED, text)

private fun changed(text: String) = Change(ChangeKind.CHANGED, text)

private fun fixed(text: String) = Change(ChangeKind.FIXED, text)

private fun menu(name: String, vararg changes: Change) = MenuChanges(name, changes.toList())

/** 최신 버전이 맨 위. 메뉴는 앱 화면 순서(홈 → 거래 등록 → 분류 관리 → 전체 → 설정 → 공통)로 적는다. */
val PATCH_NOTES: List<Release> =
    listOf(
        Release(
            version = "0.1.3",
            date = LocalDate.of(2026, 9, 25),
            menus =
            listOf(
                menu(
                    "홈",
                    added("이번 달 지출·수입과 함께, 지난달 같은 날까지보다 얼마나 더(덜) 썼는지 보여줘요"),
                    added("달력에 날마다 들어온 돈과 쓴 돈이 +/- 부호와 색으로 적혀요"),
                    added("달력에서 날짜를 누르면 그날 내역으로 바로 내려가요"),
                    added("내역을 내려 보면 한 주 달력이 위에 붙어 있어서 다른 날로 바로 옮겨 갈 수 있어요"),
                    improved("내역을 날짜별로 나눠 최신순으로 보여줘요"),
                    changed("내역 탭을 홈으로 합쳤어요. 아래 탭은 홈과 전체 두 개예요"),
                    changed("거래 등록 버튼을 오른쪽 아래 + 버튼으로 옮겼어요"),
                    changed("지출 금액 앞에 빼기(-) 부호를 붙였어요. 위쪽 합계는 쓴 돈을 빨강, 들어온 돈을 초록으로 보여줘요"),
                ),
                menu(
                    "거래 등록",
                    improved("금액·분류·결제수단·내용·메모를 입력칸으로 정리했어요. 누른 칸의 입력판만 열려요"),
                    improved("분류와 결제수단을 아이콘 표에서 골라요"),
                    added("등록하다가 분류와 결제수단을 바로 새로 만들 수 있어요"),
                    added("날짜와 시간을 바꿀 수 있어요"),
                    changed("메모를 뺀 모든 칸을 채워야 저장돼요. 빈 칸이 있으면 그 칸으로 옮겨 알려줘요"),
                ),
                menu(
                    "분류 관리",
                    added("새 메뉴예요. 분류와 결제수단을 추가하고 지울 수 있어요"),
                    added("분류와 결제수단마다 아이콘과 색이 생겼어요"),
                    added("분류를 지우면 그 분류의 거래는 '기타' 로 옮겨져요"),
                    changed("기본 분류를 식비, 교통/차량, 편의점, 패션/미용, 고정지출, 기타와 급여, 용돈, 기타로 바꿨어요. 쓰던 분류와 거래는 그대로 남아요"),
                ),
                menu(
                    "전체",
                    added("패치노트에서 버전별로 바뀐 점을 볼 수 있어요"),
                    improved("메뉴마다 아이콘을 달았어요"),
                ),
                menu(
                    "공통",
                    changed("확인 창 버튼을 왼쪽 확인, 오른쪽 취소로 바꿨어요"),
                    improved("기기 언어가 한국어가 아니어도 글이 어절 단위로 자연스럽게 줄바꿈돼요"),
                ),
            ),
        ),
        Release(
            version = "0.1.2",
            date = LocalDate.of(2026, 9, 23),
            menus =
            listOf(
                menu(
                    "설정",
                    fixed("업데이트의 바뀐 점이 길면 설치 버튼이 화면 밖으로 밀려나던 문제를 고쳤어요"),
                    improved("업데이트의 바뀐 점을 기호 없이 짧게 보여줘요"),
                    added("설치가 끝나면 앱이 닫힌다고 미리 알려줘요"),
                ),
            ),
        ),
        Release(
            version = "0.1.1",
            date = LocalDate.of(2026, 9, 23),
            menus =
            listOf(
                menu(
                    "공통",
                    improved("앱을 만들어 내보내는 과정을 안정화했어요. 화면에서 달라진 점은 없어요"),
                ),
            ),
        ),
        Release(
            version = "0.1.0",
            date = LocalDate.of(2026, 9, 23),
            menus =
            listOf(
                menu(
                    "홈",
                    added("이번 달 쓴 돈과 들어온 돈을 보여주고, 달을 넘겨 볼 수 있어요"),
                ),
                menu(
                    "내역",
                    added("달마다 거래 목록을 볼 수 있어요"),
                ),
                menu(
                    "거래 등록",
                    added("지출과 수입을 등록하고, 고치거나 지울 수 있어요"),
                ),
                menu(
                    "설정",
                    added("화면 테마를 기기 설정, 밝게, 어둡게 중에서 고를 수 있어요"),
                    added("앱 안에서 새 버전을 확인하고 바로 설치할 수 있어요"),
                ),
            ),
        ),
    )
