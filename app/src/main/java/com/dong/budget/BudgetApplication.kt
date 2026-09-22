package com.dong.budget

import android.app.Application
import com.dong.budget.data.AppContainer

class BudgetApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
