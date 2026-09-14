# Mail loading and read-state paths

This document lists every Android mail surface that loads data or marks mail as read, and the rule each one follows. Every row is closed. A row without a code change carries the reason it cannot flicker or mark mail read late.

## Rules

- A screen that has data never clears it to show a skeleton. Every skeleton gate checks that its data is empty.
- A mail list skeleton appears only when there is no data and loading lasts longer than `skeleton_defer_ms` (`skeleton_visible_after` in `app/src/main/kotlin/org/astermail/android/ui/mail/inbox_skeleton.kt`).
- Opening mail marks it read through `MailViewModel.on_user_opened_mail`. The row, the unread count, and the label counts change in the same call, and the server write follows.
- `read_overrides` in `MailViewModel` keeps a pending read until the server confirms it. Every list fetch, cache prime, revalidation, and search result applies it, so a stale response cannot restore unread.
- The search index applies the same overrides before it writes a row. `MailViewModel` registers `read_overrides` with `SearchIndexManager.add_read_overlay`, and `refresh_known_flags` and `cache_items` read it, so a background index refresh cannot write a stale unread to disk.
- If the server write fails, the row, the unread count, and the label counts roll back through `sync_read`, `revert_bulk_read`, or `revert_read_override_batch`. There is one read system: single, swipe, bulk, scope, and notification reads all record `note_read_flips` and `read_overrides`.
- The "Never" mark-as-read preference and a positive delay are respected. The open route waits for preferences before it marks read.

## Open paths

| Entry | Path | Status |
| --- | --- | --- |
| List tap from any folder, label, starred, or filtered list | Every list navigates to `routes.mail_detail_for`, and the route in `MainActivity.kt` calls `on_user_opened_mail` once per open | Closed |
| Search result | `ui/search/search_screen.kt` opens the same detail route | Closed |
| Notification tap and deep link | `MainActivity.kt` `pending_open_email_id` navigates to the same detail route after sign-in and unlock | Closed |
| Leaving detail before the delay | `cancel_opened_mail` in the route's `DisposableEffect` cancels the delayed read | Closed |
| Thread messages in detail | `on_opened_thread_known` and `mark_thread_siblings_read` record overrides and roll back failures | Closed |
| Read toggle and mark unread in detail | `mail_detail_screen.kt` calls `mark_read` and `mark_unread`, which record overrides | Closed |
| Swipe read toggle | `inbox_screen.kt` `toggle_read` calls `mark_read_bulk` and `mark_unread_bulk` | Closed |
| Bulk selection | `inbox_screen.kt` and `search_screen.kt` call `mark_read_bulk` and `mark_unread_bulk`, which record `note_read_flips` and roll back with `revert_bulk_read` | Closed |
| Mark all read or unread in a folder | `mark_all_read_scope` and `mark_all_unread_scope` record flips and overrides and roll back on failure | Closed |
| Select all in a folder, then read or unread | `bulk_scope_action` routes read actions to `bulk_scope_read`, which records flips and overrides like a single open and rolls back on failure | Closed |
| Notification Mark as Read action | `notifications/MailNotificationActionWorker.kt` emits `MailReadEvents` Applied, Confirmed, and Failed, and `MailViewModel.apply_notification_read` applies, settles, or reverts through `apply_local_read` | Closed |
| Folder and label unread counts | `label_unread_deltas` and stats change in `apply_local_read` and `apply_bulk_read`, the same call as the row | Closed |
| Home screen widgets | The app has no widget, so there is no other unread surface | Closed |

## Search index

| Case | Path | Status |
| --- | --- | --- |
| Indexed mail refreshed while a read is pending | `SearchIndexManager.refresh_known_flags` uses the pending read, not the server flag | Closed |
| New mail written to the index while a read is pending | `SearchIndexManager.cache_items` writes the pending read | Closed |
| Read rolled back | `sync_read` removes the override, so the next index refresh writes unread | Closed |
| View model cleared | `MailViewModel.onCleared` removes its overlay | Closed |

## Account switch

| Case | Path | Status |
| --- | --- | --- |
| Pending overrides, flips, delayed reads, and notification reads | `MailViewModel.reset_for_account_switch` clears them and increments `account_generation` | Closed |
| In-flight single read | `sync_read` captures `account_generation` and skips retry, confirm, rollback, and stats if it changed | Closed |
| In-flight bulk, scope, and thread reads | `mark_read_bulk`, `mark_unread_bulk`, `mark_all_read_scope`, `mark_all_unread_scope`, `bulk_scope_read`, and `mark_thread_siblings_read` return after the server call if the account changed | Closed |
| Search index | `SearchIndexManager.clear` runs on switch and bumps its epoch, so a refresh from the previous account is dropped | Closed |
| Notification action from the previous account | The worker writes a message ID that the new account does not have, so the index and list have no row to change, and `notification_reads` was cleared on switch | Closed |
| Compose sender | `ui/compose/compose_seed_store.kt` `read_identity` returns no cached identity when the stored email differs from the signed-in account | Closed |

## Failed open

| Entry | Path | Status |
| --- | --- | --- |
| Detail load fails with no content kept | `load_thread` calls `undo_failed_open`, which cancels the delayed read and, if the mail was unread at open, reverts the read through `mark_unread` | Closed |
| Same mail loads later | `load_thread` calls `resume_failed_open`, which marks it read once through `on_user_opened_mail` | Closed |
| Mail not in any local list | No row or count changed locally, and the server write fails with the same network, so `sync_read` rolls it back | Closed |
| Cancelled load | A cancellation keeps the read, because the user left the screen and the mail had opened | Closed |

## Loading paths

| Surface | Path | Status |
| --- | --- | --- |
| Inbox and folders | `inbox_screen.kt` shows the skeleton only without rows, after `skeleton_visible_after` | Closed |
| Filtered lists | `filtered_inbox_screen.kt` uses the same gate | Closed |
| Search | `search_screen.kt` shows `inbox_skeleton` only when there are no results and results are pending | Closed |
| Mail detail body | `mail_detail_screen.kt` shows the body placeholder only until the body is decrypted and the page has painted, so it never covers rendered mail | Closed |
| Drawer folders and labels | `ui/drawer/drawer_content.kt` has no loading state and renders the cached list | Closed |
| Settings, Security | `ui/settings/detail/security_screen.kt` starts ready when the security data is already loaded, so returning to the screen shows no skeleton | Closed |
| Settings, Storage | `storage_screen.kt` shows the skeleton only when both storage and stats are missing | Closed |
| Settings, Mail rules | `MailRulesViewModel.load` sets `is_loading` only when there are no rules | Closed |
| Aliases, domains, directories, and ghost aliases | `aliases_screen.kt` shows each skeleton only when its list is empty | Closed |
| Compose sender | `compose_seed_store.kt` seeds the sender from the cached identity, so the sender field does not wait for the network | Closed |
| App start | `ui/common/theme_boot_background.kt` applies the saved night mode before the first frame | Closed |

## Accessibility

| Element | Change |
| --- | --- |
| List row | `email_row.kt` sets a read or unread state description |
| Star button | Keeps a 48 dp touch target while the icon animates |
| Skeleton | A polite live region announces loading |
| Motion | Row animations snap when reduce motion is on |

## Tests

- `app/src/test/kotlin/org/astermail/android/mail/DelayedSkeletonTest.kt`: a fast load never shows the skeleton, and existing data is never replaced.
- `app/src/test/kotlin/org/astermail/android/mail/SearchIndexScopeTest.kt`: a pending read wins over a stale unread for indexed and new mail, and the index writes unread once the pending read is gone.
- `app/src/test/kotlin/org/astermail/android/mail/MailViewModelTest.kt`: the search index sees a pending read and loses it after rollback, select-all read records pending reads and rolls back on failure, a read that settles after an account switch never touches the next account, and mail that never opens goes back to unread and reads once it loads.
