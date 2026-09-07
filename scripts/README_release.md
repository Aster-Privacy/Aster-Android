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
| Build | `assembleFullRelease bundleFullRelease`, then `assembleFdroidRelease` in a second gradle call. The two calls are separate on purpose, see below. |
| Sign | Signs the unsigned fdroid APK with `apksigner` and `--alignment-preserved`, never `zipalign`. |
| Verify | Both APKs must carry cert SHA-256 `88b0a8a6…`, the fdroid APK must have no `0xd935` alignment padding, and its dex must reference no Google Play Services, Firebase, or Play classes. |
| Publish | Creates the GitHub release with `Aster-Mail.apk`, `Aster-Mail-<version>-full.apk`, and `Aster-Mail-fdroid-<version>.apk`, then re-uploads `Aster-Mail.apk` to the current Aster-Mail **Latest** release, which is what astermail.org serves. |
| Play | Uploads the AAB with `fastlane supply` if a service account is configured, otherwise copies the AAB to `~/Downloads` and says so. |
| Audit | Runs `Claude/scripts/audit_android_channels.sh` and prints the per-channel result. |

## Asset names

Every release carries exactly three APKs, all signed with the same release key, and the name
states which build each one is:

| Asset | What it is |
|---|---|
| `Aster-Mail.apk` | Fixed name, `full` flavor. `releases/latest/download/Aster-Mail.apk` always resolves to the newest build, so the website and the in-app download link keep working. Never rename it. |
| `Aster-Mail-<version>-full.apk` | The same bytes as `Aster-Mail.apk`, pinned to one version. The `-full` suffix names the product flavor. |
| `Aster-Mail-fdroid-<version>.apk` | The `fdroid` flavor, built from the same tag and signed with the same key. This exact name is what the F-Droid recipe fetches. |

Never publish a bare `Aster-Mail-<version>.apk`: with two flavors on every release, an unflavored
name is ambiguous. `verify_release_assets.yml` fails a release that carries that name or is missing
any of the three above.

## The F-Droid contract

The recipe in `fdroiddata` (`metadata/org.astermail.android.yml`, merged 2026-09-06) pins
`AllowedAPKSigningKeys` to our certificate and fetches the fdroid APK from the tag through
`Binaries:`. For each new tag, F-Droid rebuilds the `fdroid` flavor from the tagged source on their
buildserver, copies our signature onto their build, and publishes our APK only when the two match
byte for byte. If the asset is missing, their build fails and the failure is published; if the
signer differs from the pinned certificate, they reject the binary.

That contract sets three hard rules for every release:

- Every tag must carry `Aster-Mail-fdroid-<version>.apk`, signed with
  `keystore/aster-mail-upload-v3.jks`. Cert SHA-256 `88b0a8a6…` is pinned in the recipe and is the
  app's identity on every channel, so the key is never rotated.
- The fdroid APK must be reproducible from the tagged source. Build it from the clean clone at the
  tag, with nothing in `core-crypto/src/main/jniLibs`, and never apply any post-processing.
- Never `zipalign` the fdroid APK. Build-tools 35 and newer re-align during signing by default and
  rewrite zip padding into `0xd935` extra fields that F-Droid's rebuild does not have, so the signed
  byte ranges no longer match. The script passes `--alignment-preserved` whenever build-tools is not
  34.0.0 and fails the release if any `0xd935` padding is present.

`release_fdroid.yml` builds the fdroid flavor unsigned on `ubuntu-latest` on every tag as a
preflight that the flavor compiles on Linux. Its artifact is never published, because the published
fdroid APK must be the locally signed one.

Never add an fdroid task to the same gradle invocation as the full flavor. `is_fdroid_build` is
true when **any** task name contains `fdroid`, so `assembleFullRelease assembleFdroidRelease` in
one call silently strips signing from the full flavor. The script runs two separate calls.

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

What is automated: the recipe is on `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`, so
F-Droid's checkupdates bot adds a build entry for the newest tag on its own cycle and no
per-release merge request is needed. It only ever adds the newest tag, so it steps from the
current published version straight to the latest release and never looks at tags in between.
The tag it picks must already carry `Aster-Mail-fdroid-<version>.apk` when their build server
fetches it, which is why the script uploads the asset in the same `gh release create` call.

Check the state with:

```
curl -s https://f-droid.org/api/v1/packages/org.astermail.android
```
