package com.dong.budget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

/**
 * 입력칸 한 줄. 위에 작은 이름표, 아래에 값, 맨 아래 밑줄.
 * 지금 입력 중인 칸은 밑줄이 굵은 브랜드색으로 바뀐다.
 *
 * [onClick] 이 있으면 칸 전체를 눌러 아래 입력판(키패드, 분류 표 등)을 여는 칸이 된다.
 * [error] 가 있으면 이름표와 밑줄이 경고색이 되고 밑줄 아래에 그 문구가 나온다.
 */
@Composable
fun FormField(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    error: String? = null,
    content: @Composable () -> Unit,
) {
    val clickable =
        if (onClick != null) {
            Modifier.pressScaleClickable(
                shape = RoundedCornerShape(BudgetTheme.radius.chip),
                role = Role.Button,
                onClick = onClick,
            )
        } else {
            Modifier
        }
    // 밑줄은 누름 영역 밖에 둔다. 누름 효과가 모서리를 둥글게 잘라내서(clip)
    // 안에 두면 밑줄 양 끝이 깎여 다른 칸보다 짧아 보인다.
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .then(clickable)
                .defaultMinSize(minHeight = BudgetTheme.size.formFieldMinHeight)
                .semantics { if (error != null) error(error) }
                .padding(top = BudgetTheme.spacing.itemGap, bottom = BudgetTheme.spacing.inlineGap),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color =
                when {
                    error != null -> BudgetTheme.colors.danger
                    active -> MaterialTheme.colorScheme.primary
                    else -> BudgetTheme.colors.textSecondary
                },
            )
            Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                content()
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (active || error != null) BudgetTheme.size.underlineActive else BudgetTheme.size.underline)
                .background(
                    when {
                        error != null -> BudgetTheme.colors.danger
                        active -> MaterialTheme.colorScheme.primary
                        else -> BudgetTheme.colors.divider
                    },
                ),
        )
        // 문구는 칸 안의 오류 표시(error)로 이미 읽힌다. 새로 나타날 때 한 번 알려주기만 한다.
        if (error != null) ErrorText(error, Modifier.padding(top = BudgetTheme.spacing.tightGap))
    }
}

/** 값이 아직 없을 때 칸 안에 흐리게 보여주는 안내 */
@Composable
fun FormPlaceholder(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = BudgetTheme.colors.textTertiary,
    )
}

@Composable
fun FormValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = BudgetTheme.colors.textPrimary,
    )
}

/** 글자를 직접 입력하는 칸. 누르면 시스템 키보드가 올라온다. */
@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    maxLength: Int = Int.MAX_VALUE,
    error: String? = null,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    FormField(label = label, active = focused, modifier = modifier, error = error) {
        if (value.isEmpty()) FormPlaceholder(placeholder)
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = BudgetTheme.colors.textPrimary),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            modifier =
            Modifier
                .fillMaxWidth()
                // 위쪽 이름표와 안내 문구는 별개의 글자라서, 화면 읽기로는
                // 이 칸이 이름 없는 '편집창' 으로만 읽힌다. 이름을 직접 붙인다.
                .semantics {
                    contentDescription = if (value.isEmpty()) "$label, $placeholder" else label
                    if (error != null) error(error)
                }.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                },
        )
    }
}

/** 입력칸 안에서 아이콘과 글자를 나란히 보여줄 때 */
@Composable
fun FormIconValue(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
        FormValue(text)
    }
}
