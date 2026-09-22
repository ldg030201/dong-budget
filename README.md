# 동계부

개인 가계부 안드로이드 앱. 데이터는 전부 기기 안에만 있고 서버를 쓰지 않는다.

- Kotlin + Jetpack Compose + Room
- 최소 지원: Android 12 (API 31)
- 배포: GitHub Releases 에 APK 를 올리고 앱이 직접 새 버전을 확인한다

## 지금까지 만든 것

- 거래 등록 / 수정 / 삭제
- 월별 합계와 거래 목록
- 시스템 / 밝게 / 어둡게 테마
- 앱 내 업데이트 확인과 설치

## 앞으로 할 것

- 토스 알림을 읽어 결제 내역을 자동으로 잡아주기
- 통계
- 백업 내보내기와 가져오기

## 개발

```bash
./gradlew assembleDebug        # 디버그 빌드
./gradlew installDebug         # 연결된 기기에 설치
./gradlew spotlessApply        # 코드 포맷 정리
./gradlew lintDebug            # 린트
```

`local.properties` 에 Android SDK 경로가 있어야 한다. 없으면 만든다.

```
sdk.dir=/Users/<이름>/Library/Android/sdk
```

## 배포

### 1. 서명키 만들기 (딱 한 번)

**이 키는 절대 잃어버리면 안 된다.** 키가 바뀌면 두 가지가 동시에 깨진다.

1. 기존 사용자가 업데이트를 설치할 수 없다 (앱을 지우고 다시 깔아야 하고, 그러면 가계부 데이터가 전부 사라진다)
2. Android 자동 백업 복원이 실패한다

저장소 바깥에 만들고, 만든 뒤 별도 장소에 백업한다.

```bash
keytool -genkeypair -v -keystore ~/dong-budget-release.jks \
  -alias dong-budget -keyalg RSA -keysize 2048 -validity 10000
```

비밀번호를 물어보면 직접 정해서 넣는다. 그 비밀번호도 키와 함께 안전한 곳에 적어둔다.

로컬에서 서명된 빌드를 만들려면 `keystore.properties.example` 을 `keystore.properties` 로
복사하고 값을 채운다. 이 파일과 `.jks` 는 `.gitignore` 에 걸려 있어 커밋되지 않는다.

### 2. GitHub Secrets 등록

저장소 Settings → Secrets and variables → Actions 에서 네 개를 만든다.

| 이름 | 값 |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -i ~/dong-budget-release.jks \| pbcopy` 결과 |
| `KEYSTORE_PASSWORD` | 키 저장소 비밀번호 |
| `KEY_ALIAS` | `dong-budget` |
| `KEY_PASSWORD` | 키 비밀번호 |

### 3. 새 버전 내보내기

`gradle.properties` 에서 두 값을 올린다. `versionCode` 는 반드시 커져야 한다.
낮추거나 같으면 기기가 설치를 거부한다.

```properties
dongbudget.versionCode=2
dongbudget.versionName=1.0.1
```

커밋하고 같은 이름으로 태그를 만들어 올린다.

```bash
git tag v1.0.1
git push origin main --tags
```

태그를 올리면 GitHub Actions 가 서명된 APK 를 만들어 Releases 에 올린다.
태그와 `versionName` 이 다르면 빌드가 실패한다.

### 저장소는 공개여야 한다

비공개 저장소는 Releases 첨부파일을 받을 때 인증을 요구한다.
앱이 인증하려면 토큰을 APK 에 넣어야 하는데 APK 는 뜯어볼 수 있어서 토큰이 그대로 새어나간다.

가계부 데이터는 기기 안에만 있고 저장소에는 코드만 올라간다.
서명키도 저장소가 아니라 Secrets 에 있으므로 공개해도 안전하다.

## 처음 설치하는 사람 안내

스토어를 거치지 않고 APK 를 직접 설치하므로 최초 한 번 설정이 필요하다.
두 번째부터는 앱 안에서 확인 → 설치 두 번만 누르면 된다.

1. **(갤럭시)** 설정 → 보안 및 개인정보 보호 → **자동 차단 끄기**
   켜져 있으면 스토어 밖 설치가 아예 막힌다. 예외 설정이 없어서 전역으로 꺼야 한다.
2. 브라우저에서 APK 를 받고 **알 수 없는 출처 설치를 허용**한다.
   Play Protect 경고가 뜨면 무시하고 설치한다.
3. 설정 → 앱 → 동계부 → 우측 상단 메뉴 → **제한된 설정 허용**
   이걸 하지 않으면 나중에 알림 접근 권한을 켤 수 없다 (토글이 회색으로 죽어 있다).
4. 배터리 최적화에서 동계부를 **제외**한다.
5. 앱에 **설치 권한을 허용**한다. 앱 안에서 업데이트를 받으려면 필요하다.

알림 접근 권한은 토스 알림 읽기 기능을 만든 뒤에 안내한다.

## 폰트

Pretendard 를 앱에 포함한다 (SIL Open Font License 1.1).
기기마다 글자가 달라지지 않게 하려는 것이고, 라이선스 전문은
`app/src/main/assets/licenses/pretendard_ofl.txt` 에 있다.
