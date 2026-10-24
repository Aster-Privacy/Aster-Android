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

package org.astermail.android.api.errors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientErrorReporterTest {
    private fun with_frames(error: Throwable, vararg classes: String): Throwable {
        error.stackTrace = classes.map { StackTraceElement(it, "run", "File.kt", 1) }.toTypedArray()
        return error
    }

    @Test
    fun crash_error_code_uses_root_cause_class() {
        val error = RuntimeException("outer", IllegalStateException("inner", NullPointerException()))
        assertEquals("uncaught_nullpointerexception", crash_error_code(error))
    }

    @Test
    fun crash_error_code_stays_a_slug_and_under_the_cap() {
        val anonymous = object : RuntimeException() {}
        assertEquals("uncaught_throwable", crash_error_code(anonymous))
        val code = crash_error_code(ArrayIndexOutOfBoundsException())
        assertTrue(code.length <= 64)
        assertTrue(code.matches(Regex("^[a-z0-9_]+$")))
    }

    @Test
    fun crash_error_code_survives_a_cause_cycle() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertEquals("uncaught_runtimeexception", crash_error_code(b))
    }

    @Test
    fun crash_route_names_the_first_app_frame() {
        val error = with_frames(
            IllegalStateException(),
            "androidx.compose.runtime.Recomposer",
            "org.astermail.android.ui.mail.MailDetailScreenKt\$on_sender_tap\$1",
            "org.astermail.android.MainActivity",
        )
        assertEquals("/app/maildetailscreenkt", crash_route(error))
    }

    @Test
    fun crash_route_looks_through_causes() {
        val cause = with_frames(NullPointerException(), "org.astermail.android.api.ApiClient")
        val error = with_frames(RuntimeException(cause), "kotlinx.coroutines.JobSupport")
        assertEquals("/app/apiclient", crash_route(error))
    }

    @Test
    fun crash_route_falls_back_without_an_app_frame() {
        val error = with_frames(RuntimeException(), "android.os.Handler", "java.lang.Thread")
        assertEquals("/app", crash_route(error))
        assertEquals("/app", crash_route(with_frames(RuntimeException())))
    }
}
