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
    fun crash_trace_route_lists_root_cause_frames() {
        val root = IllegalStateException()
        root.stackTrace = arrayOf(
            StackTraceElement("gi3", "c", "SourceFile", 12),
            StackTraceElement("wj8", "N", "SourceFile", 340),
            StackTraceElement("androidx.compose.ui.node.Owner\$Inner", "run", "SourceFile", -1),
        )
        val error = with_frames(RuntimeException(root), "java.lang.Thread")
        assertEquals("/app/t/gi3.c:12/wj8.n:340/ownerinner.run", crash_trace_route(error))
    }

    @Test
    fun crash_trace_route_stays_within_the_backend_limits() {
        val error = IllegalStateException()
        error.stackTrace = Array(40) { StackTraceElement("a.b.LongClassName$it", "method_number_$it", "SourceFile", 1000 + it) }
        val route = crash_trace_route(error)
        assertTrue(route.length <= 96)
        assertTrue(route.matches(Regex("^/[a-z0-9_.:*/-]{0,95}$")))
        assertTrue(!route.contains("//"))
        assertTrue(route.startsWith("/app/t/longclassname0.method_number_0:1000"))
    }

    @Test
    fun crash_trace_route_falls_back_without_frames() {
        assertEquals("/app", crash_trace_route(with_frames(RuntimeException())))
    }

    @Test
    fun anr_main_thread_frames_skip_runtime_frames_and_other_threads() {
        val trace = listOf(
            "----- pid 123 at 2026-11-01 10:00:00 -----",
            "\"aster-prefs-warm\" prio=5 tid=20 Native",
            "  at android.security.keystore2.Op.run(SourceFile:5)",
            "",
            "\"main\" prio=5 tid=1 Blocked",
            "  | group=\"main\" sCount=1",
            "  at java.util.concurrent.ConcurrentHashMap.computeIfAbsent(ConcurrentHashMap.java:1708)",
            "  - waiting to lock <0x0a1b> (a java.util.concurrent.ConcurrentHashMap\$Node) held by thread 20",
            "  at nk5.f(SourceFile:44)",
            "  at org.astermail.android.ui.compose.ComposeScreenKt.a(Native method)",
            "",
            "\"binder:1\" prio=5 tid=2 Native",
            "  at qq.z(SourceFile:9)",
        ).joinToString("\n")
        val frames = anr_main_thread_frames(trace)
        assertEquals(2, frames.size)
        assertEquals("/app/t/nk5.f:44/composescreenkt.a", trace_route_of(frames))
    }

    @Test
    fun anr_main_thread_frames_handle_missing_main_thread() {
        assertTrue(anr_main_thread_frames("garbage\nmore").isEmpty())
        assertEquals(null, trace_route_of(emptyList()))
    }

    @Test
    fun anr_error_code_reads_the_description() {
        assertEquals("anr_input", anr_error_code("Input dispatching timed out (abc)"))
        assertEquals("anr_broadcast", anr_error_code("Broadcast of Intent { act=x }"))
        assertEquals("anr", anr_error_code(null))
    }

    @Test
    fun crash_route_falls_back_without_an_app_frame() {
        val error = with_frames(RuntimeException(), "android.os.Handler", "java.lang.Thread")
        assertEquals("/app", crash_route(error))
        assertEquals("/app", crash_route(with_frames(RuntimeException())))
    }
}
