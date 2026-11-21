"""Static wiring test; actual counter/seek/pause behavior is tested by Kotlin JUnit."""
from pathlib import Path
root=Path("app/src/main/java/com/musicflow/app")
gradle=Path("app/build.gradle.kts").read_text()
journal=(root/"PlaybackJournal.kt").read_text()
screen=(root/"MusicPlaybackHistoryScreen.kt").read_text()
stats=(root/"ListeningStatisticsStore.kt").read_text()
tracker=(root/"ListeningProgressTracker.kt").read_text()
assert 'versionCode = 736' in gradle and 'versionName = "0.7.36"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
assert Path("app/src/test/java/com/musicflow/app/ListeningProgressTrackerTest.kt").is_file()
assert Path("app/src/test/java/com/musicflow/app/ListeningStatisticsReducerTest.kt").is_file()
assert 'ListeningStatisticsStore.record(application, current, tick)' in journal
assert 'SystemClock.elapsedRealtime()' in journal
assert 'tick.qualifiedStart' in journal and 'appendHistory(current)' in journal
transition=journal.split('override fun onMediaItemTransition',1)[1].split('override fun onIsPlayingChanged',1)[0]
assert 'appendHistory' not in transition
assert 'onTimelineChanged' in journal and 'queueDirty' in journal
assert 'ListeningInsightsPanel(' in screen
assert 'showStats' in screen and 'ListeningStatisticsStore.clear(context)' in screen
assert 'musicflow_listening_statistics_v1' in stats
assert 'elapsedMs - old.elapsedMs' in tracker
assert 'ruvideo:' in tracker and 'vk:' in tracker
assert Path(".agents/skills/ub-mode/SKILL.md").is_file()
print("PASS: 0.7.36 listening stats, verified history, queue-write optimization and ub-mode")
