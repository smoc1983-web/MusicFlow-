"""Static checks for MusicFlow graphite icon and palette-aware mini-player."""
from pathlib import Path
root = Path("app/src/main")
main = (root / "java/com/musicflow/app/MainActivity.kt").read_text(encoding="utf-8")
theme = (root / "java/com/musicflow/app/MusicFlowAppearance.kt").read_text(encoding="utf-8")
launcher = (root / "res/mipmap-anydpi-v26/ic_launcher.xml").read_text(encoding="utf-8")
colors = (root / "res/values/icon_colors.xml").read_text(encoding="utf-8")
asset = root / "res/drawable-nodpi/ic_musicflow_graphite.webp"
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
assert asset.is_file() and asset.stat().st_size > 1500
raw = asset.read_bytes()
assert raw[:4] == b"RIFF" and raw[8:12] == b"WEBP"
assert '@drawable/ic_musicflow_graphite' in launcher
assert '#101214' in colors
assert "musicFlowGlass(palette, radius = 18.dp)" in main
assert "trackColor = palette.accent.copy(alpha = 0.23f)" in main
assert "Color(0xFF28243B), Color(0xFF191D2B)" not in main
assert "Color(0xFF3F3550)" not in main
assert "Text(trackArtist, color = palette.muted" in main
assert "Text(playbackStatus.orEmpty(), color = palette.accent" in main
assert "MusicFlowStyle.AB_ICY_NIGHT" in theme
assert "MusicFlowStyle.AURORA" not in theme
assert 'versionName = "0.7.22"' in gradle
print("MusicFlow v0.7.17 graphite launcher and mini-player checks PASS")
