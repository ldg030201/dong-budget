package com.dong.budget.data.card

import com.dong.budget.data.MAX_AMOUNT_DIGITS
import com.dong.budget.data.db.PaymentMethodEntity

// ─────────────────────────────────────────────────────────────────────
// 카드 실적 칸 읽고 쓰기.
//
// 결제수단 줄의 performanceTiers 는 실적 구간 금액(원)을 오름차순으로 쉼표로 이은 글이다("300000,700000").
// 저장소·백업·화면이 모두 여기를 거쳐 같은 정리(망가진 조각 버리기, 중복 빼기, 오름차순, 최대 개수)를 받게 한다.
// ─────────────────────────────────────────────────────────────────────

/** 카드 하나에 적을 수 있는 실적 구간 수 */
const val MAX_PERFORMANCE_TIERS = 5

/** 실적 시작일(매달 N일)의 상한. 그 달에 없는 날이면 말일부터 센다. */
const val MAX_PERFORMANCE_START_DAY = 31

/** 구간 금액 상한(12자리, 999,999,999,999원). 등록 화면이 받는 가장 큰 금액과 같다. */
val MAX_PERFORMANCE_AMOUNT: Long = "9".repeat(MAX_AMOUNT_DIGITS).toLong()

/** 구간 금액을 정리한다: 1원~상한 밖은 버리고, 중복을 빼고, 오름차순으로, 앞에서 [MAX_PERFORMANCE_TIERS] 개까지. */
fun normalizePerformanceTiers(tiers: List<Long>): List<Long> =
    tiers.filter { it in 1..MAX_PERFORMANCE_AMOUNT }.distinct().sorted().take(MAX_PERFORMANCE_TIERS)

/** 저장된 글을 구간 금액 목록으로. 숫자가 아닌 조각은 버리고 [normalizePerformanceTiers] 로 정리한다. 안 적었으면 빈 목록. */
fun parsePerformanceTiers(raw: String?): List<Long> =
    normalizePerformanceTiers(raw?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty())

/** 구간 금액 목록을 저장할 글로. 정리하고 남은 것이 없으면 null(실적을 안 적은 것)이다. */
fun encodePerformanceTiers(tiers: List<Long>): String? = normalizePerformanceTiers(tiers).takeIf { it.isNotEmpty() }?.joinToString(",")

/** 실적 시작일을 1~[MAX_PERFORMANCE_START_DAY] 로 맞춘다. */
fun normalizePerformanceStartDay(day: Int): Int = day.coerceIn(1, MAX_PERFORMANCE_START_DAY)

/** 이 결제수단의 실적 구간 금액(오름차순). 안 적었으면 빈 목록이다. */
val PaymentMethodEntity.performanceTierList: List<Long> get() = parsePerformanceTiers(performanceTiers)
