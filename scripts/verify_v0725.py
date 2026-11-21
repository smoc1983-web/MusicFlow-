"""MusicFlow v0.7.25: VK search integrated in shared MusicFlow results, no separate tab."""
from pathlib import Path
root = Path("app/src/main/java/com/musicflow/app")
main = (root / "MainActivity.kt").read_text(encoding="utf-8")
vk = (root / "VkMusicScreen.kt").read_text(encoding="utf-8")
sources = (root / "MusicSources.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
updater = (root / "MusicFlowUpdates.kt").read_text(encoding="utf-8")
assert 'versionCode = 725' in gradle
assert 'versionName = "0.7.25"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
assert '0 to "Музыка", 2 to "Избранное", 3 to "ТВ")' in main
assert '5 to "VK Музыка"' not in main
assert 'tab == 5' not in main
assert 'vkInlineSearch by remember' in main
assert 'if (tab == 1 && vkInlineSearch)' in main
assert 'vkInlineSearch = true' in main
assert 'vkInlineSearch = false' in main
assert 'initialQuery = appliedSearch' in main
assert 'VK Музыка · искать «' in main
assert 'key(appliedSearch)' in main
assert 'initialQuery: String = ""' in vk
assert 'Uri.Builder().scheme("https").authority("vk.com").appendPath("search")' in vk
assert '.appendQueryParameter("c[q]", clean)' in vk
assert '.appendQueryParameter("c[section]", "audio")' in vk
assert 'loadUrl(requestedSearch)' in vk
assert 'approvedVkHost(target.host)' in vk
assert 'addJavascriptInterface' not in vk
assert 'evaluateJavascript' not in vk
assert '"vk" to' not in sources, "Do not pretend VK provides supported Media3 streams"
assert 'incoming.packageName != context.packageName' in updater
assert 'signatures(incoming)' in updater
print("MusicFlow v0.7.25 unified VK search integration PASS")
