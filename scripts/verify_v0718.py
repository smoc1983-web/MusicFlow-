"""Static regression checks for MusicFlow 0.7.18 in-app updates & links.

A local APK build is required. Repeated in-place Android upgrades additionally
require a stable release signing key, deliberately stored outside the repository.
"""
from pathlib import Path
root = Path("app/src/main")
src = root / "java/com/musicflow/app"
updater = (src / "MusicFlowUpdates.kt").read_text(encoding="utf-8")
about = (src / "MusicFlowAboutDialog.kt").read_text(encoding="utf-8")
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
manifest = (root / "AndroidManifest.xml").read_text(encoding="utf-8")
paths = (root / "res/xml/musicflow_file_paths.xml").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
workflow = Path(".github/workflows/publish-signed-apk.yml").read_text(encoding="utf-8")

assert "github.com/smoc1983-web/MusicFlow-/releases" in updater
assert "api.github.com/repos/smoc1983-web/MusicFlow-/releases" in updater
assert "actions/artifacts" not in updater
assert "+79128580680" in updater
assert "https://t.me/c/3702128532/1" in updater
assert "PackageInfoCompat.getLongVersionCode" in updater
assert 'incoming.packageName != context.packageName' in updater
assert "signatures(incoming)" in updater
assert "ZipFile(apk)" in updater
assert "FileProvider.getUriForFile(" in updater
assert "Intent.FLAG_GRANT_READ_URI_PERMISSION" in updater
assert "allowInstallIntent(context)" in about
assert "MusicFlowUpdates.latestRelease()" in about
assert "MusicFlowUpdates.downloadVerified(context, release)" in about
assert "MusicFlowUpdates.SUPPORT_NUMBER" in about
assert "MusicFlowUpdates.TELEGRAM_URL" in about
assert "MusicFlowAboutDialog(onDismiss" in main
assert "О приложении · обновления" in main
assert 'android.permission.REQUEST_INSTALL_PACKAGES' in manifest
assert '<cache-path name="musicflow_updates" path="updates/"' in paths
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'versionName = "0.7.19-preview"' in gradle
assert "MUSICFLOW_SIGNING_KEYSTORE" in gradle
for secret in (
    "MUSICFLOW_SIGNING_KEYSTORE_BASE64",
    "MUSICFLOW_SIGNING_STORE_PASSWORD",
    "MUSICFLOW_SIGNING_KEY_ALIAS",
    "MUSICFLOW_SIGNING_KEY_PASSWORD",
):
    assert secret in workflow
assert "gradle --no-daemon :app:assembleRelease" in workflow
assert 'gh release create "$TAG"' in workflow
print("MusicFlow v0.7.18 in-app update and support checks PASS")
