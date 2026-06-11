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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.contacts.ContactsViewModel
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.inter_family
import org.astermail.android.ui.mail.SenderAvatar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    on_back: () -> Unit = {},
    on_open_contact: (String) -> Unit,
    on_create_contact: () -> Unit,
    on_open_drawer: (() -> Unit)? = null,
    vm: ContactsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val ui_state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter_favorites by remember { mutableStateOf(false) }
    var show_sync_confirm by remember { mutableStateOf(false) }

    val permission_launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.sync_device_contacts(context)
        } else {
            Toast.makeText(context, context.getString(R.string.contacts_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (ui_state.contacts.isEmpty()) vm.load_contacts()
    }

    LaunchedEffect(ui_state.sync_message) {
        ui_state.sync_message?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.clear_sync_message()
        }
    }

    LaunchedEffect(ui_state.error) {
        ui_state.error?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            vm.clear_flags()
        }
    }

    val filtered = remember(query, filter_favorites, ui_state.contacts) {
        ui_state.contacts
            .filter { if (filter_favorites) it.is_favorite else true }
            .filter {
                val q = query.trim().lowercase()
                if (q.isEmpty()) true
                else it.name.lowercase().contains(q) ||
                    it.email.lowercase().contains(q) ||
                    it.company.lowercase().contains(q)
            }
            .sortedBy { it.name.uppercase() }
    }

    val grouped = remember(filtered) {
        filtered.groupBy { letter_of(it.name) }.toSortedMap()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (on_open_drawer != null) {
                AsterIconButton(
                    icon = Icons.Filled.Menu,
                    content_description = stringResource(R.string.open_drawer),
                    onClick = on_open_drawer,
                )
            } else {
                AsterIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    content_description = stringResource(R.string.back),
                    onClick = on_back,
                )
            }
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = stringResource(R.string.contacts),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { show_sync_confirm = true },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = ui_state.is_syncing,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "sync_icon",
                ) { syncing ->
                    if (syncing) {
                        val rotation by rememberInfiniteTransition(label = "sync_spin")
                            .animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                                label = "sync_rotation",
                            )
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = stringResource(R.string.syncing),
                            tint = colors.accent_blue,
                            modifier = Modifier.size(22.dp).rotate(rotation),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = stringResource(R.string.sync_contacts),
                            tint = colors.text_secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        AsterDivider()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(SquircleShape(18.dp))
                    .background(colors.input_bg)
                    .padding(horizontal = AsterSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_contacts),
                            color = colors.text_muted,
                            fontSize = 14.sp,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text_primary),
                        cursorBrush = SolidColor(colors.accent_blue),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { query = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.clear),
                            tint = colors.text_muted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            FilterChip(stringResource(R.string.tab_all), !filter_favorites) { filter_favorites = false }
            FilterChip(stringResource(R.string.tab_favorites), filter_favorites) { filter_favorites = true }
            Spacer(Modifier.weight(1f))
            Text(
                text = context.resources.getQuantityString(R.plurals.contacts_count_plural, filtered.size, filtered.size),
                color = colors.text_muted,
                fontSize = 12.sp,
            )
        }
        AsterDivider()

        if (ui_state.is_loading && ui_state.contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue)
            }
            return@Column
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ui_state.error ?: stringResource(R.string.no_contacts_found),
                    color = colors.text_muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            grouped.forEach { (letter, group) ->
                stickyHeader {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bg_secondary)
                            .padding(
                                horizontal = AsterSpacing.lg,
                                vertical = 4.dp,
                            ),
                    ) {
                        Text(
                            text = letter,
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                itemsIndexed(group, key = { _, c -> c.id }) { i, c ->
                    ContactRow(
                        contact = c,
                        on_click = { on_open_contact(c.id) },
                    )
                    if (i < group.size - 1) AsterDivider()
                }
            }
        }
    }

    FloatingActionButton(
        onClick = on_create_contact,
        containerColor = colors.accent_blue,
        contentColor = Color.White,
        shape = SquircleShape(18.dp),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(AsterSpacing.lg)
            .testTag("fab_add_contact"),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.new_contact),
        )
    }
    }

    if (show_sync_confirm) {
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { show_sync_confirm = false },
            title = stringResource(R.string.sync_contacts),
            message = stringResource(R.string.sync_contacts_description),
            confirm_label = stringResource(R.string.sync),
            cancel_label = stringResource(R.string.cancel),
            on_confirm = {
                show_sync_confirm = false
                permission_launcher.launch(Manifest.permission.READ_CONTACTS)
            },
        )
    }
}

private fun letter_of(name: String): String {
    val t = name.trim()
    if (t.isEmpty()) return "#"
    val first = t.first().uppercaseChar()
    return if (first.isLetter()) first.toString() else "#"
}

@Composable
private fun FilterChip(label: String, active: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val bg = if (active) colors.accent_blue else colors.bg_tertiary
    val fg = if (active) Color.White else colors.text_secondary
    Box(
        modifier = Modifier
            .clip(SquircleShape(AsterRadius.pill))
            .background(bg)
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.md, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ContactRow(contact: Contact, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SenderAvatar(email = contact.email, name = contact.name)
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (contact.is_favorite) {
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            val subtitle = listOf(contact.company, contact.email)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.text_muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

