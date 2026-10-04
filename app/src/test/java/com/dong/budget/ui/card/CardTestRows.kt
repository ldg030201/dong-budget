package com.dong.budget.ui.card

import com.dong.budget.data.db.CardSpendRow
import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.data.db.TransactionType
import kotlinx.coroutines.flow.StateFlow
import java.lang.reflect.Proxy

/** 탭 조회(observeCardSpending)처럼 결제수단으로 쓴 지출·환불만 골라 쓴 돈을 세는 칸으로 */
internal fun List<TransactionListItem>.cardSpending(): List<CardSpendRow> = mapNotNull { item ->
    val method = item.paymentMethodId ?: return@mapNotNull null
    if (item.type != TransactionType.EXPENSE && item.type != TransactionType.REFUND) return@mapNotNull null
    CardSpendRow(method, item.type, item.amount, item.occurredAt)
}

/** 결제수단 [paymentMethodId] 의 쓴 돈 목록 */
internal fun List<TransactionListItem>.spendsOf(paymentMethodId: Long): List<Spend> =
    cardSpending().filter { it.paymentMethodId == paymentMethodId }.map { it.toSpend() }

/** 구독하지 않고 값만 들여다보며 [done] 이 될 때까지 기다린다(그리는 화면이 없을 때와 같다) */
internal fun <T> StateFlow<T>.await(done: (T) -> Boolean): T {
    val deadline = System.currentTimeMillis() + 5_000
    while (!done(value)) {
        check(System.currentTimeMillis() < deadline) { "기다린 값이 오지 않았다: $value" }
        Thread.sleep(5)
    }
    return value
}

/** [answer] 가 메서드 이름과 인자로 돌려주는 값만 쓰는 가짜 DAO. 쓰지 않을 메서드를 부르면 실패한다. */
internal fun <T> fakeDao(type: Class<T>, answer: (String, List<Any?>) -> Any?): T = type.cast(
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> type.simpleName
            else -> answer(method.name, args?.toList().orEmpty()) ?: error("${type.simpleName}.${method.name} 는 이 테스트에서 쓰지 않는다")
        }
    },
)
