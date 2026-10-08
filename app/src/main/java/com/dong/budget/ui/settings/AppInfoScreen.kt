package com.dong.budget.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dong.budget.ui.components.BudgetSmallButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.theme.BudgetTheme

/**
 * 앱 정보. 지금 버전과 새 버전 확인, 그 아래 업데이트 칸을 둔다.
 * 설정의 '앱 정보' 줄, 홈의 새 버전 알림 줄, 패치노트의 업데이트 버튼이 이 화면을 연다.
 * 업데이트 칸은 배포처마다 다르다(배포처 소스의 AppInfoRoute 가 채운다).
 *
 * @param canCheckUpdate '업데이트 확인' 을 누를 수 있는지. 확인 중이거나 받는 중에는 막는다.
 * @param update 버전 줄 아래의 업데이트 칸
 */
@Composable
fun AppInfoScreen(
    currentVersion: String,
    canCheckUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    update: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(onNavigationClick = onBack, title = "앱 정보")

            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = BudgetTheme.spacing.screenHorizontal),
            ) {
                SettingsGroup("버전") {
                    // 새 버전 확인은 어느 상태에서든 버전 옆 버튼으로 다시 할 수 있다.
                    // 새 버전을 보고 있는 사이 더 새 버전이 나와도 눌러서 바로 최신으로 바꿔 볼 수 있게 하기 위함이다.
                    // 확인 중이거나 내려받는 중에는 막는다. 내려받던 화면이 확인 결과로 덮이면 진행 상황이 사라진다.
                    Row(
                        modifier = Modifier.heightIn(min = BudgetTheme.size.minTouchTarget),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "현재 버전 $currentVersion",
                            style = MaterialTheme.typography.bodyLarge,
                            color = BudgetTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        BudgetSmallButton(
                            text = "업데이트 확인",
                            onClick = onCheckUpdate,
                            enabled = canCheckUpdate,
                            // 회색 판 위라 판과 같은 회색 바탕은 안 보인다. 판 위에 떠 있는 색으로 띄운다.
                            container = BudgetTheme.colors.raised,
                        )
                    }

                    update()
                }

                Spacer(Modifier.height(BudgetTheme.spacing.sectionGap))
            }
        }
    }
}

/** 업데이트 칸의 한 줄 상태 글 */
@Composable
internal fun UpdateStatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = BudgetTheme.colors.textSecondary,
    )
}
