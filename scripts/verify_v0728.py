"""0.7.28: personal VK web login and first-party playback, no credential harvesting."""
from pathlib import Path

src = Path("app/src/main/java/com/musicflow/app")
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
portal = (src / "VkMusicPortal.kt").read_text(encoding="utf-8")
native = (src / "VkAudioSource.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
manifest = Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
guidance = Path("VK_LOGIN_AND_PLAY_PROTECT_RU.md").read_text(encoding="utf-8")

assert 'versionCode = 728' in gradle and 'versionName = "0.7.28"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'androidx.browser:browser:1.8.0' in gradle
assert 'https://vk.com/audio' in portal and 'https://vk.com/search' in portal
assert 'CustomTabsIntent.Builder()' in portal
assert 'appendQueryParameter("c[q]", requested)' in portal
assert 'appendQueryParameter("c[section]", "audio")' in portal
assert "fun openMyMusic" in portal and "fun openSearch" in portal
assert 'VK Музыка · войти и слушать' in main
assert 'Войти в VK · моя музыка' in main
assert 'Моя музыка VK' in main and 'Найти во VK' in main
assert 'VkMusicPortal.openMyMusic(context)' in main and 'VkMusicPortal.openSearch(context, appliedSearch)' in main
assert 'PasswordVisualTransformation()' in main  # authorized native API token only
assert 'VkAudioSource.saveToken(vkTokenInput)' in main
assert 'VkAudioSource.initialize(this)' in main
assert 'audio.search' in native and 'AndroidKeyStore' in native
assert 'REQUEST_INSTALL_PACKAGES' in manifest  # required for in-app official updates
assert 'Play Защита' in guidance and 'нельзя' in guidance
assert 'VK ID' in guidance and 'не даёт' in guidance
assert 'WebView' not in portal and 'CookieManager' not in portal
assert 'getPassword' not in portal and 'getCookie' not in portal
print("VK account portal, authorized native source and Play Protect guidance PASS")
