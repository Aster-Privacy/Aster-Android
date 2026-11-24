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

package org.astermail.android.settings

import org.astermail.android.api.encryption.EncryptionSettings
import org.astermail.android.api.encryption.KeyserverStatusResponse
import org.astermail.android.api.encryption.PgpKeyInfo
import org.astermail.android.api.encryption.RecoveryCodesStatus
import org.astermail.android.api.encryption.WkdStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptionDisplayCacheTest {
    private val identity = encryption_identity_display(fingerprint = "ABCD1234", is_pgp = false, available = true)
    private val key = encryption_key_display(algorithm = "ed25519", key_size = 256, created_at = "2026-01-02T00:00:00Z")
    private val recovery = encryption_recovery_display(available = 7, total = 10)
    private val settings = encryption_settings_display(
        auto_discover_keys = true,
        encrypt_by_default = false,
        require_encryption = true,
    )

    @Test
    fun empty_memo_reads_null() {
        assertNull(encryption_display_memo().read("user-a"))
    }

    @Test
    fun read_returns_snapshot_for_same_account() {
        val memo = encryption_display_memo()
        val snapshot = encryption_display_snapshot(identity = identity, key = key)
        memo.write("user-a", snapshot)
        assertEquals(snapshot, memo.read("user-a"))
    }

    @Test
    fun read_ignores_other_account() {
        val memo = encryption_display_memo()
        memo.write("user-a", encryption_display_snapshot(identity = identity))
        assertNull(memo.read("user-b"))
    }

    @Test
    fun blank_or_null_account_never_reads_or_writes() {
        val memo = encryption_display_memo()
        memo.write("", encryption_display_snapshot(identity = identity))
        memo.write(null, encryption_display_snapshot(identity = identity))
        assertNull(memo.read(""))
        assertNull(memo.read(null))
        memo.write("user-a", encryption_display_snapshot(identity = identity))
        assertNull(memo.read(""))
        assertNull(memo.read(null))
    }

    @Test
    fun clear_drops_snapshot() {
        val memo = encryption_display_memo()
        memo.write("user-a", encryption_display_snapshot(identity = identity))
        memo.clear()
        assertNull(memo.read("user-a"))
    }

    @Test
    fun write_for_same_account_merges_fields() {
        val memo = encryption_display_memo()
        memo.write("user-a", encryption_display_snapshot(identity = identity, recovery = recovery))
        memo.write("user-a", encryption_display_snapshot(key = key, settings = settings, wkd_published = true))
        val read = memo.read("user-a")
        assertEquals(identity, read?.identity)
        assertEquals(key, read?.key)
        assertEquals(recovery, read?.recovery)
        assertEquals(settings, read?.settings)
        assertEquals(true, read?.wkd_published)
        assertNull(read?.keyserver_published)
    }

    @Test
    fun write_for_new_account_replaces_previous_account() {
        val memo = encryption_display_memo()
        memo.write("user-a", encryption_display_snapshot(identity = identity, recovery = recovery))
        memo.write("user-b", encryption_display_snapshot(key = key))
        assertNull(memo.read("user-a"))
        val read = memo.read("user-b")
        assertNull(read?.identity)
        assertNull(read?.recovery)
        assertEquals(key, read?.key)
    }

    @Test
    fun overlay_prefers_newer_non_null_values() {
        val older = encryption_display_snapshot(
            identity = identity,
            recovery = recovery,
            wkd_published = true,
            keyserver_published = false,
        )
        val newer_recovery = encryption_recovery_display(available = 3, total = 10)
        val merged = older.overlaid_with(
            encryption_display_snapshot(recovery = newer_recovery, wkd_published = false),
        )
        assertEquals(identity, merged.identity)
        assertEquals(newer_recovery, merged.recovery)
        assertEquals(false, merged.wkd_published)
        assertEquals(false, merged.keyserver_published)
    }

    @Test
    fun snapshot_of_all_null_inputs_is_empty() {
        assertEquals(
            encryption_display_snapshot(),
            encryption_display_snapshot_of(null, null, null, null, null, null),
        )
    }

    @Test
    fun snapshot_of_blank_key_info_has_no_key() {
        val snapshot = encryption_display_snapshot_of(null, PgpKeyInfo(), null, null, null, null)
        assertNull(snapshot.key)
    }

    @Test
    fun snapshot_of_key_info_keeps_only_display_fields() {
        val info = PgpKeyInfo(
            fingerprint = "FFFF",
            algorithm = "ed25519",
            key_size = 256,
            created_at = "2026-01-02T00:00:00Z",
            public_key_armored = "-----BEGIN PGP PUBLIC KEY BLOCK-----",
        )
        val snapshot = encryption_display_snapshot_of(null, info, null, null, null, null)
        assertEquals(key, snapshot.key)
    }

    @Test
    fun snapshot_of_recovery_without_codes_is_null() {
        val snapshot = encryption_display_snapshot_of(
            null,
            null,
            RecoveryCodesStatus(total_codes = 0, available_codes = 0),
            null,
            null,
            null,
        )
        assertNull(snapshot.recovery)
    }

    @Test
    fun snapshot_of_recovery_coerces_available_into_range() {
        val over = encryption_display_snapshot_of(
            null, null, RecoveryCodesStatus(total_codes = 10, available_codes = 14), null, null, null,
        )
        val under = encryption_display_snapshot_of(
            null, null, RecoveryCodesStatus(total_codes = 10, available_codes = -2), null, null, null,
        )
        assertEquals(encryption_recovery_display(available = 10, total = 10), over.recovery)
        assertEquals(encryption_recovery_display(available = 0, total = 10), under.recovery)
    }

    @Test
    fun snapshot_of_maps_settings_and_publishing_flags() {
        val snapshot = encryption_display_snapshot_of(
            identity,
            null,
            null,
            EncryptionSettings(auto_discover_keys = true, encrypt_by_default = false, require_encryption = true),
            WkdStatusResponse(published = true),
            KeyserverStatusResponse(published = false),
        )
        assertEquals(identity, snapshot.identity)
        assertEquals(settings, snapshot.settings)
        assertTrue(snapshot.wkd_published == true)
        assertFalse(snapshot.keyserver_published ?: true)
    }
}
