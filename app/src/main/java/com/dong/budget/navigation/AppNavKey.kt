package com.dong.budget.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 화면 주소.
 *
 * 이 파일은 UI 를 import 하지 않는다. 화면 구현을 통째로 갈아엎어도
 * 네비게이션 구조는 건드리지 않게 하기 위함이다.
 *
 * 직렬화가 필요한 이유: 프로세스가 죽었다 살아날 때 백스택을 복원해야 한다.
 */
@Serializable
sealed interface AppNavKey : NavKey

/**
 * 탭 셸 전체를 나타내는 키 하나.
 *
 * 탭은 백스택에 들어가지 않는다. 셸 안에서만 바뀌는 지역 상태다.
 * 덕분에 서브플로우를 쌓으면 탭바까지 화면과 함께 통째로 밀려나간다.
 */
@Serializable
data object ShellKey : AppNavKey

/**
 * 결제 알림에서 읽어 등록창을 미리 채울 값.
 * 알림을 눌러 들어오면 이 값으로 채워진 채 열리고, 사용자가 확인하고 저장해야 거래가 된다.
 * 자동 기능 스위치(분류·결제수단·할부 채우기)는 열 때의 값을 여기에 담는다. 앱이 다시 떠도 처음 연 모양 그대로 되살아난다.
 *
 * @property paymentName 결제수단을 고를 카드 이름. 같은 이름의 결제수단이 없으면 저장할 때 새로 만든다([addMissingCard]).
 *   '카드 이름으로 결제수단 고르기' 를 껐으면 null 이다.
 * @property memo 채울 메모(할부). '할부는 메모에 적기' 를 껐으면 null 이다.
 * @property dedupKey 같은 결제를 두 번 등록하지 않게 거래에 함께 저장한다.
 * @property guessCategory 같은 가게로 전에 등록한 지출의 분류를 미리 고를지
 * @property addMissingCard [paymentName] 과 같은 결제수단이 없을 때 '신규' 로 골라 두고 저장할 때 만들지. 아니면 비워 둔다.
 * @property source 어디서 채운 값인지. 월급날이면 수입으로 연다.
 * @property categoryCode 미리 고를 기본 분류의 코드(월급날이면 급여 'SALARY'). 사용자가 지웠으면 비워 둔다.
 *
 * 칸을 늘릴 때는 맨 뒤에 기본값을 달아 둔다. 앱이 다시 떠서 되살리는 옛 백스택에는 새 칸이 없다.
 */
@Serializable
data class EditorPrefill(
    val amount: Long,
    val merchant: String,
    val paymentName: String?,
    val memo: String?,
    val occurredAtMillis: Long,
    val dedupKey: String,
    val guessCategory: Boolean = true,
    val addMissingCard: Boolean = true,
    val source: PrefillSource = PrefillSource.PAYMENT_ALERT,
    val categoryCode: String? = null,
)

/** 등록창을 채운 곳 */
@Serializable
enum class PrefillSource {
    /** 토스 결제 알림(지출) */
    PAYMENT_ALERT,

    /** 월급날 알림이나 월급 탭(수입) */
    PAYDAY,
}

/** 거래 등록/수정. transactionId 가 null 이면 새로 등록하는 것이다. prefill 이 있으면 그 값으로 채워 연다. */
@Serializable
data class TransactionEditorKey(val transactionId: Long? = null, val prefill: EditorPrefill? = null) : AppNavKey

/** 거래 상세. 홈·통계에서 거래 줄을 누르면 열린다. 오른쪽 위 '수정' 으로 그 거래의 등록창을 연다. */
@Serializable
data class TransactionDetailKey(val transactionId: Long) : AppNavKey

@Serializable
data object CategoryManageKey : AppNavKey

/** 통계. 아래 메뉴의 '통계' 로 들어온다. 안에서 통계(한눈에 보기)·월별·일별·분류·결제수단으로 나뉜다. */
@Serializable
data object StatisticsKey : AppNavKey

/** 통계 상세가 무엇을 모아 보는지 */
@Serializable
enum class StatsDimension {
    /** 지출 분류 하나 */
    EXPENSE_CATEGORY,

    /** 수입 분류 하나 */
    INCOME_CATEGORY,

    /** 결제수단 하나(지출만) */
    PAYMENT_METHOD,
}

/**
 * 통계에서 분류나 결제수단 하나를 눌러 들어가는 상세.
 * @property id 분류나 결제수단의 id. null 이면 '분류 없음' / '결제수단 없음'
 * @property year 처음 보여 줄 달. 상세 안에서 달을 바꿔도 통계 본 화면의 달은 그대로다.
 */
@Serializable
data class StatsDetailKey(val dimension: StatsDimension, val id: Long?, val year: Int, val month: Int) : AppNavKey

@Serializable
data object SettingsKey : AppNavKey

/** 월급 탭의 설정. 연봉·월급, 출퇴근·점심시간, 일하는 요일, 월급날, 잠금 */
@Serializable
data object SalarySettingsKey : AppNavKey

/** 월급 탭 잠금의 PIN 정하기(처음 켤 때, 바꿀 때) */
@Serializable
data object SalaryPinSetupKey : AppNavKey

/** 앱 잠금의 PIN 정하기(처음 켤 때, 바꿀 때). 설정 > 잠금에서 들어온다. */
@Serializable
data object AppPinSetupKey : AppNavKey

/** 설정의 '고급 설정'. 자동 기능 스위치와 설정·데이터 초기화 */
@Serializable
data object AdvancedSettingsKey : AppNavKey

/** 설정의 '앱 정보'. 지금 버전, 새 버전 확인·내려받기·설치. 홈의 새 버전 알림 줄과 패치노트도 여기로 온다. */
@Serializable
data object AppInfoKey : AppNavKey

@Serializable
data object PatchNotesKey : AppNavKey

/** 홈 오른쪽 위 종으로 여는 알림 화면 */
@Serializable
data object InboxKey : AppNavKey

/** 전체에서 여는 개발자 모드(로그 쌓기·복사) */
@Serializable
data object DeveloperKey : AppNavKey
