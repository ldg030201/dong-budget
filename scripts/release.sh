#!/usr/bin/env bash
#
# 서명된 release APK 를 만든다.
#
#   ./scripts/release.sh
#
# Android Studio 의 Generate Signed APK 대화상자 대신 이걸 쓴다.
# 이유는 두 가지다.
#   1. 매번 비밀번호를 다시 입력하지 않아도 된다.
#   2. GitHub Actions 가 쓰는 것과 완전히 같은 경로를 탄다.
#      Studio 대화상자는 다른 경로라서 "내 컴퓨터에서는 되는데 CI 에서는 안 되는" 상황을 만든다.
#
# 키는 keystore.properties(로컬) 또는 DONG_* 환경변수(CI)에서 읽는다.
# 둘 다 저장소에 커밋되지 않는다.

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; OFF=$'\033[0m'
fail() { echo "${RED}${BOLD}실패:${OFF} $*" >&2; exit 1; }
info() { echo "${BOLD}$*${OFF}"; }
ok()   { echo "${GREEN}✓${OFF} $*"; }
warn() { echo "${YELLOW}!${OFF} $*"; }

# ── 서명 설정이 있는지 먼저 확인한다 ────────────────────────────────────
# 없는 상태로 빌드하면 서명되지 않은 APK 가 조용히 나오고,
# 그걸 모르고 배포하면 사용자가 설치할 수 없다.
if [ -f keystore.properties ]; then
  info "keystore.properties 에서 서명 정보를 읽는다"
elif [ -n "${DONG_KEYSTORE_FILE:-}" ]; then
  info "환경변수에서 서명 정보를 읽는다"
else
  cat >&2 <<'MSG'
서명 정보가 없다.

keystore.properties.example 을 keystore.properties 로 복사하고 값을 채워라.

  cp keystore.properties.example keystore.properties

아직 키가 없다면 먼저 만들어라. 이 키는 절대 잃어버리면 안 된다.
키가 바뀌면 기존 사용자가 업데이트를 설치할 수 없고 자동 백업 복원도 깨진다.

  keytool -genkeypair -v -keystore ~/dong-budget-release.jks \
    -alias dong-budget -keyalg RSA -keysize 2048 -validity 10000
MSG
  exit 1
fi

VERSION_NAME=$(grep '^dongbudget.versionName=' gradle.properties | cut -d= -f2)
VERSION_CODE=$(grep '^dongbudget.versionCode=' gradle.properties | cut -d= -f2)
info "버전 ${VERSION_NAME} (코드 ${VERSION_CODE})"

# ── 빌드 ────────────────────────────────────────────────────────────────
info "빌드 중..."
./gradlew --quiet :app:assembleRelease

APK=$(find app/build/outputs/apk/release -name '*.apk' -type f | head -1)
[ -n "$APK" ] || fail "APK 를 찾지 못했다."

case "$APK" in
  *unsigned*)
    fail "서명되지 않은 APK 가 나왔다 ($APK).
keystore.properties 의 storeFile 경로와 비밀번호를 확인하라.
경로는 저장소 최상위를 기준으로 해석된다."
    ;;
esac

# ── 서명 확인 ───────────────────────────────────────────────────────────
BUILD_TOOLS=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* 2>/dev/null | sort -V | tail -1)
[ -n "$BUILD_TOOLS" ] || fail "build-tools 를 찾지 못했다. ANDROID_HOME 을 확인하라."

FINGERPRINT=$("$BUILD_TOOLS/apksigner" verify --print-certs "$APK" 2>/dev/null \
  | grep -m1 'SHA-256 digest' | awk '{print $NF}')
[ -n "$FINGERPRINT" ] || fail "서명을 확인할 수 없다."

# ── 키가 바뀌지 않았는지 대조한다 ────────────────────────────────────────
# 이 프로젝트에서 가장 치명적인 사고가 서명키 교체다.
# 키가 바뀌면 기존 사용자는 업데이트를 설치할 수 없고, 앱을 지우고 새로 깔아야 하며,
# 그 순간 가계부 데이터가 전부 사라진다. 배포 전에 여기서 잡는다.
EXPECTED_FILE=".signing-fingerprint"
if [ -f "$EXPECTED_FILE" ]; then
  EXPECTED=$(tr -d '[:space:]' < "$EXPECTED_FILE")
  if [ "$FINGERPRINT" != "$EXPECTED" ]; then
    fail "서명키가 예전과 다르다.

  기대한 키: $EXPECTED
  지금 쓴 키: $FINGERPRINT

이대로 배포하면 기존 사용자가 업데이트를 설치할 수 없고,
앱을 지우고 새로 깔아야 해서 가계부 데이터가 전부 사라진다.

원래 키를 찾아서 쓰거나, 정말로 키를 바꾸는 것이 맞다면
${EXPECTED_FILE} 를 지우고 다시 실행하라."
  fi
  ok "서명키가 이전 배포와 같다"
else
  warn "${EXPECTED_FILE} 이 없어서 키 대조를 건너뛴다"
  echo "  이 키로 계속 배포할 것이라면 아래를 실행해 기록해 두어라."
  echo "  다음부터 키가 바뀌면 배포 전에 막아준다."
  echo
  echo "    echo $FINGERPRINT > $EXPECTED_FILE"
  echo
fi

SIZE=$(du -h "$APK" | cut -f1)
OUT="dong-budget-${VERSION_NAME}.apk"
cp "$APK" "$OUT"

echo
ok "완성: ${BOLD}${OUT}${OFF} (${SIZE})"
echo "  서명 SHA-256: $FINGERPRINT"
echo
echo "배포하려면 버전을 올리고, 바뀐 점을 메시지로 단 태그(-a)를 같은 이름으로 만들어 밀어라."
echo "태그 메시지는 앱 패치노트(PatchNotes.kt)의 이 버전 내용을 그대로 옮긴다. 형식은 docs/배포.md 참고."
echo "메시지 없는 태그(git tag v…)로 올리면 앱에 바뀐 점이 비어 보인다."
echo "  git tag -a v${VERSION_NAME} -F 태그메시지.txt && git push origin main v${VERSION_NAME}"
