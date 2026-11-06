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

package org.astermail.android.crypto.storage_rotation

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.astermail.android.api.ApiClient
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.StorageWriteKey
import org.astermail.android.mail.ratchet.collect_vault_key_fingerprints
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject

object VaultWriteLock {
    val mutex = Mutex()
}

private const val PROGRESS_PREFS_NAME = "storage_rekey_progress"
private const val PROGRESS_KEY_PREFIX = "progress_"
private const val KEY_MATERIAL_ITERATIONS = 310000
private const val MAX_KEY_MATERIAL_SALTS = 4

class PrefsProgressStore(
    private val prefs: SharedPreferences?,
    private val user_id: String,
) : ProgressStore {
    private val key = PROGRESS_KEY_PREFIX + user_id

    override fun load(): ResealProgress? = decode_progress(prefs?.getString(key, null))

    override fun save(progress: ResealProgress) {
        prefs?.edit()?.putString(key, encode_progress(progress))?.apply()
    }

    override fun clear() {
        prefs?.edit()?.remove(key)?.apply()
    }
}

fun storage_password_keys(
    passphrase: ByteArray,
    salts: List<ByteArray>,
    derive_v1: (ByteArray) -> ByteArray? = {
        StorageWriteKey.password_storage_key(it, CryptoNative.legacy_storage_kdf_version)
    },
    derive_v2: (ByteArray) -> ByteArray? = {
        StorageWriteKey.password_storage_key(it, CryptoNative.stretched_storage_kdf_version)
    },
    derive_key_material: (ByteArray, ByteArray) -> ByteArray = { pass, salt ->
        CryptoNative.derive_pbkdf2_hash(pass, salt, KEY_MATERIAL_ITERATIONS)
    },
): List<ByteArray> {
    val keys = ArrayList<ByteArray>()
    runCatching { derive_v1(passphrase) }.getOrNull()?.let(keys::add)
    runCatching { derive_v2(passphrase) }.getOrNull()?.let(keys::add)
    for (salt in salts.take(MAX_KEY_MATERIAL_SALTS)) {
        if (salt.isEmpty()) continue
        runCatching { derive_key_material(passphrase, salt) }.getOrNull()?.let(keys::add)
    }
    return keys.filter { raw ->
        val keep = raw.size == RESEAL_KEY_LENGTH
        if (!keep) raw.fill(0)
        keep
    }
}

fun absorb_rotated_vault(store: SessionKeyStore, vault: JSONObject, sealed: SealedVault) {
    store.put_encrypted_vault(sealed.encrypted_vault, sealed.vault_nonce)
    val data_kek = vault.optString("data_kek", "")
    val incoming = ArrayList<String>()
    if (data_kek.isNotBlank()) {
        decode_b64(data_kek)?.let { raw ->
            if (raw.size == RESEAL_KEY_LENGTH) store.put_data_kek(raw)
            raw.fill(0)
        }
        incoming.add(data_kek)
    }
    val legacy = vault.optJSONArray("legacy_keks")
    for (i in 0 until (legacy?.length() ?: 0)) {
        val value = legacy?.optJSONObject(i)?.optString("k", "").orEmpty()
        if (value.isNotBlank()) incoming.add(value)
    }
    val current = store.get_legacy_keks().orEmpty()
    val merged = ArrayList<String>()
    for (value in incoming + current) {
        if (value.isNotBlank() && value !in merged) merged.add(value)
    }
    if (merged != current) store.put_legacy_keks(merged)
}

fun local_storage_keys(store: SessionKeyStore): LocalStorageKeys {
    val data_kek = store.get_data_kek()
    val encoded = data_kek?.takeIf { it.size == RESEAL_KEY_LENGTH }?.let(::encode_b64)
    data_kek?.fill(0)
    return LocalStorageKeys(encoded, store.get_legacy_keks().orEmpty())
}

class SessionVaultCodec(
    private val store: SessionKeyStore,
    private val passphrase_source: () -> ByteArray? = { store.get_passphrase() },
) : StorageVaultCodec {

    private fun passphrase(): ByteArray = passphrase_source() ?: throw IllegalStateException("locked")

    override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? {
        val pass = passphrase()
        val plain = try {
            CryptoNative.decrypt_vault_with_password(
                decode_b64(encrypted_vault) ?: return null,
                decode_b64(vault_nonce) ?: return null,
                pass,
            )
        } finally {
            pass.fill(0)
        }
        return try {
            JSONObject(String(plain, Charsets.UTF_8))
        } finally {
            plain.fill(0)
        }
    }

    override fun encrypt(vault: JSONObject): SealedVault? {
        val pass = passphrase()
        val plain = vault.toString().toByteArray(Charsets.UTF_8)
        return try {
            val envelope = CryptoNative.encrypt_vault_with_password(plain, pass)
            SealedVault(encode_b64(envelope.encrypted_vault), encode_b64(envelope.vault_nonce))
        } finally {
            plain.fill(0)
            pass.fill(0)
        }
    }

    override fun password_keys(vault: JSONObject): List<ByteArray> {
        val pass = passphrase()
        val salts = listOfNotNull(store.get_password_salt()) + store.get_retired_password_salts()
        return try {
            storage_password_keys(pass, salts)
        } finally {
            pass.fill(0)
            salts.forEach { it.fill(0) }
        }
    }

    override fun generate_key(): ByteArray = StorageWriteKey.random_data_kek()

    override fun fingerprints(vault: JSONObject): List<String> = collect_vault_key_fingerprints(vault)
}

fun session_vault_updater(
    api: ResealApi,
    store: SessionKeyStore,
    passphrase_source: () -> ByteArray? = { store.get_passphrase() },
): VaultUpdater = VaultUpdater(
    api = api,
    codec = SessionVaultCodec(store, passphrase_source),
    user_id = { store.get_user_id() },
    lock = VaultWriteLock.mutex,
    on_vault_changed = { vault, sealed -> absorb_rotated_vault(store, vault, sealed) },
    local_keys = { local_storage_keys(store) },
)

@Singleton
class VaultUpdates @Inject constructor(
    private val api_client: ApiClient,
    private val session_key_store: SessionKeyStore,
) {
    fun session(): VaultUpdater = session_vault_updater(KtorResealApi(api_client), session_key_store)

    fun with_passphrase(passphrase: ByteArray): VaultUpdater =
        session_vault_updater(KtorResealApi(api_client), session_key_store) { passphrase.copyOf() }
}

@Singleton
class StorageRekeyService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api_client: ApiClient,
    private val session_key_store: SessionKeyStore,
) {
    private val prefs: SharedPreferences? by lazy {
        runCatching { SecurePrefs.open(context, PROGRESS_PREFS_NAME) }.getOrNull()
    }

    private fun rotator(api: ResealApi): StorageVaultRotator = StorageVaultRotator(
        api = api,
        codec = SessionVaultCodec(session_key_store),
        user_id = { session_key_store.get_user_id() },
        lock = VaultWriteLock.mutex,
        on_vault_changed = { vault, sealed -> absorb_rotated_vault(session_key_store, vault, sealed) },
        local_keys = { local_storage_keys(session_key_store) },
    )

    fun is_unlocked(): Boolean {
        val pass = session_key_store.get_passphrase() ?: return false
        pass.fill(0)
        return session_key_store.get_user_id() != null
    }

    suspend fun ensure_random_storage_key(): StorageVaultOutcome = withContext(Dispatchers.Default) {
        if (!is_unlocked()) return@withContext StorageVaultOutcome.Skipped("locked")
        rotator(KtorResealApi(api_client)).ensure_random_storage_key()
    }

    suspend fun run(is_cancelled: () -> Boolean = { false }): ResealRunResult = withContext(Dispatchers.Default) {
        val user_id = session_key_store.get_user_id()
        if (user_id == null || !is_unlocked()) {
            return@withContext ResealRunResult(ResealRunStatus.NOT_NEEDED, StepCounts(), "locked")
        }
        val api = KtorResealApi(api_client)
        val vault_rotator = rotator(api)
        val view = when (val outcome = vault_rotator.ensure_random_storage_key()) {
            is StorageVaultOutcome.Rotated -> VaultKeyView.of(outcome.vault)
            is StorageVaultOutcome.Adopted -> VaultKeyView.of(outcome.vault)
            is StorageVaultOutcome.Current -> VaultKeyView.of(outcome.vault)
            is StorageVaultOutcome.Skipped ->
                return@withContext ResealRunResult(ResealRunStatus.INTERRUPTED, StepCounts(), outcome.reason)
        }
        val extra_old = session_key_store.get_legacy_keks().orEmpty().mapNotNull { encoded ->
            decode_b64(encoded)?.takeIf { it.size == RESEAL_KEY_LENGTH }
        }
        val expected = view.data_kek.orEmpty()
        try {
            run_storage_reseal(
                view = view,
                api = api,
                store = PrefsProgressStore(prefs, user_id),
                finish = { data_kek -> vault_rotator.mark_storage_rekey_done(data_kek) },
                still_current = { vault_rotator.server_storage_key_matches(expected) },
                is_cancelled = is_cancelled,
                extra_old_keys = extra_old,
            )
        } finally {
            extra_old.forEach { it.fill(0) }
        }
    }
}
