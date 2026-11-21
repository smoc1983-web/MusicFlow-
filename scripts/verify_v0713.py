"""Static checks for MusicFlow 0.7.13 floating TV and theme migration."""
from pathlib import Path
src=Path("app/src/main/java/com/musicflow/app")
main=(src/"MainActivity.kt").read_text(encoding="utf-8")
tv=(src/"TvMiniPlayer.kt").read_text(encoding="utf-8")
app=(src/"MusicFlowAppearance.kt").read_text(encoding="utf-8")
full=(src/"FullPlayerDialog.kt").read_text(encoding="utf-8")
home=(src/"ZaycevHome.kt").read_text(encoding="utf-8")
manifest=Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gradle=Path("app/build.gradle.kts").read_text(encoding="utf-8")
assert "Floating TV controls do not occupy list height" in main
assert "Box(" in main and "statusBarsPadding()" in main and "navigationBarsPadding()" in main
assert "TvMiniPlayer(" in main and "onStop = {" in main
assert "detectDragGestures" in tv
assert "horizontalPosition" in tv and "verticalPosition" in tv
assert "freeX" in tv and "freeY" in tv
assert "expanded = false" in tv
assert "rememberSaveable" in tv
assert "onExpand: () -> Unit" in tv
assert "MusicFlowStyle.AB_ICY_NIGHT.name" in main
assert 'putString("ui_theme", selected.name)' in main
assert "LocalMusicFlowPalette provides palette" in main
for theme in ("AB_PEARL", "AB_ICY_NIGHT", "AB_LILAC", "AB_AMBER", "AB_EMERALD", "AB_OBSIDIAN", "AB_ONYX", "AB_BLACK_ICE", "AB_AMETHYST", "AB_FLAT_GRAPHITE"):
    assert theme+"(" in app
for removed in ("AURORA", "AMOLED", "MIDNIGHT", "GRAPHITE", "SOFT_LIGHT"):
    assert not ("    "+removed+"(") in app
assert "LocalMusicFlowPalette.current" in full
assert "LocalMusicFlowPalette.current" in home
assert '@mipmap/ic_launcher' in manifest
assert 'versionName = "0.7.29"' in gradle
assert Path("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").is_file()
assert Path("app/src/main/res/drawable/ic_musicflow_foreground.xml").is_file()
print("MusicFlow 0.7.13 themes, icon and floating player static checks PASS")
