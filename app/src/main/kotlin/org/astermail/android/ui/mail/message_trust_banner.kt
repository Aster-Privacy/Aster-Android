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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.ShieldX
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.tonal_surface_color
import org.astermail.android.mail.MessageTrust
import org.astermail.android.mail.TrustVerdict

@Composable
internal fun message_trust_banner(
    trust: MessageTrust,
    links_enabled: Boolean,
    reported: Boolean,
    on_enable_links: () -> Unit,
    on_report: () -> Unit,
    on_not_phishing: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val is_phishing = trust.verdict == TrustVerdict.phishing
    val bg = if (is_phishing) colors.danger else tonal_surface_color(colors, colors.warning)
    val tint = if (is_phishing) Color.White else colors.warning
    val text_color = if (is_phishing) Color.White else colors.text_primary
    val sub_text_color = if (is_phishing) Color.White.copy(alpha = 0.88f) else colors.text_secondary
    val action_bg = if (is_phishing) Color.White.copy(alpha = 0.18f) else tonal_surface_color(colors, colors.accent_blue)
    val action_text = if (is_phishing) Color.White else colors.accent_blue
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.xs)
            .clip(AsterShapes.island)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("message_trust_banner"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (is_phishing) TablerIcons.ShieldX else TablerIcons.AlertTriangle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(
                    if (is_phishing) R.string.trust_banner_phishing_title else R.string.trust_banner_suspicious_title,
                ),
                color = text_color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (is_phishing && !links_enabled) R.string.trust_banner_phishing_body else R.string.trust_banner_suspicious_body,
            ),
            color = sub_text_color,
            fontSize = 13.sp,
        )
        if (trust.reasons.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            for (reason in trust.reasons) {
                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    Text(text = "•", color = text_color, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(reason.line_res),
                        color = sub_text_color,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (is_phishing && !links_enabled) {
                trust_banner_action(stringResource(R.string.trust_banner_enable_links), action_bg, action_text, "trust_enable_links", on_enable_links)
            }
            if (!reported) {
                trust_banner_action(stringResource(R.string.trust_banner_report), action_bg, action_text, "trust_report", on_report)
            }
            trust_banner_action(stringResource(R.string.trust_banner_not_phishing), action_bg, action_text, "trust_not_phishing", on_not_phishing)
        }
    }
}

@Composable
private fun trust_banner_action(label: String, bg: Color, text_color: Color, tag: String, on_click: () -> Unit) {
    Text(
        text = label,
        color = text_color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(AsterShapes.control)
            .background(bg)
            .clickable(role = Role.Button, onClick = on_click)
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .testTag(tag),
    )
}
