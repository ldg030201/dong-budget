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

@Serializable
data object StatisticsKey : AppNavKey

/**
 * 설정. [checkOnOpen] 이면 들어가자마자 새 버전을 다시 확인한다.
 * 패치노트의 [업데이트하러 가기] 로 올 때 쓴다. 패치노트는 배포 목록을 새로 받아 보여주는데,
 * 설정이 들고 있는 마지막 확인 결과는 그보다 오래됐을 수 있어서다(그사이 새 버전이 또 나온 경우 등).
 */
@Serializable
data class SettingsKey(val checkOnOpen: Boolean = false) : AppNavKey

@Serializable
data object PatchNotesKey : AppNavKey
