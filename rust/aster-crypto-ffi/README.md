# aster-crypto-ffi (unused)

This crate is not part of the Android build. Nothing in the Kotlin source tree
loads `libaster_crypto_ffi.so`, and `core-crypto/.../CryptoNative.kt` is pure
Kotlin on top of Bouncy Castle and the platform `javax.crypto` providers. The
only `System.loadLibrary` call in the app loads `sqlcipher`, which ships inside
the SQLCipher AAR.

The source is kept here for reference. It is not compiled by Gradle, not built
in CI, and not packaged into the APK. If you revive it, add the JNI declarations
to `CryptoNative.kt` first, then restore the `cargo-ndk` build step and the
`jniLibs.srcDirs` source set.

It is self-contained: it depends only on published crates (argon2, aes-gcm,
ed25519-dalek, hkdf, hmac, pbkdf2, sha2, base64, rand, zeroize, jni). It does
not read or embed any secret.

## Build

Requires the Android NDK and `cargo-ndk`:

```bash
cargo install cargo-ndk --locked --version 4.1.2
ANDROID_NDK_HOME=/path/to/ndk bash scripts/build_android.sh
```

This produces `libaster_crypto_ffi.so` for `arm64-v8a`, `armeabi-v7a`, and
`x86_64` into `core-crypto/src/main/jniLibs/`. The toolchain is pinned in
`rust-toolchain.toml` and dependencies in `Cargo.lock`.
