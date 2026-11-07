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

package org.astermail.android.auth

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import org.astermail.android.api.keys.CurrentVaultResult
import org.astermail.android.crypto.AccountKey
import org.json.JSONObject

private const val PGP_PRIVATE_KEY_PREFIX = "-----BEGIN PGP PRIVATE KEY"
private const val MISSING_IDENTITY_KEY_MATERIAL = "undefined"

val MISSING_IDENTITY_KEK_CONTEXTS: List<String> = AccountKey.DATA_CONTEXTS + "astermail-calendar-v2"

data class SealedVault(val encrypted_vault: String, val vault_nonce: String)

interface IdentityKeyInstallPort {
    suspend fun fetch_server_vault(): CurrentVaultResult
    fun open_vault(sealed: SealedVault): JSONObject?
    fun generate_identity_key(): String?
    fun seal_vault(vault: JSONObject): SealedVault?
    suspend fun save_vault(sealed: SealedVault, vault: JSONObject): Boolean
}

sealed interface IdentityKeyInstallResult {
    data class Installed(
        val vault: JSONObject,
        val sealed: SealedVault,
        val identity_key: String,
    ) : IdentityKeyInstallResult

    data class AlreadyInstalled(
        val vault: JSONObject,
        val sealed: SealedVault,
    ) : IdentityKeyInstallResult

    data object NotWritten : IdentityKeyInstallResult
}

fun is_pgp_private_key(value: String?): Boolean =
    value?.trimStart()?.startsWith(PGP_PRIVATE_KEY_PREFIX) == true

fun vault_holds_pgp_identity_key(vault: JSONObject): Boolean =
    is_pgp_private_key(vault_identity_key(vault))

fun missing_identity_keks(): List<String> =
    MISSING_IDENTITY_KEK_CONTEXTS.map { context ->
        val material = (MISSING_IDENTITY_KEY_MATERIAL + context).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        try {
            Base64.getEncoder().encodeToString(digest)
        } finally {
            digest.fill(0)
        }
    }

fun install_identity_key_into_vault(vault: JSONObject, identity_key: String, added_at: String) {
    var legacy_keks = vault.optJSONArray("legacy_keks")
    for (kek in missing_identity_keks()) {
        legacy_keks = prepend_legacy_kek(legacy_keks, kek, added_at)
    }
    vault.put("legacy_keks", legacy_keks)
    replace_vault_identity_key(vault, identity_key)
}

suspend fun install_missing_identity_key(
    port: IdentityKeyInstallPort,
    now: () -> String = { Instant.now().toString() },
): IdentityKeyInstallResult {
    val server_vault = port.fetch_server_vault() as? CurrentVaultResult.Available
        ?: return IdentityKeyInstallResult.NotWritten
    val current = SealedVault(server_vault.encrypted_vault, server_vault.vault_nonce)
    val vault = port.open_vault(current) ?: return IdentityKeyInstallResult.NotWritten
    if (vault_holds_pgp_identity_key(vault)) {
        return IdentityKeyInstallResult.AlreadyInstalled(vault, current)
    }

    val identity_key = port.generate_identity_key()
        ?.takeIf { is_pgp_private_key(it) }
        ?: return IdentityKeyInstallResult.NotWritten
    install_identity_key_into_vault(vault, identity_key, now())

    val sealed = port.seal_vault(vault) ?: return IdentityKeyInstallResult.NotWritten
    if (!port.save_vault(sealed, vault)) return IdentityKeyInstallResult.NotWritten
    return IdentityKeyInstallResult.Installed(vault, sealed, identity_key)
}
