package com.dong.budget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

/**
 * 되돌릴 수 없는 동작 앞에 두는 확인 창.
 *
 * 버튼 순서는 왼쪽 확인, 오른쪽 취소다 (사용자 요청).
 * 안드로이드와 iOS 의 기본 대화상자는 반대로 확인이 오른쪽이라
 * 다른 앱에 익숙한 손이 위치만 보고 잘못 누를 수 있다.
 * 그래서 위치 말고 모양으로도 구분한다. 확인은 색이 채워진 버튼(삭제면 빨강),
 * 취소는 회색 버튼이다.
 *
 * 머티리얼의 AlertDialog 는 확인 버튼을 항상 오른쪽에 두어서 직접 만든다.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismissLabel: String = "취소",
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(BudgetTheme.radius.sheet),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = BudgetTheme.elevation.none,
        ) {
            Column(
                modifier =
                Modifier.padding(
                    start = BudgetTheme.spacing.screenHorizontal,
                    end = BudgetTheme.spacing.screenHorizontal,
                    top = BudgetTheme.spacing.sectionPadding * 1.5f,
                    bottom = BudgetTheme.spacing.sectionPadding,
                ),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = BudgetTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(BudgetTheme.spacing.inlineGap))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BudgetTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding * 1.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
                ) {
                    DialogButton(
                        label = confirmLabel,
                        container = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        content = if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                    DialogButton(
                        label = dismissLabel,
                        container = BudgetTheme.colors.sectionBackground,
                        content = BudgetTheme.colors.textPrimary,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, container: Color, content: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BudgetTheme.radius.control)
    Box(
        modifier =
        modifier
            .height(BudgetTheme.size.minTouchTarget)
            .pressScaleClickable(shape = shape, role = Role.Button, onClick = onClick)
            .background(container, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
