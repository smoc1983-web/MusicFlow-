# MusicFlow Cinema Native TEST v3

**Only a separate test build.** Source branch: `feature/musicflow-cinema-native-sources-test-v3`.

- Application ID: `com.musicflow.preview.cinematest3`.
- Label: **MusicFlow Кино Native TEST**.
- Previous official MusicFlow `com.musicflow.preview`, Pro, and other Cinema test packages are not modified.
- The test must **not** be signed with production keys or posted to the public update feed.

## New behavior

The Cinema screen **does not open a separate Lampa window**. Video plays within the Cinema page through its own embedded **Media3 ExoPlayer**. Selecting another video releases the previous ExoPlayer instance. Media3 progress and error recovery are retained.

Use **Кино → + Источник**. The native source manager recognizes:

1. **Cinema JSON v1** manifest containing `catalog_url`, or a raw `{"items":[...]}` JSON catalog.
2. **M3U channel/video playlist**, containing `#EXTINF` entries followed by HTTPS stream URLs.
3. **Direct HTTPS** MP4, HLS/M3U8, DASH/MPD, WebM or MKV URLs, which Media3 attempts to play if compatible.
4. **Lampa JavaScript plugin URLs**, such as `https://bwa.ad/rc`, **as inactive references**. A Lampa JS file is not a catalogue of direct streams, so importing its address alone cannot make its arbitrary plugin API callable from the native Media3 player. It is clearly marked **«нужен адаптер»** and is *not* treated as an available video source.

All content from playable sources appears as ordinary movie cards in the Cinema app. Source tabs let the user choose catalog. User favorites, local watch-later, recent titles and progress are managed within this test installation.

The old Lampa WebView screen and modal player are removed from this branch. Only a native inline Media3 video component remains.

### Test media sources

**Offline media:** a locally generated original 8-second diagnostic MP4 `asset:///cinema_lab_test.mp4` is packaged in the APK. This source never depends on HTTP 403/404.

**Native M3U fixture:**
`https://raw.githubusercontent.com/smoc1983-web/MusicFlow-/feature/musicflow-cinema-native-sources-test-v3/cinema-test/native-demo.m3u`

**JSON fixture:**
`https://raw.githubusercontent.com/smoc1983-web/MusicFlow-/feature/musicflow-cinema-native-sources-test-v3/cinema-test/catalog.json`

The remote M3U references the publicly advertised HLS test stream `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`. External streams may still fail in individual networks or fail to grant playback authorization. We do not bypass access controls.

### Test on Samsung S24

1. Install the separate APK, open **MusicFlow Кино Native TEST**.
2. Press **Проверка видео · офлайн** — should play *within the Cinema page*.
3. Press **+ Источник** → **M3U демо** → **Добавить в каталог**. Select the new source tab and try its HLS stream using the same native player.
4. Press **+ Источник** → **JSON демо** → **Добавить**. Select its source tab and inspect titles.
5. Optionally paste `https://bwa.ad/rc`. It should be stored as a **reference only, not as a falsely advertised playable Lampa provider**.
6. Verify favorites, back navigation, recent items, and that official MusicFlow 0.7.40 remains untouched.

## Limitations

- Generic Lampa JS providers (BwaRC, Online Mod and similar) use `window.Lampa` and extension APIs, not a universal video URL endpoint. Direct native compatibility is *not* implemented without dedicated per-provider adapters and openly available permitted video APIs. The app no longer launches an external Lampa browser, as requested.
- No paid provider login/DRM, no bypass of subscriptions, no pirated catalog integrations, no background downloading.
- HTTPS URLs only for playable sources; no redirects when fetching remote playlist/JSON; bounded response size and playlist item count.
- Tests cover stream parsing, URL safety, and nonexecution of JS plugin references.
