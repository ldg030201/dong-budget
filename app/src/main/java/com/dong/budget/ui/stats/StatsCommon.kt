package com.dong.budget.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.dong.budget.ui.components.sectionBlock
import com.dong.budget.ui.home.totalColor
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable
import kotlin.reflect.KProperty

// ─────────────────────────────────────────────────────────────────────
// 통계 탭들이 같이 쓰는 부품. 섹션 틀, 섹션 제목, 숫자 한 줄, 빈 상태.
// 흐린 보조 설명은 공용 HintText(ui/components/Basics.kt)를 그대로 쓴다.
// 탭은 LazyColumn 의 섹션마다 StatsSection 하나를 두고, 섹션 사이는 sectionGap 으로 띄운다.
// ─────────────────────────────────────────────────────────────────────

/**
 * 탭 안의 섹션 하나. 화면 좌우 여백 안에 제목과 내용을 세로로 쌓는다.
 *
 * @param title null 이면 제목 없이 내용만 둔다(월별 요약 머리처럼 숫자가 곧 제목인 섹션)
 * @param subtitle 제목 아래 한 줄(예: '최근 3달 (7월~9월)')
 * @param block true 면 제목과 내용을 회색 둥근 묶음(sectionBlock) 안에 넣는다. 숫자 묶음에 쓴다.
 *   차트는 어느 바탕 위에서도 맞게 그리므로 바탕 위에 둔다(false).
 */
@Composable
fun StatsSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    block: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .then(if (block) Modifier.sectionBlock() else Modifier),
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
    ) {
        if (title != null) SectionTitle(text = title, subtitle = subtitle)
        content()
    }
}

/**
 * 섹션 제목. 화면 읽기가 제목으로 건너뛸 수 있게 heading 을 준다.
 * @param subtitle 제목 아래 흐린 한 줄. 제목과 따로 읽는다.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = BudgetTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * 숫자 한 줄. 왼쪽에 라벨(과 그 아래 캡션), 오른쪽에 값.
 * 360dp 에서도 넘치지 않게 가로 칸을 나누지 않고 줄로 쌓는다. 화면 읽기는 한 줄을 한 번에 읽는다.
 *
 * @param value 줄에 보이는 그대로의 값(예: '-32,000원', '+12,000원', '13일', '—')
 * @param valueColor 값 글자색. 돈이면 방향 색(지출 빨강, 수입 초록)을 넘긴다.
 * @param caption 라벨 아래 흐린 설명(예: '27일 기준')
 * @param onClick 누를 수 있는 줄이면 끝에 꺾쇠를 붙인다. null 이면 누를 수 없다.
 */
@Composable
fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = BudgetTheme.colors.textPrimary,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    // 누를 수 있는 줄은 클릭이 알아서 한 줄로 합쳐 읽힌다
                    Modifier.pressScaleClickable(shape = RoundedCornerShape(BudgetTheme.radius.chip), onClick = onClick)
                } else {
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            // 줄마다 높이를 맞춘다. 누를 수 있는 줄도 최소 터치 크기를 지킨다.
            .heightIn(min = BudgetTheme.size.minTouchTarget)
            .padding(vertical = BudgetTheme.spacing.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetTheme.colors.textSecondary,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = BudgetTheme.colors.textSecondary,
                )
            }
        }
        Text(
            text = value,
            style = BudgetTheme.amount.medium,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = BudgetTheme.spacing.inlineGap),
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                // 누를 수 있다는 표시일 뿐이다. 화면 읽기는 '버튼' 으로 알려준다.
                contentDescription = null,
                tint = BudgetTheme.colors.textSecondary,
                modifier = Modifier.size(BudgetTheme.size.iconSmall),
            )
        }
    }
}

/**
 * 보여 줄 거래가 없을 때 한 번만 두는 안내. 제목과 설명을 가운데에 쌓는다.
 * 목록의 한 줄로도, 화면 전체의 빈 상태로도 쓴다.
 */
@Composable
fun StatsEmpty(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal, vertical = BudgetTheme.spacing.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = BudgetTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 순지출 글자색(formatNetExpense 와 짝). 쓴 돈은 지출색(빨강), 돌려받은 돈이 더 많으면 수입색(초록), 0 은 본문색 */
@Composable
internal fun netExpenseColor(amount: Long): Color = totalColor(-amount)

/** 수입이면 수입 쪽, 지출이면 지출 쪽 색(formatDirected 와 짝) */
@Composable
internal fun directedColor(amount: Long, income: Boolean): Color = if (income) totalColor(amount) else netExpenseColor(amount)

/**
 * [scope](달·날짜)에 묶인 저장 상태. 화면을 돌리거나 프로세스가 다시 떠도 남고, [scope] 가 바뀌면 처음(null)으로 돌아간다.
 * rememberSaveable(scope) 만으로는 모자라다. 복원할 때는 inputs 를 보지 않아서, 다른 탭에 있는 동안 달이 바뀌었거나
 * 프로세스가 다시 뜬 뒤에는 옛 달의 값이 돌아온다. 그래서 값을 [scope] 와 함께 저장하고, 다른 [scope] 의 값이면 버린다.
 */
@Stable
internal class ScopedSaveable<T : Any>(private val holder: MutableState<Pair<Any, T>?>, private val scope: Any) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = holder.value?.takeIf { it.first == scope }?.second

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        holder.value = value?.let { scope to it }
    }
}

/** [ScopedSaveable] 을 만든다. [scope] 와 값은 Bundle 에 들어가는 것(YearMonth, LocalDate, Int, String, Boolean 등)이어야 한다. */
@Composable
internal fun <T : Any> rememberScopedSaveable(scope: Any): ScopedSaveable<T> {
    val holder = rememberSaveable(scope) { mutableStateOf<Pair<Any, T>?>(null) }
    return remember(holder, scope) { ScopedSaveable(holder, scope) }
}

/** 섹션 안의 보조 한 줄(보여 줄 것이 없을 때의 안내, 평균 같은 곁들이는 말). 안내 글(HintText)보다 한 단계 크다. */
@Composable
internal fun SectionNote(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = BudgetTheme.colors.textSecondary, modifier = modifier)
}
