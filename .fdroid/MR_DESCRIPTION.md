# F-Droid recipe hand-off

`org.astermail.android.yml` in this directory mirrors the recipe merged into
`gitlab.com/fdroid/fdroiddata` on 2026-09-06 (commit `aed9cd430`, MR !40463). It is a reference
copy, not a pending submission. The published recipe is the contract:

- `Binaries:` fetches `Aster-Mail-fdroid-<version>.apk` from the GitHub release for each tag.
- `AllowedAPKSigningKeys` pins the release certificate, SHA-256
  `88b0a8a6fb94ee73a454a0f92732bd408e92e51ec1e9556744e5f00556100977`.
- `AutoUpdateMode: Version` with `UpdateCheckMode: Tags` lets F-Droid's checkupdates bot add a
  build entry for the newest tag without a merge request.

For every tag, F-Droid rebuilds the `fdroid` flavor from source, copies our signature onto their
build, and publishes our APK only if the two match byte for byte. `scripts/release.sh` builds,
signs, verifies, and uploads that asset. Changing any of the three points above needs a new
fdroiddata merge request, so keep this copy in sync with upstream and ask before proposing one.
