package com.dong.budget.ui.editor

import androidx.compose.runtime.Composable
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.theme.BudgetTheme

// 거래 지우기. 등록창(수정)과 거래 상세의 상단 바가 같은 버튼과 확인 창을 쓴다.

/** 상단 바의 '삭제'. 되돌릴 수 없는 삭제로 이어지는 버튼이라 최소 터치 크기(48dp)를 지키는 공용 글자 버튼을 쓴다. */
@Composable
internal fun DeleteTransactionButton(onClick: () -> Unit) {
    BudgetTextButton(text = "삭제", onClick = onClick, color = BudgetTheme.colors.danger)
}

/** '삭제' 를 누르면 한 번 더 묻는 창. '지우기' 를 눌러야 [onConfirm] 이 불린다. */
@Composable
internal fun DeleteTransactionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "이 거래를 지울까요?",
        message = "지운 거래는 되돌릴 수 없어요.",
        confirmLabel = "지우기",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
