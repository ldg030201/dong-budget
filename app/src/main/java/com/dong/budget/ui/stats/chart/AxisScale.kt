package com.dong.budget.ui.stats.chart

import com.dong.budget.ui.format.formatAmount
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────
// 차트 y축 눈금과 축 글자. 화면과 떼어 둔 순수 함수라 단위 테스트로 확인한다.
// ─────────────────────────────────────────────────────────────────────

/**
 * y축 범위와 눈금 간격. 모두 원 단위이고, [bottom] ≤ 0 ≤ [top] 이며 둘 다 [step] 의 배수다.
 * 그래서 0(기준선)은 늘 눈금 하나와 겹친다.
 */
data class AxisScale(val bottom: Long, val top: Long, val step: Long) {
    /** 격자를 그을 값. [bottom] 부터 [top] 까지 [step] 간격이고 0 을 포함한다. */
    val ticks: List<Long>
        get() = generateSequence(bottom) { it + step }.takeWhile { it <= top }.toList()
}

/**
 * 보기 좋은 y축 눈금을 고른다. 눈금은 1·2·5 에 10의 거듭제곱을 곱한 값 중 하나라서 "3만 7천" 같은 칸이 생기지 않는다.
 *
 *   raw = ceil(큰 쪽 / ticks), mag = 10^⌊log10 raw⌋
 *   step = {1, 2, 5, 10} × mag 중 raw 이상인 첫 값(최소 1,000원)
 *   top = ceil(max / step) × step, bottom = floor(min / step) × step
 *
 * 예: 87,000 → 5만 간격, 10만까지 / 520,000 → 20만 간격, 60만까지 / 800 → 1,000 하나.
 *
 * @param max 그릴 값의 최댓값
 * @param min 그릴 값의 최솟값. 막대처럼 0 아래가 없으면 두지 않는다(0).
 *   누적 선처럼 환불이 많아 0 아래로 내려가는 값이 있을 때만 넘긴다. 간격은 0 에서 먼 쪽을 기준으로 잡는다.
 * @param ticks 0 위(또는 아래) 먼 쪽에 둘 눈금 수의 목표. 간격이 반올림되므로 이보다 적을 수 있다.
 * @return 그릴 범위가 없으면(모든 값이 0, 또는 [max] ≤ 0 이고 [min] 도 0) null.
 *   이때 차트는 기준선만 긋고 "0" 만 적는다.
 */
fun niceAxis(max: Long, min: Long = 0, ticks: Int = 3): AxisScale? {
    require(ticks > 0) { "눈금 수는 1 이상이어야 한다: $ticks" }
    val low = minOf(min, 0)
    if (max <= 0 && low == 0L) return null
    val extent = maxOf(max, -low)
    val raw = ceilDiv(extent, ticks.toLong())
    var magnitude = 1L
    while (magnitude <= raw / 10) magnitude *= 10
    val step =
        maxOf(
            NICE_STEPS.map { it * magnitude }.first { it >= raw },
            MIN_STEP,
        )
    return AxisScale(
        bottom = Math.floorDiv(low, step) * step,
        top = maxOf(ceilDiv(max, step) * step, 0),
        step = step,
    )
}

/**
 * 축과 막대 위에 적는 줄인 금액. 좁은 자리에 들어가게 단위 '원' 을 빼고 만·억으로 줄인다.
 *   0 → "0" / 5,000 → "5,000" / 10,000 → "1만" / 15,000 → "1.5만" / 1,500,000 → "150만"
 *   150,000,000 → "1.5억" / −10,000 → "-1만"
 * 만 단위와 억 단위는 두 자리까지 소수 한 자리를 붙이고(1.5만, 12.3억), 세 자리부터는 정수로 적는다(150만).
 * 적지 못한 아래 자리는 버린다(내림). 반올림하면 전체 금액보다 커 보일 수 있어서(2,076,048 → 208만) 옆의 전체 금액과 어긋나 보인다.
 * 문장 안의 금액은 formatCompactWon 을, 화면 읽기는 늘 전체 금액을 쓴다.
 */
fun formatAxisWon(amount: Long): String {
    val size = abs(amount)
    val body =
        when {
            size < MAN -> formatAmount(size)
            size < EOK -> scaled(size, MAN, "만")
            else -> scaled(size, EOK, "억")
        }
    return if (amount < 0) "-$body" else body
}

/** [unit] 단위로 줄인다. 두 자리까지는 소수 한 자리(0 이면 뗀다), 세 자리부터는 정수로 적는다. 아래 자리는 버린다. */
private fun scaled(size: Long, unit: Long, suffix: String): String {
    val tenths = size * 10 / unit
    if (tenths >= DECIMAL_LIMIT) return "${formatAmount(size / unit)}$suffix"
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0L) "$whole$suffix" else "$whole.$fraction$suffix"
}

private fun ceilDiv(value: Long, divisor: Long): Long = -Math.floorDiv(-value, divisor)

private val NICE_STEPS = listOf(1L, 2L, 5L, 10L)

/** 눈금 간격의 하한. 원 단위보다 잘게 쪼갠 축은 쓸모가 없다. */
private const val MIN_STEP = 1_000L
private const val MAN = 10_000L
private const val EOK = 100_000_000L

/** 소수 한 자리를 붙이는 한계(10 분의 1 단위로 1,000 = 100만/100억) */
private const val DECIMAL_LIMIT = 1_000L
