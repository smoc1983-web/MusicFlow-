"""MusicFlow 0.7.25: update popup on startup and remove Preview/history UI."""
from pathlib import Path
root = Path("app/src/main")
src = root / "java/com/musicflow/app"
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
auto = (src / "MusicFlowAutoUpdate.kt").read_text(encoding="utf-8")
about = (src / "MusicFlowAboutDialog.kt").read_text(encoding="utf-8")
updater = (src / "MusicFlowUpdates.kt").read_text(encoding="utf-8")
manifest = (root / "AndroidManifest.xml").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
publish = Path(".github/workflows/publish-signed-apk.yml").read_text(encoding="utf-8")
build = Path(".github/workflows/android-apk.yml").read_text(encoding="utf-8")

assert 'android:label="MusicFlow"' in manifest
assert 'Preview' not in manifest
assert 'versionName = "0.7.25"' in gradle
assert 'versionCode = 725' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle, "Preserve application ID for update compatibility"
assert 'MusicFlowAutoUpdate()' in main
assert 'Доступно обновление MusicFlow' in auto
assert 'MusicFlowUpdates.latestRelease()' in auto
assert 'latest.versionCode > MusicFlowUpdates.currentCode(context)' in auto
assert 'MusicFlowUpdates.downloadVerified(context, update)' in auto
assert 'MusicFlowUpdates.installerIntent(uri)' in auto
assert 'MusicFlowUpdates.allowInstallIntent(context)' in auto
assert 'last_check' in auto and 'snoozed_until' in auto
assert 'История выпусков' not in about
assert 'MusicFlowUpdates.RELEASES_PAGE' not in about
assert 'MusicFlowUpdates.latestRelease()' in about
assert 'MusicFlowUpdates.importVerified(context, uri)' in about
assert 'incoming.packageName != context.packageName' in updater
assert 'signatures(incoming)' in updater
assert 'gh release create "$TAG"' in publish
assert '--prerelease' not in publish
assert 'name: MusicFlow-apk' in build
print("MusicFlow 0.7.25 in-app updates, branding and signing constraints PASS")
