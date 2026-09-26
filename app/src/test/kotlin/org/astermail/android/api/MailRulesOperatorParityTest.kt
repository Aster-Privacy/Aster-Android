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

package org.astermail.android.api

import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.astermail.android.api.mail_rules.AddressOp
import org.astermail.android.api.mail_rules.Condition
import org.astermail.android.api.mail_rules.MailRule
import org.astermail.android.api.mail_rules.MailRulesApiImpl
import org.astermail.android.api.mail_rules.TextOp
import org.astermail.android.api.mail_rules.UpdateRuleRequest
import org.astermail.android.mail_rules.rule_targets_address
import org.astermail.android.ui.settings.mail_rules.is_condition_complete
import org.astermail.android.ui.settings.mail_rules.value_for_address_op
import org.astermail.android.ui.settings.mail_rules.value_for_text_op
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MailRulesOperatorParityTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun build_client(): ApiClient = ApiClient(
        base_url = server.url("/").toString().trimEnd('/'),
        token_provider = object : TokenProvider {
            override suspend fun load(): BearerTokens? = BearerTokens("access", "refresh")
            override suspend fun refresh(): BearerTokens? = BearerTokens("access", "refresh")
            override suspend fun clear() {}
        },
        initial_csrf = "session-A:1799999999.signature",
        allow_cleartext_for_test = true,
    )

    private val all_address_ops = listOf(
        "is", "is_not", "contains", "does_not_contain", "starts_with",
        "ends_with", "matches_domain", "does_not_match_domain", "is_empty", "matches_regex",
    )

    private fun rule_json(conditions: String) =
        """{"id":"r1","name":"n","conditions":[$conditions],"actions":[]}"""

    @Test
    fun list_requests_current_operators_and_decodes_every_address_op() = runBlocking {
        val conditions = all_address_ops.joinToString(",") { op ->
            if (op == "is_empty") """{"field":"from","op":"is_empty"}"""
            else """{"field":"from","op":"$op","value":"a@b.c"}"""
        } + """,{"field":"subject","op":"is_not","value":"hi"}""" +
            """,{"field":"not","condition":{"field":"cc","op":"contains","value":"x"}}"""
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("""{"rules":[${rule_json(conditions)}]}"""))

        val rules = MailRulesApiImpl(build_client()).list().rules

        assertEquals("/api/mail/v1/mail-rules?ops=2", server.takeRequest().path)
        val decoded = rules.single().conditions
        assertEquals(
            all_address_ops,
            decoded.take(all_address_ops.size).map { op_name((it as Condition.From).op) },
        )
        assertEquals("", (decoded[all_address_ops.indexOf("is_empty")] as Condition.From).value)
        assertEquals(TextOp.IS_NOT, (decoded[all_address_ops.size] as Condition.Subject).op)
        assertTrue(decoded.last() is Condition.Not)
    }

    @Test
    fun update_requests_current_operators_and_encodes_new_ops() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(rule_json("")))

        MailRulesApiImpl(build_client()).update(
            "r1",
            UpdateRuleRequest(
                conditions = listOf(
                    Condition.From(op = AddressOp.DOES_NOT_CONTAIN, value = "newsletter"),
                    Condition.Cc(op = AddressOp.IS_EMPTY),
                ),
            ),
        )

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/mail/v1/mail-rules/r1?ops=2", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains(""""op":"does_not_contain""""))
        assertTrue(body, body.contains(""""op":"is_empty""""))
    }

    @Test
    fun address_and_text_operators_share_one_order() {
        assertEquals(all_address_ops, AddressOp.values().map { op_name(it) })
        val text = TextOp.values().map { it.name }
        assertEquals(
            listOf("IS", "IS_NOT", "CONTAINS", "DOES_NOT_CONTAIN", "STARTS_WITH", "ENDS_WITH", "IS_EMPTY", "MATCHES_REGEX"),
            text,
        )
        assertEquals(text, AddressOp.values().map { it.name }.filter { it in text })
    }

    @Test
    fun is_empty_needs_no_value_and_clears_the_old_one() {
        assertTrue(is_condition_complete(Condition.Cc(op = AddressOp.IS_EMPTY, value = "")))
        assertFalse(is_condition_complete(Condition.Cc(op = AddressOp.DOES_NOT_CONTAIN, value = "")))
        assertFalse(is_condition_complete(Condition.From(op = AddressOp.STARTS_WITH, value = " ")))
        assertEquals("", value_for_address_op(AddressOp.IS_EMPTY, "old"))
        assertEquals("old", value_for_address_op(AddressOp.ENDS_WITH, "old"))
        assertEquals("", value_for_text_op(TextOp.IS_EMPTY, "old"))
        assertEquals("old", value_for_text_op(TextOp.IS_NOT, "old"))
    }

    @Test
    fun alias_targeting_understands_prefix_and_suffix_but_not_negatives() {
        fun rule(op: AddressOp, value: String) =
            MailRule(id = "r", name = "r", conditions = listOf(Condition.To(op = op, value = value)))
        val alias = "Shop.Orders@astermail.org"

        assertTrue(rule_targets_address(rule(AddressOp.STARTS_WITH, "shop."), alias))
        assertTrue(rule_targets_address(rule(AddressOp.ENDS_WITH, "@ASTERMAIL.org"), alias))
        assertFalse(rule_targets_address(rule(AddressOp.STARTS_WITH, "orders"), alias))
        assertFalse(rule_targets_address(rule(AddressOp.DOES_NOT_CONTAIN, "zzz"), alias))
        assertFalse(rule_targets_address(rule(AddressOp.DOES_NOT_MATCH_DOMAIN, "other.org"), alias))
        assertFalse(rule_targets_address(rule(AddressOp.IS_EMPTY, ""), alias))
    }

    private fun op_name(op: AddressOp): String =
        kotlinx.serialization.json.Json.encodeToString(AddressOp.serializer(), op).trim('"')
}
