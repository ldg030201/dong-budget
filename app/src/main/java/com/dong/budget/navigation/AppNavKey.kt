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

/** 거래 등록/수정. transactionId 가 null 이면 새로 등록하는 것이다. */
@Serializable
data class TransactionEditorKey(val transactionId: Long? = null) : AppNavKey

@Serializable
data object StatisticsKey : AppNavKey

@Serializable
data object SettingsKey : AppNavKey
