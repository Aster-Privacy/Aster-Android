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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import org.astermail.android.api.settings.TwinAddressResponse
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
import org.json.JSONObject
import org.astermail.android.api.labels.VerifyFolderPasswordResponse
import org.astermail.android.crypto.storage_rotation.SealedVault
import org.astermail.android.crypto.storage_rotation.absorb_rotated_vault
import org.astermail.android.folders.folder_lock_store
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FolderUnlockPinTest {

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
    private lateinit var recovery_api: org.astermail.android.api.recovery.RecoveryApi
    private lateinit var mail_rules_api: org.astermail.android.api.mail_rules.MailRulesApi
    private lateinit var keys_api: org.astermail.android.api.keys.KeysApi
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
        folder_lock_store.lock_all()
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
        recovery_api = mockk(relaxed = true)
        mail_rules_api = mockk(relaxed = true)
        keys_api = mockk(relaxed = true)
        auth_repository = mockk(relaxed = true)
        session_key_store = SessionKeyStore(null)
        session_key_store.put_user_id("user-a")
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
        every { theme_store.hide_scrollbars } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { theme_store.dyslexia_font } returns kotlinx.coroutines.flow.MutableStateFlow(false)
        every { theme_store.haptic_enabled } returns kotlinx.coroutines.flow.MutableStateFlow(true)
        context = mockk(relaxed = true)
        every { context.getString(any()) } answers { "string-" + firstArg<Int>() }
        every { context.getString(org.astermail.android.R.string.something_went_wrong) } returns
            "Something went wrong"
        vm = build_vm(session_key_store)
    }


    private fun build_vm(store: SessionKeyStore): SettingsViewModel {
        val built = SettingsViewModel(
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
            session_key_store = store,
            token_store = token_store,
            account_store = account_store,
            preferences_cache = preferences_cache,
            theme_store = theme_store,
            context = context,
        )
        built.default_dispatcher = dispatcher
        built.io_dispatcher = dispatcher
        built.bound_folder_writes_ready = { account_ready }
        return built
    }

    @After
    fun teardown() {
        folder_lock_store.lock_all()
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(android.util.Base64::class)
        Dispatchers.resetMain()
    }

    private var account_ready = false
    private val folder_id = "6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b"
    private val other_folder_id = "0a1b2c3d-4e5f-4a6b-8c7d-8e9f0a1b2c3d"

    private fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun random_bytes(size: Int): ByteArray =
        ByteArray(size).also { java.security.SecureRandom().nextBytes(it) }

    private fun read_vault_with_pin(level: Int) {
        val vault = JSONObject().put("folder_key_pins", JSONObject().put(folder_id, level))
        absorb_rotated_vault(session_key_store, vault, SealedVault(b64(random_bytes(64)), b64(random_bytes(12))))
    }

    private fun protected_label(salt: ByteArray) = LabelItem(
        id = folder_id,
        label_token = "token-$folder_id",
        is_password_protected = true,
        password_set = true,
        password_salt = b64(salt),
    )

    private fun await_result(start: ((Boolean) -> Unit) -> Unit): Boolean? {
        var result: Boolean? = null
        start { result = it }
        val deadline = System.nanoTime() + 60_000_000_000L
        while (true) {
            dispatcher.scheduler.advanceUntilIdle()
            if (result != null || System.nanoTime() > deadline) break
            Thread.sleep(10)
        }
        return result
    }

    private fun unlock(salt: ByteArray, response: VerifyFolderPasswordResponse): Boolean? {
        coEvery { labels_api.get_label(folder_id) } returns protected_label(salt)
        coEvery { labels_api.verify_folder_password(folder_id, any()) } returns response
        return await_result { done -> vm.unlock_folder(folder_id, "folder password", done) }
    }

    private fun remove(salt: ByteArray, response: VerifyFolderPasswordResponse): Boolean? {
        coEvery { labels_api.get_label(folder_id) } returns protected_label(salt)
        coEvery { labels_api.verify_folder_password(folder_id, any()) } returns response
        return await_result { done -> vm.remove_folder_lock(folder_id, "folder password", done) }
    }

    @Test
    fun a_vault_pin_stops_a_folder_falling_back_to_the_short_salt() {
        read_vault_with_pin(1)

        val result = unlock(
            random_bytes(16),
            VerifyFolderPasswordResponse(
                verified = true,
                encrypted_folder_key = b64(random_bytes(48)),
                folder_key_nonce = b64(random_bytes(12)),
                unlock_token = "unlock",
            ),
        )

        assertEquals(false, result)
        assertFalse(folder_lock_store.is_unlocked(folder_id))
    }

    @Test
    fun a_bound_folder_refuses_a_wrap_taken_from_another_folder() {
        read_vault_with_pin(2)
        val wrap_from_other_folder = b64(random_bytes(48))

        val result = unlock(
            random_bytes(32),
            VerifyFolderPasswordResponse(
                verified = true,
                encrypted_folder_key = wrap_from_other_folder,
                folder_key_nonce = b64(random_bytes(12)),
                unlock_token = "unlock-$other_folder_id",
                folder_key_version = 2,
            ),
        )

        assertEquals(false, result)
        assertFalse(folder_lock_store.is_unlocked(folder_id))
    }

    private fun message_for(name: String): String {
        val id = runCatching {
            org.astermail.android.R.string::class.java.getField(name).getInt(null)
        }.getOrDefault(-1)
        return "string-$id"
    }

    private fun v1_folder_wrap(): org.astermail.android.folders.folder_password_material =
        org.astermail.android.folders.prepare_folder_password("folder password", folder_id, false)

    private fun reply_with(material: org.astermail.android.folders.folder_password_material) =
        VerifyFolderPasswordResponse(
            verified = true,
            encrypted_folder_key = material.encrypted_folder_key,
            folder_key_nonce = material.folder_key_nonce,
            unlock_token = "unlock",
            folder_key_version = material.folder_key_version,
        )

    @Test
    fun a_v2_pin_refuses_a_folder_reset_to_v1_elsewhere_and_asks_for_a_relock() {
        read_vault_with_pin(2)
        val material = v1_folder_wrap()

        val result = unlock(java.util.Base64.getDecoder().decode(material.password_salt), reply_with(material))

        assertEquals(false, result)
        assertFalse(folder_lock_store.is_unlocked(folder_id))
        assertEquals(message_for("folder_relock_required"), vm.state.value.action_result)
    }

    @Test
    fun a_folder_reset_to_v1_elsewhere_can_be_unlocked_by_removing_and_setting_the_lock_again() {
        account_ready = true
        read_vault_with_pin(2)
        val material = v1_folder_wrap()

        val removed = remove(java.util.Base64.getDecoder().decode(material.password_salt), reply_with(material))

        assertEquals(true, removed)
        coVerify(exactly = 1) { labels_api.remove_folder_password(folder_id, any()) }
    }

    @Test
    fun removing_a_v2_pinned_folder_refuses_a_v1_wrap_the_password_does_not_open() {
        account_ready = false
        read_vault_with_pin(2)

        val removed = remove(
            random_bytes(32),
            VerifyFolderPasswordResponse(
                verified = true,
                encrypted_folder_key = b64(random_bytes(48)),
                folder_key_nonce = b64(random_bytes(12)),
                unlock_token = "unlock",
            ),
        )

        assertEquals(false, removed)
        coVerify(exactly = 0) { labels_api.remove_folder_password(folder_id, any()) }
    }

    @Test
    fun a_v2_pinned_folder_is_set_again_at_v2_while_the_account_reads_as_not_ready() {
        account_ready = false
        read_vault_with_pin(2)
        val sent = io.mockk.slot<org.astermail.android.api.labels.SetFolderPasswordRequest>()
        coEvery { labels_api.set_folder_password(folder_id, capture(sent)) } returns Unit
        coEvery { labels_api.verify_folder_password(folder_id, any()) } returns
            VerifyFolderPasswordResponse(verified = true, unlock_token = "unlock")

        val result = await_result { done -> vm.set_folder_lock(folder_id, "folder password", done) }

        assertEquals(true, result)
        assertEquals(2, sent.captured.folder_key_version)
        coVerify(exactly = 0) { auth_repository.record_folder_key_pin(folder_id, 1) }
    }

    @Test
    fun a_lock_set_whose_wrap_comes_back_different_says_it_could_not_be_confirmed() {
        coEvery { labels_api.verify_folder_password(folder_id, any()) } returns
            VerifyFolderPasswordResponse(
                verified = true,
                encrypted_folder_key = b64(random_bytes(48)),
                folder_key_nonce = b64(random_bytes(12)),
                unlock_token = "unlock",
            )

        val result = await_result { done -> vm.set_folder_lock(folder_id, "folder password", done) }

        assertEquals(true, result)
        assertEquals(message_for("folder_lock_set_unconfirmed"), vm.state.value.action_result)
    }

    @Test
    fun unreadable_folder_pins_stop_the_unlock_with_an_error() {
        val plain = HashMap<String, org.astermail.android.storage.fake_prefs>()
        val secure = HashMap<String, org.astermail.android.storage.fake_prefs>()
        val app_context = mockk<android.content.Context>(relaxed = true)
        every { app_context.applicationContext } returns app_context
        every { app_context.getSharedPreferences(any(), any()) } answers {
            plain.getOrPut(firstArg()) { org.astermail.android.storage.fake_prefs() }
        }
        io.mockk.mockkObject(org.astermail.android.storage.SecurePrefs)
        try {
            every { org.astermail.android.storage.SecurePrefs.open(any(), any()) } answers {
                secure.getOrPut(secondArg()) { org.astermail.android.storage.fake_prefs() }
            }
            plain.getOrPut("aster_key_pin_files_v1") { org.astermail.android.storage.fake_prefs() }
                .values["aster_folder_key_pins_secure"] = true
            val store = SessionKeyStore(app_context)
            store.put_user_id("user-a")
            vm = build_vm(store)

            val result = unlock(
                random_bytes(32),
                VerifyFolderPasswordResponse(
                    verified = true,
                    encrypted_folder_key = b64(random_bytes(48)),
                    folder_key_nonce = b64(random_bytes(12)),
                    unlock_token = "unlock",
                ),
            )

            assertEquals(false, result)
            assertFalse(folder_lock_store.is_unlocked(folder_id))
            assertEquals(message_for("folder_protection_unavailable"), vm.state.value.action_result)
        } finally {
            io.mockk.unmockkObject(org.astermail.android.storage.SecurePrefs)
        }
    }

    @Test
    fun setting_a_folder_password_does_not_pin_the_folder_to_v2() {
        coEvery { labels_api.verify_folder_password(folder_id, any()) } returns
            VerifyFolderPasswordResponse(verified = true, unlock_token = "unlock")

        val result = await_result { done -> vm.set_folder_lock(folder_id, "folder password", done) }

        assertEquals(true, result)
        coVerify(exactly = 0) { auth_repository.record_folder_key_pin(folder_id, 2) }
    }

    @Test
    fun an_account_that_is_not_ready_sets_the_shared_folder_format() {
        account_ready = false
        val sent = io.mockk.slot<org.astermail.android.api.labels.SetFolderPasswordRequest>()
        coEvery { labels_api.set_folder_password(folder_id, capture(sent)) } returns Unit
        coEvery { labels_api.verify_folder_password(folder_id, any()) } returns
            VerifyFolderPasswordResponse(verified = true, unlock_token = "unlock")

        val result = await_result { done -> vm.set_folder_lock(folder_id, "folder password", done) }

        assertEquals(true, result)
        assertEquals(1, sent.captured.folder_key_version)
        coVerify(exactly = 1) { auth_repository.record_folder_key_pin(folder_id, 1) }
    }

    @Test
    fun a_ready_account_sets_a_bound_folder_and_pins_it() {
        account_ready = true
        val sent = io.mockk.slot<org.astermail.android.api.labels.SetFolderPasswordRequest>()
        coEvery { labels_api.set_folder_password(folder_id, capture(sent)) } returns Unit
        coEvery { labels_api.verify_folder_password(folder_id, any()) } returns
            VerifyFolderPasswordResponse(verified = true, unlock_token = "unlock")

        val result = await_result { done -> vm.set_folder_lock(folder_id, "folder password", done) }

        assertEquals(true, result)
        assertEquals(2, sent.captured.folder_key_version)
        coVerify(exactly = 1) { auth_repository.record_folder_key_pin(folder_id, 2) }
    }

    @Test
    fun a_ready_account_refuses_v1_for_a_v2_folder() {
        account_ready = true
        read_vault_with_pin(2)

        val result = unlock(
            random_bytes(32),
            VerifyFolderPasswordResponse(
                verified = true,
                encrypted_folder_key = b64(random_bytes(48)),
                folder_key_nonce = b64(random_bytes(12)),
                unlock_token = "unlock",
            ),
        )

        assertEquals(false, result)
        assertFalse(folder_lock_store.is_unlocked(folder_id))
    }

    @Test
    fun setting_a_folder_password_does_not_open_a_wrap_it_did_not_send() {
        coEvery { labels_api.verify_folder_password(folder_id, any()) } returns
            VerifyFolderPasswordResponse(
                verified = true,
                encrypted_folder_key = b64(random_bytes(48)),
                folder_key_nonce = b64(random_bytes(12)),
                unlock_token = "unlock-$other_folder_id",
                folder_key_version = 2,
            )

        val result = await_result { done -> vm.set_folder_lock(folder_id, "folder password", done) }

        assertEquals(true, result)
        assertFalse(folder_lock_store.is_unlocked(folder_id))
    }

    @Test
    fun removing_a_bound_folder_password_checks_the_folder_wrap_first() {
        val result = remove(
            random_bytes(32),
            VerifyFolderPasswordResponse(
                verified = true,
                encrypted_folder_key = b64(random_bytes(48)),
                folder_key_nonce = b64(random_bytes(12)),
                unlock_token = "unlock-$other_folder_id",
                folder_key_version = 2,
            ),
        )

        assertEquals(false, result)
        coVerify(exactly = 0) { labels_api.remove_folder_password(folder_id, any()) }
    }

    @Test
    fun removing_a_v1_folder_password_still_works() {
        val result = remove(
            random_bytes(32),
            VerifyFolderPasswordResponse(verified = true, unlock_token = "unlock"),
        )

        assertEquals(true, result)
        coVerify(exactly = 1) { labels_api.remove_folder_password(folder_id, any()) }
    }

    @Test
    fun an_unpinned_folder_with_the_current_salt_still_unlocks() {
        val result = unlock(
            random_bytes(32),
            VerifyFolderPasswordResponse(
                verified = true,
                unlock_token = "unlock",
            ),
        )

        assertEquals(true, result)
        assertTrue(folder_lock_store.is_unlocked(folder_id))
    }
}
