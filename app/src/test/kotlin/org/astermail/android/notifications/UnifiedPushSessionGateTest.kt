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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.astermail.android.storage.SecurePrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unifiedpush.android.connector.UnifiedPush

class UnifiedPushSessionGateTest {

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

    private val push_prefs = MemoryPrefs()
    private val token_prefs = MemoryPrefs()
    private val legacy_prefs = MemoryPrefs()
    private lateinit var context: Context
    private lateinit var distributor_lookups: CountDownLatch
    private lateinit var api_requests: CountDownLatch

    @Before
    fun set_up() {
        UnifiedPushState::class.java.getDeclaredField("secure_prefs").apply {
            isAccessible = true
            set(null, null)
        }
        distributor_lookups = CountDownLatch(1)
        api_requests = CountDownLatch(1)
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns legacy_prefs
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } answers {
            if (secondArg<String>() == "aster_tokens_v1") token_prefs else push_prefs
        }
        mockkStatic(UnifiedPush::class)
        every { UnifiedPush.getDistributors(any()) } answers {
            distributor_lookups.countDown()
            listOf("org.example.distributor")
        }
        every { UnifiedPush.getAckDistributor(any()) } returns "org.example.distributor"
        every { UnifiedPush.register(any(), any(), any(), any()) } just Runs
        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any(), UnifiedPushState.ApiClientEntryPoint::class.java)
        } answers {
            api_requests.countDown()
            throw IllegalStateException("network")
        }
    }

    @After
    fun tear_down() {
        unmockkAll()
    }

    private fun sign_in() {
        token_prefs.values["access_token"] = "access"
        token_prefs.values["refresh_token"] = "refresh"
    }

    private fun store_pending_endpoint() {
        push_prefs.values["endpoint_url"] = "https://push.example.test/endpoint"
        push_prefs.values["pending_p256dh"] = "p256dh"
        push_prefs.values["pending_auth"] = "auth"
    }

    @Test
    fun signed_out_startup_sync_requests_no_push_token() {
        UnifiedPushState.sync_registration(context)

        assertFalse(distributor_lookups.await(500, TimeUnit.MILLISECONDS))
        assertFalse(api_requests.await(100, TimeUnit.MILLISECONDS))
        assertFalse(push_prefs.contains("last_register_attempt_at"))
    }

    @Test
    fun signed_out_startup_sync_does_not_post_pending_endpoint() {
        store_pending_endpoint()

        UnifiedPushState.sync_registration(context)

        assertFalse(push_prefs.contains("last_subscribe_attempt_at"))
        assertFalse(api_requests.await(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun signed_out_new_endpoint_is_not_posted() {
        UnifiedPushState.register_with_backend(
            context = context,
            endpoint_url = "https://push.example.test/endpoint",
            p256dh = "p256dh",
            auth = "auth",
        )

        assertFalse(push_prefs.contains("last_subscribe_attempt_at"))
        assertFalse(api_requests.await(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun signed_out_vapid_retry_fetches_nothing() {
        UnifiedPushState.reregister_with_vapid(context)

        assertFalse(api_requests.await(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun signed_in_startup_sync_requests_push_token() {
        sign_in()

        UnifiedPushState.sync_registration(context)

        assertTrue(distributor_lookups.await(5, TimeUnit.SECONDS))
        assertTrue(api_requests.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun signed_in_startup_sync_posts_pending_endpoint() {
        sign_in()
        store_pending_endpoint()

        UnifiedPushState.sync_registration(context)

        assertTrue(push_prefs.contains("last_subscribe_attempt_at"))
        assertTrue(api_requests.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun sign_in_after_signed_out_launch_registers() {
        UnifiedPushState.sync_registration(context)
        assertFalse(distributor_lookups.await(300, TimeUnit.MILLISECONDS))

        sign_in()
        UnifiedPushState.clear_backend_registration(context)
        UnifiedPushState.sync_registration(context)

        assertTrue(distributor_lookups.await(5, TimeUnit.SECONDS))
        assertTrue(api_requests.await(5, TimeUnit.SECONDS))
    }
}
