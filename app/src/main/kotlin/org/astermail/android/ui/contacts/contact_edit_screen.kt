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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.contacts.ContactsViewModel
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.ui.mail.SenderAvatar

@Composable
fun ContactEditScreen(
    contact_id: String?,
    on_back: () -> Unit,
    on_saved: () -> Unit,
    vm: ContactsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val ui_state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(contact_id) {
        if (contact_id != null) vm.load_contact(contact_id)
    }

    val context = LocalContext.current
    var save_requested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(ui_state.save_success) {
        if (ui_state.save_success && save_requested) {
            save_requested = false
            vm.clear_flags()
            on_saved()
        }
    }

    LaunchedEffect(ui_state.error) {
        val message = ui_state.error
        if (message != null && save_requested) {
            save_requested = false
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            vm.clear_flags()
        }
    }

    val source = ui_state.selected_contact?.takeIf { it.id == contact_id }
    val contact_unavailable = contact_id != null && source == null

    var name by rememberSaveable { mutableStateOf(source?.name.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(source?.email.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(source?.phone.orEmpty()) }
    var company by rememberSaveable { mutableStateOf(source?.company.orEmpty()) }
    var title by rememberSaveable { mutableStateOf(source?.title.orEmpty()) }
    var work_email by rememberSaveable { mutableStateOf(source?.work_email.orEmpty()) }
    var work_phone by rememberSaveable { mutableStateOf(source?.work_phone.orEmpty()) }
    var birthday by rememberSaveable { mutableStateOf(source?.birthday.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(source?.address.orEmpty()) }
    var city by rememberSaveable { mutableStateOf(source?.city.orEmpty()) }
    var region by rememberSaveable { mutableStateOf(source?.region.orEmpty()) }
    var postal_code by rememberSaveable { mutableStateOf(source?.postal_code.orEmpty()) }
    var country by rememberSaveable { mutableStateOf(source?.country.orEmpty()) }
    var website by rememberSaveable { mutableStateOf(source?.website.orEmpty()) }
    var twitter by rememberSaveable { mutableStateOf(source?.twitter.orEmpty()) }
    var linkedin by rememberSaveable { mutableStateOf(source?.linkedin.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(source?.notes.orEmpty()) }
    var active_tab by rememberSaveable { mutableStateOf(0) }
    var loaded_contact_id by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(source) {
        if (source != null && source.id != loaded_contact_id) {
            loaded_contact_id = source.id
            name = source.name
            email = source.email
            phone = source.phone
            company = source.company
            title = source.title
            work_email = source.work_email
            work_phone = source.work_phone
            birthday = source.birthday
            address = source.address
            city = source.city
            region = source.region
            postal_code = source.postal_code
            country = source.country
            website = source.website
            twitter = source.twitter
            linkedin = source.linkedin
            notes = source.notes
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsterIconButton(
                icon = TablerIcons.ArrowLeft,
                auto_mirror = true,
                content_description = stringResource(R.string.back),
                onClick = on_back,
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = if (contact_id == null) stringResource(R.string.new_contact) else stringResource(R.string.edit_contact),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            val can_save = name.isNotBlank() || email.isNotBlank() || phone.isNotBlank()
            AsterGhostButton(
                label = stringResource(R.string.save),
                enabled = can_save && !ui_state.is_loading && !contact_unavailable,
                onClick = {
                    val contact = Contact(
                        id = contact_id ?: "",
                        name = name,
                        email = email,
                        phone = phone,
                        company = company,
                        title = title,
                        work_email = work_email,
                        work_phone = work_phone,
                        birthday = birthday,
                        address = address,
                        city = city,
                        region = region,
                        postal_code = postal_code,
                        country = country,
                        website = website,
                        twitter = twitter,
                        linkedin = linkedin,
                        notes = notes,
                        is_favorite = source?.is_favorite ?: false,
                        groups = source?.groups ?: emptyList(),
                        raw_json = source?.raw_json.orEmpty(),
                    )
                    save_requested = true
                    vm.save_contact(contact, contact_id)
                },
            )
        }
        AsterDivider()

        if (contact_unavailable && !ui_state.is_loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AsterSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.failed_to_load),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text_secondary,
                    modifier = Modifier.weight(1f),
                )
                AsterGhostButton(
                    label = stringResource(R.string.retry),
                    onClick = { if (contact_id != null) vm.load_contact(contact_id) },
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = AsterSpacing.xxl),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AsterSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SenderAvatar(
                    email = email,
                    name = name,
                    size = 80.dp,
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = name.ifBlank { stringResource(R.string.unnamed_contact) },
                    color = if (name.isBlank()) colors.text_muted else colors.text_primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
            }

            TabSegment(
                tabs = listOf(
                    stringResource(R.string.tab_basic),
                    stringResource(R.string.tab_details),
                    stringResource(R.string.tab_address),
                    stringResource(R.string.tab_social),
                ),
                active = active_tab,
                on_select = { active_tab = it },
            )
            Spacer(Modifier.height(AsterSpacing.md))

            when (active_tab) {
                0 -> FormSection(title = stringResource(R.string.tab_basic)) {
                    FormField(stringResource(R.string.name), name) { name = it }
                    FormField(stringResource(R.string.email), email) { email = it }
                    FormField(stringResource(R.string.phone), phone) { phone = it }
                }
                1 -> FormSection(title = stringResource(R.string.tab_details)) {
                    FormField(stringResource(R.string.company), company) { company = it }
                    FormField(stringResource(R.string.title), title) { title = it }
                    FormField(stringResource(R.string.work_email), work_email) { work_email = it }
                    FormField(stringResource(R.string.work_phone), work_phone) { work_phone = it }
                    FormField(stringResource(R.string.birthday), birthday) { birthday = it }
                    FormField(stringResource(R.string.notes), notes) { notes = it }
                }
                2 -> FormSection(title = stringResource(R.string.tab_address)) {
                    FormField(stringResource(R.string.street), address) { address = it }
                    FormField(stringResource(R.string.city), city) { city = it }
                    FormField(stringResource(R.string.region), region) { region = it }
                    FormField(stringResource(R.string.postal_code), postal_code) { postal_code = it }
                    FormField(stringResource(R.string.country), country) { country = it }
                }
                3 -> FormSection(title = stringResource(R.string.tab_social)) {
                    FormField(stringResource(R.string.website), website) { website = it }
                    FormField(stringResource(R.string.twitter), twitter) { twitter = it }
                    FormField(stringResource(R.string.linkedin), linkedin) { linkedin = it }
                }
            }
        }
    }
}

@Composable
private fun TabSegment(
    tabs: List<String>,
    active: Int,
    on_select: (Int) -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg)
            .clip(SquircleShape(18.dp))
            .background(colors.bg_tertiary)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { idx, label ->
            val is_active = idx == active
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(SquircleShape(AsterRadius.sm))
                    .background(if (is_active) colors.bg_card else Color.Transparent)
                    .clickable { on_select(idx) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (is_active) colors.text_primary else colors.text_muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg),
    ) {
        Text(
            text = title,
            color = colors.text_tertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = AsterSpacing.sm, bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AsterSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            content()
        }
    }
}

@Composable
private fun FormField(label: String, value: String, on_change: (String) -> Unit) {
    AsterTextField(
        value = value,
        onValueChange = on_change,
        label = label,
        placeholder = label,
    )
}
