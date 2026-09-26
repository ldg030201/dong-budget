package com.dong.budget.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.capture.PaymentCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(repository: TransactionRepository, private val capture: PaymentCapture) : ViewModel() {
    /** 최근 결제부터. 아직 불러오기 전이면 null 이다. 빈 목록과 구별해야 '아직 온 알림이 없어요' 가 잠깐 깜빡이지 않는다. */
    val items: StateFlow<List<InboxItem>?> =
        observeInbox(capture, repository).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null,
        )

    /** '모두 읽음'. [dedupKeys] 는 화면에 있던 결제다(PaymentCapture.markAllRead). */
    fun markAllRead(dedupKeys: List<String>) {
        viewModelScope.launch(Dispatchers.IO) { capture.markAllRead(dedupKeys) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
