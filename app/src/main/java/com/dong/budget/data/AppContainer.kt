package com.dong.budget.data

import android.content.Context
import com.dong.budget.data.db.BudgetDatabase
import com.dong.budget.data.settings.SettingsRepository
import com.dong.budget.data.update.ApkInstaller
import com.dong.budget.data.update.UpdateRepository

/**
 * 수동 의존성 컨테이너.
 *
 * Hilt 를 쓰지 않는다. 이 규모에서 얻는 건 없고 빌드 구성에 움직이는 부품만 늘어난다.
 * 의존성이 늘어 감당이 안 되는 시점이 오면 그때 도입한다.
 */
class AppContainer(context: Context) {
    private val database by lazy { BudgetDatabase.build(context) }

    val transactionRepository by lazy {
        TransactionRepository(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            paymentMethodDao = database.paymentMethodDao(),
        )
    }

    val settingsRepository by lazy { SettingsRepository(context) }

    val updateRepository by lazy { UpdateRepository() }

    val apkInstaller by lazy { ApkInstaller(context) }
}
