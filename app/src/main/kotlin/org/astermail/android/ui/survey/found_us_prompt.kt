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

package org.astermail.android.ui.survey

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Lock
import compose.icons.tablericons.MapPin
import org.astermail.android.R
import org.astermail.android.api.found_us.clean_found_us_detail
import org.astermail.android.api.found_us.count_found_us_chars
import org.astermail.android.api.found_us.found_us_max_detail_chars
import org.astermail.android.api.found_us.found_us_other
import org.astermail.android.api.found_us.found_us_sources
import org.astermail.android.api.found_us.limit_found_us_chars
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.survey.FoundUsFormError

@Composable
fun found_us_source_label(source: String): String = when (source) {
    "reddit" -> stringResource(R.string.found_us_source_reddit)
    "youtube" -> stringResource(R.string.found_us_source_youtube)
    "friend" -> stringResource(R.string.found_us_source_friend)
    "invite_link" -> stringResource(R.string.found_us_source_invite_link)
    "search_engine" -> stringResource(R.string.found_us_source_search_engine)
    "comparison_site" -> stringResource(R.string.found_us_source_comparison_site)
    "privacy_directory" -> stringResource(R.string.found_us_source_privacy_directory)
    "article" -> stringResource(R.string.found_us_source_article)
    "app_store" -> stringResource(R.string.found_us_source_app_store)
    "github" -> stringResource(R.string.found_us_source_github)
    "social_media" -> stringResource(R.string.found_us_source_social_media)
    "short_video" -> stringResource(R.string.found_us_source_short_video)
    "forum" -> stringResource(R.string.found_us_source_forum)
    "ai_assistant" -> stringResource(R.string.found_us_source_ai_assistant)
    else -> stringResource(R.string.found_us_source_other)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun found_us_banner(
    choosing_dismissal: Boolean,
    on_answer: () -> Unit,
    on_dismiss: () -> Unit,
    on_remind_later: () -> Unit,
    on_dont_ask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AsterShapes.island)
            .background(colors.accent_blue)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = TablerIcons.MapPin,
                contentDescription = null,
                tint = colors.on_accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.found_us_banner_message),
                color = colors.on_accent,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(AsterSpacing.xs))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
        ) {
            if (choosing_dismissal) {
                found_us_banner_pill(stringResource(R.string.found_us_remind_later), false, on_remind_later)
                found_us_banner_pill(stringResource(R.string.found_us_dont_ask), false, on_dont_ask)
            } else {
                found_us_banner_pill(stringResource(R.string.found_us_banner_answer), true, on_answer)
                found_us_banner_pill(stringResource(R.string.found_us_banner_dismiss), false, on_dismiss)
            }
        }
    }
}

@Composable
private fun found_us_banner_pill(label: String, emphasis: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val shape = AsterShapes.pill
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(colors.on_accent.copy(alpha = if (emphasis) 0.24f else 0.12f))
            .clickable(role = Role.Button, onClick = on_click)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = colors.on_accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun found_us_radio_dot(selected: Boolean) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent_blue else Color.Transparent)
            .border(1.5.dp, if (selected) colors.accent_blue else colors.text_tertiary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.on_accent),
            )
        }
    }
}

@Composable
fun found_us_dialog(
    account_id: String?,
    is_sending: Boolean,
    on_close: () -> Unit,
    on_submit: (source: String?, detail: String) -> FoundUsFormError?,
    on_skip: () -> Unit,
) {
    var source by rememberSaveable(account_id) { mutableStateOf<String?>(null) }
    var detail by rememberSaveable(account_id) { mutableStateOf("") }
    var form_error by rememberSaveable(account_id) { mutableStateOf<FoundUsFormError?>(null) }
    var error_nonce by remember { mutableIntStateOf(0) }
    val send = {
        val error = on_submit(source, detail)
        form_error = error
        if (error != null) error_nonce += 1
    }
    AsterDialog(
        on_dismiss = { if (!is_sending) on_close() },
        title = stringResource(R.string.found_us_modal_title),
        message = stringResource(R.string.found_us_modal_subtitle),
        is_busy = is_sending,
        body = {
            found_us_dialog_body(
                source = source,
                detail = detail,
                form_error = form_error,
                error_nonce = error_nonce,
                is_sending = is_sending,
                on_source = {
                    source = it
                    form_error = null
                },
                on_detail = {
                    detail = limit_found_us_chars(it)
                    if (form_error == FoundUsFormError.DETAIL) form_error = null
                },
                on_send = send,
            )
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.found_us_skip),
                onClick = on_skip,
                modifier = Modifier.weight(1f),
                enabled = !is_sending,
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.found_us_send),
                onClick = send,
                modifier = Modifier.weight(1f),
                enabled = !is_sending,
                is_loading = is_sending,
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun found_us_dialog_body(
    source: String?,
    detail: String,
    form_error: FoundUsFormError?,
    error_nonce: Int,
    is_sending: Boolean,
    on_source: (String) -> Unit,
    on_detail: (String) -> Unit,
    on_send: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val is_other = source == found_us_other
    val field_focus = remember { FocusRequester() }
    val error_view = remember { BringIntoViewRequester() }
    val error_message = when (form_error) {
        FoundUsFormError.PICK -> stringResource(R.string.found_us_pick_one_error)
        FoundUsFormError.DETAIL -> stringResource(R.string.found_us_other_required_error)
        null -> null
    }

    LaunchedEffect(is_other) {
        if (is_other) runCatching { field_focus.requestFocus() }
    }
    LaunchedEffect(error_nonce) {
        if (error_nonce == 0) return@LaunchedEffect
        if (form_error == FoundUsFormError.DETAIL) runCatching { field_focus.requestFocus() }
        error_view.bringIntoView()
    }

    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            found_us_sources.forEach { option ->
                val selected = source == option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(AsterShapes.item)
                        .selectable(
                            selected = selected,
                            enabled = !is_sending,
                            role = Role.RadioButton,
                            onClick = { on_source(option) },
                        )
                        .padding(horizontal = AsterSpacing.xs, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    found_us_radio_dot(selected)
                    Text(
                        text = found_us_source_label(option),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (is_other) {
            val other_label = stringResource(R.string.found_us_other_label)
            val detail_error = if (form_error == FoundUsFormError.DETAIL) error_message else null
            Column {
                AsterTextField(
                    value = detail,
                    onValueChange = on_detail,
                    modifier = Modifier
                        .focusRequester(field_focus)
                        .semantics(mergeDescendants = true) {
                            if (detail_error != null) error(detail_error)
                        },
                    label = other_label,
                    placeholder = stringResource(R.string.found_us_other_placeholder),
                    enabled = !is_sending,
                    singleLine = true,
                    rest_border = true,
                    keyboard_options = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboard_actions = KeyboardActions(onSend = { on_send() }),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.found_us_char_count,
                        count_found_us_chars(clean_found_us_detail(detail)),
                        found_us_max_detail_chars,
                    ),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (error_message != null) {
            Text(
                text = error_message,
                color = colors.danger,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(error_view)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
            )
        } else {
            Box(Modifier.bringIntoViewRequester(error_view))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(top = AsterSpacing.xs),
        ) {
            Icon(
                imageVector = TablerIcons.Lock,
                contentDescription = null,
                tint = colors.text_secondary,
                modifier = Modifier.padding(top = 1.dp).size(16.dp),
            )
            Text(
                text = stringResource(R.string.found_us_privacy_note),
                color = colors.text_secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
