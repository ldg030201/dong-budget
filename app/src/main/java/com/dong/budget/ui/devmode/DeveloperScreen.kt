package com.dong.budget.ui.devmode

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.devlog.LogEntry
import com.dong.budget.data.devlog.LogLevel
import com.dong.budget.data.devlog.formatLogTime
import com.dong.budget.ui.components.BudgetDivider
import com.dong.budget.ui.components.BudgetSmallButton
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.sectionBlock
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.Motion
import com.dong.budget.ui.theme.pressFeedback
import com.dong.budget.ui.theme.pressScaleClickable

/**
 * 개발자 모드. 전체 메뉴에서 들어온다.
 *
 * 맨 위 스위치로 켜고 끈다(처음에는 꺼져 있다). 켜면 그 아래에 로그가 쌓이고, 복사해서 보낼 수 있다.
 * - 켤 때: 로그에 결제 알림 내용이 들어간다는 주의를 확인창으로 먼저 보여 준다.
 * - 끌 때: 쌓인 로그를 지운다. 쌓인 것이 있으면 지워진다고 먼저 묻는다.
 * 로그는 아래로 쌓인다. 처음에는 가장 최근 것(맨 아래)이 보이고, 새 로그가 들어오면 맨 아래에 붙는다.
 *
 * @param entries 오래된 것부터
 * @param onCopy 복사 버튼. 앱·기기 정보를 붙인 로그 전체를 클립보드에 넣는다.
 */
@Composable
fun DeveloperScreen(
    enabled: Boolean,
    entries: List<LogEntry>,
    onEnabledChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf<Confirm?>(null) }
    confirming?.let { confirm ->
        ConfirmDialogFor(
            confirm = confirm,
            count = entries.size,
            onConfirm = {
                confirming = null
                when (confirm) {
                    Confirm.ENABLE -> onEnabledChange(true)
                    Confirm.DISABLE -> onEnabledChange(false)
                    Confirm.CLEAR -> onClear()
                }
            },
            onDismiss = { confirming = null },
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(onNavigationClick = onBack, title = "개발자 모드")
            ModeSwitch(
                enabled = enabled,
                onToggle = { turnOn ->
                    when {
                        turnOn -> confirming = Confirm.ENABLE

                        // 지울 것이 없으면 묻지 않고 끈다
                        entries.isEmpty() -> onEnabledChange(false)

                        else -> confirming = Confirm.DISABLE
                    }
                },
            )
            if (enabled) {
                LogHeader(count = entries.size, onCopy = onCopy, onClear = { confirming = Confirm.CLEAR })
                BudgetDivider(Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal))
                LogList(entries = entries, modifier = Modifier.weight(1f))
            } else {
                HintText(
                    text = "오류가 나면 개발자 모드를 켜고 같은 일을 한 번 더 해 본 뒤, 로그를 복사해서 보내 주세요.",
                    modifier = Modifier.padding(horizontal = BudgetTheme.spacing.screenHorizontal),
                )
            }
        }
    }
}

/** 확인창 종류 */
private enum class Confirm { ENABLE, DISABLE, CLEAR }

@Composable
private fun ConfirmDialogFor(confirm: Confirm, count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (confirm) {
        Confirm.ENABLE ->
            ConfirmDialog(
                title = "개발자 모드를 켤까요?",
                message =
                "켜 두는 동안 이 폰에 로그가 계속 쌓여요. 로그에는 토스 결제 알림 내용(금액·카드·가게 이름), " +
                    "거래 번호, 열어 본 화면, 오류 내용이 들어가요.\n\n" +
                    "로그를 복사해서 보내면 이 내용도 함께 가니 보낼 곳을 조심해 주세요. 확인이 끝나면 꺼 주세요.",
                confirmLabel = "켜기",
                destructive = false,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )

        Confirm.DISABLE ->
            ConfirmDialog(
                title = "개발자 모드를 끌까요?",
                message = "끄면 지금까지 쌓인 로그 ${count}건이 모두 지워져요. 보낼 로그가 있으면 먼저 복사해 주세요.",
                confirmLabel = "끄고 지우기",
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )

        Confirm.CLEAR ->
            ConfirmDialog(
                title = "로그를 지울까요?",
                message = "쌓인 로그 ${count}건이 모두 지워져요. 개발자 모드는 켜진 채로 두고, 이후 일은 다시 쌓여요.",
                confirmLabel = "지우기",
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
    }
}

/**
 * 맨 위 켜기·끄기. 줄 전체가 스위치로 읽히고 눌린다.
 * 켜져 있는 동안에는 무엇이 쌓이는지 줄 아래에 늘 적어 둔다.
 */
@Composable
private fun ModeSwitch(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Column(
        modifier =
        Modifier
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal)
            .padding(bottom = BudgetTheme.spacing.itemGap)
            .sectionBlock(),
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
    ) {
        // 앱은 리플을 꺼 두었으므로(Theme) 기본 누름 표시로는 아무것도 안 보인다. 다른 버튼과 같은 눌림과 포커스 테두리를 입힌다.
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .pressFeedback(interactionSource, RoundedCornerShape(BudgetTheme.radius.chip))
                .toggleable(
                    value = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Switch,
                    onValueChange = onToggle,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap)) {
                Text(text = "개발자 모드", style = MaterialTheme.typography.titleMedium, color = BudgetTheme.colors.textPrimary)
                Text(
                    text = if (enabled) "켜져 있어요. 로그를 쌓고 있어요" else "꺼져 있어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = BudgetTheme.colors.textSecondary,
                )
            }
            // 줄이 대신 눌리므로 스위치 자체는 누름을 받지 않는다
            Switch(checked = enabled, onCheckedChange = null)
        }
        AnimatedVisibility(
            visible = enabled,
            enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
            exit =
            shrinkVertically(Motion.standard()) + fadeOut(Motion.quick()),
        ) {
            HintText("결제 알림 내용과 오류가 이 폰에 쌓이고 있어요. 확인이 끝나면 꺼 주세요. 끄면 로그도 지워져요.")
        }
    }
}

/** 로그 건수와 복사·지우기 */
@Composable
private fun LogHeader(count: Int, onCopy: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
    ) {
        Text(
            text = "로그 ${count}건",
            style = MaterialTheme.typography.titleSmall,
            color = BudgetTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        BudgetTextButton(text = "지우기", onClick = onClear, enabled = count > 0)
        BudgetSmallButton(text = "복사", onClick = onCopy, enabled = count > 0)
    }
}

/**
 * 로그 목록. 아래로 쌓는다(reverseLayout). 처음에는 맨 아래 가장 최근 로그가 보이고,
 * 맨 아래를 보고 있으면 새 로그가 붙을 때 따라 내려간다. 위로 올려 읽는 중이면 그 자리에 머문다.
 * 몇 건 없을 때는 바닥이 아니라 위(건수 줄 바로 아래)부터 채운다.
 */
@Composable
private fun LogList(entries: List<LogEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) {
        HintText(
            text = "아직 쌓인 로그가 없어요. 앱을 쓰면 여기에 쌓여요.",
            modifier = modifier.fillMaxWidth().padding(BudgetTheme.spacing.screenHorizontal),
        )
        return
    }
    LazyColumn(
        // 맨 아래(가장 최근 로그)가 제스처 막대에 가리지 않게 한다
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        reverseLayout = true,
        verticalArrangement = Arrangement.Top,
        contentPadding = PaddingValues(vertical = BudgetTheme.spacing.inlineGap),
    ) {
        // reverseLayout 이라 첫 항목이 맨 아래에 온다. 키를 주지 않고 자리(번호)로 둔다.
        // 키를 주면 새 로그가 맨 아래에 붙을 때 목록이 보던 로그를 붙잡아, 맨 아래를 보고 있어도 새 로그가 가려진다.
        items(items = entries.asReversed()) { entry -> LogRow(entry) }
    }
}

/**
 * 로그 한 건. 시각·곳·무게를 한 줄에, 그 아래에 내용을 적는다.
 * 오류의 호출 기록처럼 긴 내용은 [COLLAPSED_LINES] 줄까지만 보이고 누르면 펼친다.
 */
@Composable
private fun LogRow(entry: LogEntry) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    val danger = entry.level != LogLevel.INFO
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .pressScaleClickable(shape = RoundedCornerShape(BudgetTheme.radius.chip), pressedTint = true, onClick = {
                expanded = !expanded
            })
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal, vertical = BudgetTheme.spacing.tightGap),
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap)) {
            Text(
                text = formatLogTime(entry.at, BudgetTime.ZONE),
                style = MaterialTheme.typography.labelSmall,
                color = BudgetTheme.colors.textSecondary,
            )
            Text(
                text = entry.tag,
                style = MaterialTheme.typography.labelSmall,
                color = BudgetTheme.colors.brandText,
            )
            if (danger) {
                Text(
                    text = entry.level.mark.trim(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = BudgetTheme.colors.danger,
                )
            }
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (entry.level == LogLevel.ERROR) BudgetTheme.colors.danger else BudgetTheme.colors.textPrimary,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 로그 전체를 클립보드에 넣는다. 결제 알림 내용이 들어 있어 클립보드 미리보기에는 가려 달라고 표시한다.
 * 안드로이드 13 부터는 시스템이 복사했다고 알려 주므로 그 아래에서만 직접 알린다.
 */
fun copyLog(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(CLIP_LABEL, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    val copied = runCatching { clipboard.setPrimaryClip(clip) }.isSuccess
    when {
        !copied -> Toast.makeText(context, "로그를 복사하지 못했어요. 지우기로 줄인 뒤 다시 해 주세요", Toast.LENGTH_LONG).show()
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> Toast.makeText(context, "로그를 복사했어요", Toast.LENGTH_SHORT).show()
    }
}

private const val CLIP_LABEL = "동계부 로그"

/** 접었을 때 보이는 내용 줄 수 */
private const val COLLAPSED_LINES = 6
