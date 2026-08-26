# Releasing Aster Mail for Android

One command cuts a release to every channel:

```
bash scripts/release.sh 0.6.156
```

Add `--dry-run` to build, sign, and verify without pushing or publishing anything.

## Before you run it

1. Write the Play changelog to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and
   commit it on `main`. The hard cap is 500 bytes, enforced by `validate-fastlane-supply-metadata`.
2. Write the GitHub release notes to `../.release_work/notes-<version>.md`. Start with the standard
   install alert, then group under `## What's new` and `## Fixed`.

## What the script does

| Step | Detail |
|---|---|
| CI gate | Refuses to start if the latest checks on `origin/main` are failing. Override with `ASTER_SKIP_CI_CHECK=1` only when you know why. |
| Clean clone | Clones `origin/main` into `../.release_work`. Your working tree is never packaged, so another session's uncommitted edits can't reach an APK. |
| Bump | Sets `versionName` and `versionCode` in `app/build.gradle.kts`, commits, tags. |
| Build | `assembleFullRelease bundleFullRelease` first, then `assembleFdroidRelease` in a **separate** gradle invocation. |
| Sign | Signs the fdroid APK with `apksigner --alignment-preserved`. Never runs `zipalign`. |
| Verify | Both APKs must carry cert SHA-256 `88b0a8a6…`, and the fdroid APK must have zero `0xd935` padded entries. |
| Publish | Creates the GitHub release with all three APK names, then re-uploads `Aster-Mail.apk` to the current Aster-Mail **Latest** release, which is what astermail.org serves. |
| Play | Uploads the AAB with `fastlane supply` if a service account is configured, otherwise copies the AAB to `~/Downloads` and says so. |
| Audit | Runs `Claude/scripts/audit_android_channels.sh` and prints the per-channel result. |

Why two gradle invocations: `is_fdroid_build` is true when **any** task name contains `fdroid`, so
`assembleFullRelease assembleFdroidRelease` in one call silently strips signing from the full flavor.

## Why signing is local and not in CI

`Aster-Privacy/Aster-Android` is a public repo, and the signing key is the app's identity. Android
refuses an update whose signer changed, so a leaked or rotated key strands every existing user with
no in-place upgrade. The keystore stays on the release machine. CI builds unsigned with
`ASTER_UNSIGNED=1` and needs no secrets.

## Automating the Play upload

The only step still manual is the Play rollout, because it needs a credential that has to be created
in the console by hand. To finish it:

1. In Play Console, go to **Setup > API access**, then create or link a Google Cloud service account.
2. Grant it the **Release manager** role, limited to `org.astermail.android`.
3. Create a JSON key for it and save it as `.ops/play_service_account.json`. That path is already
   excluded from the `Claude` sync repo and must never be committed.
4. `gem install fastlane`.

The script picks it up automatically on the next release. Override the path with
`ASTER_PLAY_SERVICE_ACCOUNT_JSON` if you keep it elsewhere.

## F-Droid

F-Droid is the one channel nobody can fully automate from here, because publishing happens in
`gitlab.com/fdroid/fdroiddata`, which F-Droid maintainers control.

What is automated: the recipe is on `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`, so once
it is merged, F-Droid's bot picks up each new tag on its own and no per-release merge request is
needed. That only works if every tag carries `Aster-Mail-fdroid-<version>.apk` signed with our key,
which `release.sh` guarantees and `verify_release_assets.yml` double-checks.

Until MR !40463 merges, the app is not on F-Droid at all and no release can reach that channel.
Check the state with:

```
curl -s https://f-droid.org/api/v1/packages/org.astermail.android
```
