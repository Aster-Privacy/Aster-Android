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

import kotlinx.coroutines.runBlocking
import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordFormsTest {

    private val composed = "caf\u00e9"
    private val decomposed = "cafe\u0301"

    private class Server(private val stored: String) {
        val seen = mutableListOf<String>()

        fun attempt(form: String): Result<String> {
            seen += form
            return if (form == stored) Result.success("session") else Result.failure(ApiError.InvalidCredentials)
        }
    }

    @Test
    fun a_password_with_one_encoding_has_one_form() {
        assertEquals(listOf("correct horse 42"), PasswordForms.of("correct horse 42"))
    }

    @Test
    fun the_typed_form_comes_first_and_the_other_encoding_follows() {
        assertEquals(listOf(composed, decomposed), PasswordForms.of(composed))
        assertEquals(listOf(decomposed, composed), PasswordForms.of(decomposed))
    }

    @Test
    fun a_password_that_mixes_encodings_has_three_forms() {
        val mixed = composed + decomposed
        assertEquals(listOf(mixed, composed + composed, decomposed + decomposed), PasswordForms.of(mixed))
    }

    @Test
    fun the_typed_form_is_tried_once_when_it_works() = runBlocking {
        val server = Server(composed)
        val result = PasswordForms.first_accepted(composed) { server.attempt(it) }
        assertEquals("session", result.getOrThrow())
        assertEquals(listOf(composed), server.seen)
    }

    @Test
    fun a_wrong_password_with_one_encoding_is_tried_once() = runBlocking {
        val server = Server("something else")
        val result = PasswordForms.first_accepted("hunter2") { server.attempt(it) }
        assertSame(ApiError.InvalidCredentials, result.exceptionOrNull())
        assertEquals(listOf("hunter2"), server.seen)
    }

    @Test
    fun the_other_encoding_signs_in_in_both_directions() = runBlocking {
        val stored_decomposed = Server(decomposed)
        assertEquals("session", PasswordForms.first_accepted(composed) { stored_decomposed.attempt(it) }.getOrThrow())
        assertEquals(listOf(composed, decomposed), stored_decomposed.seen)
        val stored_composed = Server(composed)
        assertEquals("session", PasswordForms.first_accepted(decomposed) { stored_composed.attempt(it) }.getOrThrow())
        assertEquals(listOf(decomposed, composed), stored_composed.seen)
    }

    @Test
    fun an_answer_that_is_not_about_the_password_is_not_retried() = runBlocking {
        val failure = IllegalStateException("offline")
        var calls = 0
        val result = PasswordForms.first_accepted(composed) {
            calls += 1
            Result.failure<String>(failure)
        }
        assertSame(failure, result.exceptionOrNull())
        assertEquals(1, calls)
    }

    @Test
    fun the_first_answer_is_kept_when_no_form_works() = runBlocking {
        var calls = 0
        val result = PasswordForms.first_accepted(composed) {
            calls += 1
            if (calls == 1) Result.failure<String>(ApiError.InvalidCredentials)
            else Result.failure(IllegalStateException("captcha already used"))
        }
        assertTrue(result.exceptionOrNull() is ApiError.InvalidCredentials)
        assertEquals(2, calls)
    }
}
