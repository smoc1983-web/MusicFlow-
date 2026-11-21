#!/usr/bin/env bash
# Deterministically check Android's in-place update requirements for the
# actual v0.7.20 APK and the freshly built v0.7.21 APK.
set -euo pipefail
old="${1:?Path to v0.7.20 APK required}"
new="${2:?Path to v0.7.21 APK required}"
mkdir -p build
report="build/update_0720_to_0721_report.txt"
aapt="${ANDROID_HOME}/build-tools/36.0.0/aapt"
signer="${ANDROID_HOME}/build-tools/36.0.0/apksigner"

old_badging="$("$aapt" dump badging "$old" | sed -n '1p')"
new_badging="$("$aapt" dump badging "$new" | sed -n '1p')"
extract() { sed -n "s/.*$2='\([^']*\)'.*/\1/p" <<< "$1"; }
old_package="$(extract "$old_badging" name)"
new_package="$(extract "$new_badging" name)"
old_code="$(extract "$old_badging" versionCode)"
new_code="$(extract "$new_badging" versionCode)"
old_cert="$("$signer" verify --print-certs "$old" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | sed -n '1p')"
new_cert="$("$signer" verify --print-certs "$new" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | sed -n '1p')"

compatible=false
if [[ -n "$old_package" && "$old_package" == "$new_package" &&
      -n "$old_cert" && "$old_cert" == "$new_cert" &&
      "$new_code" =~ ^[0-9]+$ && "$old_code" =~ ^[0-9]+$ &&
      "$new_code" -gt "$old_code" ]]; then
    compatible=true
fi

{
  echo "MusicFlow 0.7.20 -> 0.7.21 Android update compatibility"
  echo "Current: package=$old_package versionCode=$old_code"
  echo "New:     package=$new_package versionCode=$new_code"
  echo "Current APK signer SHA-256: $old_cert"
  echo "New APK signer SHA-256:     $new_cert"
  echo "Compatible in-place Android upgrade: $compatible"
  if [[ "$compatible" == false ]]; then
    echo "BLOCKED: Android requires the same package ID and signing identity."
    echo "Do NOT uninstall v0.7.20 merely to clear the error; preserve user data."
    echo "This cannot be repaired by UI code or skipping installer checks."
  fi
} | tee "$report"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "compatible=$compatible" >> "$GITHUB_OUTPUT"
fi
if [[ "$compatible" == false ]]; then
  echo "::warning::MusicFlow 0.7.21 cannot update v0.7.20 in-place. Check attached report."
fi
