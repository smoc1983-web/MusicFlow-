"""MusicFlow 0.7.35: rounded menus and audited in-app upgrade tests."""
from pathlib import Path

root = Path("app/src/main")
src = root / "java/com/musicflow/app"
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
home = (src / "ZaycevHome.kt").read_text(encoding="utf-8")
menus = (src / "MusicFlowMenuStyle.kt").read_text(encoding="utf-8")
about = (src / "MusicFlowAboutDialog.kt").read_text(encoding="utf-8")
autoupdate = (src / "MusicFlowAutoUpdate.kt").read_text(encoding="utf-8")
updater = (src / "MusicFlowUpdates.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
workflow = Path(".github/workflows/android-apk.yml").read_text(encoding="utf-8")
check = Path("scripts/check_upgrade_0720_0721.sh").read_text(encoding="utf-8")

assert main.count("Modifier.musicFlowMenuSurface(palette)") >= 2
assert home.count("Modifier.musicFlowMenuSurface(palette)") >= 3
assert "RoundedCornerShape(20.dp)" in menus
assert "palette.accent.copy(alpha = 0.25f)" in menus
assert "shadowElevation = 14.dp" in main and "shadowElevation = 14.dp" in home
assert "shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)" in about
assert "shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)" in autoupdate

assert 'versionName = "0.7.35"' in gradle
assert "versionCode = 735" in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'incoming.packageName != context.packageName' in updater
assert "signatures(incoming)" in updater
assert "MusicFlowUpdates.latestRelease()" in autoupdate
assert "MusicFlowUpdates.downloadVerified(context, update)" in autoupdate
assert "MusicFlowUpdates.installerIntent(uri)" in autoupdate
assert "scripts/verify_v0722.py" in workflow
assert "gradle --no-daemon :app:assembleDebug" in workflow
assert "MusicFlow-apk" in workflow
assert 'apksigner' in check
assert "old_cert" in check and "new_cert" in check
assert "compatible=false" in check
assert "Do NOT uninstall v0.7.20" in check

print("MusicFlow v0.7.35 rounded UI and Android upgrade checks PASS")
