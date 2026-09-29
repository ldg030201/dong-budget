package com.dong.budget.data.salary

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.dong.budget.BudgetApplication
import com.dong.budget.data.TransactionRepository
import com.dong.budget.data.db.BudgetTime
import com.dong.budget.data.db.SALARY_CATEGORY_CODE
import com.dong.budget.data.devlog.DevLog
import com.dong.budget.data.devlog.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth

/**
 * 월급날 알림을 맞춰 둔다. 늘 '다음 월급날 출근 시각' 알람 하나만 걸려 있다(같은 PendingIntent 로 다시 걸면 앞의 것을 바꾼다).
 *
 * 정확한 시각 알람(SCHEDULE_EXACT_ALARM)은 쓰지 않는다. Android 14 부터 새로 깐 앱에는 기본으로 막혀 있고,
 * 월급날 알림은 몇십 분 늦어도 괜찮다. 대신 절전 중에도 울리는 느슨한 알람(setAndAllowWhileIdle)을 쓴다.
 *
 * 알람은 재부팅하면 사라지고 앱을 강제로 멈춰도 사라진다. 재부팅·업데이트 때는 [SalaryAlarmReceiver] 가,
 * 강제 종료 뒤에는 다음에 앱을 열 때(AppContainer 가 설정을 따라가며) 다시 맞춘다.
 */
class SalaryScheduler(private val context: Context, private val prefs: SharedPreferences) {
    /** 이미 알린(또는 등록돼 있어 건너뛴) 가장 최근 달. 같은 달을 두 번 묻지 않는다. */
    var notifiedMonth: YearMonth?
        get() = prefs.getString(KEY_NOTIFIED, null)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        private set(value) = prefs.edit { putString(KEY_NOTIFIED, value?.toString()) }

    fun markNotified(month: YearMonth) {
        val last = notifiedMonth
        if (last == null || month.isAfter(last)) notifiedMonth = month
    }

    /** [settings] 로 다음 알람을 맞춘다. 알릴 것이 없으면(정하지 않음·알림 끔) 걸려 있던 알람을 뗀다. */
    fun schedule(settings: SalarySettings, now: Instant = Instant.now()) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = settings.nextPaydayAlarm(now.atZone(BudgetTime.ZONE).toLocalDateTime(), notifiedMonth)
        if (next == null) {
            alarmIntent(PendingIntent.FLAG_NO_CREATE)?.let { pending ->
                alarmManager.cancel(pending)
                pending.cancel()
                DevLog.info(LogTag.SALARY, "월급날 알림을 뗐어요")
            }
            return
        }
        val (month, at) = next
        val triggerAt = at.atZone(BudgetTime.ZONE).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, checkNotNull(alarmIntent(0)))
        DevLog.info(LogTag.SALARY, "월급날 알림을 맞췄어요 · ${month.monthValue}월 월급 · $at")
    }

    /** 알람이 울리면 [SalaryAlarmReceiver] 로 온다. 늘 같은 요청 번호·action 이라 한 개만 걸린다. */
    private fun alarmIntent(extraFlags: Int): PendingIntent? {
        val intent =
            Intent(context, SalaryAlarmReceiver::class.java)
                .setAction(SalaryAlarmReceiver.ACTION_PAYDAY)
                .setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, REQUEST_ALARM, intent, PendingIntent.FLAG_IMMUTABLE or extraFlags)
    }

    companion object {
        /** 알린 달을 적어 두는 SharedPreferences. 기기 밖(자동 백업)으로 나가지 않는다. */
        const val PREFS_NAME = "salary_alarm"
        private const val KEY_NOTIFIED = "notified_month"
        private const val REQUEST_ALARM = 1
    }
}

/**
 * 월급날 알람과 재부팅·업데이트를 받는다.
 *
 * - 알람: 오늘이 월급날이고 그달을 아직 알리지 않았고 월급도 아직 등록하지 않았으면 알림을 띄운다. 그리고 다음 달 알람을 맞춘다.
 * - 재부팅(BOOT_COMPLETED)·앱 업데이트(MY_PACKAGE_REPLACED): 사라진 알람을 다시 맞추기만 한다. 여기서는 알리지 않는다.
 *   그날이 월급날이고 시각이 지났으면 맞춘 알람이 곧바로 울려 거기서 알린다.
 */
class SalaryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_PAYDAY && action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as BudgetApplication).container
        // 설정은 저장소에서 읽어야 해서(suspend) 잠깐 붙잡아 두고 뒤에서 처리한다. 몇 초 안에 끝난다.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = container.salaryRepository.settings.first()
                if (action == ACTION_PAYDAY) {
                    notifyIfPayday(settings, container.salaryScheduler, container.salaryNotifier, container.transactionRepository)
                }
                container.salaryScheduler.schedule(settings)
            } catch (e: Exception) {
                DevLog.warn(LogTag.SALARY, "월급날 알람을 처리하지 못했어요", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun notifyIfPayday(
        settings: SalarySettings,
        scheduler: SalaryScheduler,
        notifier: SalaryNotifier,
        transactions: TransactionRepository,
    ) {
        val today = BudgetTime.toLocalDate(Instant.now())
        val month = settings.payMonthOn(today) ?: return
        val notified = scheduler.notifiedMonth
        if (!settings.isReady || !settings.paydayNotice || (notified != null && !month.isAfter(notified))) return
        val (from, until) = settings.salaryPeriod(month)
        when {
            transactions.isSalaryRegistered(salaryKey(month), SALARY_CATEGORY_CODE, from, until) ->
                DevLog.info(LogTag.SALARY, "${month.monthValue}월 월급은 이미 등록돼 있어 묻지 않았어요")

            // 알림을 꺼 뒀으면 이번 달은 건너뛴다. 알린 것으로 적지 않으면 그날 안에 알람이 계속 다시 울린다.
            !notifier.canNotify() -> DevLog.info(LogTag.SALARY, "알림을 보낼 수 없어 ${month.monthValue}월 월급날 알림을 건너뛰었어요")

            else -> {
                notifier.show(month, settings.monthly.toLong())
                DevLog.info(LogTag.SALARY, "${month.monthValue}월 월급날 알림을 띄웠어요")
            }
        }
        scheduler.markNotified(month)
    }

    companion object {
        const val ACTION_PAYDAY = "com.dong.budget.action.SALARY_PAYDAY"
    }
}
