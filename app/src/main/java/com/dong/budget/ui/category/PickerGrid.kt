package com.dong.budget.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.dong.budget.R
import com.dong.budget.data.db.CategoryStyle
import com.dong.budget.data.db.StyledItem
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

private const val COLUMNS = 4

/**
 * 아직 없지만 저장하면 새로 생길 항목. 알림에서 읽은 카드가 결제수단에 없을 때 쓴다.
 * 새 항목은 맨 뒤에 붙으므로 표에서도 맨 뒤('추가' 앞)에 '신규' 꼬리표를 달고 보여준다.
 */
data class PickerPreview(val name: String, val icon: String, val color: String, val selected: Boolean, val onSelect: () -> Unit)

/**
 * 아이콘 붙은 항목을 고르는 표. 한 줄에 네 개씩, 맨 끝에 '추가' 칸.
 * 등록 화면 아래 입력판으로 쓴다. 분류와 결제수단이 같이 쓴다.
 */
@Composable
fun PickerGrid(
    items: List<StyledItem>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    preview: PickerPreview? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        modifier = modifier,
        contentPadding = PaddingValues(vertical = BudgetTheme.spacing.inlineGap),
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.itemGap),
    ) {
        items(items, key = { it.id }) { item ->
            PickerTile(
                selected = item.id == selectedId,
                label = item.name,
                onClick = { onSelect(item.id) },
            ) { CategoryBadge(icon = item.icon, color = item.color, size = BudgetTheme.size.badgeLarge) }
        }
        if (preview != null) {
            item(key = "preview") {
                PickerTile(selected = preview.selected, label = preview.name, onClick = preview.onSelect, tag = "신규") {
                    CategoryBadge(icon = preview.icon, color = preview.color, size = BudgetTheme.size.badgeLarge)
                }
            }
        }
        item(key = "add") {
            PickerTile(selected = false, label = "추가", onClick = onAdd) {
                IconBadge(
                    iconRes = R.drawable.ic_sym_add,
                    swatch = BudgetTheme.categoryPalette[CategoryStyle.FALLBACK_COLOR],
                    size = BudgetTheme.size.badgeLarge,
                )
            }
        }
    }
}

/** @param tag 아이콘 오른쪽 위에 붙는 꼬리표. 없으면 null */
@Composable
private fun PickerTile(selected: Boolean, label: String, onClick: () -> Unit, tag: String? = null, badge: @Composable () -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .pressScaleClickable(
                shape = RoundedCornerShape(BudgetTheme.radius.control),
                role = Role.RadioButton,
                onClick = onClick,
            ).semantics { this.selected = selected }
            .padding(vertical = BudgetTheme.spacing.tightGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            // 고른 칸은 아이콘 둘레에 브랜드색 고리를 두른다. 색만이 아니라 모양으로도 구분된다.
            Box(
                modifier =
                Modifier
                    .size(BudgetTheme.size.badgeLarge + BudgetTheme.spacing.inlineGap)
                    .border(
                        width = BudgetTheme.size.underlineActive,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                badge()
            }
            if (tag != null) {
                // 패치노트의 '신규' 와 같은 모양. 고리 위에 그려서 고리가 글자를 가로지르지 않게 하고,
                // 오른쪽 위로 조금 나가게 붙인다.
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = BudgetTheme.spacing.inlineGap + BudgetTheme.spacing.tightGap, y = -BudgetTheme.spacing.tightGap)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(BudgetTheme.radius.full))
                        .padding(horizontal = BudgetTheme.spacing.inlineGap, vertical = BudgetTheme.spacing.tightGap / 2),
                )
            }
        }
        Spacer(Modifier.height(BudgetTheme.spacing.tightGap))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else BudgetTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = BudgetTheme.spacing.tightGap),
        )
    }
}
