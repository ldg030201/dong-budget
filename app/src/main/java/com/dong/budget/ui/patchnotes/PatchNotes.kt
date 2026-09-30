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

    /** 1.5.0 에서 생긴 아래 메뉴 '월급'(실시간 월급) */
    SALARY("월급"),

    /** 0.1.3 에서 홈으로 합쳐진 옛 탭. 지난 기록을 위해 남긴다. */
    HISTORY("내역"),

    /** 1.4.0 에서 생긴 거래 상세. 홈·통계에서 거래를 누르면 열린다. */
    DETAIL("거래 상세"),
    EDITOR("거래 등록"),
    STATISTICS("통계"),
    CATEGORIES("분류 관리"),
    MORE("전체"),
    PATCH_NOTES("패치노트"),
    DEVELOPER("개발자 모드"),
    SETTINGS("설정"),

    /** 특정 메뉴가 아니라 앱 전체에 걸친 변화 */
    COMMON("공통"),
}

/**
 * 한 메뉴에서 바뀐 것들.
 * @property isNew 이 버전에서 처음 생긴 메뉴인지. 메뉴 이름 옆에 '신규' 를 붙인다.
 */
data class MenuChanges(val menu: PatchMenu, val changes: List<Change>, val isNew: Boolean = false)

/** @property date 낸 날. 아직 내지 않은 버전은 null */
data class Release(val version: String, val date: LocalDate?, val menus: List<MenuChanges>)

private fun added(text: String) = Change(ChangeKind.ADDED, text)

private fun improved(text: String) = Change(ChangeKind.IMPROVED, text)

private fun changed(text: String) = Change(ChangeKind.CHANGED, text)

private fun fixed(text: String) = Change(ChangeKind.FIXED, text)

private fun menu(menu: PatchMenu, vararg changes: Change) = MenuChanges(menu, changes.toList())

/** 이 버전에서 처음 생긴 메뉴 */
private fun newMenu(menu: PatchMenu, vararg changes: Change) = MenuChanges(menu, changes.toList(), isNew = true)

/**
 * 최신 버전이 맨 위. 메뉴는 앱 화면 순서(홈 → 월급 → 거래 상세 → 거래 등록 → 통계 → 분류 관리 → 전체 → 패치노트 → 개발자 모드 → 설정 → 공통)로 적는다.
 * 메뉴 안의 항목은 화면에서 종류 순서(추가 → 개선 → 수정 → 오류수정)로 다시 정렬되므로 적는 순서는 자유다.
 * 그 버전에서 처음 생긴 메뉴는 [newMenu] 로 적는다(메뉴 이름 옆 '신규'). 첫 버전(0.1.0)은 모두 처음이라 붙이지 않는다.
 */
val PATCH_NOTES: List<Release> =
    listOf(
        Release(
            version = "1.5.1",
            date = LocalDate.of(2026, 9, 30),
            menus =
            listOf(
                menu(
                    PatchMenu.EDITOR,
                    fixed("토스뱅크 체크카드 결제에 캐시백이 붙어 'N원 캐시백'으로 오는 토스 알림에는 등록할지 묻지 않던 문제를 고쳤어요"),
                ),
            ),
        ),
        Release(
            version = "1.5.0",
            date = LocalDate.of(2026, 9, 29),
            menus =
            listOf(
                newMenu(
                    PatchMenu.SALARY,
                    added("아래 메뉴와 전체에 '월급'이 생겼어요. 일하는 동안 오늘 번 돈이 초마다 올라가고, 오늘 쓴 돈과 견줘 보여 줘요"),
                    added("'월급날부터 번 돈'과 올해 번 돈, 1초·1시간에 버는 돈(₩/s, ₩/h), 통상시급, 월급날까지 남은 날을 보여 줘요"),
                    added("세전 연봉이나 월급, 실수령 월급(선택), 출퇴근·점심시간, 일하는 요일, 입사일, 월급날을 정할 수 있어요. 실수령을 적으면 그 돈으로 쌓여요"),
                    added("월급날 출근 시각에 '월급 들어왔나요?' 알림을 띄워요. 알림이나 월급 탭의 등록 버튼을 누르면 월급이 채워진 수입 등록창이 열려요"),
                    added("월급 탭을 PIN 4자리로 잠그고 지문으로도 열 수 있어요. 앱을 나갔다 오면 다시 잠겨요"),
                    added("월급 탭 맨 아래 '최근 내역'에서 최근에 쓴 돈마다 얼마나 일한 값인지 보여 줘요"),
                ),
                menu(
                    PatchMenu.MORE,
                    fixed("가로 화면에서 목록 아래쪽 줄이 가려져 누를 수 없던 문제를 고쳤어요"),
                ),
                menu(
                    PatchMenu.SETTINGS,
                    added("백업: 거래·분류·결제수단과 월급 설정을 JSON으로 복사하거나 다운로드 폴더에 파일로 저장할 수 있어요"),
                    added("복원: 백업 파일이나 복사한 JSON으로 되살릴 수 있어요. 담긴 내용을 먼저 보여 주고, 지금 데이터를 백업 내용으로 바꿔요"),
                    added("고급 설정: 설정 초기화(화면 테마·자동 기능)와 데이터 초기화(거래·분류·결제수단·월급 설정)를 할 수 있어요"),
                    changed("설정을 새로 정리했어요. 자동 기능 스위치(새 버전 자동 확인 포함)는 '고급 설정'으로, 업데이트는 새 '앱 정보' 화면으로 옮겼어요"),
                ),
                menu(
                    PatchMenu.COMMON,
                    improved("어두운 테마에서 지출/수입처럼 나란히 놓인 칸 중 고른 칸이 더 또렷하게 보여요"),
                ),
            ),
        ),
        Release(
            version = "1.4.1",
            date = LocalDate.of(2026, 9, 29),
            menus =
            listOf(
                menu(
                    PatchMenu.SETTINGS,
                    added("앱이 알아서 골라 주거나 채워 주던 기능을 하나씩 켜고 끌 수 있어요. 처음에는 모두 켜져 있어요"),
                    added("결제 알림: 등록할지 묻기, 같은 결제 알림은 한 번만 묻기, 앱을 열 때 놓친 알림 다시 살피기"),
                    added("알림으로 등록할 때: 같은 가게면 지난 분류 고르기, 카드 이름으로 결제수단 고르기, 없는 카드는 새로 추가하기, 할부는 메모에 적기"),
                    added("거래 등록: 금액 키패드 바로 열기, 새로 만든 분류·결제수단 바로 고르기, 새 분류·결제수단은 안 쓴 색으로"),
                    added("통계: 일별에서 볼 날 자동으로 고르기. 앱 정보: 앱을 열 때 새 버전 확인하기"),
                ),
            ),
        ),
        Release(
            version = "1.4.0",
            date = LocalDate.of(2026, 9, 28),
            menus =
            listOf(
                menu(
                    PatchMenu.HOME,
                    changed("지난달과 견준 금액을 반올림하지 않고 내림으로 적어요"),
                    fixed("달력에서 날짜를 누른 뒤 목록을 손으로 올리면 맨 위 달력에 처음 누른 날이 그대로 칠해져 있던 문제를 고쳤어요. 이제 끌기 시작하면 위의 한 주 줄이 스크롤을 따라가요"),
                ),
                newMenu(
                    PatchMenu.DETAIL,
                    added("홈과 통계에서 내역을 누르면 바로 수정 화면 대신 거래 상세가 열려요. 금액, 분류, 결제수단, 날짜, 시간, 메모를 한눈에 봐요"),
                    added("고치려면 오른쪽 위 '수정'을 눌러요"),
                    added("아래 '최근 내역'에서 같은 곳에서 쓴 내역을 최근 1년까지 달별로 모아 보여 주고, 몇 번 썼는지와 모두 얼마인지, 한 번에 평균도 알려 줘요. 누르면 그 내역의 상세로 가요"),
                ),
                menu(
                    PatchMenu.EDITOR,
                    added("금액을 1만원 이상 적으면 그 아래에 '1억 2,345만 6,789원'처럼 만·억 단위로 끊어 1원까지 보여 줘요"),
                ),
                menu(
                    PatchMenu.STATISTICS,
                    added("아래 메뉴의 '통계'를 누르면 새로 생긴 첫 칸 '통계'부터 보여요. 이번 달 쓴 돈, 수입과 지출, 하루 기록, 눈에 띄는 점, 큰 지출을 한 화면에서 봐요"),
                    changed("월별은 흐름·최근 6개월·올해 모아 보기를, 일별은 날마다 쓴 돈·요일별 하루 평균을 자세히 보는 곳으로 정리했어요. 결제수단은 위의 합계를 빼고 그래프와 순위만 보여요"),
                    improved("통계를 열면 아래 메뉴의 '통계'가 아래에 떠오르는 메뉴의 첫 칸 '통계' 자리로 옮겨 가고, 나올 때는 다시 아래 메뉴로 돌아가요"),
                    changed("만원 단위로 줄여 쓴 금액을 반올림하지 않고 내림으로 적어요. 지출이 2,076,048원이면 도넛 가운데에 208만원이 아니라 207만원으로 보여서 위의 금액과 어긋나 보이지 않아요"),
                ),
            ),
        ),
        Release(
            version = "1.3.0",
            date = LocalDate.of(2026, 9, 28),
            menus =
            listOf(
                menu(
                    PatchMenu.EDITOR,
                    improved("결제 알림에서 가져온 새 카드가 결제수단 표 맨 뒤에 '신규'로 보여서, 다른 결제수단을 골랐다가도 다시 고를 수 있어요"),
                    fixed("토스가 결제 한 건을 알림 두 개로 보내면 등록할지 두 번 묻던 문제를 고쳤어요"),
                    fixed("결제 알림에서 가져온 카드 이름이 10자에서 잘리던 문제를 고쳤어요. 전에 잘린 이름으로 만들어진 결제수단은 그대로 이어서 써요"),
                ),
                menu(
                    PatchMenu.STATISTICS,
                    improved("차트가 처음 보일 때와 달을 넘길 때 차오르고, 고른 날 표시는 미끄러져 옮겨가요"),
                ),
                menu(
                    PatchMenu.CATEGORIES,
                    improved("길게 눌러 끌면 줄이 살짝 떠오르고, 놓으면 진동으로 알려줘요"),
                    fixed("분류·결제수단 이름을 10자까지만 쓸 수 있던 것을 20자까지 늘렸어요. 고르는 표에서는 긴 이름이 두 줄까지 보여요"),
                    fixed("지출·수입·결제수단 탭을 바꿀 때 목록이 깜빡이던 문제를 고쳤어요"),
                ),
                menu(
                    PatchMenu.DEVELOPER,
                    added("개발자 모드가 켜져 있으면 모든 화면 오른쪽 아래에 벌레 표시가 반투명하게 떠요. 표시만 하고 눌리지 않아서 밑의 버튼도 그대로 눌려요"),
                ),
                menu(
                    PatchMenu.COMMON,
                    improved("화면을 오갈 때 옆으로 밀려 들어오고 빠져요. 전에는 0.7초 동안 두 화면이 함께 흐려졌어요"),
                    improved("누르거나 고를 때, 달을 넘길 때, 목록과 입력판이 바뀔 때 움직임을 부드럽게 다듬었어요"),
                    fixed("아래 메뉴와 + 버튼, 개발자 모드 스위치처럼 눌러도 아무 반응이 없던 곳이 눌리게 고쳤어요"),
                ),
            ),
        ),
        Release(
            version = "1.2.0",
            date = LocalDate.of(2026, 9, 27),
            menus =
            listOf(
                menu(
                    PatchMenu.EDITOR,
                    fixed("토스뱅크 카드처럼 '결제 완료'로 오는 토스 결제 알림에는 등록할지 묻지 않던 문제를 고쳤어요"),
                ),
                newMenu(
                    PatchMenu.STATISTICS,
                    added("아래 메뉴와 전체에서 들어가요. 들어가면 아래에 떠 있는 메뉴에서 월별·일별·분류·결제수단으로 나눠 볼 수 있어요"),
                    added("월별에서는 쓴 돈과 수입 대비 비율, 눈에 띄는 점, 지난달과 견준 이번 달 흐름, 최근 6개월, 큰 지출, 올해 모아 보기를 봐요"),
                    added("일별에서는 날마다 쓴 돈을 분류 순서대로 분류별 색을 쌓아 보여 줘요. 날을 고르면 그날 분류별 금액과 거래를 보고, 분류를 누르면 그 분류만 볼 수 있어요. 요일별 하루 평균도 봐요"),
                    added("분류·결제수단에서는 비율 그래프와 순위, 지난달 대비 증감, 많이 쓴 곳을 봐요. 누르면 최근 6개월 흐름과 그 달 거래를 모아 봐요"),
                    added("통계의 금액은 지출은 -(빨간색), 수입은 +(초록색)으로 적어서 한눈에 구분돼요"),
                ),
                newMenu(
                    PatchMenu.DEVELOPER,
                    added("전체에서 들어가요. 켜 두면 오류와 결제 알림 처리 기록이 쌓이고, 복사해서 보낼 수 있어요"),
                    added("처음에는 꺼져 있어요. 켤 때 무엇이 쌓이는지 먼저 알려 주고, 끄면 쌓인 기록을 모두 지워요"),
                ),
            ),
        ),
        Release(
            version = "1.1.0",
            date = LocalDate.of(2026, 9, 27),
            menus =
            listOf(
                menu(
                    PatchMenu.HOME,
                    added("오른쪽 위 종을 누르면 알림 화면에서 그동안 온 결제 등록 알림을 모아 볼 수 있어요. 누르면 알림에서처럼 등록창이 열려요"),
                    added("아직 안 본 알림은 바탕색과 빨간 점으로 표시돼요. 새 알림이 있으면 종에도 빨간 점이 떠요"),
                    added("'모두 읽음'을 누르면 표시가 사라지고, 알림창에 남은 등록 알림도 함께 치워요"),
                ),
                menu(
                    PatchMenu.PATCH_NOTES,
                    improved("버전을 눌러 바뀐 점을 접고 펼칠 수 있어요. 처음에는 가장 최근 버전만 펼쳐져 있어요"),
                ),
                menu(
                    PatchMenu.SETTINGS,
                    improved("어두운 화면에서 업데이트 부분의 글자 버튼('받은 파일로 설치', '브라우저에서 받기')이 더 잘 보여요"),
                ),
                menu(
                    PatchMenu.COMMON,
                    improved("화면 읽기(TalkBack)에서 화면 제목으로 바로 건너뛸 수 있어요"),
                    fixed("가로 화면에서 버튼과 글자가 옆의 시스템 버튼 줄이나 카메라 구멍에 가려지던 문제를 고쳤어요"),
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
                newMenu(
                    PatchMenu.CATEGORIES,
                    added("분류와 결제수단을 추가하고 지울 수 있어요"),
                    added("분류와 결제수단마다 아이콘과 색이 생겼어요"),
                    added("분류를 지우면 그 분류의 거래는 '기타' 로 옮겨져요"),
                    changed("기본 분류를 식비, 교통/차량, 편의점, 패션/미용, 고정지출, 기타와 급여, 용돈, 기타로 바꿨어요. 쓰던 분류와 거래는 그대로 남아요"),
                ),
                menu(
                    PatchMenu.MORE,
                    improved("메뉴마다 아이콘을 달았어요"),
                ),
                newMenu(
                    PatchMenu.PATCH_NOTES,
                    added("전체 메뉴에서 버전별로 바뀐 점을 볼 수 있어요"),
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
