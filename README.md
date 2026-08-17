<img width="200" alt="Aster" src="https://raw.githubusercontent.com/Aster-Privacy/.github/main/profile/aster_logo.png" />

# Aster Mail for Android

This repository contains the native Android app for Aster Mail, published on Google Play.

Aster Mail is a free, open-source, end-to-end encrypted mail service. Every message subject line and attachment is encrypted locally on your device. This means we have no way to read your email and we never will.

You can sign up at [astermail.org](https://astermail.org). A phone number and recovery email are not required.

## How it works

All Aster-to-Aster messages are end-to-end encrypted using the standard OpenPGP (Ed25519 and Curve25519). Subject lines are also encrypted. This means that we cannot read your subject lines, unlike other providers. Aster-to-Aster messages also use ML-KEM-768 inside an X3DH and Double Ratchet protocol, which protects against store-now-decrypt-later attacks.

Your keys are yours, and they are fully portable. You can export them and use them with GPG or any OpenPGP client. Public keys are published through WKD and key servers automatically, so encrypting to other Aster users happens without any setup.

Aster runs on a zero-access architecture located in Germany. This means we store nothing we could hand over, even if we were compelled.

## Getting started

Go to [astermail.org](https://astermail.org) to create a free account. If you would like to contribute code to Aster, see [CONTRIBUTING.md](https://github.com/Aster-Privacy/.github/blob/main/CONTRIBUTING.md) for instructions.

## Community

Join our [Discord](https://discord.gg/R4XqRUfgWZ) to share feedback, ask questions, and contribute to the privacy community. You can also find us on [X](https://x.com/AsterPrivacy) and [Reddit](https://www.reddit.com/r/AsterPrivacy).

If you have any questions or security disclosures, email us at [hello@astermail.org](mailto:hello@astermail.org) or [security@astermail.org](mailto:security@astermail.org). **Do not open a public issue for security vulnerabilities.** Read [SECURITY.md](SECURITY.md) for the full security vulnerability disclosure process.

## Contributing

We welcome contributions of all kinds. Read [CONTRIBUTING.md](https://github.com/Aster-Privacy/.github/blob/main/CONTRIBUTING.md) before opening a pull request.

By contributing to any Aster repository, you agree that your contributions will be licensed under [AGPL v3](https://www.gnu.org/licenses/agpl-3.0.en.html).
