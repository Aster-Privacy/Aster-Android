//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.guard

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Clock
import org.astermail.android.design.island_surface_color
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes

enum class guard_hold_kind(val wire: String) {
    forwarding_change("forwarding_change"),
    mail_rule_change("mail_rule_change"),
    mail_app_activation("mail_app_activation"),
    guard_device_remove("guard_device_remove"),
    recovery_email_change("recovery_email_change"),
    recovery_phone_change("recovery_phone_change"),
    password_change("password_change"),
    second_factor_off("second_factor_off"),
    data_export("data_export"),
    pgp_export("pgp_export"),
    guard_switches_off("guard_switches_off"),
    account_recovery("account_recovery"),
    rescue("rescue"),
    rescue_code_rotation("rescue_code_rotation"),
    unknown("");

    val skippable: Boolean get() = this != account_recovery && this != rescue && this != unknown

    val can_veto: Boolean get() = this == account_recovery || this == rescue

    companion object {
        fun from_wire(value: String?): guard_hold_kind =
            entries.firstOrNull { it != unknown && it.wire == value } ?: unknown
    }
}

data class guard_held_notice(val id: String, val kind: guard_hold_kind, val apply_after: Instant?)

fun guard_held_of(t: Throwable?): guard_held_notice? {
    var current = t
    var depth = 0
    while (current != null && depth < 8) {
        if (current is ApiError.GuardHeld) return current.to_notice()
        current = current.cause
        depth++
    }
    return null
}

fun guard_parse_instant(value: String?): Instant? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun ApiError.GuardHeld.to_notice(): guard_held_notice {
    guard_local_holds.record(id)
    return guard_held_notice(id = id, kind = guard_hold_kind.from_wire(kind), apply_after = guard_parse_instant(apply_after))
}

fun guard_format_instant(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(instant)

@StringRes
fun guard_hold_kind.label(): Int = when (this) {
    guard_hold_kind.forwarding_change -> R.string.guard_hold_forwarding_change
    guard_hold_kind.mail_rule_change -> R.string.guard_hold_mail_rule_change
    guard_hold_kind.mail_app_activation -> R.string.guard_hold_mail_app_activation
    guard_hold_kind.guard_device_remove -> R.string.guard_hold_guard_device_remove
    guard_hold_kind.recovery_email_change -> R.string.guard_hold_recovery_email_change
    guard_hold_kind.recovery_phone_change -> R.string.guard_hold_recovery_phone_change
    guard_hold_kind.password_change -> R.string.guard_hold_password_change
    guard_hold_kind.second_factor_off -> R.string.guard_hold_second_factor_off
    guard_hold_kind.data_export -> R.string.guard_hold_data_export
    guard_hold_kind.pgp_export -> R.string.guard_hold_pgp_export
    guard_hold_kind.guard_switches_off -> R.string.guard_hold_guard_switches_off
    guard_hold_kind.account_recovery -> R.string.guard_hold_account_recovery
    guard_hold_kind.rescue -> R.string.guard_hold_rescue
    guard_hold_kind.rescue_code_rotation -> R.string.guard_hold_rescue_code_rotation
    guard_hold_kind.unknown -> R.string.guard_hold_unknown
}

@Composable
fun GuardHeldBanner(notice: guard_held_notice, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val tone = guard_held_tone(colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AsterShapes.island)
            .background(island_surface_color(colors))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)
            .testTag("guard_held_banner"),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = TablerIcons.Clock,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.padding(top = 1.dp).size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.guard_badge),
                color = tone,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.guard_held_banner_title, stringResource(notice.kind.label())),
                color = colors.text_primary,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            val body = notice.apply_after?.let {
                stringResource(R.string.guard_held_banner_body, guard_format_instant(it))
            } ?: stringResource(R.string.guard_held_banner_body_no_time)
            Text(
                text = body,
                color = colors.text_secondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

internal fun guard_held_tone(colors: org.astermail.android.design.AsterSemanticColors): Color = colors.warning
