#!/usr/bin/env bash
# MusicFlow 0.7.62: mandatory offline Android UI/Media3 checks and explicitly
# classified *nonpassing* network diagnostics. Never fake a source PASS.
set -Eeuo pipefail
mkdir -p emulator-evidence
suite_group="${1:?offline, radio or live required}"
case "$suite_group" in offline|radio|live) ;; *) exit 2 ;; esac
printf '%s\n' "$suite_group" > emulator-evidence/suite.txt
# Bound every adb call, including diagnostic collection, even after device loss.
# Resolve adb outside the shell function so timeout invokes the executable.
ADB_BIN="$(type -P adb)"
adb() { timeout --kill-after=5s 40s "$ADB_BIN" "$@"; }
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
# This disposable AVD has no user to answer system crash/ANR dialogs.
# Instrumentation failures and crashes still fail run_android_test below.
adb shell settings put global hide_error_dialogs 1
# boot_completed does not mean system_server and launcher are quiescent.
# Capture baseline, allow cold-boot services to settle before installation.
adb shell cat /proc/pressure/cpu > emulator-evidence/cpu-before.txt || true
sleep 45
adb shell cat /proc/pressure/cpu > emulator-evidence/cpu-after.txt || true
install_checked app/build/outputs/apk/debug/app-debug.apk
install_checked app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Disposable AVD: explicitly unlock before exercising the actual UI.
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell dumpsys window > emulator-evidence/window-before-ui.txt || true
# Record the actual emulator acceleration mode; never silently run a software AVD.
"$ANDROID_HOME/emulator/emulator" -accel-check | tee emulator-evidence/acceleration.txt

run_android_test() {
  local id="$1" suite="$2" result=0
  printf 'Starting Android instrumentation: %s\n' "$suite"
  if ! adb get-state > "emulator-evidence/$id-device-before.txt" 2>&1 ||
     ! grep -qx device "emulator-evidence/$id-device-before.txt"; then
    echo "::error::ADB device unavailable before $id"
    return 1
  fi
  adb logcat -c || true
  # Radio navigation has multiple explicit waits; allow up to 8 minutes,
  # not the entire CI job. Live sources also get a bounded host-side deadline.
  if timeout --kill-after=10s 480s "$ADB_BIN" shell am instrument -w -r -e class "$suite" \
    com.musicflow.preview.dev.test/androidx.test.runner.AndroidJUnitRunner \
    > "emulator-evidence/$id.txt" 2>&1; then
    result=0
  else
    result=$?
  fi
  printf '%s\n' "$result" > "emulator-evidence/$id-exit-code.txt"
  if [[ "$result" -eq 124 || "$result" -eq 137 ]]; then
    echo "::error::Instrumentation $id exceeded host deadline"
    adb get-state > "emulator-evidence/$id-device-after.txt" 2>&1 || true
  fi
  tail -n 110 "emulator-evidence/$id.txt" || true
  adb logcat -d -v threadtime > "emulator-evidence/$id.logcat.txt" || true
  if [[ "$id" == "live-sources" ]]; then
    adb exec-out run-as com.musicflow.preview.dev cat files/source-probe-v0760.txt \
      > emulator-evidence/source-probe-v0760.txt 2>/dev/null || true
    cat emulator-evidence/source-probe-v0760.txt
  fi
  if [[ "$result" -ne 0 ]] ||
     ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "emulator-evidence/$id.txt" ||
     grep -Eq 'Process crashed|FAILURES!!!|INSTRUMENTATION_FAILED' "emulator-evidence/$id.txt"; then
    echo "::error::Android instrumentation $id FAILED; see full evidence"
    return 1
  fi
  echo "PASS: $id"
}

overall=0
case "$suite_group" in
  offline)
    run_android_test offline-hls com.musicflow.app.OfflineHlsCacheAndroidTest || overall=1
    run_android_test dash-quality com.musicflow.app.DashVideoQualityTest || overall=1
    ;;
  radio)
    run_android_test radio-ui \
      'com.musicflow.app.MusicSourcesEmulatorTest#radioScreenAlwaysShowsArtworkAndPlayerNavigationControls' || overall=1
    ;;
  live)
    adb shell getprop net.dns1 > emulator-evidence/dns-1.txt || true
    adb shell getprop net.dns2 > emulator-evidence/dns-2.txt || true
    report=emulator-evidence/source-probe-v0760.txt
    if run_android_test live-sources \
      'com.musicflow.app.MusicSourcesEmulatorTest#searchAndPlaybackOfAllThreeRegisteredSources'; then
      # An instrumentation PASS may only mean at least one provider reached READY.
      # Preserve independent status instead of claiming all providers passed.
      if [[ -s "$report" ]] && grep -Eq 'source=zaycev candidate=[0-9]+ playback=READY' "$report" &&
        grep -Eq 'source=muzofond candidate=[0-9]+ playback=READY' "$report" &&
        grep -Eq 'source=mailru candidate=[0-9]+ playback=READY' "$report"; then
        echo "LIVE_SOURCE_STATUS=PASS_ALL_READY" | tee emulator-evidence/live-source-status.env
      else
        echo "LIVE_SOURCE_STATUS=PARTIAL_NOT_ALL_READY" | tee emulator-evidence/live-source-status.env
        echo "::error::Not all three providers reached READY; inspect independent report"
        overall=1
      fi
    elif [[ -s "$report" ]] &&
       grep -Fq 'source=zaycev search=ERROR type=UnknownHostException' "$report" &&
       grep -Fq 'source=muzofond search=ERROR type=UnknownHostException' "$report" &&
       grep -Fq 'source=mailru search=ERROR type=UnknownHostException' "$report" &&
       grep -Fq 'playback=SKIPPED: no playable candidates returned' "$report" &&
       grep -Fq 'No provider returned a candidate:' emulator-evidence/live-sources.txt; then
      echo "LIVE_SOURCE_STATUS=NOT_VERIFIED_DNS_BLOCKED" | tee emulator-evidence/live-source-status.env
      echo "::warning::All three live source lookups failed DNS; no provider PASS claimed."
    else
      if ! adb get-state 2>/dev/null | grep -qx device; then
        echo "LIVE_SOURCE_STATUS=NOT_VERIFIED_DEVICE_LOST" | tee emulator-evidence/live-source-status.env
      else
        echo "LIVE_SOURCE_STATUS=FAILED" | tee emulator-evidence/live-source-status.env
      fi
      overall=1
    fi
    ;;
esac

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
echo "PASS: Android suite $suite_group; see suite evidence for exact results."
