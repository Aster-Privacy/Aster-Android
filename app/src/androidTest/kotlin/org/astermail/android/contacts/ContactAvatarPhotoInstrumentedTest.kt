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

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.contacts.ContactAvatar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactAvatarPhotoInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private data class TestContact(val email: String, val name: String, val avatar_url: String)

    private enum class Shown { RED, BLUE, NONE }

    private fun photo_url(color: Int): String {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(96 * 96) { i -> if ((i / 96 + i % 96) % 7 == 0) color xor 0x00010101 else color }
        bitmap.setPixels(pixels, 0, 96, 0, 0, 96, 96)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private val red = TestContact("ana@example.org", "Ana", photo_url(0xFFFF0000.toInt()))
    private val blue = TestContact("ben@example.org", "Ben", photo_url(0xFF0000FF.toInt()))
    private val plain = TestContact("cy@example.org", "Cy", "")

    private fun shown(tag: String): Shown {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        val c = pixels[pixels.width / 2, pixels.height / 6]
        return when {
            c.red > 0.9f && c.green < 0.1f && c.blue < 0.1f -> Shown.RED
            c.blue > 0.9f && c.red < 0.1f && c.green < 0.1f -> Shown.BLUE
            else -> Shown.NONE
        }
    }

    private fun wait_for(tag: String, expected: Shown): Shown {
        val deadline = System.currentTimeMillis() + 3_000
        var last = shown(tag)
        while (last != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            last = shown(tag)
        }
        return last
    }

    @Test
    fun one_avatar_follows_the_contact_it_is_given() {
        var contact by mutableStateOf(red)
        compose.setContent {
            AsterTheme {
                ContactAvatar(
                    avatar_url = contact.avatar_url,
                    email = contact.email,
                    name = contact.name,
                    size = 48.dp,
                    modifier = Modifier.testTag("avatar"),
                )
            }
        }
        assertEquals(Shown.RED, wait_for("avatar", Shown.RED))
        compose.runOnIdle { contact = plain }
        assertEquals(Shown.NONE, wait_for("avatar", Shown.NONE))
        compose.runOnIdle { contact = blue }
        assertEquals(Shown.BLUE, wait_for("avatar", Shown.BLUE))
        compose.runOnIdle { contact = red }
        assertEquals(Shown.RED, wait_for("avatar", Shown.RED))
        compose.runOnIdle { contact = plain }
        assertEquals(Shown.NONE, wait_for("avatar", Shown.NONE))
    }

    @Test
    fun unkeyed_row_shows_each_contact_its_own_photo_after_reordering() {
        var contacts by mutableStateOf(listOf(red, blue, plain))
        compose.setContent {
            AsterTheme {
                Row {
                    contacts.forEachIndexed { index, contact ->
                        ContactAvatar(
                            avatar_url = contact.avatar_url,
                            email = contact.email,
                            name = contact.name,
                            size = 48.dp,
                            modifier = Modifier.testTag("slot_$index"),
                        )
                    }
                }
            }
        }
        assertEquals(Shown.RED, wait_for("slot_0", Shown.RED))
        assertEquals(Shown.BLUE, wait_for("slot_1", Shown.BLUE))
        assertEquals(Shown.NONE, wait_for("slot_2", Shown.NONE))

        compose.runOnIdle { contacts = listOf(plain, red, blue) }
        assertEquals(Shown.NONE, wait_for("slot_0", Shown.NONE))
        assertEquals(Shown.RED, wait_for("slot_1", Shown.RED))
        assertEquals(Shown.BLUE, wait_for("slot_2", Shown.BLUE))

        compose.runOnIdle { contacts = listOf(blue, plain, red) }
        assertEquals(Shown.BLUE, wait_for("slot_0", Shown.BLUE))
        assertEquals(Shown.NONE, wait_for("slot_1", Shown.NONE))
        assertEquals(Shown.RED, wait_for("slot_2", Shown.RED))
    }
}
