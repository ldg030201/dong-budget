# R8 keep 규칙.
#
# release 빌드에서 R8 코드 축소가 켜져 있다 (app/build.gradle.kts 의 isMinifyEnabled).
# 리플렉션으로만 접근하는 클래스가 생기면 여기에 keep 규칙을 추가해야 한다.
#
# keep 규칙은 아직 없다. Room, Compose, kotlinx.serialization 모두 자체 규칙을 함께 배포하고
# 축소본을 실제로 설치해 거래 등록·조회, 화면 전환, 프로세스 재시작 후 복원까지 확인했다.

# 개발자 모드 로그에 남는 오류의 호출 기록을 그대로 읽을 수 있게 이름을 바꾸지 않는다.
# 이름을 바꾸면(난독화) 'a.b.c' 처럼 남아서 그 빌드의 mapping.txt 없이는 어디서 났는지 알 수 없다.
# 코드는 공개 저장소에 있어서 이름을 숨길 까닭이 없다. 줄이기(안 쓰는 코드 빼기)와 최적화는 그대로 한다.
-dontobfuscate
# 호출 기록에 파일 이름과 줄 번호를 남긴다
-keepattributes SourceFile,LineNumberTable
