package com.dong.budget.data.settings

/** 앱 테마 선택지. 설정 화면에서 사용자가 고른다. */
enum class ThemeMode {
    /** 기기의 다크 모드 설정을 따라간다 */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(value: String?): ThemeMode = fromNameOrNull(value) ?: SYSTEM

        /** 이 앱이 아는 테마면 그것, 아니면 null(백업이 더 새 앱의 테마를 담은 때 등) */
        fun fromNameOrNull(value: String?): ThemeMode? = entries.firstOrNull { it.name == value }
    }
}
