package com.dong.budget.data.salary

/** 월급 설정을 지울 때 잠금을 어떻게 할지(AppContainer.clearSalary) */
enum class SalaryLockReset {
    /** 그대로 둔다(월급 설정 초기화) */
    KEEP,

    /** PIN 과 지문만 지운다(PIN 을 잊었을 때) */
    PIN,

    /** 처음 안내까지 모두 지운다(데이터 초기화) */
    ALL,
}
