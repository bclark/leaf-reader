---
name: deploy-play
description: "Prepare and publish a Leaf Android release: commit approved work, sync main, bump the version, write reader-friendly release notes and an iOS handoff, tag, push, and verify GitHub build artifacts. Use for /deploy-play or requests to ship a new Leaf Android build. Google Play Console uploads require a separately specified track and signing setup."
---

# Deploy Leaf Android

Treat this as Leaf's commit-to-tag release workflow. Carry out the publication requested by the user without repeating routine confirmations. If the user asks only for preparation or a draft, stop at that boundary. This workflow normally publishes to GitHub Releases; its name does not imply that Google Play Console is configured.

## Establish the release

- Read the current repository instructions, branch/status, diff, latest remote main, latest version/tag, `app/build.gradle.kts`, `fdroid/net.digitalbc.leafreader.yml`, and `.github/workflows/release.yml`.
- Fetch origin with tags before choosing a version. Preserve accepted in-progress work and coordinate unrelated changes rather than silently including or discarding them.
- Use Android Studio's `studio_*` workspace tools when available. Read/edit text through IDE tools so unsaved buffers remain authoritative. Use Studio shell for Git, Gradle, GitHub CLI, and existing skill validators; binary assets may be copied through the shell.
- Never read, print, stage, or publish `.env`, keystores, credentials, or signing material. Check ignored/tracked status without displaying contents.
- Pick the next unused version unless the user specified one. Keep versionCode increasing and consistent with the existing release series. For example, 0.3.5 used 30500 and 0.3.6 used 30600; do not apply a stale formula that lowers the published code.
- Update Android versionName/versionCode, the F-Droid build/tag and current version fields, and `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. Do not bump the iOS app version or publish an iOS tag unless requested.

## Make the release reviewable

- Write `docs/releases/v<version>.md` for people who read comics: describe visible improvements, where to find new controls, and practical limits. Avoid raw commit subjects, PR lists, internal class names, and claims for unimplemented features.
- Keep the fastlane changelog under 500 characters and focused on the same user benefits.
- Ensure the release workflow uses the checked-in notes as `body_path`, with generated PR notes disabled. Keep signing/install information accurate. Do not call unsigned APKs installable or an unsigned AAB Play-ready.
- Write or refresh `docs/IOS_ANDROID_<version-without-dots>_HANDOFF.md`. State what is implemented on Android, what iOS already has, exact interactions/constants/units, shared Kotlin APIs versus native UI responsibilities, relevant source files, migration and cancellation details, and concrete iOS acceptance checks. Keep proposed work distinct from completed work.

## Verify and commit

Run shared JVM tests first, then the relevant Android tests, both debug flavors, and lint, for example:

```bash
./gradlew :shared:jvmTest --stacktrace
./gradlew :app:testFullDebugUnitTest :app:assembleFullDebug :app:assembleFdroidDebug :app:lintFullDebug :app:lintFdroidDebug --stacktrace
```

If local Kotlin compilation stalls in temporary-file creation, this machine has previously worked with `--no-daemon -Djava.io.tmpdir=/private/tmp -Pkotlin.compiler.execution.strategy=in-process`; apply those flags to the invocation, not to committed Gradle configuration.

- Complete relevant required checks; verify actual release artifacts through the tag workflow. Native dependency changes also require the repository's alignment checks.
- Validate a changed copy of this skill with the skill-creator's `scripts/quick_validate.py` when available.
- Review `git diff --check`, the complete staged diff, and the staged file list. Stage explicit approved paths. Commit features/docs and release metadata with clear messages.

## Sync and publish

- Fetch again before merging. Switch to main only after the approved work is committed. Fast-forward main from origin, then merge the feature branch; prefer a fast-forward when possible. Resolve genuine conflicts against both sides' intent and rerun affected checks.
- Confirm the release tag does not already exist locally or remotely. Create an annotated `v<version>` tag at the final main commit. Never move an existing published tag.
- Push main and that tag to origin together with `git push --atomic origin main v<version>` when supported. A remote-advance rejection calls for fetching and reconciling, not force-pushing.
- Use connected GitHub tools where sufficient. Use `gh ... --repo bclark/leaf-reader` for missing Actions/release capabilities; no default CLI repository is assumed.
- Follow the tag-triggered Release workflow through completion. Verify the release URL, intended human-written body, tag SHA, full APK, F-Droid APK, AAB, and both R8 mapping files. Report signing from actual configuration/artifacts.
- If CI fails, inspect the failing job. Retry a transient infrastructure failure once. For a code or workflow failure, fix it and use a new patch tag rather than rewriting the published one. Report a persistent external blocker without retrying indefinitely.
- Check main's CI when it runs, including shared iOS compilation and the iOS app if touched. Distinguish Android release success from any outstanding cross-platform checks.
- Finish with the release link/version, concise check results, signing status, iOS handoff link, and local/remote sync status.

A real Google Play upload additionally needs explicit publication scope (app/track/rollout), compatible signing and credentials, and available upload tooling. Do not silently create or promote a production rollout as part of the GitHub tag workflow.
