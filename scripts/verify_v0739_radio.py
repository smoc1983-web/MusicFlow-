#!/usr/bin/env python3
"""Static regression gate for v0.7.39 Russian radio catalog recovery."""
from pathlib import Path

root = Path("app/src/main/java/com/musicflow/app")
radio = (root / "RussianRadio.kt").read_text(encoding="utf-8")
main = (root / "MainActivity.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionCode = 739' in gradle and 'versionName = "0.7.39"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'applicationIdSuffix = ".dev"' in gradle
assert '6 to "Радио"' in main and 'RussianRadioScreen(' in main
assert 'hidebroken=false' in radio and 'hidebroken=true' not in radio
assert 'lastcheckok", 0) != 1' not in radio
assert 'isListingCandidate(item.optString("countrycode"), uuid, name)' in radio
assert '"/json/stations/bycountrycodeexact/RU?' in radio
assert '"/json/stations/search?countrycode=RU' in radio
assert 'if (found.isNotEmpty())' in radio
assert 'cachedStations(context)' in radio and 'saveCachedStations(context, fresh)' in radio
assert 'if (stations.isEmpty()) return' in radio
assert 'refreshIndex++' in radio
assert 'musicflow_radio_favorites' in radio
assert 'musicflow_radio_catalog_cache' in radio
print("PASS: Russian radio no longer excludes stations flagged broken by external servers")
print("PASS: mirror and catalog query fallback, persisted last-good list and favorites isolation")
print("PASS: signed-update package and 0.7.39 version identity preserved")
