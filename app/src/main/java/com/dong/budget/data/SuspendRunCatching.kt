package com.dong.budget.data

import kotlin.coroutines.cancellation.CancellationException

/** 코루틴 취소는 잡지 않고 다시 던지는 runCatching. runCatching 은 CancellationException 까지 실패로 바꿔 취소를 삼킨다. */
internal inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
