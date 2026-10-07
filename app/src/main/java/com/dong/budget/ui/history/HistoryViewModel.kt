package com.dong.budget.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

/**
 * 내역 화면의 상태. 모든 거래를 한 번에 읽어 검색용 목록([searchIndex])으로 만들어 두고, 검색어가 바뀔 때마다 순수 함수([buildHistory])로 다시 거른다.
 * 검색용 목록은 DB 가 새 목록을 줄 때만 다시 만든다.
 * 거래를 고치거나 지우면 조회가 다시 내보내서 목록이 바로 바뀐다.
 *
 * 검색어는 입력칸이 바로 읽고 쓰는 화면 상태로 둔다(흐름을 거치면 빠르게 칠 때 글자가 밀린다).
 * 앱이 내려갔다 되살아나도 이어지게 저장 상태에도 적는다.
 *
 * @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다.
 */
class HistoryViewModel(
    repository: TransactionRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.system(BudgetTime.ZONE),
) : ViewModel() {
    /** 검색어 */
    var query: String by mutableStateOf(savedState[QUERY_KEY] ?: "")
        private set

    fun search(text: String) {
        query = text
        savedState[QUERY_KEY] = text
    }

    val uiState: StateFlow<HistoryUiState> =
        combine(repository.observeAll().map(::searchIndex), snapshotFlow { query }, BudgetTime.today(clock)) { index, text, today ->
            buildHistory(index, text, today)
        }
            // 거르기와 묶기는 기본 풀에서 한다. 조회는 Room 이 자기 스레드에서 한다.
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = HistoryUiState.loading(query, LocalDate.now(clock)),
            )

    private companion object {
        const val QUERY_KEY = "query"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
