#!/usr/bin/env bash
# DreamPipe local verification and USB installation. Requires an existing Android SDK and JDK 21.
set -Eeuo pipefail
trap 'printf "\nStopped at line %s; see the error above.\n" "$LINENO" >&2' ERR
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
command -v python3 >/dev/null
python3 tools/check-resource-duplicates.py
command -v adb >/dev/null
export JAVA_HOME="${DREAMPIPE_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"
[[ -x "$JAVA_HOME/bin/java" ]] || { printf "Set DREAMPIPE_JAVA_HOME to your JDK 21 directory.\n" >&2; exit 1; }
export PATH="$JAVA_HOME/bin:$PATH"

[[ -f local.properties ]]
chmod +x gradlew
export ANDROID_HOME="$(sed -n 's/^sdk\.dir=//p' local.properties | tr -d '\r' | tail -n 1)"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
[[ -d "$ANDROID_HOME" ]] || { printf 'Android SDK not found: %s\n' "$ANDROID_HOME" >&2; exit 1; }

dreampipe_version="$(python3 - <<'READ_APP_VERSION'
from pathlib import Path
import re
text = Path('smarttubetv/build.gradle').read_text()
flavor = re.search(r'^\s*stmobile\s*\{', text, re.M)
if flavor is None:
    raise SystemExit('The DreamPipe phone flavor was not found.')
depth, end = 1, flavor.end()
while depth and end < len(text):
    depth += (text[end] == '{') - (text[end] == '}')
    end += 1
version = re.search(r'^\s*versionName\s+"([^"]+)"', text[flavor.end():end - 1], re.M)
if version is None or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.]+)?', version[1]):
    raise SystemExit('Cannot determine the DreamPipe phone application version.')
print(version[1])
READ_APP_VERSION
)"

mkdir -p "$HOME/.down"

if ! ./gradlew --no-daemon --console=plain --max-workers=4 --continue \
    :common:testDebugUnitTest \
    :smarttubetv:testStmobileDebugUnitTest \
    :smarttubetv:lintStmobileDebug \
    :smarttubetv:assembleStmobileDebug 2>&1 | tee dreampipe-build.log; then
    diagnostics="$HOME/.down/DreamPipe-${dreampipe_version}-diagnostics.txt"
    mkdir -p "$HOME/.down"
    python3 tools/dreampipe-diagnostics.py > "$diagnostics"
    printf '\nBUILD LOG END:\n' >> "$diagnostics"
    tail -n 100 dreampipe-build.log >> "$diagnostics"
    printf '\nChecks failed; upload the complete file: %s\n' "$diagnostics" >&2
    exit 1
fi

adb start-server
printf '\nConnect the phone by USB and allow USB debugging if prompted.\n'
timeout 120s adb -d wait-for-device
serial="$(adb -d get-serialno)"
abi="$(adb -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')"
case "$abi" in arm64-v8a|armeabi-v7a|x86) ;; *) abi=universal ;; esac
apk="$PWD/smarttubetv/build/outputs/renamed_apks/stmobileDebug/DreamPipe_${dreampipe_version}_${abi}.apk"
[[ -f "$apk" ]] || apk="$PWD/smarttubetv/build/outputs/apk/stmobile/debug/smarttubetv-stmobile-${abi}-debug.apk"
[[ -f "$apk" ]] || { printf 'Built APK not found for %s\n' "$abi" >&2; exit 1; }
adb -s "$serial" install --user 0 -r "$apk"
adb -s "$serial" shell am start --user 0 -n \
    app.dreampipe.debug/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
cp -- "$apk" "$HOME/.down/DreamPipe-${dreampipe_version}-${abi}-debug.apk"
printf '\nInstalled DreamPipe %s.\nAPK: %s/.down/DreamPipe-%s-%s-debug.apk\nProject: %s\n' "$dreampipe_version" "$HOME" "$dreampipe_version" "$abi" "$PWD"
