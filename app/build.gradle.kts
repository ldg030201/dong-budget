import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 의 built-in Kotlin 을 쓰므로 org.jetbrains.kotlin.android 는 적용하지 않는다.
    // 아래 플러그인의 버전이 곧 Kotlin(KGP) 버전이 된다.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    // Navigation 3 의 백스택은 화면 키를 직렬화해서 프로세스 사망 후에 복원한다.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

// ── 서명 설정 ──────────────────────────────────────────────────────────
// keystore.properties(로컬) → 환경변수(CI) 순으로 찾고, 둘 다 없으면 서명 설정을 만들지 않는다.
// 무조건 만들면 키스토어가 없는 상태에서 assembleRelease 가 "파일 없음"으로 죽는다.
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps =
    Properties().apply {
        if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
    }

fun signingValue(
    key: String,
    envName: String,
): String? =
    keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(envName).orNull?.takeIf { it.isNotBlank() }

val storeFilePath = signingValue("storeFile", "DONG_KEYSTORE_FILE")
val storePasswordValue = signingValue("storePassword", "DONG_KEYSTORE_PASSWORD")
val keyAliasValue = signingValue("keyAlias", "DONG_KEY_ALIAS")
val keyPasswordValue = signingValue("keyPassword", "DONG_KEY_PASSWORD")

val hasSigning =
    storeFilePath != null &&
        storePasswordValue != null &&
        keyAliasValue != null &&
        keyPasswordValue != null &&
        rootProject.file(storeFilePath).exists()

android {
    namespace = "com.dong.budget"

    // Compose 1.12.0 부터 compileSdk 37 이 필수다. 36 으로 내리면 checkDebugAarMetadata 가 실패한다.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dong.budget"
        minSdk = 31
        // AGP 9 부터 targetSdk 를 생략하면 compileSdk 값이 그대로 들어간다. 반드시 명시할 것.
        // 36 으로 두는 이유: Android 17(API 37)의 동작 변경을 알림 리스너 구현 전에 떠안지 않으려는 것.
        // 2단계(토스 알림)가 동작한 뒤 37 로 올린다. 그때 아래 lint 의 OldTargetApi 억제도 같이 지운다.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // 코드와 리소스를 줄인다.
            // 앱 내 업데이터가 업데이트마다 이 APK 를 통째로 내려받으므로 크기가 곧 비용이다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                // AGP 9 에서 "proguard-android.txt" 는 금지됐다. 반드시 -optimize 판을 써야 한다.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        // compileSdk 37 + targetSdk 36 조합이면 이 경고가 상시로 뜬다. 의도된 상태다.
        // targetSdk 를 37 로 올릴 때 이 줄을 지운다.
        disable += "OldTargetApi"
    }
}

// Room 스키마 JSON 출력 위치. 이 디렉토리는 git 에 커밋한다 (마이그레이션 검증에 필요).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}
