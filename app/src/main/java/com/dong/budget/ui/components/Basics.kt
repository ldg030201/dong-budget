package com.dong.budget.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion

/** 화면 안 묶음의 작은 제목(예: '화면 테마', '색') */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = BudgetTheme.colors.textSecondary,
        modifier = modifier.padding(top = BudgetTheme.spacing.sectionPadding, bottom = BudgetTheme.spacing.inlineGap),
    )
}

/** 흐린 보조 설명 */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = BudgetTheme.colors.textSecondary,
        modifier = modifier,
    )
}

/** 저장·추가가 거절된 이유나 빈 칸 안내. 새로 나타나면 화면 읽기가 바로 읽어 준다. */
@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = BudgetTheme.colors.danger,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * [text] 가 있을 때만 [ErrorText] 를 보여준다. 문구가 생기거나 없어질 때 아래 내용이 한 번에 밀리지 않게 펼치며 나온다.
 * 사라지는 동안에는 마지막 문구를 그대로 둔다.
 */
@Composable
fun AnimatedErrorText(text: String?, modifier: Modifier = Modifier) {
    AnimatedLine(text) { ErrorText(it, modifier) }
}

/** [text] 가 있을 때만 [HintText] 를 보여준다. 나타나고 사라지는 모양은 [AnimatedErrorText] 와 같다. */
@Composable
fun AnimatedHintText(text: String?, modifier: Modifier = Modifier) {
    AnimatedLine(text) { HintText(it, modifier) }
}

/** [text] 가 생기면 펼치며 나오고 없어지면 접히며 사라진다. 사라지는 동안에는 마지막 문구로 [content] 를 그린다. */
@Composable
private fun AnimatedLine(text: String?, content: @Composable (String) -> Unit) {
    val last = remember { LastText() }
    if (text != null) last.value = text
    AnimatedVisibility(
        visible = text != null,
        enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
        exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.quick()),
    ) {
        last.value?.let { content(it) }
    }
}

/** 사라지는 동안 보여줄 마지막 문구. 그리기 중에 적어도 다시 그리게 하지 않는 보관함이다. */
private class LastText(var value: String? = null)

/** 목록이나 묶음 사이의 가는 구분선 */
@Composable
fun BudgetDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(BudgetTheme.size.underline)
            .background(BudgetTheme.colors.divider),
    )
}

/** 회색 바탕의 둥근 묶음(홈 요약). 패치노트 버전 카드도 같은 모양이지만, 머리를 누를 수 있게 여백을 따로 준다. */
@Composable
fun Modifier.sectionBlock(): Modifier = this
    .background(BudgetTheme.colors.sectionBackground, RoundedCornerShape(BudgetTheme.radius.block))
    .padding(BudgetTheme.spacing.sectionPadding)

/** 새 알림 표시 점. 홈의 종과 알림 화면의 새 알림 줄이 같이 쓴다. 화면 읽기는 따로 알려주므로 점은 읽지 않는다. */
@Composable
fun NoticeDot(modifier: Modifier = Modifier) {
    Box(modifier.size(BudgetTheme.size.noticeDot).background(BudgetTheme.colors.danger, CircleShape))
}

/** [visible] 에 따라 [NoticeDot] 이 톡 튀어나오고 줄어들며 사라진다 */
@Composable
fun AnimatedNoticeDot(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(Motion.standard()) + fadeIn(Motion.quick()),
        exit = scaleOut(Motion.standard()) + fadeOut(Motion.quick()),
    ) {
        NoticeDot()
    }
}
