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

package org.astermail.android.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.contrast_body_text
import org.astermail.android.design.contrast_large_text
import org.astermail.android.design.ensure_contrast
import org.astermail.android.security.EmailAuthCheck
import org.astermail.android.security.EmailAuthCheckResult
import org.astermail.android.security.EmailAuthStatus
import org.astermail.android.security.EmailAuthSummary
import org.astermail.android.security.EmailAuthVerdict
import org.astermail.android.security.auth_display_domain
import org.astermail.android.security.summarize_email_authentication
import org.astermail.android.ui.settings.detail.tone_badge

fun summarize_email_authentication(msg: ThreadMessage): EmailAuthSummary? {
    if (msg.item_type != "received") return null
    return summarize_email_authentication(msg.spf_result, msg.dkim_result, msg.dmarc_result)
}

internal fun shows_auth_badge(verdict: EmailAuthVerdict): Boolean =
    verdict == EmailAuthVerdict.partial || verdict == EmailAuthVerdict.failed

private const val first_strong_isolate = '\u2068'
private const val pop_directional_isolate = '\u2069'
private const val non_breaking_hyphen = '\u2011'
private const val nowrap_domain_length = 36

private fun verdict_label_res(verdict: EmailAuthVerdict): Int =
    if (verdict == EmailAuthVerdict.partial) R.string.email_auth_partial else R.string.email_auth_failed

private fun verdict_description_res(verdict: EmailAuthVerdict): Int =
    if (verdict == EmailAuthVerdict.partial) R.string.email_auth_partial_desc else R.string.email_auth_failed_desc

private fun verdict_icon(verdict: EmailAuthVerdict): ImageVector =
    if (verdict == EmailAuthVerdict.partial) Icons.Rounded.Warning else Icons.Rounded.Cancel

@Composable
private fun verdict_tone(verdict: EmailAuthVerdict): Color {
    val colors = AsterMaterial.colors
    return if (verdict == EmailAuthVerdict.partial) colors.warning else colors.danger
}

@Composable
internal fun email_auth_badge(msg: ThreadMessage, modifier: Modifier = Modifier) {
    val summary = remember(msg.item_type, msg.spf_result, msg.dkim_result, msg.dmarc_result) {
        summarize_email_authentication(msg)
    } ?: return
    if (!shows_auth_badge(summary.verdict)) return
    val domain = remember(msg.sender_email) { auth_display_domain(msg.sender_email) }
    if (domain.isEmpty()) return
    var show_checks by remember(msg.id) { mutableStateOf(false) }
    val verdict_label = stringResource(verdict_label_res(summary.verdict))
    val label = stringResource(R.string.email_auth_label, verdict_label)
    tone_badge(
        text = verdict_label,
        tone = verdict_tone(summary.verdict),
        icon = verdict_icon(summary.verdict),
        icon_size = 12.dp,
        modifier = modifier
            .clip(AsterShapes.item)
            .semantics(mergeDescendants = true) {}
            .testTag("email_auth_badge")
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick {
                    show_checks = true
                    true
                }
            }
            .clickable { show_checks = true },
    )
    if (show_checks) {
        email_auth_dialog(summary = summary, domain = domain, on_dismiss = { show_checks = false })
    }
}

@Composable
private fun email_auth_dialog(summary: EmailAuthSummary, domain: String, on_dismiss: () -> Unit) {
    val colors = AsterMaterial.colors
    val surface = if (colors.is_glass) colors.solid_bg else colors.bg_card
    val shown_domain = if (domain.length <= nowrap_domain_length) domain.replace('-', non_breaking_hyphen) else domain
    val isolated_domain = "$first_strong_isolate$shown_domain$pop_directional_isolate"
    val sentence = stringResource(verdict_description_res(summary.verdict), isolated_domain)
    val description = remember(sentence, isolated_domain, colors.text_primary) {
        val at = sentence.indexOf(isolated_domain)
        buildAnnotatedString {
            if (at < 0) {
                append(sentence)
            } else {
                append(sentence.substring(0, at))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.text_primary)) {
                    append(isolated_domain)
                }
                append(sentence.substring(at + isolated_domain.length))
            }
        }
    }
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(verdict_label_res(summary.verdict)),
        body = {
            Column(modifier = Modifier.fillMaxWidth().testTag("email_auth_checks")) {
                Text(
                    text = description,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                summary.checks.forEach { result ->
                    email_auth_check_row(result = result, surface = surface)
                }
                Spacer(Modifier.height(AsterSpacing.sm))
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.border_secondary),
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.email_auth_source),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        },
        footer = {
            AsterDialogPrimaryButton(
                label = stringResource(R.string.done),
                onClick = on_dismiss,
            )
        },
    )
}

@Composable
private fun email_auth_check_row(result: EmailAuthCheckResult, surface: Color) {
    val colors = AsterMaterial.colors
    val (icon, tone) = when (result.status) {
        EmailAuthStatus.pass -> Icons.Rounded.CheckCircle to colors.success
        EmailAuthStatus.fail -> Icons.Rounded.Cancel to colors.danger
        EmailAuthStatus.other -> Icons.Rounded.Warning to colors.warning
        EmailAuthStatus.none, EmailAuthStatus.missing -> Icons.Rounded.RemoveCircle to colors.text_muted
    }
    val (name, purpose) = when (result.check) {
        EmailAuthCheck.spf -> "SPF" to R.string.email_auth_spf_purpose
        EmailAuthCheck.dkim -> "DKIM" to R.string.email_auth_dkim_purpose
        EmailAuthCheck.dmarc -> "DMARC" to R.string.email_auth_dmarc_purpose
    }
    val status = when (result.status) {
        EmailAuthStatus.pass -> stringResource(R.string.email_auth_status_pass)
        EmailAuthStatus.fail -> stringResource(R.string.email_auth_status_fail)
        EmailAuthStatus.none -> stringResource(R.string.email_auth_status_none)
        EmailAuthStatus.missing -> stringResource(R.string.email_auth_status_missing)
        EmailAuthStatus.other -> result.value
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("email_auth_check_${result.check.name}"),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ensure_contrast(tone, listOf(surface), contrast_large_text),
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = colors.text_primary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(purpose),
                color = colors.text_secondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = status,
            color = ensure_contrast(tone, listOf(surface), contrast_body_text),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
