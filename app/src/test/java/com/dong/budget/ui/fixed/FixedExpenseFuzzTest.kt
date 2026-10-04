package com.dong.budget.ui.fixed

import com.dong.budget.data.db.TransactionListItem
import com.dong.budget.testing.tx
import com.dong.budget.ui.home.localDate
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * 아무렇게나 낸 가게(날마다 · 요일마다 · 며칠마다 · 한 달에 몇 번 · 몇 달마다 · 하루 차이 두 청구 · 두 청구 가운데 하나를 해지,
 * 금액이 바뀌고 밀리고 빠지고 겹친다)를 수천 개 만들어
 * 고정지출 계산이 어떤 기록에도 예외를 던지지 않는지 본다(리뷰 검증: 자주 내는 가게에서 금액이 바뀌면 짝짓기가 죽었다).
 * 읽는 범위는 화면 모델과 같다([fixedHistoryStart] ~ [fixedHistoryEnd]). 씨앗을 박아 두어 늘 같은 기록을 본다.
 */
class FixedExpenseFuzzTest {
    @Test
    fun `아무렇게나 낸 가게 수천 개를 이번 달과 지난 달로 봐도 계산이 예외를 던지지 않는다`() {
        val random = Random(SEED)
        val failures = mutableListOf<String>()
        var checked = 0
        repeat(PATTERNS) { n ->
            val kind = n % KINDS
            val rows = pattern(random, kind, "가게$n")
            val first = rows.minOf { it.localDate() }
            val last = rows.maxOf { it.localDate() }
            val today = first.plusDays(random.nextLong(-10, daysBetween(first, last) + 90L))
            val thisMonth = YearMonth.from(today)
            for (month in listOf(thisMonth, thisMonth.minusMonths(1), thisMonth.minusMonths(random.nextLong(2, 14)))) {
                checked++
                try {
                    val board = buildFixedExpenses(month, today, read(rows, month, today))
                    (board.due + board.paid + board.notThisMonth + board.stopped).forEach { item ->
                        check(item.amount >= 0 && item.paidAmount >= 0)
                        check(item.droppedDays.all { it in item.usualDays })
                    }
                } catch (e: Exception) {
                    failures += "가게$n(종류 $kind) $month@$today: $e · ${rows.joinToString { "${it.localDate()}=${it.amount}" }}"
                }
            }
        }
        assertTrue("${failures.size}/$checked 실패\n" + failures.take(SHOWN).joinToString("\n"), failures.isEmpty())
    }

    /** 화면 모델처럼 [month] 의 읽는 범위만, 조회처럼 최신순으로 */
    private fun read(rows: List<TransactionListItem>, month: YearMonth, today: LocalDate) = rows
        .filter { YearMonth.from(it.localDate()) in fixedHistoryStart(month)..fixedHistoryEnd(month, today) }
        .sortedWith(compareByDescending<TransactionListItem> { it.occurredAt }.thenByDescending { it.id })

    private fun pattern(random: Random, kind: Int, name: String): List<TransactionListItem> {
        val start = LocalDate.of(2025, 1, 1).plusDays(random.nextLong(0, 1500))
        val end = start.plusDays(random.nextLong(1, 800))
        val base = AMOUNTS[random.nextInt(AMOUNTS.size)]
        val changeAt = start.plusDays(random.nextLong(0, daysBetween(start, end) + 1L))
        val ratio = CHANGES[random.nextInt(CHANGES.size)]
        // 결제일마다 그 청구의 금액(바뀌기 전)
        val claims =
            when (kind) {
                0 -> List(random.nextInt(1, 80)) { start.plusDays(random.nextLong(0, daysBetween(start, end) + 1L)) }.map { it to base }
                1 -> weekdays(random, start, end).map { it to base }
                2 -> stepped(random.nextInt(1, 12), start, end).map { it to base }
                3 -> monthly(random, start, end, List(random.nextInt(1, 4)) { random.nextInt(1, LAST_DAY + 1) }).map { it to base }
                4 -> periodic(random, start, end).map { it to base }
                5 -> nextDays(random, start, end).map { it to base }
                else -> cancelled(random, start, end, base)
            }.ifEmpty { listOf(start to base) }
        return claims.flatMap { (date, claim) ->
            val amount = if (date < changeAt) claim else claim * ratio / 10
            val odd = if (random.nextInt(ODD_ONE_IN) == 0) amount * ODD_RATIOS[random.nextInt(ODD_RATIOS.size)] / 10 else amount
            List(if (random.nextInt(TWICE_ONE_IN) == 0) 2 else 1) { tx(date.toString(), maxOf(1, odd), categoryId = 4, merchant = name) }
        }
    }

    /** 고른 요일마다(평일 돌봄 · 주 3회 PT 같은 것) */
    private fun weekdays(random: Random, start: LocalDate, end: LocalDate): List<LocalDate> {
        val days = DayOfWeek.entries.filter { random.nextInt(3) > 0 }.toSet().ifEmpty { setOf(DayOfWeek.MONDAY) }
        return generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.filter { it.dayOfWeek in days }.toList()
    }

    /** [step] 일마다 */
    private fun stepped(step: Int, start: LocalDate, end: LocalDate): List<LocalDate> =
        generateSequence(start) { it.plusDays(step.toLong()) }.takeWhile { it <= end }.toList()

    /** 달마다 평소 날 [days] 에, 가끔 쉬는 날로 밀리고 · 빠지고 · 며칠 일찍이나 늦게 */
    private fun monthly(random: Random, start: LocalDate, end: LocalDate, days: List<Int>): List<LocalDate> {
        val pushed = random.nextBoolean()
        return months(start, end, 1).flatMap { month ->
            days.mapNotNull { day ->
                val usual = usualDateOf(month, day).let { if (pushed) KoreanCalendar.nextBusinessDay(it) else it }
                when (random.nextInt(JITTER_ONE_IN)) {
                    0 -> null
                    1 -> usual.plusDays(random.nextLong(-6, 16))
                    else -> usual
                }
            }
        }
    }

    /** 하루 차이로 따로 나가는 두 청구(5일 · 6일, 30일 · 말일). 흔들림은 [monthly] 와 같다. */
    private fun nextDays(random: Random, start: LocalDate, end: LocalDate): List<LocalDate> =
        random.nextInt(1, LAST_DAY).let { monthly(random, start, end, listOf(it, it + 1)) }

    /**
     * 두 청구(같은 날 두 회선 · 하루 차이 · 날이 따로인 두 차례, 금액은 따로) 가운데 하나를 어느 날부터 해지한 것. 앞 청구를 해지할 때도
     * 뒤 청구를 해지할 때도 있다. 흔들림은 [monthly] 와 같다.
     */
    private fun cancelled(random: Random, start: LocalDate, end: LocalDate, base: Long): List<Pair<LocalDate, Long>> {
        val first = random.nextInt(1, LAST_DAY + 1)
        val second = minOf(LAST_DAY, first + listOf(0, 1, random.nextInt(2, LAST_DAY))[random.nextInt(3)])
        val other = AMOUNTS[random.nextInt(AMOUNTS.size)]
        val cancelAt = start.plusDays(random.nextLong(0, daysBetween(start, end) + 1L))
        val dropFirst = random.nextBoolean()
        val firsts = monthly(random, start, end, listOf(first)).filter { !dropFirst || it < cancelAt }
        val seconds = monthly(random, start, end, listOf(second)).filter { dropFirst || it < cancelAt }
        return firsts.map { it to base } + seconds.map { it to other }
    }

    /** 몇 달마다 · 매년, 낼 날에서 앞뒤로 흔들리게 */
    private fun periodic(random: Random, start: LocalDate, end: LocalDate): List<LocalDate> {
        val step = listOf(2, 3, 4, 6, 12)[random.nextInt(5)].toLong()
        val day = random.nextInt(1, LAST_DAY + 1)
        return months(start, end, step).map { usualDateOf(it, day).plusDays(random.nextLong(-25, 26)) }
    }

    private fun months(start: LocalDate, end: LocalDate, step: Long): List<YearMonth> =
        generateSequence(YearMonth.from(start)) { it.plusMonths(step) }.takeWhile { it <= YearMonth.from(end) }.toList()
}

private const val SEED = 20_261_004
private const val PATTERNS = 3_000
private const val KINDS = 7
private const val SHOWN = 10
private const val ODD_ONE_IN = 15
private const val TWICE_ONE_IN = 25
private const val JITTER_ONE_IN = 6
private val AMOUNTS = listOf(3_000L, 9_900L, 17_000L, 55_000L, 500_000L)

/** 금액이 바뀐 뒤의 배수(× 1/10). 그대로 · 조금 · 세 배 넘게 오르고 내린다. */
private val CHANGES = listOf(10L, 11L, 35L, 2L, 40L)

/** 가끔 섞이는 동떨어진 금액의 배수(× 1/10) */
private val ODD_RATIOS = listOf(30L, 100L, 1L, 25L)
