#!/usr/bin/env bash
# MusicFlow 0.7.61: mandatory offline Android UI/Media3 checks and explicitly
# classified *nonpassing* network diagnostics. Never fake a source PASS.
set -Eeuo pipefail
mkdir -p emulator-evidence
adb wait-for-device
adb shell getprop sys.boot_completed | tee emulator-evidence/boot.txt
for attempt in $(seq 1 60); do
  if [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == "1" ]] &&
     adb shell pm path android >/dev/null 2>&1; then break; fi
  sleep 5
done
install_checked() {
  local apk="$1" attempt
  for attempt in 1 2 3; do
    if adb install --no-streaming -r "$apk" 2>&1 | tee -a emulator-evidence/install.txt; then
      return 0
    fi
    adb logcat -d -s PackageManager PackageInstaller > emulator-evidence/install-logcat.txt || true
    sleep 30
  done
  return 1
}
install_checked app/build/outputs/apk/debug/app-debug.apk
install_checked app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

run_android_test() {
  local id="$1" suite="$2" result=0
  printf 'Starting Android instrumentation: %s\n' "$suite"
  adb logcat -c || true
  set +e
  adb shell am instrument -w -r -e class "$suite" \
    com.musicflow.preview.dev.test/androidx.test.runner.AndroidJUnitRunner \
    > "emulator-evidence/$id.txt" 2>&1
  result=$?
  set -e
  tail -n 110 "emulator-evidence/$id.txt" || true
  adb logcat -d -v threadtime > "emulator-evidence/$id.logcat.txt" || true
  if [[ "$result" -ne 0 ]] ||
     ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "emulator-evidence/$id.txt" ||
     grep -Eq 'Process crashed|FAILURES!!!|INSTRUMENTATION_FAILED' "emulator-evidence/$id.txt"; then
    echo "::error::Android instrumentation $id FAILED; see full evidence"
    return 1
  fi
  echo "PASS: $id"
}

overall=0
# These checks use bundled radio fixtures and synthetic DASH MPDs, no external
# music providers; therefore network/DNS failures cannot hide Android defects.
run_android_test radio-ui \
  'com.musicflow.app.MusicSourcesEmulatorTest#radioScreenAlwaysShowsArtworkAndPlayerNavigationControls' || overall=1
run_android_test dash-quality com.musicflow.app.DashVideoQualityTest || overall=1

# Live provider checks are not interchangeable with offline regressions.
# Keep real asserts and require READY. The only exception to the release gate
# is independently recorded DNS failure of *all three* provider lookups.
adb shell getprop net.dns1 > emulator-evidence/dns-1.txt || true
adb shell getprop net.dns2 > emulator-evidence/dns-2.txt || true
if run_android_test live-sources \
  'com.musicflow.app.MusicSourcesEmulatorTest#searchAndPlaybackOfAllThreeRegisteredSources'; then
  echo "LIVE_SOURCE_STATUS=PASS_READY" | tee emulator-evidence/live-source-status.env
else
  report=emulator-evidence/live-sources.txt
  if grep -Fq 'zaycev=ERROR type=UnknownHostException' "$report" &&
     grep -Fq 'muzofond=ERROR type=UnknownHostException' "$report" &&
     grep -Fq 'mailru=ERROR type=UnknownHostException' "$report"; then
    echo "LIVE_SOURCE_STATUS=NOT_VERIFIED_DNS_BLOCKED" | tee emulator-evidence/live-source-status.env
    echo "::warning::All three live source lookups failed DNS in Android emulator. No provider PASS claimed; requires a live device check."
  else
    echo "LIVE_SOURCE_STATUS=FAILED" | tee emulator-evidence/live-source-status.env
    echo "::error::Provider failures were NOT exclusively DNS; gate remains failed."
    overall=1
  fi
fi

for item in source-probe-v0760.txt musicflow-radio-list-v0760.png musicflow-radio-player-v0760.png; do
  adb exec-out run-as com.musicflow.preview.dev cat "files/$item" \
    > "emulator-evidence/$item" 2>/dev/null || rm -f "emulator-evidence/$item"
done
adb logcat -d -b crash > emulator-evidence/crash-logcat.txt || true
adb exec-out screencap -p > emulator-evidence/last-emulator-screen.png || true
if [[ "$overall" -ne 0 ]]; then
  echo "::error::Required offline Android gate or unclassified source test failed."
  exit 1
fi
echo "PASS: REQUIRED Android UI/DASH gate; see live-source-status.env for independent network result."
