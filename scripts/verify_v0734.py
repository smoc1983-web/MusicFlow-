"""Regression guard for 0.7.37 radio navigation label and updater version."""
from pathlib import Path

main = Path("app/src/main/java/com/musicflow/app/MainActivity.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
assert '6 -> "Радио"' in main
assert '6 to "Радио"' in main
assert '"Русское радио"' not in main
assert 'versionCode = 737' in gradle
assert 'versionName = "0.7.37"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
print("PASS: Radio navigation label; applicationId and version 0.7.37")
