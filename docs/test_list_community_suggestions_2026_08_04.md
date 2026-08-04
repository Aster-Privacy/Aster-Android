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

## 6. Alias delivery vs mail rules

Code: `mail_rules/alias_rule_delivery.kt` (`alias_rule_delivery`,
`rule_alias_delivery_conflict` and helpers, mirroring the backend precedence in
`rules_engine.rs` / `inbound_poller.rs`: rules are ordered by `(priority,
created_at)`, the last `move_to` wins, and a rule's `move_to` overrides the alias
`delivery_folder_token`), `ui/settings/detail/alias_detail_panel.kt`
(`AliasRuleDeliveryNote`, `alias_delivery_rule_note` under the Deliver to picker),
`ui/settings/detail/aliases_screen.kt` (`alias_rule_delivery_note`),
`ui/settings/mail_rules/rule_editor_screen.kt` (conflict banner),
`settings/SettingsViewModel.kt` (`load_mail_rules`, `mail_rules` state),
3 new keys in all 14 `values*/strings.xml`.

The Deliver to picker keeps showing the alias's own stored setting; it is not
silently overwritten with the rule's folder. A rule can be conditional (`ALL`
match mode with extra conditions) and can be deleted, so displaying its folder as
the alias setting would misreport what is stored. Instead the rule and its
destination are named under the picker, in muted text when they agree and in
warning amber when they differ.

Automated - `test settings/AliasRuleDeliveryTest` (18 cases)
- a `move_to` rule targeting the alias is reported with its name and folder
- an alias no rule targets has no delivery note
- disabled rules and rules with no `move_to` are ignored
- the last rule by priority then `created_at` wins; the last `move_to` in a rule wins
- address matching ignores case and surrounding whitespace
- `to` / `cc` / `bcc` / `any_recipient` all match; `contains` and `matches_domain` match
- nested `and` / `or` match, `not` is excluded
- blank condition values and non-address alias strings never match
- conflict reported when the rule folder differs from the alias folder
- no conflict when they are equal or when the alias has no explicit target
- archive-only aliases (`never_inbox`) conflict with a `move_to` rule
- conflicts only consider exact (`is`) address conditions and require a `move_to`
- the alias is found inside a nested `or`

Automated - `androidTest ui/settings/AliasDeliveryRuleNoteTest` (2 cases)
- matching rule renders "Mail rule "X" already moves mail for this alias to Y."
- conflicting rule names the selected destination too

Manual (emulator, signed in) - NOT RUN, see the caveat below
- Rule "To is alias@astermail.org -> Move to My Feed", then Settings -> Aliases ->
  expand that alias: the note under Deliver to names the rule and My Feed.
- Change Deliver to to Inbox: the note turns amber and says mail will not land in Inbox.
- Set Deliver to to My Feed: the note returns to muted text.
- Rule editor: with the alias already delivering to My Feed, build a rule
  "To is alias@ -> Move to Receipts": the amber banner names both folders.
- Delete the rule: the note disappears from the alias panel.

## 7. Move up / move down for labels

The drawer context menu already gained Move up / Move down on `main` in `6331da6`
(unreleased, which is why the reporter's build lacks it). This entry covers the four
defects found while auditing whether that option actually works, plus the missing
affordance on the Labels settings screen.

What was wrong:

- Neither labels nor tags were sorted by `sort_order` on the client. `load_labels`
  concatenates `decrypted + surviving + preserved`, so a custom order survived the
  server round trip but was lost on the next optimistic merge.
- Settings -> Labels had no move controls at all, while Settings -> Folders did.
- `move_tag` issued N sequential `update_tag` calls; a failure mid-loop left the
  server half-reordered. The backend has had `POST /tags/bulk/reorder` all along.
- `can_move_up` / `can_move_down` were derived from the filtered visible list while
  `move_label_row` operated on the unfiltered group, so an undecryptable label sitting
  between two visible ones made a move look like a no-op.

Ordering now lives in one place, `labels/label_order.kt`, used by the drawer, the
settings screen, the rule editor picker, inbox, detail and search.

JVM, `LabelOrderTest` (12):

- sort_order ordering regardless of server order; folders and `custom` excluded.
- blank, null and still-encrypted names excluded.
- equal sort_order falls back to created_at, then token.
- tag ordering plus unreadable-name drop.
- move up, move down, multi-position move.
- refusals: both edges, negative index, out-of-range index, zero direction, empty list.
- reorder entries contain only positions that changed; empty when nothing moved.
- renumbering starts at zero; duplicate sort_order values renumber stably.

JVM, `SettingsViewModelTest` (6):

- `move_label_row` reorders and persists via one bulk reorder call.
- `move_label_row` leaves folders untouched.
- `move_label_row` at an edge makes no api call.
- `move_label_row` reverts the optimistic order and toasts when the api fails.
- `move_tag` sends a single bulk request and never calls `update_tag`.
- `move_tag` reverts the optimistic order when the api fails.

Instrumented, `LabelMoveControlsTest` (6, runs on the emulator):

- every row renders both chevrons.
- a middle row moves in both directions.
- the first row's up and the last row's down are inert.
- the first row can still move down, the last can still move up.
- a lone label has neither direction enabled.
- the chevrons do not shadow the delete action.

This suite renders `label_settings_row` directly rather than `LabelsScreen`, so it
does not need Hilt or mockk. That matters: `FolderDeleteConfirmTest`, the existing
suite that otherwise covers this screen, cannot run in this environment (see below).

Manual (NOT RUN, see the caveat below):

- Settings -> Labels: move a label up, back out, return, order persists.
- Sidebar: same order as the settings screen.
- Rule editor label picker and the inbox/detail/search tag chips: same order.

## Emulator run (AVD `Aster_Test`, API 34)

- JVM unit suite: 1171 tests, 0 failures (1135 before item 6, +18 for item 7:
  12 `LabelOrderTest` and 6 `SettingsViewModelTest`).
- Instrumented `LabelMoveControlsTest`: 6 tests, 0 failures.
- Instrumented `org.astermail.android.ui.settings`: 28 tests, 11 failures, all the
  known mockk-android set below.
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
