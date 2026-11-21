# MusicFlow development instructions (persistent)

Project: MusicFlow Android / Kotlin Compose / Media3
Permanent skill: **ub-mode** from https://github.com/unclebob/ub-mode
Installed copy: `.claude/skills/ub-mode/SKILL.md` (MIT, attribution retained).

## Required process for EVERY future MusicFlow code modification
1. **Tell the user, before any Android build, that ub-mode is being applied** and which quality checks will run. Never call a build "passing" until verified.
2. **First Make it Work**: establish the behavior, define data boundaries, simplify the design, write a failing behavioral test, and make the smallest focused code change.
3. **Then Make it Right**: correct root causes; refactor only after tests prove behavior, use static analysis and behavioral tests. Run CRAP (<10), Dryer (<0.8) and mutation quality gates **when the tools actually support the project's language**. Report unsupported gates instead of claiming success.
4. **Practice**: keep an action log with the reason for changes, make small reviewable commits, run CI and review the resulting APK. Record verified evidence.
5. Preserve MusicFlow's identity `com.musicflow.preview` and original release signing certificate. Never commit signing passwords or raw keystores.
6. Do not import the GPL-3.0 SimpMusic implementation into MusicFlow. Independently implement narrowly scoped functions and note the original project as inspiration. Respect third-party terms.
7. Keep VK, Audius, and Jamendo out of MusicFlow unless the user explicitly reverses that decision.
8. **Publication gate**: Before a new GitHub Release, show the user a summary of functionality, CI evidence, unresolved limitations and signed-package verification. Publish only when authorized.
9. Inspect the latest project branch and release first; do not assume `main` contains the latest changes.

## Report format
- Behavior verified, tests run, failures found and corrected.
- Source branches and commit IDs.
- Which CRAP, duplication and mutation tools ran, or why they could not.
- Explicit note that **ub-mode** was applied.
