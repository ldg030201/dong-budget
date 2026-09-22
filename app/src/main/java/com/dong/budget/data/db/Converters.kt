package com.dong.budget.data.db

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Instant 를 epoch millis 로 저장한다.
 *
 * enum 은 컨버터를 만들지 않는다. Room 2.3.0 부터 enum 과 이름 문자열 사이 변환이 내장이고,
 * 직접 만들면 손으로 쓴 SQL 의 문자열 비교와 어긋날 위험만 생긴다.
 */
class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
