#!/usr/bin/env bash
set -euo pipefail

cd "$GITHUB_WORKSPACE"
mkdir -p visual-lab/screens
firebase emulators:start --project demo-brujula --only auth,firestore > firebase-emulators.log 2>&1 &
firebase_pid=$!
evidence_dir="$GITHUB_WORKSPACE/visual-lab"
trap 'adb logcat -d > "$evidence_dir/logcat.txt" 2>&1 || true; adb exec-out run-as cl.brujula.app.test tar -C cache/test-evidence -cf - . 2>/dev/null | tar -xf - -C "$evidence_dir/screens" 2>/dev/null || true; cp "$GITHUB_WORKSPACE/firebase-emulators.log" "$evidence_dir/" 2>/dev/null || true; kill "$firebase_pid" 2>/dev/null || true' EXIT

for attempt in $(seq 1 90); do
  if nc -z 127.0.0.1 9099 && nc -z 127.0.0.1 8080; then
    break
  fi
  if ! kill -0 "$firebase_pid" 2>/dev/null; then
    cat firebase-emulators.log
    exit 1
  fi
  sleep 2
done
nc -z 127.0.0.1 9099
nc -z 127.0.0.1 8080

adb wait-for-device
package_ready=false
for attempt in $(seq 1 45); do
  if adb shell cmd package list packages >/dev/null 2>&1; then
    package_ready=true
    break
  fi
  sleep 2
done
test "$package_ready" = true

cd "$GITHUB_WORKSPACE/android"
if timeout 1200s ./gradlew :app:connectedDebugAndroidTest --no-daemon \
  -PBRUJULA_FIREBASE_API_KEY=demo-api-key \
  -PBRUJULA_FIREBASE_APP_ID=1:1234567890:android:abcdef1234567890 \
  -PBRUJULA_FIREBASE_PROJECT_ID=demo-brujula \
  -PBRUJULA_FIREBASE_EMULATOR_HOST=10.0.2.2 > ../visual-lab/instrumentation.txt 2>&1; then
  cat ../visual-lab/instrumentation.txt
else
  gradle_status=$?
  cat ../visual-lab/instrumentation.txt
  exit "$gradle_status"
fi

cd "$GITHUB_WORKSPACE"
