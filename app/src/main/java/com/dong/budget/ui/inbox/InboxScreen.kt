package com.dong.budget.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.dong.budget.data.capture.CaptureStore
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.BudgetTopAppBar
import com.dong.budget.ui.components.NoticeDot
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatNoticeTime
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable
import java.time.Instant
import java.time.LocalDate

/**
 * 알림 화면. 홈 오른쪽 위 종으로 들어온다. 지금은 결제 등록 알림만 모은다.
 *
 * 아직 눌러 보지 않은 알림은 연한 남색 바탕에 오른쪽 빨간 점, 눌러 본 알림은 바탕도 점도 없이 둔다.
 * 한 줄을 누르면 알림창의 알림을 누른 것과 똑같이 결제 내용이 채워진 등록창이 열린다.
 * '모두 읽음' 은 새 알림 표시를 모두 없애고 알림창에 남은 묻는 알림도 치운다(PaymentCapture.markAllRead).
 *
 * @param items 최근 것부터. 아직 불러오기 전이면 null
 * @param today 알림이 온 때를 '오늘'·'어제' 로 적을 기준
 * @param onOpen 누른 결제의 열쇠
 * @param onMarkAllRead 화면에 있는 결제의 열쇠를 넘긴다. 누르는 사이 새로 온 결제까지 쓸어 가지 않게 하기 위함이다.
 */
@Composable
fun InboxScreen(
    items: List<InboxItem>?,
    today: LocalDate,
    onBack: () -> Unit,
    onOpen: (dedupKey: String) -> Unit,
    onMarkAllRead: (dedupKeys: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetTopAppBar(
                onNavigationClick = onBack,
                title = "알림",
                actions = {
                    BudgetTextButton(
                        text = "모두 읽음",
                        onClick = { onMarkAllRead(items.orEmpty().map { it.payment.dedupKey }) },
                        enabled = items.orEmpty().any { it.isNew },
                    )
                },
            )
            when {
                // 불러오는 동안은 비워 둔다. 빈 목록 안내가 잠깐 보였다 사라지지 않게 한다.
                items == null -> Unit

                items.isEmpty() -> EmptyInbox()

                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        // 줄 바탕이 화면 끝에 붙지 않게 조금 띄운다. 글자는 줄 안쪽 여백까지 더해 화면 좌우 여백에 맞는다.
                        contentPadding =
                        PaddingValues(
                            start = BudgetTheme.spacing.inlineGap,
                            end = BudgetTheme.spacing.inlineGap,
                            bottom = BudgetTheme.spacing.sectionGap,
                        ),
                        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
                    ) {
                        // 안내는 목록의 첫 줄로 둔다. 목록은 맨 위 줄을 기준으로 자리를 지키므로,
                        // 맨 위를 보던 중에 새 결제가 들어오면 이 줄 바로 아래에 보인다(위쪽 밖에 숨지 않는다).
                        item(key = CAPTION_KEY) {
                            Text(
                                text = "최근 ${CaptureStore.RETENTION_DAYS}일 동안 온 결제 알림이에요",
                                style = MaterialTheme.typography.bodySmall,
                                color = BudgetTheme.colors.textSecondary,
                                modifier = Modifier.padding(
                                    horizontal = BudgetTheme.spacing.itemGap,
                                ).padding(bottom = BudgetTheme.spacing.inlineGap),
                            )
                        }
                        items(items = items, key = { it.payment.dedupKey }) { item ->
                            InboxRow(item = item, today = today, onClick = { onOpen(item.payment.dedupKey) })
                        }
                    }
            }
        }
    }
}

private const val CAPTION_KEY = "caption"

@Composable
private fun EmptyInbox() {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BudgetTheme.spacing.screenHorizontal, vertical = BudgetTheme.spacing.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.inlineGap),
    ) {
        Text(
            text = "아직 온 알림이 없어요",
            style = MaterialTheme.typography.titleMedium,
            color = BudgetTheme.colors.textPrimary,
        )
        Text(
            text = "결제 등록 알림이 오면 여기에 ${CaptureStore.RETENTION_DAYS}일 동안 모여요",
            style = MaterialTheme.typography.bodyMedium,
            color = BudgetTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 알림 한 줄. 금액과 가게, 그 아래 카드·할부·온 때를 적는다. 화면 읽기는 한 번에 읽는다. */
@Composable
private fun InboxRow(item: InboxItem, today: LocalDate, onClick: () -> Unit) {
    val payment = item.payment
    val shape = RoundedCornerShape(BudgetTheme.radius.control)
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .pressScaleClickable(shape = shape, onClick = onClick)
            .background(if (item.isNew) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape)
            // 바탕색만으로는 화면 읽기가 새 알림인지 알 수 없다
            .semantics { if (item.isNew) stateDescription = "새 알림" }
            .padding(horizontal = BudgetTheme.spacing.itemGap, vertical = BudgetTheme.spacing.listItemVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOfNotNull("${formatAmount(payment.amount)}원", payment.merchant.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = BudgetTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                listOfNotNull(
                    payment.paymentName,
                    payment.installmentLabel,
                    formatNoticeTime(Instant.ofEpochMilli(payment.occurredAtMillis), today),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = BudgetTheme.colors.textSecondary,
                modifier = Modifier.padding(top = BudgetTheme.spacing.tightGap),
            )
        }
        // 새 알림과 등록함은 함께 붙지 않는다(InboxItem). 둘 다 줄 오른쪽 끝을 쓴다.
        if (item.isNew) {
            Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
            // 연한 바탕은 흰 바탕과 명암 차이가 작아(라이트 1.22:1) 색만으로는 새 알림이 잘 안 보인다
            NoticeDot()
        }
        if (item.registered) {
            Spacer(Modifier.width(BudgetTheme.spacing.inlineGap))
            Text(
                text = "등록함",
                style = MaterialTheme.typography.labelMedium,
                color = BudgetTheme.colors.textSecondary,
            )
        }
    }
}
