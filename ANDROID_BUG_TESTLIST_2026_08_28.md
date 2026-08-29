# Android bug batch 2026-08-28

Status: [ ] todo  [~] fixed, untested  [x] fixed + emulator verified

| # | Area | Bug | Status |
|---|------|-----|--------|
| 1 | Email template | New sign-in email has no system logo (first message missing logo, second has it) || [~] |
| 2 | Notifications | Double notification on sign-in; prompt notification buttons not tappable || [~] |
| 3 | Compose editor | Text color pickers need white borders | [x] |
| 4 | Dialogs | Info-page pop-ups open/close slowly | [x] |
| 5 | Network | Low network mode does nothing on Android; port full web behavior || [x] |
| 6 | Security | Settings + protection score load one-by-one instead of instantly || [x] |
| 7 | Security | Two-factor "Enabled" / "10 backup codes remaining" should match web; colored icon odd || [x] |
| 8 | Security | Active sessions list hard to scroll; show app icons | [x] |
| 9 | Security | "This device" tag off-design and misaligned with Sign out | [x] |
| 10 | Security | Passkeys/security keys: remote image loading should be a dropdown || [x] |
| 11 | Settings | "Active" tag on Vanguard is a weird active tab || [x] |
| 12 | Security | Recent activity is a long log; needs filters and a better design || [x] |
| 13 | Security | Recovery email design should match web; Remove button text double-lines/overflows | [x] |
| 14 | Encryption | PGP key section does not match web; boxes do not fill; recovery code shows only "OK" || [x] |
| 15 | Encryption | "Active" tag clashes with the icon background; does not match web || [x] |
| 16 | Account | Delete account: authenticator/backup-code text switches live instead of preloading | [x] |
| 17 | Security | Trusted devices "This device" tag is a bare line; Link a device design is basic, repeated icon | [x] |
| 18 | Aliases | Alias list repetitive, no filters || [x] |
| 19 | Aliases | Import/Export should collapse into an overflow menu || [x] |
| 20 | Aliases | Choose file cramped, does not match web; export button buggy || [x] |
| 21 | App-wide | Dropdown menus: poor highlighting, janky open/close animation | [x] |
| 22 | Aliases | Alias note placeholder too long, double-lines || [x] |
| 23 | Aliases | Website save/cancel buttons odd; alias settings dropdowns off-theme || [x] |
| 24 | Filters | Blocked senders / rules double-line; Add rule button poor; section needs redesign to match web || [x] |
| 25 | Domains | Custom domains shows "0 domains" then loads forever; manage dialog needs more detail || [x] |
| 26 | Compose | Discard draft dialog is ugly; default domain row double-lines | [x] |
| 27 | Billing | Plans and billing does not match web || [x] |
| 28 | Compose | Compose/reply prefill (from address, sender name, recipients, subject, quoted body) pops in after ~1s instead of being present on first paint. Must be instant everywhere. | [x] |
| 29 | App-wide | Pop-up animations must be smooth and clean, not instant | [x] |
| 30 | Mail list | Scroll bugs: after deleting drafts the top row sits half scrolled and is clipped; drag and select misbehaves | [x] |
| 31 | App-wide | Dropdowns do not match the web design (radius, item size, text size, selected state) | [x] |
| 32 | App-wide | Icons must not sit on tinted circular backgrounds. Remove icon background chips everywhere (activity list, session rows, settings rows, empty states) | [x] |
| 33 | Compose | Send button popup must anchor and animate correctly when there is space below the button instead of flipping upward | [x] |
| 34 | Mail list | Large blank space at the end of the scroll list | [x] |
| 35 | App-wide | Side scroll bar is buggy | [x] |
| 36 | Unsubscribe | Mailing list scan runs forever and shows two spinner icons at once; whole unsubscribe flow is a poor experience | [x] |
| 37 | App-wide | Dropdown visual quality still wrong after first pass | [x] |
| 38 | Mail list | Select all then delete does not stick: the same 13 messages reappear after the list refreshes | [x] |
| 39 | Mail list | Double haptic feedback when selecting a message | [x] |

## Rows that cannot be verified on the emulator

Both fixes are in place and were reviewed in code. Neither can be exercised on the test device,
so they stay `[~]`:

- **Row 1** lives in the backend email template (`Aster-Backend`). Verifying it needs a deployed
  backend and a real sign-in email, not an app build.
- **Row 2** needs a fresh sign-in to fire the login alert, and the emulator account password is
  not available in this session. Signing out would lock the test account. The fix is a unique
  WorkManager job per session (`enqueueUniqueWork` with `KEEP`) plus a stable notification id,
  which removes the duplicate, and separate PendingIntent request codes per action slot with
  `setAuthenticationRequired(false)`, which makes both buttons tappable.

## Deep scan, 2026-08-29

A follow-up sweep of the whole app for the same bug classes the reported rows belong to.
Each item below is fixed, builds clean, and is covered by the 1809-test unit suite.

| # | Area | Bug found by the scan | Fix | Verified |
|---|---|---|---|---|
| D1 | Mail data | `trash`, `archive`, `mark spam`, `unmark spam`, `unarchive`, and `restore from trash` reported failure when only the best-effort metadata mirror failed, so the list restored messages the server had already moved | The authoritative bulk action alone decides the result; the mirror is best effort | Emulator: select all, trash, pull to refresh, nothing returns |
| D2 | Mail data | Permanent delete ran one request per message with no dedupe, so a large selection emitted a toast per message and a missing message counted as a failure | New bulk path dedupes ids, treats "not found" as already deleted, and emits one toast | Emulator: bulk delete emits exactly one toast |
| D3 | Mail list | The permanent-delete confirmation always used singular wording, so 26 selected messages read as one | Count-aware plural joiner string added in all 14 locales | Emulator: 26 selected reads correctly |
| D4 | Search index | `refresh_index` and `resume_indexing` each started a second build that overwrote the job handle, so pausing cancelled the wrong job and the spinner never cleared | Index builds coalesce onto one job | Emulator: rapid double tap on Scan inbox shows one spinner and terminates |
| D5 | App-wide | Cancelled coroutines surfaced as error toasts when a screen closed mid-request, across recovery, plan limits, device linking, two-factor, and mail rules | Cancellation is rethrown instead of being rendered | Build and unit suite |
| D6 | Export | Tapping Export twice started two concurrent exports | The second tap is ignored while an export is running | Build |
| D7 | Auth | Compose drafts survived sign-out and account deletion | The draft store is cleared on both paths | Build |
| D8 | Mail data | Address parsing could read past the end of a malformed header | Bounded index lookup with a guard | Unit suite |
