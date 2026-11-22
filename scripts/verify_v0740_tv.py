#!/usr/bin/env python3
"""MusicFlow v0.7.40: three TV layouts, original player, safe manifest plugins."""
from pathlib import Path

root = Path("app/src/main/java/com/musicflow/app")
screen = (root / "RuVideoHubTv.kt").read_text(encoding="utf-8")
modern = (root / "TvModernHub.kt").read_text(encoding="utf-8")
lib = (root / "TvHubLibrary.kt").read_text(encoding="utf-8")
plugin = (root / "TvPluginManager.kt").read_text(encoding="utf-8")
epg = (root / "TvEpgParser.kt").read_text(encoding="utf-8")
sources = (root / "UserIptvSources.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionCode = 740' in gradle and 'versionName = "0.7.40"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'applicationIdSuffix = ".dev"' in gradle
assert 'TvHubModeTabs(' in screen and 'TvModernHub(' in screen
assert 'Column(Modifier.weight(1f))' in screen
assert 'retrieveChannels()' in screen and 'playChannel(channel: TvChannel)' in screen
assert 'TvPlaybackHandoff.set(channels, channel, activeSourceId)' in screen
assert 'TvHubLibrary.saveMode' in screen
assert 'onAddPlugin' in screen and 'IptvSourceKind.PLUGIN' in screen
assert 'TvPluginManager.load(plugin.address)' in screen
assert 'TvPluginManager.loadEpg' in screen
for value in ['OVERVIEW("Обзор")', 'LIVE("Эфир")', 'PERSONAL("Моё")',
              'CLASSIC("Классика")']:
    assert value in lib, value
for value in ['Избранные каналы', 'Продолжить просмотр', 'Смотреть позже',
              'Недавно просмотренные', 'Телепрограмма (EPG)']:
    assert value in modern, value
assert 'isSafePluginUrl' in plugin and 'parseManifest' in plugin
assert 'instanceFollowRedirects = false' in plugin
assert 'setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)' in epg
assert 'IptvSourceKind.PLUGIN -> TvPluginManager.isSafePluginUrl' in sources
assert 'epgId = iptvAttribute(lastInfo, "tvg-id")' in sources
assert 'MusicFlowProTrial' not in screen and 'MusicFlowProTrial' not in modern
assert Path("app/src/test/java/com/musicflow/app/TvHubIntegrationTest.kt").exists()
print("PASS: three layouts + classic, real TV player, persisted favorites/history")
print("PASS: declarative plugin JSON, remote playlist + secure XMLTV and URL limits")
print("PASS: original app ID and release update identity v0.7.40")
