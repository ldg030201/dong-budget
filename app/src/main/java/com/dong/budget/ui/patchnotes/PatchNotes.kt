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

/**
 * 패치노트를 나누는 메뉴. 앱 화면의 순서대로 둔다.
 * 아이콘과 색은 화면 쪽(PatchNotesScreen)에서 정한다.
 */
enum class PatchMenu(val label: String) {
    HOME("홈"),

    /** 0.1.3 에서 홈으로 합쳐진 옛 탭. 지난 기록을 위해 남긴다. */
    HISTORY("내역"),
    EDITOR("거래 등록"),
    CATEGORIES("분류 관리"),
    MORE("전체"),
    PATCH_NOTES("패치노트"),
    SETTINGS("설정"),

    /** 특정 메뉴가 아니라 앱 전체에 걸친 변화 */
    COMMON("공통"),
}

/** 한 메뉴에서 바뀐 것들 */
data class MenuChanges(val menu: PatchMenu, val changes: List<Change>)

/** @property date 낸 날. 아직 내지 않은 버전은 null */
data class Release(val version: String, val date: LocalDate?, val menus: List<MenuChanges>)

private fun added(text: String) = Change(ChangeKind.ADDED, text)

private fun improved(text: String) = Change(ChangeKind.IMPROVED, text)

private fun changed(text: String) = Change(ChangeKind.CHANGED, text)

private fun fixed(text: String) = Change(ChangeKind.FIXED, text)

private fun menu(menu: PatchMenu, vararg changes: Change) = MenuChanges(menu, changes.toList())

/**
 * 최신 버전이 맨 위. 메뉴는 앱 화면 순서(홈 → 거래 등록 → 분류 관리 → 전체 → 패치노트 → 설정 → 공통)로 적는다.
 * 메뉴 안의 항목은 화면에서 종류 순서(추가 → 개선 → 수정 → 오류수정)로 다시 정렬되므로 적는 순서는 자유다.
 */
val PATCH_NOTES: List<Release> =
    listOf(
        Release(
            version = "1.1.0",
            date = null,
            menus =
            listOf(
                menu(
                    PatchMenu.PATCH_NOTES,
                    improved("버전을 눌러 바뀐 점을 접고 펼칠 수 있어요. 처음에는 지금 버전과 새 버전만 펼쳐져 있어요"),
                ),
            ),
        ),
        Release(
            version = "1.0.0",
            date = LocalDate.of(2026, 9, 26),
            menus =
            listOf(
                menu(
                    PatchMenu.EDITOR,
                    improved("삭제 버튼을 누르기 쉽게 키웠어요"),
                    improved("이미 등록한 결제라고 알려줄 때 묻던 알림도 함께 치워요"),
                    fixed("등록하기를 빠르게 여러 번 누르면 같은 거래가 두 번 저장되던 문제를 고쳤어요"),
                    fixed("가게 이름 앞뒤에 빈칸이 들어가면 지난번에 고른 분류를 찾지 못하던 문제를 고쳤어요"),
                    fixed("분류나 결제수단을 추가한 뒤 화면을 돌리거나 테마가 바뀌면 열어 둔 선택판이 저절로 닫히던 문제를 고쳤어요"),
                    fixed("결제 알림에서 처음 보는 카드의 미리보기 색이 실제로 저장되는 색과 다르던 문제를 고쳤어요"),
                    fixed("결제 등록 알림을 꺼 둔 동안 온 결제를, 알림을 다시 켜도 묻지 않던 문제를 고쳤어요"),
                    fixed("새 결제 알림이 가끔 소리 없이 뜨던 문제를 고쳤어요"),
                ),
                menu(
                    PatchMenu.CATEGORIES,
                    added("분류와 결제수단을 길게 눌러 끌면 순서를 바꿀 수 있어요. 바꾼 순서는 거래 등록 화면에도 그대로 보여요"),
                ),
                menu(
                    PatchMenu.PATCH_NOTES,
                    fixed("같은 버전의 배포가 두 개 올라와 있으면 패치노트 화면이 꺼지던 문제를 고쳤어요"),
                ),
                menu(
                    PatchMenu.SETTINGS,
                    improved("업데이트 부분을 간단하게 정리했어요. 큰 버튼은 설치 버튼 하나만 두고, 다른 설치 방법은 글자 버튼으로 줄였어요"),
                    improved("화면 테마 버튼에 아이콘을 달았어요"),
                    improved("앱을 열 때 받아 둔 새 버전 정보를 패치노트도 같이 써서, 패치노트를 열 때 다시 확인하지 않아요"),
                    fixed("새 버전이 보이는 동안 더 새 버전이 나와도 다시 확인할 수 없던 문제를 고쳤어요. 버전 옆 '업데이트 확인'을 언제든 누를 수 있어요"),
                    fixed("이미 지난 업데이트 정보나 지난번 설치 실패가 계속 보이던 문제를 고쳤어요"),
                    fixed("업데이트를 받는 동안 다른 앱으로 가면 설치 확인창이 뜨지 않던 문제를 고쳤어요. 앱으로 돌아오면 이어서 떠요"),
                    fixed("인터넷이 없을 때 업데이트 오류가 영어로 나오던 문제를 고쳤어요"),
                ),
                menu(
                    PatchMenu.COMMON,
                    improved("화면 읽기(TalkBack)로도 분류 순서를 바꿀 수 있고, 저장되지 않은 이유와 지금 고른 항목을 읽어줘요"),
                    improved("계속 떠 있는 알림(음악, 길 안내, 내려받기 진행률 등)은 읽지 않아 배터리를 덜 써요"),
                    fixed("뒤로·닫기 버튼을 빠르게 두 번 누르면 앱이 꺼지던 문제를 고쳤어요"),
                ),
            ),
        ),
        Release(
            version = "0.1.7",
            date = LocalDate.of(2026, 9, 25),
            menus =
            listOf(
                menu(
                    PatchMenu.EDITOR,
                    improved("이미 등록한 결제의 알림을 누르면 알려주고 알림을 치워요"),
                    improved("알림을 허용하고 돌아오면, 그전에 와 있던 토스 결제 알림도 등록할지 물어봐요"),
                    fixed("여러 개가 묶인 결제 알림을 한꺼번에 지우거나 워치에서 지운 결제를, 기기를 다시 켜면 또 물어보던 문제를 고쳤어요"),
                    fixed("알림으로 등록한 거래를 지운 뒤 기기를 다시 켜면 그 결제를 또 물어보던 문제를 고쳤어요"),
                    fixed("이름이 긴 카드는 이미 결제수단에 있어도 '새로 추가돼요'로 보이던 문제를 고쳤어요"),
                    fixed("결제 알림을 번갈아 누르면 같은 등록창이 두 번 열리던 문제를 고쳤어요"),
                ),
                menu(
                    PatchMenu.PATCH_NOTES,
                    added("아직 설치하지 않은 새 버전의 바뀐 점을 맨 위에 보여줘요. 바로 업데이트하러 갈 수도 있어요"),
                ),
                menu(
                    PatchMenu.SETTINGS,
                    added("갤럭시에서 '보안 위험 자동 차단' 때문에 업데이트가 막히면 알려주고, 버튼을 누르면 그 설정 화면으로 바로 가요"),
                    changed("새 버전 확인을 6시간에 한 번에서 앱을 열 때마다로 바꿨어요. 새 버전이 나오면 바로 알 수 있어요"),
                    fixed("업데이트 파일을 받는 도중 앱에 돌아오면 받던 파일이 지워져 설치가 실패하던 문제를 고쳤어요"),
                ),
                menu(
                    PatchMenu.COMMON,
                    changed("알림 권한을 알림 읽기보다 먼저 물어봐요"),
                    fixed("알림 권한 창을 그냥 닫기만 해도 다음부터 설정 화면으로 보내던 문제를 고쳤어요"),
                    fixed("작은 화면에서 안내창의 버튼이 잘리던 문제를 고쳤어요"),
                    fixed("다른 앱이 잘못된 값으로 동계부를 열면 앱이 꺼질 수 있던 문제를 고쳤어요"),
                ),
            ),
        ),
        Release(
            version = "0.1.6",
            date = LocalDate.of(2026, 9, 25),
            menus =
            listOf(
                menu(
                    PatchMenu.EDITOR,
                    added("토스 결제 알림이 오면 '가계부에 등록할까요?' 알림을 보내요. 누르면 금액·카드·가게·결제 시각이 채워진 등록창이 열려요"),
                    added("알림에서 가져온 카드가 결제수단에 없으면 저장할 때 새로 만들어요"),
                    added("전에 같은 가게로 등록한 적이 있으면 그때 고른 분류를 미리 골라 둬요"),
                    added("할부 결제는 '3개월 할부'처럼 메모에 적어 둬요"),
                    added("같은 결제를 두 번 등록하려고 하면 알려줘요"),
                ),
                menu(
                    PatchMenu.COMMON,
                    added("앱을 켤 때 '알림 읽기'와 알림 권한이 꺼져 있으면 알려줘요. '제한된 설정'으로 막힌 경우 푸는 방법도 안내해요"),
                ),
            ),
        ),
        Release(
            version = "0.1.5",
            date = LocalDate.of(2026, 9, 25),
            menus =
            listOf(
                menu(
                    PatchMenu.HOME,
                    added("새 버전이 나오면 홈 위쪽에 알려줘요. 누르면 바로 업데이트 화면으로 가요"),
                ),
                menu(
                    PatchMenu.MORE,
                    changed("설정을 목록 맨 아래에서 오른쪽 위 톱니바퀴 버튼으로 옮겼어요"),
                ),
                menu(
                    PatchMenu.PATCH_NOTES,
                    improved("메뉴별 아이콘과 세로줄로 나누고, 추가·개선·수정·오류수정 순서로 정리했어요"),
                ),
                menu(
                    PatchMenu.SETTINGS,
                    added("앱을 열 때 새 버전이 있는지 알아서 확인해요 (6시간에 한 번)"),
                ),
                menu(
                    PatchMenu.COMMON,
                    added("앱 아이콘이 생겼어요"),
                    fixed("다크 테마에서 메뉴에 들어갈 때 흰빛이 잠깐 비치던 문제를 고쳤어요"),
                ),
            ),
        ),
        Release(
            version = "0.1.4",
            date = LocalDate.of(2026, 9, 25),
            menus =
            listOf(
                menu(
                    PatchMenu.SETTINGS,
                    added(
                        "'출처를 알 수 없는 앱 설치'를 허용해 두면 다음 업데이트부터는 '업데이트할까요?' 확인 없이 '설치하기' 한 번으로 설치돼요. Play 프로텍트 검사 창은 뜰 수 있어요 (0.1.4 다음 버전부터)",
                    ),
                    added("앱 안에서 업데이트가 안 되면 받아둔 파일로 직접 설치하거나, 브라우저에서 설치 파일을 받을 수 있어요"),
                    improved("받아둔 설치 파일은 앱을 다시 켜도 남아 있어서 처음부터 다시 받지 않아도 돼요"),
                    improved("업데이트가 실패하면 시스템이 알려준 이유도 함께 보여줘요"),
                    improved("설치 중에 Play 프로텍트 창이 뜨면 무엇을 눌러야 하는지 알려줘요"),
                    fixed("설치가 중간에 멈춘 경우에도 '설치를 취소했어요' 라고 잘못 알려주던 문구를 고쳤어요"),
                    fixed("Play 프로텍트 검사에 막힌 경우를 알아보지 못하던 문제를 고쳤어요. 이제 무엇을 눌러야 하는지 알려줘요"),
                ),
                menu(
                    PatchMenu.COMMON,
                    added("앱을 켤 때 꺼져 있는 권한이 있으면 알려주고, 누르면 바로 설정 화면으로 가요. 갤럭시의 '보안 위험 자동 차단'도 함께 안내해요"),
                ),
            ),
        ),
        Release(
            version = "0.1.3",
            date = LocalDate.of(2026, 9, 25),
            menus =
            listOf(
                menu(
                    PatchMenu.HOME,
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
                    PatchMenu.EDITOR,
                    improved("금액·분류·결제수단·내용·메모를 입력칸으로 정리했어요. 누른 칸의 입력판만 열려요"),
                    improved("분류와 결제수단을 아이콘 표에서 골라요"),
                    added("등록하다가 분류와 결제수단을 바로 새로 만들 수 있어요"),
                    added("날짜와 시간을 바꿀 수 있어요"),
                    changed("메모를 뺀 모든 칸을 채워야 저장돼요. 빈 칸이 있으면 그 칸으로 옮겨 알려줘요"),
                ),
                menu(
                    PatchMenu.CATEGORIES,
                    added("새 메뉴예요. 분류와 결제수단을 추가하고 지울 수 있어요"),
                    added("분류와 결제수단마다 아이콘과 색이 생겼어요"),
                    added("분류를 지우면 그 분류의 거래는 '기타' 로 옮겨져요"),
                    changed("기본 분류를 식비, 교통/차량, 편의점, 패션/미용, 고정지출, 기타와 급여, 용돈, 기타로 바꿨어요. 쓰던 분류와 거래는 그대로 남아요"),
                ),
                menu(
                    PatchMenu.MORE,
                    improved("메뉴마다 아이콘을 달았어요"),
                ),
                menu(
                    PatchMenu.PATCH_NOTES,
                    added("새 메뉴예요. 전체 메뉴에서 버전별로 바뀐 점을 볼 수 있어요"),
                ),
                menu(
                    PatchMenu.COMMON,
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
                    PatchMenu.SETTINGS,
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
                    PatchMenu.COMMON,
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
                    PatchMenu.HOME,
                    added("이번 달 쓴 돈과 들어온 돈을 보여주고, 달을 넘겨 볼 수 있어요"),
                ),
                menu(
                    PatchMenu.HISTORY,
                    added("달마다 거래 목록을 볼 수 있어요"),
                ),
                menu(
                    PatchMenu.EDITOR,
                    added("지출과 수입을 등록하고, 고치거나 지울 수 있어요"),
                ),
                menu(
                    PatchMenu.SETTINGS,
                    added("화면 테마를 기기 설정, 밝게, 어둡게 중에서 고를 수 있어요"),
                    added("앱 안에서 새 버전을 확인하고 바로 설치할 수 있어요"),
                ),
            ),
        ),
    )
