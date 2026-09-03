# Android bug batch 2026-09-03

Status: [ ] todo  [~] fixed, untested or partial  [x] fixed + emulator verified

Verified against `emulator-5554` on the `fdroidDebug` build with `-Pastermail.localApi=true`,
signed in as the local test account `uitest0903a@astermail.org`.

| # | Area | Bug | Status |
|---|------|-----|--------|
| 1a | Compose | Reply signature inserts late instead of on first paint | [x] |
| 1b | Inbox | App opens half scrolled down; new messages insert without restoring the top | [x] |
| 2 | Mail detail | Thread view does not match the reference mail app layout | [x] |
| 3 | Mail detail | Message body font too small; no message cards, splits hard to read | [x] |
| 4 | Mail detail | Long sender name wraps to a new line in the message header | [x] |
| 5 | Compose | No draft-saved indicator; "Saved" text not aligned with the reply text | [x] |
| 6 | App-wide | Dropdowns have borders, radius too tight, animation slow | [x] |
| 7 | App-wide | Dialogs have borders | [x] |
| 8 | App-wide | Button design needs work | [~] |
| 9 | Mail detail | Per-message overflow menu weak; reply screen shows arrows only | [x] |
| 10 | Compose | Large blank space below the three dots; expand does not work properly | [x] |
| 11 | Compose | Send icon not optically centered | [x] |
| 12 | Contacts | Star must be a filled color; parity with web contacts | [~] |
| 13 | Inbox | Sync icon is confusing | [x] |
| 14 | Mail detail | Inbox tag chip too large | [x] |
| 15 | Mail detail | Tag chip must sit to the right of the subject, not below it, and not be clickable | [x] |
| 16 | Mail detail | Scroll feedback and small layout bugs on the message page | [~] |
| 17 | Mail detail | Message thread should use the inbox list card design | [x] |

## Notes on the partial rows

- **8** - the shared token pass landed in `core-design` (radius, motion, pressed scale) and the
  surfaces reworked for items 6, 7, 9 and 12 pick it up. Screens that were not part of this batch
  still draw their own button styling and need a separate sweep.
- **12** - the star is a filled gold star on the list row and the detail toolbar, and it persists
  server-side. Contact writes from Android are now non-destructive (see the deep scan findings), but
  the web-only editors are still missing on Android: multiple typed emails, phones, addresses and
  dates, related people, social networks, websites, instant messengers, nickname, pronouns, middle
  name, name suffix, phonetic names, role, department, comment, relationship and profile color.
  Android reads and preserves all of those; it cannot yet edit them.
- **16** - the card, chip, header and quote-expansion layout bugs are fixed and verified. The
  per-message scroll feedback was only exercised against a single-message thread, because the local
  test mailbox holds one message. It needs a re-check against a long multi-message thread.

## Deep scan findings

1. **Android contact writes destroyed every web-only field.** `encode_contact_json` built a fresh
   `JSONObject` from the handful of fields the Android model carries, so saving a contact on Android
   silently dropped `email_entries`, `phone_entries`, `address_entries`, `date_entries`,
   `related_people`, `social_networks`, `websites`, `instant_messengers`, `nickname`, `pronouns`,
   `middle_name`, `name_suffix`, the phonetic names, `role`, `department`, `comment` and `revisions`
   written by the web client. Fixed: `Contact` now carries `raw_json`, `parse_contact_json` records
   the source blob, and `encode_contact_json` mutates that blob instead of replacing it. Emails past
   the first two are carried forward rather than truncated.
2. **`work_phone` was never persisted.** The edit screen collected it and the encoder never wrote it,
   so it vanished on save and always read back blank. Fixed with `put_typed_entry`, which writes it
   as the `work` entry in `phone_entries`, the same shape the web client reads. Verified on device:
   after saving, the detail screen shows both **Mobile** and **Work** numbers.
3. **Groups were dropped on every contact edit.** The Save handler in `contact_edit_screen.kt`
   rebuilt `Contact(...)` without `groups`, so editing any field removed the contact from all of its
   groups. Fixed by carrying `groups` and `raw_json` through from the source contact.
4. **`compose_discard_draft_message` is misleading, NOT fixed.** The string reads "This message is
   not saved. If you discard it, the text and any attachments are removed," but by the time that
   dialog appears a draft has usually been saved and discarding deletes it. Correcting the copy means
   retranslating it across 15 locales, so it is left as a known issue rather than shipped with 15
   stale translations.
5. **Cold-start scroll position is safe by construction.** `session_lazy_list_state_saver()` stamps
   `app_session.process_token` into the saved state and restores `LazyListState(0, 0)` on any token
   mismatch. The token is a fresh UUID per process, so a cold launch can never restore a mid-list
   offset. This is the structural half of item 1b.
