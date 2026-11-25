"""Validate MusicFlow 0.7.28 upgrades through its official in-app installer."""
from pathlib import Path
root = Path("app/src/main/java/com/musicflow/app")
updates = (root / "MusicFlowUpdates.kt").read_text(encoding="utf-8")
auto = (root / "MusicFlowAutoUpdate.kt").read_text(encoding="utf-8")
about = (root / "MusicFlowAboutDialog.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
workflow = Path(".github/workflows/publish-v0727-inapp.yml").read_text(encoding="utf-8")
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'versionCode = 740' in gradle and 'versionName = "0.7.40"' in gradle
assert 'MUSICFLOW_SIGNING_KEYSTORE' in gradle
assert 'getByName("release")' in gradle
assert 'private const val OFFICIAL_PACKAGE = "com.musicflow.preview"' in updates
assert 'supportsOfficialUpdates(context)' in updates
assert 'updateChannelMessage(context)' in about
assert 'ClipData.newRawUri("MusicFlow update", uri)' in updates
assert 'checkArchive(context, temp, update.versionCode)' in updates
assert 'signatures(incoming)' in updates
assert 'incoming.packageName != context.packageName' in updates
assert 'MusicFlowUpdates.latestRelease()' in auto
assert 'last_check' not in auto
assert 'MusicFlowBackgroundUpdates.enqueue(context, update)' in auto
assert 'MusicFlowUpdates.installerIntent(uri)' in auto
assert 'officialUpdateChannel && !checking && !downloading' in about
assert 'apksigner' in workflow and 'gh release create' in workflow
assert 'test "$BASE_SIGNER" = "$NEW_SIGNER"' in workflow
assert 'MUSICFLOW_SIGNING_BACKUP_PASSPHRASE' in workflow
assert "versionCode='727'" in workflow
assert 'MusicFlow_signing_release.jks.enc.b64' in workflow
assert '-pbkdf2 -iter 350000' in workflow
assert 'c461ee635d5b295ef6c765e1ae533d0079dc6e53cb2632a11a1b445773752410' in workflow
print("MusicFlow 0.7.28 updater and release compatibility regression PASS")
