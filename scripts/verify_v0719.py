"""Regression checks for three official banks, in-app APK updates and icon #10."""
from pathlib import Path

root = Path("app/src/main")
src = root / "java/com/musicflow/app"
bank = (src / "MusicFlowBankSupport.kt").read_text(encoding="utf-8")
about = (src / "MusicFlowAboutDialog.kt").read_text(encoding="utf-8")
update = (src / "MusicFlowUpdates.kt").read_text(encoding="utf-8")
manifest = (root / "AndroidManifest.xml").read_text(encoding="utf-8")
launcher = (root / "res/mipmap-anydpi-v26/ic_launcher_graphite_v19.xml").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")

for package in ("com.idamob.tinkoff.android", "ru.sberbankmobile",
                "ru.ozon.fintech.finance"):
    assert package in bank
    assert package in manifest
    assert "https://www.rustore.ru/catalog/app/" + package in bank

assert "MusicFlowUpdates.SUPPORT_NUMBER" in bank
assert "getLaunchIntentForPackage(bank.packageId)" in bank
assert "ClipData.newPlainText(" in bank
assert "MusicFlowBank.entries.forEach" in about
assert "Поддержать через " in about
assert "MusicFlowUpdates.latestRelease()" in about
assert "LaunchedEffect(Unit)" in about
assert "ActivityResultContracts.OpenDocument()" in about
assert "MusicFlowUpdates.importVerified(context, uri)" in about
assert "MusicFlowBackgroundUpdates.enqueue(context, release)" in about
assert "MusicFlowUpdates.installerIntent(imported)" in about
assert "checkArchive(context, tmp, 0)" in update
assert "incoming.packageName != context.packageName" in update
assert "signatures(incoming)" in update
assert "Intent.ACTION_INSTALL_PACKAGE" in update
assert "android:icon=\"@mipmap/ic_launcher_graphite_v19\"" in manifest
assert "android:roundIcon=\"@mipmap/ic_launcher_graphite_v19\"" in manifest
assert '@drawable/ic_musicflow_graphite' in launcher
assert (root / "res/drawable-nodpi/ic_musicflow_graphite.webp").is_file()
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'versionName = "0.7.39"' in gradle
print("MusicFlow v0.7.19 banks, updates and restored graphite icon checks PASS")
