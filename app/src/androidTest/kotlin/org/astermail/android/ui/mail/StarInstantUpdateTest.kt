//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.mail

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StarInstantUpdateTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun sample(id: String, starred: Boolean) = Email(
        id = id,
        sender_name = "Aster",
        sender_email = "noreply@astermail.org",
        subject = "Welcome",
        preview = "hi",
        received_at = 1_000L,
        is_read = false,
        is_starred = starred,
        has_attachment = false,
    )

    private fun new_fingerprint(emails: SnapshotStateList<Email>): Int {
        var hash = emails.size
        emails.forEach { e ->
            hash = 31 * hash + e.id.hashCode()
            hash = 31 * hash + (if (e.is_starred) 1 else 0)
            hash = 31 * hash + (if (e.is_read) 2 else 0)
            hash = 31 * hash + (if (e.is_pinned) 4 else 0)
        }
        return hash
    }

    private fun old_fingerprint(emails: SnapshotStateList<Email>): Any =
        emails.size to (emails.firstOrNull()?.id to emails.lastOrNull()?.id)

    @Composable
    private fun harness(fingerprint: (SnapshotStateList<Email>) -> Any) {
        val emails = remember { mutableStateListOf(sample("m1", starred = false)) }
        val fp = fingerprint(emails)
        val threads = remember(fp) { group_by_thread(emails.toList()) }
        Column {
            Text("starred=${threads.first().is_starred}")
            Button(onClick = {
                val idx = emails.indexOfFirst { it.id == "m1" }
                emails[idx] = emails[idx].copy(is_starred = !emails[idx].is_starred)
            }) { Text("toggle") }
        }
    }

    // The fixed fingerprint folds per-item flags in, so toggling a star recomputes
    // `threads` and the rendered star state flips immediately.
    @Test
    fun star_reflects_instantly_with_fixed_fingerprint() {
        compose_rule.setContent { harness(::new_fingerprint) }

        compose_rule.onNodeWithText("starred=false").assertIsDisplayed()
        compose_rule.onNodeWithText("toggle").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("starred=true").assertIsDisplayed()
    }

    // Regression guard: the previous fingerprint (size + first/last id) ignored
    // per-item flags, so `threads` never recomputed on a star toggle and the star
    // stayed stale. This asserts that broken behavior to lock the fix in place.
    @Test
    fun star_stays_stale_with_old_fingerprint() {
        compose_rule.setContent { harness(::old_fingerprint) }

        compose_rule.onNodeWithText("starred=false").assertIsDisplayed()
        compose_rule.onNodeWithText("toggle").performClick()
        compose_rule.waitForIdle()
        // Bug: still reads the stale ThreadRow because the fingerprint did not change.
        compose_rule.onNodeWithText("starred=false").assertIsDisplayed()
    }
}
