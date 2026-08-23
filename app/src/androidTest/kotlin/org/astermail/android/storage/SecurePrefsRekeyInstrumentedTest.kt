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

package org.astermail.android.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurePrefsRekeyInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val migration_prefs = "aster_secure_prefs_migration"
    private val marker_prefix = "rekeyed_v2_"
    private val access_token = "test_access_value"
    private val refresh_token = "test_refresh_value"

    private fun unique_name(tag: String): String =
        "test_secure_prefs_${tag}_${System.nanoTime()}"

    private fun shared_prefs_dir(): File =
        File(context.filesDir.parentFile, "shared_prefs")

    private fun legacy_master_key(): MasterKey =
        MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun write_legacy(name: String, entries: Map<String, Any>) {
        val prefs = EncryptedSharedPreferences.create(
            context,
            name,
            legacy_master_key(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val editor = prefs.edit()
        for ((key, value) in entries) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        assertTrue(editor.commit())
    }

    private fun clear_marker(name: String) {
        context.getSharedPreferences(migration_prefs, Context.MODE_PRIVATE)
            .edit()
            .remove(marker_prefix + name)
            .commit()
    }

    private fun marker_set(name: String): Boolean =
        context.getSharedPreferences(migration_prefs, Context.MODE_PRIVATE)
            .getBoolean(marker_prefix + name, false)

    @Test
    fun legacy_prefs_are_rekeyed_without_losing_the_session() {
        val name = unique_name("session")
        write_legacy(
            name,
            mapOf(
                "access_token" to access_token,
                "refresh_token" to refresh_token,
                "biometric_enabled" to true,
                "failed_attempts" to 3,
                "known_devices" to setOf("device_a", "device_b"),
            ),
        )
        assertTrue(File(shared_prefs_dir(), "$name.xml").exists())

        val prefs = SecurePrefs.open(context, name)

        assertEquals(access_token, prefs.getString("access_token", null))
        assertEquals(refresh_token, prefs.getString("refresh_token", null))
        assertTrue(prefs.getBoolean("biometric_enabled", false))
        assertEquals(3, prefs.getInt("failed_attempts", 0))
        assertEquals(setOf("device_a", "device_b"), prefs.getStringSet("known_devices", emptySet()))
        assertTrue(marker_set(name))
        assertTrue(File(shared_prefs_dir(), "${name}_v2.xml").exists())
        assertFalse(File(shared_prefs_dir(), "$name.xml").exists())
    }

    @Test
    fun rekeyed_prefs_survive_a_second_process_start() {
        val name = unique_name("restart")
        write_legacy(name, mapOf("access_token" to access_token))

        SecurePrefs.open(context, name)
        SecurePrefs.forget_for_test(name)
        val reopened = SecurePrefs.open(context, name)

        assertEquals(access_token, reopened.getString("access_token", null))
    }

    @Test
    fun an_interrupted_rekey_replays_from_the_legacy_file() {
        val name = unique_name("interrupted")
        write_legacy(name, mapOf("access_token" to access_token, "stale" to "drop_me"))

        SecurePrefs.open(context, name)
        SecurePrefs.forget_for_test(name)

        val target = SecurePrefs.open(context, name)
        target.edit().putString("access_token", "half_written").remove("stale").commit()
        SecurePrefs.forget_for_test(name)

        write_legacy(name, mapOf("access_token" to access_token, "stale" to "drop_me"))
        clear_marker(name)

        val replayed = SecurePrefs.open(context, name)

        assertEquals(access_token, replayed.getString("access_token", null))
        assertEquals("drop_me", replayed.getString("stale", null))
    }

    @Test
    fun a_fresh_namespace_opens_without_a_legacy_file() {
        val name = unique_name("fresh")

        val prefs = SecurePrefs.open(context, name)
        prefs.edit().putString("access_token", access_token).commit()

        assertTrue(marker_set(name))
        assertNull(prefs.getString("missing", null))
        assertEquals(access_token, prefs.getString("access_token", null))
        assertFalse(File(shared_prefs_dir(), "$name.xml").exists())
    }

    @Test
    fun repeated_opens_share_one_instance() {
        val name = unique_name("cached")

        val first = SecurePrefs.open(context, name)
        val second = SecurePrefs.open(context, name)

        assertSame(first, second)
    }

    @Test
    fun warming_opens_every_namespace_once() {
        val names = List(6) { unique_name("warm_$it") }
        for (name in names) {
            write_legacy(name, mapOf("access_token" to "$access_token-$name"))
        }

        SecurePrefs.warm(context, names)

        for (name in names) {
            assertEquals(
                "$access_token-$name",
                SecurePrefs.open(context, name).getString("access_token", null),
            )
        }
    }
}
