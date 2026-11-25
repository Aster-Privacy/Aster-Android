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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.aster_reduce_motion

@Composable
internal fun search_hit_span_style(current: Boolean = false): SpanStyle {
    val colors = AsterMaterial.colors
    val palette = remember(colors.accent_blue, colors.is_dark) {
        search_hit_palette_for(colors.accent_blue.toArgb(), colors.is_dark)
    }
    return remember(palette, current) {
        if (current) {
            SpanStyle(background = Color(palette.current_background), color = Color(palette.current_foreground))
        } else {
            SpanStyle(background = Color(palette.background), color = Color(palette.foreground))
        }
    }
}

@Composable
internal fun search_row_text(
    text: AnnotatedString,
    pick: (search_highlight_terms) -> List<String>,
): AnnotatedString {
    val terms = local_search_row_terms.current ?: return text
    val chosen = pick(terms)
    if (chosen.isEmpty()) return text
    val style = search_hit_span_style()
    return remember(text, chosen, style) { highlight_annotated(text, chosen, style) }
}

@Composable
internal fun search_row_text(
    text: String,
    pick: (search_highlight_terms) -> List<String>,
): AnnotatedString {
    val terms = local_search_row_terms.current
    val chosen = terms?.let(pick).orEmpty()
    if (chosen.isEmpty()) return remember(text) { AnnotatedString(text) }
    val style = search_hit_span_style()
    return remember(text, chosen, style) { highlight_annotated(AnnotatedString(text), chosen, style) }
}

@Composable
internal fun search_preview_text(text: String): AnnotatedString? {
    val terms = local_search_row_terms.current?.body
    if (terms.isNullOrEmpty()) return null
    val style = search_hit_span_style()
    return remember(text, terms, style) {
        val window = search_snippet_window(text, terms)
        highlight_annotated(AnnotatedString(window.text), terms, style)
    }
}

@Composable
internal fun search_subject_text(subject: String): AnnotatedString? {
    val session = local_search_highlight.current ?: return null
    if (!session.active) return null
    val terms = session.terms.subject
    if (terms.isEmpty()) return null
    val style = search_hit_span_style()
    val current_style = search_hit_span_style(current = true)
    val current = session.current
    val focused = if (current in 0 until session.subject_count) current else -1
    return remember(subject, terms, style, current_style, focused) {
        highlight_annotated(AnnotatedString(subject), terms, style, current_style, focused)
    }
}

@Composable
internal fun search_match_bar(
    session: search_highlight_session,
    on_previous: () -> Unit,
    on_next: () -> Unit,
    on_clear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val reduce_motion = aster_reduce_motion()
    val total = session.total
    val visible = session.active && total > 0
    val enter = if (reduce_motion) fadeIn(tween(0)) else fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.92f)
    val exit = if (reduce_motion) fadeOut(tween(0)) else fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.92f)
    AnimatedVisibility(visible = visible, enter = enter, exit = exit, modifier = modifier) {
        val shown_total = total.coerceAtLeast(1)
        val position = session.current.coerceAtMost(shown_total - 1)
        val label = if (position >= 0) {
            stringResource(R.string.search_match_position, position + 1, shown_total)
        } else {
            pluralStringResource(R.plurals.search_match_count, shown_total, shown_total)
        }
        val spoken = if (position >= 0) {
            stringResource(R.string.search_match_position_description, position + 1, shown_total)
        } else {
            label
        }
        val shape = RoundedCornerShape(22.dp)
        val bar_bg = if (colors.is_dark) Color(0xFF26272B) else Color.White
        val border_color = colors.text_primary.copy(alpha = if (colors.is_dark) 0.12f else 0.08f)
        val accent_dot = remember(colors.accent_blue, colors.is_dark) {
            Color(search_hit_palette_for(colors.accent_blue.toArgb(), colors.is_dark).ring)
        }
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.22f),
                )
                .clip(shape)
                .background(bar_bg)
                .border(1.dp, border_color, shape)
                .padding(start = 6.dp, end = 4.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent_dot),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = spoken
                    },
            )
            Spacer(Modifier.width(2.dp))
            match_bar_button(TablerIcons.ChevronUp, stringResource(R.string.search_match_previous), on_previous)
            match_bar_button(TablerIcons.ChevronDown, stringResource(R.string.search_match_next), on_next)
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(1.dp)
                    .height(20.dp)
                    .background(border_color),
            )
            match_bar_button(TablerIcons.X, stringResource(R.string.search_match_clear), on_clear)
        }
    }
}

@Composable
private fun match_bar_button(
    icon: ImageVector,
    description: String,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    IconButton(onClick = on_click, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.text_secondary,
            modifier = Modifier.size(20.dp),
        )
    }
}
