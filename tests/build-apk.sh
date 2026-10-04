#!/usr/bin/env bash
# Build an OSS Android project into an APK, keeping ALL work under the repo (no /tmp).
# Usage: tests/build-apk.sh <project-dir-name> [extra gradle args...]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJ="${1:?usage: build-apk.sh <project> [gradle args]}"
shift || true

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$ROOT/work/gradle-home"
export TMPDIR="$ROOT/tmp"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.io.tmpdir=$ROOT/tmp"

cd "$ROOT/oss/$PROJ"
echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "== building $PROJ (sdk=$ANDROID_HOME, gradle home=$GRADLE_USER_HOME) =="
./gradlew --console=plain assembleDebug "$@"

mkdir -p "$ROOT/apks"
found=0
while IFS= read -r apk; do
  cp -f "$apk" "$ROOT/apks/${PROJ}-$(basename "$apk")"
  echo "APK: $ROOT/apks/${PROJ}-$(basename "$apk")"
  found=1
done < <(find . -path '*/build/outputs/apk/*' -name '*.apk')
[ "$found" = 1 ] || { echo "no APK produced for $PROJ"; exit 2; }
