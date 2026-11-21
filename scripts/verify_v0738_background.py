"""ub-mode red: Android OS owns MusicFlow APK background download in 0.7.38."""
from pathlib import Path
root=Path("app/src/main/java/com/musicflow/app")
transport=(root/"MusicFlowBackgroundUpdates.kt").read_text(encoding="utf-8")
about=(root/"MusicFlowAboutDialog.kt").read_text(encoding="utf-8")
automatic=(root/"MusicFlowAutoUpdate.kt").read_text(encoding="utf-8")
updates=(root/"MusicFlowUpdates.kt").read_text(encoding="utf-8")
gradle=Path("app/build.gradle.kts").read_text(encoding="utf-8")
assert "DownloadManager" in transport
assert "DownloadManager.Request" in transport
assert "setDestinationInExternalFilesDir" in transport
assert "VISIBILITY_VISIBLE_NOTIFY_COMPLETED" in transport
assert "enqueue(" in transport and "snapshot(" in transport
assert "getSharedPreferences" in transport
assert "MusicFlowUpdates.importVerified(" in transport
assert "MusicFlowBackgroundUpdates.enqueue(" in about
assert "MusicFlowBackgroundUpdates.enqueue(" in automatic
assert "MusicFlowBackgroundUpdates.snapshot(" in about
assert "MusicFlowBackgroundUpdates.snapshot(" in automatic
assert "MusicFlowUpdates.downloadVerified(context, update)" not in automatic
assert "MusicFlowUpdates.downloadVerified(context, release)" not in about
assert "versionCode = 738" in gradle and 'versionName = "0.7.38"' in gradle
print("PASS: durable system-managed update downloads and signature gating")
