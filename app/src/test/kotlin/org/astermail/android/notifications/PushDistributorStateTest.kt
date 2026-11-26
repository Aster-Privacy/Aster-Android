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
package org.astermail.android.notifications

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.EntryPointAccessors
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.astermail.android.storage.SecurePrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.unifiedpush.android.connector.UnifiedPush

class PushDistributorStateTest {

    private class MemoryPrefs : SharedPreferences {
        val values = java.util.concurrent.ConcurrentHashMap<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(values)
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun edit(): SharedPreferences.Editor = MemoryEditor(values)
    }

    private class MemoryEditor(private val target: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val puts = linkedMapOf<String, Any>()
        private val removals = mutableListOf<String>()

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            if (key == null) return this
            if (value == null) removals.add(key) else puts[key] = value
            return this
        }

        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removals.add(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            target.clear()
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            removals.forEach { target.remove(it) }
            target.putAll(puts)
        }
    }

    private val own = "org.astermail.android"
    private val ntfy = "io.heckel.ntfy"
    private val push_prefs = MemoryPrefs()
    private val token_prefs = MemoryPrefs()
    private val legacy_prefs = MemoryPrefs()
    private var installed = listOf(own, ntfy)
    private lateinit var context: Context
    private lateinit var registrations: CountDownLatch

    private fun reset_prefs_cache() {
        UnifiedPushState::class.java.getDeclaredField("secure_prefs").apply {
            isAccessible = true
            set(null, null)
        }
    }

    @Before
    fun set_up() {
        reset_prefs_cache()
        registrations = CountDownLatch(1)
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.packageName } returns own
        every { context.getSharedPreferences(any(), any()) } returns legacy_prefs
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } answers {
            if (secondArg<String>() == "aster_tokens_v1") token_prefs else push_prefs
        }
        mockkObject(PushDistributorNotifier)
        every { PushDistributorNotifier.notify_fallback(any(), any()) } returns true
        mockkStatic(UnifiedPush::class)
        every { UnifiedPush.getDistributors(any()) } answers { installed }
        every { UnifiedPush.getAckDistributor(any()) } returns null
        every { UnifiedPush.saveDistributor(any(), any()) } just Runs
        every { UnifiedPush.register(any(), any(), any(), any()) } answers { registrations.countDown() }
        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any(), UnifiedPushState.ApiClientEntryPoint::class.java)
        } throws IllegalStateException("network")
        token_prefs.values["access_token"] = "access"
        token_prefs.values["refresh_token"] = "refresh"
        push_prefs.values["endpoint_url"] = "https://push.example.test/old"
        push_prefs.values["pending_p256dh"] = "p256dh"
        push_prefs.values["pending_auth"] = "auth"
    }

    @After
    fun tear_down() {
        unmockkAll()
    }

    @Test
    fun choosing_a_distributor_persists_it_and_registers_with_it() {
        assumeTrue(UnifiedPushState.distributor_choice_supported())

        UnifiedPushState.choose_distributor(context, ntfy, "ntfy")

        assertEquals(ntfy, push_prefs.values["distributor_choice"])
        assertEquals("ntfy", push_prefs.values["distributor_choice_label"])
        assertFalse(push_prefs.contains("endpoint_url"))
        assertFalse(push_prefs.contains("pending_p256dh"))
        assertTrue(registrations.await(5, TimeUnit.SECONDS))
        verify { UnifiedPush.saveDistributor(context, ntfy) }
        assertEquals(ntfy, UnifiedPushState.current_distributor(context))
    }

    @Test
    fun choice_survives_a_restart() {
        assumeTrue(UnifiedPushState.distributor_choice_supported())
        UnifiedPushState.choose_distributor(context, ntfy, "ntfy")
        assertTrue(registrations.await(5, TimeUnit.SECONDS))

        reset_prefs_cache()

        assertEquals(ntfy, UnifiedPushState.distributor_choice(context))
        assertEquals(ntfy, UnifiedPushState.current_distributor(context))
        assertFalse(UnifiedPushState.distributor_missing(context))
    }

    @Test
    fun choosing_built_in_clears_the_external_label() {
        assumeTrue(UnifiedPushState.distributor_choice_supported())
        push_prefs.values["distributor_choice"] = ntfy
        push_prefs.values["distributor_choice_label"] = "ntfy"
        push_prefs.values["distributor_fallback_label"] = "ntfy"

        UnifiedPushState.choose_distributor(context, own, null)

        assertEquals(own, push_prefs.values["distributor_choice"])
        assertFalse(push_prefs.contains("distributor_choice_label"))
        assertNull(UnifiedPushState.fallback_notice(context))
        assertTrue(registrations.await(5, TimeUnit.SECONDS))
        verify { UnifiedPush.saveDistributor(context, own) }
        assertEquals(own, UnifiedPushState.current_distributor(context))
    }

    @Test
    fun uninstalled_choice_falls_back_to_built_in_and_tells_the_user() {
        assumeTrue(UnifiedPushState.distributor_choice_supported())
        push_prefs.values["distributor_choice"] = ntfy
        push_prefs.values["distributor_choice_label"] = "ntfy"
        installed = listOf(own)

        assertTrue(UnifiedPushState.distributor_missing(context))
        assertTrue(UnifiedPushState.has_pending_registration(context))
        UnifiedPushState.sync_registration(context)

        assertFalse(push_prefs.contains("distributor_choice"))
        assertEquals("ntfy", UnifiedPushState.fallback_notice(context))
        assertFalse(push_prefs.contains("endpoint_url"))
        verify { PushDistributorNotifier.notify_fallback(context, "ntfy") }
        assertTrue(registrations.await(5, TimeUnit.SECONDS))
        verify { UnifiedPush.saveDistributor(context, own) }
        assertFalse(UnifiedPushState.distributor_missing(context))
        assertEquals(own, UnifiedPushState.current_distributor(context))

        UnifiedPushState.dismiss_fallback_notice(context)
        assertNull(UnifiedPushState.fallback_notice(context))
    }

    @Test
    fun fdroid_build_ignores_distributor_choice() {
        assumeFalse(UnifiedPushState.distributor_choice_supported())
        push_prefs.values["distributor_choice"] = ntfy
        installed = listOf(own)

        UnifiedPushState.choose_distributor(context, own, null)

        assertEquals(ntfy, push_prefs.values["distributor_choice"])
        assertFalse(UnifiedPushState.distributor_missing(context))
        assertFalse(registrations.await(300, TimeUnit.MILLISECONDS))
    }
}
