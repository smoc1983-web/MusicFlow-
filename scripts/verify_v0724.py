"""MusicFlow 0.7.24: official VK Music source + signed update compatibility checks."""
from pathlib import Path

root = Path("app/src/main")
src = root / "java/com/musicflow/app"
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
vk = (src / "VkMusicScreen.kt").read_text(encoding="utf-8")
manifest = (root / "AndroidManifest.xml").read_text(encoding="utf-8")
updates = (src / "MusicFlowUpdates.kt").read_text(encoding="utf-8")
auto = (src / "MusicFlowAutoUpdate.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")

assert '0 to "Музыка", 2 to "Избранное", 3 to "ТВ", 5 to "VK Музыка"' in main
assert '5 -> "VK Музыка"' in main
assert 'VkMusicScreen(modifier = Modifier.weight(1f))' in main
assert "if (index == 5) player?.pause()" in main
assert 'if (tab != 5 && trackTitle.isNotBlank()' in main
assert 'VK_MUSIC_HOME = "https://music.vk.com/"' in vk
assert 'VK_MUSIC_PACKAGE = "com.uma.musicvk"' in vk
assert '<package android:name="com.uma.musicvk"' in manifest
assert "AndroidView(" in vk and "WebView(ctx)" in vk
assert "settings.javaScriptEnabled = true" in vk
assert "settings.domStorageEnabled = true" in vk
assert "settings.allowFileAccess = false" in vk
assert "approvedVkHost(target.host)" in vk
assert "shouldOverrideUrlLoading" in vk
assert "onReceivedError" in vk
assert "getLaunchIntentForPackage(VK_MUSIC_PACKAGE)" in vk
assert "addJavascriptInterface" not in vk
assert "onInterceptRequest" not in vk
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'versionName = "0.7.24"' in gradle
assert "versionCode = 724" in gradle
assert "MUSICFLOW_SIGNING_KEYSTORE" in gradle
assert "incoming.packageName != context.packageName" in updates
assert "signatures(incoming)" in updates
assert "MusicFlowUpdates.latestRelease()" in auto
assert "MusicFlowUpdates.downloadVerified(context, update)" in auto
assert "MusicFlowUpdates.installerIntent(uri)" in auto
print("MusicFlow 0.7.24 VK Music + in-app update regression PASS")
