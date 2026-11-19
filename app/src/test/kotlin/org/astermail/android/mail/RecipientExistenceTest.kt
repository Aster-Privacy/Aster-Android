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

import kotlinx.coroutines.runBlocking
import org.astermail.android.api.ApiError
import org.astermail.android.mail.ratchet.PostQuantumCoverage
import org.astermail.android.mail.ratchet.PostQuantumUnavailableException
import org.astermail.android.mail.ratchet.RatchetEncryptionException
import org.astermail.android.mail.ratchet.RecipientKeyUntrustedException
import org.astermail.android.mail.ratchet.RecipientPublicKeyMissingException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecipientExistenceTest {

    private val registered = setOf("lucas@astermail.org", "nopq@aster.cx")

    private val lookups = mutableListOf<Pair<String, String>>()

    private val directory: suspend (String, String) -> Unit = { username, email ->
        lookups.add(username to email)
        if (email.trim().lowercase() !in registered) throw ApiError.NotFoundError
    }

    private val find: suspend (List<String>) -> List<String> = { recipients ->
        collect_unregistered_aster_addresses(recipients, directory)
    }

    @Before
    fun reset_before() {
        HostedRecipientDirectory.clear()
        lookups.clear()
    }

    @After
    fun reset_after() = HostedRecipientDirectory.clear()

    @Test
    fun aster_address_without_account_is_unregistered() = runBlocking {
        assertTrue(is_unregistered_aster_address("nobody@astermail.org", directory))
        assertEquals(listOf("nobody" to "nobody@astermail.org"), lookups)
    }

    @Test
    fun aster_address_with_account_is_registered() = runBlocking {
        assertFalse(is_unregistered_aster_address("lucas@astermail.org", directory))
    }

    @Test
    fun only_not_found_counts_as_missing() = runBlocking {
        val failures = listOf(
            ApiError.ServerError(500),
            ApiError.NetworkError,
            ApiError.UnauthorizedError,
            ApiError.RateLimited(),
            IllegalStateException("boom"),
        )
        for (failure in failures) {
            val unregistered = is_unregistered_aster_address("nobody@astermail.org") { _, _ -> throw failure }
            assertFalse("$failure must not block the send", unregistered)
        }
    }

    @Test
    fun external_and_ghost_addresses_are_never_looked_up() = runBlocking {
        HostedRecipientDirectory.record("owner@example-domain.de", internal = true)
        val outside = listOf(
            "someone@example.com",
            "owner@example-domain.de",
            "friend@wkd-provider.example",
            "masked@realiased.me",
        )
        assertEquals(emptyList<String>(), collect_unregistered_aster_addresses(outside, directory))
        assertEquals(emptyList<Pair<String, String>>(), lookups)
    }

    @Test
    fun mixed_list_reports_only_the_missing_aster_address() = runBlocking {
        val recipients = listOf("someone@example.com", "lucas@astermail.org", "Nobody@Aster.cx", "nobody@aster.cx")
        assertEquals(listOf("Nobody@Aster.cx"), collect_unregistered_aster_addresses(recipients, directory))
    }

    @Test
    fun external_recipients_are_not_post_quantum_candidates() {
        HostedRecipientDirectory.record("owner@example-domain.de", internal = true)
        val recipients = listOf(
            "someone@example.com",
            "friend@wkd-provider.example",
            "lucas@astermail.org",
            "owner@example-domain.de",
        )
        assertEquals(listOf("lucas@astermail.org", "owner@example-domain.de"), post_quantum_candidates(recipients))
        assertEquals(emptyList<String>(), post_quantum_candidates(listOf("someone@example.com", "a@b.example")))
    }

    @Test
    fun unregistered_aster_address_blocks_without_prompt() = runBlocking {
        val gate = post_quantum_send_gate(PostQuantumCoverage(missing = listOf("nobody@astermail.org")), find)
        assertEquals(PostQuantumSendGate.NotOnAster(listOf("nobody@astermail.org")), gate)
    }

    @Test
    fun real_account_without_post_quantum_keys_still_prompts() = runBlocking {
        val coverage = PostQuantumCoverage(missing = listOf("nopq@aster.cx"), downgraded = listOf("nopq@aster.cx"))
        assertEquals(PostQuantumSendGate.Prompt(coverage), post_quantum_send_gate(coverage, find))
    }

    @Test
    fun server_error_during_existence_check_keeps_the_prompt() = runBlocking {
        val coverage = PostQuantumCoverage(missing = listOf("nobody@astermail.org"))
        val gate = post_quantum_send_gate(coverage) { recipients ->
            collect_unregistered_aster_addresses(recipients) { _, _ -> throw ApiError.ServerError(503) }
        }
        assertEquals(PostQuantumSendGate.Prompt(coverage), gate)
    }

    @Test
    fun full_coverage_proceeds_without_lookup() = runBlocking {
        assertEquals(PostQuantumSendGate.Proceed, post_quantum_send_gate(PostQuantumCoverage(), find))
        assertEquals(emptyList<Pair<String, String>>(), lookups)
    }

    @Test
    fun send_path_failure_for_missing_account_names_the_address() = runBlocking {
        val recipients = listOf("lucas@astermail.org", "nobody@astermail.org")
        val failures = listOf(
            PostQuantumUnavailableException(listOf("nobody@astermail.org")),
            RatchetEncryptionException("nobody@astermail.org", "no prekey bundle available for recipient"),
            RecipientPublicKeyMissingException(listOf("nobody@astermail.org")),
        )
        for (failure in failures) {
            assertEquals(listOf("nobody@astermail.org"), recipients_not_on_aster_for_failure(failure, recipients, find))
        }
    }

    @Test
    fun send_path_failure_for_real_accounts_is_unchanged() = runBlocking {
        val failure = PostQuantumUnavailableException(listOf("nopq@aster.cx"))
        assertEquals(emptyList<String>(), recipients_not_on_aster_for_failure(failure, listOf("nopq@aster.cx"), find))
    }

    @Test
    fun identity_pin_failure_skips_the_existence_check() = runBlocking {
        val failure = RecipientKeyUntrustedException("nobody@astermail.org", "recipient key is not trusted")
        assertEquals(emptyList<String>(), recipients_not_on_aster_for_failure(failure, listOf("nobody@astermail.org"), find))
        assertEquals(emptyList<Pair<String, String>>(), lookups)
    }

    @Test
    fun missing_account_is_a_permanent_failure_with_its_own_reason() {
        val error = RecipientNotOnAsterException(listOf("nobody@astermail.org"), "No Aster Mail account uses nobody@astermail.org.")
        assertEquals(SendFailureReason.RECIPIENT_NOT_FOUND, send_failure_reason_for(error))
        assertTrue(is_permanent_send_failure_cause(error))
        assertEquals(SendFailureReason.RECIPIENT_NOT_FOUND, SendFailureReason.from_code("recipient_not_found"))
    }
}
