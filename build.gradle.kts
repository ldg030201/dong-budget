plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // build 태스크에 포맷 검사를 끼워넣지 않는다.
    // 켜두면 들여쓰기 한 칸 때문에 ./gradlew build 가 실패해서 원인 파악이 어려워진다.
    // 포맷은 ./gradlew spotlessApply 로 직접 돌리고, 필요해지면 CI 에서만 spotlessCheck 를 강제한다.
    isEnforceCheck = false

    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                // @Composable 함수는 PascalCase 가 관례인데 ktlint 의 function-naming 규칙이 이를 거부한다.
                // 이 설정은 .editorconfig 파일에 적으면 적용되지 않는다. 반드시 여기로 넘겨야 한다.
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "max_line_length" to "140",
            ),
        )
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}
