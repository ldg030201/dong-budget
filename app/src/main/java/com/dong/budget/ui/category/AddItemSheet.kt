package com.dong.budget.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.dong.budget.data.AddResult
import com.dong.budget.data.MAX_NAME_LENGTH
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.ui.components.BudgetPrimaryButton
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.FormTextField
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.components.categoryIconRes
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

/**
 * 분류나 결제수단을 새로 만드는 시트. 이름, 색, 아이콘을 고른다.
 *
 * 입력 중인 값은 이 시트 안에만 있다. 저장이 끝나면 부르는 쪽이 시트를 닫고,
 * 닫히면 값도 함께 사라져 다음에 열 때 새로 시작한다.
 *
 * @param usedColors 이미 쓰고 있는 색. 기본 선택을 겹치지 않는 색으로 고른다.
 * @param error 저장이 거절된 이유 (같은 이름이 있다 등). 없으면 null
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddItemSheet(
    title: String,
    namePlaceholder: String,
    usedColors: Set<String>,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, icon: String, color: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(CategoryStyle.firstUnusedColor(usedColors)) }
    var icon by rememberSaveable { mutableStateOf(CategoryStyle.FALLBACK_ICON) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = BudgetTheme.elevation.none,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                // 시트는 키보드 높이를 알아서 빼주지 않는다. 빼지 않으면 이름을 입력하는 동안
                // 아래쪽 아이콘과 '추가하기' 버튼이 키보드 뒤에 깔려 스크롤로도 닿을 수 없다.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
                .padding(bottom = BudgetTheme.spacing.sectionPadding),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = BudgetTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(BudgetTheme.spacing.sectionPadding))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 고른 색과 아이콘이 실제로 어떻게 보일지 미리 보여준다
                CategoryBadge(icon = icon, color = color, size = BudgetTheme.size.badgeLarge)
                Spacer(Modifier.width(BudgetTheme.spacing.itemGap))
                FormTextField(
                    label = "이름",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = namePlaceholder,
                    imeAction = ImeAction.Done,
                    maxLength = MAX_NAME_LENGTH,
                    modifier = Modifier.weight(1f),
                )
            }
            if (error != null) {
                Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = BudgetTheme.colors.danger,
                    // 추가가 거절된 이유를 화면 읽기가 바로 읽어 준다
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            SheetLabel("색")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
                verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
            ) {
                CategoryStyle.COLORS.forEach { key ->
                    ColorDot(key = key, selected = key == color, onClick = { color = key })
                }
            }

            SheetLabel("아이콘")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
                verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
            ) {
                CategoryStyle.ICONS.forEach { key ->
                    IconChoice(key = key, selected = key == icon, color = color, onClick = { icon = key })
                }
            }

            Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            BudgetPrimaryButton(
                text = "추가하기",
                onClick = { onSubmit(name, icon, color) },
                enabled = name.isNotBlank(),
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = BudgetTheme.colors.textSecondary,
        modifier = Modifier.padding(top = BudgetTheme.spacing.sectionPadding, bottom = BudgetTheme.spacing.inlineGap),
    )
}

@Composable
private fun ColorDot(key: String, selected: Boolean, onClick: () -> Unit) {
    val swatch = BudgetTheme.categoryPalette[key]
    Box(
        modifier =
        Modifier
            .size(BudgetTheme.size.minTouchTarget)
            .pressScaleClickable(shape = CircleShape, role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = colorLabel(key)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(BudgetTheme.size.badgeSmall)
                .border(
                    width = BudgetTheme.size.underlineActive,
                    color = if (selected) BudgetTheme.colors.textPrimary else Color.Transparent,
                    shape = CircleShape,
                ).padding(BudgetTheme.size.underlineActive * 2)
                .background(swatch.content, CircleShape),
        )
    }
}

@Composable
private fun IconChoice(key: String, selected: Boolean, color: String, onClick: () -> Unit) {
    // 고르지 않은 아이콘은 회색으로 둔다. 스물네 개가 전부 색칠돼 있으면 화면이 요란하다.
    IconBadge(
        iconRes = categoryIconRes(key),
        swatch = BudgetTheme.categoryPalette[if (selected) color else "gray"],
        size = BudgetTheme.size.minTouchTarget,
        modifier =
        Modifier
            .border(
                width = BudgetTheme.size.underlineActive,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape,
            ).pressScaleClickable(shape = CircleShape, role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = iconLabel(key)
            },
    )
}

/** 기타(회색)와 겹치지 않고 아직 안 쓴 색을 먼저 고른다 */
private fun colorLabel(key: String): String = when (key) {
    "red" -> "빨강"
    "orange" -> "주황"
    "amber" -> "노랑"
    "green" -> "초록"
    "teal" -> "청록"
    "blue" -> "파랑"
    "indigo" -> "남색"
    "purple" -> "보라"
    "pink" -> "분홍"
    else -> "회색"
}

/** 아이콘의 뜻. 화면 읽기 기능이 이 이름으로 읽어준다. */
internal fun iconLabel(key: String): String = when (key) {
    "restaurant" -> "식사"
    "local_cafe" -> "카페"
    "local_bar" -> "술"
    "storefront" -> "가게"
    "shopping_bag" -> "쇼핑"
    "directions_car" -> "자동차"
    "directions_bus" -> "버스"
    "local_gas_station" -> "주유"
    "checkroom" -> "옷"
    "content_cut" -> "미용"
    "event_repeat" -> "정기 지출"
    "home" -> "집"
    "smartphone" -> "휴대폰"
    "local_hospital" -> "병원"
    "school" -> "교육"
    "movie" -> "영화"
    "sports_esports" -> "게임"
    "fitness_center" -> "운동"
    "flight" -> "여행"
    "pets" -> "반려동물"
    "redeem" -> "선물"
    "payments" -> "돈"
    "savings" -> "저금"
    "credit_card" -> "카드"
    "account_balance" -> "은행"
    "account_balance_wallet" -> "지갑"
    "contactless" -> "간편결제"
    "interests" -> "여러 가지"
    else -> "기타"
}

/** 추가가 거절된 이유를 사람이 읽을 문장으로 */
fun AddResult.message(): String? = when (this) {
    is AddResult.Added -> null
    AddResult.BlankName -> "이름을 적어주세요"
    AddResult.NameTooLong -> "이름은 ${MAX_NAME_LENGTH}자까지 쓸 수 있어요"
    AddResult.DuplicateName -> "이미 있는 이름이에요"
}
