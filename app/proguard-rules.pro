# R8 keep 규칙.
#
# release 빌드에서 R8 코드 축소가 켜져 있다 (app/build.gradle.kts 의 isMinifyEnabled).
# 리플렉션으로만 접근하는 클래스가 생기면 여기에 keep 규칙을 추가해야 한다.
#
# 현재는 비어 있다. Room, Compose, kotlinx.serialization 모두 자체 규칙을 함께 배포하고
# 축소본을 실제로 설치해 거래 등록·조회, 화면 전환, 프로세스 재시작 후 복원까지 확인했다.
