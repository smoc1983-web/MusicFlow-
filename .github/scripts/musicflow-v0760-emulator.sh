#!/usr/bin/env bash
set -Eeuo pipefail
mkdir -p emulator-evidence
adb wait-for-device
adb shell getprop sys.boot_completed | tee emulator-evidence/boot.txt
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
overall=0
for suite in MusicSourcesEmulatorTest DashVideoQualityTest; do
  echo "Testing $suite"
  adb logcat -c || true
  set +e
  adb shell am instrument -w -r -e class "com.musicflow.app.$suite" com.musicflow.preview.dev.test/androidx.test.runner.AndroidJUnitRunner > "emulator-evidence/$suite.txt" 2>&1
  rc=$?
  set -e
  cat "emulator-evidence/$suite.txt" | tail -n 150
  adb logcat -d -v threadtime > "emulator-evidence/$suite.logcat.txt" || true
  if [[ $rc -ne 0 ]] || ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "emulator-evidence/$suite.txt"; then
    echo "::error::Instrumentation $suite failed or crashed (command status $rc)"
    overall=1
  fi
  if grep -Eq 'Process crashed|FAILURES!!!|INSTRUMENTATION_FAILED' "emulator-evidence/$suite.txt"; then overall=1; fi
done
for item in source-probe-v0760.txt musicflow-radio-list-v0760.png musicflow-radio-player-v0760.png; do
  adb exec-out run-as com.musicflow.preview.dev cat "files/$item" > "emulator-evidence/$item" 2>/dev/null || rm -f "emulator-evidence/$item"
done
adb logcat -d -b crash > emulator-evidence/crash-logcat.txt || true
adb exec-out screencap -p > emulator-evidence/last-emulator-screen.png || true
if [[ $overall -ne 0 ]]; then
  echo "::error::Emulator tests did not pass; consult saved source results and logcat."
  exit 1
fi
echo "PASS: all requested instrumented suites completed without crashes"
