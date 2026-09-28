package com.dong.budget.ui.editor

import com.dong.budget.data.capture.CapturedPayment
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.AutoSettings
import com.dong.budget.navigation.EditorPrefill

/**
 * 알림에서 읽은 결제를 등록창에 채울 값으로 바꾼다. 금액·가게·시각은 늘 채우고, 나머지는 자동 기능 스위치를 따른다.
 * - 카드 이름: '카드 이름으로 결제수단 고르기' 가 켜져 있을 때만 넘긴다. 없는 카드를 새로 만들지는 '없는 카드는 새로 추가하기' 가 정한다.
 * - 할부: '할부는 메모에 적기' 가 켜져 있으면 메모로 남긴다.
 * - 분류: '같은 가게면 지난 분류 고르기' 가 켜져 있으면 등록창이 찾아 고른다.
 */
internal fun CapturedPayment.toPrefill(auto: AutoSettings) = EditorPrefill(
    amount = amount,
    merchant = merchant,
    paymentName = paymentName.takeIf { auto[AutoOption.FILL_PAYMENT] },
    memo = installmentLabel.takeIf { auto[AutoOption.FILL_INSTALLMENT] },
    occurredAtMillis = occurredAtMillis,
    dedupKey = dedupKey,
    guessCategory = auto[AutoOption.FILL_CATEGORY],
    addMissingCard = auto[AutoOption.FILL_NEW_CARD],
)
