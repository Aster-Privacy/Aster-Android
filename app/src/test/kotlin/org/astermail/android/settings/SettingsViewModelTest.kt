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

import android.content.Context
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.aliases.AliasDetailApi
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
import org.astermail.android.api.encryption.EncryptionApi
import org.astermail.android.api.family.FamilyApi
import org.astermail.android.api.ghost.CreateGhostAliasResponse
import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.ghost.GhostAliasApi
import org.astermail.android.api.ghost.GhostAliasListResponse
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.labels.BulkReorderLabelsRequest
import org.astermail.android.api.labels.BulkReorderLabelsResponse
import org.astermail.android.api.labels.CreateLabelRequest
import org.astermail.android.api.labels.CreateLabelResponse
import org.astermail.android.api.labels.DeleteLabelResponse
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.LabelsApi
import org.astermail.android.api.labels.LabelsListResponse
import org.astermail.android.api.labels.ReferralInfoResponse
import org.astermail.android.api.labels.UpdateLabelRequest
import org.astermail.android.api.mail_rules.MailRulesApi
import org.astermail.android.api.preferences.EncryptedPreferencesResponse
import org.astermail.android.api.preferences.PreferencesApi
import org.astermail.android.api.preferences.PreferencesSaveResult
import org.astermail.android.api.preferences.SaveEncryptedPreferencesRequest
import org.astermail.android.api.preferences.SaveVersionedPreferencesRequest
import org.astermail.android.api.preferences.VersionedEncryptedPreferences
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.recovery.RecoveryApi
import org.astermail.android.api.recovery_email.RecoveryEmailApi
import org.astermail.android.api.recovery_email.RecoveryEmailStateResponse
import org.astermail.android.api.recovery_email.RecoveryEmailApiImpl
import org.astermail.android.api.recovery_email.RecoveryEmailError
import org.astermail.android.api.security.SecurityApi
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.AliasListResponse
import org.astermail.android.api.settings.AllDomainAddressesResponse
import org.astermail.android.api.settings.BlockedSenderInfo
import org.astermail.android.api.settings.BlockedSendersResponse
import org.astermail.android.api.settings.CheckAliasAvailabilityRequest
import org.astermail.android.api.settings.CheckAliasAvailabilityResponse
import org.astermail.android.api.settings.CreateAliasRequest
import org.astermail.android.api.settings.CreateAliasResponse
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.astermail.android.api.settings.DeletedAliasInfo
import org.astermail.android.api.settings.ListDeletedAliasesResponse
import org.astermail.android.api.settings.ListSmtpTokensResponse
import org.astermail.android.api.settings.NormalizeAliasRequest
import org.astermail.android.api.settings.NormalizeAliasResponse
import org.astermail.android.api.settings.RekeyAliasesRequest
import org.astermail.android.api.settings.RekeyAliasesResponse
import org.astermail.android.api.settings.SecurityStatusResponse
import org.astermail.android.api.settings.SessionInfo
import org.astermail.android.api.settings.SessionListResponse
import org.astermail.android.api.settings.SettingsApi
import org.astermail.android.api.settings.SmtpTokenRow
import org.astermail.android.api.settings.StorageOverview
import org.astermail.android.api.settings.SubscriptionInfo
import org.astermail.android.api.settings.TwinAddressResponse
import org.astermail.android.api.settings.UpdateAliasRequest
import org.astermail.android.api.signatures.ListSignaturesResponse
import org.astermail.android.api.signatures.Signature
import org.astermail.android.api.signatures.SignaturesApi
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.api.tags.BulkReorderTagsRequest
import org.astermail.android.api.tags.BulkReorderTagsResponse
import org.astermail.android.api.tags.TagItem
import org.astermail.android.api.tags.TagsApi
import org.astermail.android.api.tags.TagsListResponse
import org.astermail.android.api.tags.UpdateTagRequest
import org.astermail.android.api.user.UpdateDisplayNameResponse
import org.astermail.android.api.user.UserApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.ArchivedBlob
import org.astermail.android.storage.PreferencesCacheStore
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.TextSize
import org.astermail.android.storage.ThemeMode
import org.astermail.android.storage.ThemeStore
import org.astermail.android.storage.TokenStore
import org.astermail.android.ui.settings.detail.SmtpTokensBody
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
    private lateinit var family_api: FamilyApi
    private lateinit var tags_api: TagsApi
    private lateinit var preferences_api: PreferencesApi
    private lateinit var signatures_api: SignaturesApi
    private lateinit var ghost_alias_api: GhostAliasApi
    private lateinit var auto_forward_api: AutoForwardApi
    private lateinit var developer_api: DeveloperApi
    private lateinit var subscriptions_api: SubscriptionsApi
    private lateinit var recovery_email_api: RecoveryEmailApi
    private lateinit var security_api: SecurityApi
    private lateinit var encryption_api: EncryptionApi
    private lateinit var alias_detail_api: AliasDetailApi
    private lateinit var recovery_api: RecoveryApi
    private lateinit var mail_rules_api: MailRulesApi
    private lateinit var keys_api: KeysApi
    private lateinit var auth_repository: AuthRepository
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var token_store: TokenStore
    private lateinit var account_store: AccountStore
    private lateinit var preferences_cache: PreferencesCacheStore
    private lateinit var theme_store: ThemeStore
    private lateinit var context: Context
    private lateinit var vm: SettingsViewModel

    private val test_data_kek = ByteArray(32) { (it + 1).toByte() }

    private fun blocked_senders_hmac_key(): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(test_data_kek + "blocked-senders-hmac-v1".toByteArray(Charsets.UTF_8))

    private fun hmac_b64(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data))
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
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(test_data_kek, "AES"),
            GCMParameterSpec(128, nonce),
        )
        val encoder = Base64.getEncoder()
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
            Base64.getEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            Base64.getDecoder().decode(firstArg<String>())
        }
        io.mockk.mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<Throwable>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
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
        recovery_api = mockk(relaxed = true)
        mail_rules_api = mockk(relaxed = true)
        keys_api = mockk(relaxed = true)
        auth_repository = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_data_kek() } answers { test_data_kek.copyOf() }
        every { session_key_store.keys_settled() } returns true
        token_store = mockk(relaxed = true)
        account_store = mockk(relaxed = true)
        preferences_cache = mockk(relaxed = true)
        every { auth_repository.is_signed_in } returns MutableStateFlow(false)
        theme_store = mockk(relaxed = true)
        every { theme_store.theme_mode } returns
            MutableStateFlow(ThemeMode.system)
        every { theme_store.text_size } returns
            MutableStateFlow(TextSize.default_size)
        every { theme_store.color_theme } returns MutableStateFlow("aster")
        every { theme_store.custom_theme_seed } returns MutableStateFlow("")
        every { theme_store.custom_theme_overrides } returns
            MutableStateFlow(emptyMap())
        every { theme_store.font_choice } returns MutableStateFlow("default")
        every { theme_store.high_contrast } returns MutableStateFlow(false)
        every { theme_store.reduce_transparency } returns MutableStateFlow(false)
        every { theme_store.reduce_motion } returns MutableStateFlow(false)
        every { theme_store.compact_mode } returns MutableStateFlow(false)
        every { theme_store.text_spacing } returns MutableStateFlow(false)
        every { theme_store.underline_links } returns MutableStateFlow(false)
        every { theme_store.hide_scrollbars } returns MutableStateFlow(false)
        every { theme_store.dyslexia_font } returns MutableStateFlow(false)
        every { theme_store.haptic_enabled } returns MutableStateFlow(true)
        context = mockk(relaxed = true)
        every { context.getString(R.string.something_went_wrong) } returns
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
            recovery_api = recovery_api,
            mail_rules_api = mail_rules_api,
            keys_api = keys_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        )
        vm.default_dispatcher = dispatcher
        vm.io_dispatcher = dispatcher
        vm.await_session_ready = {}
    }

    @After
    fun teardown() {
        io.mockk.unmockkStatic(Log::class)
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
        coEvery { auth_api.me() } throws ApiError.UnknownError("unauthorized")

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
    fun `revoke_session rechecks the local session`() = runTest {
        coEvery { settings_api.list_sessions() } returns SessionListResponse(emptyList())

        vm.revoke_session("s1")
        advanceUntilIdle()

        coVerify { auth_repository.handle_unauthorized_signal(true) }
    }

    @Test
    fun `revoke_session rechecks the local session after a failure`() = runTest {
        coEvery { settings_api.revoke_session("s1") } throws
            ApiError.UnauthorizedError

        vm.revoke_session("s1")
        advanceUntilIdle()

        coVerify { auth_repository.handle_unauthorized_signal(true) }
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
    fun `recovery email presence is unknown until the server answers`() = runTest {
        assertFalse(vm.state.value.recovery_email_known)

        coEvery { recovery_email_api.get_state() } returns
            RecoveryEmailStateResponse(exists = true)
        vm.refresh_recovery_email_presence()
        advanceUntilIdle()

        assertTrue(vm.state.value.recovery_email_known)
        assertTrue(vm.state.value.recovery_email_set)
    }

    @Test
    fun `recovery email save asks for a password when the server requires one`() = runTest {
        every { session_key_store.get_identity_key() } returns "identity-key"
        coEvery { recovery_email_api.get_state() } returns RecoveryEmailStateResponse()
        coEvery { recovery_email_api.save(any()) } throws
            RecoveryEmailError(RecoveryEmailApiImpl.STEP_UP_REQUIRED, "To make this change, confirm your password.")

        vm.save_recovery_email("new.address@example.com", null, null, get_credential = { "" })
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, vm.state.value.save_status)
        assertTrue(vm.state.value.recovery_email_step_up_required)
        assertFalse(vm.state.value.recovery_email_totp_required)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `recovery email save asks for a code when the server requires one`() = runTest {
        every { session_key_store.get_identity_key() } returns "identity-key"
        coEvery { auth_repository.derive_password_hash_b64("pw") } returns "hash"
        coEvery { recovery_email_api.get_state() } returns RecoveryEmailStateResponse()
        coEvery { recovery_email_api.save(any()) } throws
            RecoveryEmailError(RecoveryEmailApiImpl.TOTP_REQUIRED, "Enter your authenticator code.")

        vm.save_recovery_email("new.address@example.com", "pw", null, get_credential = { "" })
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, vm.state.value.save_status)
        assertTrue(vm.state.value.recovery_email_step_up_required)
        assertTrue(vm.state.value.recovery_email_totp_required)
    }

    @Test
    fun `failed recovery email check leaves presence unknown`() = runTest {
        coEvery { recovery_email_api.get_state() } throws IOException("offline")
        vm.refresh_recovery_email_presence()
        advanceUntilIdle()

        assertFalse(vm.state.value.recovery_email_known)
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
            CreateGhostAliasResponse(
                id = "ga1",
                success = true,
                expires_at = "2026-07-01T00:00:00Z",
                grace_expires_at = "2026-07-08T00:00:00Z",
            )

        vm.create_ghost_alias()
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

        vm.create_label(CreateLabelRequest(
            label_token = "lt_new", encrypted_name = "enc", name_nonce = "n",
        ))
        advanceUntilIdle()

        coVerify { labels_api.create_label(any()) }
        coVerify { labels_api.list_labels(any(), any()) }
    }

    @Test
    fun `load_preferences populates defaults when no encrypted data`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            EncryptedPreferencesResponse()

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
    fun `forget learned senders clears the list`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns EncryptedPreferencesResponse()
        coEvery { preferences_api.get_preferences_raw() } returns "{}"
        coEvery { preferences_api.get_preferences() } returns UserPreferences()
        vm.load_preferences(force = true)
        advanceUntilIdle()
        val learned = listOf(
            org.astermail.android.api.preferences.LearnedCategorySender("a@x.com", "social", 10),
            org.astermail.android.api.preferences.LearnedCategorySender("b@x.com", "updates", 20),
        )
        vm.save_preferences(vm.state.value.preferences!!.copy(learned_category_senders = learned))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.preferences?.learned_category_senders?.size)

        vm.forget_learned_category_senders()
        advanceUntilIdle()

        assertEquals(emptyList<org.astermail.android.api.preferences.LearnedCategorySender>(), vm.state.value.preferences?.learned_category_senders)
    }

    @Test
    fun `learned edits from a move are saved and undone`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns EncryptedPreferencesResponse()
        coEvery { preferences_api.get_preferences_raw() } returns "{}"
        coEvery { preferences_api.get_preferences() } returns UserPreferences()
        vm.load_preferences(force = true)
        advanceUntilIdle()
        val move = org.astermail.android.mail.learn_from_category_move(
            current = emptyList(),
            learning_enabled = true,
            sources = listOf(org.astermail.android.mail.LearnedMoveSource("shop@store.com", null, false, true)),
            category = "shopping",
            now_seconds = 100,
        )!!

        vm.apply_learned_category_edit(move.apply)
        advanceUntilIdle()
        assertEquals(
            listOf(org.astermail.android.api.preferences.LearnedCategorySender("shop@store.com", "shopping", 100)),
            vm.state.value.preferences?.learned_category_senders,
        )

        vm.apply_learned_category_edit(move.undo)
        advanceUntilIdle()
        assertEquals(emptyList<org.astermail.android.api.preferences.LearnedCategorySender>(), vm.state.value.preferences?.learned_category_senders)
    }

    @Test
    fun `learned edit before preferences load changes nothing and asks to retry`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } throws RuntimeException("offline")
        every { preferences_cache.read(any()) } returns null
        every { context.getString(R.string.preferences_locked_retry) } returns "locked"
        val target = new_vm()
        target.load_preferences(force = true)
        advanceUntilIdle()
        assertFalse(target.state.value.preferences_authoritative)
        assertFalse(target.state.value.preferences_cached)
        io.mockk.clearMocks(preferences_api, answers = false, recordedCalls = true)

        target.apply_learned_category_edit(
            org.astermail.android.mail.LearnedCategoryEdit(
                upserts = listOf(org.astermail.android.api.preferences.LearnedCategorySender("shop@store.com", "shopping", 100)),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            emptyList<org.astermail.android.api.preferences.LearnedCategorySender>(),
            target.state.value.preferences?.learned_category_senders.orEmpty(),
        )
        assertEquals(SaveStatus.ERROR, target.state.value.save_status)
        assertEquals("locked", target.state.value.error)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        coVerify(exactly = 0) { preferences_api.save_preferences(any()) }
        coVerify(exactly = 0) { preferences_api.save_preferences_raw(any()) }
    }

    @Test
    fun `cached learned senders are known before the server load finishes`() = runTest {
        val learned = listOf(org.astermail.android.api.preferences.LearnedCategorySender("shop@store.com", "shopping", 100))
        coEvery { preferences_api.get_encrypted_preferences() } throws RuntimeException("offline")
        every { session_key_store.get_user_id() } returns "user_1"
        every { preferences_cache.read("user_1") } returns
            kotlinx.serialization.json.Json { encodeDefaults = true }
                .encodeToString(UserPreferences.serializer(), UserPreferences(learned_category_senders = learned))

        val target = new_vm()

        assertTrue(target.state.value.preferences_cached)
        assertEquals(learned, target.state.value.preferences?.learned_category_senders)
        target.load_preferences(force = true)
        advanceUntilIdle()
        assertTrue(target.state.value.preferences_cached)
        assertEquals(learned, target.state.value.preferences?.learned_category_senders)
    }

    @Test
    fun `turning off learning keeps learned senders`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns EncryptedPreferencesResponse()
        coEvery { preferences_api.get_preferences_raw() } returns "{}"
        coEvery { preferences_api.get_preferences() } returns UserPreferences()
        vm.load_preferences(force = true)
        advanceUntilIdle()
        val learned = listOf(org.astermail.android.api.preferences.LearnedCategorySender("a@x.com", "social", 10))
        vm.save_preferences(vm.state.value.preferences!!.copy(learned_category_senders = learned))
        advanceUntilIdle()

        vm.set_learn_from_category_moves(false)
        advanceUntilIdle()

        assertEquals(false, vm.state.value.preferences?.learn_from_category_moves)
        assertEquals(learned, vm.state.value.preferences?.learned_category_senders)
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
    fun `get_recovery_codes delegates to session key store`() = runTest {
        every { session_key_store.get_recovery_codes() } returns listOf("code1", "code2", "code3")

        val codes = vm.get_recovery_codes()

        assertEquals(3, codes?.size)
        assertEquals("code1", codes?.get(0))
        verify { session_key_store.get_recovery_codes() }
    }

    @Test
    fun `get_recovery_codes returns null when no codes stored`() = runTest {
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
    fun `load_aliases settles its own loading flag while another load is still running`() = runTest {
        val never = CompletableDeferred<LabelsListResponse>()
        coEvery { labels_api.list_labels(include_counts = any(), folder_type = any()) } coAnswers { never.await() }
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(emptyList())
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()
        vm.load_labels(folder_type = "folder")
        advanceUntilIdle()

        assertTrue(vm.state.value.is_loading)
        assertFalse(vm.state.value.aliases_loading)
        assertTrue(vm.state.value.aliases.isEmpty())
        never.cancel()
    }

    @Test
    fun `load_aliases clears its loading flag when the request fails`() = runTest {
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } throws RuntimeException("offline")

        vm.load_aliases()
        advanceUntilIdle()

        assertFalse(vm.state.value.aliases_loading)
    }

    @Test
    fun `load_twin_address keeps a finished alias load settled`() = runTest {
        val alias_gate = CompletableDeferred<AliasListResponse>()
        val twin_gate = CompletableDeferred<TwinAddressResponse>()
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } coAnswers { alias_gate.await() }
        coEvery { settings_api.get_twin_address() } coAnswers { twin_gate.await() }
        every { session_key_store.get_identity_key() } returns null
        vm.load_aliases()
        vm.load_twin_address()
        advanceUntilIdle()
        assertTrue(vm.state.value.aliases_loading)
        alias_gate.complete(AliasListResponse(emptyList()))
        advanceUntilIdle()
        assertFalse(vm.state.value.aliases_loading)
        twin_gate.complete(TwinAddressResponse(address = "user@aster.cx", domain = "aster.cx", local_part = "user", state = "available"))
        advanceUntilIdle()
        assertFalse(vm.state.value.aliases_loading)
        assertFalse(vm.state.value.is_loading)
        assertEquals("user@aster.cx", vm.state.value.twin_address?.address)
    }

    private fun sealed_alias(
        id: String,
        local_part: String,
        routing_hash: String,
        domain: String = "aster.cx",
        orphaned: Boolean = false,
        retained: Boolean = false,
    ): AliasInfo {
        val (enc, nonce) = kotlinx.coroutines.runBlocking { encrypt_alias_field_with(session_key_store, local_part) }
        return AliasInfo(
            id = id,
            encrypted_local_part = enc,
            local_part_nonce = nonce,
            routing_address_hash = routing_hash,
            orphaned_by_key_rotation = orphaned,
            is_retained_primary = retained,
            domain = domain,
        )
    }

    @Test
    fun `load_custom_domain_addresses recovers directory records without modifying server records`() = runTest {
        val encoder = Base64.getEncoder()
        val local_part = "merchant.finance"
        val bytes = local_part.toByteArray(Charsets.UTF_8)
        val directory_address = CustomDomainAddressInfo(
            id = "directory-address",
            domain_name = "custom.example",
            encrypted_local_part = encoder.encodeToString(bytes),
            local_part_hash = encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes)),
        )
        val encrypted_alias = sealed_alias("encrypted", "regular", "", domain = "custom.example")
        val encrypted_address = directory_address.copy(
            id = "encrypted-address",
            encrypted_local_part = encrypted_alias.encrypted_local_part,
            local_part_nonce = encrypted_alias.local_part_nonce,
            local_part_hash = "unrelated-keyed-hash",
        )
        val invalid_address = directory_address.copy(id = "invalid-address", local_part_hash = "")
        coEvery { settings_api.list_all_domain_addresses() } returns
            AllDomainAddressesResponse(listOf(directory_address, encrypted_address, invalid_address))

        vm.load_custom_domain_addresses(force = true)
        advanceUntilIdle()

        val addresses = vm.state.value.custom_domain_addresses
        assertEquals(listOf("directory-address", "encrypted-address", "invalid-address"), addresses.map { it.id })
        assertEquals("merchant.finance@custom.example", addresses[0].address)
        assertFalse(addresses[0].decryption_failed)
        assertEquals("regular@custom.example", addresses[1].address)
        assertFalse(addresses[1].decryption_failed)
        assertTrue(addresses[2].decryption_failed)
        coVerify(exactly = 0) { settings_api.create_domain_address(any(), any()) }
        coVerify(exactly = 0) { settings_api.update_domain_address(any(), any(), any()) }
        coVerify(exactly = 0) { settings_api.delete_domain_address(any(), any(), any()) }
        coVerify(exactly = 0) { settings_api.normalize_alias(any(), any()) }
    }

    private class KeyGate(private val store: SessionKeyStore, private val data_kek: ByteArray) {
        var open = false
        val material = MutableStateFlow(0L)

        init {
            every { store.keys_settled() } answers { open }
            every { store.is_locked() } answers { !open }
            every { store.get_data_kek() } answers { if (open) data_kek.copyOf() else null }
            every { store.get_identity_key() } returns null
            every { store.identity_key_events() } returns MutableStateFlow(0L)
            every { store.account_keys_ready_events() } returns MutableStateFlow(0L)
            every { store.key_material_events() } returns material
            every { store.key_readiness_epoch() } answers { material.value }
        }

        fun unlock() {
            open = true
            material.value = material.value + 1
        }
    }

    @Test
    fun `aliases loaded before sealed keys open are not shown as locked`() = runTest {
        val alias = sealed_alias("a1", "news", routing_hash_of("news@aster.cx"))
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(listOf(alias))
        coEvery { auth_repository.try_refresh_vault_keys() } returns false
        val gate = KeyGate(session_key_store, test_data_kek)

        vm.load_aliases()
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(vm.state.value.aliases.none { it.decryption_failed })
        assertTrue(vm.state.value.aliases.none { vm.alias_is_restorable(it) })

        gate.unlock()
        advanceTimeBy(1_000)
        runCurrent()

        val shown = vm.state.value.aliases.single()
        assertFalse(shown.decryption_failed)
        assertEquals("news", shown.encrypted_local_part)
    }

    @Test
    fun `aliases screen reload inside the list ttl still decrypts after keys open`() = runTest {
        val alias = sealed_alias("a1", "news", routing_hash_of("news@aster.cx"))
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(listOf(alias))
        coEvery { auth_repository.try_refresh_vault_keys() } returns false
        val gate = KeyGate(session_key_store, test_data_kek)

        vm.load_aliases()
        runCurrent()
        gate.open = true
        vm.load_aliases()
        runCurrent()

        val shown = vm.state.value.aliases.single()
        assertFalse(shown.decryption_failed)
        assertEquals("news", shown.encrypted_local_part)
    }

    @Test
    fun `domain addresses loaded before sealed keys open are not shown as locked`() = runTest {
        val sealed = sealed_alias("seal", "billing", "", domain = "custom.example")
        val address = CustomDomainAddressInfo(
            id = "addr-1",
            domain_name = "custom.example",
            encrypted_local_part = sealed.encrypted_local_part,
            local_part_nonce = sealed.local_part_nonce,
        )
        coEvery { settings_api.list_all_domain_addresses() } returns AllDomainAddressesResponse(listOf(address))
        val gate = KeyGate(session_key_store, test_data_kek)

        vm.load_custom_domain_addresses()
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(vm.state.value.custom_domain_addresses.none { it.decryption_failed })

        gate.unlock()
        advanceTimeBy(1_000)
        runCurrent()

        val shown = vm.state.value.custom_domain_addresses.single()
        assertFalse(shown.decryption_failed)
        assertEquals("billing@custom.example", shown.address)
    }

    @Test
    fun `aliases sealed under a changed password stay locked once keys are settled`() = runTest {
        val other_key = ByteArray(32) { (it * 7 + 3).toByte() }
        every { session_key_store.get_data_kek() } answers { other_key.copyOf() }
        val alias = sealed_alias("a1", "news", routing_hash_of("news@aster.cx"))
        every { session_key_store.get_data_kek() } answers { test_data_kek.copyOf() }
        every { session_key_store.get_identity_key() } returns null
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(listOf(alias))
        coEvery { auth_repository.try_refresh_vault_keys() } returns false

        vm.load_aliases()
        advanceUntilIdle()

        val shown = vm.state.value.aliases.single()
        assertTrue(shown.decryption_failed)
        assertTrue(vm.alias_is_restorable(shown))
        coVerify(exactly = 1) { settings_api.list_aliases(limit = any(), offset = any()) }
    }

    @Test
    fun `legacy routing hash is the dotted address and absent without a dot`() {
        assertEquals(
            routing_hash_of("jesper.floymo@aster.cx"),
            compute_legacy_routing_address_hash_for(" Jesper.Floymo ", "Aster.CX"),
        )
        assertEquals(null, compute_legacy_routing_address_hash_for("jesperfloymo", "aster.cx"))
        assertEquals(
            routing_hash_of("jesperfloymo@aster.cx"),
            compute_routing_address_hash_for("jesper.floymo", "aster.cx"),
        )
    }

    @Test
    fun `load_aliases normalizes a dotted alias that still has the legacy routing hash`() = runTest {
        val legacy = sealed_alias("a1", "jesper.floymo", routing_hash_of("jesper.floymo@aster.cx"))
        val current = sealed_alias("a2", "jesperfloymo", routing_hash_of("jesperfloymo@aster.cx"))
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returnsMany listOf(
            AliasListResponse(listOf(legacy, current)),
            AliasListResponse(listOf(current)),
        )
        coEvery { settings_api.normalize_alias(any(), any()) } returns
            NormalizeAliasResponse(status = "merged", alias_id = "a2")
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()

        val request = slot<NormalizeAliasRequest>()
        coVerify(exactly = 1) { settings_api.normalize_alias("a1", capture(request)) }
        coVerify(exactly = 0) { settings_api.normalize_alias("a2", any()) }
        assertEquals("jesper.floymo", request.captured.local_part)
        coVerify(exactly = 2) { settings_api.list_aliases(limit = any(), offset = any()) }
        assertEquals(listOf("a2"), vm.state.value.aliases.map { it.id })
    }

    @Test
    fun `load_aliases skips aliases that cannot or need not be normalized`() = runTest {
        val aliases = listOf(
            sealed_alias("a1", "jesper.floymo", routing_hash_of("jesperfloymo@aster.cx")),
            sealed_alias("a2", "plain", routing_hash_of("plain@aster.cx")),
            sealed_alias("a3", "old.one", routing_hash_of("old.one@aster.cx"), orphaned = true),
            sealed_alias("a4", "kept.primary", routing_hash_of("kept.primary@aster.cx"), retained = true),
            sealed_alias("a5", "no.hash", ""),
            AliasInfo(
                id = "a6",
                encrypted_local_part = Base64.getEncoder().encodeToString(ByteArray(24) { 7 }),
                local_part_nonce = Base64.getEncoder().encodeToString(ByteArray(12) { 3 }),
                routing_address_hash = routing_hash_of("x.y@aster.cx"),
                domain = "aster.cx",
            ),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()

        coVerify(exactly = 0) { settings_api.normalize_alias(any(), any()) }
        assertEquals(6, vm.state.value.aliases.size)
    }

    @Test
    fun `load_aliases normalizes once per session and keeps the list when normalize fails`() = runTest {
        val legacy = sealed_alias("a1", "jesper.floymo", routing_hash_of("jesper.floymo@aster.cx"))
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(listOf(legacy))
        coEvery { settings_api.normalize_alias(any(), any()) } throws RuntimeException("offline")
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()
        vm.load_aliases(force = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { settings_api.normalize_alias(any(), any()) }
        coVerify(exactly = 2) { settings_api.list_aliases(limit = any(), offset = any()) }
        assertEquals(listOf("a1"), vm.state.value.aliases.map { it.id })
        assertFalse(vm.state.value.aliases_loading)
    }

    @Test
    fun `load_aliases does not reload when normalize reports a conflict`() = runTest {
        val legacy = sealed_alias("a1", "jesper.floymo", routing_hash_of("jesper.floymo@aster.cx"))
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(listOf(legacy))
        coEvery { settings_api.normalize_alias(any(), any()) } returns
            NormalizeAliasResponse(status = "conflict", alias_id = "a1")
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()

        coVerify(exactly = 1) { settings_api.list_aliases(limit = any(), offset = any()) }
    }

    @Test
    fun `create and availability send the legacy routing hash only for dotted addresses`() = runTest {
        coEvery { settings_api.check_alias_availability(any()) } returns
            CheckAliasAvailabilityResponse(available = false)
        coEvery { settings_api.create_alias(any()) } returns
            CreateAliasResponse(id = "new", success = true)
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(emptyList())
        every { session_key_store.get_identity_key() } returns null

        vm.check_alias_availability("Jesper.Floymo", "aster.cx")
        vm.check_alias_availability("jesperfloymo", "aster.cx")
        vm.create_alias_now("Jesper.Floymo", "aster.cx")
        advanceUntilIdle()

        val checks = mutableListOf<CheckAliasAvailabilityRequest>()
        coVerify(exactly = 2) { settings_api.check_alias_availability(capture(checks)) }
        assertEquals(routing_hash_of("jesper.floymo@aster.cx"), checks[0].legacy_routing_address_hash)
        assertEquals(routing_hash_of("jesperfloymo@aster.cx"), checks[0].routing_address_hash)
        assertEquals(null, checks[1].legacy_routing_address_hash)
        val created = slot<CreateAliasRequest>()
        coVerify { settings_api.create_alias(capture(created)) }
        assertEquals(routing_hash_of("jesper.floymo@aster.cx"), created.captured.legacy_routing_address_hash)
    }

    private fun routing_hash_of(address: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(address.toByteArray(Charsets.UTF_8)),
        )

    private fun orphaned_alias(routing_hash: String): AliasInfo = AliasInfo(
        id = "a1",
        encrypted_local_part = Base64.getEncoder().encodeToString(ByteArray(24) { 7 }),
        local_part_nonce = Base64.getEncoder().encodeToString(ByteArray(12) { 3 }),
        routing_address_hash = routing_hash,
        orphaned_by_key_rotation = true,
        domain = "astermail.org",
    )

    @Test
    fun `restore_orphaned_alias re-encrypts the label when the address matches`() = runTest {
        val alias = orphaned_alias(routing_hash_of("spamme@astermail.org"))
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns
            AliasListResponse(listOf(alias))
        coEvery { settings_api.rekey_aliases(any()) } returns
            RekeyAliasesResponse(success = true, aliases_updated = 1)
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()
        assertTrue(vm.state.value.aliases.first().decryption_failed)
        assertTrue(vm.alias_is_restorable(vm.state.value.aliases.first()))

        val outcome = vm.restore_orphaned_alias("a1", " SpamMe ")
        advanceUntilIdle()

        assertEquals(SettingsViewModel.AliasRestoreResult.Restored, outcome)
        val request = slot<RekeyAliasesRequest>()
        coVerify { settings_api.rekey_aliases(capture(request)) }
        val entry = request.captured.re_encrypted_aliases.single()
        assertEquals("a1", entry.id)
        assertTrue(entry.encrypted_local_part.isNotBlank())
        assertTrue(entry.local_part_nonce.isNotBlank())
        assertTrue(entry.alias_address_hash.isNotBlank())
    }

    @Test
    fun `restore_orphaned_alias rejects an address that does not match`() = runTest {
        val alias = orphaned_alias(routing_hash_of("spamme@astermail.org"))
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns
            AliasListResponse(listOf(alias))
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()

        val outcome = vm.restore_orphaned_alias("a1", "notmine")
        advanceUntilIdle()

        assertEquals(SettingsViewModel.AliasRestoreResult.AddressMismatch, outcome)
        coVerify(exactly = 0) { settings_api.rekey_aliases(any()) }
    }

    @Test
    fun `restore_orphaned_alias needs a routing hash from the server`() = runTest {
        val alias = orphaned_alias("")
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns
            AliasListResponse(listOf(alias))
        every { session_key_store.get_identity_key() } returns null

        vm.load_aliases()
        advanceUntilIdle()
        assertFalse(vm.alias_is_restorable(vm.state.value.aliases.first()))

        val outcome = vm.restore_orphaned_alias("a1", "spamme")
        advanceUntilIdle()

        assertEquals(SettingsViewModel.AliasRestoreResult.Unverifiable, outcome)
        coVerify(exactly = 0) { settings_api.rekey_aliases(any()) }
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
        val encoded = Base64.getEncoder()
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
        val encoded = Base64.getEncoder()
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
        val encoded = Base64.getEncoder()
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
        coEvery { settings_api.list_sessions() } throws ApiError.UnknownError("network error")

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
        coEvery { settings_api.list_blocked_senders() } throws ApiError.UnknownError("timeout")

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
        coEvery { settings_api.get_storage_overview() } throws ApiError.UnknownError("storage unavailable")

        vm.load_storage()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_loading)
        assertEquals("storage unavailable", vm.state.value.error)
        assertNull(vm.state.value.storage)
    }

    @Test
    fun `load_subscription error sets error message`() = runTest {
        coEvery { settings_api.get_subscription() } throws ApiError.UnknownError("no subscription")

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
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } throws ApiError.UnknownError("alias error")

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
            settings_api.update_alias("a1", UpdateAliasRequest(never_inbox = true))
        } returns true

        vm.load_aliases()
        advanceUntilIdle()

        vm.set_alias_delivery("a1", null, to_archive = true)
        advanceUntilIdle()

        val updated = vm.state.value.aliases.single { it.id == "a1" }
        assertTrue(updated.never_inbox)
        assertEquals(null, updated.delivery_folder_token)
        coVerify {
            settings_api.update_alias("a1", UpdateAliasRequest(never_inbox = true))
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
                UpdateAliasRequest(delivery_folder_token = "dG9rZW4="),
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
                UpdateAliasRequest(delivery_folder_token = "dG9rZW4="),
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
            settings_api.update_alias("a1", UpdateAliasRequest(never_inbox = false))
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
    fun `set_alias_delivery_label sends the tag token`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "alias1", domain = "astermail.org"),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null
        coEvery { settings_api.update_alias_delivery_label("a1", "dGFn") } returns true

        vm.load_aliases()
        advanceUntilIdle()

        vm.set_alias_delivery_label("a1", "dGFn")
        advanceUntilIdle()

        assertEquals("dGFn", vm.state.value.aliases.single { it.id == "a1" }.delivery_label_token)
        coVerify { settings_api.update_alias_delivery_label("a1", "dGFn") }
    }

    @Test
    fun `set_alias_delivery_label clears the tag token`() = runTest {
        val aliases = listOf(
            AliasInfo(
                id = "a1",
                encrypted_local_part = "alias1",
                domain = "astermail.org",
                delivery_label_token = "dGFn",
            ),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null
        coEvery { settings_api.update_alias_delivery_label("a1", null) } returns true

        vm.load_aliases()
        advanceUntilIdle()

        vm.set_alias_delivery_label("a1", null)
        advanceUntilIdle()

        assertEquals(null, vm.state.value.aliases.single { it.id == "a1" }.delivery_label_token)
        coVerify { settings_api.update_alias_delivery_label("a1", null) }
    }

    @Test
    fun `set_alias_delivery_label rolls back on api error`() = runTest {
        val aliases = listOf(
            AliasInfo(
                id = "a1",
                encrypted_local_part = "alias1",
                domain = "astermail.org",
                delivery_label_token = "b2xk",
            ),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null
        coEvery { settings_api.update_alias_delivery_label("a1", "dGFn") } throws RuntimeException("boom")

        vm.load_aliases()
        advanceUntilIdle()

        vm.set_alias_delivery_label("a1", "dGFn")
        advanceUntilIdle()

        assertEquals("b2xk", vm.state.value.aliases.single { it.id == "a1" }.delivery_label_token)
    }

    @Test
    fun `set_alias_delivery rolls back on api error`() = runTest {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "alias1", domain = "astermail.org", never_inbox = false),
        )
        coEvery { settings_api.list_aliases(limit = any(), offset = any()) } returns AliasListResponse(aliases)
        every { session_key_store.get_identity_key() } returns null
        coEvery {
            settings_api.update_alias("a1", UpdateAliasRequest(never_inbox = true))
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
        coEvery { ghost_alias_api.list_ghost_aliases() } throws ApiError.UnknownError("ghost error")

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

        vm.create_ghost_alias()
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
    fun `expire_ghost_alias_now reports expired on success`() = runTest {
        coEvery { ghost_alias_api.list_ghost_aliases() } returns GhostAliasListResponse(emptyList())

        assertEquals(SettingsViewModel.GhostExpireOutcome.Expired, vm.expire_ghost_alias_now("g1"))
    }

    @Test
    fun `expire_ghost_alias_now reports the cooldown when the server refuses`() = runTest {
        val reason = "Ghost aliases can only be expired 30 days after creation."
        coEvery { ghost_alias_api.expire_ghost_alias(any()) } throws
            ApiError.ForbiddenError(reason, "FORBIDDEN")

        assertEquals(SettingsViewModel.GhostExpireOutcome.Cooldown(reason), vm.expire_ghost_alias_now("g1"))
        coVerify(exactly = 0) { ghost_alias_api.list_ghost_aliases() }
    }

    @Test
    fun `expire_ghost_alias_now treats other errors as failures`() = runTest {
        every { context.getString(R.string.ghost_action_failed) } returns "Couldn't update ghost alias"
        coEvery { ghost_alias_api.expire_ghost_alias(any()) } throws RuntimeException("boom")

        assertEquals(SettingsViewModel.GhostExpireOutcome.Failed("Couldn't update ghost alias"), vm.expire_ghost_alias_now("g1"))
    }

    @Test
    fun `extend_ghost_alias_now falls back to the ghost failure text`() = runTest {
        every { context.getString(R.string.ghost_action_failed) } returns "Couldn't update ghost alias"
        coEvery { ghost_alias_api.extend_ghost_alias(any()) } throws RuntimeException("boom")

        assertEquals("Couldn't update ghost alias", vm.extend_ghost_alias_now("g1"))
    }

    @Test
    fun `load_forwarding_rules error sets error message`() = runTest {
        coEvery { auto_forward_api.list_rules() } throws ApiError.UnknownError("rules error")

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
        coEvery { auto_forward_api.create_rule(any()) } throws ApiError.UnknownError("invalid target")

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
        coEvery { labels_api.list_labels(any(), any()) } throws ApiError.UnknownError("labels error")

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

        vm.create_label(CreateLabelRequest(
            label_token = "lt_new", encrypted_name = "enc", name_nonce = "n",
        ))
        advanceUntilIdle()

        coVerify { labels_api.create_label(any()) }
        coVerify(exactly = 0) { labels_api.list_labels(any(), any()) }
    }

    @Test
    fun `create_label error does not reload`() = runTest {
        coEvery { labels_api.create_label(any()) } throws RuntimeException("error")

        vm.create_label(CreateLabelRequest(
            label_token = "lt_new", encrypted_name = "enc", name_nonce = "n",
        ))
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.list_labels(any(), any()) }
    }

    @Test
    fun `create_folder sends parent_token and sort_order and encrypts the name`() = runTest {
        val request_slot = slot<CreateLabelRequest>()
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
        coEvery { labels_api.delete_label(any(), any()) } returns DeleteLabelResponse(status = "deleted")

        vm.create_folder(name = "Receipts")
        advanceUntilIdle()

        val created = vm.state.value.labels.single()
        assertEquals("srv_id", created.id)

        vm.delete_label(created.id)
        advanceUntilIdle()

        coVerify { labels_api.delete_label("srv_id", null) }
        assertTrue(vm.state.value.labels.isEmpty())
    }

    @Test
    fun `delete_label failure reports an action result`() = runTest {
        coEvery { labels_api.list_labels(any(), any()) } returns
            LabelsListResponse(listOf(LabelItem(id = "l1", label_token = "lt1", encrypted_name = "Alpha")))
        vm.load_labels()
        advanceUntilIdle()
        coEvery { labels_api.delete_label("l1", null) } throws
            ApiError.UnknownError("folder is locked")

        vm.delete_label("l1")
        advanceUntilIdle()

        assertEquals("folder is locked", vm.state.value.action_result)
        assertEquals(1, vm.state.value.labels.size)
    }

    private suspend fun TestScope.seed_folders() {
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
            slot<BulkReorderLabelsRequest>()
        coEvery { labels_api.bulk_reorder_labels(capture(request_slot)) } returns
            BulkReorderLabelsResponse(updated = 2)

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

    private suspend fun TestScope.seed_named_folders(labels: List<LabelItem>) {
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(labels)
        every { session_key_store.get_identity_key() } returns "test-identity-key"
        vm.load_labels()
        advanceUntilIdle()
    }

    private suspend fun TestScope.seed_unsorted_folders() {
        seed_named_folders(
            listOf(
                LabelItem(id = "z", label_token = "ltz", encrypted_name = "Zulu", sort_order = 0),
                LabelItem(id = "a", label_token = "lta", encrypted_name = "alpha", sort_order = 1),
                LabelItem(id = "m", label_token = "ltm", encrypted_name = "Mike", sort_order = 2),
                LabelItem(id = "c10", label_token = "ltc10", encrypted_name = "Child 10", sort_order = 0, parent_token = "lta"),
                LabelItem(id = "c2", label_token = "ltc2", encrypted_name = "Child 2", sort_order = 1, parent_token = "lta"),
                LabelItem(id = "t", label_token = "ltt", encrypted_name = "Aaa", folder_type = "label", sort_order = 4),
            ),
        )
    }

    private suspend fun TestScope.seed_sorted_folders() {
        seed_named_folders(
            listOf(
                LabelItem(id = "l1", label_token = "lt1", encrypted_name = "Alpha", sort_order = 0),
                LabelItem(id = "l2", label_token = "lt2", encrypted_name = "Beta", sort_order = 1),
                LabelItem(id = "c1", label_token = "ltc1", encrypted_name = "Child", sort_order = 0, parent_token = "lt1"),
            ),
        )
    }

    @Test
    fun `sort_folders_a_z sends only changed entries in one request`() = runTest {
        seed_unsorted_folders()
        every { context.getString(R.string.folders_sorted_a_to_z) } returns
            "Folders sorted A to Z"
        val request_slot =
            slot<BulkReorderLabelsRequest>()
        coEvery { labels_api.bulk_reorder_labels(capture(request_slot)) } returns
            BulkReorderLabelsResponse(updated = 5)

        vm.sort_folders_a_z()
        advanceUntilIdle()

        coVerify(exactly = 1) { labels_api.bulk_reorder_labels(any()) }
        assertEquals(
            mapOf("a" to 0, "m" to 1, "z" to 2, "c2" to 0, "c10" to 1),
            request_slot.captured.labels.associate { it.id to it.sort_order },
        )
        val by_id = vm.state.value.labels.associateBy { it.id }
        assertEquals(0, by_id["a"]?.sort_order)
        assertEquals(2, by_id["z"]?.sort_order)
        assertEquals(4, by_id["t"]?.sort_order)
        assertEquals("Folders sorted A to Z", vm.state.value.action_result)
    }

    @Test
    fun `sort_folders_a_z restores previous orders when the api call fails`() = runTest {
        seed_unsorted_folders()
        coEvery { labels_api.bulk_reorder_labels(any()) } throws RuntimeException("offline")

        vm.sort_folders_a_z()
        advanceUntilIdle()

        val orders = vm.state.value.labels.associate { it.id to it.sort_order }
        assertEquals(mapOf("z" to 0, "a" to 1, "m" to 2, "c10" to 0, "c2" to 1, "t" to 4), orders)
        assertEquals("Something went wrong", vm.state.value.action_result)
    }

    @Test
    fun `sort_folders_a_z does nothing when folders are already sorted`() = runTest {
        seed_sorted_folders()

        vm.sort_folders_a_z()
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.bulk_reorder_labels(any()) }
    }

    @Test
    fun `create_folder without sort_order inserts alphabetically into a sorted group`() = runTest {
        seed_sorted_folders()
        val create_slot = slot<CreateLabelRequest>()
        coEvery { labels_api.create_label(capture(create_slot)) } returns
            CreateLabelResponse(id = "srv", label_token = "srv_tok", success = true)
        val reorder_slot =
            slot<BulkReorderLabelsRequest>()
        coEvery { labels_api.bulk_reorder_labels(capture(reorder_slot)) } returns
            BulkReorderLabelsResponse(updated = 1)

        vm.create_folder(name = "Amber")
        advanceUntilIdle()

        assertEquals(1, create_slot.captured.sort_order)
        assertEquals(mapOf("l2" to 2), reorder_slot.captured.labels.associate { it.id to it.sort_order })
        val by_id = vm.state.value.labels.associateBy { it.id }
        assertEquals(2, by_id["l2"]?.sort_order)
        assertEquals(1, by_id["srv"]?.sort_order)
        assertEquals(0, by_id["l1"]?.sort_order)
    }

    @Test
    fun `create_folder without sort_order appends to an unsorted group`() = runTest {
        seed_unsorted_folders()
        val create_slot = slot<CreateLabelRequest>()
        coEvery { labels_api.create_label(capture(create_slot)) } returns
            CreateLabelResponse(id = "srv", label_token = "srv_tok", success = true)

        vm.create_folder(name = "Bravo")
        advanceUntilIdle()

        assertEquals(3, create_slot.captured.sort_order)
        coVerify(exactly = 0) { labels_api.bulk_reorder_labels(any()) }
    }

    @Test
    fun `rename_folder re-sorts a sorted group`() = runTest {
        seed_sorted_folders()
        val reorder_slot =
            slot<BulkReorderLabelsRequest>()
        coEvery { labels_api.bulk_reorder_labels(capture(reorder_slot)) } returns
            BulkReorderLabelsResponse(updated = 2)

        vm.rename_folder("l1", "Zed")
        advanceUntilIdle()

        assertEquals(mapOf("l1" to 1, "l2" to 0), reorder_slot.captured.labels.associate { it.id to it.sort_order })
        val by_id = vm.state.value.labels.associateBy { it.id }
        assertEquals("Zed", by_id["l1"]?.encrypted_name)
        assertEquals(1, by_id["l1"]?.sort_order)
    }

    @Test
    fun `rename_folder leaves an unsorted group alone`() = runTest {
        seed_unsorted_folders()

        vm.rename_folder("z", "Aaron")
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.bulk_reorder_labels(any()) }
    }

    @Test
    fun `set_folder_parent places the folder alphabetically among its new siblings`() = runTest {
        seed_sorted_folders()
        val reorder_slot =
            slot<BulkReorderLabelsRequest>()
        coEvery { labels_api.bulk_reorder_labels(capture(reorder_slot)) } returns
            BulkReorderLabelsResponse(updated = 2)

        vm.set_folder_parent("l2", "lt1")
        advanceUntilIdle()

        assertEquals(mapOf("l2" to 0, "c1" to 1), reorder_slot.captured.labels.associate { it.id to it.sort_order })
    }

    private suspend fun TestScope.seed_label_rows() {
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
        val request_slot = slot<BulkReorderLabelsRequest>()
        coEvery { labels_api.bulk_reorder_labels(capture(request_slot)) } returns
            BulkReorderLabelsResponse(updated = 2)

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
            BulkReorderLabelsResponse(updated = 2)

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

    private suspend fun TestScope.seed_tag_rows() {
        val tags = listOf(
            TagItem(
                id = "t2", tag_token = "tk2", encrypted_name = "Beta", name_nonce = "", sort_order = 1,
            ),
            TagItem(
                id = "t1", tag_token = "tk1", encrypted_name = "Alpha", name_nonce = "", sort_order = 0,
            ),
        )
        coEvery { tags_api.list_tags(any()) } returns
            TagsListResponse(tags = tags)
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_previous_keys() } returns null
        vm.load_tags()
        advanceUntilIdle()
    }

    @Test
    fun `move_tag persists a single bulk reorder request`() = runTest {
        seed_tag_rows()
        val request_slot = slot<BulkReorderTagsRequest>()
        coEvery { tags_api.bulk_reorder_tags(capture(request_slot)) } returns
            BulkReorderTagsResponse(updated = 2)

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
        coEvery { labels_api.delete_label("l1", null) } throws RuntimeException("error")

        vm.load_labels()
        advanceUntilIdle()

        vm.delete_label("l1")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.labels.size)
    }

    @Test
    fun `load_preferences keeps prefs when encrypted blob present but identity key missing`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            EncryptedPreferencesResponse()
        coEvery { preferences_api.get_preferences() } returns
            UserPreferences(conversation_grouping = false, swipe_right_action = "star")
        vm.load_preferences(force = true)
        advanceUntilIdle()
        assertEquals(false, vm.state.value.preferences?.conversation_grouping)
        assertEquals("star", vm.state.value.preferences?.swipe_right_action)

        coEvery { preferences_api.get_encrypted_preferences() } returns
            EncryptedPreferencesResponse(
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
            EncryptedPreferencesResponse(
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
            recovery_api = recovery_api,
            mail_rules_api = mail_rules_api,
            keys_api = keys_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        ).also {
            it.default_dispatcher = dispatcher
            it.io_dispatcher = dispatcher
        }
        advanceUntilIdle()

        assertNotNull(fresh_vm.state.value.preferences)
        assertFalse(fresh_vm.state.value.is_loading)
    }

    @Test
    fun `save_preferences never overwrites a server blob this device cannot decrypt`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            EncryptedPreferencesResponse(
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
            recovery_api = recovery_api,
            mail_rules_api = mail_rules_api,
            keys_api = keys_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        ).also {
            it.default_dispatcher = dispatcher
            it.io_dispatcher = dispatcher
        }
        advanceUntilIdle()

        io.mockk.clearMocks(preferences_cache, answers = false, recordedCalls = true)

        fresh_vm.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, fresh_vm.state.value.save_status)
        assertTrue(fresh_vm.state.value.preferences_locked)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        verify(exactly = 0) { preferences_cache.write(any(), any()) }
    }

    private fun test_prefs_key(identity_key: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest((identity_key + "astermail-preferences-v1").toByteArray(Charsets.UTF_8))

    private fun encrypt_test_blob(json_str: String, identity_key: String): Pair<String, String> {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(test_prefs_key(identity_key), "AES"),
            GCMParameterSpec(128, nonce),
        )
        val ct = cipher.doFinal(json_str.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(ct) to
            Base64.getEncoder().encodeToString(nonce)
    }

    private fun decrypt_test_blob(encrypted_b64: String, nonce_b64: String, identity_key: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(test_prefs_key(identity_key), "AES"),
            GCMParameterSpec(128, Base64.getDecoder().decode(nonce_b64)),
        )
        return String(cipher.doFinal(Base64.getDecoder().decode(encrypted_b64)), Charsets.UTF_8)
    }

    @Test
    fun `save after failed decrypt rebases onto fresh server blob instead of writing defaults`() = runTest {
        val identity_key = "user_identity_key"
        val server_json = """{"theme":"dark","haptic_enabled":false,"web_only_setting":"keep_me"}"""
        val (blob, nonce) = encrypt_test_blob(server_json, identity_key)
        coEvery { preferences_api.get_encrypted_preferences() } returns
            EncryptedPreferencesResponse(
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
            recovery_api = recovery_api,
            mail_rules_api = mail_rules_api,
            keys_api = keys_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        ).also {
            it.default_dispatcher = dispatcher
            it.io_dispatcher = dispatcher
        }
        advanceUntilIdle()

        every { session_key_store.get_identity_key() } returns identity_key
        val request = slot<SaveEncryptedPreferencesRequest>()
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

    private val account_prefs_key = ByteArray(32) { (it * 7 + 3).toByte() }

    private fun new_vm() = SettingsViewModel(
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
        recovery_api = recovery_api,
        mail_rules_api = mail_rules_api,
        keys_api = keys_api,
        auth_repository = auth_repository,
        session_key_store = session_key_store,
        token_store = token_store,
        account_store = account_store,
        preferences_cache = preferences_cache,
        theme_store = theme_store,
        context = context,
    ).also {
        it.default_dispatcher = dispatcher
        it.io_dispatcher = dispatcher
    }

    private fun aes_gcm(mode: Int, key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            mode,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, nonce),
        )
        return cipher.doFinal(input)
    }

    private fun encrypt_with_raw_key(json_str: String, key: ByteArray): Pair<String, String> {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ct = aes_gcm(Cipher.ENCRYPT_MODE, key, nonce, json_str.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(ct) to
            Base64.getEncoder().encodeToString(nonce)
    }

    private fun decrypt_with_raw_key(encrypted_b64: String, nonce_b64: String, key: ByteArray): String =
        String(
            aes_gcm(
                Cipher.DECRYPT_MODE,
                key,
                Base64.getDecoder().decode(nonce_b64),
                Base64.getDecoder().decode(encrypted_b64),
            ),
            Charsets.UTF_8,
        )

    private fun server_blob(blob: Pair<String, String>) {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            EncryptedPreferencesResponse(
                encrypted_preferences = blob.first,
                preferences_nonce = blob.second,
            )
    }

    private fun account_write_key_available(format_writes: Boolean) {
        every { session_key_store.has_account_write_kek("astermail-preferences-v1") } returns true
        every { session_key_store.get_account_write_kek("astermail-preferences-v1") } answers {
            account_prefs_key.copyOf()
        }
        coEvery { keys_api.get_account_key_format_writes() } returns format_writes
    }

    private fun TestScope.save_and_capture(
        target: SettingsViewModel,
    ): SaveEncryptedPreferencesRequest {
        val request = slot<SaveEncryptedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences(capture(request)) } returns Unit
        target.save_preferences(target.state.value.preferences!!.copy(show_aster_branding = false))
        advanceUntilIdle()
        assertEquals(SaveStatus.SAVED, target.state.value.save_status)
        return request.captured
    }

    @Test
    fun `preferences under a public constant stay readable but only the edited field is saved`() = runTest {
        val identity_key = "user_identity_key"
        every { session_key_store.get_identity_key() } returns identity_key
        every { session_key_store.get_previous_keys() } returns emptyList()
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        val constant = org.astermail.android.crypto.StorageWriteKey.public_mail_kek_raws().first()
        server_blob(encrypt_with_raw_key("""{"theme":"dark","web_only_setting":"from_constant"}""", constant))
        val target = new_vm()
        advanceUntilIdle()

        assertEquals("dark", target.state.value.preferences?.theme)

        val request = save_and_capture(target)

        val written = decrypt_test_blob(request.encrypted_preferences, request.preferences_nonce, identity_key)
        assertTrue(written.contains("\"show_aster_branding\":false"))
        assertFalse(
            "values that opened under a public constant must not be saved under the account key",
            written.contains("\"theme\":\"dark\"") || written.contains("from_constant"),
        )
    }

    @Test
    fun `preferences under a placeholder previous key are not saved again unedited`() = runTest {
        val identity_key = "user_identity_key"
        every { session_key_store.get_identity_key() } returns identity_key
        every { session_key_store.get_previous_keys() } returns listOf("undefined")
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        server_blob(encrypt_test_blob("""{"theme":"dark","web_only_setting":"from_placeholder"}""", "undefined"))
        val target = new_vm()
        advanceUntilIdle()

        assertEquals("dark", target.state.value.preferences?.theme)

        val request = save_and_capture(target)

        val written = decrypt_test_blob(request.encrypted_preferences, request.preferences_nonce, identity_key)
        assertTrue(written.contains("\"show_aster_branding\":false"))
        assertFalse(
            "values that opened under a placeholder key must not be saved under the account key",
            written.contains("\"theme\":\"dark\"") || written.contains("from_placeholder"),
        )
    }

    @Test
    fun `save writes preferences with the account key when the flag is on`() = runTest {
        val identity_key = "user_identity_key"
        every { session_key_store.get_identity_key() } returns identity_key
        server_blob(encrypt_test_blob("""{"web_only_setting":"keep_me"}""", identity_key))
        account_write_key_available(format_writes = true)
        val target = new_vm()
        advanceUntilIdle()

        val request = save_and_capture(target)

        val written = decrypt_with_raw_key(request.encrypted_preferences, request.preferences_nonce, account_prefs_key)
        assertTrue(written.contains("\"show_aster_branding\":false"))
        assertTrue(written.contains("\"web_only_setting\":\"keep_me\""))
        assertTrue(
            runCatching {
                decrypt_test_blob(request.encrypted_preferences, request.preferences_nonce, identity_key)
            }.isFailure,
        )
    }

    @Test
    fun `save keeps the legacy key when the flag is off`() = runTest {
        val identity_key = "user_identity_key"
        every { session_key_store.get_identity_key() } returns identity_key
        server_blob(encrypt_test_blob("""{"web_only_setting":"keep_me"}""", identity_key))
        account_write_key_available(format_writes = false)
        val target = new_vm()
        advanceUntilIdle()

        val request = save_and_capture(target)

        val written = decrypt_test_blob(request.encrypted_preferences, request.preferences_nonce, identity_key)
        assertTrue(written.contains("\"show_aster_branding\":false"))
    }

    @Test
    fun `save keeps the legacy key and skips the flag without an account write key`() = runTest {
        val identity_key = "user_identity_key"
        every { session_key_store.get_identity_key() } returns identity_key
        server_blob(encrypt_test_blob("""{"web_only_setting":"keep_me"}""", identity_key))
        coEvery { keys_api.get_account_key_format_writes() } returns true
        val target = new_vm()
        advanceUntilIdle()

        val request = save_and_capture(target)

        decrypt_test_blob(request.encrypted_preferences, request.preferences_nonce, identity_key)
        coVerify(exactly = 0) { keys_api.get_account_key_format_writes() }
    }

    @Test
    fun `save keeps the legacy key when the capability request throws`() = runTest {
        val identity_key = "user_identity_key"
        every { session_key_store.get_identity_key() } returns identity_key
        server_blob(encrypt_test_blob("""{"web_only_setting":"keep_me"}""", identity_key))
        account_write_key_available(format_writes = true)
        coEvery { keys_api.get_account_key_format_writes() } throws RuntimeException("offline")
        val target = new_vm()
        advanceUntilIdle()

        val request = save_and_capture(target)

        decrypt_test_blob(request.encrypted_preferences, request.preferences_nonce, identity_key)
    }

    @Test
    fun `load reads preferences written with the account key`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns
            listOf(Base64.getEncoder().encodeToString(account_prefs_key))
        server_blob(encrypt_with_raw_key("""{"show_aster_branding":false}""", account_prefs_key))
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(false, target.state.value.preferences?.show_aster_branding)
        assertTrue(target.state.value.preferences_authoritative)
    }

    @Test
    fun `load reads preferences written under a previous identity key`() = runTest {
        every { session_key_store.get_identity_key() } returns "relocked_identity_key"
        every { session_key_store.get_previous_keys() } returns listOf("old_identity_key")
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        server_blob(encrypt_test_blob("""{"show_aster_branding":false}""", "old_identity_key"))
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(false, target.state.value.preferences?.show_aster_branding)
    }

    private fun public_constant(context: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(("undefined" + context).toByteArray(Charsets.UTF_8))

    @Test
    fun `load still reads preferences sealed under a public constant`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        server_blob(encrypt_with_raw_key("""{"show_aster_branding":false}""", public_constant("astermail-envelope-v1")))
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(false, target.state.value.preferences?.show_aster_branding)
    }

    @Test
    fun `load still reads preferences sealed under a preferences context constant`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        server_blob(encrypt_with_raw_key("""{"show_aster_branding":false}""", public_constant("astermail-preferences-v1")))
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(false, target.state.value.preferences?.show_aster_branding)
    }

    private fun lost_key_blob(): Pair<String, String> =
        encrypt_with_raw_key("""{"show_aster_branding":false}""", ByteArray(32) { 99 })

    private fun lost_key_field(): Pair<String, String> {
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(ByteArray(40) { (it + 3).toByte() }) to
            encoder.encodeToString(ByteArray(12) { (it + 5).toByte() })
    }

    private fun cached_copy() {
        every { preferences_cache.read("user_1") } returns
            Json { encodeDefaults = true }.encodeToString(
                UserPreferences.serializer(),
                UserPreferences(show_aster_branding = false),
            )
    }

    private fun vault_ready(ready: Boolean) {
        every { session_key_store.account_key_load_pending() } returns false
        every { session_key_store.account_keys_ready() } returns ready
        every { session_key_store.get_data_kek() } returns ByteArray(32) { 1 }
        every { session_key_store.get_user_id() } returns "user_1"
        every { preferences_cache.archive_unreadable(any(), any(), any(), any(), any(), any(), any()) } returns true
    }

    @Test
    fun `save archives a well formed blob that no key opens then saves`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        vault_ready(true)
        val blob = lost_key_blob()
        cached_copy()
        server_blob(blob)
        coEvery { preferences_api.save_encrypted_preferences(any()) } returns Unit
        val target = new_vm()
        advanceUntilIdle()

        assertFalse(target.state.value.preferences_locked)

        target.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, target.state.value.save_status)
        coVerifyOrder {
            preferences_cache.archive_unreadable(any(), "preferences", "${blob.first}.${blob.second}", any(), any(), any(), any())
            preferences_api.save_encrypted_preferences(any())
        }
    }

    @Test
    fun `save refuses after an unconfirmed load even with account keys loaded`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        vault_ready(true)
        every { preferences_cache.read(any()) } returns null
        server_blob(lost_key_blob())
        coEvery { preferences_api.save_encrypted_preferences(any()) } returns Unit
        val target = new_vm()
        advanceUntilIdle()
        assertTrue(target.state.value.preferences_locked)

        target.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, target.state.value.save_status)
        assertTrue(target.state.value.preferences_locked)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        coVerify(exactly = 0) { preferences_cache.archive_unreadable(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `save refuses after an unconfirmed load when the server copy comes back empty`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        vault_ready(true)
        every { preferences_cache.read(any()) } returns null
        server_blob(lost_key_blob())
        coEvery { preferences_api.save_encrypted_preferences(any()) } returns Unit
        val target = new_vm()
        advanceUntilIdle()
        assertTrue(target.state.value.preferences_locked)

        coEvery { preferences_api.get_encrypted_preferences() } returns EncryptedPreferencesResponse()
        target.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, target.state.value.save_status)
        assertTrue(target.state.value.preferences_locked)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
    }

    @Test
    fun `save after an unconfirmed load keeps server values for fields the user left alone`() = runTest {
        val identity_key = "user_identity_key"
        every { session_key_store.get_identity_key() } returns null
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        every { preferences_cache.read(any()) } returns
            Json { encodeDefaults = true }.encodeToString(
                UserPreferences.serializer(),
                UserPreferences(show_aster_branding = false),
            )
        server_blob(encrypt_test_blob("""{"show_aster_branding":true,"web_only_setting":"keep_me"}""", identity_key))
        val target = new_vm()
        advanceUntilIdle()
        assertTrue(target.state.value.preferences_locked)
        assertEquals(false, target.state.value.preferences?.show_aster_branding)

        every { session_key_store.get_identity_key() } returns identity_key
        val request = slot<SaveEncryptedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences(capture(request)) } returns Unit
        val shown = target.state.value.preferences!!
        target.save_preferences(shown.copy(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, target.state.value.save_status)
        val written = decrypt_test_blob(request.captured.encrypted_preferences, request.captured.preferences_nonce, identity_key)
        assertTrue(written, written.contains("\"load_remote_images\":\"always\""))
        assertTrue(written, written.contains("\"web_only_setting\":\"keep_me\""))
        assertFalse(written, written.contains("\"show_aster_branding\":false"))
        assertEquals(true, target.state.value.preferences?.show_aster_branding)
    }

    private fun TestScope.locked_vm_showing_a_cached_copy(identity_key: String): SettingsViewModel {
        every { session_key_store.get_identity_key() } returns null
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        every { preferences_cache.read(any()) } returns
            Json { encodeDefaults = true }.encodeToString(
                UserPreferences.serializer(),
                UserPreferences(show_aster_branding = false),
            )
        server_blob(encrypt_test_blob("""{"show_aster_branding":true,"web_only_setting":"keep_me"}""", identity_key))
        val target = new_vm()
        advanceUntilIdle()
        assertTrue(target.state.value.preferences_locked)
        return target
    }

    @Test
    fun `retry after a save that failed without keys keeps the edit`() = runTest {
        val identity_key = "user_identity_key"
        val target = locked_vm_showing_a_cached_copy(identity_key)
        val request = slot<SaveEncryptedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences(capture(request)) } returns Unit

        target.save_preferences(target.state.value.preferences!!.copy(load_remote_images = "always"))
        advanceUntilIdle()
        assertEquals(SaveStatus.ERROR, target.state.value.save_status)

        every { session_key_store.get_identity_key() } returns identity_key
        target.retry_preferences_save()
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, target.state.value.save_status)
        val written = decrypt_test_blob(request.captured.encrypted_preferences, request.captured.preferences_nonce, identity_key)
        assertTrue(written, written.contains("\"load_remote_images\":\"always\""))
        assertFalse(written, written.contains("\"show_aster_branding\":false"))
        assertEquals("always", target.state.value.preferences?.load_remote_images)
    }

    @Test
    fun `a second save that replaces an unconfirmed first save keeps both edits`() = runTest {
        val identity_key = "user_identity_key"
        val target = locked_vm_showing_a_cached_copy(identity_key)
        val request = slot<SaveEncryptedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences(capture(request)) } returns Unit

        val first = target.state.value.preferences!!.copy(load_remote_images = "always")
        target.save_preferences(first)
        every { session_key_store.get_identity_key() } returns identity_key
        target.save_preferences(target.state.value.preferences!!.copy(compact_mode = true))
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, target.state.value.save_status)
        val written = decrypt_test_blob(request.captured.encrypted_preferences, request.captured.preferences_nonce, identity_key)
        assertTrue(written, written.contains("\"load_remote_images\":\"always\""))
        assertTrue(written, written.contains("\"compact_mode\":true"))
        assertFalse(written, written.contains("\"show_aster_branding\":false"))
    }

    @Test
    fun `save refuses when the key load ended without keys`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        vault_ready(false)
        cached_copy()
        server_blob(lost_key_blob())
        val target = new_vm()
        advanceUntilIdle()

        target.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, target.state.value.save_status)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        coVerify(exactly = 0) { preferences_cache.archive_unreadable(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `save refuses when the archive cannot be written`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        vault_ready(true)
        every { preferences_cache.archive_unreadable(any(), any(), any(), any(), any(), any(), any()) } returns false
        cached_copy()
        server_blob(lost_key_blob())
        val target = new_vm()
        advanceUntilIdle()

        target.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, target.state.value.save_status)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
    }

    private suspend fun TestScope.seed_lost_key_tag(): Pair<String, String> {
        val field = lost_key_field()
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_previous_keys() } returns null
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        coEvery { tags_api.list_tags(any()) } returns TagsListResponse(
            tags = listOf(
                TagItem(
                    id = "t1",
                    tag_token = "tk1",
                    encrypted_name = field.first,
                    name_nonce = field.second,
                    encrypted_color = field.first,
                    color_nonce = field.second,
                ),
            ),
        )
        vm.load_tags()
        advanceUntilIdle()
        return field
    }

    @Test
    fun `tag rename archives an unreadable name before writing`() = runTest {
        vault_ready(true)
        val field = seed_lost_key_tag()

        vm.rename_tag("t1", "Fresh")
        advanceUntilIdle()

        coVerifyOrder {
            preferences_cache.archive_unreadable(any(), "tag", "${field.first}.${field.second}", "t1:name", any(), any(), any())
            tags_api.update_tag("t1", any())
        }
    }

    @Test
    fun `tag rename and recolor refuse when the key load ended without keys`() = runTest {
        vault_ready(false)
        seed_lost_key_tag()

        vm.rename_tag("t1", "Fresh")
        vm.recolor_tag("t1", "#ff0000")
        advanceUntilIdle()

        coVerify(exactly = 0) { tags_api.update_tag(any(), any()) }
        coVerify(exactly = 0) { preferences_cache.archive_unreadable(any(), any(), any(), any(), any(), any(), any()) }
    }

    private suspend fun TestScope.seed_lost_key_label(): Pair<String, String> {
        val field = lost_key_field()
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_previous_keys() } returns null
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(
            listOf(
                LabelItem(
                    id = "l1",
                    label_token = "lt1",
                    encrypted_name = field.first,
                    name_nonce = field.second,
                    folder_type = "folder",
                ),
            ),
        )
        vm.load_labels()
        advanceUntilIdle()
        return field
    }

    @Test
    fun `folder rename archives an unreadable name before writing`() = runTest {
        vault_ready(true)
        val field = seed_lost_key_label()

        vm.rename_folder("l1", "Fresh")
        advanceUntilIdle()

        coVerifyOrder {
            preferences_cache.archive_unreadable(any(), "label", "${field.first}.${field.second}", "l1:name", any(), any(), any())
            labels_api.update_label("l1", any())
        }
    }

    @Test
    fun `folder rename refuses when the key load ended without keys`() = runTest {
        vault_ready(false)
        seed_lost_key_label()

        vm.rename_folder("l1", "Fresh")
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.update_label(any(), any()) }
    }

    private fun lost_key_recovery_state() {
        val field = lost_key_field()
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_previous_keys() } returns null
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        coEvery { recovery_email_api.get_state() } returns
            RecoveryEmailStateResponse(
                encrypted_email = field.first,
                email_nonce = field.second,
                exists = true,
            )
    }

    @Test
    fun `recovery email save archives an unreadable address before writing`() = runTest {
        vault_ready(true)
        lost_key_recovery_state()

        vm.save_recovery_email("new@example.com", null, null) { "" }
        advanceUntilIdle()

        coVerifyOrder {
            preferences_cache.archive_unreadable(any(), "recovery_email", any(), any(), any(), any(), any())
            recovery_email_api.save(any())
        }
    }

    @Test
    fun `recovery email save refuses when the key load ended without keys`() = runTest {
        vault_ready(false)
        lost_key_recovery_state()

        vm.save_recovery_email("new@example.com", null, null) { "" }
        advanceUntilIdle()

        coVerify(exactly = 0) { recovery_email_api.save(any()) }
    }

    @Test
    fun `save that starts before the key load finishes does not replace an unread blob`() = runTest {
        var pending = true
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        vault_ready(false)
        every { session_key_store.account_key_load_pending() } answers { pending }
        coEvery { session_key_store.await_account_key_load(any()) } answers { pending = false }
        server_blob(lost_key_blob())
        val target = new_vm()
        advanceUntilIdle()
        pending = true

        target.save_preferences(UserPreferences(load_remote_images = "always"))
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, target.state.value.save_status)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        coVerify(exactly = 0) { preferences_cache.archive_unreadable(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `tag rename reports the locked edit message`() = runTest {
        every { context.getString(R.string.encrypted_edit_locked_retry) } returns "locked_edit"
        vault_ready(false)
        seed_lost_key_tag()

        vm.rename_tag("t1", "Fresh")
        advanceUntilIdle()

        assertEquals("locked_edit", vm.state.value.action_result)
    }

    private fun archived_preferences(json: String, key: ByteArray): ArchivedBlob {
        val sealed = encrypt_with_raw_key(json, key)
        return ArchivedBlob("archive_k1", "preferences", "", "${sealed.first}.${sealed.second}", 5L)
    }

    private fun versioned_server(
        blob: Pair<String, String>,
        result: PreferencesSaveResult = PreferencesSaveResult.SAVED,
    ): MutableList<SaveVersionedPreferencesRequest> {
        server_blob(blob)
        coEvery { preferences_api.get_versioned_encrypted_preferences() } returns
            VersionedEncryptedPreferences(blob.first, blob.second, 3)
        val saved = mutableListOf<SaveVersionedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences_if_version(capture(saved)) } returns result
        return saved
    }

    private fun restore_setup(
        archive: ArchivedBlob,
        server_json: String = """{"load_remote_images":"always"}""",
        result: PreferencesSaveResult = PreferencesSaveResult.SAVED,
    ): MutableList<SaveVersionedPreferencesRequest> {
        val identity_key = "user_identity_key"
        every { session_key_store.get_identity_key() } returns identity_key
        every { session_key_store.get_decrypt_keks() } returns
            listOf(Base64.getEncoder().encodeToString(account_prefs_key))
        every { preferences_cache.list_archived(any(), any()) } returns emptyList()
        every { preferences_cache.list_archived("user_1", "preferences") } returns listOf(archive)
        every { preferences_cache.drop_archived(any()) } returns true
        return versioned_server(encrypt_test_blob(server_json, identity_key), result)
    }

    private fun baseline_target(json: String): String =
        Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

    @Test
    fun `load restores an archived blob that opens after the key load`() = runTest {
        vault_ready(true)
        val saved = restore_setup(
            archived_preferences("""{"show_aster_branding":false,"load_remote_images":"never"}""", account_prefs_key),
        )
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(false, target.state.value.preferences?.show_aster_branding)
        assertEquals("always", target.state.value.preferences?.load_remote_images)
        assertEquals(1, saved.size)
        assertEquals(3, saved[0].expected_version)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        val written = decrypt_test_blob(saved[0].encrypted_preferences, saved[0].preferences_nonce, "user_identity_key")
        assertTrue(written.contains("\"show_aster_branding\":false"))
        assertTrue(written.contains("\"load_remote_images\":\"always\""))
        verify(exactly = 1) { preferences_cache.drop_archived("archive_k1") }
    }

    @Test
    fun `load keeps an archived blob that still does not open`() = runTest {
        vault_ready(true)
        restore_setup(archived_preferences("""{"show_aster_branding":false}""", ByteArray(32) { 99 }))
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(true, target.state.value.preferences?.show_aster_branding)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        verify(exactly = 0) { preferences_cache.drop_archived(any()) }
    }

    @Test
    fun `load leaves the archive alone when the key load ended without keys`() = runTest {
        vault_ready(false)
        restore_setup(archived_preferences("""{"show_aster_branding":false}""", account_prefs_key))
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(true, target.state.value.preferences?.show_aster_branding)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        verify(exactly = 0) { preferences_cache.drop_archived(any()) }
    }

    @Test
    fun `load drops a folder name archive once it opens`() = runTest {
        vault_ready(true)
        restore_setup(archived_preferences("""{"show_aster_branding":false}""", ByteArray(32) { 99 }))
        val label_key = MessageDigest.getInstance("SHA-256")
            .digest("user_identity_keyastermail-labels-v1".toByteArray(Charsets.UTF_8))
        val sealed = encrypt_with_raw_key("Receipts", label_key)
        val rename = sealed_rename()
        val lost = lost_key_field()
        every { preferences_cache.list_archived("user_1", "label") } returns listOf(
            ArchivedBlob("archive_l1", "label", "l1:name", "${sealed.first}.${sealed.second}", 3L),
            ArchivedBlob("archive_l2", "label", "l2:name", "${lost.first}.${lost.second}", 4L),
        )
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(
            labels = listOf(
                LabelItem(id = "l1", label_token = "lt1", encrypted_name = rename.first, name_nonce = rename.second),
                LabelItem(id = "l2", label_token = "lt2", encrypted_name = lost.first, name_nonce = lost.second),
            ),
        )
        new_vm()
        advanceUntilIdle()

        verify(exactly = 1) { preferences_cache.drop_archived("archive_l1") }
        verify(exactly = 0) { preferences_cache.drop_archived("archive_l2") }
        coVerify(exactly = 0) { labels_api.update_label(any(), any()) }
    }

    private fun label_field_key(): ByteArray = MessageDigest.getInstance("SHA-256")
        .digest("user_identity_keyastermail-labels-v1".toByteArray(Charsets.UTF_8))

    private fun tag_field_key(): ByteArray = MessageDigest.getInstance("SHA-256")
        .digest("user_identity_keyastermail-tags-v1".toByteArray(Charsets.UTF_8))

    private fun sealed_rename(): Pair<String, String> = encrypt_with_raw_key("Bills", label_field_key())

    private fun label_archive_setup(): ArchivedBlob {
        vault_ready(true)
        restore_setup(archived_preferences("""{"show_aster_branding":false}""", ByteArray(32) { 99 }))
        val sealed = encrypt_with_raw_key("Receipts", label_field_key())
        val archive = ArchivedBlob("archive_l1", "label", "l1:name", "${sealed.first}.${sealed.second}", 3L)
        every { preferences_cache.list_archived("user_1", "label") } returns listOf(archive)
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(
            labels = listOf(LabelItem(id = "l1", label_token = "lt1")),
        )
        return archive
    }

    @Test
    fun `load never writes back an archived folder name sealed under a public constant`() = runTest {
        label_archive_setup()
        val constant = org.astermail.android.crypto.StorageWriteKey.public_mail_kek_raws().first()
        val sealed = encrypt_with_raw_key("Forged", constant)
        every { preferences_cache.list_archived("user_1", "label") } returns listOf(
            ArchivedBlob("archive_l1", "label", "l1:name", "${sealed.first}.${sealed.second}", 3L),
        )
        new_vm()
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.update_label(any(), any()) }
    }

    @Test
    fun `load never restores archived preferences sealed under a public constant`() = runTest {
        vault_ready(true)
        val constant = org.astermail.android.crypto.StorageWriteKey.public_mail_kek_raws().first()
        val saved = restore_setup(archived_preferences("""{"show_aster_branding":false}""", constant))
        val target = new_vm()
        advanceUntilIdle()

        assertTrue("archived preferences under a public constant must not be saved", saved.isEmpty())
        assertEquals(true, target.state.value.preferences?.show_aster_branding)
    }

    @Test
    fun `load writes an archived folder name back when the folder has none`() = runTest {
        label_archive_setup()
        val request = slot<UpdateLabelRequest>()
        coEvery { labels_api.update_label("l1", capture(request)) } returns Unit
        new_vm()
        advanceUntilIdle()

        assertTrue(request.isCaptured)
        assertEquals(
            "Receipts",
            decrypt_with_raw_key(request.captured.encrypted_name!!, request.captured.name_nonce!!, label_field_key()),
        )
        assertEquals(null, request.captured.encrypted_color)
        verify(exactly = 1) { preferences_cache.drop_archived("archive_l1") }
    }

    @Test
    fun `load keeps a folder name archive when the write back fails`() = runTest {
        label_archive_setup()
        coEvery { labels_api.update_label(any(), any()) } throws RuntimeException("offline")
        new_vm()
        advanceUntilIdle()

        coVerify(exactly = 1) { labels_api.update_label("l1", any()) }
        verify(exactly = 0) { preferences_cache.drop_archived("archive_l1") }
    }

    @Test
    fun `load keeps a folder name archive while the folder list cannot be fetched`() = runTest {
        label_archive_setup()
        coEvery { labels_api.list_labels(any(), any()) } throws RuntimeException("offline")
        new_vm()
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.update_label(any(), any()) }
        verify(exactly = 0) { preferences_cache.drop_archived("archive_l1") }
    }

    @Test
    fun `load writes an archived tag name back when the tag has none`() = runTest {
        vault_ready(true)
        restore_setup(archived_preferences("""{"show_aster_branding":false}""", ByteArray(32) { 99 }))
        val sealed = encrypt_with_raw_key("Travel", tag_field_key())
        every { preferences_cache.list_archived("user_1", "tag") } returns listOf(
            ArchivedBlob("archive_t1", "tag", "t1:name", "${sealed.first}.${sealed.second}", 3L),
        )
        coEvery { tags_api.list_tags(any()) } returns TagsListResponse(
            tags = listOf(TagItem(id = "t1", tag_token = "tt1", encrypted_name = "", name_nonce = "")),
        )
        val request = slot<UpdateTagRequest>()
        coEvery { tags_api.update_tag("t1", capture(request)) } returns Unit
        new_vm()
        advanceUntilIdle()

        assertTrue(request.isCaptured)
        assertEquals(
            "Travel",
            decrypt_with_raw_key(request.captured.encrypted_name!!, request.captured.name_nonce!!, tag_field_key()),
        )
        verify(exactly = 1) { preferences_cache.drop_archived("archive_t1") }
    }

    @Test
    fun `restore keeps a setting the user reset to its default`() = runTest {
        vault_ready(true)
        val sealed = encrypt_with_raw_key("""{"show_aster_branding":false,"compact_mode":true}""", account_prefs_key)
        val archive = ArchivedBlob(
            "archive_k1",
            "preferences",
            baseline_target("""{"show_aster_branding":false}"""),
            "${sealed.first}.${sealed.second}",
            5L,
        )
        val saved = restore_setup(archive, """{"show_aster_branding":true,"load_remote_images":"always"}""")
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(true, target.state.value.preferences?.show_aster_branding)
        assertEquals(true, target.state.value.preferences?.compact_mode)
        assertEquals(1, saved.size)
        verify(exactly = 1) { preferences_cache.drop_archived("archive_k1") }
    }

    @Test
    fun `restore keeps the archive when the versioned save conflicts`() = runTest {
        vault_ready(true)
        restore_setup(
            archived_preferences("""{"show_aster_branding":false}""", account_prefs_key),
            result = PreferencesSaveResult.CONFLICT,
        )
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(true, target.state.value.preferences?.show_aster_branding)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        verify(exactly = 0) { preferences_cache.drop_archived(any()) }
    }

    @Test
    fun `restore keeps the archive when the save fails`() = runTest {
        vault_ready(true)
        restore_setup(
            archived_preferences("""{"show_aster_branding":false}""", account_prefs_key),
            result = PreferencesSaveResult.FAILED,
        )
        coEvery { preferences_api.save_encrypted_preferences(any()) } throws RuntimeException("offline")
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(true, target.state.value.preferences?.show_aster_branding)
        verify(exactly = 0) { preferences_cache.drop_archived(any()) }
    }

    @Test
    fun `restore leaves a newer edit from another device alone`() = runTest {
        vault_ready(true)
        restore_setup(archived_preferences("""{"show_aster_branding":false}""", account_prefs_key))
        val newer = encrypt_test_blob("""{"load_remote_images":"always","compact_mode":true}""", "user_identity_key")
        coEvery { preferences_api.get_versioned_encrypted_preferences() } returns
            VersionedEncryptedPreferences(newer.first, newer.second, 4)
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(true, target.state.value.preferences?.show_aster_branding)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences_if_version(any()) }
        verify(exactly = 0) { preferences_cache.drop_archived(any()) }
    }

    @Test
    fun `keys that finish after the preferences load still restore the archive`() = runTest {
        val ready_events = MutableStateFlow(0L)
        every { session_key_store.account_keys_ready_events() } returns ready_events
        vault_ready(false)
        val saved = restore_setup(archived_preferences("""{"show_aster_branding":false}""", account_prefs_key))
        val target = new_vm()
        advanceUntilIdle()
        assertEquals(true, target.state.value.preferences?.show_aster_branding)

        every { session_key_store.account_keys_ready() } returns true
        ready_events.value = 1L
        advanceUntilIdle()

        assertEquals(false, target.state.value.preferences?.show_aster_branding)
        assertEquals(1, saved.size)
        verify(exactly = 1) { preferences_cache.drop_archived("archive_k1") }
    }

    @Test
    fun `a key load that finishes before the watcher starts still restores the archive`() = runTest {
        advanceUntilIdle()
        val ready_events = MutableStateFlow(0L)
        every { session_key_store.account_keys_ready_events() } returns ready_events
        vault_ready(true)
        val server_json = """{"load_remote_images":"always"}"""
        val saved = restore_setup(archived_preferences("""{"show_aster_branding":false}""", account_prefs_key), server_json)
        val blob = encrypt_test_blob(server_json, "user_identity_key")
        coEvery { preferences_api.get_encrypted_preferences() } answers {
            ready_events.value = 1L
            EncryptedPreferencesResponse(encrypted_preferences = blob.first, preferences_nonce = blob.second)
        }
        coEvery { preferences_api.get_versioned_encrypted_preferences() } returns null andThen
            VersionedEncryptedPreferences(blob.first, blob.second, 3)
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(false, target.state.value.preferences?.show_aster_branding)
        assertEquals(1, saved.size)
        verify(exactly = 1) { preferences_cache.drop_archived("archive_k1") }
    }

    @Test
    fun `a recovery email archive alone does not reload on every key event`() = runTest {
        advanceUntilIdle()
        val ready_events = MutableStateFlow(0L)
        every { session_key_store.account_keys_ready_events() } returns ready_events
        vault_ready(true)
        restore_setup(archived_preferences("""{"show_aster_branding":false}""", account_prefs_key))
        every { preferences_cache.list_archived("user_1", "preferences") } returns emptyList()
        val lost = lost_key_field()
        every { preferences_cache.list_archived("user_1", "recovery_email") } returns listOf(
            ArchivedBlob("archive_r1", "recovery_email", "", "${lost.first}.${lost.second}", 3L),
        )
        new_vm()
        advanceUntilIdle()
        io.mockk.clearMocks(preferences_api, answers = false, childMocks = false, exclusionRules = false)

        ready_events.value = 1L
        advanceUntilIdle()
        ready_events.value = 2L
        advanceUntilIdle()

        coVerify(exactly = 0) { preferences_api.get_encrypted_preferences() }
        coVerify(exactly = 0) { recovery_email_api.save(any()) }
        verify(exactly = 0) { preferences_cache.drop_archived("archive_r1") }
    }

    private fun locked_key_setup(identity_events: MutableStateFlow<Long>): String {
        val identity_key = "user_identity_key"
        every { session_key_store.identity_key_events() } returns identity_events
        every { context.getString(R.string.preferences_locked_retry) } returns "locked"
        every { session_key_store.get_identity_key() } returns null
        coEvery { auth_repository.try_refresh_vault_keys() } returns false
        val blob = encrypt_test_blob("""{"show_aster_branding":false}""", identity_key)
        coEvery { preferences_api.get_encrypted_preferences() } returns
            EncryptedPreferencesResponse(encrypted_preferences = blob.first, preferences_nonce = blob.second)
        return identity_key
    }

    @Test
    fun `preferences held by a sealed key store load once the identity key arrives`() = runTest {
        advanceUntilIdle()
        vault_ready(true)
        val identity_events = MutableStateFlow(0L)
        val identity_key = locked_key_setup(identity_events)
        every { session_key_store.is_locked() } returns true

        val target = new_vm()
        advanceUntilIdle()

        assertTrue(target.state.value.preferences_locked)
        assertNull(target.state.value.error)

        every { session_key_store.is_locked() } returns false
        every { session_key_store.get_identity_key() } returns identity_key
        identity_events.value = 1L
        advanceUntilIdle()

        assertFalse(target.state.value.preferences_locked)
        assertTrue(target.state.value.preferences_authoritative)
        assertEquals(false, target.state.value.preferences?.show_aster_branding)
        assertNull(target.state.value.error)
    }

    @Test
    fun `preferences locked after the key wait reload when account keys finish`() = runTest {
        advanceUntilIdle()
        vault_ready(true)
        val identity_events = MutableStateFlow(0L)
        val ready_events = MutableStateFlow(0L)
        every { session_key_store.account_keys_ready_events() } returns ready_events
        val identity_key = locked_key_setup(identity_events)

        val target = new_vm()
        advanceUntilIdle()

        assertTrue(target.state.value.preferences_locked)
        assertEquals("locked", target.state.value.error)

        every { session_key_store.get_identity_key() } returns identity_key
        ready_events.value = 1L
        advanceUntilIdle()

        assertFalse(target.state.value.preferences_locked)
        assertTrue(target.state.value.preferences_authoritative)
        assertEquals(false, target.state.value.preferences?.show_aster_branding)
    }

    @Test
    fun `an identity key change does not reload preferences that already loaded`() = runTest {
        advanceUntilIdle()
        vault_ready(true)
        val identity_events = MutableStateFlow(0L)
        val identity_key = locked_key_setup(identity_events)
        every { session_key_store.get_identity_key() } returns identity_key

        val target = new_vm()
        advanceUntilIdle()
        assertFalse(target.state.value.preferences_locked)
        io.mockk.clearMocks(preferences_api, answers = false, childMocks = false, exclusionRules = false)

        identity_events.value = 1L
        advanceUntilIdle()

        coVerify(exactly = 0) { preferences_api.get_encrypted_preferences() }
    }

    @Test
    fun `restore does not bring back a tag color the user cleared`() = runTest {
        advanceUntilIdle()
        vault_ready(true)
        restore_setup(archived_preferences("""{"show_aster_branding":false}""", ByteArray(32) { 99 }))
        val sealed = encrypt_with_raw_key("#ff0000", tag_field_key())
        every { preferences_cache.list_archived("user_1", "tag") } returns listOf(
            ArchivedBlob(
                "archive_tc",
                "tag",
                "t1:color",
                "${sealed.first}.${sealed.second}",
                3L,
                replaced_with_blank = true,
            ),
        )
        coEvery { tags_api.list_tags(any()) } returns TagsListResponse(
            tags = listOf(TagItem(id = "t1", tag_token = "tt1", encrypted_name = "", name_nonce = "", encrypted_color = "", color_nonce = "")),
        )
        new_vm()
        advanceUntilIdle()

        coVerify(exactly = 0) { tags_api.update_tag(any(), any()) }
        verify(exactly = 1) { preferences_cache.drop_archived("archive_tc") }
    }

    @Test
    fun `clearing an unreadable tag color records the clear with the archive`() = runTest {
        vault_ready(true)
        val field = seed_lost_key_tag()

        vm.recolor_tag("t1", "")
        advanceUntilIdle()

        verify(exactly = 1) {
            preferences_cache.archive_unreadable(any(), "tag", "${field.first}.${field.second}", "t1:color", any(), true, any())
        }
    }

    @Test
    fun `restore skips the write when the folder changed after it was read`() = runTest {
        advanceUntilIdle()
        label_archive_setup()
        coEvery { labels_api.list_labels(false, any()) } returnsMany listOf(
            LabelsListResponse(labels = listOf(LabelItem(id = "l1", label_token = "lt1"))),
            LabelsListResponse(
                labels = listOf(
                    LabelItem(id = "l1", label_token = "lt1", encrypted_name = "bmV3bmFtZQ==", name_nonce = "bm9uY2Vub25jZQ=="),
                ),
            ),
        )
        new_vm()
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.update_label(any(), any()) }
        verify(exactly = 0) { preferences_cache.drop_archived("archive_l1") }
    }

    @Test
    fun `an unreadable field from the previous account is not archived after a switch`() = runTest {
        vault_ready(true)
        val field = seed_lost_key_tag()

        vm.reset_for_account_switch()
        every { session_key_store.get_user_id() } returns "user_2"
        vm.recolor_tag("t1", "#00ff00")
        advanceUntilIdle()

        verify(exactly = 0) {
            preferences_cache.archive_unreadable(any(), "tag", "${field.first}.${field.second}", any(), any(), any(), any())
        }
    }

    @Test
    fun `save records the shown settings with the archived blob`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        vault_ready(true)
        cached_copy()
        server_blob(lost_key_blob())
        coEvery { preferences_api.save_encrypted_preferences(any()) } returns Unit
        val target_slot = slot<String>()
        val baseline_slot = slot<String>()
        every {
            preferences_cache.archive_unreadable(any(), "preferences", any(), capture(target_slot), any(), any(), capture(baseline_slot))
        } returns true
        val target = new_vm()
        advanceUntilIdle()
        val shown = target.state.value.preferences!!

        target.save_preferences(shown.copy(load_remote_images = "always"))
        advanceUntilIdle()

        assertTrue(baseline_slot.isCaptured)
        assertEquals("", target_slot.captured)
        assertTrue(baseline_slot.captured.isNotEmpty())
        val recorded = Json { ignoreUnknownKeys = true }.decodeFromString(
            UserPreferences.serializer(),
            String(Base64.getDecoder().decode(baseline_slot.captured), Charsets.UTF_8),
        )
        assertEquals(shown, recorded)
    }

    @Test
    fun `restore rebases on the baseline stored beside the archive`() = runTest {
        vault_ready(true)
        val sealed = encrypt_with_raw_key("""{"show_aster_branding":false,"compact_mode":true}""", account_prefs_key)
        val archive = ArchivedBlob(
            "archive_k1",
            "preferences",
            "",
            "${sealed.first}.${sealed.second}",
            5L,
            baseline = baseline_target("""{"show_aster_branding":false}"""),
        )
        val saved = restore_setup(archive, """{"show_aster_branding":true,"load_remote_images":"always"}""")
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(true, target.state.value.preferences?.show_aster_branding)
        assertEquals(true, target.state.value.preferences?.compact_mode)
        assertEquals(1, saved.size)
        verify(exactly = 1) { preferences_cache.drop_archived("archive_k1") }
    }

    private fun three_label_archives_setup() {
        vault_ready(true)
        restore_setup(archived_preferences("""{"show_aster_branding":false}""", ByteArray(32) { 99 }))
        every { preferences_cache.list_archived("user_1", "label") } returns (1..3).map { i ->
            val sealed = encrypt_with_raw_key("Folder $i", label_field_key())
            ArchivedBlob("archive_l$i", "label", "l$i:name", "${sealed.first}.${sealed.second}", i.toLong())
        }
        coEvery { labels_api.list_labels(any(), any()) } returns LabelsListResponse(
            labels = (1..3).map { i -> LabelItem(id = "l$i", label_token = "lt$i") },
        )
        coEvery { labels_api.update_label(any(), any()) } returns Unit
    }

    @Test
    fun `restore lists the folders once before writing several archives`() = runTest {
        advanceUntilIdle()
        three_label_archives_setup()
        new_vm()
        advanceUntilIdle()

        coVerify(exactly = 3) { labels_api.update_label(any(), any()) }
        coVerify(exactly = 2) { labels_api.list_labels(false, any()) }
        (1..3).forEach { i -> verify(exactly = 1) { preferences_cache.drop_archived("archive_l$i") } }
    }

    @Test
    fun `restore writes only the folders unchanged at the read before the writes`() = runTest {
        advanceUntilIdle()
        three_label_archives_setup()
        coEvery { labels_api.list_labels(false, any()) } returnsMany listOf(
            LabelsListResponse(labels = (1..3).map { i -> LabelItem(id = "l$i", label_token = "lt$i") }),
            LabelsListResponse(
                labels = listOf(
                    LabelItem(id = "l1", label_token = "lt1"),
                    LabelItem(id = "l2", label_token = "lt2", encrypted_name = "bmV3bmFtZQ==", name_nonce = "bm9uY2Vub25jZQ=="),
                    LabelItem(id = "l3", label_token = "lt3"),
                ),
            ),
        )
        new_vm()
        advanceUntilIdle()

        coVerify(exactly = 1) { labels_api.update_label("l1", any()) }
        coVerify(exactly = 0) { labels_api.update_label("l2", any()) }
        coVerify(exactly = 1) { labels_api.update_label("l3", any()) }
        verify(exactly = 0) { preferences_cache.drop_archived("archive_l2") }
    }

    @Test
    fun `restore lists the folders again before each chunk of writes`() = runTest {
        advanceUntilIdle()
        vault_ready(true)
        restore_setup(archived_preferences("""{"show_aster_branding":false}""", ByteArray(32) { 99 }))
        every { preferences_cache.list_archived("user_1", "label") } returns (1..10).map { i ->
            val sealed = encrypt_with_raw_key("Folder $i", label_field_key())
            ArchivedBlob("archive_l$i", "label", "l$i:name", "${sealed.first}.${sealed.second}", i.toLong())
        }
        val blank = LabelsListResponse(labels = (1..10).map { i -> LabelItem(id = "l$i", label_token = "lt$i") })
        val filled = LabelsListResponse(
            labels = (1..10).map { i ->
                if (i == 1) {
                    LabelItem(id = "l1", label_token = "lt1", encrypted_name = "bmV3bmFtZQ==", name_nonce = "bm9uY2Vub25jZQ==")
                } else {
                    LabelItem(id = "l$i", label_token = "lt$i")
                }
            },
        )
        coEvery { labels_api.list_labels(any(), any()) } returns blank
        coEvery { labels_api.list_labels(false, any()) } returnsMany listOf(blank, blank, filled)
        coEvery { labels_api.update_label(any(), any()) } returns Unit
        new_vm()
        advanceUntilIdle()

        coVerify(exactly = 0) { labels_api.update_label("l1", any()) }
        (2..10).forEach { i -> coVerify(exactly = 1) { labels_api.update_label("l$i", any()) } }
        coVerify(exactly = 3) { labels_api.list_labels(false, any()) }
        verify(exactly = 0) { preferences_cache.drop_archived("archive_l1") }
    }

    private suspend fun TestScope.seed_lost_key_signature() {
        val field = lost_key_field()
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        coEvery { signatures_api.list_signatures() } returns ListSignaturesResponse(
            signatures = listOf(
                Signature(
                    id = "s1",
                    encrypted_name = field.first,
                    name_nonce = field.second,
                    encrypted_content = field.first,
                    content_nonce = field.second,
                    is_default = true,
                ),
            ),
        )
        vm.load_signature()
        advanceUntilIdle()
    }

    @Test
    fun `signature edit refuses when the key load ended without keys`() = runTest {
        vault_ready(false)
        seed_lost_key_signature()

        vm.update_signature("s1", "Work", "Regards", null, null, null)
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, vm.state.value.save_status)
        coVerify(exactly = 0) { signatures_api.update_signature(any(), any()) }
        coVerify(exactly = 0) { preferences_cache.archive_unreadable(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `signature edit keeps the archive when the update fails and retries once`() = runTest {
        vault_ready(true)
        seed_lost_key_signature()
        coEvery { signatures_api.update_signature(any(), any()) } throws RuntimeException("offline")

        vm.update_signature("s1", "Work", "Regards", null, null, null)
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, vm.state.value.save_status)
        verify(exactly = 1) { preferences_cache.archive_unreadable(any(), "signature", any(), "s1:name", any(), any(), any()) }
        verify(exactly = 1) { preferences_cache.archive_unreadable(any(), "signature", any(), "s1:content", any(), any(), any()) }
        verify(exactly = 0) { preferences_cache.drop_archived(any()) }

        coEvery { signatures_api.update_signature(any(), any()) } returns mockk(relaxed = true)
        vm.update_signature("s1", "Work", "Regards", null, null, null)
        advanceUntilIdle()

        coVerify(exactly = 2) { signatures_api.update_signature("s1", any()) }
        verify(exactly = 2) { preferences_cache.archive_unreadable(any(), "signature", any(), any(), any(), any(), any()) }
        verify(exactly = 0) { preferences_cache.drop_archived(any()) }
    }

    @Test
    fun `signature edit archives unreadable fields before writing`() = runTest {
        vault_ready(true)
        seed_lost_key_signature()

        vm.update_signature("s1", "Work", "Regards", null, null, null)
        advanceUntilIdle()

        coVerifyOrder {
            preferences_cache.archive_unreadable(any(), "signature", any(), "s1:name", any(), any(), any())
            preferences_cache.archive_unreadable(any(), "signature", any(), "s1:content", any(), any(), any())
            signatures_api.update_signature("s1", any())
        }
    }

    @Test
    fun `load waits for the account key load before giving up`() = runTest {
        var keks = emptyList<String>()
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        every { session_key_store.get_decrypt_keks() } answers { keks }
        coEvery { session_key_store.await_account_key_load(any()) } answers {
            keks = listOf(Base64.getEncoder().encodeToString(account_prefs_key))
        }
        server_blob(encrypt_with_raw_key("""{"show_aster_branding":false}""", account_prefs_key))
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(false, target.state.value.preferences?.show_aster_branding)
        coVerify(exactly = 1) { session_key_store.await_account_key_load(5_000L) }
    }

    @Test
    fun `load does not wait when the legacy key opens the preferences`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        server_blob(encrypt_test_blob("""{"show_aster_branding":false}""", "user_identity_key"))
        val target = new_vm()
        advanceUntilIdle()

        assertEquals(false, target.state.value.preferences?.show_aster_branding)
        coVerify(exactly = 0) { session_key_store.await_account_key_load(any()) }
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
    fun `undecryptable preferences without a cache never save defaults over the server copy`() = runTest {
        every { session_key_store.get_identity_key() } returns "identity_after_password_change"
        server_blob(
            encrypt_test_blob(
                """{"show_aster_branding":false,"compact_mode":true,"inbox_categories_enabled":false}""",
                "identity_unknown_to_this_device",
            ),
        )
        every { preferences_cache.read(any()) } returns null
        val target = new_vm()
        target.load_preferences(force = true)
        advanceUntilIdle()

        assertTrue(target.state.value.preferences_locked)
        assertFalse(target.state.value.preferences_authoritative)

        target.load_preferences(force = true)
        advanceUntilIdle()
        assertTrue(target.state.value.preferences_locked)

        val shown = target.state.value.preferences ?: UserPreferences()
        target.save_preferences(shown.copy(locked_data_banner_dismissed = "banner"))
        target.save_preferences(shown.copy(emoji_skin_tone = "medium"))
        advanceUntilIdle()

        assertEquals(SaveStatus.ERROR, target.state.value.save_status)
        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        coVerify(exactly = 0) { preferences_api.save_preferences(any()) }
        coVerify(exactly = 0) { preferences_api.save_preferences_raw(any()) }
    }

    @Test
    fun `preferences save again once the server copy opens after a failed load`() = runTest {
        every { session_key_store.get_identity_key() } returns "user_identity_key"
        server_blob(encrypt_test_blob("""{"show_aster_branding":false}""", "identity_unknown_to_this_device"))
        every { preferences_cache.read(any()) } returns null
        val target = new_vm()
        target.load_preferences(force = true)
        advanceUntilIdle()
        assertTrue(target.state.value.preferences_locked)

        server_blob(encrypt_test_blob("""{"show_aster_branding":false,"compact_mode":true}""", "user_identity_key"))
        target.load_preferences(force = true)
        advanceUntilIdle()

        assertFalse(target.state.value.preferences_locked)
        assertEquals(false, target.state.value.preferences?.show_aster_branding)
        assertEquals(true, target.state.value.preferences?.compact_mode)

        val request = io.mockk.slot<org.astermail.android.api.preferences.SaveEncryptedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences(capture(request)) } returns Unit
        target.save_preferences(target.state.value.preferences!!.copy(emoji_skin_tone = "medium"))
        advanceUntilIdle()

        assertEquals(SaveStatus.SAVED, target.state.value.save_status)
        val written = decrypt_with_raw_key(
            request.captured.encrypted_preferences,
            request.captured.preferences_nonce,
            test_prefs_key("user_identity_key"),
        )
        assertTrue(written.contains("\"show_aster_branding\":false"))
        assertTrue(written.contains("\"compact_mode\":true"))
    }

    @Test
    fun `signatures that fail to decrypt keep their cached text`() = runTest {
        every { session_key_store.get_user_id() } returns "user_1"
        val cached = listOf(
            DecryptedSignature(
                id = "sig_1",
                name = "Work",
                content = "Kind regards, Sam",
                is_default = true,
                is_html = false,
                alias_id = null,
                placement = null,
            ),
        )
        every { preferences_cache.read_signatures("user_1") } returns
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(DecryptedSignature.serializer()),
                cached,
            )
        coEvery { signatures_api.list_signatures() } returns
            org.astermail.android.api.signatures.ListSignaturesResponse(
                signatures = listOf(
                    org.astermail.android.api.signatures.Signature(
                        id = "sig_1",
                        encrypted_name = "bm90LWEtdmFsaWQtYmxvYg==",
                        name_nonce = "AAAAAAAAAAAAAAAA",
                        encrypted_content = "bm90LWEtdmFsaWQtYmxvYg==",
                        content_nonce = "AAAAAAAAAAAAAAAA",
                        is_default = true,
                    ),
                ),
                total = 1,
            )
        val written = io.mockk.slot<String>()
        every { preferences_cache.write_signatures("user_1", capture(written)) } returns Unit

        val target = new_vm()
        target.load_signature()
        advanceUntilIdle()

        assertEquals("Kind regards, Sam", target.signatures.value.single().content)
        assertEquals("Work", target.signatures.value.single().name)
        assertEquals("Kind regards, Sam", target.signature_text.value)
        if (written.isCaptured) {
            assertTrue(written.captured.contains("Kind regards, Sam"))
        }
    }

    @Test
    fun `plaintext save preserves web-only keys from the raw load`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            EncryptedPreferencesResponse()
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
            recovery_api = recovery_api,
            mail_rules_api = mail_rules_api,
            keys_api = keys_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        ).also {
            it.default_dispatcher = dispatcher
            it.io_dispatcher = dispatcher
        }
        advanceUntilIdle()
        assertEquals("dark", vm.state.value.preferences?.theme)

        val body = slot<String>()
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
            EncryptedPreferencesResponse(
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
            recovery_api = recovery_api,
            mail_rules_api = mail_rules_api,
            keys_api = keys_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        ).also {
            it.default_dispatcher = dispatcher
            it.io_dispatcher = dispatcher
        }
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
            EncryptedPreferencesResponse()
        coEvery { preferences_api.get_preferences() } returns
            UserPreferences(conversation_grouping = false)

        vm.load_preferences()
        advanceUntilIdle()

        assertTrue(vm.state.value.preferences_authoritative)
    }

    @Test
    fun `load_preferences stays non-authoritative when encrypted blob present but identity key missing`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            EncryptedPreferencesResponse(
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
            recovery_api = recovery_api,
            mail_rules_api = mail_rules_api,
            keys_api = keys_api,
            auth_repository = auth_repository,
            session_key_store = session_key_store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        ).also {
            it.default_dispatcher = dispatcher
            it.io_dispatcher = dispatcher
        }
        advanceUntilIdle()

        assertNotNull(fresh_vm.state.value.preferences)
        assertFalse(fresh_vm.state.value.preferences_authoritative)
    }

    @Test
    fun `load_preferences recovers from the cached copy after a decrypt failure`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            EncryptedPreferencesResponse(
                encrypted_preferences = "blob",
                preferences_nonce = "nonce",
            )
        every { session_key_store.get_identity_key() } returns "current_key_after_password_reset"
        val cached = UserPreferences(show_aster_branding = false, compact_mode = true)
        every { session_key_store.get_user_id() } returns "user_1"
        every { preferences_cache.read("user_1") } returns
            kotlinx.serialization.json.Json { encodeDefaults = true }
                .encodeToString(UserPreferences.serializer(), cached)

        val target = new_vm()
        advanceUntilIdle()

        assertTrue(target.state.value.preferences_authoritative)
        assertFalse(target.state.value.preferences_locked)
        assertEquals(false, target.state.value.preferences?.show_aster_branding)
        assertEquals(true, target.state.value.preferences?.compact_mode)
    }

    @Test
    fun `load_preferences stays locked after a decrypt failure with no cached copy`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } returns
            org.astermail.android.api.preferences.EncryptedPreferencesResponse(
                encrypted_preferences = "blob",
                preferences_nonce = "nonce",
            )
        every { session_key_store.get_identity_key() } returns "current_key_after_password_reset"
        every { preferences_cache.read(any()) } returns null

        val target = new_vm()
        advanceUntilIdle()

        assertFalse(target.state.value.preferences_authoritative)
        assertTrue(target.state.value.preferences_locked)
    }

    @Test
    fun `load_preferences error sets error message`() = runTest {
        coEvery { preferences_api.get_encrypted_preferences() } throws ApiError.UnknownError("prefs error")

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
        coEvery { developer_api.list_api_keys() } throws ApiError.UnknownError("api keys error")

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
    fun `load_referral_info error flags failure without inventing data`() = runTest {
        coEvery { labels_api.get_referral_info() } throws RuntimeException("fail")

        vm.load_referral_info()
        advanceUntilIdle()

        assertNull(vm.state.value.referral)
        assertTrue(vm.state.value.referral_load_failed)
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
        coEvery { auth_api.me() } throws ApiError.UnknownError("first error")
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

    private fun preferences_vm(api: PreferencesApi): SettingsViewModel = SettingsViewModel(
        auth_api = auth_api,
        user_api = user_api,
        settings_api = settings_api,
        labels_api = labels_api,
        family_api = family_api,
        tags_api = tags_api,
        preferences_api = api,
        signatures_api = signatures_api,
        ghost_alias_api = ghost_alias_api,
        auto_forward_api = auto_forward_api,
        developer_api = developer_api,
        subscriptions_api = subscriptions_api,
        recovery_email_api = recovery_email_api,
        security_api = security_api,
        encryption_api = encryption_api,
        alias_detail_api = alias_detail_api,
        recovery_api = recovery_api,
        mail_rules_api = mail_rules_api,
        keys_api = keys_api,
        auth_repository = auth_repository,
        session_key_store = session_key_store,
        token_store = token_store,
        account_store = account_store,
        preferences_cache = preferences_cache,
        theme_store = theme_store,
        context = context,
    ).also {
        it.default_dispatcher = dispatcher
        it.io_dispatcher = dispatcher
    }

    @Test
    fun `load_preferences does not restart an in-flight load`() = runTest {
        val api = mockk<PreferencesApi>(relaxed = true)
        val gate = CompletableDeferred<EncryptedPreferencesResponse>()
        var cancelled = false
        coEvery { api.get_encrypted_preferences() } coAnswers {
            try {
                gate.await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        coEvery { api.get_preferences_raw() } returns """{"theme":"dark"}"""
        val fresh_vm = preferences_vm(api)
        advanceUntilIdle()

        fresh_vm.load_preferences()
        advanceUntilIdle()
        gate.complete(EncryptedPreferencesResponse())
        advanceUntilIdle()

        coVerify(exactly = 1) { api.get_encrypted_preferences() }
        assertFalse(cancelled)
        assertEquals("dark", fresh_vm.state.value.preferences?.theme)
        assertTrue(fresh_vm.state.value.preferences_authoritative)
        assertFalse(fresh_vm.state.value.is_loading)
    }

    @Test
    fun `forced load_preferences restarts an in-flight load`() = runTest {
        val api = mockk<PreferencesApi>(relaxed = true)
        val gate = CompletableDeferred<EncryptedPreferencesResponse>()
        coEvery { api.get_encrypted_preferences() } coAnswers { gate.await() }
        coEvery { api.get_preferences_raw() } returns """{"theme":"dark"}"""
        val fresh_vm = preferences_vm(api)
        advanceUntilIdle()

        fresh_vm.load_preferences(force = true)
        advanceUntilIdle()
        gate.complete(EncryptedPreferencesResponse())
        advanceUntilIdle()

        coVerify(exactly = 2) { api.get_encrypted_preferences() }
        assertEquals("dark", fresh_vm.state.value.preferences?.theme)
    }

    @Test
    fun `load_preferences after account switch fetches while previous load is in flight`() = runTest {
        val api = mockk<PreferencesApi>(relaxed = true)
        val gate = CompletableDeferred<EncryptedPreferencesResponse>()
        coEvery { api.get_encrypted_preferences() } coAnswers { gate.await() }
        coEvery { api.get_preferences_raw() } returns """{"theme":"dark"}"""
        val fresh_vm = preferences_vm(api)
        advanceUntilIdle()

        fresh_vm.reset_for_account_switch()
        fresh_vm.load_preferences()
        advanceUntilIdle()
        gate.complete(EncryptedPreferencesResponse())
        advanceUntilIdle()

        coVerify(exactly = 2) { api.get_encrypted_preferences() }
        assertEquals("dark", fresh_vm.state.value.preferences?.theme)
        assertTrue(fresh_vm.state.value.preferences_authoritative)
    }

    private fun sidebar_layout_of(written: String): Pair<List<String>, List<String>> {
        val obj = Json.parseToJsonElement(written).jsonObject
        fun list(key: String) = obj.getValue(key).jsonArray.map { it.jsonPrimitive.content }
        return list("sidebar_section_order") to list("sidebar_hidden_items")
    }

    private fun TestScope.sidebar_vm(
        identity_key: String,
        server_json: String,
    ): SettingsViewModel {
        every { session_key_store.get_identity_key() } returns identity_key
        server_blob(encrypt_test_blob(server_json, identity_key))
        val target = new_vm()
        advanceUntilIdle()
        return target
    }

    @Test
    fun `sidebar layout changes save to the synced blob and keep unknown ids`() = runTest {
        val identity_key = "user_identity_key"
        val target = sidebar_vm(
            identity_key,
            """{"sidebar_section_order":["mail","calendar","folders","labels","aliases"],""" +
                """"sidebar_hidden_items":["future_item"],"web_only_setting":"keep_me"}""",
        )
        val requests = mutableListOf<SaveEncryptedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences(capture(requests)) } returns Unit

        target.move_sidebar_section("folders", -1)
        target.set_sidebar_item_hidden("trash", true)
        advanceUntilIdle()

        val prefs = target.state.value.preferences!!
        assertEquals(listOf("folders", "calendar", "mail", "labels", "aliases"), prefs.sidebar_section_order)
        assertEquals(listOf("future_item", "trash"), prefs.sidebar_hidden_items)
        assertEquals(SaveStatus.SAVED, target.state.value.save_status)
        val written = decrypt_test_blob(
            requests.last().encrypted_preferences,
            requests.last().preferences_nonce,
            identity_key,
        )
        val (order, hidden) = sidebar_layout_of(written)
        assertEquals(listOf("folders", "calendar", "mail", "labels", "aliases"), order)
        assertEquals(listOf("future_item", "trash"), hidden)
        assertTrue(written.contains("\"web_only_setting\":\"keep_me\""))
    }

    @Test
    fun `sidebar changes that do nothing never save`() = runTest {
        val target = sidebar_vm("user_identity_key", """{"sidebar_hidden_items":["spam"]}""")

        target.move_sidebar_section("mail", -1)
        target.move_sidebar_section("aliases", 1)
        target.move_sidebar_section("calendar", 1)
        target.set_sidebar_item_hidden("inbox", true)
        target.set_sidebar_item_hidden("mail", true)
        target.set_sidebar_item_hidden("spam", true)
        target.set_sidebar_item_hidden("trash", false)
        advanceUntilIdle()

        coVerify(exactly = 0) { preferences_api.save_encrypted_preferences(any()) }
        assertEquals(listOf("spam"), target.state.value.preferences!!.sidebar_hidden_items)
    }

    @Test
    fun `sidebar move rebases onto a hide saved by another device`() = runTest {
        val identity_key = "user_identity_key"
        val target = sidebar_vm(identity_key, """{"web_only_setting":"keep_me"}""")
        server_blob(
            encrypt_test_blob(
                """{"sidebar_hidden_items":["spam","aliases"],"web_only_setting":"keep_me"}""",
                identity_key,
            ),
        )
        val request = slot<SaveEncryptedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences(capture(request)) } returns Unit

        target.move_sidebar_section("labels", -1)
        advanceUntilIdle()

        val written = decrypt_test_blob(request.captured.encrypted_preferences, request.captured.preferences_nonce, identity_key)
        val (order, hidden) = sidebar_layout_of(written)
        assertEquals(listOf("mail", "labels", "folders", "aliases"), order)
        assertEquals(listOf("spam", "aliases"), hidden)
    }

    @Test
    fun `sidebar reset restores the default layout`() = runTest {
        val identity_key = "user_identity_key"
        val target = sidebar_vm(
            identity_key,
            """{"sidebar_section_order":["aliases","labels"],"sidebar_hidden_items":["sent","folders"]}""",
        )
        val request = slot<SaveEncryptedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences(capture(request)) } returns Unit

        target.reset_sidebar_layout()
        advanceUntilIdle()

        val prefs = target.state.value.preferences!!
        assertEquals(listOf("mail", "folders", "labels", "aliases"), prefs.sidebar_section_order)
        assertTrue(prefs.sidebar_hidden_items.isEmpty())
        val written = decrypt_test_blob(request.captured.encrypted_preferences, request.captured.preferences_nonce, identity_key)
        assertEquals(listOf("mail", "folders", "labels", "aliases") to emptyList<String>(), sidebar_layout_of(written))

        target.reset_sidebar_layout()
        advanceUntilIdle()
        coVerify(exactly = 1) { preferences_api.save_encrypted_preferences(any()) }
    }

    @Test
    fun `sidebar reset keeps ids from newer apps`() = runTest {
        val identity_key = "user_identity_key"
        val target = sidebar_vm(
            identity_key,
            """{"sidebar_section_order":["aliases","calendar","labels","mail"],"sidebar_hidden_items":["calendar_tasks","spam"]}""",
        )
        val request = slot<SaveEncryptedPreferencesRequest>()
        coEvery { preferences_api.save_encrypted_preferences(capture(request)) } returns Unit

        target.reset_sidebar_layout()
        advanceUntilIdle()

        val written = decrypt_test_blob(request.captured.encrypted_preferences, request.captured.preferences_nonce, identity_key)
        assertEquals(
            listOf("mail", "calendar", "folders", "labels", "aliases") to listOf("calendar_tasks"),
            sidebar_layout_of(written),
        )
    }

    private val old_recovery_codes = listOf("ASTER-AAAA-BBBB-CCCC", "ASTER-DDDD-EEEE-FFFF")
    private val new_recovery_codes = listOf("ASTER-AAAA-BBBB-CCCC-DDDD", "ASTER-EEEE-FFFF-GGGG-HHHH")

    @Test
    fun `recovery code rotation success shows new codes and clears the prompt`() = runTest {
        every { session_key_store.get_recovery_codes() } returns old_recovery_codes
        coEvery { auth_repository.sync_recovery_codes() } returns old_recovery_codes
        vm.refresh_recovery_codes_outdated()
        advanceUntilIdle()
        assertTrue(vm.state.value.recovery_codes_outdated)

        coEvery { auth_repository.rotate_recovery_codes("token-1") } returns new_recovery_codes
        vm.start_recovery_codes_rotation("token-1")
        assertEquals(RecoveryRotationPhase.rotating, vm.state.value.recovery_rotation.phase)
        advanceUntilIdle()

        val rotation = vm.state.value.recovery_rotation
        assertEquals(RecoveryRotationPhase.done, rotation.phase)
        assertEquals(new_recovery_codes, rotation.codes)
        assertFalse(vm.state.value.recovery_codes_outdated)
        coVerify { recovery_api.methods() }

        vm.acknowledge_recovery_rotation()
        assertEquals(RecoveryRotationPhase.idle, vm.state.value.recovery_rotation.phase)
    }

    @Test
    fun `starting a rotation reads stored codes inside the background job`() = runTest {
        every { session_key_store.get_recovery_codes() } returns old_recovery_codes
        coEvery { auth_repository.rotate_recovery_codes("token-1") } returns new_recovery_codes

        vm.start_recovery_codes_rotation("token-1")

        assertEquals(RecoveryRotationPhase.rotating, vm.state.value.recovery_rotation.phase)
        verify(exactly = 0) { session_key_store.get_recovery_codes() }

        advanceUntilIdle()

        verify(exactly = 1) { session_key_store.get_recovery_codes() }
        assertEquals(RecoveryRotationPhase.done, vm.state.value.recovery_rotation.phase)
    }

    @Test
    fun `a stale outdated check cannot bring the prompt back after rotation`() = runTest {
        every { session_key_store.get_recovery_codes() } returns old_recovery_codes
        coEvery { auth_repository.sync_recovery_codes() } coAnswers {
            kotlinx.coroutines.delay(5_000)
            old_recovery_codes
        }
        coEvery { auth_repository.rotate_recovery_codes(any()) } returns new_recovery_codes
        vm.refresh_recovery_codes_outdated()
        vm.start_recovery_codes_rotation("token-1")
        advanceUntilIdle()

        assertEquals(RecoveryRotationPhase.done, vm.state.value.recovery_rotation.phase)
        assertFalse(vm.state.value.recovery_codes_outdated)
    }

    @Test
    fun `recovery code rotation server error ends in failed with a message`() = runTest {
        every { session_key_store.get_recovery_codes() } returns old_recovery_codes
        coEvery { auth_repository.rotate_recovery_codes(any()) } throws
            ApiError.ServerError(500)
        coEvery { auth_repository.sync_recovery_codes() } returns old_recovery_codes

        vm.start_recovery_codes_rotation("token-1")
        advanceUntilIdle()

        val rotation = vm.state.value.recovery_rotation
        assertEquals(RecoveryRotationPhase.failed, rotation.phase)
        assertNotNull(rotation.error)
        assertFalse(rotation.needs_step_up)
    }

    @Test
    fun `recovery code rotation keeps codes the server already saved`() = runTest {
        every { session_key_store.get_recovery_codes() } returns old_recovery_codes
        coEvery { auth_repository.rotate_recovery_codes(any()) } throws
            ApiError.NetworkError
        coEvery { auth_repository.sync_recovery_codes() } returns new_recovery_codes

        vm.start_recovery_codes_rotation("token-1")
        advanceUntilIdle()

        val rotation = vm.state.value.recovery_rotation
        assertEquals(RecoveryRotationPhase.done, rotation.phase)
        assertEquals(new_recovery_codes, rotation.codes)
    }

    @Test
    fun `an expired step up token asks for the password again`() = runTest {
        every { session_key_store.get_recovery_codes() } returns old_recovery_codes
        every { context.getString(R.string.recovery_step_up_description) } returns
            "Confirm your password"
        coEvery { auth_repository.rotate_recovery_codes(any()) } throws
            ApiError.ForbiddenError("step-up required", "STEP_UP_REQUIRED")

        vm.start_recovery_codes_rotation("spent")
        advanceUntilIdle()

        val rotation = vm.state.value.recovery_rotation
        assertEquals(RecoveryRotationPhase.failed, rotation.phase)
        assertTrue(rotation.needs_step_up)
        assertEquals("Confirm your password", rotation.error)
        coVerify(exactly = 0) { auth_repository.sync_recovery_codes() }
    }

    @Test
    fun `recovery code rotation timeout never leaves the screen loading`() = runTest {
        every { session_key_store.get_recovery_codes() } returns old_recovery_codes
        every { context.getString(R.string.error_timeout) } returns "Timed out"
        coEvery { auth_repository.rotate_recovery_codes(any()) } coAnswers {
            kotlinx.coroutines.delay(SettingsViewModel.RECOVERY_ROTATION_TIMEOUT_MS * 2)
            new_recovery_codes
        }

        vm.start_recovery_codes_rotation("token-1")
        advanceTimeBy(SettingsViewModel.RECOVERY_ROTATION_TIMEOUT_MS + 1)
        runCurrent()

        val timed_out = vm.state.value.recovery_rotation
        assertEquals(RecoveryRotationPhase.failed, timed_out.phase)
        assertEquals("Timed out", timed_out.error)

        advanceUntilIdle()
        val late = vm.state.value.recovery_rotation
        assertEquals(RecoveryRotationPhase.done, late.phase)
        assertEquals(new_recovery_codes, late.codes)
    }

    @Test
    fun `a second tap while rotating does not start another rotation`() = runTest {
        every { session_key_store.get_recovery_codes() } returns old_recovery_codes
        coEvery { auth_repository.rotate_recovery_codes(any()) } coAnswers {
            kotlinx.coroutines.delay(1_000)
            new_recovery_codes
        }

        vm.start_recovery_codes_rotation("token-1")
        vm.start_recovery_codes_rotation("token-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { auth_repository.rotate_recovery_codes(any()) }
        assertEquals(RecoveryRotationPhase.done, vm.state.value.recovery_rotation.phase)
    }

    @Test
    fun `recovery code rotation runs off the main dispatcher`() = runTest {
        val worker = StandardTestDispatcher(testScheduler, name = "recovery-worker")
        vm.default_dispatcher = worker
        every { session_key_store.get_recovery_codes() } returns old_recovery_codes
        var observed: ContinuationInterceptor? = null
        coEvery { auth_repository.rotate_recovery_codes(any()) } coAnswers {
            observed = kotlin.coroutines.coroutineContext[ContinuationInterceptor]
            new_recovery_codes
        }

        vm.start_recovery_codes_rotation("token-1")
        advanceUntilIdle()

        assertEquals(worker, observed)
        assertTrue(observed != dispatcher)
        assertEquals(RecoveryRotationPhase.done, vm.state.value.recovery_rotation.phase)
    }

    @Test
    fun `a held session sign out shows the hold`() = runTest {
        coEvery { settings_api.revoke_session("s1") } throws
            org.astermail.android.api.ApiError.GuardHeld("c1", "guard_device_remove", "2026-11-14T11:00:00Z")

        vm.revoke_session("s1")
        advanceUntilIdle()

        assertEquals("c1", vm.state.value.sessions_held?.id)
    }

    @Test
    fun `a held forwarding rule shows the hold and no error`() = runTest {
        coEvery { auto_forward_api.create_rule(any()) } throws
            org.astermail.android.api.ApiError.GuardHeld("c2", "forwarding_change", "2026-11-14T11:00:00Z")

        vm.create_forwarding_rule("a@example.com", keep_copy = true)
        advanceUntilIdle()

        assertEquals("c2", vm.state.value.forwarding_held?.id)
        assertEquals(SaveStatus.IDLE, vm.state.value.save_status)
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun `a held recovery email removal shows the hold`() = runTest {
        coEvery { auth_repository.derive_password_hash_b64(any()) } returns "h"
        coEvery { recovery_email_api.remove(any()) } throws
            org.astermail.android.api.ApiError.GuardHeld("c3", "recovery_email_change", "2026-11-14T11:00:00Z")

        vm.remove_recovery_email("pw", null) { "" }
        advanceUntilIdle()

        assertEquals("c3", vm.state.value.recovery_email_held?.id)
        assertEquals(SaveStatus.IDLE, vm.state.value.save_status)
        assertEquals(null, vm.state.value.error)
    }

    private fun smtp_body_now(): SmtpTokensBody {
        val s = vm.state.value
        return org.astermail.android.ui.settings.detail.smtp_tokens_body(
            addresses_known = true,
            has_addresses = true,
            has_tokens = s.smtp_tokens.isNotEmpty(),
            loaded = s.smtp_tokens_loaded,
            load_failed = s.smtp_tokens_load_failed,
        )
    }

    @Test
    fun `smtp tokens show no empty state before the first load finishes`() = runTest {
        val gate = CompletableDeferred<ListSmtpTokensResponse>()
        coEvery { settings_api.list_smtp_tokens() } coAnswers { gate.await() }

        assertEquals(SmtpTokensBody.placeholder, smtp_body_now())
        vm.load_smtp_tokens()
        runCurrent()
        assertEquals(SmtpTokensBody.placeholder, smtp_body_now())

        gate.complete(ListSmtpTokensResponse(emptyList()))
        advanceUntilIdle()
        assertEquals(SmtpTokensBody.empty, smtp_body_now())
    }

    @Test
    fun `smtp token load failure shows an error instead of the empty state`() = runTest {
        coEvery { settings_api.list_smtp_tokens() } throws IOException("offline")

        vm.load_smtp_tokens()
        advanceUntilIdle()

        assertEquals(SmtpTokensBody.error, smtp_body_now())
    }

    @Test
    fun `smtp tokens wait for custom domain addresses before showing the no domain notice`() {
        val body = org.astermail.android.ui.settings.detail.smtp_tokens_body(
            addresses_known = false,
            has_addresses = false,
            has_tokens = false,
            loaded = false,
            load_failed = false,
        )
        assertEquals(SmtpTokensBody.placeholder, body)
    }

    @Test
    fun `reopening smtp tokens keeps the cached rows while it revalidates`() = runTest {
        val rows = listOf(
            SmtpTokenRow(id = "t1", bound_address = "a@example.com"),
            SmtpTokenRow(id = "t2", bound_address = "b@example.com"),
        )
        coEvery { settings_api.list_smtp_tokens() } returns
            ListSmtpTokensResponse(rows)
        vm.load_smtp_tokens()
        advanceUntilIdle()

        val gate = CompletableDeferred<ListSmtpTokensResponse>()
        coEvery { settings_api.list_smtp_tokens() } coAnswers { gate.await() }
        vm.load_smtp_tokens()
        runCurrent()

        assertEquals(listOf("t1", "t2"), vm.state.value.smtp_tokens.map { it.id })
        assertEquals(SmtpTokensBody.list, smtp_body_now())
        gate.complete(ListSmtpTokensResponse(rows.take(1)))
        advanceUntilIdle()
        assertEquals(listOf("t1"), vm.state.value.smtp_tokens.map { it.id })
        coVerify(exactly = 2) { settings_api.list_smtp_tokens() }
    }

    @Test
    fun `smtp token prefetch and screen open share one request`() = runTest {
        val gate = CompletableDeferred<ListSmtpTokensResponse>()
        coEvery { settings_api.list_smtp_tokens() } coAnswers { gate.await() }

        vm.prefetch_smtp_tokens()
        runCurrent()
        vm.load_smtp_tokens()
        runCurrent()
        gate.complete(ListSmtpTokensResponse(emptyList()))
        advanceUntilIdle()
        vm.prefetch_smtp_tokens()
        advanceUntilIdle()

        coVerify(exactly = 1) { settings_api.list_smtp_tokens() }
    }

    @Test
    fun `smtp tokens from a previous account are dropped on account switch`() = runTest {
        coEvery { settings_api.list_smtp_tokens() } returns ListSmtpTokensResponse(
            listOf(SmtpTokenRow(id = "t1")),
        )
        vm.load_smtp_tokens()
        advanceUntilIdle()

        vm.reset_for_account_switch()

        assertTrue(vm.state.value.smtp_tokens.isEmpty())
        assertEquals(SmtpTokensBody.placeholder, smtp_body_now())
    }

    @Test
    fun `opening smtp tokens during the settings prefetch shares one request`() = runTest {
        val gate = CompletableDeferred<ListSmtpTokensResponse>()
        coEvery { settings_api.list_smtp_tokens() } coAnswers { gate.await() }

        vm.load_smtp_tokens()
        runCurrent()
        vm.load_smtp_tokens()
        runCurrent()
        gate.complete(ListSmtpTokensResponse(emptyList()))
        advanceUntilIdle()

        coVerify(exactly = 1) { settings_api.list_smtp_tokens() }
    }

    @Test
    fun `smtp tokens report loading as soon as a load starts`() {
        coEvery { settings_api.list_smtp_tokens() } returns
            ListSmtpTokensResponse(emptyList())

        vm.load_smtp_tokens()

        assertTrue(vm.state.value.smtp_tokens_loading)
    }

    private val smtp_details = org.astermail.android.api.settings.SmtpTokenConnectionSettings(
        host = "smtp.example.com",
        port = 587,
        security = "STARTTLS",
        username = "a@example.com",
    )

    private fun seed_smtp_tokens(with_settings: Boolean = true) {
        coEvery { settings_api.list_smtp_tokens() } returns ListSmtpTokensResponse(
            listOf(
                SmtpTokenRow(
                    id = "t1",
                    bound_address = "a@example.com",
                    smtp_settings = if (with_settings) smtp_details else null,
                    decrypted_label = "Old name",
                ),
                SmtpTokenRow(id = "t2", bound_address = "b@example.com", decrypted_label = "Other"),
            ),
        )
        every { context.getString(R.string.smtp_token_renamed_toast) } returns "renamed"
        every { context.getString(R.string.smtp_token_rename_failed) } returns "rename_failed"
        every { context.getString(R.string.smtp_token_rename_unavailable) } returns "rename_unavailable"
        every { context.getString(R.string.smtp_token_unavailable) } returns "token_unavailable"
        every { context.getString(R.string.smtp_token_name_invalid) } returns "name_invalid"
        every { context.getString(R.string.error_no_connection) } returns "no_connection"
        vm.load_smtp_tokens()
    }

    private fun smtp_label(id: String): String? =
        vm.state.value.smtp_tokens.firstOrNull { it.id == id }?.decrypted_label

    @Test
    fun `renaming an smtp token updates its label after the server accepts it`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        val sent = slot<org.astermail.android.api.settings.RenameSmtpTokenRequest>()
        val gate = CompletableDeferred<SmtpTokenRow>()
        coEvery { settings_api.rename_smtp_token("t1", capture(sent)) } coAnswers { gate.await() }

        vm.rename_smtp_token("t1", "  Billing server  ")
        runCurrent()

        assertTrue(vm.state.value.smtp_token_renaming)
        assertEquals("Old name", smtp_label("t1"))

        gate.complete(SmtpTokenRow(id = "t1", bound_address = "a@example.com"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Billing server", smtp_label("t1"))
        assertEquals("Other", smtp_label("t2"))
        assertEquals(smtp_details, state.smtp_tokens.first { it.id == "t1" }.smtp_settings)
        assertFalse(state.smtp_token_renaming)
        assertEquals("t1", state.smtp_token_renamed_id)
        assertNull(state.smtp_token_rename_error)
        assertEquals("renamed", state.action_result)
        assertTrue(sent.captured.label_encrypted.isNotBlank())
        assertTrue(sent.captured.label_nonce.isNotBlank())
        assertFalse(sent.captured.label_encrypted.contains("Billing server"))
        coVerify(exactly = 1) { settings_api.rename_smtp_token("t1", any()) }
    }

    @Test
    fun `renaming an smtp token reports it as unavailable when the route is missing`() = runTest {
        seed_smtp_tokens(with_settings = false)
        advanceUntilIdle()
        coEvery { settings_api.rename_smtp_token(any(), any()) } throws ApiError.NotFoundError

        vm.rename_smtp_token("t1", "Billing server")
        advanceUntilIdle()

        assertEquals("rename_unavailable", vm.state.value.smtp_token_rename_error)
        assertEquals("Old name", smtp_label("t1"))
        assertFalse(vm.state.value.smtp_token_renaming)
        assertNull(vm.state.value.smtp_token_renamed_id)
        assertNull(vm.state.value.action_result)
        coVerify(exactly = 1) { settings_api.list_smtp_tokens() }
    }

    @Test
    fun `renaming an smtp token that no longer exists removes it and reloads the list`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        coEvery { settings_api.rename_smtp_token(any(), any()) } throws ApiError.NotFoundError
        coEvery { settings_api.list_smtp_tokens() } returns ListSmtpTokensResponse(
            tokens = listOf(
                SmtpTokenRow(
                    id = "t2",
                    bound_address = "b@example.com",
                    smtp_settings = smtp_details,
                    decrypted_label = "Other",
                ),
            ),
        )

        vm.rename_smtp_token("t1", "Billing server")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("token_unavailable", state.action_result)
        assertNull(state.smtp_token_rename_error)
        assertNull(state.smtp_token_renamed_id)
        assertFalse(state.smtp_token_renaming)
        assertEquals(listOf("t2"), state.smtp_tokens.map { it.id })
        coVerify(exactly = 2) { settings_api.list_smtp_tokens() }
    }

    @Test
    fun `closing the created token dialog drops the one time password from state`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        coEvery { settings_api.create_smtp_token(any()) } returns
            org.astermail.android.api.settings.CreateSmtpTokenResponse(
                id = "t3",
                smtp_settings = org.astermail.android.api.settings.SmtpTokenSettings(password = "one-time-secret"),
            )

        vm.create_smtp_token("Billing server", "a", "example.com")
        advanceUntilIdle()
        assertEquals("one-time-secret", vm.state.value.smtp_token_created?.smtp_settings?.password)

        vm.clear_created_smtp_token()

        assertNull(vm.state.value.smtp_token_created)
        assertFalse(vm.state.value.toString().contains("one-time-secret"))
    }

    @Test
    fun `a failed smtp token create keeps its error for the dialog and clears on retry`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        coEvery { settings_api.create_smtp_token(any()) } throws RuntimeException("boom")

        vm.create_smtp_token("Billing server", "a", "example.com")
        advanceUntilIdle()

        assertEquals("Something went wrong", vm.state.value.smtp_token_create_error)
        assertFalse(vm.state.value.smtp_token_creating)
        assertNull(vm.state.value.smtp_token_created)
        assertNull(vm.state.value.action_result)

        coEvery { settings_api.create_smtp_token(any()) } returns
            org.astermail.android.api.settings.CreateSmtpTokenResponse(id = "t4")
        vm.create_smtp_token("Billing server", "a", "example.com")
        advanceUntilIdle()

        assertNull(vm.state.value.smtp_token_create_error)
        assertEquals("t4", vm.state.value.smtp_token_created?.id)
    }

    @Test
    fun `dismissing the create dialog clears its error`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        coEvery { settings_api.create_smtp_token(any()) } throws RuntimeException("boom")
        vm.create_smtp_token("Billing server", "a", "example.com")
        advanceUntilIdle()

        vm.clear_smtp_token_create_error()

        assertNull(vm.state.value.smtp_token_create_error)
    }

    @Test
    fun `a method not allowed rename stays unavailable when the list carries settings`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        coEvery { settings_api.rename_smtp_token(any(), any()) } throws ApiError.ServerError(405)

        vm.rename_smtp_token("t1", "Billing server")
        advanceUntilIdle()

        assertEquals("rename_unavailable", vm.state.value.smtp_token_rename_error)
        assertEquals("Old name", smtp_label("t1"))
        coVerify(exactly = 1) { settings_api.list_smtp_tokens() }
    }

    @Test
    fun `renaming an smtp token reports it as unavailable when the method is not allowed`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        coEvery { settings_api.rename_smtp_token(any(), any()) } throws ApiError.ServerError(405)

        vm.rename_smtp_token("t1", "Billing server")
        advanceUntilIdle()

        assertEquals("rename_unavailable", vm.state.value.smtp_token_rename_error)
        assertEquals("Old name", smtp_label("t1"))
        assertFalse(vm.state.value.smtp_token_renaming)
        assertNull(vm.state.value.smtp_token_renamed_id)
    }

    @Test
    fun `a failed smtp token rename keeps the old label and reports the failure`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        coEvery { settings_api.rename_smtp_token(any(), any()) } throws ApiError.ServerError(500)

        vm.rename_smtp_token("t1", "Billing server")
        advanceUntilIdle()

        assertEquals("rename_failed", vm.state.value.smtp_token_rename_error)
        assertEquals("Old name", smtp_label("t1"))
        assertFalse(vm.state.value.smtp_token_renaming)

        coEvery { settings_api.rename_smtp_token(any(), any()) } throws IOException("boom")
        vm.rename_smtp_token("t1", "Billing server")
        advanceUntilIdle()

        assertEquals("rename_failed", vm.state.value.smtp_token_rename_error)
        assertEquals("Old name", smtp_label("t1"))
    }

    @Test
    fun `an smtp token rename without a connection reports the connection`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        coEvery { settings_api.rename_smtp_token(any(), any()) } throws ApiError.NetworkError

        vm.rename_smtp_token("t1", "Billing server")
        advanceUntilIdle()

        assertEquals("no_connection", vm.state.value.smtp_token_rename_error)
        assertEquals("Old name", smtp_label("t1"))
        assertFalse(vm.state.value.smtp_token_renaming)
    }

    @Test
    fun `an invalid smtp token name is rejected before any request`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()

        for (name in listOf("", "   ", "first\nsecond", "first\tsecond", "a".repeat(65))) {
            vm.clear_smtp_token_rename()
            vm.rename_smtp_token("t1", name)
            advanceUntilIdle()

            assertEquals("name_invalid", vm.state.value.smtp_token_rename_error)
            assertFalse(vm.state.value.smtp_token_renaming)
        }

        assertEquals("Old name", smtp_label("t1"))
        coVerify(exactly = 0) { settings_api.rename_smtp_token(any(), any()) }
    }

    @Test
    fun `an smtp token rename that finishes after an account switch is ignored`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        val gate = CompletableDeferred<SmtpTokenRow>()
        coEvery { settings_api.rename_smtp_token(any(), any()) } coAnswers { gate.await() }

        vm.rename_smtp_token("t1", "Billing server")
        runCurrent()
        vm.reset_for_account_switch()
        gate.complete(SmtpTokenRow(id = "t1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.smtp_tokens.isEmpty())
        assertNull(state.smtp_token_renamed_id)
        assertNull(state.smtp_token_rename_error)
        assertNull(state.action_result)
        assertFalse(state.smtp_token_renaming)
    }

    @Test
    fun `an smtp token rename that fails after an account switch is ignored`() = runTest {
        seed_smtp_tokens()
        advanceUntilIdle()
        val gate = CompletableDeferred<SmtpTokenRow>()
        coEvery { settings_api.rename_smtp_token(any(), any()) } coAnswers { gate.await() }

        vm.rename_smtp_token("t1", "Billing server")
        runCurrent()
        vm.reset_for_account_switch()
        gate.completeExceptionally(ApiError.ServerError(500))
        advanceUntilIdle()

        assertNull(vm.state.value.smtp_token_rename_error)
        assertFalse(vm.state.value.smtp_token_renaming)
    }

    @Test
    fun `first security load survives a transient server error on every list`() = runTest {
        var key_calls = 0
        var device_calls = 0
        var audit_calls = 0
        coEvery { security_api.list_hardware_keys() } answers {
            key_calls++
            if (key_calls == 1) throw ApiError.ServerError(502)
            org.astermail.android.api.security.HardwareKeysResponse(
                keys = listOf(org.astermail.android.api.security.HardwareKey(id = "k1", name = "Laptop")),
            )
        }
        coEvery { security_api.list_trusted_devices() } answers {
            device_calls++
            if (device_calls == 1) throw ApiError.RateLimited()
            org.astermail.android.api.security.TrustedDevicesResponse(
                devices = listOf(org.astermail.android.api.security.TrustedDevice(id = "d1", label = "Phone")),
            )
        }
        coEvery { security_api.get_audit_log(any(), any()) } answers {
            audit_calls++
            if (audit_calls == 1) throw ApiError.UnauthorizedError
            org.astermail.android.api.security.AuditLogResponse(
                events = listOf(org.astermail.android.api.security.AuditEvent(id = "e1", event_type = "login")),
            )
        }

        vm.load_hardware_keys()
        vm.load_trusted_devices()
        vm.load_audit_log()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.hardware_keys.size)
        assertFalse(state.hardware_keys_load_failed)
        assertEquals(1, state.trusted_devices.size)
        assertFalse(state.trusted_devices_load_failed)
        assertEquals(1, state.audit_events.size)
        assertFalse(state.audit_events_load_failed)
    }

    @Test
    fun `security lists read as loading until the first response arrives`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { security_api.list_hardware_keys() } coAnswers {
            gate.await()
            org.astermail.android.api.security.HardwareKeysResponse()
        }
        coEvery { security_api.list_trusted_devices() } coAnswers {
            gate.await()
            org.astermail.android.api.security.TrustedDevicesResponse()
        }
        coEvery { security_api.get_audit_log(any(), any()) } coAnswers {
            gate.await()
            org.astermail.android.api.security.AuditLogResponse()
        }

        vm.load_hardware_keys()
        vm.load_trusted_devices()
        vm.load_audit_log()
        runCurrent()

        var state = vm.state.value
        assertFalse(state.hardware_keys_loaded || state.hardware_keys_load_failed)
        assertFalse(state.trusted_devices_loaded || state.trusted_devices_load_failed)
        assertFalse(state.audit_events_loaded || state.audit_events_load_failed)

        gate.complete(Unit)
        advanceUntilIdle()

        state = vm.state.value
        assertTrue(state.hardware_keys_loaded)
        assertTrue(state.trusted_devices_loaded)
        assertTrue(state.audit_events_loaded)
        assertFalse(state.hardware_keys_load_failed)
    }

    @Test
    fun `security lists fail only after retries run out or on a real error`() = runTest {
        var key_calls = 0
        coEvery { security_api.list_hardware_keys() } answers {
            key_calls++
            throw ApiError.ServerError(503)
        }
        var device_calls = 0
        coEvery { security_api.list_trusted_devices() } answers {
            device_calls++
            throw ApiError.ForbiddenError()
        }

        vm.load_hardware_keys()
        vm.load_trusted_devices()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(settings_load_backoff_ms.size + 1, key_calls)
        assertTrue(state.hardware_keys_load_failed)
        assertEquals(1, device_calls)
        assertTrue(state.trusted_devices_load_failed)

        coEvery { security_api.list_hardware_keys() } returns org.astermail.android.api.security.HardwareKeysResponse()
        vm.load_hardware_keys()
        runCurrent()
        assertFalse(vm.state.value.hardware_keys_load_failed)
        advanceUntilIdle()
        assertTrue(vm.state.value.hardware_keys_loaded)
        assertFalse(vm.state.value.hardware_keys_load_failed)
    }

    @Test
    fun `security lists wait for the session before the first request`() = runTest {
        val session = CompletableDeferred<Unit>()
        vm.await_session_ready = { session.await() }
        coEvery { security_api.list_hardware_keys() } returns org.astermail.android.api.security.HardwareKeysResponse()

        vm.load_hardware_keys()
        runCurrent()
        coVerify(exactly = 0) { security_api.list_hardware_keys() }
        assertFalse(vm.state.value.hardware_keys_load_failed)

        session.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 1) { security_api.list_hardware_keys() }
        assertTrue(vm.state.value.hardware_keys_loaded)
    }
}
