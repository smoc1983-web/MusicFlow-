# Cinema scroll regression — MusicFlow 0.7.47 TEST (ub-mode)

Baseline: officially signed v0.7.46. The test is a **separate debug package** `com.musicflow.preview.cinemascrolltest47`; it cannot replace the official MusicFlow application or access that application's private data.

## First make it work

1. Reproduced the triggering state transition from existing Compose code: before RUTUBE shelves or search results arrive, the `LazyColumn` contains only an empty-message item and the permanently keyed `rights` footer. On asynchronous data insertion above the footer, Compose may retain the footer's visible-item key and consequently scroll to the bottom of the new result list.
2. Found existing `scrollToItem(0)` calls in `LaunchedEffect` and in the search coroutine. They race with the next Compose item measurement and cannot reliably prevent key-based anchor retention.
3. Added `CinemaCatalogScrollPolicyTest.kt` **before** the fix, specifying reset-on-content-arrival, reset-on-query/section/source/shelf change, and **no reset on unchanged pagination**.

## Then make it right

4. Added `CinemaCatalogScrollPolicy.kt`, which returns a stable view identity containing section/query/source/shelf and an `empty`, `searching` or `ready` content phase.
5. Wrapped the cinema catalogue `LazyColumn` in `key(catalogScrollIdentity)` and moved `rememberLazyListState()` inside. Each changed identity initializes a list at item zero *in the composition that receives the changed data*, not in a coroutine before layout. Ordinary extra pages retain the same identity.
6. Removed obsolete `scrollToItem(0)` calls. Did not change media catalogues, stream access, episode lookup or unrelated screens.

## Verification and package isolation

- GitHub Actions workflow: `.github/workflows/musicflow-v0747-cinema-scroll-test.yml`.
- Required verification: source wiring assertions; full `:app:testDebugUnitTest` including `CinemaCatalogScrollPolicyTest`; `:app:assembleDebug`; Android `aapt` package name and version; `apksigner verify`.
- Debug APK is labeled `MusicFlow Кино TEST 0.7.47`, with versionCode 747; installed production package remains `com.musicflow.preview` (v0.7.46).
- Actual scrolling with an Android display requires device testing; JVM tests verify state-transition policy, not pixel location. Test on launch, entering cinema from another tab, typing a title, receiving results, clearing search, and paging within an already populated result set.
- CRAP, Dryer and Mutator tooling does not support Kotlin in this repository; not run and no fabricated metrics.
- No GitHub Release or signed production update is authorized in this request.
