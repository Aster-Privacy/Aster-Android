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

package org.astermail.android.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.contacts.ContactRelation
import org.astermail.android.contacts.ContactSocial
import org.astermail.android.contacts.MAX_RELATIONS
import org.astermail.android.contacts.MAX_RELATION_LABEL
import org.astermail.android.contacts.MAX_RELATION_VALUE
import org.astermail.android.contacts.MAX_SOCIAL_LABEL
import org.astermail.android.contacts.MAX_SOCIAL_NETWORKS
import org.astermail.android.contacts.MAX_SOCIAL_VALUE
import org.astermail.android.contacts.RelationKind
import org.astermail.android.contacts.cap_code_points
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item

@Composable
fun relation_kind_names(): Map<RelationKind, String> = mapOf(
    RelationKind.FRIEND to stringResource(R.string.relation_friend),
    RelationKind.MANAGER to stringResource(R.string.relation_manager),
    RelationKind.PARTNER to stringResource(R.string.relation_partner),
    RelationKind.ASSISTANT to stringResource(R.string.relation_assistant),
    RelationKind.SPOUSE to stringResource(R.string.relation_spouse),
    RelationKind.RELATIVE to stringResource(R.string.relation_relative),
    RelationKind.CHILD to stringResource(R.string.relation_child),
    RelationKind.MOTHER to stringResource(R.string.relation_mother),
    RelationKind.FATHER to stringResource(R.string.relation_father),
    RelationKind.BROTHER to stringResource(R.string.relation_brother),
    RelationKind.SISTER to stringResource(R.string.relation_sister),
    RelationKind.CUSTOM to stringResource(R.string.relation_custom),
    RelationKind.PARENT to stringResource(R.string.relation_parent),
    RelationKind.SIBLING to stringResource(R.string.relation_sibling),
)

fun relation_display_name(relation: ContactRelation, names: Map<RelationKind, String>): String =
    if (relation.kind == RelationKind.CUSTOM) {
        relation.label.trim().ifEmpty { names[RelationKind.CUSTOM] ?: RelationKind.CUSTOM.english }
    } else {
        names[relation.kind] ?: relation.kind.english
    }

@Composable
fun social_type_names(): Map<String, String> = mapOf(
    "twitter" to stringResource(R.string.twitter),
    "linkedin" to stringResource(R.string.linkedin),
    "github" to stringResource(R.string.social_github),
    "instagram" to stringResource(R.string.social_instagram),
    "facebook" to stringResource(R.string.social_facebook),
    "mastodon" to stringResource(R.string.social_mastodon),
    "bluesky" to stringResource(R.string.social_bluesky),
    "other" to stringResource(R.string.contact_type_custom),
)

fun social_display_name(social: ContactSocial, names: Map<String, String>): String =
    if (social.is_custom) {
        social.label.trim().ifEmpty {
            if (social.type == "other") names["other"].orEmpty() else social.type
        }
    } else {
        names[social.type] ?: social.type
    }

data class EditableRelation(
    val kind: RelationKind = RelationKind.FRIEND,
    val value: String = "",
    val label: String = "",
) {
    fun to_relation(): ContactRelation =
        ContactRelation(kind, value, if (kind == RelationKind.CUSTOM) label else "")

    companion object {
        fun of(relation: ContactRelation) = EditableRelation(relation.kind, relation.value, relation.label)
    }
}

data class EditableSocial(
    val type: String = "other",
    val value: String = "",
    val label: String = "",
) {
    fun to_social(): ContactSocial = ContactSocial(type, value, label)

    companion object {
        fun of(social: ContactSocial) = EditableSocial(social.type, social.value, social.label)
    }
}

val editable_relation_list_saver: Saver<List<EditableRelation>, List<String>> = Saver(
    save = { list -> list.flatMap { listOf(it.kind.id, it.value, it.label) } },
    restore = { flat ->
        flat.chunked(3).filter { it.size == 3 }.map {
            EditableRelation(RelationKind.from_id(it[0]) ?: RelationKind.CUSTOM, it[1], it[2])
        }
    },
)

val editable_social_list_saver: Saver<List<EditableSocial>, List<String>> = Saver(
    save = { list -> list.flatMap { listOf(it.type, it.value, it.label) } },
    restore = { flat -> flat.chunked(3).filter { it.size == 3 }.map { EditableSocial(it[0], it[1], it[2]) } },
)

@Composable
private fun RelationKindPicker(
    relation: EditableRelation,
    names: Map<RelationKind, String>,
    on_pick: (RelationKind) -> Unit,
) {
    val colors = AsterMaterial.colors
    var expanded by remember { mutableStateOf(false) }
    val shown = relation_display_name(relation.to_relation(), names)
    val picker_label = stringResource(R.string.contact_relation_type)
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = picker_label) { expanded = true }
                .semantics { contentDescription = "$picker_label: $shown" }
                .padding(horizontal = AsterSpacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shown,
                color = colors.accent_blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(14.dp),
            )
        }
        aster_menu(expanded = expanded, on_dismiss = { expanded = false }) {
            for (kind in relation.to_relation().picker_kinds()) {
                aster_menu_item(
                    label = names[kind] ?: kind.english,
                    selected = kind == relation.kind,
                    on_click = {
                        expanded = false
                        on_pick(kind)
                    },
                )
            }
        }
    }
}

@Composable
fun ContactRelationRows(
    relations: List<EditableRelation>,
    on_change: (List<EditableRelation>) -> Unit,
) {
    val names = relation_kind_names()
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
        relations.forEachIndexed { index, relation ->
            fun update(next: EditableRelation) = on_change(relations.toMutableList().also { it[index] = next })
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        RelationKindPicker(relation, names) { update(relation.copy(kind = it)) }
                    }
                    AsterIconButton(
                        icon = TablerIcons.X,
                        content_description = stringResource(R.string.contact_remove_relation),
                        onClick = { on_change(relations.filterIndexed { i, _ -> i != index }) },
                    )
                }
                if (relation.kind == RelationKind.CUSTOM) {
                    AsterTextField(
                        value = relation.label,
                        onValueChange = { update(relation.copy(label = cap_code_points(it, MAX_RELATION_LABEL))) },
                        label = stringResource(R.string.contact_entry_label),
                        keyboard_options = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }
                AsterTextField(
                    value = relation.value,
                    onValueChange = { update(relation.copy(value = cap_code_points(it, MAX_RELATION_VALUE))) },
                    label = stringResource(R.string.contact_relation_name),
                    keyboard_options = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
        }
        if (relations.size < MAX_RELATIONS) {
            AddRowButton(stringResource(R.string.contact_add_relation)) { on_change(relations + EditableRelation()) }
        }
    }
}

@Composable
fun ContactSocialRows(
    socials: List<EditableSocial>,
    on_change: (List<EditableSocial>) -> Unit,
) {
    val colors = AsterMaterial.colors
    val names = social_type_names()
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
        socials.forEachIndexed { index, social ->
            fun update(next: EditableSocial) = on_change(socials.toMutableList().also { it[index] = next })
            val custom = social.to_social().is_custom
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = social_display_name(social.to_social(), names),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AsterSpacing.sm),
                    )
                    AsterIconButton(
                        icon = TablerIcons.X,
                        content_description = stringResource(R.string.contact_remove_social_link),
                        onClick = { on_change(socials.filterIndexed { i, _ -> i != index }) },
                    )
                }
                if (custom) {
                    AsterTextField(
                        value = social.label,
                        onValueChange = { update(social.copy(label = cap_code_points(it, MAX_SOCIAL_LABEL))) },
                        label = stringResource(R.string.contact_entry_label),
                        keyboard_options = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }
                AsterTextField(
                    value = social.value,
                    onValueChange = { update(social.copy(value = cap_code_points(it, MAX_SOCIAL_VALUE))) },
                    label = stringResource(R.string.contact_social_link_value),
                    keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
            }
        }
        if (socials.size < MAX_SOCIAL_NETWORKS) {
            AddRowButton(stringResource(R.string.contact_add_social_link)) { on_change(socials + EditableSocial()) }
        }
    }
}
