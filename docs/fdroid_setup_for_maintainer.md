# F-Droid setup: instructions for the upstream maintainer

Audience: a maintainer of `Aster-Privacy/Aster-Android` who holds the release
signing key and can publish releases and edit the fdroiddata merge request.

## Why the first submission stalled

F-Droid builds every app entirely from source on its own servers. The earlier
attempt could not pass because the app packaged a **prebuilt binary**,
`libaster_crypto_ffi.so`. The Rust source was not in this repo and
`core-crypto/src/main/jniLibs/`, where the `.so` lived, is gitignored. A clean
checkout therefore produced an APK that did not match the hand-signed reference
binary attached to the release. That mismatch is the "not reproducible" failure.

## What changed

The app no longer ships a hand-built native library at all.

Nothing in the Kotlin source tree loads `libaster_crypto_ffi.so`.
`core-crypto/.../CryptoNative.kt` is pure Kotlin on top of Bouncy Castle and the
platform `javax.crypto` providers, and it declares no `external fun`. The only
`System.loadLibrary` call in the app loads `sqlcipher`, which ships inside the
published SQLCipher AAR and needs no local toolchain.

The prebuilt `.so` was therefore dead weight in every APK. Removing it means:

- The build has no Rust, NDK, or `cargo-ndk` step.
- Every byte of native code now comes from a published Maven artifact.
- A clean checkout builds a complete, functional APK with Gradle alone.

The Rust crate stays in `rust/aster-crypto-ffi/` for reference. Gradle does not
compile it, CI does not build it, and it is not packaged. See that directory's
`README.md` if you ever revive it.

## The two flavors (unchanged)

`app/build.gradle.kts` defines `full` (GMS and Firebase; the `Aster-Mail.apk`
published on GitHub) and `fdroid` (FOSS, no GMS). F-Droid must build `fdroid`.

## fdroiddata recipe

The recipe is now a plain Gradle build. `subdir` is `app`.

```yaml
Builds:
  - versionName: 0.6.138
    versionCode: 148
    commit: <tag commit sha>
    subdir: app
    gradle:
      - fdroid
```

There are no `prebuild` steps, no `ndk:` pin, and no offline-cargo problem to
solve, because no crate is fetched during the build.

### Track B (ship this first): F-Droid builds and signs

This is the simplest reliable path. There is no reference binary, no signing key
anywhere, and no reproducibility comparison. F-Droid only needs the build above
to succeed. Use the `Builds:` block as-is, with **no** `Binaries` and **no**
`AllowedAPKSigningKeys`:

```yaml
AutoUpdateMode: Version
UpdateCheckMode: Tags
```

Trade-off: F-Droid signs with its own key, so users cannot cross-update between
the F-Droid build and your GitHub or Play builds, and there is no "reproducible"
badge.

### Track A (optional enhancement): reproducible and developer-signed

This keeps your signature across channels and earns the reproducible badge. It
requires the build to be byte-reproducible on F-Droid's servers. Confirm that
with one build round-trip before you rely on it. Dropping the native library
removes the largest source of nonreproducibility, so this track is now far more
likely to succeed than it was.

Add to the recipe:

```yaml
Binaries: https://github.com/Aster-Privacy/Aster-Android/releases/download/v%v/Aster-Mail-fdroid-%v.apk
AllowedAPKSigningKeys: 88b0a8a6fb94ee73a454a0f92732bd408e92e51ec1e9556744e5f00556100977
```

For each release, publish a reference binary that CI builds in a clean room and
you sign locally, so the key never reaches public CI:

1. Run the `release_fdroid` workflow on the tag, then download the
   `fdroid-unsigned` artifact (`app-fdroid-release-unsigned.apk`).
2. On the release machine, sign it locally. Do **not** run `zipalign`. The
   unsigned APK is already aligned by AGP exactly as F-Droid's buildserver
   produces it, and `apksigner` from build-tools 35 and later re-aligns on its
   own by default, rewriting the padding into `0xd935` alignment extra fields.
   That changes the bytes covered by the v2 and v3 signatures and breaks the
   reproducible comparison. Pass `--alignment-preserved` to keep the AGP layout
   so the signature transplant matches the from-source build:

   ```bash
   apksigner sign --ks keystore/aster-mail-upload-v3.jks \
     --ks-key-alias aster-mail --alignment-preserved \
     --out Aster-Mail-fdroid-<version>.apk app-fdroid-release-unsigned.apk
   apksigner verify --print-certs Aster-Mail-fdroid-<version>.apk
   ```

   `--alignment-preserved` requires build-tools 35 or later. The `apksigner` in
   build-tools 34 never re-aligns, so it also works there without the flag. The
   cert SHA-256 must be `88b0a8a6...0977`, which matches
   `AllowedAPKSigningKeys`.
3. Attach `Aster-Mail-fdroid-<version>.apk` to the GitHub Release for that tag.

Do **not** add `KEYSTORE_*` secrets to this public repo. That key also signs
your Play and GitHub builds, and anyone with repo write or Actions access could
exfiltrate it. Local signing keeps it off CI.

## Per-release discipline

For Track B, tag the release and let F-Droid pick it up. For Track A, also
publish the locally signed `Aster-Mail-fdroid-%v.apk` at the `v%v` URL, built
from the exact tagged commit.
