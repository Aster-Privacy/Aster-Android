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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.UserInfo
import org.astermail.android.api.autoforward.AutoForwardApi
import org.astermail.android.api.autoforward.ForwardingRule
import org.astermail.android.api.autoforward.ForwardingRulesResponse
import org.astermail.android.api.developer.ApiKeyInfo
import org.astermail.android.api.developer.ApiKeyListResponse
import org.astermail.android.api.developer.DeveloperApi
import org.astermail.android.api.developer.WebhookInfo
import org.astermail.android.api.developer.WebhookListResponse
import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.ghost.GhostAliasApi
import org.astermail.android.api.ghost.GhostAliasListResponse
import org.astermail.android.api.labels.CreateLabelResponse
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.LabelsApi
import org.astermail.android.api.labels.LabelsListResponse
import org.astermail.android.api.labels.ReferralInfoResponse
import org.astermail.android.api.tags.TagsApi
import org.astermail.android.api.preferences.PreferencesApi
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.AliasListResponse
import org.astermail.android.api.settings.DeletedAliasInfo
import org.astermail.android.api.settings.ListDeletedAliasesResponse
import org.astermail.android.api.settings.BlockedSenderInfo
import org.astermail.android.api.settings.BlockedSendersResponse
import org.astermail.android.api.settings.SecurityStatusResponse
import org.astermail.android.api.settings.SessionInfo
import org.astermail.android.api.settings.SessionListResponse
import org.astermail.android.api.settings.SettingsApi
import org.astermail.android.api.settings.StorageOverview
import org.astermail.android.api.settings.SubscriptionInfo
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.api.user.UpdateDisplayNameResponse
import org.astermail.android.api.user.UserApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.TokenStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var auth_api: AuthApi
    private lateinit var user_api: UserApi
    private lateinit var settings_api: SettingsApi
    private lateinit var labels_api: LabelsApi
    private lateinit var family_api: org.astermail.android.api.family.FamilyApi
    private lateinit var tags_api: TagsApi
    private lateinit var preferences_api: PreferencesApi
    private lateinit var signatures_api: org.astermail.android.api.signatures.SignaturesApi
    private lateinit var ghost_alias_api: GhostAliasApi
    private lateinit var auto_forward_api: AutoForwardApi
    private lateinit var developer_api: DeveloperApi
    private lateinit var subscriptions_api: SubscriptionsApi
    private lateinit var recovery_email_api: org.astermail.android.api.recovery_email.RecoveryEmailApi
    private lateinit var security_api: org.astermail.android.api.security.SecurityApi
    private lateinit var encryption_api: org.astermail.android.api.encryption.EncryptionApi
    private lateinit var alias_detail_api: org.astermail.android.api.aliases.AliasDetailApi
    private lateinit var mail_rules_api: org.astermail.android.api.mail_rules.MailRulesApi
    private lateinit var auth_repository: AuthRepository
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var token_store: TokenStore
    private lateinit var account_store: org.astermail.android.storage.AccountStore
    private lateinit var preferences_cache: org.astermail.android.storage.PreferencesCacheStore
    private lateinit var theme_store: org.astermail.android.storage.ThemeStore
    private lateinit var context: android.content.Context
    private lateinit var vm: SettingsViewModel

    private val test_data_kek = ByteArray(32) { (it + 1).toByte() }

    private fun blocked_senders_hmac_key(): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(test_data_kek + "blocked-senders-hmac-v1".toByteArray(Charsets.UTF_8))

    private fun hmac_b64(key: ByteArray, data: ByteArray): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(data))
    }

    private fun blocked_sender_token(address: String, is_domain: Boolean = false): String {
        val prefix = if (is_domain) "domain:" else ""
        return hmac_b64(blocked_senders_hmac_key(), (prefix + address).toByteArray(Charsets.UTF_8))
    }

    private fun encrypted_blocked_sender(
        address: String,
        is_domain: Boolean = false,
    ): BlockedSenderInfo {
        val token = blocked_sender_token(address, is_domain)
        val payload = """{"email":"$address","is_domain":$is_domain}"""
        val nonce = ByteArray(12) { (address[it % address.length].code + it).toByte() }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(test_data_kek, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce),
        )
        val encoder = java.util.Base64.getEncoder()
        val encrypted = encoder.encodeToString(cipher.doFinal(payload.toByteArray(Charsets.UTF_8)))
        val nonce_b64 = encoder.encodeToString(nonce)
        return BlockedSenderInfo(
            id = token,
            sender_token = token,
            encrypted_sender_data = encrypted,
            sender_data_nonce = nonce_b64,
            integrity_hash = hmac_b64(
                blocked_senders_hmac_key(),
                "$encrypted:$nonce_b64:blocked-senders-v1".toByteArray(Charsets.UTF_8),
            ),
            is_domain = is_domain,
        )
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        io.mockk.mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any(), any<Throwable>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        auth_api = mockk(relaxed = true)
        user_api = mockk(relaxed = true)
        settings_api = mockk(relaxed = true)
        labels_api = mockk(relaxed = true)
        family_api = mockk(relaxed = true)
        tags_api = mockk(relaxed = true)
        preferences_api = mockk(relaxed = true)
        signatures_api = mockk(relaxed = true)
        ghost_alias_api = mockk(relaxed = true)
        auto_forward_api = mockk(relaxed = true)
        developer_api = mockk(relaxed = true)
        subscriptions_api = mockk(relaxed = true)
        recovery_email_api = mockk(relaxed = true)
        security_api = mockk(relaxed = true)
        encryption_api = mockk(relaxed = true)
        alias_detail_api = mockk(relaxed = true)
        mail_rules_api = mockk(relaxed = true)
        auth_repository = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_data_kek() } answers { test_data_kek.copyOf() }
        token_store = mockk(relaxed = true)
        account_store = mockk(relaxed = true)
        preferences_cache = mockk(relaxed = true)
        every { auth_repository.is_signed_in } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        theme_store = mockk(relaxed = true)
        every { theme_store.theme_mode } returns
            kotlinx.coroutines.flow.MutableStateFlow(org.astermail.android.storage.ThemeMode.system)
        every { theme_store.text_size } returns
            kotlinx.coroutines.flow.MutableStateFlow(org.astermail.android.storage.TextSize.default_size)
        every { theme_store.color_theme } returns kotlinx.coroutines.flow.MutableStateFlow("aster")
        every { theme_store.custom_theme_seed } returns kotlinx.coroutines.flow.MutableStateFlow("")
        every { theme_store.custom_theme_overrides } returns
            kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
        every { theme_store.font_choice } returns kotlinx.coroutines.flow.MutableStateFlow("default")
        every { theme_store.high_contrast } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { theme_store.reduce_transparency } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { theme_store.reduce_motion } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { theme_store.compact_mode } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { theme_store.text_spacing } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { theme_store.underline_links } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { theme_store.dyslexia_font } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { theme_store.haptic_enabled } returns kotlinx.coroutines.flow.MutableStateFlow(true)
        context = mockk(relaxed = true)
        every { context.getString(org.astermail.android.R.string.something_went_wrong) } returns
            "Something went wrong"
        vm = SettingsViewModel(
            auth_api = auth_api,
            user_api = user_api,
            settings_api = settings_api,
            labels_api = labels_api,
            family_api = family_api,
            tags_api = tags_api,
            preferences_api = preferences_api,
            signatures_api = signatures_api,
            ghost_alias_api = ghost_alias_api,
            auto_forward_api = auto_forward_api,
            developer_api = developer_api,
            subscriptions_api = subscriptions_api,
            recovery_email_api = recovery_email_api,
            security_api = security_api,
            encryption_api = encryption_api,
            alias_detail_api = alias_detail_api,
            mail_rules_api = mail_rules_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        )
        vm.default_dispatcher = dispatcher
    }

    @After
    fun teardown() {
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(android.util.Base64::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() {
        val state = vm.state.value
        assertNull(state.user)
        assertTrue(state.sessions.isEmpty())
        assertTrue(state.blocked_senders.isEmpty())
        assertFalse(state.is_loading)
        assertNull(state.error)
        assertEquals(SaveStatus.IDLE, state.save_status)
    }

    @Test
    fun `load_profile fetches user info`() = runTest {
        val user = UserInfo(
            user_id = "u1",
            username = "testuser",
            email = "test@astermail.org",
            display_name = "Test User",
        )
        coEvery { auth_api.me() } returns user

        vm.load_profile()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.is_loading)
        assertEquals("testuser", state.user?.username)
        assertEquals("Test User", state.user?.display_name)
    }

    @Test
    fun `load_profile error sets error message`() = runTest {
        coEvery { auth_api.me() } throws org.astermail.android.api.ApiError.UnknownError("unauthorized")

        vm.load_profile()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("unauthorized", vm.state.value.error)
    }

    @Test
    fun `update_display_name saves and updates user`() = runTest {
        val updated_user = UserInfo(
            user_id = "u1",
            username = "testuser",
            email = "test@astermail.org",
            display_name = "New Name",
        )
        coEvery { user_api.update_display_name("New Name") } returns
            UpdateDisplayNameResponse(user = updated_user)

        vm.update_display_name("New Name")
        advanceUntilIdle()

        assertEquals("New Name", vm.state.value.user?.display_name)
        assertEquals(SaveStatus.SAVED, vm.state.value.save_status)
    }

    @Test
    fun `update_display_name error sets error status`() = runTest {
        coEvery { user_api.update_display_name(any()) } throws RuntimeException("server error")

        vm.update_display_name("Whatever")
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, vm.state.value.save_status)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `load_sessions populates sessions list`() = runTest {
        val sessions = listOf(
            SessionInfo(id = "s1", is_current = true),
            SessionInfo(id = "s2", is_current = false),
        )
        coEvery { settings_api.list_sessions() } returns SessionListResponse(sessions)

        vm.load_sessions()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.sessions.size)
        assertTrue(vm.state.value.sessions[0].is_current)
    }

    @Test
    fun `revoke_session removes session from list`() = runTest {
        val sessions = listOf(
            SessionInfo(id = "s1", is_current = true),
            SessionInfo(id = "s2", is_current = false),
            SessionInfo(id = "s3", is_current = false),
        )
        coEvery { settings_api.list_sessions() } returns SessionListResponse(sessions)

        vm.load_sessions()
        advanceUntilIdle()
        assertEquals(3, vm.state.value.sessions.size)

        coEvery { settings_api.list_sessions() } returns SessionListResponse(
            sessions.filter { it.id != "s2" },
        )

        vm.revoke_session("s2")
        advanceUntilIdle()

        assertEquals(2, vm.state.value.sessions.size)
        assertTrue(vm.state.value.sessions.none { it.id == "s2" })
        coVerify { settings_api.revoke_session("s2") }
    }

    @Test
    fun `logout_others keeps only current session`() = runTest {
        val sessions = listOf(
            SessionInfo(id = "s1", is_current = true),
            SessionInfo(id = "s2", is_current = false),
            SessionInfo(id = "s3", is_current = false),
        )
        coEvery { settings_api.list_sessions() } returns SessionListResponse(sessions)

        vm.load_sessions()
        advanceUntilIdle()

        vm.logout_others()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.sessions.size)
        assertTrue(vm.state.value.sessions[0].is_current)
        coVerify { settings_api.logout_others() }
    }

    @Test
    fun `load_blocked_senders populates list`() = runTest {
        val blocked = listOf(
            encrypted_blocked_sender("spam@evil.com"),
            encrypted_blocked_sender("scam@bad.com"),
        )
        coEvery { settings_api.list_blocked_senders() } returns BlockedSendersResponse(blocked)

        vm.load_blocked_senders()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.blocked_senders.size)
        assertEquals("spam@evil.com", vm.state.value.blocked_senders[0].address)
    }

    @Test
    fun `block_sender adds to list and calls api`() = runTest {
        vm.block_sender("newspam@evil.com")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.blocked_senders.size)
        assertEquals("newspam@evil.com", vm.state.value.blocked_senders[0].address)
        coVerify { settings_api.block_sender(any()) }
    }

    @Test
    fun `unblock_sender removes from list and calls api`() = runTest {
        val blocked = listOf(
            encrypted_blocked_sender("spam@evil.com"),
            encrypted_blocked_sender("keep@evil.com"),
        )
        coEvery { settings_api.list_blocked_senders() } returns BlockedSendersResponse(blocked)

        vm.load_blocked_senders()
        advanceUntilIdle()

        vm.unblock_sender(blocked_sender_token("spam@evil.com"))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.blocked_senders.size)
        assertEquals("keep@evil.com", vm.state.value.blocked_senders[0].address)
        coVerify { settings_api.unblock_sender(blocked_sender_token("spam@evil.com")) }
    }

    @Test
    fun `load_storage populates storage overview`() = runTest {
        val storage = StorageOverview(
            used_bytes = 500_000_000,
            total_bytes = 1_073_741_824,
            percentage_used = 46.6,
        )
        coEvery { settings_api.get_storage_overview() } returns storage

        vm.load_storage()
        advanceUntilIdle()

        assertEquals(storage, vm.state.value.storage)
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `load_subscription populates subscription info`() = runTest {
        val sub = SubscriptionInfo(
            plan_name = "Star",
            status = "active",
            amount = 999,
            interval = "month",
            current_period_end = "2026-05-26",
        )
        coEvery { settings_api.get_subscription() } returns sub

        vm.load_subscription()
        advanceUntilIdle()

        assertEquals("Star", vm.state.value.subscription?.plan_name)
        assertEquals(999, vm.state.value.subscription?.amount)
    }

    @Test
    fun `load_security_status updates state`() = runTest {
        val status = SecurityStatusResponse(
            totp_enabled = true,
            recovery_email_set = true,
            recovery_email_verified = false,
        )
        coEvery { settings_api.get_security_status() } returns status

        vm.load_security_status()
        advanceUntilIdle()

        assertTrue(vm.state.value.security_status?.totp_enabled == true)
        assertFalse(vm.state.value.security_status?.recovery_email_set == true)
    }

    @Test
    fun `load_ghost_aliases populates list`() = runTest {
        val aliases = listOf(
            GhostAlias(id = "g1", domain = "astermail.org", encrypted_local_part = ""),
            GhostAlias(id = "g2", domain = "astermail.org", encrypted_local_part = ""),
        )
        coEvery { ghost_alias_api.list_ghost_aliases() } returns GhostAliasListResponse(aliases)

        vm.load_ghost_aliases()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.ghost_aliases.size)
    }

    @Test
    fun `create_ghost_alias calls api when passphrase available`() = runTest {
        val passphrase = ByteArray(32) { it.toByte() }
        every { session_key_store.get_passphrase() } returns passphrase
        coEvery { ghost_alias_api.create_ghost_alias(any()) } returns
            org.astermail.android.api.ghost.CreateGhostAliasResponse(
                id = "ga1",
                success = true,
                expires_at = "2026-07-01T00:00:00Z",
                grace_expires_at = "2026-07-08T00:00:00Z",
            )

        vm.create_ghost_alias("my note")
        advanceUntilIdle()

        coVerify { ghost_alias_api.create_ghost_alias(any()) }
    }

    @Test
    fun `expire_ghost_alias calls api and reloads`() = runTest {
        coEvery { ghost_alias_api.list_ghost_aliases() } returns GhostAliasListResponse(emptyList())

        vm.expire_ghost_alias("g1")
        advanceUntilIdle()

        coVerify { ghost_alias_api.expire_ghost_alias("g1") }
        coVerify { ghost_alias_api.list_ghost_aliases() }
    }

    @Test
    fun `load_forwarding_rules populates list`() = runTest {
        val rules = listOf(
            ForwardingRule(id = "r1", forward_to = listOf("fwd@gmail.com"), is_enabled = true),
        )
        coEvery { auto_forward_api.list_rules() } returns ForwardingRulesResponse(rules)

        vm.load_forwarding_rules()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.forwarding_rules.size)
        assertTrue(vm.state.value.forwarding_rules[0].enabled)
    }

    @Test
    fun `toggle_forwarding_rule updates enabled state`() = runTest {
        val rules = listOf(
            ForwardingRule(id = "r1", forward_to = listOf("fwd@gmail.com"), is_enabled = true),
        )
        coEvery { auto_forward_api.list_rules() } returns ForwardingRulesResponse(rules)

        vm.load_forwarding_rules()
        advanceUntilIdle()

        vm.toggle_forwarding_rule("r1", false)
        advanceUntilIdle()

        assertFalse(vm.state.value.forwarding_rules[0].enabled)
        coVerify { auto_forward_api.toggle_rule(any()) }
    }

    @Test
    fun `delete_forwarding_rule removes from list`() = runTest {
        val rules = listOf(
            ForwardingRule(id = "r1", forward_to = listOf("fwd1@gmail.com"), is_enabled = true),
            ForwardingRule(id = "r2", forward_to = listOf("fwd2@gmail.com"), is_enabled = true),
        )
        coEvery { auto_forward_api.list_rules() } returns ForwardingRulesResponse(rules)

        vm.load_forwarding_rules()
        advanceUntilIdle()

        vm.delete_forwarding_rule("r1")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.forwarding_rules.size)
        assertEquals("r2", vm.state.value.forwarding_rules[0].id)
    }

    @Test
    fun `load_labels populates label list`() = runTest {
        val labels = listOf(
            LabelItem(id = "l1", label_token = "lt1", encrypted_name = "Work", name_nonce = ""),
            LabelItem(id = "l2", label_token = "lt2", encrypted_name = "Personal", name_nonce = ""),
        )
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(labels)
        every { session_key_store.get_identity_key() } returns null

        vm.load_labels()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.labels.size)
    }

    @Test
    fun `delete_label removes from list`() = runTest {
        val labels = listOf(
            LabelItem(id = "l1", label_token = "lt1", encrypted_name = "Work", name_nonce = ""),
            LabelItem(id = "l2", label_token = "lt2", encrypted_name = "Personal", name_nonce = ""),
        )
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(labels)
        every { session_key_store.get_identity_key() } returns null

        vm.load_labels()
        advanceUntilIdle()

        vm.delete_label("l1")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.labels.size)
        assertEquals("l2", vm.state.value.labels[0].id)
    }

    @Test
    fun `create_label calls api and reloads on success`() = runTest {
        coEvery { labels_api.create_label(any()) } returns CreateLabelResponse(success = true)
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(emptyList())
        every { session_key_store.get_identity_key() } returns null

        vm.create_label(org.astermail.android.api.labels.CreateLabelRequest(
            label_token = "lt_new", encrypted_name = "enc", name_nonce = "n",
        ))
        advanceUntilIdle()

        coVerify { labels_api.create_label(any()) }
        coVerify { labels_api.list_labels(any(), any()) }
    }

    @Test
    fun `load_preferences populates defaults when no encrypted data`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse()

        vm.load_preferences()
        advanceUntilIdle()

        assertNotNull(vm.state.value.preferences)
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `save_preferences updates state and sets saved status`() = runTest {
        val prefs = UserPreferences(load_remote_images = "always")

        vm.save_preferences(prefs)
        advanceUntilIdle()

        assertEquals("always", vm.state.value.preferences?.load_remote_images)
        assertEquals(SaveStatus.SAVED, vm.state.value.save_status)
    }

    @Test
    fun `load_api_keys populates api keys`() = runTest {
        val keys = listOf(
            ApiKeyInfo(id = "k1", name_encrypted = "enc", name_nonce = "n", prefix = "ak_"),
        )
        coEvery { developer_api.list_api_keys() } returns ApiKeyListResponse(keys)

        vm.load_api_keys()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.api_keys.size)
        assertEquals("ak_", vm.state.value.api_keys[0].prefix)
    }

    @Test
    fun `revoke_api_key removes from list`() = runTest {
        val keys = listOf(
            ApiKeyInfo(id = "k1"),
            ApiKeyInfo(id = "k2"),
        )
        coEvery { developer_api.list_api_keys() } returns ApiKeyListResponse(keys)

        vm.load_api_keys()
        advanceUntilIdle()

        vm.revoke_api_key("k1")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.api_keys.size)
        assertEquals("k2", vm.state.value.api_keys[0].id)
    }

    @Test
    fun `get_recovery_codes delegates to session key store`() {
        every { session_key_store.get_recovery_codes() } returns listOf("code1", "code2", "code3")

        val codes = vm.get_recovery_codes()

        assertEquals(3, codes?.size)
        assertEquals("code1", codes?.get(0))
        verify { session_key_store.get_recovery_codes() }
    }

    @Test
    fun `get_recovery_codes returns null when no codes stored`() {
        every { session_key_store.get_recovery_codes() } returns null

        assertNull(vm.get_recovery_codes())
    }

    @Test
    fun `get_access_token delegates to token store`() {
        every { token_store.access_token } returns "test_token_123"

        assertEquals("test_token_123", vm.get_access_token())
    }

    @Test
    fun `reset_save_status sets status to idle`() = runTest {
        val prefs = UserPreferences()
        vm.save_preferences(prefs)
        advanceUntilIdle()
        assertEquals(SaveStatus.SAVED, vm.state.value.save_status)

        vm.reset_save_status()
        assertEquals(SaveStatus.IDLE, vm.state.value.save_status)
    }

    @Test
    fun `logout calls auth repository`() = runTest {
        vm.logout()
        advanceUntilIdle()

        coVerify { auth_repository.logout() }
    }

    @Test
    fun `load_aliases populates aliases`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "alias1", domain = "astermail.org"),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.aliases.size)
    }

    @Test
    fun `delete_alias removes from list`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "alias1", domain = "astermail.org"),
            AliasInfo(id = "a2", encrypted_local_part = "alias2", domain = "astermail.org"),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()

        vm.delete_alias("a1")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.aliases.size)
        assertEquals("a2", vm.state.value.aliases[0].id)
    }

    @Test
    fun `load_deleted_aliases populates and decrypts trash`() = runTest {
        val encoded = java.util.Base64.getEncoder()
            .encodeToString("myalias".toByteArray())
        val deleted = listOf(
            DeletedAliasInfo(
                id = "d1",
                encrypted_local_part = encoded,
                domain = "astermail.org",
                is_random = true,
                deleted_at = "2026-07-02T09:26:00Z",
            ),
        )
        coEvery { settings_api.list_deleted_aliases() } returns
            ListDeletedAliasesResponse(deleted, total = 1)

        vm.load_deleted_aliases()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.deleted_aliases.size)
        assertEquals("myalias@astermail.org", vm.state.value.deleted_aliases[0].address)
        assertEquals("2026-07-02T09:26:00Z", vm.state.value.deleted_aliases[0].deleted_at)
    }

    @Test
    fun `restore_deleted_alias removes from trash`() = runTest {
        val encoded = java.util.Base64.getEncoder()
            .encodeToString("myalias".toByteArray())
        coEvery { settings_api.list_deleted_aliases() } returns
            ListDeletedAliasesResponse(
                listOf(
                    DeletedAliasInfo(
                        id = "d1",
                        encrypted_local_part = encoded,
                        domain = "astermail.org",
                        is_random = true,
                    ),
                ),
                total = 1,
            )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(emptyList())

        vm.load_deleted_aliases()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.deleted_aliases.size)

        vm.restore_deleted_alias("d1")
        advanceUntilIdle()

        assertTrue(vm.state.value.deleted_aliases.isEmpty())
        coVerify { settings_api.restore_deleted_alias("d1") }
    }

    @Test
    fun `empty_deleted_aliases clears trash`() = runTest {
        val encoded = java.util.Base64.getEncoder()
            .encodeToString("myalias".toByteArray())
        coEvery { settings_api.list_deleted_aliases() } returns
            ListDeletedAliasesResponse(
                listOf(
                    DeletedAliasInfo(
                        id = "d1",
                        encrypted_local_part = encoded,
                        domain = "astermail.org",
                        is_random = true,
                    ),
                ),
                total = 1,
            )

        vm.load_deleted_aliases()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.deleted_aliases.size)

        vm.empty_deleted_aliases()
        advanceUntilIdle()

        assertTrue(vm.state.value.deleted_aliases.isEmpty())
        coVerify { settings_api.empty_deleted_aliases() }
    }

    @Test
    fun `load_webhooks populates webhooks`() = runTest {
        val hooks = listOf(
            WebhookInfo(id = "w1", url_encrypted = "enc", url_nonce = "n"),
        )
        coEvery { developer_api.list_webhooks() } returns WebhookListResponse(hooks)

        vm.load_webhooks()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.webhooks.size)
    }

    @Test
    fun `load_referral_info updates referral state`() = runTest {
        val info = ReferralInfoResponse(
            referral_code = "REF123",
            total_referrals = 5,
            completed_referrals = 3,
        )
        coEvery { labels_api.get_referral_info() } returns info

        vm.load_referral_info()
        advanceUntilIdle()

        assertEquals("REF123", vm.state.value.referral?.referral_code)
        assertEquals(5L, vm.state.value.referral?.total_referrals)
    }

    @Test
    fun `concurrent loads do not corrupt state`() = runTest {
        val sessions = listOf(SessionInfo(id = "s1", is_current = true))
        val blocked = listOf(encrypted_blocked_sender("spam@x.com"))
        val storage = StorageOverview(used_bytes = 1000, total_bytes = 10000)

        coEvery { settings_api.list_sessions() } returns SessionListResponse(sessions)
        coEvery { settings_api.list_blocked_senders() } returns BlockedSendersResponse(blocked)
        coEvery { settings_api.get_storage_overview() } returns storage

        vm.load_sessions()
        vm.load_blocked_senders()
        vm.load_storage()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.sessions.size)
        assertEquals(1, vm.state.value.blocked_senders.size)
        assertNotNull(vm.state.value.storage)
    }

    @Test
    fun `load_sessions error sets error message`() = runTest {
        coEvery { settings_api.list_sessions() } throws org.astermail.android.api.ApiError.UnknownError("network error")

        vm.load_sessions()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("network error", vm.state.value.error)
        assertTrue(vm.state.value.sessions.isEmpty())
    }

    @Test
    fun `load_sessions empty list`() = runTest {
        coEvery { settings_api.list_sessions() } returns SessionListResponse(emptyList())

        vm.load_sessions()
        advanceUntilIdle()

        assertTrue(vm.state.value.sessions.isEmpty())
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `revoke_session error does not modify list`() = runTest {
        val sessions = listOf(
            SessionInfo(id = "s1", is_current = true),
            SessionInfo(id = "s2", is_current = false),
        )
        coEvery { settings_api.list_sessions() } returns SessionListResponse(sessions)
        coEvery { settings_api.revoke_session("s2") } throws RuntimeException("forbidden")

        vm.load_sessions()
        advanceUntilIdle()

        vm.revoke_session("s2")
        advanceUntilIdle()

        assertEquals(2, vm.state.value.sessions.size)
    }

    @Test
    fun `revoke_session nonexistent id is harmless`() = runTest {
        val sessions = listOf(SessionInfo(id = "s1", is_current = true))
        coEvery { settings_api.list_sessions() } returns SessionListResponse(sessions)

        vm.load_sessions()
        advanceUntilIdle()

        vm.revoke_session("nonexistent")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.sessions.size)
    }

    @Test
    fun `logout_others error does not modify list`() = runTest {
        val sessions = listOf(
            SessionInfo(id = "s1", is_current = true),
            SessionInfo(id = "s2", is_current = false),
        )
        coEvery { settings_api.list_sessions() } returns SessionListResponse(sessions)
        coEvery { settings_api.logout_others() } throws RuntimeException("server error")

        vm.load_sessions()
        advanceUntilIdle()

        vm.logout_others()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.sessions.size)
    }

    @Test
    fun `load_blocked_senders error sets error message`() = runTest {
        coEvery { settings_api.list_blocked_senders() } throws org.astermail.android.api.ApiError.UnknownError("timeout")

        vm.load_blocked_senders()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("timeout", vm.state.value.blocked_senders_error)
        assertTrue(vm.state.value.blocked_senders.isEmpty())
    }

    @Test
    fun `load_blocked_senders empty list`() = runTest {
        coEvery { settings_api.list_blocked_senders() } returns BlockedSendersResponse(emptyList())

        vm.load_blocked_senders()
        advanceUntilIdle()

        assertTrue(vm.state.value.blocked_senders.isEmpty())
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `block_sender error does not add to list`() = runTest {
        coEvery { settings_api.block_sender(any()) } throws RuntimeException("error")

        vm.block_sender("bad@evil.com")
        advanceUntilIdle()

        assertTrue(vm.state.value.blocked_senders.isEmpty())
    }

    @Test
    fun `block_sender duplicate replaces the existing entry`() = runTest {
        val blocked = listOf(encrypted_blocked_sender("spam@evil.com"))
        coEvery { settings_api.list_blocked_senders() } returns BlockedSendersResponse(blocked)

        vm.load_blocked_senders()
        advanceUntilIdle()

        vm.block_sender("spam@evil.com")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.blocked_senders.size)
    }

    @Test
    fun `unblock_sender error does not modify list`() = runTest {
        val blocked = listOf(encrypted_blocked_sender("spam@evil.com"))
        coEvery { settings_api.list_blocked_senders() } returns BlockedSendersResponse(blocked)
        coEvery { settings_api.unblock_sender(any()) } throws RuntimeException("error")

        vm.load_blocked_senders()
        advanceUntilIdle()

        vm.unblock_sender(blocked_sender_token("spam@evil.com"))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.blocked_senders.size)
    }

    @Test
    fun `unblock_sender nonexistent address is harmless`() = runTest {
        val blocked = listOf(encrypted_blocked_sender("spam@evil.com"))
        coEvery { settings_api.list_blocked_senders() } returns BlockedSendersResponse(blocked)

        vm.load_blocked_senders()
        advanceUntilIdle()

        vm.unblock_sender(blocked_sender_token("nonexistent@evil.com"))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.blocked_senders.size)
        coVerify { settings_api.unblock_sender(blocked_sender_token("nonexistent@evil.com")) }
    }

    @Test
    fun `load_storage error sets error message`() = runTest {
        coEvery { settings_api.get_storage_overview() } throws org.astermail.android.api.ApiError.UnknownError("storage unavailable")

        vm.load_storage()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("storage unavailable", vm.state.value.error)
        assertNull(vm.state.value.storage)
    }

    @Test
    fun `load_subscription error sets error message`() = runTest {
        coEvery { settings_api.get_subscription() } throws org.astermail.android.api.ApiError.UnknownError("no subscription")

        vm.load_subscription()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("no subscription", vm.state.value.error)
        assertNull(vm.state.value.subscription)
    }

    @Test
    fun `load_security_status error does not set error`() = runTest {
        coEvery { settings_api.get_security_status() } throws RuntimeException("fail")

        vm.load_security_status()
        advanceUntilIdle()

        assertNull(vm.state.value.security_status)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `load_aliases error sets error message`() = runTest {
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } throws org.astermail.android.api.ApiError.UnknownError("alias error")

        vm.load_aliases()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("alias error", vm.state.value.error)
        assertTrue(vm.state.value.aliases.isEmpty())
    }

    @Test
    fun `load_aliases empty list`() = runTest {
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(emptyList())
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()

        assertTrue(vm.state.value.aliases.isEmpty())
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `delete_alias error does not modify list`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "alias1", domain = "astermail.org"),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null
        coEvery { settings_api.delete_alias("a1") } throws RuntimeException("error")

        vm.load_aliases()
        advanceUntilIdle()

        vm.delete_alias("a1")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.aliases.size)
    }

    @Test
    fun `delete_alias nonexistent id is harmless`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "alias1", domain = "astermail.org"),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()

        vm.delete_alias("nonexistent")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.aliases.size)
    }

    @Test
    fun `set_alias_delivery to archive sets never_inbox and calls api`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "alias1", domain = "astermail.org", never_inbox = false),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null
        coEvery {
            settings_api.update_alias("a1", org.astermail.android.api.settings.UpdateAliasRequest(never_inbox = true))
        } returns true

        vm.load_aliases()
        advanceUntilIdle()

        vm.set_alias_delivery("a1", null, to_archive = true)
        advanceUntilIdle()

        val updated = vm.state.value.aliases.single { it.id == "a1" }
        assertTrue(updated.never_inbox)
        assertEquals(null, updated.delivery_folder_token)
        coVerify {
            settings_api.update_alias("a1", org.astermail.android.api.settings.UpdateAliasRequest(never_inbox = true))
        }
    }

    @Test
    fun `set_alias_delivery to folder sends token and clears never_inbox`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "alias1", domain = "astermail.org", never_inbox = true),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null
        coEvery {
            settings_api.update_alias(
                "a1",
                org.astermail.android.api.settings.UpdateAliasRequest(delivery_folder_token = "dG9rZW4="),
            )
        } returns true

        vm.load_aliases()
        advanceUntilIdle()

        vm.set_alias_delivery("a1", "dG9rZW4=", to_archive = false)
        advanceUntilIdle()

        val updated = vm.state.value.aliases.single { it.id == "a1" }
        assertFalse(updated.never_inbox)
        assertEquals("dG9rZW4=", updated.delivery_folder_token)
        coVerify {
            settings_api.update_alias(
                "a1",
                org.astermail.android.api.settings.UpdateAliasRequest(delivery_folder_token = "dG9rZW4="),
            )
        }
    }

    @Test
    fun `set_alias_delivery to inbox clears token and never_inbox`() = runTest {
        val aliases = listOf(
            AliasInfo(
                id = "a1",
                encrypted_local_part = "alias1",
                domain = "astermail.org",
                never_inbox = false,
                delivery_folder_token = "dG9rZW4=",
            ),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null
        coEvery {
            settings_api.update_alias("a1", org.astermail.android.api.settings.UpdateAliasRequest(never_inbox = false))
        } returns true

        vm.load_aliases()
        advanceUntilIdle()

        vm.set_alias_delivery("a1", null, to_archive = false)
        advanceUntilIdle()

        val updated = vm.state.value.aliases.single { it.id == "a1" }
        assertFalse(updated.never_inbox)
        assertEquals(null, updated.delivery_folder_token)
    }

    @Test
    fun `set_alias_delivery rolls back on api error`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "alias1", domain = "astermail.org", never_inbox = false),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null
        coEvery {
            settings_api.update_alias("a1", org.astermail.android.api.settings.UpdateAliasRequest(never_inbox = true))
        } throws RuntimeException("server error")

        vm.load_aliases()
        advanceUntilIdle()

        vm.set_alias_delivery("a1", null, to_archive = true)
        advanceUntilIdle()

        assertFalse(vm.state.value.aliases.single { it.id == "a1" }.never_inbox)
        assertEquals("Something went wrong", vm.state.value.action_result)
    }

    @Test
    fun `set_alias_delivery nonexistent id is harmless`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "alias1", domain = "astermail.org", never_inbox = false),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()

        vm.set_alias_delivery("nonexistent", null, to_archive = true)
        advanceUntilIdle()

        assertFalse(vm.state.value.aliases.single { it.id == "a1" }.never_inbox)
        coVerify(exactly = 0) { settings_api.update_alias(any(), any()) }
    }

    @Test
    fun `load_ghost_aliases error sets error message`() = runTest {
        coEvery { ghost_alias_api.list_ghost_aliases() } throws org.astermail.android.api.ApiError.UnknownError("ghost error")

        vm.load_ghost_aliases()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("ghost error", vm.state.value.error)
        assertTrue(vm.state.value.ghost_aliases.isEmpty())
    }

    @Test
    fun `load_ghost_aliases empty list`() = runTest {
        coEvery { ghost_alias_api.list_ghost_aliases() } returns GhostAliasListResponse(emptyList())

        vm.load_ghost_aliases()
        advanceUntilIdle()

        assertTrue(vm.state.value.ghost_aliases.isEmpty())
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `create_ghost_alias error does not reload`() = runTest {
        coEvery { ghost_alias_api.create_ghost_alias(any()) } throws RuntimeException("error")

        vm.create_ghost_alias("note")
        advanceUntilIdle()

        coVerify(exactly = 0) { ghost_alias_api.list_ghost_aliases() }
    }

    @Test
    fun `expire_ghost_alias error does not reload`() = runTest {
        coEvery { ghost_alias_api.expire_ghost_alias(any()) } throws RuntimeException("error")

        vm.expire_ghost_alias("g1")
        advanceUntilIdle()

        coVerify(exactly = 0) { ghost_alias_api.list_ghost_aliases() }
    }

    @Test
    fun `load_forwarding_rules error sets error message`() = runTest {
        coEvery { auto_forward_api.list_rules() } throws org.astermail.android.api.ApiError.UnknownError("rules error")

        vm.load_forwarding_rules()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("rules error", vm.state.value.error)
        assertTrue(vm.state.value.forwarding_rules.isEmpty())
    }

    @Test
    fun `load_forwarding_rules empty list`() = runTest {
        coEvery { auto_forward_api.list_rules() } returns ForwardingRulesResponse(emptyList())

        vm.load_forwarding_rules()
        advanceUntilIdle()

        assertTrue(vm.state.value.forwarding_rules.isEmpty())
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `create_forwarding_rule success updates save status and reloads`() = runTest {
        val rules = listOf(
            ForwardingRule(id = "r1", forward_to = listOf("fwd@gmail.com"), is_enabled = true),
        )
        coEvery { auto_forward_api.list_rules() } returns ForwardingRulesResponse(rules)

        vm.create_forwarding_rule("fwd@gmail.com", true)
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, vm.state.value.save_status)
        coVerify { auto_forward_api.create_rule(any()) }
        coVerify { auto_forward_api.list_rules() }
    }

    @Test
    fun `create_forwarding_rule error sets error status`() = runTest {
        coEvery { auto_forward_api.create_rule(any()) } throws org.astermail.android.api.ApiError.UnknownError("invalid target")

        vm.create_forwarding_rule("bad", false)
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, vm.state.value.save_status)
        assertEquals("invalid target", vm.state.value.error)
    }

    @Test
    fun `toggle_forwarding_rule error does not change state`() = runTest {
        val rules = listOf(
            ForwardingRule(id = "r1", forward_to = listOf("fwd@gmail.com"), is_enabled = true),
        )
        coEvery { auto_forward_api.list_rules() } returns ForwardingRulesResponse(rules)
        coEvery { auto_forward_api.toggle_rule(any()) } throws RuntimeException("error")

        vm.load_forwarding_rules()
        advanceUntilIdle()

        vm.toggle_forwarding_rule("r1", false)
        advanceUntilIdle()

        assertTrue(vm.state.value.forwarding_rules[0].enabled)
    }

    @Test
    fun `toggle_forwarding_rule nonexistent id is harmless`() = runTest {
        val rules = listOf(
            ForwardingRule(id = "r1", forward_to = listOf("fwd@gmail.com"), is_enabled = true),
        )
        coEvery { auto_forward_api.list_rules() } returns ForwardingRulesResponse(rules)

        vm.load_forwarding_rules()
        advanceUntilIdle()

        vm.toggle_forwarding_rule("nonexistent", false)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.forwarding_rules.size)
        assertTrue(vm.state.value.forwarding_rules[0].enabled)
    }

    @Test
    fun `delete_forwarding_rule error does not modify list`() = runTest {
        val rules = listOf(
            ForwardingRule(id = "r1", forward_to = listOf("fwd@gmail.com"), is_enabled = true),
        )
        coEvery { auto_forward_api.list_rules() } returns ForwardingRulesResponse(rules)
        coEvery { auto_forward_api.delete_rule(any()) } throws RuntimeException("error")

        vm.load_forwarding_rules()
        advanceUntilIdle()

        vm.delete_forwarding_rule("r1")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.forwarding_rules.size)
    }

    @Test
    fun `load_labels error sets error message`() = runTest {
        coEvery { labels_api.list_labels(any(), any()) } throws org.astermail.android.api.ApiError.UnknownError("labels error")

        vm.load_labels()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("labels error", vm.state.value.error)
        assertTrue(vm.state.value.labels.isEmpty())
    }

    @Test
    fun `load_labels with folder_type passes parameter`() = runTest {
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(emptyList())
        every { session_key_store.get_identity_key() } returns null

        vm.load_labels("folder")
        advanceUntilIdle()

        coVerify { labels_api.list_labels(include_counts = true, folder_type = "folder") }
    }

    @Test
    fun `load_labels empty list`() = runTest {
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(emptyList())
        every { session_key_store.get_identity_key() } returns null

        vm.load_labels()
        advanceUntilIdle()

        assertTrue(vm.state.value.labels.isEmpty())
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `create_label does not reload when success is false`() = runTest {
        coEvery { labels_api.create_label(any()) } returns CreateLabelResponse(success = false)

        vm.create_label(org.astermail.android.api.labels.CreateLabelRequest(
            label_token = "lt_new", encrypted_name = "enc", name_nonce = "n",
        ))
        advanceUntilIdle()

        coVerify { labels_api.create_label(any()) }
        coVerify(exactly = 0) { labels_api.list_labels(any(), any()) }
    }

    @Test
    fun `create_label error does not reload`() = runTest {
        coEvery { labels_api.create_label(any()) } throws RuntimeException("error")

        vm.create_label(org.astermail.android.api.labels.CreateLabelRequest(
            label_token = "lt_new", encrypted_name = "enc", name_nonce = "n",
        ))
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.list_labels(any(), any()) }
    }

    @Test
    fun `create_folder sends parent_token and sort_order and encrypts the name`() = runTest {
        val request_slot = io.mockk.slot<org.astermail.android.api.labels.CreateLabelRequest>()
        every { session_key_store.get_identity_key() } returns "test-identity-key"
        coEvery { labels_api.create_label(capture(request_slot)) } returns
            CreateLabelResponse(id = "srv", label_token = "srv_tok", success = true)

        vm.create_folder(name = "Receipts", sort_order = 2, parent_token = "parent_tok")
        advanceUntilIdle()

        val request = request_slot.captured
        assertEquals("parent_tok", request.parent_token)
        assertEquals(2, request.sort_order)
        assertEquals("folder", request.folder_type)
        assertFalse(request.encrypted_name.contains("Receipts"))
        assertTrue(request.name_nonce.isNotBlank())

        val optimistic = vm.state.value.labels.single()
        assertEquals("Receipts", optimistic.encrypted_name)
        assertEquals("parent_tok", optimistic.parent_token)
        assertEquals(2, optimistic.sort_order)
    }

    @Test
    fun `create_folder keeps the server id so delete works immediately`() = runTest {
        every { session_key_store.get_identity_key() } returns "test-identity-key"
        coEvery { labels_api.create_label(any()) } returns
            CreateLabelResponse(id = "srv_id", label_token = "srv_tok", success = true)
        coEvery { labels_api.delete_label(any()) } returns Unit

        vm.create_folder(name = "Receipts")
        advanceUntilIdle()

        val created = vm.state.value.labels.single()
        assertEquals("srv_id", created.id)

        vm.delete_label(created.id)
        advanceUntilIdle()

        coVerify { labels_api.delete_label("srv_id") }
        assertTrue(vm.state.value.labels.isEmpty())
    }

    @Test
    fun `delete_label failure reports an action result`() = runTest {
        coEvery { labels_api.list_labels(any(), any()) } returns
            LabelsListResponse(listOf(LabelItem(id = "l1", label_token = "lt1", encrypted_name = "Alpha")))
        vm.load_labels()
        advanceUntilIdle()
        coEvery { labels_api.delete_label("l1") } throws
            org.astermail.android.api.ApiError.UnknownError("folder is locked")

        vm.delete_label("l1")
        advanceUntilIdle()

        assertEquals("folder is locked", vm.state.value.action_result)
        assertEquals(1, vm.state.value.labels.size)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.seed_folders() {
        val labels = listOf(
            LabelItem(id = "l1", label_token = "lt1", encrypted_name = "Alpha", sort_order = 0),
            LabelItem(id = "l2", label_token = "lt2", encrypted_name = "Beta", sort_order = 1),
            LabelItem(
                id = "c1",
                label_token = "ltc1",
                encrypted_name = "Child",
                sort_order = 0,
                parent_token = "lt1",
            ),
        )
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(labels)
        every { session_key_store.get_identity_key() } returns null
        vm.load_labels()
        advanceUntilIdle()
    }

    @Test
    fun `move_folder swaps sibling sort orders and persists via bulk reorder`() = runTest {
        seed_folders()
        val request_slot =
            io.mockk.slot<org.astermail.android.api.labels.BulkReorderLabelsRequest>()
        coEvery { labels_api.bulk_reorder_labels(capture(request_slot)) } returns
            org.astermail.android.api.labels.BulkReorderLabelsResponse(updated = 2)

        vm.move_folder("l2", -1)
        advanceUntilIdle()

        val entries = request_slot.captured.labels.associate { it.id to it.sort_order }
        assertEquals(0, entries["l2"])
        assertEquals(1, entries["l1"])

        val by_id = vm.state.value.labels.associateBy { it.id }
        assertEquals(0, by_id["l2"]?.sort_order)
        assertEquals(1, by_id["l1"]?.sort_order)
        assertEquals("lt1", by_id["c1"]?.parent_token)
    }

    @Test
    fun `move_folder at the top edge is a no-op`() = runTest {
        seed_folders()

        vm.move_folder("l1", -1)
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.bulk_reorder_labels(any()) }
    }

    @Test
    fun `move_folder scoped to siblings ignores children of other parents`() = runTest {
        seed_folders()

        vm.move_folder("c1", 1)
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.bulk_reorder_labels(any()) }
    }

    @Test
    fun `move_folder reverts optimistic order when the api call fails`() = runTest {
        seed_folders()
        coEvery { labels_api.bulk_reorder_labels(any()) } throws RuntimeException("offline")

        vm.move_folder("l2", -1)
        advanceUntilIdle()

        val by_id = vm.state.value.labels.associateBy { it.id }
        assertEquals(0, by_id["l1"]?.sort_order)
        assertEquals(1, by_id["l2"]?.sort_order)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.seed_label_rows() {
        val labels = listOf(
            LabelItem(id = "f1", label_token = "ftk1", encrypted_name = "Inbox", folder_type = "folder"),
            LabelItem(id = "b", label_token = "ltb", encrypted_name = "Bank", folder_type = "label", sort_order = 1),
            LabelItem(id = "a", label_token = "lta", encrypted_name = "Invoice", folder_type = "label", sort_order = 0),
            LabelItem(id = "c", label_token = "ltc", encrypted_name = "News", folder_type = "label", sort_order = 2),
        )
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(labels)
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_previous_keys() } returns null
        vm.load_labels()
        advanceUntilIdle()
    }

    @Test
    fun `move_label_row reorders labels and persists via bulk reorder`() = runTest {
        seed_label_rows()
        val request_slot = io.mockk.slot<org.astermail.android.api.labels.BulkReorderLabelsRequest>()
        coEvery { labels_api.bulk_reorder_labels(capture(request_slot)) } returns
            org.astermail.android.api.labels.BulkReorderLabelsResponse(updated = 2)

        vm.move_label_row("c", -1)
        advanceUntilIdle()

        val entries = request_slot.captured.labels.associate { it.id to it.sort_order }
        assertEquals(1, entries["c"])
        assertEquals(2, entries["b"])
        assertEquals(
            listOf("a", "c", "b"),
            org.astermail.android.labels.label_rows(vm.state.value.labels).map { it.id },
        )
    }

    @Test
    fun `move_label_row leaves folders untouched`() = runTest {
        seed_label_rows()
        coEvery { labels_api.bulk_reorder_labels(any()) } returns
            org.astermail.android.api.labels.BulkReorderLabelsResponse(updated = 2)

        vm.move_label_row("b", -1)
        advanceUntilIdle()

        val folder = vm.state.value.labels.first { it.id == "f1" }
        assertEquals(0, folder.sort_order)
        assertEquals("folder", folder.folder_type)
    }

    @Test
    fun `move_label_row at an edge does not call the api`() = runTest {
        seed_label_rows()

        vm.move_label_row("a", -1)
        vm.move_label_row("c", 1)
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.bulk_reorder_labels(any()) }
    }

    @Test
    fun `move_label_row reverts optimistic order when the api call fails`() = runTest {
        seed_label_rows()
        coEvery { labels_api.bulk_reorder_labels(any()) } throws RuntimeException("offline")

        vm.move_label_row("c", -1)
        advanceUntilIdle()

        assertEquals(
            listOf("a", "b", "c"),
            org.astermail.android.labels.label_rows(vm.state.value.labels).map { it.id },
        )
    }

    private suspend fun kotlinx.coroutines.test.TestScope.seed_tag_rows() {
        val tags = listOf(
            org.astermail.android.api.tags.TagItem(
                id = "t2", tag_token = "tk2", encrypted_name = "Beta", name_nonce = "", sort_order = 1,
            ),
            org.astermail.android.api.tags.TagItem(
                id = "t1", tag_token = "tk1", encrypted_name = "Alpha", name_nonce = "", sort_order = 0,
            ),
        )
        coEvery { tags_api.list_tags(any()) } returns
            org.astermail.android.api.tags.TagsListResponse(tags = tags)
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_previous_keys() } returns null
        vm.load_tags()
        advanceUntilIdle()
    }

    @Test
    fun `move_tag persists a single bulk reorder request`() = runTest {
        seed_tag_rows()
        val request_slot = io.mockk.slot<org.astermail.android.api.tags.BulkReorderTagsRequest>()
        coEvery { tags_api.bulk_reorder_tags(capture(request_slot)) } returns
            org.astermail.android.api.tags.BulkReorderTagsResponse(updated = 2)

        vm.move_tag("t2", -1)
        advanceUntilIdle()

        val entries = request_slot.captured.tags.associate { it.id to it.sort_order }
        assertEquals(0, entries["t2"])
        assertEquals(1, entries["t1"])
        coVerify(exactly = 0) { tags_api.update_tag(any(), any()) }
        assertEquals(
            listOf("t2", "t1"),
            org.astermail.android.labels.tag_rows(vm.state.value.tags).map { it.id },
        )
    }

    @Test
    fun `move_tag reverts optimistic order when the api call fails`() = runTest {
        seed_tag_rows()
        coEvery { tags_api.bulk_reorder_tags(any()) } throws RuntimeException("offline")

        vm.move_tag("t2", -1)
        advanceUntilIdle()

        assertEquals(
            listOf("t1", "t2"),
            org.astermail.android.labels.tag_rows(vm.state.value.tags).map { it.id },
        )
    }

    @Test
    fun `delete_label error does not modify list`() = runTest {
        val labels = listOf(
            LabelItem(id = "l1", label_token = "lt1", encrypted_name = "Work", name_nonce = ""),
        )
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(labels)
        every { session_key_store.get_identity_key() } returns null
        coEvery { labels_api.delete_label("l1") } throws RuntimeException("error")

        vm.load_labels()
        advanceUntilIdle()

        vm.delete_label("l1")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.labels.size)
    }

    @Test
    fun `load_preferences keeps prefs when encrypted blob present but identity key missing`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse()
        coEvery { preferences_api.get_preferences() } returns
            UserPreferences(conversation_grouping = false, swipe_right_action = "star")
        vm.load_preferences(force = true)
        advanceUntilIdle()
        assertEquals(false, vm.state.value.preferences?.conversation_grouping)
        assertEquals("star", vm.state.value.preferences?.swipe_right_action)

        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse(
                encrypted_preferences = "blob", preferences_nonce = "nonce",
            )
        every { session_key_store.get_identity_key() } returns null
        coEvery { preferences_api.get_preferences() } returns UserPreferences()

        vm.load_preferences(force = true)
        advanceUntilIdle()

        assertEquals(false, vm.state.value.preferences?.conversation_grouping)
        assertEquals("star", vm.state.value.preferences?.swipe_right_action)
    }

    @Test
    fun `first load with encrypted blob and no identity key still populates prefs so screen is not stuck loading`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse(
                encrypted_preferences = "blob",
                preferences_nonce = "nonce",
            )
        every { session_key_store.get_identity_key() } returns null

        val fresh_vm = SettingsViewModel(
            auth_api = auth_api,
            user_api = user_api,
            settings_api = settings_api,
            labels_api = labels_api,
            family_api = family_api,
            tags_api = tags_api,
            preferences_api = preferences_api,
            signatures_api = signatures_api,
            ghost_alias_api = ghost_alias_api,
            auto_forward_api = auto_forward_api,
            developer_api = developer_api,
            subscriptions_api = subscriptions_api,
            recovery_email_api = recovery_email_api,
            security_api = security_api,
            encryption_api = encryption_api,
            alias_detail_api = alias_detail_api,
            mail_rules_api = mail_rules_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        )
        advanceUntilIdle()

        assertNotNull(fresh_vm.state.value.preferences)
        assertFalse(fresh_vm.state.value.is_loading)
    }

    @Test
    fun `save_preferences recovers and re-encrypts under current key when server blob is undecryptable`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse(
                encrypted_preferences = "blob",
                preferences_nonce = "nonce",
            )
        every { session_key_store.get_identity_key() } returns "current_key_after_password_reset"

        val fresh_vm = SettingsViewModel(
            auth_api = auth_api,
            user_api = user_api,
            settings_api = settings_api,
            labels_api = labels_api,
            family_api = family_api,
            tags_api = tags_api,
            preferences_api = preferences_api,
            signatures_api = signatures_api,
            ghost_alias_api = ghost_alias_api,
            auto_forward_api = auto_forward_api,
            developer_api = developer_api,
            subscriptions_api = subscriptions_api,
            recovery_email_api = recovery_email_api,
            security_api = security_api,
            encryption_api = encryption_api,
            alias_detail_api = alias_detail_api,
            mail_rules_api = mail_rules_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        )
        advanceUntilIdle()

        fresh_vm.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, fresh_vm.state.value.save_status)
        coVerify(exactly = 1) { preferences_api.save_encrypted_preferences(any()) }
    }

    private fun test_prefs_key(identity_key: String): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest((identity_key + "astermail-preferences-v1").toByteArray(Charsets.UTF_8))

    private fun encrypt_test_blob(json_str: String, identity_key: String): Pair<String, String> {
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(test_prefs_key(identity_key), "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce),
        )
        val ct = cipher.doFinal(json_str.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(ct) to
            java.util.Base64.getEncoder().encodeToString(nonce)
    }

    private fun decrypt_test_blob(encrypted_b64: String, nonce_b64: String, identity_key: String): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(test_prefs_key(identity_key), "AES"),
            javax.crypto.spec.GCMParameterSpec(128, java.util.Base64.getDecoder().decode(nonce_b64)),
        )
        return String(cipher.doFinal(java.util.Base64.getDecoder().decode(encrypted_b64)), Charsets.UTF_8)
    }

    @Test
    fun `save after failed decrypt rebases onto fresh server blob instead of writing defaults`() = runTest {
        val identity_key = "user_identity_key"
        val server_json = """{"theme":"dark","haptic_enabled":false,"web_only_setting":"keep_me"}"""
        val (blob, nonce) = encrypt_test_blob(server_json, identity_key)
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse(
                encrypted_preferences = blob,
                preferences_nonce = nonce,
            )
        every { session_key_store.get_identity_key() } returns "wrong_key_during_startup"

        val fresh_vm = SettingsViewModel(
            auth_api = auth_api,
            user_api = user_api,
            settings_api = settings_api,
            labels_api = labels_api,
            family_api = family_api,
            tags_api = tags_api,
            preferences_api = preferences_api,
            signatures_api = signatures_api,
            ghost_alias_api = ghost_alias_api,
            auto_forward_api = auto_forward_api,
            developer_api = developer_api,
            subscriptions_api = subscriptions_api,
            recovery_email_api = recovery_email_api,
            security_api = security_api,
            encryption_api = encryption_api,
            alias_detail_api = alias_detail_api,
            mail_rules_api = mail_rules_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        )
        advanceUntilIdle()

        every { session_key_store.get_identity_key() } returns identity_key
        val request = io.mockk.slot<org.astermail.android.api.preferences.SaveEncryptedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences(capture(request)) } returns Unit

        val shown = fresh_vm.state.value.preferences!!
        fresh_vm.save_preferences(shown.copy(show_aster_branding = false))
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, fresh_vm.state.value.save_status)
        val written = decrypt_test_blob(
            request.captured.encrypted_preferences,
            request.captured.preferences_nonce,
            identity_key,
        )
        assertTrue(written.contains("\"theme\":\"dark\""))
        assertTrue(written.contains("\"haptic_enabled\":false"))
        assertTrue(written.contains("\"web_only_setting\":\"keep_me\""))
        assertTrue(written.contains("\"show_aster_branding\":false"))
    }

    @Test
    fun `reset_for_account_switch blocks saves until preferences reload`() = runTest {
        advanceUntilIdle()

        vm.reset_for_account_switch()
        vm.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, vm.state.value.save_status)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        coVerify(exactly = 0) { preferences_api.save_preferences(any()) }
        coVerify(exactly = 0) { preferences_api.save_preferences_raw(any()) }
    }

    @Test
    fun `plaintext save preserves web-only keys from the raw load`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse()
        coEvery { preferences_api.get_preferences_raw() } returns
            """{"theme":"dark","web_only_setting":"keep_me"}"""
        every { session_key_store.get_identity_key() } returns null

        val vm = SettingsViewModel(
            auth_api = auth_api,
            user_api = user_api,
            settings_api = settings_api,
            labels_api = labels_api,
            family_api = family_api,
            tags_api = tags_api,
            preferences_api = preferences_api,
            signatures_api = signatures_api,
            ghost_alias_api = ghost_alias_api,
            auto_forward_api = auto_forward_api,
            developer_api = developer_api,
            subscriptions_api = subscriptions_api,
            recovery_email_api = recovery_email_api,
            security_api = security_api,
            encryption_api = encryption_api,
            alias_detail_api = alias_detail_api,
            mail_rules_api = mail_rules_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        )
        advanceUntilIdle()
        assertEquals("dark", vm.state.value.preferences?.theme)

        val body = io.mockk.slot<String>()
        coEvery { preferences_api.save_preferences_raw(capture(body)) } returns Unit

        vm.save_preferences(vm.state.value.preferences!!.copy(theme = "light"))
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, vm.state.value.save_status)
        assertTrue(body.captured.contains("\"web_only_setting\":\"keep_me\""))
        assertTrue(body.captured.contains("\"theme\":\"light\""))
    }

    @Test
    fun `save_preferences refuses and never writes when encrypted account has no identity key`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse(
                encrypted_preferences = "blob",
                preferences_nonce = "nonce",
            )
        every { session_key_store.get_identity_key() } returns null

        val fresh_vm = SettingsViewModel(
            auth_api = auth_api,
            user_api = user_api,
            settings_api = settings_api,
            labels_api = labels_api,
            family_api = family_api,
            tags_api = tags_api,
            preferences_api = preferences_api,
            signatures_api = signatures_api,
            ghost_alias_api = ghost_alias_api,
            auto_forward_api = auto_forward_api,
            developer_api = developer_api,
            subscriptions_api = subscriptions_api,
            recovery_email_api = recovery_email_api,
            security_api = security_api,
            encryption_api = encryption_api,
            alias_detail_api = alias_detail_api,
            mail_rules_api = mail_rules_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        )
        advanceUntilIdle()

        fresh_vm.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, fresh_vm.state.value.save_status)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        coVerify(exactly = 0) { preferences_api.save_preferences(any()) }
    }

    @Test
    fun `load_preferences marks authoritative after a successful plaintext load`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse()
        coEvery { preferences_api.get_preferences() } returns
            UserPreferences(conversation_grouping = false)

        vm.load_preferences()
        advanceUntilIdle()

        assertTrue(vm.state.value.preferences_authoritative)
    }

    @Test
    fun `load_preferences stays non-authoritative when encrypted blob present but identity key missing`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse(
                encrypted_preferences = "blob",
                preferences_nonce = "nonce",
            )
        every { session_key_store.get_identity_key() } returns null

        val fresh_vm = SettingsViewModel(
            auth_api = auth_api,
            user_api = user_api,
            settings_api = settings_api,
            labels_api = labels_api,
            family_api = family_api,
            tags_api = tags_api,
            preferences_api = preferences_api,
            signatures_api = signatures_api,
            ghost_alias_api = ghost_alias_api,
            auto_forward_api = auto_forward_api,
            developer_api = developer_api,
            subscriptions_api = subscriptions_api,
            recovery_email_api = recovery_email_api,
            security_api = security_api,
            encryption_api = encryption_api,
            alias_detail_api = alias_detail_api,
            mail_rules_api = mail_rules_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        )
        advanceUntilIdle()

        assertNotNull(fresh_vm.state.value.preferences)
        assertFalse(fresh_vm.state.value.preferences_authoritative)
    }

    @Test
    fun `load_preferences marks authoritative after decrypt-failure recovery`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse(
                encrypted_preferences = "blob",
                preferences_nonce = "nonce",
            )
        every { session_key_store.get_identity_key() } returns "current_key_after_password_reset"

        vm.load_preferences()
        advanceUntilIdle()

        assertTrue(vm.state.value.preferences_authoritative)
    }

    @Test
    fun `load_preferences error sets error message`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } throws org.astermail.android.api.ApiError.UnknownError("prefs error")

        vm.load_preferences(force = true)
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("prefs error", vm.state.value.error)
        assertNotNull(vm.state.value.preferences)
    }

    @Test
    fun `save_preferences error sets error status`() = runTest {
        every { session_key_store.get_identity_key() } throws RuntimeException("key error")

        vm.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, vm.state.value.save_status)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `load_api_keys error sets error message`() = runTest {
        coEvery { developer_api.list_api_keys() } throws org.astermail.android.api.ApiError.UnknownError("api keys error")

        vm.load_api_keys()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("api keys error", vm.state.value.error)
        assertTrue(vm.state.value.api_keys.isEmpty())
    }

    @Test
    fun `load_api_keys empty list`() = runTest {
        coEvery { developer_api.list_api_keys() } returns ApiKeyListResponse(emptyList())

        vm.load_api_keys()
        advanceUntilIdle()

        assertTrue(vm.state.value.api_keys.isEmpty())
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `create_api_key calls api and reloads`() = runTest {
        coEvery { developer_api.list_api_keys() } returns ApiKeyListResponse(emptyList())

        vm.create_api_key("Test Key")
        advanceUntilIdle()

        coVerify { developer_api.create_api_key(any()) }
        coVerify { developer_api.list_api_keys() }
    }

    @Test
    fun `create_api_key error does not reload`() = runTest {
        coEvery { developer_api.create_api_key(any()) } throws RuntimeException("error")

        vm.create_api_key("Test Key")
        advanceUntilIdle()

        coVerify(exactly = 0) { developer_api.list_api_keys() }
    }

    @Test
    fun `revoke_api_key error does not modify list`() = runTest {
        val keys = listOf(ApiKeyInfo(id = "k1"))
        coEvery { developer_api.list_api_keys() } returns ApiKeyListResponse(keys)
        coEvery { developer_api.revoke_api_key("k1") } throws RuntimeException("error")

        vm.load_api_keys()
        advanceUntilIdle()

        vm.revoke_api_key("k1")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.api_keys.size)
    }

    @Test
    fun `load_webhooks error does not set error`() = runTest {
        coEvery { developer_api.list_webhooks() } throws RuntimeException("fail")

        vm.load_webhooks()
        advanceUntilIdle()

        assertTrue(vm.state.value.webhooks.isEmpty())
        assertNull(vm.state.value.error)
    }

    @Test
    fun `load_webhooks empty list`() = runTest {
        coEvery { developer_api.list_webhooks() } returns WebhookListResponse(emptyList())

        vm.load_webhooks()
        advanceUntilIdle()

        assertTrue(vm.state.value.webhooks.isEmpty())
    }

    @Test
    fun `send_feedback delegates to settings api`() = runTest {
        vm.send_feedback("bug", "something broke")

        coVerify { settings_api.send_feedback(any()) }
    }

    @Test
    fun `send_feedback error propagates exception`() = runTest {
        coEvery { settings_api.send_feedback(any()) } throws RuntimeException("feedback error")

        var threw = false
        try {
            vm.send_feedback("bug", "broken")
        } catch (_: RuntimeException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `load_referral_info error does not set error`() = runTest {
        coEvery { labels_api.get_referral_info() } throws RuntimeException("fail")

        vm.load_referral_info()
        advanceUntilIdle()

        assertEquals(ReferralInfoResponse(), vm.state.value.referral)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `get_access_token returns null when not set`() {
        every { token_store.access_token } returns null

        assertNull(vm.get_access_token())
    }

    @Test
    fun `load_profile sets is_loading true during request`() = runTest {
        var loading_during_request = false
        coEvery { auth_api.me() } coAnswers {
            loading_during_request = vm.state.value.is_loading
            UserInfo(user_id = "u1", username = "user", email = "u@a.org", display_name = "U")
        }

        vm.load_profile()
        advanceUntilIdle()

        assertTrue(loading_during_request)
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `load_sessions sets is_loading true during request`() = runTest {
        var loading_during_request = false
        coEvery { settings_api.list_sessions() } coAnswers {
            loading_during_request = vm.state.value.is_loading
            SessionListResponse(emptyList())
        }

        vm.load_sessions()
        advanceUntilIdle()

        assertTrue(loading_during_request)
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `update_display_name sets saving status during request`() = runTest {
        var status_during_request = SaveStatus.IDLE
        coEvery { user_api.update_display_name(any()) } coAnswers {
            status_during_request = vm.state.value.save_status
            UpdateDisplayNameResponse(
                user = UserInfo(user_id = "u1", username = "user", email = "u@a.org", display_name = "New"),
            )
        }

        vm.update_display_name("New")
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVING, status_during_request)
        assertEquals(SaveStatus.SAVED, vm.state.value.save_status)
    }

    @Test
    fun `save_preferences sets saving then saved status`() = runTest {
        vm.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, vm.state.value.save_status)
    }

    @Test
    fun `create_forwarding_rule sets saving status during request`() = runTest {
        var status_during_request = SaveStatus.IDLE
        coEvery { auto_forward_api.create_rule(any()) } coAnswers {
            status_during_request = vm.state.value.save_status
            ForwardingRule(id = "r1", forward_to = listOf("fwd@test.com"), is_enabled = true)
        }
        coEvery { auto_forward_api.list_rules() } returns ForwardingRulesResponse(emptyList())

        vm.create_forwarding_rule("fwd@test.com", true)
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVING, status_during_request)
        assertEquals(SaveStatus.SAVED, vm.state.value.save_status)
    }

    @Test
    fun `load_profile clears previous error`() = runTest {
        coEvery { auth_api.me() } throws org.astermail.android.api.ApiError.UnknownError("first error")
        vm.load_profile()
        advanceUntilIdle()
        assertEquals("first error", vm.state.value.error)

        coEvery { auth_api.me() } returns UserInfo(
            user_id = "u1", username = "user", email = "u@a.org", display_name = "U",
        )
        vm.load_profile()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.user)
    }

    @Test
    fun `load_profile error with null message uses fallback`() = runTest {
        coEvery { auth_api.me() } throws RuntimeException(null as String?)

        vm.load_profile()
        advanceUntilIdle()

        assertEquals("Something went wrong", vm.state.value.error)
    }

    @Test
    fun `update_display_name error with null message uses fallback`() = runTest {
        coEvery { user_api.update_display_name(any()) } throws RuntimeException(null as String?)

        vm.update_display_name("name")
        advanceUntilIdle()

        assertEquals("Something went wrong", vm.state.value.error)
    }

    @Test
    fun `sequential load then mutate preserves unrelated state`() = runTest {
        val sessions = listOf(SessionInfo(id = "s1", is_current = true))
        val blocked = listOf(encrypted_blocked_sender("spam@x.com"))
        coEvery { settings_api.list_sessions() } returns SessionListResponse(sessions)
        coEvery { settings_api.list_blocked_senders() } returns BlockedSendersResponse(blocked)

        vm.load_sessions()
        advanceUntilIdle()
        vm.load_blocked_senders()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.sessions.size)
        assertEquals(1, vm.state.value.blocked_senders.size)

        vm.block_sender("new@evil.com")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.sessions.size)
        assertEquals(2, vm.state.value.blocked_senders.size)
    }

    @Test
    fun `multiple reset_save_status calls are idempotent`() {
        vm.reset_save_status()
        vm.reset_save_status()

        assertEquals(SaveStatus.IDLE, vm.state.value.save_status)
    }

    @Test
    fun `logout delegates to auth repository only`() = runTest {
        vm.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { auth_repository.logout() }
    }

    @Test
    fun `load_labels decryption skipped when no identity key`() = runTest {
        val labels = listOf(
            LabelItem(id = "l1", label_token = "lt1", encrypted_name = "enc_value", name_nonce = "nonce_val"),
        )
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(labels)
        every { session_key_store.get_identity_key() } returns null

        vm.load_labels()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.labels.size)
        assertNull(vm.state.value.labels[0].encrypted_name)
    }

    @Test
    fun `load_aliases decryption with blank encrypted_local_part returns alias as is`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "", domain = "astermail.org"),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.aliases.size)
        assertEquals("", vm.state.value.aliases[0].encrypted_local_part)
    }

    @Test
    fun `load_storage then load_subscription preserves both`() = runTest {
        val storage = StorageOverview(used_bytes = 100, total_bytes = 1000)
        val sub = SubscriptionInfo(plan_name = "Star", status = "active", amount = 999)
        coEvery { settings_api.get_storage_overview() } returns storage
        coEvery { settings_api.get_subscription() } returns sub

        vm.load_storage()
        advanceUntilIdle()
        vm.load_subscription()
        advanceUntilIdle()

        assertNotNull(vm.state.value.storage)
        assertEquals(100L, vm.state.value.storage?.used_bytes)
        assertNotNull(vm.state.value.subscription)
        assertEquals("Star", vm.state.value.subscription?.plan_name)
    }

    @Test
    fun `block_then_unblock_sender results in empty list`() = runTest {
        vm.block_sender("test@evil.com")
        advanceUntilIdle()
        assertEquals(1, vm.state.value.blocked_senders.size)

        vm.unblock_sender(blocked_sender_token("test@evil.com"))
        advanceUntilIdle()
        assertTrue(vm.state.value.blocked_senders.isEmpty())
    }

    @Test
    fun `load_sessions replaces previous sessions`() = runTest {
        coEvery { settings_api.list_sessions() } returns SessionListResponse(
            listOf(SessionInfo(id = "s1", is_current = true)),
        )

        vm.load_sessions()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.sessions.size)

        coEvery { settings_api.list_sessions() } returns SessionListResponse(
            listOf(
                SessionInfo(id = "s1", is_current = true),
                SessionInfo(id = "s2", is_current = false),
            ),
        )

        vm.load_sessions()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.sessions.size)
    }

    @Test
    fun `error then success clears error for load_blocked_senders`() = runTest {
        coEvery { settings_api.list_blocked_senders() } throws RuntimeException("fail")
        vm.load_blocked_senders()
        advanceUntilIdle()
        assertNotNull(vm.state.value.blocked_senders_error)

        coEvery { settings_api.list_blocked_senders() } returns BlockedSendersResponse(emptyList())
        vm.load_blocked_senders()
        advanceUntilIdle()
        assertNull(vm.state.value.blocked_senders_error)
    }

    @Test
    fun `error then success clears error for load_storage`() = runTest {
        coEvery { settings_api.get_storage_overview() } throws RuntimeException("fail")
        vm.load_storage()
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        coEvery { settings_api.get_storage_overview() } returns StorageOverview(
            used_bytes = 100, total_bytes = 1000,
        )
        vm.load_storage()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `error then success clears error for load_subscription`() = runTest {
        coEvery { settings_api.get_subscription() } throws RuntimeException("fail")
        vm.load_subscription()
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        coEvery { settings_api.get_subscription() } returns SubscriptionInfo(plan_name = "Star")
        vm.load_subscription()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
    }
}
