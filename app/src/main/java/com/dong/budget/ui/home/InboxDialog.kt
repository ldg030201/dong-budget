package com.dong.budget.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.dong.budget.data.capture.CaptureStore
import com.dong.budget.ui.components.BudgetIconButton
import com.dong.budget.ui.components.BudgetTextButton
import com.dong.budget.ui.components.DialogButton
import com.dong.budget.ui.format.formatAmount
import com.dong.budget.ui.format.formatNoticeTime
import com.dong.budget.ui.theme.BudgetTheme
import com.dong.budget.ui.theme.pressScaleClickable
import java.time.Instant
import java.time.LocalDate

/** 홈 오른쪽 위의 종. 새 알림이 있으면 오른쪽 위에 빨간 점을 찍는다. */
@Composable
internal fun InboxButton(hasNew: Boolean, onClick: () -> Unit) {
    Box {
        BudgetIconButton(
            icon = Icons.Outlined.Notifications,
            contentDescription = if (hasNew) "알림, 새 알림 있음" else "알림",
            onClick = onClick,
        )
        if (hasNew) {
            // 종 그림 칸의 오른쪽 위 모서리에 맞춘다
            val inset = (BudgetTheme.size.minTouchTarget - BudgetTheme.size.icon) / 2
            NoticeDot(Modifier.align(Alignment.TopEnd).padding(top = inset, end = inset))
        }
    }
}

/** 새 알림 표시 점. 종과 목록의 새 알림 줄이 같이 쓴다. 화면 읽기는 따로 알려주므로 점은 읽지 않는다. */
@Composable
private fun NoticeDot(modifier: Modifier = Modifier) {
    Box(modifier.size(BudgetTheme.size.noticeDot).background(BudgetTheme.colors.danger, CircleShape))
}

/**
 * 종을 누르면 뜨는 알림 목록. 지금은 결제 등록 알림만 모은다.
 *
 * 아직 눌러 보지 않은 알림은 연한 남색 바탕에 오른쪽 빨간 점, 눌러 본 알림은 바탕도 점도 없이 둔다.
 * 한 줄을 누르면 알림창의 알림을 누른 것과 똑같이 결제 내용이 채워진 등록창이 열린다.
 * '모두 읽음' 은 새 알림 표시를 모두 없애고 알림창에 남은 묻는 알림도 치운다(PaymentCapture.markAllRead).
 *
 * @param today 알림이 온 때를 '오늘'·'어제' 로 적을 기준
 * @param onOpen 누른 결제의 열쇠를 넘긴다. 창은 부르는 쪽이 닫는다.
 * @param onMarkAllRead 목록에 보이는 결제의 열쇠를 넘긴다. 누르는 사이 새로 온 결제까지 쓸어 가지 않게 하기 위함이다.
 */
@Composable
internal fun InboxDialog(
    items: List<InboxItem>,
    today: LocalDate,
    onOpen: (dedupKey: String) -> Unit,
    onMarkAllRead: (dedupKeys: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(BudgetTheme.radius.sheet),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = BudgetTheme.elevation.none,
        ) {
            Column(modifier = Modifier.padding(top = BudgetTheme.spacing.sectionPadding, bottom = BudgetTheme.spacing.sectionPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        start = BudgetTheme.spacing.screenHorizontal,
                        end = BudgetTheme.spacing.itemGap,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "알림",
                            style = MaterialTheme.typography.titleMedium,
                            color = BudgetTheme.colors.textPrimary,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = "최근 ${CaptureStore.RETENTION_DAYS}일 동안 온 결제 알림이에요",
                            style = MaterialTheme.typography.bodySmall,
                            color = BudgetTheme.colors.textSecondary,
                        )
                    }
                    BudgetTextButton(
                        text = "모두 읽음",
                        onClick = { onMarkAllRead(items.map { it.payment.dedupKey }) },
                        enabled = items.any { it.isNew },
                    )
                }
                Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
                if (items.isEmpty()) {
                    Text(
                        text = "아직 온 알림이 없어요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BudgetTheme.colors.textSecondary,
                        modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = BudgetTheme.spacing.sectionGap),
                    )
                } else {
                    val listState = rememberLazyListState()
                    // 줄마다 열쇠를 주면 목록은 보던 줄을 따라간다. 창을 연 채 새 결제가 맨 위에 들어오면 새 줄이
                    // 창 위쪽 밖에 숨어, 못 본 채 '모두 읽음' 에 함께 쓸려 갈 수 있다. 맨 위를 보던 중이면 맨 위에 머문다.
                    remember(items.first().payment.dedupKey) {
                        // 스크롤할 때마다 이 자리가 다시 그려지지 않게 위치를 읽은 것으로 치지 않는다
                        Snapshot.withoutReadObservation {
                            if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                                listState.requestScrollToItem(0)
                            }
                        }
                    }
                    // 알림이 많아 창에 다 안 들어가면 목록만 스크롤한다. 닫기 버튼은 항상 아래에 보이게 둔다.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(horizontal = BudgetTheme.spacing.inlineGap),
                        verticalArrangement = Arrangement.spacedBy(BudgetTheme.spacing.tightGap),
                    ) {
                        items(items = items, key = { it.payment.dedupKey }) { item ->
                            InboxRow(item = item, today = today, onClick = { onOpen(item.payment.dedupKey) })
                        }
                    }
                }
                Spacer(Modifier.height(BudgetTheme.spacing.itemGap))
                DialogButton(
                    label = "닫기",
                    container = BudgetTheme.colors.sectionBackground,
                    content = BudgetTheme.colors.textPrimary,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = BudgetTheme.spacing.screenHorizontal),
                )
            }
        }
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
            .padding(horizontal = BudgetTheme.spacing.itemGap, vertical = BudgetTheme.spacing.itemGap),
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
