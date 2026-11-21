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

package org.astermail.android.ui.settings.detail

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmtpTokenCopySourceTest {

    private val source = File("src/main/kotlin/org/astermail/android/ui/settings/detail/smtp_tokens_screen.kt")
        .readText()
        .replace("\r\n", "\n")

    private fun read_source(path: String): String = File(path).readText().replace("\r\n", "\n")

    private fun created_dialog_block(): String {
        val start = source.indexOf("fun smtp_token_created_dialog(")
        assertTrue("missing created token dialog", start >= 0)
        val end = source.indexOf("\n@Composable", start)
        assertTrue("missing end of created token dialog", end > start)
        return source.substring(start, end)
    }

    private fun dialog_call(block: String): String {
        val dialog_at = block.indexOf("AsterAlertDialog(")
        assertTrue("missing dialog call", dialog_at >= 0)
        val extra_at = block.indexOf("extra_content = {", dialog_at)
        assertTrue("missing dialog content", extra_at > dialog_at)
        return block.substring(dialog_at, extra_at)
    }

    @Test
    fun copy_all_writes_a_sensitive_clip() {
        val block = created_dialog_block()
        assertTrue(
            "copy all must go through write_sensitive_to_clipboard",
            block.contains("write_sensitive_to_clipboard(context, clipboard_label, all_text)"),
        )
    }

    @Test
    fun copy_all_schedules_the_clipboard_clear() {
        val block = created_dialog_block()
        val write_at = block.indexOf("write_sensitive_to_clipboard(")
        val clear_at = block.indexOf("schedule_sensitive_clipboard_clear(context)")
        assertTrue("copy all must schedule the clipboard clear", clear_at >= 0)
        assertTrue("the clear must follow the sensitive write", write_at in 0 until clear_at)
    }

    @Test
    fun the_screen_writes_no_plain_clip() {
        val plain_writes = listOf("ClipData.newPlainText", "write_to_clipboard(", "setPrimaryClip(")
            .filter { source.contains(it) }
        assertEquals(emptyList<String>(), plain_writes)
    }

    @Test
    fun the_screen_opens_the_created_token_dialog() {
        val start = source.indexOf("val created = state.smtp_token_created")
        assertTrue("missing created token block", start >= 0)
        assertTrue(
            "the created token must open smtp_token_created_dialog",
            source.indexOf("smtp_token_created_dialog(created.smtp_settings", start) > start,
        )
    }

    @Test
    fun the_created_token_dialog_blocks_screen_capture() {
        val block = created_dialog_block()
        val secure_at = block.indexOf("secure_screen()")
        val dialog_at = block.indexOf("AsterAlertDialog(")
        assertTrue("the created token dialog must call secure_screen()", secure_at >= 0)
        assertTrue("secure_screen() must run before the dialog opens", secure_at < dialog_at)
    }

    @Test
    fun the_dialog_window_is_secure_before_its_first_frame() {
        assertTrue(
            "the dialog must be created with the secure window policy",
            dialog_call(created_dialog_block()).contains("secure = true,"),
        )
        val dialog_source = read_source(
            "../core-design/src/main/kotlin/org/astermail/android/design/components/aster_dialog.kt",
        )
        assertTrue(
            "the secure dialog must pass SecureFlagPolicy.SecureOn to the window",
            dialog_source.contains("securePolicy = SecureFlagPolicy.SecureOn"),
        )
        val secure_source = read_source("src/main/kotlin/org/astermail/android/ui/common/secure_screen.kt")
        assertFalse(
            "FLAG_SECURE must not be added after composition",
            secure_source.contains("SideEffect"),
        )
    }

    @Test
    fun the_token_screen_has_no_double_blank_line() {
        assertFalse(source.contains("\n\n\n"))
    }

    private fun details_dialog_block(): String {
        val start = source.indexOf("fun smtp_token_details_dialog(")
        assertTrue(start >= 0)
        val end = source.indexOf("\n@Composable", start)
        assertTrue(end > start)
        return source.substring(start, end)
    }

    @Test
    fun the_details_dialog_never_reads_a_password_value() {
        val block = details_dialog_block()
        assertTrue(block.contains("settings: SmtpTokenConnectionSettings"))
        assertFalse(block.contains(".password"))
        assertFalse(block.contains("SmtpTokenSettings"))
        assertTrue(block.contains("R.string.smtp_token_password_hidden"))
    }

    @Test
    fun the_details_copy_all_holds_only_the_connection_settings() {
        val block = details_dialog_block()
        assertTrue(block.contains("write_sensitive_to_clipboard(context, clipboard_label, details_text)"))
        assertTrue(block.contains("val details_text = smtp_connection_copy_text("))
        val start = source.indexOf("internal fun smtp_connection_copy_text(")
        assertTrue(start >= 0)
        val helper = source.substring(start, source.indexOf("\n\n", start))
        assertFalse(helper.contains("password", ignoreCase = true))
    }

    @Test
    fun setup_details_never_fall_back_to_fixed_connection_values() {
        assertTrue(source.contains("token.smtp_settings?.takeIf { it.host.isNotBlank() && it.username.isNotBlank() }"))
        assertFalse(Regex("host = \"[^\"]").containsMatchIn(source))
        assertFalse(Regex("port = \\d").containsMatchIn(source))
    }
}
