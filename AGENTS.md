# MusicFlow development instructions (persistent)

Project: MusicFlow Android / Kotlin Compose / Media3
Permanent skill: **ub-mode** from https://github.com/unclebob/ub-mode
Installed copies for Codex and other agents: `.agents/skills/ub-mode/SKILL.md` and `.claude/skills/ub-mode/SKILL.md` (MIT, attribution retained).

## Required process for EVERY future MusicFlow code modification
1. **Tell the user, before any Android build, that ub-mode is being applied** and which quality checks will run. Never call a build "passing" until verified.
2. **First Make it Work**: establish the behavior, define data boundaries, simplify the design, write a failing behavioral test, and make the smallest focused code change.
3. **Then Make it Right**: correct root causes; refactor only after tests prove behavior, use static analysis and behavioral tests. Run CRAP (<10), Dryer (<0.8) and mutation quality gates **when the tools actually support the project's language**. Report unsupported gates instead of claiming success.
4. **Practice**: keep an action log with the reason for changes, make small reviewable commits, run CI and review the resulting APK. Record verified evidence.
5. Preserve MusicFlow's identity `com.musicflow.preview` and original release signing certificate. Never commit signing passwords or raw keystores.
6. Do not import the GPL-3.0 SimpMusic implementation into MusicFlow. Independently implement narrowly scoped functions and note the original project as inspiration. Respect third-party terms.
7. Keep VK, Audius, and Jamendo out of MusicFlow unless the user explicitly reverses that decision.
8. **Publication gate / standing authorization**: The owner has authorized the STANDARD automatic MusicFlow `main` workflow for new official versions. Do not request separate approval for each ordinary `main` release. Publish only when the version is new, Kotlin tests pass, the package is `com.musicflow.preview`, and the APK is verified with the original signing certificate. Never overwrite an existing release or automatically publish test/debug APKs. Report functionality, CI evidence, signed-package verification, limitations, and any failed steps. For extraordinary releases, channels or destructive operations, obtain explicit authorization.
9. Inspect the latest project branch and release first; do not assume `main` contains the latest changes.
10. **Mandatory permanent change journal:** Read `DEVELOPMENT_JOURNAL.md` before work, and append an entry for every meaningful code/configuration/CI/test/build/signing/release change in the same work cycle. Every entry needs date, branch/version, reason, changes, commit/PR/run links, actual test/build outcome, errors, and next step. Never mark pending tests or publications as passed; append corrections as new entries, never silently rewrite history.
11. **Automatic official workflow**: All future changes to MusicFlow application/build sources merged into `main` MUST run `.github/workflows/release-signed-musicflow.yml` automatically on `push`: run Kotlin tests for the main package, and when the version is not already published, restore the original encrypted JKS on CI, sign/verify and publish the official update if all gates pass. Do not trigger an extra release for documentation, journal, or workflow-only edits. A user-started manual run is not to be duplicated. Keep test builds separate; a failing check MUST block publication. Log every actual run outcome in `DEVELOPMENT_JOURNAL.md` when it becomes available; never infer success from triggering a workflow.

12. **Canonical release channel playbook (MANDATORY):** Before EVERY MusicFlow official or TEST build, signing or release, read `docs/RELEASE_CHANNELS_RU.md` and latest `DEVELOPMENT_JOURNAL.md`. Keep official package `com.musicflow.preview`, original certificate SHA-256 `c461ee635d5b295ef6c765e1ae533d0079dc6e53cb2632a11a1b445773752410`, numeric official tag `vX.Y.Z`, non-prerelease `MusicFlow_X.Y.Z.apk`; TEST must use a distinct package/test signing key, distinguishable `-test` prerelease tag and TEST APK name. If GitHub signing Secrets are missing, use the previously proven private local original-JKS -> validated signed-byte-delta -> gated publisher workflow instead of switching to a test key or repeatedly rerunning the failing signer. Check exact source run, artifact SHA-256, full Git history (`fetch-depth: 0`), certificate and GitHub Release asset SHA. Never commit passwords, plaintext JKS, personal signing source files, or assume a passed unsigned build is a published update. Always log verified outcomes and Android installation feedback.

13. **PERMANENT SIGNING KEY LOCATIONS (OWNER CONFIRMED 2026-11-27):** Before each OFFICIAL or TEST release, read `main/docs/SIGNING_KEYS_REGISTRY_RU.md` first. OFFICIAL encrypted JKS is in `main/.github/signing/MusicFlow_signing_release.jks.enc.b64` and private credentials in ChatGPT Library `/MusicFlow_Private_OfficialSigning/`; TEST encrypted P12 and credentials in private Library `/MusicFlow_Private_TestSigning/`. Both decrypt with AES-256-CBC PBKDF2 350000; the official and TEST certs are different. Do not regenerate signing keys or expose secrets. Check package, version, original certificate and logs, then append accurate results to `DEVELOPMENT_JOURNAL.md`.

## Report format
- Behavior verified, tests run, failures found and corrected.
- Source branches and commit IDs.
- Which CRAP, duplication and mutation tools ran, or why they could not.
- Explicit note that **ub-mode** was applied.

## Быстрый вход в проект

Начинай с конца DEVELOPMENT_JOURNAL.md в рабочей ветке. Для TEST 0.7.61 рабочая ветка — feature/musicflow-quality-sizes-v0761. Для других версий уточняй ветку по последней записи соответствующего канала. Сначала смотри список изменённых путей и нужный экран, тест или workflow. Исторические ветки, двоичные патчи и материалы подписи открывай только для задач, которым они нужны. Записывай в журнал ветки также незавершённые наработки, фактические проверки и оставшиеся вопросы.
