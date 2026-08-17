<img width="200" alt="Aster" src="https://raw.githubusercontent.com/Aster-Privacy/.github/main/profile/aster_logo.png" />

# Aster Security Policy

## Reporting a vulnerability

**Don't open a public GitHub issue for a security vulnerability.**

Send your report to security@astermail.org, or submit it through our vulnerability disclosure program on Bugcrowd: https://bugcrowd.com/engagements/aster-privacy-vdpc

We acknowledge reports within 48 hours, prioritize fixes by severity, and keep you updated until the issue is resolved.

## Scope

This policy covers all Aster products and infrastructure:

- Aster Mail (astermail.org)
- All repositories under github.com/Aster-Privacy

## Safe harbor

We don't pursue legal action against researchers who:

- Report vulnerabilities in good faith
- Don't access, modify, or exfiltrate user data
- Don't disrupt service availability or degrade the user experience
- Give us a reasonable amount of time to respond before public disclosure

## Encryption architecture

All encryption and decryption happens on your device. The server never has access to your plaintext data.

| Channel | Protocol |
|---|---|
| Aster to Aster | X3DH and Double Ratchet with ML-KEM-768 (post-quantum) |
| Aster to external recipients | Ed25519 OpenPGP, portable keys that work with any OpenPGP client |

Encrypted metadata includes subject lines, message content, contacts, folder structure, search indices, timestamps, and attachment data.

## Coordinated disclosure

We follow coordinated disclosure. Give us adequate time to patch a vulnerability before you publish it. If you would like public credit, say so in your report.

## Acknowledgments

We thank the researchers who help keep Aster secure. Credited disclosures are listed here as we receive them.
