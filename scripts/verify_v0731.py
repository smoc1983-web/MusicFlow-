"""Static integration smoke check; Kotlin/JUnit tests validate actual behavior."""
from pathlib import Path

src=Path("app/src/main/java/com/musicflow/app")
main=(src/"MainActivity.kt").read_text(encoding="utf-8")
service=(src/"PlaybackService.kt").read_text(encoding="utf-8")
lyrics=(src/"SyncedLyrics.kt").read_text(encoding="utf-8")
gradle=Path("app/build.gradle.kts").read_text(encoding="utf-8")
ci=Path(".github/workflows/android-apk.yml").read_text(encoding="utf-8")
assert 'versionCode = 733' in gradle and 'versionName = "0.7.33"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'showSleepTimerDialog' in main and 'MusicFlowSleepTimerDialog(' in main
assert 'MusicFlowSleepTimerStore.deadlineMs(this@PlaybackService)' in service
assert 'player.pause()' in service and 'playbackJournal.persist()' in service
assert 'LyricsTiming.activeIndex(' in lyrics and 'changeOffset(' in lyrics
assert 'musicflow_lyrics_timing_v1' in lyrics
assert 'testDebugUnitTest' in ci
assert Path("AGENTS.md").is_file() and Path(".claude/skills/ub-mode/SKILL.md").is_file()
assert Path(".agents/skills/ub-mode/SKILL.md").is_file()
assert (src/"MusicPlaybackRecovery.kt").is_file()
print("0.7.33 source wiring PASS (Kotlin/JUnit checks behavior)")
