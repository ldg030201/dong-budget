package com.dong.budget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dong.budget.ui.theme.BudgetTheme

private val FieldMinHeight = 52.dp

/** 회색 블록 위에 올라가는 한 줄 입력. 테두리 대신 배경색으로 입력 영역을 표시한다. */
@Composable
fun BudgetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val shape = RoundedCornerShape(BudgetTheme.radius.control)
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = FieldMinHeight)
            .background(BudgetTheme.colors.sectionBackground, shape)
            .padding(horizontal = BudgetTheme.spacing.sectionPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = BudgetTheme.colors.textTertiary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle =
            LocalTextStyle.current.merge(
                MaterialTheme.typography.bodyLarge.copy(color = BudgetTheme.colors.textPrimary),
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState -> onFocusChanged(focusState.isFocused) },
        )
    }
}
