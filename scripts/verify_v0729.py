"""MusicFlow 0.7.35: retired VK provider, Play Protect static audit and native sound."""
from pathlib import Path
import re

root = Path("app/src/main/java/com/musicflow/app")
main = (root / "MainActivity.kt").read_text(encoding="utf-8")
sources = (root / "MusicSources.kt").read_text(encoding="utf-8")
player = (root / "PlaybackService.kt").read_text(encoding="utf-8")
eq = (root / "NativeAudioEqualizer.kt").read_text(encoding="utf-8")
ui = (root / "MusicFlowEqualizerDialog.kt").read_text(encoding="utf-8")
updater = (root / "MusicFlowUpdates.kt").read_text(encoding="utf-8")
auto = (root / "MusicFlowAutoUpdate.kt").read_text(encoding="utf-8")
manifest = Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
readme = Path("README.md").read_text(encoding="utf-8")

assert not (root / "VkAudioSource.kt").exists()
assert not (root / "VkMusicPortal.kt").exists()
assert "VkAudioSource" not in main and "VkMusicPortal" not in main
assert not re.search(r"vkSourceError|vkConfigured|showVkConfiguration|vkTokenInput|vkPortalMessage", main)
assert "VK Музыка" not in main and "Найти во VK" not in main
assert '"vk" to ::vkTracks' not in sources
assert '"vk" -> VkAudioSource' not in sources
assert all(f'"{source}" to ::' in sources for source in ("zaycev", "muzofond", "mailru"))
assert 'deleteSharedPreferences("musicflow_vk_private")' in main
assert "com.musicflow.app.vk.audio.credentials.v1" in main
assert 'id.startsWith("vk:")' in main  # legacy favorites can use non-VK fallback
assert 'versionCode = 735' in gradle and 'versionName = "0.7.35"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'applicationIdSuffix = ".dev"' in gradle
assert 'androidx.browser:browser:' not in gradle
assert "ExoPlayer.Builder(this)" in player
assert 'val eq = NativeAudioEqualizer(this)' in player
assert "player.addListener(eq)" in player and "audioEqualizer?.release()" in player
assert "override fun onAudioSessionIdChanged" in eq
assert "android.media.audiofx.Equalizer" in eq
assert "android.media.audiofx.LoudnessEnhancer" in eq
assert "SharedPreferences.OnSharedPreferenceChangeListener" in eq
assert "PREFS = " in eq and "musicflow_eq_v1" in eq
assert "fun gainAtFrequency(" in eq and "ln(f)" in eq
assert "0f, 3f" in eq and "disabled by default" in eq.lower()
assert '"Звук · эквалайзер"' in main and "MusicFlowEqualizerDialog" in main
assert 'Text("Басы")' not in ui  # presets come from native stored values
assert "AudioEqSettings.presets" in ui and "AudioEqSettings.labels" in ui
assert "AudioEqSettings.save(context, next)" in ui
assert "MusicFlowUpdates.latestRelease()" in auto
assert "checkArchive(context, temp, update.versionCode)" in updater
assert "signatures(incoming)" in updater
assert "REQUEST_INSTALL_PACKAGES" in manifest
assert 'targetSdk = 35' in gradle

permissions = re.findall(r'<uses-permission\s+android:name="android.permission.([^"]+)"', manifest)
expected = {
    "INTERNET", "ACCESS_NETWORK_STATE", "REQUEST_INSTALL_PACKAGES",
    "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "WAKE_LOCK", "WAKE_WIFI_LOCK",
}
assert set(permissions) == expected, f"Unexpected Android permission: {permissions}"
assert 'setWakeMode(C.WAKE_MODE_NETWORK)' in player
assert "Audius" in readme and "Play Protect" in readme
print("PASS: VK UI, source files, tokens and provider removed")
print("PASS: user settings dialog and ExoPlayer audio-session effects wired")
print("PASS: bounded Android permissions, Media3-managed screen-off wake locks and stable package ID")
print("NOTE: this cannot guarantee Google Play Protect will not warn")
