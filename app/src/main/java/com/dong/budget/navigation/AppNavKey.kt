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
 *
 * @property paymentName 카드 이름. 같은 이름의 결제수단이 없으면 저장할 때 새로 만든다.
 * @property dedupKey 같은 결제를 두 번 등록하지 않게 거래에 함께 저장한다.
 */
@Serializable
data class EditorPrefill(
    val amount: Long,
    val merchant: String,
    val paymentName: String?,
    val memo: String?,
    val occurredAtMillis: Long,
    val dedupKey: String,
)

/** 거래 등록/수정. transactionId 가 null 이면 새로 등록하는 것이다. prefill 이 있으면 그 값으로 채워 연다. */
@Serializable
data class TransactionEditorKey(val transactionId: Long? = null, val prefill: EditorPrefill? = null) : AppNavKey

@Serializable
data object CategoryManageKey : AppNavKey

/** 통계. 아래 메뉴의 '통계' 로 들어온다. 안에서 월별·일별·분류·결제수단으로 나뉜다. */
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

@Serializable
data object PatchNotesKey : AppNavKey

/** 홈 오른쪽 위 종으로 여는 알림 화면 */
@Serializable
data object InboxKey : AppNavKey
