"""v0.7.26: VK as a genuine optional native Media3 search/playback source."""
from pathlib import Path

src = Path("app/src/main/java/com/musicflow/app")
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
sources = (src / "MusicSources.kt").read_text(encoding="utf-8")
vk = (src / "VkAudioSource.kt").read_text(encoding="utf-8")
service = (src / "PlaybackService.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
assert "versionCode = 726" in gradle and 'versionName = "0.7.26"' in gradle
assert '"vk" to ::vkTracks' in sources
assert 'VkAudioSource.initialize(this)' in main
assert 'VkAudioSource.saveToken(vkTokenInput)' in main
assert 'VkAudioSource.testAccess()' in main
assert 'VkAudioSource.disconnect()' in main
assert 'PasswordVisualTransformation()' in main
assert 'VkMusicScreen(' not in main
assert 'vkInlineSearch' not in main
assert not (src / "VkMusicScreen.kt").exists()
assert "VK" in main and 'track.id.startsWith("vk:")' in main
assert 'MediaItem.Builder()' in main and '.setUri(t.stream)' in main
assert 'ExoPlayer.Builder(this)' in service
assert 'AES/GCM/NoPadding' in vk and 'AndroidKeyStore' in vk
assert 'audio.search' in vk and '"url"' in vk
assert 'audio_api_unavailable' in vk and 'if (!isPlayableUrl(url)) continue' in vk
assert 'VkAudioSource.isPlayableUrl(it.uri)' in sources
assert 'if (source == "vk")' in main
assert 'getSharedPreferences(PREFERENCES' in vk
assert '"access_token" to token' in vk
assert 'VKAndroidApp' not in vk
assert 'CookieManager' not in vk and 'WebView' not in vk
print("MusicFlow 0.7.26 VK native provider wiring PASS (not live VK access)")
