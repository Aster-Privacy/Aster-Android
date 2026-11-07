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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.astermail.android.api.ApiClient
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.StorageWriteKey
import org.astermail.android.crypto.VaultKdf
import org.astermail.android.mail.ratchet.collect_vault_key_fingerprints
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

object VaultWriteLock {
    val mutex = Mutex()
}

private const val PROGRESS_PREFS_NAME = "storage_rekey_progress"
private const val PROGRESS_KEY_PREFIX = "progress_"
private const val KEY_MATERIAL_ITERATIONS = 310000
private const val MAX_KEY_MATERIAL_SALTS = 4
private const val BACKUP_REFRESH_KEY_PREFIX = "backup_refresh_"

private const val FOLDER_CURSOR_KEY_PREFIX = "unlocked_folder_cursor_"

class PrefsFolderRekeyCursorStore(
    private val prefs: SharedPreferences?,
    private val user_id: () -> String?,
) : FolderRekeyCursorStore {
    private fun key(): String? = user_id()?.let { FOLDER_CURSOR_KEY_PREFIX + it }

    private fun read(key: String): Map<String, FolderRekeyCursor> = decode_folder_cursors(prefs?.getString(key, null))

    @Synchronized
    override fun load(folder_id: String): FolderRekeyCursor? {
        val key = key() ?: return null
        return read(key)[folder_id]
    }

    @Synchronized
    override fun save(folder_id: String, cursor: FolderRekeyCursor) {
        val key = key() ?: return
        val next = read(key).toMutableMap()
        if (cursor == FolderRekeyCursor()) next.remove(folder_id) else next[folder_id] = cursor
        val editor = prefs?.edit() ?: return
        if (next.isEmpty()) editor.remove(key) else editor.putString(key, encode_folder_cursors(next))
        editor.apply()
    }

    @Synchronized
    override fun clear() {
        val key = key() ?: return
        prefs?.edit()?.remove(key)?.apply()
    }
}

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
    store.put_encrypted_vault(sealed.encrypted_vault, sealed.vault_nonce, sealed.kdf_iterations)
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

    override fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject? =
        decrypt_at(encrypted_vault, vault_nonce, VaultKdf.DEFAULT_ITERATIONS)

    override fun encrypt(vault: JSONObject): SealedVault? = encrypt_at(vault, VaultKdf.DEFAULT_ITERATIONS)

    override fun decrypt_at(encrypted_vault: String, vault_nonce: String, iterations: Int): JSONObject? {
        val pass = passphrase()
        val plain = try {
            CryptoNative.decrypt_vault_with_password(
                decode_b64(encrypted_vault) ?: return null,
                decode_b64(vault_nonce) ?: return null,
                pass,
                iterations,
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

    override fun encrypt_at(vault: JSONObject, iterations: Int): SealedVault? {
        val pass = passphrase()
        val plain = vault.toString().toByteArray(Charsets.UTF_8)
        return try {
            val envelope = CryptoNative.encrypt_vault_with_password(plain, pass, iterations)
            SealedVault(encode_b64(envelope.encrypted_vault), encode_b64(envelope.vault_nonce), iterations)
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

    suspend fun update_identity(
        updater: VaultUpdater,
        edit: suspend (VaultDraft) -> VaultEdit,
    ): VaultUpdateOutcome = update_identity_with_side_copy(
        updater,
        KtorResealApi(api_client),
        PgpStorageKeyWrapCrypto { session_key_store.get_passphrase() },
        edit,
    )
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

    private val healed_users = java.util.Collections.synchronizedSet(HashSet<String>())
    private val side_copied = java.util.Collections.synchronizedSet(HashSet<String>())

    companion object {
        @Volatile private var backup_builder: RecoveryBackupBuilder? = null

        fun install_backup_builder(builder: RecoveryBackupBuilder?) {
            backup_builder = builder
        }
    }

    private val folder_queue by lazy {
        UnlockedFolderRekeyQueue(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            session_marker = { org.astermail.android.folders.folder_lock_store.unlock_session_marker(it) },
            run_batch = { items, still_unlocked, max_items -> rekey_unlocked_items(items, still_unlocked, max_items) },
            fetch_page = { folder_id, offset -> fetch_unlocked_folder_page(folder_id, offset) },
            cursors = PrefsFolderRekeyCursorStore(prefs) { session_key_store.get_user_id() },
        )
    }

    fun forget_session() {
        healed_users.clear()
        side_copied.clear()
        folder_queue.forget()
    }

    fun offer_unlocked_folder_items(folder_id: String, items: List<JsonObject>) {
        if (!session_key_store.is_level2_admitted() || !is_unlocked()) return
        folder_queue.offer(folder_id, items)
    }

    private suspend fun rekey_unlocked_items(
        items: List<JsonObject>,
        still_unlocked: () -> Boolean,
        max_items: Int,
    ): ResealRunResult {
        val ready = { still_unlocked() && is_unlocked() && session_key_store.is_level2_admitted() }
        if (!ready()) return ResealRunResult(ResealRunStatus.NOT_NEEDED, StepCounts(), "locked")
        return run_unlocked_folder_rekey(
            keys_source = { session_reseal_keys() },
            api = GatedResealApi(KtorResealApi(api_client), sleep = { delay(it) }, is_cancelled = { !ready() }),
            crypto = SessionEnvelopeV2Crypto(session_key_store),
            items = items,
            still_unlocked = ready,
            draft_in_use = { org.astermail.android.mail.LocalDraftActivity.is_in_use(it) },
            max_items = max_items,
        )
    }

    private suspend fun fetch_unlocked_folder_page(folder_id: String, offset: Int): FolderRekeyPage? {
        if (!is_unlocked() || !session_key_store.is_level2_admitted()) return null
        val token = org.astermail.android.folders.folder_lock_store.unlocked_folder_label_token(folder_id) ?: return null
        val reply = KtorResealApi(api_client).get(unlocked_folder_page_path(token, offset))
        if (reply.status != 200) return null
        val data = reply.data as? JsonObject ?: return null
        return FolderRekeyPage(items = data.objects("items"), has_more = data.bool("has_more") == true)
    }

    private fun session_reseal_keys(): ResealKeys? {
        val raw = session_key_store.get_data_kek() ?: return null
        if (raw.size != RESEAL_KEY_LENGTH) {
            raw.fill(0)
            return null
        }
        return ResealKeys(raw, emptyList())
    }

    private fun wrap_crypto(): StorageKeyWrapCrypto =
        PgpStorageKeyWrapCrypto { session_key_store.get_passphrase() }

    private suspend fun heal_once(api: ResealApi, force: Boolean) {
        val user_id = session_key_store.get_user_id() ?: return
        if (!force && user_id in healed_users) return
        val result = heal_storage_key_from_side_copy(api, session_vault_updater(api, session_key_store), wrap_crypto())
        if (result !is StorageKeyHeal.Skipped) healed_users.add(user_id)
    }

    private suspend fun keep_side_copy(api: ResealApi, vault: JSONObject) {
        val user_id = session_key_store.get_user_id() ?: return
        val key = vault_random_storage_key(vault) ?: return
        val marker = user_id + ":" + wrap_key_id(key) + ":" + vault_identity_marker(vault).hashCode()
        key.fill(0)
        if (marker in side_copied) return
        val wraps = runCatching { list_storage_key_wraps(api) }.getOrNull() ?: return
        val stored = runCatching { store_side_copy(api, wrap_crypto(), vault, wraps) }.getOrNull()
        if (stored == SideCopyStore.STORED || stored == SideCopyStore.CURRENT) side_copied.add(marker)
    }

    private suspend fun refresh_backup_for(api: ResealApi, vault: JSONObject) {
        val builder = backup_builder ?: return
        if (!needs_storage_reseal(VaultKeyView.of(vault))) return
        val user_id = session_key_store.get_user_id() ?: return
        val key = vault_random_storage_key(vault) ?: return
        val key_id = wrap_key_id(key)
        key.fill(0)
        val marker_key = BACKUP_REFRESH_KEY_PREFIX + user_id
        if (prefs?.getString(marker_key, null) == key_id) return
        val updater = session_vault_updater(api, session_key_store)
        val result = runCatching { refresh_recovery_backup(api, { updater.latest() }, builder) }.getOrNull()
        when (result) {
            BackupRefresh.REFRESHED, BackupRefresh.NO_CODES, BackupRefresh.CODES_CHANGED ->
                prefs?.edit()?.putString(marker_key, key_id)?.apply()
            else -> Unit
        }
    }

    private fun outcome_vault(outcome: StorageVaultOutcome): JSONObject? = when (outcome) {
        is StorageVaultOutcome.Rotated -> outcome.vault
        is StorageVaultOutcome.Adopted -> outcome.vault
        is StorageVaultOutcome.Current -> outcome.vault
        is StorageVaultOutcome.Skipped -> null
    }

    private fun rotator(api: ResealApi): StorageVaultRotator = StorageVaultRotator(
        api = api,
        codec = SessionVaultCodec(session_key_store),
        user_id = { session_key_store.get_user_id() },
        lock = VaultWriteLock.mutex,
        on_vault_changed = { vault, sealed -> absorb_rotated_vault(session_key_store, vault, sealed) },
        local_keys = { local_storage_keys(session_key_store) },
        upgrade_on_rotation = true,
    )

    fun is_unlocked(): Boolean {
        val pass = session_key_store.get_passphrase() ?: return false
        pass.fill(0)
        return session_key_store.get_user_id() != null
    }

    suspend fun ensure_random_storage_key(): StorageVaultOutcome = withContext(Dispatchers.Default) {
        if (!is_unlocked()) return@withContext StorageVaultOutcome.Skipped("locked")
        val api = KtorResealApi(api_client)
        runCatching { heal_once(api, force = false) }
        val outcome = rotator(api).ensure_random_storage_key()
        outcome_vault(outcome)?.let { vault -> runCatching { keep_side_copy(api, vault) } }
        outcome
    }

    suspend fun run(is_cancelled: () -> Boolean = { false }): ResealRunResult = withContext(Dispatchers.Default) {
        val user_id = session_key_store.get_user_id()
        if (user_id == null || !is_unlocked()) {
            return@withContext ResealRunResult(ResealRunStatus.NOT_NEEDED, StepCounts(), "locked")
        }
        val api = KtorResealApi(api_client)
        runCatching { heal_once(api, force = true) }
        val vault_rotator = rotator(api)
        val outcome = vault_rotator.ensure_random_storage_key()
        val vault = outcome_vault(outcome)
            ?: return@withContext ResealRunResult(
                ResealRunStatus.INTERRUPTED,
                StepCounts(),
                (outcome as? StorageVaultOutcome.Skipped)?.reason,
            )
        runCatching { keep_side_copy(api, vault) }
        runCatching { refresh_backup_for(api, vault) }
        val view = VaultKeyView.of(vault)
        val extra_old = session_key_store.get_legacy_keks().orEmpty().mapNotNull { encoded ->
            decode_b64(encoded)?.takeIf { it.size == RESEAL_KEY_LENGTH }
        }
        val expected = view.data_kek.orEmpty()
        val reseal = try {
            run_storage_reseal(
                view = view,
                api = GatedResealApi(api, sleep = { delay(it) }, is_cancelled = is_cancelled),
                store = PrefsProgressStore(prefs, user_id),
                finish = { data_kek -> vault_rotator.mark_storage_rekey_done(data_kek) },
                still_current = { vault_rotator.server_storage_key_matches(expected) },
                is_cancelled = is_cancelled,
                extra_old_keys = extra_old,
                gate = { check_rekey_gate(api) },
            )
        } finally {
            extra_old.forEach { it.fill(0) }
        }
        if (reseal.status != ResealRunStatus.NOT_NEEDED && reseal.status != ResealRunStatus.DONE) {
            return@withContext reseal
        }
        if (!view.data_kek_random || !local_key_matches(view)) return@withContext reseal
        val admitted = request_level2_admission(api)
        session_key_store.set_level2_admitted(admitted)
        if (!admitted) return@withContext reseal
        run_vault_work_factor_step(
            admitted = admitted,
            password_ready = { is_unlocked() },
            store = PrefsProgressStore(prefs, VAULT_WORK_FACTOR_STEP + "_" + user_id),
            raise = { session_vault_updater(api, session_key_store).raise_work_factor() },
        )
        val sweep = run_envelope_v2_sweep(
            view = view,
            api = GatedResealApi(api, sleep = { delay(it) }, is_cancelled = is_cancelled),
            store = PrefsProgressStore(prefs, ENVELOPE_V2_STEP + "_" + user_id),
            crypto = SessionEnvelopeV2Crypto(session_key_store),
            is_cancelled = is_cancelled,
            draft_in_use = { org.astermail.android.mail.LocalDraftActivity.is_in_use(it) },
        )
        when (sweep.status) {
            ResealRunStatus.INTERRUPTED, ResealRunStatus.DEFERRED, ResealRunStatus.FAILED -> sweep
            else -> reseal
        }
    }

    private fun local_key_matches(view: VaultKeyView): Boolean {
        val server = decode_b64(view.data_kek.orEmpty()) ?: return false
        val local = session_key_store.get_data_kek()
        if (local == null) {
            server.fill(0)
            return false
        }
        val same = java.security.MessageDigest.isEqual(server, local)
        server.fill(0)
        local.fill(0)
        return same
    }
}
