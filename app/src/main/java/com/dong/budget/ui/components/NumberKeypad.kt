package com.dong.budget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dong.budget.R
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

private val KeyHeight = 56.dp

/**
 * 금액 입력용 숫자 키패드.
 *
 * 시스템 키보드를 쓰지 않는 이유: 금액은 숫자만 받으면 되는데
 * 시스템 키보드는 화면을 절반 가까이 덮고 기기마다 모양이 다르다.
 */
@Composable
fun NumberKeypad(onDigit: (String) -> Unit, onDelete: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val backspace: ImageVector = ImageVector.vectorResource(R.drawable.ic_backspace)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
    ) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        ).forEach { row ->
            KeypadRow {
                row.forEach { digit ->
                    KeypadKey(modifier = Modifier.weight(1f), onClick = { onDigit(digit) }) {
                        Text(
                            text = digit,
                            style = BudgetTheme.amount.keypadDigit,
                            color = BudgetTheme.colors.textPrimary,
                        )
                    }
                }
            }
        }
        KeypadRow {
            KeypadKey(modifier = Modifier.weight(1f), onClick = { onDigit("00") }) {
                Text(
                    text = "00",
                    style = BudgetTheme.amount.keypadDigit,
                    color = BudgetTheme.colors.textPrimary,
                )
            }
            KeypadKey(modifier = Modifier.weight(1f), onClick = { onDigit("0") }) {
                Text(
                    text = "0",
                    style = BudgetTheme.amount.keypadDigit,
                    color = BudgetTheme.colors.textPrimary,
                )
            }
            KeypadKey(
                modifier = Modifier.weight(1f),
                onClick = onDelete,
                onLongClick = onClear,
                contentDescription = "지우기",
            ) {
                Icon(
                    imageVector = backspace,
                    contentDescription = null,
                    tint = BudgetTheme.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
    ) {
        content()
    }
}

@Composable
private fun KeypadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(BudgetTheme.radius.control)
    Box(
        modifier =
        modifier
            .height(KeyHeight)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .pressScaleClickable(shape = shape, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
