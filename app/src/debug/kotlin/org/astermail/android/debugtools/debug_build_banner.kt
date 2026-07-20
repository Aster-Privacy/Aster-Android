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

package org.astermail.android.debugtools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial

private const val production_release_url = "https://github.com/Aster-Privacy/Aster-Mail/releases/"

@Composable
fun debug_build_banner() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        debug_build_pill_inline(modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
fun debug_build_pill_inline(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val colors = AsterMaterial.colors

    Box(modifier = modifier) {
        Text(
            text = stringResource(R.string.debug_banner_label),
            color = colors.warning,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { expanded = true }
                .background(colors.bg_card.copy(alpha = 0.9f), RoundedCornerShape(50))
                .border(1.dp, colors.warning.copy(alpha = 0.5f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            debug_build_notice(on_collapse = { expanded = false })
        }
    }
}

@Composable
private fun debug_build_notice(on_collapse: () -> Unit) {
    val colors = AsterMaterial.colors
    val link_text = stringResource(R.string.debug_banner_link)
    val body = stringResource(R.string.debug_banner_body, link_text)
    val link_start = body.indexOf(link_text)

    val annotated = buildAnnotatedString {
        if (link_start < 0) {
            append(body)
        } else {
            append(body.substring(0, link_start))
            withLink(
                LinkAnnotation.Url(
                    url = production_release_url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = colors.accent_blue,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ) {
                append(link_text)
            }
            append(body.substring(link_start + link_text.length))
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clickable(onClick = on_collapse)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.debug_banner_label),
            color = colors.warning,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = annotated,
            color = colors.text_secondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
