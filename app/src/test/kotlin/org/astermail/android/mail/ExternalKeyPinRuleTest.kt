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

package org.astermail.android.mail

import org.astermail.android.api.keys.ExternalKeyInfo
import org.astermail.android.crypto.OpportunisticKeyTrust
import org.astermail.android.crypto.PgpKeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalKeyPinRuleTest {

    private val passphrase = "pin rule test".toCharArray()
    private val first = PgpKeyGenerator.generate("Pat", "pat@example.com", passphrase)
    private val second = PgpKeyGenerator.generate("Pat", "pat@example.com", passphrase)
    private val other = PgpKeyGenerator.generate("Sam", "sam@example.com", passphrase)

    private fun pin_of(key: org.astermail.android.crypto.PgpKeyPairResult, email: String = "pat@example.com"): String =
        (ExternalKeyPinRule.vet_armored(email, key.armored_public_key, null, explicit = true) as ExternalKeyVetting.Accepted).fingerprint

    @Test
    fun a_first_key_is_accepted_for_pinning_on_an_explicit_send() {
        val vetting = ExternalKeyPinRule.vet_armored("Pat@Example.com ", first.armored_public_key, null, explicit = true)
        assertTrue(vetting is ExternalKeyVetting.Accepted)
        vetting as ExternalKeyVetting.Accepted
        assertTrue(vetting.first_use)
        assertEquals(first.fingerprint.filterNot { it.isWhitespace() || it == ':' }.lowercase(), vetting.fingerprint)
    }

    @Test
    fun a_pinned_key_is_accepted_again() {
        val pin = pin_of(first)
        val vetting = ExternalKeyPinRule.vet_armored("pat@example.com", first.armored_public_key, pin.uppercase())
        assertEquals(ExternalKeyVetting.Accepted(pin, first_use = false), vetting)
    }

    @Test
    fun a_different_key_for_a_pinned_address_is_a_change() {
        val pin = pin_of(first)
        val vetting = ExternalKeyPinRule.vet_armored("pat@example.com", second.armored_public_key, pin)
        assertTrue(vetting is ExternalKeyVetting.Changed)
        vetting as ExternalKeyVetting.Changed
        assertEquals(pin, vetting.prior_fingerprint)
        assertNotEquals(pin, vetting.new_fingerprint)
        assertEquals(vetting, ExternalKeyPinRule.vet_armored("pat@example.com", second.armored_public_key, pin, explicit = true))
    }

    @Test
    fun an_unsaved_key_is_not_used_opportunistically() {
        assertSame(ExternalKeyVetting.Rejected, ExternalKeyPinRule.vet_armored("pat@example.com", first.armored_public_key, null))
        assertEquals(
            ExternalKeySendStep.SKIP_OR_REQUIRE,
            ExternalKeyPinRule.send_step(ExternalKeyPinRule.vet_armored("pat@example.com", first.armored_public_key, null)),
        )
    }

    @Test
    fun a_saved_key_that_names_the_address_is_used_opportunistically() {
        val pin = pin_of(first)
        assertEquals(
            ExternalKeyVetting.Accepted(pin, first_use = false),
            ExternalKeyPinRule.vet_armored("pat@example.com", first.armored_public_key, pin.uppercase()),
        )
    }

    @Test
    fun a_saved_key_that_names_another_address_is_not_used() {
        val pin = pin_of(other, "sam@example.com")
        assertSame(ExternalKeyVetting.Rejected, ExternalKeyPinRule.vet_armored("pat@example.com", other.armored_public_key, pin))
        assertSame(ExternalKeyVetting.Rejected, ExternalKeyPinRule.vet_armored("pat@example.com", other.armored_public_key, pin, explicit = true))
    }

    @Test
    fun the_lock_trust_needs_a_saved_key_that_names_the_address() {
        val pin = pin_of(first)
        assertEquals(OpportunisticKeyTrust.PINNED, ExternalKeyPinRule.opportunistic_trust("pat@example.com", first.armored_public_key, pin))
        assertEquals(OpportunisticKeyTrust.UNPINNED, ExternalKeyPinRule.opportunistic_trust("pat@example.com", first.armored_public_key, null))
        assertEquals(OpportunisticKeyTrust.CHANGED, ExternalKeyPinRule.opportunistic_trust("pat@example.com", second.armored_public_key, pin))
        val other_pin = pin_of(other, "sam@example.com")
        assertEquals(OpportunisticKeyTrust.UNPINNED, ExternalKeyPinRule.opportunistic_trust("pat@example.com", other.armored_public_key, other_pin))
        assertEquals(OpportunisticKeyTrust.UNPINNED, ExternalKeyPinRule.opportunistic_trust("pat@example.com", "not a key", pin))
    }

    @Test
    fun a_key_without_the_address_is_rejected() {
        assertSame(ExternalKeyVetting.Rejected, ExternalKeyPinRule.vet_armored("pat@example.com", other.armored_public_key, null))
        assertSame(ExternalKeyVetting.Rejected, ExternalKeyPinRule.vet_armored("pat@example.com", "not a key", null))
        assertSame(ExternalKeyVetting.Rejected, ExternalKeyPinRule.vet("pat@example.com", "ab", emptyList(), null))
        assertSame(ExternalKeyVetting.Rejected, ExternalKeyPinRule.vet("pat@example.com", null, listOf("pat@example.com"), null))
        assertSame(ExternalKeyVetting.Rejected, ExternalKeyPinRule.vet_armored("pat@example.com", other.armored_public_key, null, explicit = true))
        assertSame(ExternalKeyVetting.Rejected, ExternalKeyPinRule.vet_armored("pat@example.com", "not a key", null, explicit = true))
        assertSame(ExternalKeyVetting.Rejected, ExternalKeyPinRule.vet("pat@example.com", null, listOf("pat@example.com"), null, explicit = true))
    }

    @Test
    fun discovery_changes_come_from_local_pins_and_ignore_the_server_flag() {
        val pin = pin_of(first)
        val keys = listOf(
            ExternalKeyInfo(email = "pat@example.com", found = true, public_key = second.armored_public_key),
            ExternalKeyInfo(email = "sam@example.com", found = true, public_key = other.armored_public_key),
            ExternalKeyInfo(email = "lee@example.com", found = false),
        )
        val changes = ExternalKeyPinRule.local_changes(keys) { email ->
            ExternalKeyPinRead(readable = true, fingerprint = if (email == "pat@example.com") pin else null)
        }
        assertEquals(1, changes.size)
        assertEquals("pat@example.com", changes.single().email)
        assertEquals(pin, changes.single().prior_fingerprint)
    }

    @Test
    fun local_changes_take_the_place_of_server_reports_for_the_same_address() {
        val local = listOf(RecipientKeyChange("pat@example.com", "aa", "bb"))
        val server = listOf(
            RecipientKeyChange("pat@example.com", "cc", "dd"),
            RecipientKeyChange("sam@example.com", "ee", "ff"),
        )
        assertEquals(
            listOf(local.single(), server[1]),
            ExternalKeyPinRule.merge_changes(server, local),
        )
    }

    @Test
    fun an_unreadable_pin_store_never_counts_as_first_use() {
        val vetting = ExternalKeyPinRule.vet_armored("pat@example.com", first.armored_public_key, null, pin_readable = false)
        assertTrue(vetting is ExternalKeyVetting.Unverified)
        assertEquals(ExternalKeySendStep.STOP_UNREADABLE, ExternalKeyPinRule.send_step(vetting))
    }

    @Test
    fun an_unreadable_pin_store_shows_as_a_change_before_sending() {
        val keys = listOf(
            ExternalKeyInfo(email = "pat@example.com", found = true, public_key = first.armored_public_key),
            ExternalKeyInfo(email = "lee@example.com", found = false),
        )
        val changes = ExternalKeyPinRule.local_changes(keys) { ExternalKeyPinRead(readable = false, fingerprint = null) }
        assertEquals(1, changes.size)
        assertEquals("pat@example.com", changes.single().email)
        assertEquals(ExternalKeyPinRule.UNREADABLE_PRIOR, changes.single().prior_fingerprint)
        assertTrue(changes.single().new_fingerprint.isNotEmpty())
    }

    @Test
    fun only_a_first_use_key_is_pinned_while_sending() {
        val first_use = ExternalKeyPinRule.vet_armored("pat@example.com", first.armored_public_key, null, explicit = true)
        assertEquals(ExternalKeySendStep.USE_AND_PIN, ExternalKeyPinRule.send_step(first_use))
        val pin = (first_use as ExternalKeyVetting.Accepted).fingerprint
        val again = ExternalKeyPinRule.vet_armored("pat@example.com", first.armored_public_key, pin)
        assertEquals(ExternalKeySendStep.USE, ExternalKeyPinRule.send_step(again))
    }

    @Test
    fun a_changed_key_stops_a_send_until_it_is_confirmed() {
        val pin = pin_of(first)
        val changed = ExternalKeyPinRule.vet_armored("pat@example.com", second.armored_public_key, pin)
        assertEquals(ExternalKeySendStep.STOP_CHANGED, ExternalKeyPinRule.send_step(changed))
        val confirmed = (changed as ExternalKeyVetting.Changed).new_fingerprint
        val after = ExternalKeyPinRule.vet_armored("pat@example.com", second.armored_public_key, confirmed)
        assertEquals(ExternalKeySendStep.USE, ExternalKeyPinRule.send_step(after))
    }

    @Test
    fun a_key_without_the_address_falls_back_to_the_encryption_rule() {
        assertEquals(ExternalKeySendStep.SKIP_OR_REQUIRE, ExternalKeyPinRule.send_step(ExternalKeyVetting.Rejected))
        assertFalse(org.astermail.android.crypto.RecipientEncryptionRules.copy_failure_throws(false, false, false, false))
        assertTrue(org.astermail.android.crypto.RecipientEncryptionRules.copy_failure_throws(false, false, false, true))
    }

    @Test
    fun a_missing_pin_store_reads_as_unreadable_and_refuses_writes() {
        val missing = ExternalKeyPinStore { null }
        assertEquals(ExternalKeyPinRead(readable = false, fingerprint = null), missing.read("account-1", "pat@example.com"))
        assertFalse(missing.pin("account-1", "pat@example.com", "ab".repeat(20)))
        val broken = ExternalKeyPinStore { throw IllegalStateException("closed") }
        assertFalse(broken.read("account-1", "pat@example.com").readable)
        assertFalse(broken.pin("account-1", "pat@example.com", "ab".repeat(20)))
    }

    @Test
    fun an_acknowledgment_only_matches_the_send_it_was_given_for() {
        val acknowledgments = ExternalKeyAcknowledgments()
        val fingerprint = "ab".repeat(20)
        assertTrue(acknowledgments.note("account-1", "draft-1", "pat@example.com", fingerprint))
        assertTrue(acknowledgments.matches("account-1", "draft-1", "Pat@Example.com", fingerprint.uppercase()))
        assertFalse(acknowledgments.matches("account-1", "draft-2", "pat@example.com", fingerprint))
        assertFalse(acknowledgments.matches("account-1", null, "pat@example.com", fingerprint))
        assertFalse(acknowledgments.matches("account-2", "draft-1", "pat@example.com", fingerprint))
        assertFalse(acknowledgments.matches("account-1", "draft-1", "sam@example.com", fingerprint))
        assertFalse(acknowledgments.matches("account-1", "draft-1", "pat@example.com", "cd".repeat(20)))
        assertFalse(acknowledgments.note("account-1", null, "pat@example.com", fingerprint))
        assertFalse(acknowledgments.note("account-1", " ", "pat@example.com", fingerprint))
    }

    @Test
    fun an_acknowledgment_stays_until_its_send_uses_it_up() {
        val acknowledgments = ExternalKeyAcknowledgments()
        val fingerprint = "ab".repeat(20)
        acknowledgments.note("account-1", "draft-1", "pat@example.com", fingerprint)
        assertTrue(acknowledgments.matches("account-1", "draft-1", "pat@example.com", fingerprint))
        assertTrue(acknowledgments.matches("account-1", "draft-1", "pat@example.com", fingerprint))
        assertFalse(acknowledgments.consume("account-1", "draft-2", "pat@example.com", fingerprint))
        assertTrue(acknowledgments.matches("account-1", "draft-1", "pat@example.com", fingerprint))
        assertTrue(acknowledgments.consume("account-1", "draft-1", "pat@example.com", fingerprint))
        assertFalse(acknowledgments.matches("account-1", "draft-1", "pat@example.com", fingerprint))
    }

    @Test
    fun dropping_an_abandoned_send_forgets_only_its_acknowledgments() {
        val acknowledgments = ExternalKeyAcknowledgments()
        val fingerprint = "ab".repeat(20)
        acknowledgments.note("account-1", "pending:1", "pat@example.com", fingerprint)
        acknowledgments.note("account-1", "pending:2", "pat@example.com", fingerprint)
        acknowledgments.drop("pending:1")
        assertFalse(acknowledgments.has_scope("pending:1"))
        assertFalse(acknowledgments.matches("account-1", "pending:1", "pat@example.com", fingerprint))
        assertTrue(acknowledgments.matches("account-1", "pending:2", "pat@example.com", fingerprint))
        acknowledgments.drop(null)
        acknowledgments.drop(" ")
        assertTrue(acknowledgments.has_scope("pending:2"))
    }

    @Test
    fun a_queued_send_takes_over_the_acknowledgments_of_its_compose_session() {
        val acknowledgments = ExternalKeyAcknowledgments()
        val fingerprint = "ab".repeat(20)
        acknowledgments.note("account-1", "draft-1", "pat@example.com", fingerprint)
        acknowledgments.note("account-1", "draft-2", "sam@example.com", fingerprint)
        acknowledgments.move("draft-1", "pending:1")
        assertFalse(acknowledgments.matches("account-1", "draft-1", "pat@example.com", fingerprint))
        assertTrue(acknowledgments.matches("account-1", "pending:1", "pat@example.com", fingerprint))
        assertFalse(acknowledgments.matches("account-1", "pending:1", "sam@example.com", fingerprint))
        assertTrue(acknowledgments.matches("account-1", "draft-2", "sam@example.com", fingerprint))
        acknowledgments.move(null, "pending:1")
        acknowledgments.move("draft-2", null)
        assertTrue(acknowledgments.matches("account-1", "draft-2", "sam@example.com", fingerprint))
    }

    @Test
    fun pins_are_kept_per_account_without_plain_addresses() {
        val key = ExternalKeyPinRule.entry_key("account-1", "Pat@Example.com")
        assertEquals(key, ExternalKeyPinRule.entry_key("account-1", "pat@example.com"))
        assertNotEquals(key, ExternalKeyPinRule.entry_key("account-2", "pat@example.com"))
        assertTrue(!key.contains("pat"))
    }
}
