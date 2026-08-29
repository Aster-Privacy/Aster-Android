New app: Aster Mail (`org.astermail.android`)

Private, end-to-end encrypted email client for [astermail.org](https://astermail.org) accounts.

- License: AGPL-3.0-or-later
- `NonFreeNet` anti-feature declared, because the app requires an astermail.org account
- No Firebase or Google Play Services in the `fdroid` flavor
- Push notifications through UnifiedPush only, with a 15-minute polling fallback
- Source: https://github.com/Aster-Privacy/Aster-Android

F-Droid builds the `fdroid` flavor from source and signs it with the F-Droid key. The recipe
carries no `Binaries:` and no `AllowedAPKSigningKeys:`, so the build does not need to reproduce
an APK that we publish, and we publish no APK to this channel. The app has never been on
F-Droid, so no installed base is affected by the resulting signing key.

`AutoUpdateMode: Version` and `UpdateCheckMode: Tags` let the bot pick up later releases from
the tags on the repo, so each new version does not need its own merge request.
