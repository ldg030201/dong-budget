package com.dong.budget.ui.editor

import com.dong.budget.data.capture.CaptureKind
import com.dong.budget.data.capture.CapturedPayment
import com.dong.budget.data.settings.AutoOption
import com.dong.budget.data.settings.AutoSettings
import com.dong.budget.navigation.EditorPrefill
import com.dong.budget.navigation.PrefillSource

/**
 * 알림에서 읽은 결제(입금)를 등록창에 채울 값으로 바꾼다. 금액·가게(입금이면 보낸 사람)·시각은 늘 채우고, 나머지는 자동 기능 스위치를 따른다.
 * - 카드(통장) 이름: '카드·통장 이름으로 미리 고르기' 가 켜져 있을 때만 넘긴다. 없는 것을 새로 만들지는 '없는 카드·통장은 새로 추가하기' 가 정한다.
 *   입금이면 들어온 곳에서 찾고, 없으면 통장(계좌)으로 만든다.
 * - 할부: '할부는 메모에 적기' 가 켜져 있으면 메모로 남긴다. 입금은 할부가 없다.
 * - 분류: '같은 가게면 지난 분류 고르기' 가 켜져 있으면 등록창이 찾아 고른다. 입금이면 같은 사람에게 받은 지난 수입의 분류다.
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
    // 입금이면 수입으로 연다
    source = kind.prefillSource,
)

/** 이 종류의 알림으로 채워 연 등록창의 출처. 이미 등록했을 때의 안내도 이것으로 고른다([alreadyRegisteredMessage]). */
internal val CaptureKind.prefillSource: PrefillSource
    get() = when (this) {
        CaptureKind.PAYMENT -> PrefillSource.PAYMENT_ALERT
        CaptureKind.DEPOSIT -> PrefillSource.DEPOSIT_ALERT
    }
