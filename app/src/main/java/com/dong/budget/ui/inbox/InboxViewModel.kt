package com.dong.budget.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.capture.PaymentCapture
import com.dong.budget.data.db.BudgetTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** @param clock 지금 시각. 테스트에서 날짜를 고정하려고 바꿀 수 있게 둔다. */
class InboxViewModel(
    repository: TransactionRepository,
    private val capture: PaymentCapture,
    clock: Clock = Clock.system(BudgetTime.ZONE),
) : ViewModel() {
    /** 최근 결제부터. 아직 불러오기 전이면 null 이다. 빈 목록과 구별해야 '아직 온 알림이 없어요' 가 잠깐 깜빡이지 않는다. */
    val items: StateFlow<List<InboxItem>?> =
        observeInbox(capture, repository).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null,
        )

    /** 알림이 온 때를 '오늘'·'어제' 로 적을 기준. 켜 둔 채 자정을 넘기거나 밤새 뒤에 있다 돌아와도 따라온다. */
    val today: StateFlow<LocalDate> =
        BudgetTime.today(clock).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = LocalDate.now(clock),
        )

    /** '모두 읽음'. [dedupKeys] 는 화면에 있던 결제다(PaymentCapture.markAllRead). */
    fun markAllRead(dedupKeys: List<String>) {
        viewModelScope.launch(Dispatchers.IO) { capture.markAllRead(dedupKeys) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
