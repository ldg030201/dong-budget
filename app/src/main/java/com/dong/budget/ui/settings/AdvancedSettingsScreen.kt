package com.dong.budget.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.AutoSettings
import com.dong.budget.ui.components.ActionRow
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.ConfirmDialog
import com.dong.budget.ui.components.HintText
import com.dong.budget.ui.components.SwitchRow
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 고급 설정. 앱이 알아서 골라 주거나 채워 주는 기능의 스위치를 묶음([AutoGroup])별로 모으고, 맨 아래에 초기화 두 가지를 둔다.
 * 초기화는 실수로 누르지 않게 설정 첫 화면이 아니라 여기 맨 아래에 두고, 누르면 한 번 더 묻는다.
 *
 * @param prompt 묻는 중인 초기화. 없으면 null
 * @param busy 초기화하는 중. 초기화 줄을 막는다.
 */
@Composable
fun AdvancedSettingsScreen(
    autoSettings: AutoSettings,
    onAutoChange: (AutoOption, Boolean) -> Unit,
    prompt: ResetPrompt?,
    busy: Boolean,
    onResetSettings: () -> Unit,
    onResetData: () -> Unit,
    onConfirmReset: () -> Unit,
    onDismissReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(onNavigationClick = onBack, title = "고급 설정")

            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            ) {
                HintText("앱이 알아서 골라 주거나 채워 주는 기능이에요. 처음에는 모두 켜져 있어요.")

                AutoGroup.entries.forEach { group ->
                    SettingsGroup(group.label) {
                        group.options.forEach { option -> AutoSwitch(option = option, settings = autoSettings, onChange = onAutoChange) }
                    }
                }

                SettingsGroup("초기화") {
                    ActionRow(
                        title = "설정 초기화",
                        description = "화면 테마·하단 메뉴·자동 기능을 처음대로 돌려요. 거래와 월급 설정은 그대로예요",
                        onClick = onResetSettings,
                        enabled = !busy,
                    )
                    ActionRow(
                        title = "데이터 초기화",
                        description = "거래·분류·결제수단·월급 설정·알림 목록을 모두 지우고 처음 설치한 상태로 돌려요",
                        onClick = onResetData,
                        danger = true,
                        enabled = !busy,
                    )
                }

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            }
        }
    }

    when (prompt) {
        null -> Unit

        ResetPrompt.Settings ->
            ConfirmDialog(
                title = "설정을 처음대로 돌릴까요?",
                message = RESET_SETTINGS_MESSAGE,
                confirmLabel = "되돌리기",
                onConfirm = onConfirmReset,
                onDismiss = onDismissReset,
                destructive = false,
            )

        is ResetPrompt.Data ->
            ConfirmDialog(
                title = "데이터를 모두 지울까요?",
                message = resetDataMessage(prompt.transactionCount, prompt.autoBackup),
                confirmLabel = "모두 지우기",
                onConfirm = onConfirmReset,
                onDismiss = onDismissReset,
            )
    }
}

/**
 * 자동 기능 스위치 한 줄. 기대는 스위치(parent)가 꺼져 있으면 흐리게 막는다. 스위치에는 사용자가 고른 값을 그대로 둬서,
 * 위 스위치를 다시 켜면 전에 고른 대로 돌아간다. 기대는 줄은 조금 들여 써서 어느 스위치 밑인지 보이게 한다.
 */
@Composable
private fun AutoSwitch(option: AutoOption, settings: AutoSettings, onChange: (AutoOption, Boolean) -> Unit) {
    val parent = option.parent
    SwitchRow(
        title = option.title,
        description = option.description,
        checked = settings.chosen(option),
        onCheckedChange = { on -> onChange(option, on) },
        enabled = parent == null || settings[parent],
        modifier = if (parent != null) Modifier.padding(start = BudgetTheme.spacing.itemGap) else Modifier,
    )
}
