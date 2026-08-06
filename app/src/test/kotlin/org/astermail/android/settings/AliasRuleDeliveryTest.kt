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

package org.astermail.android.settings

import org.astermail.android.api.mail_rules.Action
import org.astermail.android.api.mail_rules.AddressOp
import org.astermail.android.api.mail_rules.Condition
import org.astermail.android.api.mail_rules.MailRule
import org.astermail.android.api.mail_rules.TextOp
import org.astermail.android.mail_rules.AliasDeliverySetting
import org.astermail.android.mail_rules.alias_rule_delivery
import org.astermail.android.mail_rules.alias_rule_label
import org.astermail.android.mail_rules.rule_alias_delivery_conflict
import org.astermail.android.mail_rules.rule_alias_label_conflict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliasRuleDeliveryTest {

    private fun rule(
        id: String,
        name: String = id,
        enabled: Boolean = true,
        priority: Int = 0,
        created_at: String? = null,
        conditions: List<Condition> = emptyList(),
        actions: List<Action> = emptyList(),
    ) = MailRule(
        id = id,
        name = name,
        enabled = enabled,
        priority = priority,
        created_at = created_at,
        conditions = conditions,
        actions = actions,
    )

    private fun to_is(value: String) = Condition.To(op = AddressOp.IS, value = value)

    @Test
    fun `move_to rule targeting the alias is reported`() {
        val rules = listOf(
            rule(
                id = "r1",
                name = "Feed",
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.MoveTo("folder_feed")),
            ),
        )
        val found = alias_rule_delivery(rules, "shop@astermail.org")
        assertEquals("r1", found?.rule_id)
        assertEquals("Feed", found?.rule_name)
        assertEquals("folder_feed", found?.folder_token)
    }

    @Test
    fun `alias not targeted by any rule has no delivery`() {
        val rules = listOf(
            rule(
                id = "r1",
                conditions = listOf(to_is("other@astermail.org")),
                actions = listOf(Action.MoveTo("folder_feed")),
            ),
        )
        assertNull(alias_rule_delivery(rules, "shop@astermail.org"))
    }

    @Test
    fun `disabled rules are ignored`() {
        val rules = listOf(
            rule(
                id = "r1",
                enabled = false,
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.MoveTo("folder_feed")),
            ),
        )
        assertNull(alias_rule_delivery(rules, "shop@astermail.org"))
    }

    @Test
    fun `rules without a move_to action are ignored`() {
        val rules = listOf(
            rule(
                id = "r1",
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.ApplyLabels(listOf("label_a"))),
            ),
        )
        assertNull(alias_rule_delivery(rules, "shop@astermail.org"))
    }

    @Test
    fun `the last rule by priority then created_at wins`() {
        val rules = listOf(
            rule(
                id = "later",
                priority = 5,
                created_at = "2026-01-01T00:00:00Z",
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.MoveTo("folder_last")),
            ),
            rule(
                id = "earlier",
                priority = 1,
                created_at = "2026-02-01T00:00:00Z",
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.MoveTo("folder_first")),
            ),
            rule(
                id = "same_priority_older",
                priority = 5,
                created_at = "2025-01-01T00:00:00Z",
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.MoveTo("folder_middle")),
            ),
        )
        assertEquals("folder_last", alias_rule_delivery(rules, "shop@astermail.org")?.folder_token)
    }

    @Test
    fun `the last move_to action inside a rule wins`() {
        val rules = listOf(
            rule(
                id = "r1",
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.MoveTo("folder_a"), Action.MoveTo("folder_b")),
            ),
        )
        assertEquals("folder_b", alias_rule_delivery(rules, "shop@astermail.org")?.folder_token)
    }

    @Test
    fun `address matching ignores case and surrounding whitespace`() {
        val rules = listOf(
            rule(
                id = "r1",
                conditions = listOf(to_is("  Shop@AsterMail.org ")),
                actions = listOf(Action.MoveTo("folder_feed")),
            ),
        )
        assertEquals("folder_feed", alias_rule_delivery(rules, "shop@astermail.org")?.folder_token)
    }

    @Test
    fun `cc bcc and any_recipient conditions all match`() {
        listOf(
            Condition.Cc(op = AddressOp.IS, value = "shop@astermail.org"),
            Condition.Bcc(op = AddressOp.IS, value = "shop@astermail.org"),
            Condition.AnyRecipient(op = AddressOp.IS, value = "shop@astermail.org"),
        ).forEach { condition ->
            val rules = listOf(
                rule(id = "r1", conditions = listOf(condition), actions = listOf(Action.MoveTo("f"))),
            )
            assertEquals("f", alias_rule_delivery(rules, "shop@astermail.org")?.folder_token)
        }
    }

    @Test
    fun `contains and matches_domain operators match`() {
        val contains = listOf(
            rule(
                id = "r1",
                conditions = listOf(Condition.To(op = AddressOp.CONTAINS, value = "shop@")),
                actions = listOf(Action.MoveTo("f")),
            ),
        )
        assertEquals("f", alias_rule_delivery(contains, "shop@astermail.org")?.folder_token)

        val domain = listOf(
            rule(
                id = "r1",
                conditions = listOf(Condition.To(op = AddressOp.MATCHES_DOMAIN, value = "@astermail.org")),
                actions = listOf(Action.MoveTo("f")),
            ),
        )
        assertEquals("f", alias_rule_delivery(domain, "shop@astermail.org")?.folder_token)
    }

    @Test
    fun `nested and or conditions match while not is ignored`() {
        val nested = listOf(
            rule(
                id = "r1",
                conditions = listOf(
                    Condition.And(
                        listOf(
                            Condition.Subject(op = TextOp.CONTAINS, value = "receipt"),
                            Condition.Or(listOf(to_is("shop@astermail.org"))),
                        ),
                    ),
                ),
                actions = listOf(Action.MoveTo("f")),
            ),
        )
        assertEquals("f", alias_rule_delivery(nested, "shop@astermail.org")?.folder_token)

        val negated = listOf(
            rule(
                id = "r1",
                conditions = listOf(Condition.Not(to_is("shop@astermail.org"))),
                actions = listOf(Action.MoveTo("f")),
            ),
        )
        assertNull(alias_rule_delivery(negated, "shop@astermail.org"))
    }

    @Test
    fun `blank condition values and blank addresses never match`() {
        val rules = listOf(
            rule(id = "r1", conditions = listOf(to_is("   ")), actions = listOf(Action.MoveTo("f"))),
        )
        assertNull(alias_rule_delivery(rules, "shop@astermail.org"))
        assertNull(alias_rule_delivery(listOf(rule(id = "r2")), ""))
        assertNull(alias_rule_delivery(listOf(rule(id = "r3")), "not-an-address"))
    }

    @Test
    fun `conflict is reported when the rule folder differs from the alias folder`() {
        val conflict = rule_alias_delivery_conflict(
            conditions = listOf(to_is("Shop@astermail.org")),
            actions = listOf(Action.MoveTo("folder_feed")),
            alias_delivery = mapOf(
                "shop@astermail.org" to AliasDeliverySetting("folder_receipts", null, never_inbox = false),
            ),
        )
        assertEquals("Shop@astermail.org", conflict?.alias_address)
        assertEquals("folder_feed", conflict?.rule_folder_token)
    }

    @Test
    fun `no conflict when the rule folder equals the alias folder`() {
        assertNull(
            rule_alias_delivery_conflict(
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.MoveTo("folder_feed")),
                alias_delivery = mapOf(
                    "shop@astermail.org" to AliasDeliverySetting("folder_feed", null, never_inbox = false),
                ),
            ),
        )
    }

    @Test
    fun `no conflict when the alias has no explicit delivery target`() {
        assertNull(
            rule_alias_delivery_conflict(
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.MoveTo("folder_feed")),
                alias_delivery = mapOf(
                    "shop@astermail.org" to AliasDeliverySetting(null, null, never_inbox = false),
                ),
            ),
        )
    }

    @Test
    fun `archive-only aliases conflict with a move_to rule`() {
        val conflict = rule_alias_delivery_conflict(
            conditions = listOf(to_is("shop@astermail.org")),
            actions = listOf(Action.MoveTo("folder_feed")),
            alias_delivery = mapOf(
                "shop@astermail.org" to AliasDeliverySetting(null, null, never_inbox = true),
            ),
        )
        assertEquals("folder_feed", conflict?.rule_folder_token)
    }

    @Test
    fun `conflict only considers exact address conditions`() {
        assertNull(
            rule_alias_delivery_conflict(
                conditions = listOf(Condition.To(op = AddressOp.CONTAINS, value = "shop@astermail.org")),
                actions = listOf(Action.MoveTo("folder_feed")),
                alias_delivery = mapOf(
                    "shop@astermail.org" to AliasDeliverySetting("folder_receipts", null, never_inbox = false),
                ),
            ),
        )
    }

    @Test
    fun `conflict requires a move_to action`() {
        assertNull(
            rule_alias_delivery_conflict(
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.ApplyLabels(listOf("label_a"))),
                alias_delivery = mapOf(
                    "shop@astermail.org" to AliasDeliverySetting("folder_receipts", null, never_inbox = false),
                ),
            ),
        )
    }

    @Test
    fun `conflict finds the alias nested inside an or condition`() {
        val conflict = rule_alias_delivery_conflict(
            conditions = listOf(Condition.Or(listOf(to_is("shop@astermail.org")))),
            actions = listOf(Action.MoveTo("folder_feed")),
            alias_delivery = mapOf(
                "shop@astermail.org" to AliasDeliverySetting("folder_receipts", null, never_inbox = false),
            ),
        )
        assertEquals("shop@astermail.org", conflict?.alias_address)
    }

    @Test
    fun `apply_labels rule targeting the alias is reported`() {
        val rules = listOf(
            rule(
                id = "r1",
                name = "Receipts",
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.ApplyLabels(listOf("tag_a", "tag_b"))),
            ),
        )
        val found = alias_rule_label(rules, "shop@astermail.org")
        assertEquals("r1", found?.rule_id)
        assertEquals("Receipts", found?.rule_name)
        assertEquals(listOf("tag_a", "tag_b"), found?.label_tokens)
    }

    @Test
    fun `apply_labels rules that are disabled or target another alias are ignored`() {
        val rules = listOf(
            rule(
                id = "off",
                enabled = false,
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.ApplyLabels(listOf("tag_a"))),
            ),
            rule(
                id = "other",
                conditions = listOf(to_is("news@astermail.org")),
                actions = listOf(Action.ApplyLabels(listOf("tag_b"))),
            ),
        )
        assertNull(alias_rule_label(rules, "shop@astermail.org"))
        assertNull(alias_rule_label(rules, ""))
        assertNull(alias_rule_label(rules, "not-an-address"))
    }

    @Test
    fun `label conflict is reported when the rule labels differ from the alias label`() {
        val conflict = rule_alias_label_conflict(
            conditions = listOf(to_is("Shop@astermail.org")),
            actions = listOf(Action.ApplyLabels(listOf("tag_b"))),
            alias_delivery = mapOf(
                "shop@astermail.org" to AliasDeliverySetting(null, "tag_a", never_inbox = false),
            ),
        )
        assertEquals("Shop@astermail.org", conflict?.alias_address)
        assertEquals("tag_a", conflict?.alias_label_token)
        assertEquals(listOf("tag_b"), conflict?.rule_label_tokens)
    }

    @Test
    fun `no label conflict when the rule already applies the alias label`() {
        assertNull(
            rule_alias_label_conflict(
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.ApplyLabels(listOf("tag_a", "tag_b"))),
                alias_delivery = mapOf(
                    "shop@astermail.org" to AliasDeliverySetting(null, "tag_a", never_inbox = false),
                ),
            ),
        )
    }

    @Test
    fun `no label conflict when the alias has no delivery label`() {
        assertNull(
            rule_alias_label_conflict(
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.ApplyLabels(listOf("tag_b"))),
                alias_delivery = mapOf(
                    "shop@astermail.org" to AliasDeliverySetting("folder_receipts", null, never_inbox = false),
                ),
            ),
        )
    }

    @Test
    fun `label conflict requires an apply_labels action`() {
        assertNull(
            rule_alias_label_conflict(
                conditions = listOf(to_is("shop@astermail.org")),
                actions = listOf(Action.MoveTo("folder_feed")),
                alias_delivery = mapOf(
                    "shop@astermail.org" to AliasDeliverySetting(null, "tag_a", never_inbox = false),
                ),
            ),
        )
    }
}
