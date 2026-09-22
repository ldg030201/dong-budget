package com.dong.budget.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 되돌릴 수 없는 동작 앞에 두는 확인 창.
 *
 * 확인 버튼에만 경고색을 쓰고 취소를 기본처럼 보이게 둔다.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = BudgetTheme.colors.textPrimary,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                    if (destructive) {
                        BudgetTheme.colors.danger
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "취소",
                    style = MaterialTheme.typography.labelLarge,
                    color = BudgetTheme.colors.textSecondary,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = BudgetTheme.colors.textPrimary,
        textContentColor = BudgetTheme.colors.textSecondary,
        // tonal elevation 착색을 쓰지 않는다. 계층은 배경색 차이로만 표현한다.
        tonalElevation = BudgetTheme.elevation.none,
        iconContentColor = Color.Unspecified,
    )
}
