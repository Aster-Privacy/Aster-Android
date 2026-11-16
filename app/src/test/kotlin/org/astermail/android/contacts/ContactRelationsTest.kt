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

import org.astermail.android.ui.contacts.Contact
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRelationsTest {

    private fun json_map(obj: JSONObject): Map<String, Any?> = obj.keys().asSequence().associateWith { obj.get(it) }

    private fun decode(json: String): ContactRelation? = decode_relation(JSONObject(json))

    @Test
    fun `decodes the shared encoding for every picker kind`() {
        assertEquals(ContactRelation(RelationKind.MOTHER, "Ana"), decode("""{"value":"Ana","type":"parent","relation":"mother"}"""))
        assertEquals(ContactRelation(RelationKind.FATHER, "Joe"), decode("""{"value":"Joe","type":"parent","relation":"father"}"""))
        assertEquals(ContactRelation(RelationKind.BROTHER, "Leo"), decode("""{"value":"Leo","type":"sibling","relation":"brother"}"""))
        assertEquals(ContactRelation(RelationKind.SISTER, "Mia"), decode("""{"value":"Mia","type":"sibling","relation":"sister"}"""))
        assertEquals(ContactRelation(RelationKind.RELATIVE, "Mei"), decode("""{"value":"Mei","type":"other","relation":"relative"}"""))
        assertEquals(ContactRelation(RelationKind.CUSTOM, "Pat", "Coach"), decode("""{"value":"Pat","type":"other","label":"Coach"}"""))
        assertEquals(ContactRelation(RelationKind.FRIEND, "Sam"), decode("""{"value":"Sam","type":"friend"}"""))
        assertEquals(ContactRelation(RelationKind.SPOUSE, "Kim"), decode("""{"value":"Kim","type":"spouse"}"""))
    }

    @Test
    fun `legacy parent and sibling rows keep their kind and stay pickable`() {
        val parent = decode("""{"value":"Ann","type":"parent"}""")!!
        assertEquals(RelationKind.PARENT, parent.kind)
        assertTrue(RelationKind.PARENT in parent.picker_kinds())
        assertFalse(RelationKind.PARENT in ContactRelation(RelationKind.FRIEND, "x").picker_kinds())
        assertEquals(RelationKind.SIBLING, decode("""{"value":"Al","type":"sibling"}""")!!.kind)
    }

    @Test
    fun `stale relation key does not override a direct type`() {
        assertEquals(ContactRelation(RelationKind.FRIEND, "Sam"), decode("""{"value":"Sam","type":"friend","relation":"mother"}"""))
        assertEquals(RelationKind.PARENT, decode("""{"value":"Ann","type":"parent","relation":"brother"}""")!!.kind)
    }

    @Test
    fun `unknown type becomes custom labeled with the label or raw type`() {
        assertEquals(ContactRelation(RelationKind.CUSTOM, "Rui", "Godparent"), decode("""{"value":"Rui","type":"Godparent"}"""))
        assertEquals(ContactRelation(RelationKind.CUSTOM, "Rui", "Mentor"), decode("""{"value":"Rui","type":"godparent","label":"Mentor"}"""))
        assertNull(decode("""{"type":"friend"}"""))
    }

    @Test
    fun `encode matches the shared contract`() {
        assertEquals(json_map(JSONObject("""{"value":"Ana","type":"parent","relation":"mother"}""")), json_map(encode_relation(ContactRelation(RelationKind.MOTHER, "Ana"))))
        assertEquals(json_map(JSONObject("""{"value":"Leo","type":"sibling","relation":"brother"}""")), json_map(encode_relation(ContactRelation(RelationKind.BROTHER, "Leo"))))
        assertEquals(json_map(JSONObject("""{"value":"Mei","type":"other","relation":"relative"}""")), json_map(encode_relation(ContactRelation(RelationKind.RELATIVE, "Mei"))))
        assertEquals(json_map(JSONObject("""{"value":"Rui","type":"other","label":"Godparent"}""")), json_map(encode_relation(ContactRelation(RelationKind.CUSTOM, "Rui", "Godparent"))))
        assertEquals(json_map(JSONObject("""{"value":"Rui","type":"other"}""")), json_map(encode_relation(ContactRelation(RelationKind.CUSTOM, "Rui", ""))))
        assertEquals(json_map(JSONObject("""{"value":"Sam","type":"friend"}""")), json_map(encode_relation(ContactRelation(RelationKind.FRIEND, "Sam"))))
    }

    @Test
    fun `normalize drops empty and whitespace values`() {
        val out = normalize_relations(
            listOf(
                ContactRelation(RelationKind.FRIEND, ""),
                ContactRelation(RelationKind.FRIEND, "   \t "),
                ContactRelation(RelationKind.FRIEND, "  Sam  "),
            ),
        )
        assertEquals(listOf(ContactRelation(RelationKind.FRIEND, "Sam")), out)
    }

    @Test
    fun `normalize caps value and label by code points and replaces control characters`() {
        val emoji = "😀"
        val long_value = emoji.repeat(250)
        val out = normalize_relation(ContactRelation(RelationKind.CUSTOM, long_value, "L".repeat(80)))!!
        assertEquals(MAX_RELATION_VALUE, out.value.codePointCount(0, out.value.length))
        assertEquals(MAX_RELATION_LABEL, out.label.length)
        assertEquals("a b", normalize_relation(ContactRelation(RelationKind.FRIEND, "a\nb"))!!.value)
        assertEquals("", normalize_relation(ContactRelation(RelationKind.FRIEND, "Sam", "ignored"))!!.label)
    }

    @Test
    fun `normalize dedupes case and unicode forms and keeps at most fifty`() {
        val composed = "José"
        val decomposed = "josé"
        val out = normalize_relations(
            listOf(
                ContactRelation(RelationKind.FRIEND, composed),
                ContactRelation(RelationKind.FRIEND, decomposed),
                ContactRelation(RelationKind.MANAGER, composed),
                ContactRelation(RelationKind.CUSTOM, composed, "Coach"),
                ContactRelation(RelationKind.CUSTOM, composed, "COACH"),
                ContactRelation(RelationKind.CUSTOM, composed, "Mentor"),
            ),
        )
        assertEquals(4, out.size)
        val many = (1..80).map { ContactRelation(RelationKind.FRIEND, "Person $it") }
        assertEquals(MAX_RELATIONS, normalize_relations(many).size)
    }

    @Test
    fun `custom with an empty label is kept and encoded without a label`() {
        val out = normalize_relations(listOf(ContactRelation(RelationKind.CUSTOM, "Rui", "   ")))
        assertEquals(listOf(ContactRelation(RelationKind.CUSTOM, "Rui", "")), out)
        assertFalse(encode_relation(out.single()).has("label"))
    }

    @Test
    fun `untouched relations leave the stored array byte identical`() {
        val raw = """[{"value":"Ana","type":"parent","relation":"mother","x_extra":1},{"value":"Rui","type":"Godparent"}]"""
        val obj = JSONObject().put(RELATIONS_KEY, JSONArray(raw))
        val before = obj.toString()
        write_contact_relations(obj, null)
        assertEquals(before, obj.toString())
        write_contact_relations(obj, decode_contact_relations(obj))
        assertEquals(before, obj.toString())
    }

    @Test
    fun `editing one row keeps the other rows verbatim including unknown types`() {
        val raw = """[{"value":"Ana","type":"parent","relation":"mother","x_extra":1},{"value":"Rui","type":"Godparent"}]"""
        val obj = JSONObject().put(RELATIONS_KEY, JSONArray(raw))
        val edited = decode_contact_relations(obj) + ContactRelation(RelationKind.FRIEND, "Sam")
        write_contact_relations(obj, edited)
        val arr = obj.getJSONArray(RELATIONS_KEY)
        assertEquals(3, arr.length())
        assertEquals(1, arr.getJSONObject(0).getInt("x_extra"))
        assertEquals("Godparent", arr.getJSONObject(1).getString("type"))
        assertEquals("friend", arr.getJSONObject(2).getString("type"))
    }

    @Test
    fun `removing every relation removes the key`() {
        val obj = JSONObject().put(RELATIONS_KEY, JSONArray("""[{"value":"Sam","type":"friend"}]"""))
        write_contact_relations(obj, emptyList())
        assertFalse(obj.has(RELATIONS_KEY))
    }

    @Test
    fun `merge is a deduped union capped at fifty`() {
        val a = listOf(ContactRelation(RelationKind.FRIEND, "Sam"), ContactRelation(RelationKind.MOTHER, "Ana"))
        val b = listOf(ContactRelation(RelationKind.FRIEND, "sam"), ContactRelation(RelationKind.CUSTOM, "Rui", "Godparent"))
        assertEquals(
            listOf(
                ContactRelation(RelationKind.FRIEND, "Sam"),
                ContactRelation(RelationKind.MOTHER, "Ana"),
                ContactRelation(RelationKind.CUSTOM, "Rui", "Godparent"),
            ),
            merge_relations(a, b),
        )
        val big = (1..40).map { ContactRelation(RelationKind.FRIEND, "A$it") }
        val big2 = (1..40).map { ContactRelation(RelationKind.FRIEND, "B$it") }
        assertEquals(MAX_RELATIONS, merge_relations(big, big2).size)
    }

    @Test
    fun `merging duplicate contacts unions their relations`() {
        val a = Contact(id = "1", name = "Ada", email = "ada@example.com", relations = listOf(ContactRelation(RelationKind.FRIEND, "Sam")))
        val b = Contact(id = "2", name = "Ada", email = "ada@example.com", relations = listOf(ContactRelation(RelationKind.FRIEND, "SAM"), ContactRelation(RelationKind.MOTHER, "Ana")))
        val merged = merge_contacts(listOf(a, b))
        assertEquals(listOf(ContactRelation(RelationKind.FRIEND, "Sam"), ContactRelation(RelationKind.MOTHER, "Ana")), merged.relations)
        assertNull(merge_contacts(listOf(Contact(id = "1", name = "A", email = "a@example.com"), Contact(id = "2", name = "A", email = "a@example.com"))).relations)
    }

    @Test
    fun `search matches value label and kind name`() {
        val fold: (String) -> String = { it.lowercase() }
        val custom = ContactRelation(RelationKind.CUSTOM, "Rui", "Godparent")
        assertTrue(relation_matches(custom, "Godparent", "rui", fold))
        assertTrue(relation_matches(custom, "Godparent", "godp", fold))
        assertTrue(relation_matches(ContactRelation(RelationKind.MOTHER, "Ana"), "Mother", "moth", fold))
        assertFalse(relation_matches(ContactRelation(RelationKind.MOTHER, "Ana"), "Mother", "zzz", fold))
    }

    @Test
    fun `vcard export and import round trip every kind`() {
        val relations = listOf(
            ContactRelation(RelationKind.MOTHER, "Ana Lima"),
            ContactRelation(RelationKind.BROTHER, "Leo"),
            ContactRelation(RelationKind.RELATIVE, "Mei"),
            ContactRelation(RelationKind.CUSTOM, "Rui", "Godparent"),
            ContactRelation(RelationKind.FRIEND, "Sam"),
            ContactRelation(RelationKind.MANAGER, "Kai"),
            ContactRelation(RelationKind.SPOUSE, "Lee"),
        )
        val card = contact_to_vcard(Contact(id = "1", name = "Relation Test", email = "r@example.com", relations = relations))
        assertTrue(card.contains("RELATED;TYPE=parent;VALUE=text:Ana Lima"))
        assertTrue(card.contains("X-ABLabel:Godparent"))
        val parsed = parse_vcards(card).single()
        assertEquals(relations, parsed.relations)
    }

    @Test
    fun `vcard import reads other clients relation properties and skips uuid references`() {
        val card = listOf(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "FN:Import Test",
            "item1.X-ABRELATEDNAMES:Ana",
            "item1.X-ABLabel:_$!<Mother>!$" + "_",
            "item2.X-ABRELATEDNAMES:Rui",
            "item2.X-ABLabel:Godparent",
            "RELATED;TYPE=friend:Sam",
            "RELATED:urn:uuid:0d9a2c43-0000-4000-8000-000000000000",
            "X-ANDROID-CUSTOM:vnd.android.cursor.item/relation;Kai;7;;;;;;;;;;;;;",
            "END:VCARD",
        ).joinToString("\r\n")
        val parsed = parse_vcards(card).single()
        assertEquals(
            listOf(
                ContactRelation(RelationKind.MOTHER, "Ana"),
                ContactRelation(RelationKind.CUSTOM, "Rui", "Godparent"),
                ContactRelation(RelationKind.FRIEND, "Sam"),
                ContactRelation(RelationKind.MANAGER, "Kai"),
            ),
            parsed.relations,
        )
    }

    @Test
    fun `device types map both ways`() {
        for (kind in RelationKind.entries) {
            val relation = ContactRelation(kind, "V", if (kind == RelationKind.CUSTOM) "Godparent" else "")
            val (type, label) = relation_to_device_type(relation, "Sibling")
            assertEquals(relation, relation_from_device_type(type, label.orEmpty(), "V", "Sibling"))
        }
        assertEquals(ContactRelation(RelationKind.PARTNER, "V"), relation_from_device_type(4, "", "V"))
        assertEquals(ContactRelation(RelationKind.CUSTOM, "V", REFERRED_BY_LABEL), relation_from_device_type(11, "", "V"))
    }

    @Test
    fun `csv export and import keep relations`() {
        val relations = listOf(ContactRelation(RelationKind.MOTHER, "Ana Lima"), ContactRelation(RelationKind.CUSTOM, "Rui", "Godparent"))
        val csv = contacts_to_csv(listOf(Contact(id = "1", name = "Csv Test", email = "c@example.com", relations = relations)))
        assertTrue(csv.lines().first().contains("Relation 1 - Label"))
        val parsed = parse_contacts_file("contacts.csv", csv).single()
        assertEquals(relations, parsed.relations)
    }
}
