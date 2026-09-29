package com.dong.budget.data.salary

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * 실시간 월급 설정 저장소. 화면 테마·자동 기능(app_settings)과 파일을 나눠서, 설정 초기화가 월급 설정까지 지우지 않게 한다.
 * 설정 한 벌을 JSON 한 줄로 저장한다. 백업도 같은 모양([SalaryRecord])을 쓴다.
 */
class SalaryRepository(private val context: Context) {
    private val key = stringPreferencesKey("salary")

    /** 저장된 설정. 정한 적 없거나 읽을 수 없으면 기본값(금액 0, 아직 정하지 않음)이다. */
    val settings: Flow<SalarySettings> =
        context.salaryDataStore.data
            // 저장 파일이 깨졌을 때 앱이 죽지 않고 기본값으로 시작하게 한다
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences -> preferences[key]?.let(::decode)?.toSettings() ?: SalarySettings() }
            .distinctUntilChanged()

    /** 설정을 저장한다. 월급 설정 화면은 숫자 하나 누를 때마다 부르므로 여기서 로그를 남기지 않는다([describe]). */
    suspend fun save(settings: SalarySettings) {
        context.salaryDataStore.edit { preferences ->
            preferences[key] = json.encodeToString(SalaryRecord.serializer(), settings.toRecord())
        }
    }

    /** 월급 설정을 지운다(데이터 초기화, 복원) */
    suspend fun clear() {
        context.salaryDataStore.edit { preferences -> preferences.clear() }
    }

    /**
     * 로그에 적을 설정 요약. 번 돈이 이상하다는 신고를 받으면 어떤 설정이었는지 보려고 쓴다.
     * 금액은 적지 않는다. 개발자 모드 로그는 사용자가 복사해 남에게 보내는 글이다.
     */
    fun describe(settings: SalarySettings): String = "${settings.basis}${if (settings.usesTakeHome) " · 실수령" else ""} · " +
        "${settings.workStart}~${settings.workEnd}" +
        "${if (settings.skipLunch) " 점심 ${settings.lunchStart}~${settings.lunchEnd} 뺌" else ""} · " +
        "요일 ${settings.workdays.sorted().joinToString(",") { it.value.toString() }} · 월급날 ${settings.payday}일" +
        "${settings.startDate?.let { " · 입사일 $it" } ?: ""} · 알림 ${if (settings.paydayNotice) "켬" else "끔"}"

    private fun decode(text: String): SalaryRecord? = runCatching { json.decodeFromString(SalaryRecord.serializer(), text) }.getOrNull()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private val Context.salaryDataStore by preferencesDataStore(name = "salary")

/**
 * 저장·백업용 월급 설정. 시각은 "09:00", 날짜는 "2026-09-15", 요일은 월=1…일=7 로 적는다.
 * 칸을 늘릴 때는 기본값을 줘서 예전에 저장한 설정도 읽히게 한다.
 * 급여 종류는 enum 이 아니라 글자로 둔다. 모르는 값이 오면 백업 전체가 망가진 것으로 거절되지 않고 월급으로 읽힌다.
 */
@Serializable
data class SalaryRecord(
    val basis: String = PayBasis.MONTHLY.name,
    val amount: Long = 0,
    val takeHome: Long = 0,
    val workStart: String = "09:00",
    val workEnd: String = "18:00",
    val skipLunch: Boolean = true,
    val lunchStart: String = "12:00",
    val lunchEnd: String = "13:00",
    val workdays: List<Int> = SalarySettings.WEEKDAYS.map { it.value }.sorted(),
    val payday: Int = 25,
    val startDate: String? = null,
    val paydayNotice: Boolean = true,
)

fun SalarySettings.toRecord(): SalaryRecord = SalaryRecord(
    basis = basis.name,
    amount = amount,
    takeHome = takeHome,
    workStart = workStart.toString(),
    workEnd = workEnd.toString(),
    skipLunch = skipLunch,
    lunchStart = lunchStart.toString(),
    lunchEnd = lunchEnd.toString(),
    workdays = workdays.map { it.value }.sorted(),
    payday = payday,
    startDate = startDate?.toString(),
    paydayNotice = paydayNotice,
)

/** 읽을 수 없는 칸은 기본값으로 둔다(손으로 고친 백업 등). 금액이 음수면 0(정하지 않음)이다. */
fun SalaryRecord.toSettings(): SalarySettings {
    val defaults = SalarySettings()
    return SalarySettings(
        basis = PayBasis.entries.firstOrNull { it.name == basis } ?: PayBasis.MONTHLY,
        amount = amount.coerceAtLeast(0),
        takeHome = takeHome.coerceAtLeast(0),
        workStart = parseTime(workStart) ?: defaults.workStart,
        workEnd = parseTime(workEnd) ?: defaults.workEnd,
        skipLunch = skipLunch,
        lunchStart = parseTime(lunchStart) ?: defaults.lunchStart,
        lunchEnd = parseTime(lunchEnd) ?: defaults.lunchEnd,
        workdays = workdays.filter { it in 1..DAYS_PER_WEEK }.map(DayOfWeek::of).toSet(),
        payday = payday.coerceIn(1, MAX_PAYDAY),
        startDate = startDate?.let(::parseDate),
        paydayNotice = paydayNotice,
    )
}

private fun parseTime(text: String): LocalTime? = try {
    LocalTime.parse(text)
} catch (_: DateTimeParseException) {
    null
}

private fun parseDate(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (_: DateTimeParseException) {
    null
}

private const val DAYS_PER_WEEK = 7

/** 월급날은 1~31일. 그 달에 없는 날이면 말일로 본다. */
const val MAX_PAYDAY = 31
