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

package org.astermail.android.contacts

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import org.astermail.android.api.contacts.BulkDeleteContactsRequest
import org.astermail.android.api.contacts.CONTACT_DATA_VERSION
import org.astermail.android.api.contacts.ContactGroupEncrypted
import org.astermail.android.api.contacts.ContactItem
import org.astermail.android.api.contacts.ContactsApi
import org.astermail.android.api.contacts.CreateContactGroupRequest
import org.astermail.android.api.contacts.CreateContactGroupResponse
import org.astermail.android.api.contacts.CreateContactRequest
import org.astermail.android.api.contacts.CreateContactResponse
import org.astermail.android.api.contacts.DeleteContactResponse
import org.astermail.android.api.contacts.SuccessResponse
import org.astermail.android.api.contacts.UpdateContactRequest
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.ui.contacts.Contact

data class ContactGroup(
    val id: String,
    val name: String,
    val color: String,
    val contact_count: Int,
    val created_at: String? = null,
)

@Singleton
class ContactsRepository @Inject constructor(
    private val contacts_api: ContactsApi,
    private val session_key_store: SessionKeyStore,
) {
    suspend fun fetch_contacts(group_id: String? = null): Result<List<Contact>> = runCatching {
        val all = mutableListOf<ContactItem>()
        var cursor: String? = null
        do {
            val page = contacts_api.list_contacts(limit = 100, cursor = cursor, group_id = group_id)
            all.addAll(page.items)
            cursor = page.next_cursor
        } while (page.has_more && cursor != null)
        all.mapNotNull { decrypt_contact(it) }
    }

    suspend fun fetch_contact(contact_id: String): Result<Contact> = runCatching {
        val item = contacts_api.get_contact(contact_id)
        decrypt_contact(item) ?: throw IllegalStateException("failed to decrypt contact")
    }

    suspend fun get_count(): Result<Int> = runCatching {
        contacts_api.get_contacts_count().count
    }

    suspend fun create_contact(contact: Contact): Result<CreateContactResponse> = runCatching {
        val key = derive_contacts_key()
        try {
            val request = build_create_request(contact, key)
            contacts_api.create_contact(request)
        } finally {
            key.fill(0)
        }
    }

    suspend fun update_contact(contact_id: String, contact: Contact): Result<Unit> = runCatching {
        val key = derive_contacts_key()
        try {
            val request = build_update_request(contact, key)
            contacts_api.update_contact(contact_id, request)
        } finally {
            key.fill(0)
        }
    }

    suspend fun delete_contact(contact_id: String): Result<DeleteContactResponse> = runCatching {
        contacts_api.delete_contact(contact_id)
    }

    suspend fun bulk_delete_contacts(ids: List<String>): Result<DeleteContactResponse> = runCatching {
        contacts_api.bulk_delete_contacts(BulkDeleteContactsRequest(ids))
    }

    suspend fun search_contacts(query: String, field: String = "all", limit: Int? = null): Result<List<Contact>> = runCatching {
        val key = derive_contacts_key()
        val token = try {
            generate_search_token(query, key)
        } finally {
            key.fill(0)
        }
        val response = contacts_api.search_contacts(token, field, limit)
        response.items.mapNotNull { decrypt_contact(it) }
    }

    suspend fun list_contact_groups(): Result<List<ContactGroup>> = runCatching {
        val response = contacts_api.list_contact_groups()
        response.groups.mapNotNull { decrypt_group(it) }
    }

    suspend fun create_contact_group(name: String, color: String): Result<CreateContactGroupResponse> = runCatching {
        val key = derive_contacts_key()
        try {
            val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val ciphertext = aes_gcm_encrypt(name.toByteArray(Charsets.UTF_8), key, nonce)
            val group_token = generate_search_token(name, key)
            contacts_api.create_contact_group(
                CreateContactGroupRequest(
                    group_token = group_token,
                    encrypted_name = b64(ciphertext),
                    name_nonce = b64(nonce),
                    color = color,
                ),
            )
        } finally {
            key.fill(0)
        }
    }

    suspend fun delete_contact_group(group_id: String): Result<SuccessResponse> = runCatching {
        contacts_api.delete_contact_group(group_id)
    }

    suspend fun add_contact_to_group(contact_id: String, group_id: String): Result<SuccessResponse> = runCatching {
        contacts_api.add_contact_to_group(contact_id, group_id)
    }

    suspend fun remove_contact_from_group(contact_id: String, group_id: String): Result<SuccessResponse> = runCatching {
        contacts_api.remove_contact_from_group(contact_id, group_id)
    }

    private fun build_create_request(contact: Contact, key: ByteArray): CreateContactRequest {
        val payload = encode_contact_json(contact, include_envelope = true)
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val ciphertext = aes_gcm_encrypt(payload.toByteArray(Charsets.UTF_8), key, nonce)
        val encrypted_data = b64(ciphertext)
        val data_nonce = b64(nonce)
        val integrity_hash = generate_integrity_hash(encrypted_data, data_nonce, CONTACT_DATA_VERSION, key)
        val contact_token = generate_contact_token(contact, key)
        return CreateContactRequest(
            contact_token = contact_token,
            encrypted_data = encrypted_data,
            data_nonce = data_nonce,
            integrity_hash = integrity_hash,
            data_version = CONTACT_DATA_VERSION,
            name_search_token = generate_name_token(contact, key),
            email_search_token = generate_email_token(contact, key),
            company_search_token = generate_company_token(contact, key),
        )
    }

    private fun build_update_request(contact: Contact, key: ByteArray): UpdateContactRequest {
        val payload = encode_contact_json(contact, include_envelope = true)
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val ciphertext = aes_gcm_encrypt(payload.toByteArray(Charsets.UTF_8), key, nonce)
        val encrypted_data = b64(ciphertext)
        val data_nonce = b64(nonce)
        val integrity_hash = generate_integrity_hash(encrypted_data, data_nonce, CONTACT_DATA_VERSION, key)
        return UpdateContactRequest(
            encrypted_data = encrypted_data,
            data_nonce = data_nonce,
            integrity_hash = integrity_hash,
            name_search_token = generate_name_token(contact, key),
            email_search_token = generate_email_token(contact, key),
            company_search_token = generate_company_token(contact, key),
        )
    }

    private fun decrypt_contact(item: ContactItem): Contact? {
        val encrypted_data = item.encrypted_data ?: return null
        val data_nonce = item.data_nonce ?: return null
        return try {
            val ciphertext = android.util.Base64.decode(encrypted_data, android.util.Base64.DEFAULT)
            val nonce = android.util.Base64.decode(data_nonce, android.util.Base64.DEFAULT)
            val key = derive_contacts_key()
            val decrypted = try {
                val ih = item.integrity_hash
                val dv = item.data_version
                if (ih != null && dv != null) {
                    if (!verify_integrity_hash(encrypted_data, data_nonce, ih, dv, key)) {
                        throw IllegalStateException("integrity check failed")
                    }
                }
                aes_gcm_decrypt(ciphertext, key, nonce)
            } finally {
                key.fill(0)
            }
            val json_str = String(decrypted, Charsets.UTF_8)
            decrypted.fill(0)
            parse_contact_json(item.id, json_str)
        } catch (_: Throwable) {
            try {
                decrypt_contact_identity_fallback(item)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun decrypt_contact_identity_fallback(item: ContactItem): Contact? {
        val encrypted_data = item.encrypted_data ?: return null
        val data_nonce = item.data_nonce ?: return null
        val identity_key = session_key_store.get_identity_key() ?: return null
        val ciphertext = android.util.Base64.decode(encrypted_data, android.util.Base64.DEFAULT)
        val nonce = android.util.Base64.decode(data_nonce, android.util.Base64.DEFAULT)

        for (version in ENVELOPE_VERSIONS) {
            try {
                val material = (identity_key + version).toByteArray(Charsets.UTF_8)
                val key = MessageDigest.getInstance("SHA-256").digest(material)
                val decrypted = aes_gcm_decrypt(ciphertext, key, nonce)
                val json_str = String(decrypted, Charsets.UTF_8)
                decrypted.fill(0)
                return parse_contact_json(item.id, json_str)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun decrypt_group(group: ContactGroupEncrypted): ContactGroup? {
        return try {
            val ciphertext = android.util.Base64.decode(group.encrypted_name, android.util.Base64.DEFAULT)
            val nonce = android.util.Base64.decode(group.name_nonce, android.util.Base64.DEFAULT)
            val key = derive_contacts_key()
            val name_bytes = try {
                aes_gcm_decrypt(ciphertext, key, nonce)
            } finally {
                key.fill(0)
            }
            val name = String(name_bytes, Charsets.UTF_8)
            name_bytes.fill(0)
            ContactGroup(
                id = group.id,
                name = name,
                color = group.color,
                contact_count = group.contact_count,
                created_at = group.created_at,
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun derive_contacts_key(): ByteArray {
        val passphrase = session_key_store.get_passphrase()
            ?: throw IllegalStateException("no passphrase")
        try {
            val prefix = SALT_PREFIX.toByteArray(Charsets.UTF_8)
            val salt_input = ByteArray(prefix.size + passphrase.size)
            System.arraycopy(prefix, 0, salt_input, 0, prefix.size)
            System.arraycopy(passphrase, 0, salt_input, prefix.size, passphrase.size)
            val salt = MessageDigest.getInstance("SHA-256").digest(salt_input)
            salt_input.fill(0)

            val info = DERIVED_KEY_INFO.toByteArray(Charsets.UTF_8)
            return hkdf_sha256(passphrase, salt, info, 32)
        } finally {
            passphrase.fill(0)
        }
    }

    private fun derive_subkey(raw_key: ByteArray, info: String): ByteArray {
        val info_bytes = info.toByteArray(Charsets.UTF_8)
        val combined = ByteArray(raw_key.size + info_bytes.size)
        System.arraycopy(raw_key, 0, combined, 0, raw_key.size)
        System.arraycopy(info_bytes, 0, combined, raw_key.size, info_bytes.size)
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)
        combined.fill(0)
        return digest
    }

    private fun hmac_sha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun generate_search_token(value: String, raw_key: ByteArray): String {
        val sub = derive_subkey(raw_key, SEARCH_INFO)
        try {
            val normalized = value.lowercase().trim()
            val hash = hmac_sha256(sub, normalized.toByteArray(Charsets.UTF_8))
            return b64(hash)
        } finally {
            sub.fill(0)
        }
    }

    private fun generate_contact_token(contact: Contact, raw_key: ByteArray): String {
        val sub = derive_subkey(raw_key, HMAC_INFO)
        try {
            val (first, last) = split_name(contact.name)
            val emails = listOf(contact.email, contact.work_email).filter { it.isNotBlank() }
            val searchable = "$first $last ${emails.joinToString(" ")}".lowercase()
            val hash = hmac_sha256(sub, searchable.toByteArray(Charsets.UTF_8))
            return b64(hash)
        } finally {
            sub.fill(0)
        }
    }

    private fun generate_name_token(contact: Contact, raw_key: ByteArray): String? {
        val full = contact.name.trim()
        if (full.isBlank()) return null
        return generate_search_token(full, raw_key)
    }

    private fun generate_email_token(contact: Contact, raw_key: ByteArray): String? {
        val primary = contact.email
        if (primary.isBlank()) return null
        return generate_search_token(primary, raw_key)
    }

    private fun generate_company_token(contact: Contact, raw_key: ByteArray): String? {
        if (contact.company.isBlank()) return null
        return generate_search_token(contact.company, raw_key)
    }

    private fun generate_integrity_hash(encrypted_data: String, nonce: String, version: Int, raw_key: ByteArray): String {
        val sub = derive_subkey(raw_key, HMAC_INFO)
        try {
            val combined = "$encrypted_data:$nonce:$version"
            val hash = hmac_sha256(sub, combined.toByteArray(Charsets.UTF_8))
            return b64(hash)
        } finally {
            sub.fill(0)
        }
    }

    private fun verify_integrity_hash(encrypted_data: String, nonce: String, expected: String, version: Int, raw_key: ByteArray): Boolean {
        val computed = generate_integrity_hash(encrypted_data, nonce, version, raw_key)
        return constant_time_equals(computed, expected)
    }

    private fun constant_time_equals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    private fun hkdf_sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(1.toByte())
        val okm = mac.doFinal()
        prk.fill(0)

        return okm.copyOf(length)
    }

    private fun aes_gcm_decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun aes_gcm_encrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)
    }

    private fun b64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun split_name(name: String): Pair<String, String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "" to ""
        val parts = trimmed.split(" ", limit = 2)
        return parts[0] to (parts.getOrNull(1) ?: "")
    }

    private fun parse_contact_json(id: String, json_str: String): Contact? {
        return try {
            val obj = org.json.JSONObject(json_str)
            val first_name = obj.optString("first_name", "")
            val last_name = obj.optString("last_name", "")
            val name = listOf(first_name, last_name).filter { it.isNotBlank() }.joinToString(" ")
            val emails_arr = obj.optJSONArray("emails")
            val emails = mutableListOf<String>()
            if (emails_arr != null) {
                for (i in 0 until emails_arr.length()) {
                    emails.add(emails_arr.optString(i, ""))
                }
            }
            val address_obj = obj.optJSONObject("address")
            val social_obj = obj.optJSONObject("social_links")

            Contact(
                id = id,
                name = name.ifBlank { emails.firstOrNull() ?: "" },
                email = emails.firstOrNull() ?: "",
                phone = obj.optString("phone", ""),
                company = obj.optString("company", ""),
                title = obj.optString("job_title", ""),
                work_email = if (emails.size > 1) emails[1] else "",
                birthday = obj.optString("birthday", ""),
                address = address_obj?.optString("street", "") ?: "",
                city = address_obj?.optString("city", "") ?: "",
                region = address_obj?.optString("state", "") ?: "",
                postal_code = address_obj?.optString("postal_code", "") ?: "",
                country = address_obj?.optString("country", "") ?: "",
                website = social_obj?.optString("website", "") ?: "",
                twitter = social_obj?.optString("twitter", "") ?: "",
                linkedin = social_obj?.optString("linkedin", "") ?: "",
                notes = obj.optString("notes", ""),
                is_favorite = obj.optBoolean("is_favorite", false),
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun encode_contact_json(contact: Contact, include_envelope: Boolean): String {
        val obj = org.json.JSONObject()
        val (first, last) = split_name(contact.name)
        obj.put("first_name", first)
        obj.put("last_name", last)

        val emails = org.json.JSONArray()
        if (contact.email.isNotBlank()) emails.put(contact.email)
        if (contact.work_email.isNotBlank()) emails.put(contact.work_email)
        obj.put("emails", emails)

        if (contact.phone.isNotBlank()) obj.put("phone", contact.phone)
        if (contact.company.isNotBlank()) obj.put("company", contact.company)
        if (contact.title.isNotBlank()) obj.put("job_title", contact.title)

        val has_address = listOf(contact.address, contact.city, contact.region, contact.postal_code, contact.country)
            .any { it.isNotBlank() }
        if (has_address) {
            val addr = org.json.JSONObject()
            addr.put("street", contact.address)
            addr.put("city", contact.city)
            addr.put("state", contact.region)
            addr.put("postal_code", contact.postal_code)
            addr.put("country", contact.country)
            obj.put("address", addr)
        }

        val has_social = contact.website.isNotBlank() || contact.twitter.isNotBlank() || contact.linkedin.isNotBlank()
        if (has_social) {
            val social = org.json.JSONObject()
            if (contact.website.isNotBlank()) social.put("website", contact.website)
            if (contact.twitter.isNotBlank()) social.put("twitter", contact.twitter)
            if (contact.linkedin.isNotBlank()) social.put("linkedin", contact.linkedin)
            obj.put("social_links", social)
        }

        if (contact.birthday.isNotBlank()) obj.put("birthday", contact.birthday)
        if (contact.notes.isNotBlank()) obj.put("notes", contact.notes)
        obj.put("is_favorite", contact.is_favorite)

        if (include_envelope) {
            obj.put("_version", CONTACT_DATA_VERSION)
            obj.put("_encrypted_at", java.time.Instant.now().toString())
        }

        return obj.toString()
    }

    companion object {
        private const val SALT_PREFIX = "aster-hkdf-salt-v1:"
        private const val DERIVED_KEY_INFO = "aster-storage-encryption-key-v1"
        private const val HMAC_INFO = "contacts-hmac-v2"
        private const val SEARCH_INFO = "contacts-search-v2"
        private val ENVELOPE_VERSIONS = listOf("astermail-envelope-v1", "astermail-import-v1")
    }
}
