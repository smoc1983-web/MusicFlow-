"""MusicFlow 0.7.35 integration wiring. Behavior is tested by Kotlin/JUnit and Android emulator."""
from pathlib import Path

root = Path("app/src/main/java/com/musicflow/app")
gradle = Path("app/build.gradle.kts").read_text()
main = (root / "MainActivity.kt").read_text()
full = (root / "FullPlayerDialog.kt").read_text()
journal = (root / "PlaybackJournal.kt").read_text()
service = (root / "PlaybackService.kt").read_text()
manifest = Path("app/src/main/AndroidManifest.xml").read_text()
workflow = Path(".github/workflows/android-background-soak.yml").read_text()

assert 'versionCode = 735' in gradle and 'versionName = "0.7.35"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'applicationIdSuffix = ".dev"' in gradle
assert 'ResumePositionPolicy.resumePosition(' in journal
assert 'deferredResume' in journal and 'seekDeferredIfPossible()' in journal
assert 'player.isCurrentMediaItemSeekable' in journal
assert '"seekable_" + key' in journal
assert 'deferredResume?.id != current.mediaId' in journal
assert 'onPositionDiscontinuity' in journal
assert 'detectVerticalDragGestures(' in full
assert 'KeyboardArrowDown' in full and '"Свернуть плеер"' in full
assert 'PlayerMinimizePolicy.shouldMinimize' in full
assert 'currentClose()' in full
assert 'PlayerMinimizePolicy.canShowNowPlaying' in main
assert 'Text("Сейчас играет"' in main
assert 'onClose = { expandedPlayer = false }' in main
assert 'setWakeMode(C.WAKE_MODE_NETWORK)' in service
assert 'android.permission.WAKE_LOCK' in manifest
assert 'android.permission.WAKE_WIFI_LOCK' in manifest
assert 'testInstrumentationRunner' in gradle
assert Path("app/src/test/java/com/musicflow/app/ResumePositionPolicyTest.kt").exists()
assert Path("app/src/test/java/com/musicflow/app/PlayerMinimizePolicyTest.kt").exists()
assert Path("app/src/test/java/com/musicflow/app/BackgroundPlaybackSoakModelTest.kt").exists()
assert Path("app/src/androidTest/java/com/musicflow/app/PlaybackBackgroundSoakTest.kt").exists()
assert 'reactivecircus/android-emulator-runner@v2' in workflow
print("PASS: 0.7.35 deferred position, swipe/arrow minimize, Now Playing button, screen-off soak wiring")
