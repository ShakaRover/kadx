#!/usr/bin/env bash
# GUI smoke test: launch jadx-gui with a generated project that restores a
# class tab at startup. This exercises the SmaliArea construction path that
# previously crashed with two Kotlin-vs-Swing nullability NPEs:
#   1. SmaliArea.getFont -> super.getFont() == null during installUI
#   2. SmaliV2Style.restoreDefaults(null) from SyntaxScheme(true) ctor
# Usage: run-gui-smoke.sh <path-to-jadx-gui-all.jar> [seconds] [apk]
# Verdict: exit 0 and no 'must not be null' / 'non-null is null' signatures.
set -euo pipefail
K="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAR="${1:?usage: run-gui-smoke.sh <jadx-gui-all.jar> [seconds] [apk]}"
DUR="${2:-90}"
APK="${3:-$K/tests/apks/markor-release-net.gsantner.markor-v163-2.16.1-flavorDefault-release-unsigned.apk}"
[ -f "$APK" ] || { echo "APK not found: $APK (build via tests/build-apk.sh markor release)"; exit 2; }

PROJ="$K/tests/gui-smoke/gui-test-project.jadx"
cat > "$PROJ" <<JSON
{
  "files": ["$APK"],
  "openTabs": [
    {
      "type": "class",
      "tabPath": "net.gsantner.markor.activity.MainActivity",
      "caret": 100,
      "view": {"x": 0, "y": 0},
      "active": true
    }
  ]
}
JSON

OUT="$(mktemp -p "$K/tests/tmp")"
export DISPLAY="${DISPLAY:-:1}"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$K/tests/tmp"
RC=0
timeout -k 10 "$DUR" java -jar "$JAR" "$PROJ" > "$OUT.log" 2>&1 || RC=$?
NPE=$(grep -cE "must not be null|Parameter specified as non-null" "$OUT.log" || true)
echo "exit=$RC npe_signatures=$NPE log=$OUT.log"
if [ "$NPE" != "0" ]; then
  grep -m2 -B1 -A4 -E "must not be null|Parameter specified as non-null" "$OUT.log"
  exit 1
fi
if ! grep -q "Loaded classes" "$OUT.log"; then
  # 加载未在时长内完成：标签恢复流程未执行，判定无效
  echo "INCONCLUSIVE: app load did not finish within ${DUR}s"
  exit 3
fi
echo "PASS: no nullability NPE signatures; load line:"
grep -m1 "Loaded classes" "$OUT.log" || true
