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

package org.astermail.android.mail

import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.api.keys.BoundWriteSwitches
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class AttachmentWriteBindingTest {

    private val key = ByteArray(32) { (it * 3).toByte() }
    private val nonce = ByteArray(12) { (it + 7).toByte() }
    private val plaintext = "attachment body".toByteArray()

    @Before
    fun setup() {
        BoundWriteSwitches.clear()
    }

    private fun write_attachment(seq_num: Int): ByteArray {
        val unsafe_field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafe_field.isAccessible = true
        val unsafe = unsafe_field.get(null)
        val repository = unsafe.javaClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, MailRepository::class.java)
        val method = MailRepository::class.java.getDeclaredMethod(
            "seal_attachment_data",
            ByteArray::class.java,
            ByteArray::class.java,
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(repository, plaintext, key, nonce, seq_num) as ByteArray
    }

    private fun open(sealed: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(sealed)
    }

    @Test
    fun binds_attachment_data_to_its_slot_without_a_server_switch() {
        val sealed = write_attachment(4)
        val opened = runCatching {
            open(sealed, "aster-attachment-v2|att=4|part=data".toByteArray(Charsets.UTF_8))
        }.getOrNull()
        assertArrayEquals(plaintext, opened)
        assertThrows(AEADBadTagException::class.java) { open(sealed, null) }
        assertThrows(AEADBadTagException::class.java) {
            open(sealed, "aster-attachment-v2|att=5|part=data".toByteArray(Charsets.UTF_8))
        }
    }
}
