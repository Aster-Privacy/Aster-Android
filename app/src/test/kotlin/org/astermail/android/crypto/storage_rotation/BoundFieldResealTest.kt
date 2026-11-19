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

package org.astermail.android.crypto.storage_rotation

import org.astermail.android.crypto.FieldFormat
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundFieldResealTest {

    private val old_key = ByteArray(32) { it.toByte() }
    private val new_key = ByteArray(32) { (it + 100).toByte() }
    private val plaintext = "Checking in.".toByteArray(Charsets.UTF_8)

    private fun keys(): ResealKeys = ResealKeys(new_key.copyOf(), listOf(old_key.copyOf()))

    private fun binding(record_id: String?): FieldBinding =
        FieldBinding(FieldFormat.KIND_TEMPLATE, "content", record_id)

    private fun resealed(sealed: SealedValue, record_id: String): SealedValue {
        val result = reseal_bound_field(keys(), sealed.ciphertext, sealed.nonce, binding(record_id))
        assertTrue(result is ResealResult.Sealed)
        return (result as ResealResult.Sealed).value
    }

    private fun open_new(sealed: SealedValue, aad: ByteArray?): ByteArray? =
        gcm_open(new_key, decode_b64(sealed.ciphertext)!!, decode_b64(sealed.nonce)!!, aad)

    @After
    fun reset_switch() {
        FieldFormat.write_v2 = false
    }

    @Test
    fun a_bound_field_stays_bound_to_its_record() {
        val aad = FieldFormat.aad(FieldFormat.KIND_TEMPLATE, "content", "t1")
        val moved = resealed(gcm_seal(old_key, plaintext, aad), "t1")
        assertArrayEquals(plaintext, open_new(moved, aad))
        assertNull(open_new(moved, null))
    }

    @Test
    fun a_field_bound_before_its_record_had_an_id_keeps_that_binding() {
        val aad = FieldFormat.aad(FieldFormat.KIND_TEMPLATE, "content", null)
        val moved = resealed(gcm_seal(old_key, plaintext, aad), "t2")
        assertArrayEquals(plaintext, open_new(moved, aad))
    }

    @Test
    fun an_unbound_field_stays_unbound_while_the_switch_is_off() {
        val moved = resealed(gcm_seal(old_key, plaintext, null), "t3")
        assertArrayEquals(plaintext, open_new(moved, null))
    }

    @Test
    fun an_unbound_field_gains_its_binding_once_the_switch_is_on() {
        FieldFormat.write_v2 = true
        val aad = FieldFormat.aad(FieldFormat.KIND_TEMPLATE, "content", "t4")
        val moved = resealed(gcm_seal(old_key, plaintext, null), "t4")
        assertArrayEquals(plaintext, open_new(moved, aad))
        assertNull(open_new(moved, null))
    }

    @Test
    fun a_bound_field_already_under_the_new_key_is_left_alone() {
        val aad = FieldFormat.aad(FieldFormat.KIND_TEMPLATE, "content", "t5")
        val current = gcm_seal(new_key, plaintext, aad)
        assertEquals(
            ResealResult.Current,
            reseal_bound_field(keys(), current.ciphertext, current.nonce, binding("t5")),
        )
    }

    @org.junit.After
    fun reset_records() {
        org.astermail.android.crypto.BoundFieldRecords.bind(null)
    }

    @Test
    fun a_record_seen_bound_does_not_reseal_an_unbound_form() {
        org.astermail.android.crypto.BoundFieldRecords.bind(null)
        org.astermail.android.crypto.BoundFieldRecords.mark(FieldFormat.KIND_TEMPLATE, "tpl-50")
        val nonce = ByteArray(12) { 3 }
        val unbound = FieldFormat.seal(plaintext, old_key, nonce, FieldFormat.KIND_TEMPLATE, "content", "tpl-50", FieldFormat.V1)
        val result = reseal_bound_field(
            keys(),
            java.util.Base64.getEncoder().encodeToString(unbound),
            java.util.Base64.getEncoder().encodeToString(nonce),
            binding("tpl-50"),
        )
        assertEquals(ResealResult.Unreadable, result)
    }
}
