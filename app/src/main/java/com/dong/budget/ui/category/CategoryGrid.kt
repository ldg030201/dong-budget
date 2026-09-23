package com.dong.budget.ui.category

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.dong.budget.data.db.CategoryEntity
import com.dong.budget.ui.components.CategoryBadge
import com.dong.budget.ui.components.IconBadge
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable

private const val COLUMNS = 4

/**
 * 분류를 고르는 표. 한 줄에 네 개씩, 맨 끝에 '추가' 칸.
 * 등록 화면 아래 입력판으로 쓴다.
 */
@Composable
fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        modifier = modifier,
        contentPadding = PaddingValues(vertical = BudgetTheme.spacing.inlineGap),
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.itemGap),
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryTile(
                selected = category.id == selectedId,
                label = category.name,
                onClick = { onSelect(category.id) },
            ) { CategoryBadge(icon = category.icon, color = category.color, size = BudgetTheme.size.badgeLarge) }
        }
        item(key = "add") {
            CategoryTile(selected = false, label = "추가", onClick = onAdd) {
                IconBadge(
                    iconRes = R.drawable.ic_sym_add,
                    swatch = BudgetTheme.categoryPalette["gray"],
                    size = BudgetTheme.size.badgeLarge,
                )
            }
        }
    }
}

@Composable
private fun CategoryTile(selected: Boolean, label: String, onClick: () -> Unit, badge: @Composable () -> Unit) {
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
