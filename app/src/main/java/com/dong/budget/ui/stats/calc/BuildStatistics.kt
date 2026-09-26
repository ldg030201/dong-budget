package com.dong.budget.ui.stats.calc

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.ui.stats.StatsUiState
import java.time.LocalDate
import java.time.YearMonth

/**
 * 통계 화면 전체를 계산하는 입구.
 *
 * @param rows [month] 가 들어 있는 조회 창(StatsViewModel 이 읽는 여러 달)의 거래 전부. 이체도 섞여 온다.
 * @param firstRecord 기록 시작일. 거래가 없으면 null
 */
fun buildStatistics(month: YearMonth, today: LocalDate, rows: List<TransactionListItem>, firstRecord: LocalDate?): StatsUiState =
    // TODO(통계 계산): 자리 값. 계산 함수가 들어오면 바꾼다.
    StatsUiState.loading(month, today).copy(loaded = true, hasAnyRecord = firstRecord != null)
