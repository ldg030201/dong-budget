package com.dong.budget.data.db

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 이 앱의 시간 기준.
 *
 * 기기 타임존(ZoneId.systemDefault)을 쓰지 않고 서울로 못박는다.
 * 기기 타임존을 따르면 해외에 나갔을 때 이미 입력해둔 거래의 소속 월이 바뀐다.
 */
object BudgetTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    /** yyyyMMdd 정수. 월별 조회와 일자 그룹핑에 쓴다. */
    fun toDateKey(instant: Instant): Int {
        // LocalDate.ofInstant 는 API 34 부터라 minSdk 31 에서 쓸 수 없다.
        val date = instant.atZone(ZONE).toLocalDate()
        return date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth
    }

    fun toLocalDate(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()

    /**
     * 한 달의 시작과 끝을 Instant 로 돌려준다.
     *
     * 목록 조회는 이 범위로 occurredAt 을 거른다.
     * 정렬 기준이 occurredAt 인데 필터를 occurredDate 로 걸면 두 컬럼이 달라서
     * 인덱스가 범위 스캔에 쓰이지 못한다.
     */
    fun monthRange(month: YearMonth): Pair<Instant, Instant> {
        val start = month.atDay(1).atStartOfDay(ZONE).toInstant()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant()
        return start to end
    }

    fun currentMonth(): YearMonth = YearMonth.now(ZONE)
}
