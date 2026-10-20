#!/usr/bin/env bash
set -euo pipefail

cd "$GITHUB_WORKSPACE"
mkdir -p visual-lab/screens
firebase emulators:start --config tools/firebase-emulator-ci.json --project demo-brujula --only auth,firestore > firebase-emulators.log 2>&1 &
firebase_pid=$!
evidence_dir="$GITHUB_WORKSPACE/visual-lab"
adb_serial="${ANDROID_SERIAL:-emulator-5554}"
trap 'adb -s "$adb_serial" exec-out screencap -p > "$evidence_dir/screens/final-screen.png" 2>/dev/null || true; adb -s "$adb_serial" logcat -d > "$evidence_dir/logcat.txt" 2>&1 || true; adb -s "$adb_serial" shell run-as cl.brujula.app ls -la cache/test-evidence > "$evidence_dir/screenshot-files.txt" 2>&1 || true; adb -s "$adb_serial" exec-out run-as cl.brujula.app tar -C cache/test-evidence -cf - . 2>/dev/null | tar -xf - -C "$evidence_dir/screens" 2>/dev/null || true; cp "$GITHUB_WORKSPACE/firebase-emulators.log" "$evidence_dir/" 2>/dev/null || true; kill "$firebase_pid" 2>/dev/null || true' EXIT

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

adb -s "$adb_serial" wait-for-device
package_ready=false
for attempt in $(seq 1 45); do
  if adb -s "$adb_serial" shell cmd package list packages >/dev/null 2>&1; then
    package_ready=true
    break
  fi
  sleep 2
done
test "$package_ready" = true
adb -s "$adb_serial" reverse tcp:9099 tcp:9099
adb -s "$adb_serial" reverse tcp:8080 tcp:8080
adb -s "$adb_serial" reverse --list

cd "$GITHUB_WORKSPACE/android"
timeout 1200s ./gradlew :app:connectedDebugAndroidTest --no-daemon \
  -PBRUJULA_FIREBASE_API_KEY=demo-api-key \
  -PBRUJULA_FIREBASE_APP_ID=1:1234567890:android:abcdef1234567890 \
  -PBRUJULA_FIREBASE_PROJECT_ID=demo-brujula \
  -PBRUJULA_FIREBASE_EMULATOR_HOST=127.0.0.1 > ../visual-lab/instrumentation.txt 2>&1 &
gradle_pid=$!
(
  while kill -0 "$gradle_pid" 2>/dev/null; do
    adb -s "$adb_serial" exec-out run-as cl.brujula.app tar -C cache/test-evidence -cf - . 2>/dev/null \
      | tar -xf - -C "$evidence_dir/screens" 2>/dev/null || true
    sleep 2
  done
  adb -s "$adb_serial" exec-out run-as cl.brujula.app tar -C cache/test-evidence -cf - . 2>/dev/null \
    | tar -xf - -C "$evidence_dir/screens" 2>/dev/null || true
) &
capture_pid=$!
set +e
wait "$gradle_pid"
gradle_status=$?
wait "$capture_pid"
set -e
cat ../visual-lab/instrumentation.txt
if [ "$gradle_status" -ne 0 ]; then
  exit "$gradle_status"
fi

cd "$GITHUB_WORKSPACE"
