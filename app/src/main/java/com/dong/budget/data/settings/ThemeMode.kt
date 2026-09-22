package com.dong.budget.data.settings

/** 앱 테마 선택지. 설정 화면에서 사용자가 고른다. */
enum class ThemeMode {
    /** 기기의 다크 모드 설정을 따라간다 */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
