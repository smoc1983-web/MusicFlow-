# MusicFlow TV Plugin v1 · third-party source manifest

This version adds **three selectable TV layouts** (Overview, Live, My TV) and preserves the Classic layout. All layouts share the existing Media3 TV player and the same RuVideoHub/user M3U sources. Preferences, watchlist, favorites, and recently viewed channels are device-local.

## Third-party plugin capability

A plugin is a **declarative JSON manifest**, not executable JavaScript, APK, Dex, or arbitrary Kotlin code. The app will never run script from a third-party URL.

Example manifest hosted over public HTTPS:

```json
{
  "format": "musicflow.tv-plugin.v1",
  "name": "My legal TV package",
  "playlist_url": "https://example.org/public/channels.m3u",
  "epg_url": "https://example.org/public/guide.xml",
  "homepage": "https://example.org"
}
```

`format`, `name`, and `playlist_url` are required. `epg_url` and `homepage` are optional. All links must be public HTTPS; credentials, private-network addresses, plaintext HTTP, and redirects are not accepted for the manifest/EPG retrieval.

To connect: **ТВ → Обзор / Эфир / Моё → + Плагин → Плагин JSON**. Paste the direct HTTPS address of the plugin JSON and choose a name. The app fetches the manifest and reads the authorized M3U playlist. If `epg_url` is present, it also reads bounded XMLTV and shows actual program titles/time slots for channels where `tvg-id` matches the XMLTV `channel`.

The app does not grant content licenses. Add only streams you are authorized to access. Plugins cannot bypass broadcaster access controls or perform remote code execution.

## Functional design and limits

- **Обзор**: selected channel hero, popular channels, favorite rails, recent channels, genre-based suggestions.
- **Эфир**: quick access, list of live channels, current show when EPG exists, and guide.
- **Моё**: favorites, locally stored watchlist and recent channels, related-source suggestions.
- **Классика**: original TV interface and existing source add/delete flow, unchanged as a fallback.
- Import existing M3U/M3U8 and custom channels is preserved; package `com.musicflow.preview` is unchanged.
- The initial TV mockups depicted movies and fictional series; these images are **not a claim of licensed VOD availability**.
- **Recording, cloud DVR, offline TV downloads, cross-device sync, AI recommendation backends, casts, and parental profiles are NOT implemented**. None is represented as available in the production UI.
- EPG requires a plugin with a valid XMLTV feed; source may be too large or temporarily unavailable. If EPG is missing, the UI explicitly says so.
- No change to MusicFlow Pro tests or Pro branches.

### Security boundaries

Manifest size 32 KB, XMLTV max 3 MB, no network redirects for manifests, parser rejects DTD and external entities, scripts and code rejected. Direct playlist URL is validated before it is fetched. Third-party streams and playlist origin remain untrusted; users must explicitly add sources. There are no downloaded binaries, no arbitrary code execution and no new Android permissions.

### ub-mode

Behavioral tests cover mode availability, genre selection, private URL rejection, XMLTV time and parsing. Android CI runs full regression scripts and `testDebugUnitTest` then assembles. Unsupported external CRAP/Dryer/mutation gates are not claimed to have been run.
