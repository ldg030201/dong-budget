# R8 keep 규칙.
# 현재 isMinifyEnabled = false 라 사용되지 않지만, app/build.gradle.kts 의 proguardFiles 가
# 이 파일을 참조하므로 반드시 존재해야 한다. (없으면 R8 을 켜는 순간 설정 단계에서 하드 실패)
