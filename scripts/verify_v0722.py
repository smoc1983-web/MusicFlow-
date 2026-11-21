"""Stable MusicFlow 0.7.31 and 0.7.23 APK update baseline static checks."""
from pathlib import Path
src = Path("app/src/main/java/com/musicflow/app")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
update = (src / "MusicFlowUpdates.kt").read_text(encoding="utf-8")
auto = (src / "MusicFlowAutoUpdate.kt").read_text(encoding="utf-8")
about = (src / "MusicFlowAboutDialog.kt").read_text(encoding="utf-8")
workflow = Path(".github/workflows/bootstrap-stable-signing.yml").read_text(encoding="utf-8")
publish = Path(".github/workflows/publish-signed-apk.yml").read_text(encoding="utf-8")
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'versionCode = 731' in gradle
assert 'versionName = "0.7.31"' in gradle
assert 'create("musicflowRelease")' in gradle
assert 'signingConfig = signingConfigs.getByName("musicflowRelease")' in gradle
for key in ("MUSICFLOW_SIGNING_KEYSTORE", "MUSICFLOW_SIGNING_STORE_PASSWORD",
            "MUSICFLOW_SIGNING_KEY_ALIAS", "MUSICFLOW_SIGNING_KEY_PASSWORD"):
    assert key in gradle
assert "incoming.packageName != context.packageName" in update
assert "signatures(incoming)" in update
assert "MusicFlowUpdates.latestRelease()" in auto
assert "MusicFlowUpdates.downloadVerified(context, update)" in auto
assert "MusicFlowUpdates.installerIntent(uri)" in auto
assert "MusicFlowUpdates.importVerified(context, uri)" in about
assert "openssl cms -encrypt" in workflow
assert "app:assembleRelease" in workflow
assert "apksigner" in workflow
assert "v0.7.23" in workflow
assert "MUSICFLOW_SIGNING_KEYSTORE_BASE64" in publish
print("MusicFlow v0.7.31 static signing and update prerequisites PASS")
