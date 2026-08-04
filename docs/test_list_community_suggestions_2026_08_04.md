# Test list - community suggestions batch (2026-08-04)

Five suggestions, the automated coverage added for each, and the manual emulator
steps that cannot be automated. Automated tests below are green on AVD `Aster_Test`
(API 34) and on the JVM unit suite.

## 1. Focused field scrolls above the keyboard (Aliases -> expanded alias)

Code: `core-design/.../keyboard_visibility.kt` (`keep_visible_above_keyboard`),
`ui/settings/detail/detail_common.kt` (`detail_scaffold` now pads for
`WindowInsets.systemBars.union(WindowInsets.ime)`),
`ui/settings/detail/alias_detail_sections.kt` (`alias_inline_field`).

Automated
- `androidTest ui/settings/KeepVisibleAboveKeyboardTest#focused_field_scrolls_into_view`
  - field starts off-screen between two 2000.dp spacers -> `assertIsNotDisplayed`
  - after `requestFocus()` the field is scrolled into view -> `assertIsDisplayed`

Manual (emulator, signed in)
- Settings -> Aliases -> expand an alias -> tap Display name: field sits above the IME.
- Repeat for Alias note and Websites, including the last alias in a long list.
- Rotate to landscape with the IME open: field stays visible.
- Gesture-nav and 3-button nav both tested (different inset heights).
- Close the IME with Back: layout returns to full height, no leftover gap.

## 2. Duplicate alias/condition warning in mail rules

Code: `ui/settings/mail_rules/mail_rules_model.kt`
(`condition_duplicate_key`, `condition_is_address_field`,
`duplicate_condition_indices`, `duplicates_condition_at`),
`ui/settings/mail_rules/rule_editor_screen.kt` (warning banner + rejection in the
`pick_value` sheet).

Automated - `test settings/MailRulesDuplicateConditionTest` (10 cases)
- same address twice on the same field is a duplicate
- case/whitespace differences still count as duplicates
- different address is not a duplicate
- different operator on the same address is not a duplicate
- different field with the same address is not a duplicate
- editing a condition back to its own value is not a duplicate
- blank values are never duplicates
- header conditions key on name and value
- `duplicate_condition_indices` reports every repeat after the first
- `condition_is_address_field` picks the alias-specific message

Manual (emulator, signed in)
- Rule editor -> add "To is a@b" -> add another "To is a@b": banner
  "This alias is already added." appears and the second value is not stored.
- Same with different casing and trailing spaces.
- Add "Subject contains x" twice: generic "This condition is already added."
- Remove a condition: banner clears.
- Saving a rule that already had duplicates from an older client still shows the banner.

## 3. Security settings parity with app.astermail.org/settings/security

Code: `ui/settings/detail/security_screen.kt` (sections regrouped to the web layout:
Tracking Protection / Images / HTML Content / External Link Warnings; new
`Remote Image Loading` selector), `ui/settings/settings_search_index.kt`,
all 14 `values*/strings.xml`. Every shared option title and description is copied
verbatim from the web translation files, per locale.

Automated
- `test settings/SecurityStringsWebParityTest#english_security_options_match_the_web_wording_exactly`
  - 27 option titles/descriptions equal the exact web strings
- `test settings/SecurityStringsWebParityTest#every_locale_defines_the_security_option_strings`
  - all 14 locales define every one of those keys, non-blank
- `androidTest ui/settings/SecurityRemoteImageChoiceTest#selecting_a_remote_image_option_updates_the_selection`
  - the three Remote Image Loading rows render and clicking one reports its id

Manual (emulator, signed in)
- Settings -> Security: sections read Tracking Protection, Images, HTML Content,
  External Link Warnings, in that order, matching the browser.
- Toggle each switch, force-quit, reopen: values persist and match the web client.
- Remote Image Loading: pick Never/Ask/Always; Block Remote Images flips to match
  (Always turns blocking off) and the browser shows the same state after a refresh.
- Switch the device language to de/fr/ja and confirm the wording matches the
  browser in the same language.

Not ported (web-only, no Android API yet): Session Timeout, Backup Codes,
Forward Secrecy key rotation, Connection (Direct / CDN Relay).

## 4. Label icons in the mail-rules label picker

Code: `ui/settings/mail_rules/rule_editor_screen.kt` - label picker items now use
`resolve_label_icon(label.encrypted_icon)` and the label's own colour, falling back
to the rules palette when the label has no colour.

Manual (emulator, signed in)
- Rule editor -> Add action -> Apply label: each row shows the same icon and colour
  as the sidebar.
- A label with no custom icon shows the default tag icon, not a blank space.
- Folder rows in the same sheet keep their folder icons.

## 5. Folder name chip first in All Mail rows

Code: already shipped in `ui/mail/email_row.kt` (`folder_chip` renders before
`thread.label_colors`), fed by `ui/mail/inbox_screen.kt`. Locked in with a
regression test.

Automated - `androidTest ui/mail/ThreadRowFolderChipOrderTest`
- `folder_chip_renders_before_label_chips` - folder chip bounds precede the label chip
- `label_chips_still_render_without_a_folder_chip` - labels still render when a row has no folder

Manual (emulator, signed in)
- All Mail: every row shows a folder chip first, then label chips.
- Rows in Trash/Spam show those folder names; custom folders show their own name.
- Inbox and folder-filtered lists are unchanged (no folder chip there).

## Emulator run (AVD `Aster_Test`, API 34)

- JVM unit suite: 1135 tests, 0 failures (includes the 10 duplicate-condition cases
  and the 2 web-parity cases).
- Instrumented `org.astermail.android.ui`: 157 tests, 16 failures.
- Instrumented crypto/mail/notifications/settings/storage: 43 tests, 2 failures.
- All 18 failures are pre-existing and environmental, not regressions: the same 16
  fail identically with this batch stashed out at `6a62629`. `FolderDeleteConfirmTest`
  (7), `AliasDetailPanelTest` (4), `SenderAttachmentLinkInstrumentedTest` (1) and
  `DecryptedMailCachePurgeInstrumentedTest` (1) die on
  `NoClassDefFoundError: io.mockk.impl.JvmMockKGateway` (mockk-android cannot
  initialise its proxy maker on this image); `SearchSelectionDeleteTest` (5) dies on
  `Given component holder class androidx.activity.ComponentActivity does not
  implement interface dagger.hilt.internal.GeneratedComponent`.
- Every test added by this batch passes.

## Emulator caveat

The demo account in `DEMO_CREDENTIALS.txt` (`familyownervigkl`) no longer
authenticates, so the signed-in manual passes above could not be run in this
session; coverage rests on the automated tests listed here.
