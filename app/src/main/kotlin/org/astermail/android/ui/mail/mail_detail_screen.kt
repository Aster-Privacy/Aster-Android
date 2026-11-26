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

import org.astermail.android.ui.common.lazy_top_fade
import org.astermail.android.ui.common.aster_modal_sheet
import org.astermail.android.ui.common.vertical_scroll_with_indicator
import compose.icons.TablerIcons
import kotlinx.coroutines.CancellationException
import org.astermail.android.design.auto_mirrored
import org.astermail.android.ui.common.sheet_container_color
import org.astermail.android.ui.common.show_copy_result_toast
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.write_to_clipboard
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.*
import compose.icons.tablericons.Plus

import org.astermail.android.ui.icons.pin_icon
import org.astermail.android.ui.icons.pin_icon_filled
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_surface
import org.astermail.android.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.labels.as_conversation_member
import org.astermail.android.subscriptions.MailingListsViewModel
import org.astermail.android.ui.common.TopToastState
import org.astermail.android.ui.common.app_toast
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.tonal_surface_color
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.disabled_surface_color
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.skeleton_reveal
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.AsterExpandVisibility
import org.astermail.android.design.aster_expand_in
import org.astermail.android.design.aster_collapse_out
import org.astermail.android.ui.common.horizontal_pan_signal
import org.astermail.android.ui.common.horizontal_scroll_indicator
import org.astermail.android.ui.common.local_horizontal_pan_signal
import org.astermail.android.ui.common.vertical_scroll_bar
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.mail.AttachmentIntegrityException
import org.astermail.android.mail.AttachmentKeyUnavailableException
import org.astermail.android.mail.DecryptedReaction
import org.astermail.android.mail.MailViewModel
import org.astermail.android.mail.can_move_to_inbox
import org.astermail.android.mail.body_starts_with
import org.astermail.android.mail.group_reaction_chips
import org.astermail.android.mail.strip_non_rendered_blocks
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.translation.TranslationDownloadPolicy
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.design.mirror_in_rtl
import org.astermail.android.util.clip_with_ellipsis
import org.astermail.android.design.acrylic
import org.astermail.android.design.acrylic_backdrop
import org.astermail.android.ui.common.glass_bar
import org.astermail.android.ui.common.chrome_surface
import org.astermail.android.ui.common.page_surface

private val placeholder_body_height = 96.dp
private const val thread_draft_remove_ms = 260L


private val FITTED_VIEWPORT_WIDTH = Regex("name=\"viewport\" content=\"width=([0-9]{3,4})")

internal fun fitted_viewport_width(document: String): Int? =
    FITTED_VIEWPORT_WIDTH.find(document)?.groupValues?.get(1)?.toIntOrNull()

private val MEASURE_PROBE_HEIGHT = 24.dp

internal fun failed_image_response(): android.webkit.WebResourceResponse =
    android.webkit.WebResourceResponse(
        "image/gif",
        null,
        404,
        "Not Found",
        mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"),
        java.io.ByteArrayInputStream(ByteArray(0)),
    )

private fun escape_body_text(raw: String): String = raw
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun strip_markup(raw: String): String {
    val without_hidden_blocks = strip_non_rendered_blocks(raw)
    val out = StringBuilder(without_hidden_blocks.length)
    var in_tag = false
    for (ch in without_hidden_blocks) {
        when {
            ch == '<' -> in_tag = true
            ch == '>' -> {
                if (in_tag) out.append(' ')
                in_tag = false
            }
            !in_tag -> out.append(ch)
        }
    }
    return out.toString()
}

private fun plain_text_fallback_body(raw: String): String {
    val text = strip_markup(raw).trim()
    return "<pre dir=\"auto\" style=\"white-space:pre-wrap;word-wrap:break-word;font-family:inherit;unicode-bidi:plaintext\">" +
        escape_body_text(text) + "</pre>"
}

internal const val FALLBACK_BODY_CSP =
    "default-src 'none'; script-src 'none'; style-src 'unsafe-inline'; img-src data:; " +
        "base-uri 'none'; form-action 'none'; frame-src 'none'; object-src 'none'"

internal fun plain_text_fallback_document(raw: String, bg_hex: String, fg_hex: String): String =
    "<!DOCTYPE html><html><head>" +
        "<meta http-equiv=\"Content-Security-Policy\" content=\"" + FALLBACK_BODY_CSP + "\">" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
        "</head><body style=\"margin:0;padding:12px;background:" + bg_hex + ";color:" + fg_hex + ";" +
        "font-family:-apple-system,Roboto,sans-serif;font-size:15px;line-height:1.5\">" +
        "<div id=\"m\">" + plain_text_fallback_body(raw) + "</div></body></html>"

private const val BODY_REPAINT_STEP_DP = 24f
private const val BODY_PENDING_TIMEOUT_MS = 12_000L
private const val SLOW_LINK_BODY_PENDING_TIMEOUT_MS = 65_000L

private val EXTERNAL_RESOURCE_PATTERN = Regex(
    """(?:src\s*=\s*["']https?://|background\s*=\s*["']https?://|url\s*\(\s*["']?https?://|@font-face)""",
    RegexOption.IGNORE_CASE,
)

private val FONT_FACE_PATTERN = Regex("""@font-face""", RegexOption.IGNORE_CASE)

private val LINK_STYLESHEET_PATTERN = Regex(
    """<link\b[^>]*rel\s*=\s*["']stylesheet["'][^>]*>""",
    RegexOption.IGNORE_CASE,
)

internal enum class ExternalContentType { image, tracker, font, stylesheet }

internal data class ExternalContentItem(
    val type: ExternalContentType,
    val url: String,
)

internal data class ExternalContentCounts(
    val image_count: Int,
    val tracker_count: Int,
    val font_count: Int,
    val css_count: Int,
    val items: List<ExternalContentItem> = emptyList(),
) {
    val total: Int get() = image_count + tracker_count + font_count + css_count
}

private const val EXTERNAL_ITEM_LIST_CAP = 60

private val HREF_URL_PATTERN = Regex("""(?<![-\w])href\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
private val FONT_URL_PATTERN = Regex("""url\s*\(\s*["']?(https?://[^"')]+)""", RegexOption.IGNORE_CASE)

private fun external_display_url(url: String): String {
    val trimmed = url.trim()
    if (trimmed.length <= 60) return trimmed
    return try {
        val parsed = android.net.Uri.parse(trimmed)
        val host = parsed.host ?: return trimmed.clip_with_ellipsis(60)
        val path = parsed.path.orEmpty()
        val remaining = 60 - host.length - 3
        if (remaining > 10) {
            host + if (path.length > remaining) path.clip_with_ellipsis(remaining) else path
        } else {
            "$host/…"
        }
    } catch (_: Throwable) {
        trimmed.clip_with_ellipsis(60)
    }
}

internal fun count_external_content(html: String, report: EmailHtmlSanitizer.TrackerReport): ExternalContentCounts {
    val items = mutableListOf<ExternalContentItem>()
    for (url in report.image_urls) {
        if (items.size < EXTERNAL_ITEM_LIST_CAP) items.add(ExternalContentItem(ExternalContentType.image, url))
    }
    for (url in report.pixel_urls) {
        if (items.size < EXTERNAL_ITEM_LIST_CAP) items.add(ExternalContentItem(ExternalContentType.tracker, url))
    }
    val fonts = FONT_FACE_PATTERN.findAll(html).count()
    val css = LINK_STYLESHEET_PATTERN.findAll(html).count()
    if (fonts > 0) {
        FONT_URL_PATTERN.findAll(html).take(EXTERNAL_ITEM_LIST_CAP).forEach { m ->
            if (items.size < EXTERNAL_ITEM_LIST_CAP) {
                items.add(ExternalContentItem(ExternalContentType.font, m.groupValues[1]))
            }
        }
    }
    LINK_STYLESHEET_PATTERN.findAll(html).forEach { m ->
        if (items.size < EXTERNAL_ITEM_LIST_CAP) {
            val url = HREF_URL_PATTERN.find(m.value)?.groupValues?.get(1)
            if (!url.isNullOrBlank()) {
                items.add(ExternalContentItem(ExternalContentType.stylesheet, url))
            }
        }
    }
    return ExternalContentCounts(report.image_count, report.pixel_count, fonts, css, items)
}

internal data class MessageContentAnalysis(
    val tracker_report: EmailHtmlSanitizer.TrackerReport = EmailHtmlSanitizer.TrackerReport(),
    val external_counts: ExternalContentCounts = ExternalContentCounts(0, 0, 0, 0),
)

internal fun analyze_message_content(html: String?): MessageContentAnalysis {
    val report = EmailHtmlSanitizer.analyze_trackers(html)
    val counts = if (html != null) count_external_content(html, report) else ExternalContentCounts(0, 0, 0, 0)
    return MessageContentAnalysis(report, counts)
}

internal fun needs_cached_translation_source(current: String?, translate_active: Boolean): Boolean =
    translate_active && current == null

private val PROXY_CSS_URL_PATTERN = Regex(
    """(url\(\s*["']?)((?:https?:)?//[^"')\s]+)(["']?\s*\))""",
    RegexOption.IGNORE_CASE,
)

private val CID_SRC_PATTERN = Regex(
    """(src\s*=\s*["'])cid:([^"']+)(["'])""",
    RegexOption.IGNORE_CASE,
)

internal fun trim_email_runon(address: String): String {
    val at = address.lastIndexOf('@')
    if (at < 0) return address
    val domain = address.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    if (dot < 0) return address
    var tld = domain.substring(dot + 1)
    val seam = Regex("[a-z][A-Z]").find(tld)
    if (seam != null) tld = tld.substring(0, seam.range.first + 1)
    if (tld.length > 24) tld = tld.substring(0, 24)
    return address.substring(0, at + 1) + domain.substring(0, dot + 1) + tld
}

internal fun secure_image_url(url: String): String =
    if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

private fun is_proxyable_url(value: String): Boolean {
    val lower = value.trim().lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//")
}

internal fun proxied_image_target(raw_url: String, proxy_base: String): String {
    val url = raw_url
        .replace("&amp;", "&")
        .replace("&#38;", "&")
        .replace("&#x26;", "&")
        .trim()
    val absolute = secure_image_url(if (url.startsWith("//")) "https:$url" else url)
    if (absolute.startsWith(proxy_base)) return absolute
    if (absolute.startsWith(INLINE_IMAGE_URL_PREFIX)) return absolute
    return proxy_base + java.net.URLEncoder.encode(absolute, "UTF-8")
}

internal fun proxy_external_urls(html: String, proxy_base: String): String {
    if (html.isBlank()) return html
    fun to_proxied(raw_url: String): String = proxied_image_target(raw_url, proxy_base)

    fun proxied_css(css: String): String =
        if (css.contains("url(", ignoreCase = true)) {
            PROXY_CSS_URL_PATTERN.replace(css) { match ->
                "${match.groupValues[1]}${to_proxied(match.groupValues[2])}${match.groupValues[3]}"
            }
        } else {
            css
        }

    fun proxied_srcset(value: String): String = value.split(",").mapNotNull { entry ->
        val parts = entry.trim().split(Regex("\\s+"), 2)
        val url = parts[0]
        val descriptor = if (parts.size > 1) " ${parts[1]}" else ""
        if (is_proxyable_url(url)) "${to_proxied(url)}$descriptor" else null
    }.joinToString(", ")

    return try {
        val doc = org.jsoup.Jsoup.parseBodyFragment(html)
        doc.outputSettings(org.jsoup.nodes.Document.OutputSettings().prettyPrint(false))
        for (element in doc.select("[src]")) {
            val raw = element.attr("src")
            if (is_proxyable_url(raw)) element.attr("src", to_proxied(raw))
        }
        for (element in doc.select("[background]")) {
            val raw = element.attr("background")
            if (is_proxyable_url(raw)) element.attr("background", to_proxied(raw))
        }
        for (element in doc.select("[srcset]")) {
            element.attr("srcset", proxied_srcset(element.attr("srcset")))
        }
        for (element in doc.select("[style]")) {
            val raw = element.attr("style")
            val rewritten = proxied_css(raw)
            if (rewritten != raw) element.attr("style", rewritten)
        }
        for (element in doc.select("style")) {
            val raw = element.data()
            val rewritten = proxied_css(raw)
            if (rewritten != raw) {
                element.empty()
                element.appendChild(org.jsoup.nodes.DataNode(rewritten))
            }
        }
        doc.body().html()
    } catch (_: Throwable) {
        html
    }
}

private const val INLINE_IMAGE_TOTAL_BUDGET_BYTES = 48 * 1024 * 1024
private const val TRANSPARENT_PIXEL_DATA_URI =
    "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=="

internal fun normalized_content_id(raw: String?): String =
    raw?.trim()?.trim('<', '>').orEmpty()

internal fun inline_reference_key(raw: String?): String =
    normalized_content_id(raw).lowercase()

internal const val INLINE_IMAGE_WAIT_MS = 1500L

internal fun inline_cid_references(html: String): Set<String> =
    if (!html.contains("cid:", ignoreCase = true)) emptySet()
    else CID_SRC_PATTERN.findAll(html).map { inline_reference_key(it.groupValues[2]) }.filter { it.isNotBlank() }.toSet()

internal fun inline_cids_resolved(references: Set<String>, inline_images: Map<String, String>): Boolean =
    references.all { inline_images.containsKey(it) || inline_images.containsKey(it.substringBefore('@')) }

internal fun resolve_inline_cids(html: String, inline_images: Map<String, String>): String {
    if (!html.contains("cid:", ignoreCase = true)) return html
    val sized = INLINE_IMG_TAG_PATTERN.replace(html) { tag_match ->
        val tag = tag_match.value
        var size: Pair<Int, Int>? = null
        val rewritten = CID_SRC_PATTERN.replace(tag) { match ->
            val reference = inline_reference_key(match.groupValues[2])
            val resolved = inline_images[reference]
                ?: inline_images[reference.substringBefore('@')]
                ?: TRANSPARENT_PIXEL_DATA_URI
            if (resolved != TRANSPARENT_PIXEL_DATA_URI) size = InlineImageStore.size_for(resolved)
            "${match.groupValues[1]}$resolved${match.groupValues[3]}"
        }
        with_intrinsic_size(rewritten, size)
    }
    if (!sized.contains("cid:", ignoreCase = true)) return sized
    return CID_SRC_PATTERN.replace(sized) { match ->
        val reference = inline_reference_key(match.groupValues[2])
        val resolved = inline_images[reference]
            ?: inline_images[reference.substringBefore('@')]
            ?: TRANSPARENT_PIXEL_DATA_URI
        "${match.groupValues[1]}$resolved${match.groupValues[3]}"
    }
}

private val INLINE_IMG_TAG_PATTERN = Regex("""<img\s[^>]*>""", RegexOption.IGNORE_CASE)

private val IMG_WIDTH_ATTR = Regex("""\swidth\s*=\s*["']?\s*(\d+(?:\.\d+)?)\s*(?:px)?\s*["']?(?=[\s/>])""", RegexOption.IGNORE_CASE)

private val IMG_HEIGHT_ATTR = Regex("""\sheight\s*=""", RegexOption.IGNORE_CASE)

private val IMG_ANY_WIDTH_ATTR = Regex("""\swidth\s*=""", RegexOption.IGNORE_CASE)

internal fun with_intrinsic_size(tag: String, size: Pair<Int, Int>?): String {
    val (w, h) = size ?: return tag
    if (w <= 0 || h <= 0) return tag
    if (IMG_HEIGHT_ATTR.containsMatchIn(tag)) return tag
    val insert = if (IMG_ANY_WIDTH_ATTR.containsMatchIn(tag)) {
        val declared = IMG_WIDTH_ATTR.find(tag)?.groupValues?.get(1)?.toFloatOrNull() ?: return tag
        if (declared <= 0f) return tag
        " height=\"${(declared * h / w).toInt().coerceAtLeast(1)}\""
    } else {
        " width=\"$w\" height=\"$h\""
    }
    val end = if (tag.endsWith("/>")) tag.length - 2 else tag.length - 1
    return tag.substring(0, end).trimEnd() + insert + tag.substring(end)
}

internal fun inline_image_sources(
    body_html: String,
    attachments: List<MessageAttachment>,
): Map<String, String> {
    if (body_html.isBlank() || attachments.isEmpty()) return emptyMap()
    val referenced = CID_SRC_PATTERN.findAll(body_html)
        .map { inline_reference_key(it.groupValues[2]) }
        .filter { it.isNotBlank() }
        .toSet()
    if (referenced.isEmpty()) return emptyMap()
    val resolved = LinkedHashMap<String, String>()
    var budget = INLINE_IMAGE_TOTAL_BUDGET_BYTES
    for (att in attachments) {
        if (!att.content_type.startsWith("image/", ignoreCase = true)) continue
        val cid = inline_reference_key(att.content_id)
        val filename = att.filename.trim().lowercase()
        val aliases = buildList {
            if (cid.isNotBlank()) {
                add(cid)
                cid.substringBefore('@').takeIf { it.isNotBlank() && it != cid }?.let { add(it) }
            }
            if (filename.isNotBlank()) add(filename)
        }
        val used = aliases.filter { it in referenced }
        if (used.isEmpty()) continue
        if (used.all { resolved.containsKey(it) }) continue
        val local = org.astermail.android.mail.DecryptedMimeAttachments.bytes_for(att.id)
        if (local != null) {
            if (local.isEmpty() || local.size > org.astermail.android.mail.INLINE_IMAGE_MAX_BYTES || local.size > budget) continue
            budget -= local.size
            val local_key = InlineImageStore.content_key(used.first(), local)
            InlineImageStore.put(local_key, att.content_type, local)
            val local_url = InlineImageStore.url_for(local_key)
            InlineImageStore.remember_size(local_url, local)
            used.forEach { alias -> resolved.putIfAbsent(alias, local_url) }
            continue
        }
        val data = att.encrypted_data
        val nonce = att.data_nonce
        if (data.isNullOrBlank() || nonce.isNullOrBlank()) continue
        val bytes = runCatching {
            org.astermail.android.mail.MailRepository.decrypt_attachment_bytes(
                data,
                nonce,
                att.session_key.orEmpty(),
                att.mail_item_id,
                att.seq_num,
            )
        }.getOrNull() ?: continue
        if (bytes.isEmpty() || bytes.size > org.astermail.android.mail.INLINE_IMAGE_MAX_BYTES || bytes.size > budget) continue
        budget -= bytes.size
        val key = InlineImageStore.content_key(used.first(), bytes)
        InlineImageStore.put(key, att.content_type, bytes)
        val reload_session_key = att.session_key.orEmpty()
        val reload_mail_item_id = att.mail_item_id
        val reload_seq_num = att.seq_num
        InlineImageStore.register_reloader(
            key,
            InlineImageReloader(used.first(), att.content_type) {
                org.astermail.android.mail.MailRepository.decrypt_attachment_bytes(
                    data,
                    nonce,
                    reload_session_key,
                    reload_mail_item_id,
                    reload_seq_num,
                )
            },
        )
        val url = InlineImageStore.url_for(key)
        InlineImageStore.remember_size(url, bytes)
        used.forEach { alias -> resolved.putIfAbsent(alias, url) }
    }
    return resolved
}

private val GHOST_LOCAL_PATTERN = Regex("^[a-z]+\\.[a-z]+\\d{2}@", RegexOption.IGNORE_CASE)

@Composable
fun MailDetailScreen(
    email_id: String,
    on_back: () -> Unit,
    on_reply: (String, String?) -> Unit,
    on_reply_all: (String, String?) -> Unit,
    on_forward: (String, String?) -> Unit,
    on_archive: () -> Unit,
    on_delete: () -> Unit,
    on_next: (() -> Unit)? = null,
    on_previous: (() -> Unit)? = null,
    swipe_direction: Int = 0,
    chained: Boolean = false,
    on_navigate: ((String) -> Unit)? = null,
    mail_vm: MailViewModel = hiltViewModel(),
    settings_vm: SettingsViewModel = shared_settings_view_model(),
    subscriptions_vm: MailingListsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val density = LocalDensity.current
    val context = LocalContext.current
    val request_storage_access = org.astermail.android.util.remember_downloads_permission_gate()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val detail_swipe = remember_detail_swipe_state(email_id, swipe_direction)
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val privacy_blocks_external = setting_blocks_remote_images(settings_state.preferences)
    val traffic_blocks_external = org.astermail.android.network.low_network_active()
    val block_external_images = privacy_blocks_external || traffic_blocks_external
    val blocked_for_traffic_only = traffic_blocks_external && !privacy_blocks_external
    val offer_always_allow_external =
        settings_state.preferences?.load_remote_images != "never"
    val image_grant_allowed = allows_remote_image_grant(settings_state.preferences)
    val thread_state by mail_vm.thread_state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val reactions_enabled = settings_state.preferences?.reactions_enabled != false
    val fetched_require_encryption by produceState<Boolean?>(initialValue = null, email_id) {
        value = mail_vm.require_encryption_setting()
    }
    val require_encryption = settings_state.encryption_settings?.require_encryption
        ?: fetched_require_encryption
        ?: settings_state.preferences?.require_encryption
        ?: false
    val show_encryption_indicators = settings_state.preferences?.show_encryption_indicators != false
    val message_reactions by mail_vm.message_reactions.collectAsStateWithLifecycle()
    val reaction_placeholders by mail_vm.reaction_placeholders.collectAsStateWithLifecycle()
    val decrypt_retry_active by mail_vm.decrypt_retry_active.collectAsStateWithLifecycle()
    val locked_data_vm: org.astermail.android.mail.LockedDataViewModel = hiltViewModel()
    val locked_data_state by locked_data_vm.state.collectAsStateWithLifecycle()
    var show_recover_data_dialog by remember { mutableStateOf(false) }
    val has_message_sealed_to_missing_keys = thread_state.messages.any {
        it.is_undecryptable && it.is_sealed_to_missing_keys
    }
    val identity_changes by mail_vm.identity_changes.collectAsStateWithLifecycle()
    val unauthenticated_message_ids by mail_vm.unauthenticated_message_ids.collectAsStateWithLifecycle()
    val identity_changed_senders = remember(identity_changes) {
        identity_changes.mapNotNull {
            it.sender_email.takeIf { e -> e.isNotBlank() }
                ?.let { e -> org.astermail.android.mail.ratchet.PinIdentity.canonical_id(e) }
        }.toSet()
    }
    val reaction_picker_open_state = remember { mutableStateOf(false) }
    val left_after_unread_state = remember(email_id) { mutableStateOf(false) }

    LaunchedEffect(reactions_enabled) {
        mail_vm.set_reactions_enabled(reactions_enabled)
    }

    val current_on_back by rememberUpdatedState(on_back)
    LaunchedEffect(mail_vm) {
        mail_vm.thread_locked_away.collect { current_on_back() }
    }

    val detail_grouping = detail_conversation_grouping(
        preference = settings_state.preferences?.conversation_grouping,
        preferences_locked = settings_state.preferences_locked,
        fallback = mail_vm.is_conversation_grouping_enabled,
    )

    var load_requested by remember(email_id) { mutableStateOf(false) }
    LaunchedEffect(email_id, detail_grouping) {
        mail_vm.set_conversation_grouping(detail_grouping)
        mail_vm.load_thread(email_id)
        load_requested = true
    }

    LaunchedEffect(has_message_sealed_to_missing_keys) {
        if (has_message_sealed_to_missing_keys) locked_data_vm.refresh()
    }

    LaunchedEffect(locked_data_vm) {
        locked_data_vm.outcomes.collect { outcome ->
            val unlocked_some = outcome == org.astermail.android.mail.LockedDataRecoveryOutcome.SUCCESS ||
                outcome == org.astermail.android.mail.LockedDataRecoveryOutcome.PARTIAL
            if (unlocked_some) {
                mail_vm.reload_after_key_recovery()
                mail_vm.retry_decrypt_thread()
            }
            if (outcome == org.astermail.android.mail.LockedDataRecoveryOutcome.SUCCESS) {
                show_recover_data_dialog = false
                org.astermail.android.ui.common.app_toast.show(
                    context.getString(R.string.recover_data_success),
                )
            }
        }
    }

    if (show_recover_data_dialog) {
        recover_data_dialog(
            is_recovering = locked_data_state.recovering,
            outcome = locked_data_state.last_outcome,
            on_recover = { password -> locked_data_vm.recover(password) },
            on_recover_with_code = { code -> locked_data_vm.recover_with_code(code) },
            on_clear_outcome = { locked_data_vm.clear_outcome() },
            on_dismiss = {
                show_recover_data_dialog = false
                locked_data_vm.clear_outcome()
            },
        )
    }

    failed_send_dialog(mail_vm)

    val thread_matches_email = thread_state.item?.id == email_id ||
        thread_state.messages.any { it.id == email_id }
    val unread_thread_ids = remember(thread_state.messages, thread_matches_email) {
        if (thread_matches_email) thread_state.messages.filter { !it.is_read }.map { it.id } else emptyList()
    }
    LaunchedEffect(email_id, thread_matches_email, unread_thread_ids) {
        if (!thread_matches_email) return@LaunchedEffect
        mail_vm.on_opened_thread_known(email_id, unread_thread_ids)
    }

    LaunchedEffect(Unit) {
        settings_vm.load_preferences()
        settings_vm.load_tags(force = false)
        settings_vm.load_profile()
    }

    val thread_attachments = thread_state.attachments
    val api_messages = remember(thread_state.messages, thread_attachments) {
        thread_state.messages.map { msg ->
            val base = thread_message_to_mock(msg)
            val atts = thread_attachments[msg.id]
            if (!atts.isNullOrEmpty()) thread_message_with_attachments(base, atts) else base
        }
    }
    val api_item = thread_state.item?.takeIf { thread_matches_email }
    val all_thread_attachments = remember(api_messages) {
        api_messages.flatMap { it.attachments }
    }
    val attachment_failure_ids = remember(
        thread_state.messages,
        thread_state.attachments_failed,
        thread_attachments,
    ) {
        if (!thread_state.attachments_failed) {
            emptySet()
        } else {
            thread_state.messages
                .filter { it.has_attachments && thread_attachments[it.id].isNullOrEmpty() }
                .map { it.id }
                .toSet()
        }
    }

    val attachment_loading_ids = remember(
        thread_state.messages,
        thread_state.attachments_loading,
        thread_state.attachments_settled,
        thread_state.is_loading,
        thread_state.attachments_failed,
        thread_attachments,
    ) {
        attachments_awaiting_ids(
            messages = thread_state.messages,
            loaded = thread_attachments,
            loading = thread_state.attachments_loading,
            settled = thread_state.attachments_settled,
            thread_loading = thread_state.is_loading,
            failed = thread_state.attachments_failed,
        )
    }
    val pending_attachment_slots_by_id = remember(attachment_loading_ids, thread_state.messages) {
        thread_state.messages
            .filter { it.id in attachment_loading_ids }
            .associate { msg ->
                msg.id to pending_attachment_slots(
                    message_id = msg.id,
                    expected_count = expected_attachment_count(msg),
                    hints = org.astermail.android.mail.InboundAttachmentKeyStore.listed_entries(msg.id),
                )
            }
    }

    val thread_ghost_email = remember(thread_state.messages) {
        val latest_sent = thread_state.messages
            .filter { it.raw_item.item_type == "sent" }
            .maxByOrNull { it.timestamp }
        val sender = latest_sent?.sender_email?.lowercase().orEmpty()
        if (sender.isNotBlank() && GHOST_LOCAL_PATTERN.containsMatchIn(sender)) sender else null
    }

    LaunchedEffect(reactions_enabled) {
        if (reactions_enabled) {
            settings_vm.load_aliases()
            settings_vm.load_custom_domain_addresses()
        }
    }

    LaunchedEffect(reactions_enabled) {
        if (reactions_enabled) {
            settings_vm.load_ghost_aliases()
        }
    }

    val email = remember(email_id, api_item) {
        if (api_item != null) inbox_item_to_email(api_item, context = context) else null
    }
    var star_override by remember(email_id) { mutableStateOf<Boolean?>(null) }
    val is_starred = star_override ?: (api_item?.is_starred == true)
    val is_spam_override_state = remember(email_id) { mutableStateOf<Boolean?>(null) }
    val is_spam_override by is_spam_override_state
    val inbox_state_for_folder by mail_vm.inbox_state.collectAsStateWithLifecycle()
    val is_in_spam_folder = inbox_state_for_folder.current_folder == "spam"
    val is_spam = is_spam_override ?: ((api_item?.is_spam == true) || is_in_spam_folder)
    val current_account by settings_vm.account_store.current_account.collectAsStateWithLifecycle()
    val my_email = current_account?.email?.lowercase().orEmpty()
    val my_profile_pic = settings_state.user?.profile_picture?.takeIf { it.isNotBlank() }
        ?: current_account?.profile_picture?.takeIf { it.isNotBlank() }
    val reaction_sender_options = remember(
        my_email,
        settings_state.aliases,
        settings_state.custom_domain_addresses,
        settings_state.ghost_aliases,
    ) {
        val options = mutableListOf<String>()
        if (my_email.isNotBlank()) options.add(my_email)
        settings_state.aliases
            .filter { it.is_enabled && !it.decryption_failed && it.address.contains('@') }
            .forEach { if (it.address !in options) options.add(it.address) }
        settings_state.custom_domain_addresses
            .filter { it.is_enabled && !it.decryption_failed && it.address.contains('@') }
            .forEach { if (it.address !in options) options.add(it.address) }
        settings_state.ghost_aliases
            .filter { it.is_enabled && !it.decryption_failed && it.decrypted_address.contains('@') }
            .forEach { if (it.decrypted_address !in options) options.add(it.decrypted_address) }
        options.toList()
    }
    val reaction_sender_hashes = remember(
        settings_state.aliases,
        settings_state.custom_domain_addresses,
        settings_state.ghost_aliases,
    ) {
        val map = mutableMapOf<String, String>()
        settings_state.aliases
            .filter { !it.decryption_failed }
            .forEach { map[it.address] = it.alias_address_hash }
        settings_state.custom_domain_addresses
            .filter { !it.decryption_failed }
            .forEach { map[it.address] = it.local_part_hash }
        settings_state.ghost_aliases.forEach { ghost ->
            if (!ghost.decryption_failed && ghost.decrypted_address.contains('@') &&
                ghost.alias_address_hash.isNotBlank()
            ) {
                map[ghost.decrypted_address] = ghost.alias_address_hash
            }
        }
        map.toMap()
    }
    val reaction_routing_tokens = remember(
        settings_state.aliases,
        settings_state.custom_domain_addresses,
        settings_state.ghost_aliases,
    ) {
        org.astermail.android.ui.compose.reaction_routing_token_map(
            aliases = settings_state.aliases,
            custom_domain_addresses = settings_state.custom_domain_addresses,
            ghost_aliases = settings_state.ghost_aliases,
        )
    }
    val reaction_identity_for: (String) -> org.astermail.android.ui.compose.ReactionSenderIdentity? =
        { message_id ->
            val target = thread_state.messages.find { it.id == message_id }
            if (target == null) {
                null
            } else {
                val item_type = target.raw_item.item_type ?: target.raw_item.metadata?.item_type ?: "received"
                val is_received = item_type == "received"
                org.astermail.android.ui.compose.resolve_reaction_sender_identity(
                    own_recipient_addresses = target.to_addresses + target.cc_addresses,
                    message_sender_email = target.sender_email,
                    is_own_message = item_type == "sent",
                    alias_options = reaction_sender_options,
                    alias_hash_map = reaction_sender_hashes,
                    user_email = my_email,
                    delivered_to = if (is_received) extract_delivered_to(target.raw_headers) else null,
                    routing_token = if (is_received) target.raw_item.routing_token else null,
                    routing_token_map = reaction_routing_tokens,
                )
            }
        }
    val reaction_own_addresses = remember(reaction_sender_options) {
        reaction_sender_options.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    }
    val is_trashed = api_item?.is_trashed
        ?: api_messages.firstOrNull { it.id == email_id }?.is_trashed
        ?: inbox_state_for_folder.items.firstOrNull { it.id == email_id }?.is_trashed
        ?: false
    val thread_trash_flags = remember(api_messages, email_id, is_trashed) {
        api_messages.map { it.id to (it.is_trashed || (it.id == email_id && is_trashed)) }
    }
    val message_is_trashed: (String) -> Boolean = { id ->
        thread_trash_flags.firstOrNull { it.first == id }?.second ?: (id == email_id && is_trashed)
    }
    val reaction_restriction_for: (ThreadMessage) -> org.astermail.android.mail.ReactionRestriction? =
        { msg ->
            org.astermail.android.mail.reaction_restriction(
                item_type = msg.item_type,
                sender_email = msg.sender_email,
                to_addresses = msg.to_addresses,
                cc_addresses = msg.cc_addresses,
                raw_headers = msg.raw_headers,
                reactions = message_reactions[msg.id].orEmpty(),
                user_email = my_email,
                is_spam = is_spam,
                is_trashed = is_trashed,
                reactions_enabled = reactions_enabled,
                is_own_address = { address -> address in reaction_own_addresses },
                sender_resolved = reaction_identity_for(msg.id) != null,
                require_encryption = require_encryption,
            )
        }
    val show_action_sheet_state = remember { mutableStateOf(false) }
    var show_action_sheet by show_action_sheet_state
    val show_topbar_menu_state = remember { mutableStateOf(false) }
    val show_message_details_state = remember { mutableStateOf(false) }
    val show_raw_source_dialog_state = remember { mutableStateOf(false) }
    val show_encryption_info_state = remember { mutableStateOf(false) }
    val pending_block_sender_state = remember { mutableStateOf<String?>(null) }
    val pending_delete_permanent_state = remember { mutableStateOf<List<String>?>(null) }
    val profile_sender_state = remember { mutableStateOf<Pair<String, String>?>(null) }
    var profile_sender by profile_sender_state
    val profile_sender_trust_state = remember { mutableStateOf<org.astermail.android.security.SenderTrust?>(null) }
    var profile_sender_trust by profile_sender_trust_state
    val show_snooze_sheet_state = remember { mutableStateOf(false) }
    val show_folder_sheet_state = remember { mutableStateOf(false) }
    val show_label_sheet_state = remember { mutableStateOf(false) }
    val show_category_sheet_state = remember { mutableStateOf(false) }
    val categories_enabled = settings_state.preferences?.inbox_categories_enabled ?: true
    val action_target_id_state = remember { mutableStateOf<String?>(null) }
    var action_target_id by action_target_id_state
    val action_menu_anchor_state = remember { mutableStateOf<IntRect?>(null) }
    var action_menu_anchor by action_menu_anchor_state

    var show_encryption_dropdown by remember { mutableStateOf(false) }
    var hidden_group_revealed by remember(email_id) { mutableStateOf(false) }
    var allow_external_ids by remember(email_id) { mutableStateOf(emptySet<String>()) }
    var dismissed_unsub_ids by remember(email_id) { mutableStateOf(emptySet<String>()) }
    val unsubscribed_tokens by subscriptions_vm.unsubscribed_tokens.collectAsStateWithLifecycle()
    LaunchedEffect(email_id) { subscriptions_vm.refresh_unsubscribed() }
    val pending_link_state = remember { mutableStateOf<String?>(null) }
    var pending_link by pending_link_state
    val lightbox_src_state = remember { mutableStateOf<String?>(null) }
    var lightbox_src by lightbox_src_state
    val preview_attachment_state = remember { mutableStateOf<MessageAttachment?>(null) }
    var preview_attachment by preview_attachment_state
    val preview_bytes_state = remember { mutableStateOf<ByteArray?>(null) }
    var preview_bytes by preview_bytes_state
    var is_downloading_attachment by remember { mutableStateOf(false) }
    val options_attachment_state = remember { mutableStateOf<MessageAttachment?>(null) }
    var options_attachment by options_attachment_state
    val save_as_pending = remember { arrayOfNulls<ByteArray>(1) }
    val save_as_launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = result.data?.data
        val bytes = save_as_pending[0]
        save_as_pending[0] = null
        if (target == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                        out.write(bytes)
                        out.flush()
                    } != null
                }.getOrDefault(false)
            }
            org.astermail.android.ui.common.app_toast.show(
                context.getString(if (written) R.string.attachment_saved else R.string.failed_to_save),
            )
        }
    }
    fun open_saved_or_toast(saved: saved_attachment) {
        if (!open_saved_attachment(context, saved)) {
            org.astermail.android.ui.common.app_toast.show(context.getString(R.string.no_app_to_open))
        }
    }
    fun announce_saved(saved: saved_attachment?) {
        if (saved == null) {
            org.astermail.android.ui.common.app_toast.show(context.getString(R.string.failed_to_save))
            return
        }
        org.astermail.android.ui.common.app_toast.show(
            TopToastState(
                message = context.getString(R.string.saved_to_downloads),
                undo_label = context.getString(R.string.open),
                on_undo = { open_saved_or_toast(saved) },
            ),
        )
    }
    fun with_attachment_bytes(att: MessageAttachment, block: (MessageAttachment, ByteArray) -> Unit) {
        mail_vm.download_attachment(att) { result ->
            result.onSuccess { (resolved_att, bytes) -> block(resolved_att, bytes) }
                .onFailure { error ->
                    org.astermail.android.ui.common.app_toast.show(
                        if (error is AttachmentIntegrityException) context.getString(R.string.attachment_content_mismatch)
                        else if (error is AttachmentKeyUnavailableException) context.getString(R.string.attachment_locked)
                        else context.getString(R.string.failed_to_download, att.filename),
                    )
                }
        }
    }

    val messages = remember(email_id, api_messages) { api_messages.distinctBy { it.id } }
    var translated_thread_subject by remember(email_id) { mutableStateOf<String?>(null) }
    val subject_translation_owner = remember(email_id, messages) {
        messages.firstOrNull { it.id == email_id }?.id ?: messages.lastOrNull()?.id
    }
    val optimistic_thread_messages by mail_vm.thread_optimistic_messages.collectAsStateWithLifecycle()
    val thread_send_aliases by mail_vm.thread_send_aliases.collectAsStateWithLifecycle()
    val display_messages = remember(messages, optimistic_thread_messages, thread_matches_email, detail_grouping) {
        if (!detail_grouping || !thread_matches_email || optimistic_thread_messages.isEmpty()) {
            messages
        } else {
            val known = messages.mapTo(HashSet()) { it.id }
            messages + optimistic_thread_messages
                .filter { it.id !in known }
                .map { thread_message_to_mock(it) }
        }
    }
    val display_ids = remember(display_messages) { display_messages.map { it.id } }
    val own_sent_ids = remember(display_messages) {
        display_messages.asSequence().filter { it.is_own_copy }.map { it.id }.toSet()
    }
    val detail_count_corrections by mail_vm.thread_count_corrections.collectAsStateWithLifecycle()
    val expected_message_count = remember(
        email_id,
        api_item?.thread_message_count,
        inbox_state_for_folder.items,
        detail_count_corrections,
        detail_grouping,
    ) {
        if (!detail_grouping) return@remember 1
        val listed = inbox_state_for_folder.items.firstOrNull { it.id == email_id }
        val claimed = api_item?.thread_message_count ?: listed?.thread_message_count ?: 1
        val token = api_item?.thread_token ?: listed?.thread_token
        corrected_thread_count(claimed.coerceAtLeast(1), token?.let { detail_count_corrections[it] })
    }
    var thread_settled by remember(email_id, detail_grouping) {
        mutableStateOf(thread_matches_email && !thread_state.is_loading && messages.isNotEmpty())
    }
    LaunchedEffect(email_id, thread_matches_email, thread_state.is_loading, messages.size) {
        if (thread_matches_email && !thread_state.is_loading && messages.isNotEmpty()) thread_settled = true
    }
    val thread_complete = email != null && thread_is_complete(
        loaded_count = display_messages.size,
        expected_count = expected_message_count,
        settled = thread_settled,
        any_body_pending = messages.any { it.is_body_pending },
    )
    val single_message_open = expected_message_count <= 1 &&
        display_messages.size <= 1 &&
        display_messages.any { it.id == email_id }
    val layout_ready = thread_complete || single_message_open
    var open_layout by remember(email_id, detail_grouping) {
        mutableStateOf(
            if (layout_ready) initial_thread_layout(display_ids, email_id) else null,
        )
    }
    var initial_scroll_done by remember(email_id) { mutableStateOf(false) }
    LaunchedEffect(email_id, layout_ready, display_ids, own_sent_ids) {
        if (!layout_ready) return@LaunchedEffect
        val layout = open_layout
        if (layout == null) {
            open_layout = initial_thread_layout(display_ids, email_id)
        } else if (!initial_scroll_done && thread_layout_needs_refresh(layout.known_ids, display_ids, own_sent_ids)) {
            open_layout = initial_thread_layout(display_ids, email_id)
        }
    }
    val is_thread_encrypted = remember(messages) { thread_is_end_to_end_encrypted(messages) }
    val metadata_shows_transit = remember(api_item) { api_item?.raw_item?.let(::metadata_shows_transit_only) == true }
    val is_thread_pgp = remember(messages) { thread_is_pgp_encrypted(messages) }
    val is_thread_outside_pgp = remember(messages) { thread_reaches_outside_with_pgp(messages) }
    val thread_trackers_blocked = remember(messages) { messages.sumOf { it.trackers_blocked } }

    val bottom_bar_height_state = remember { mutableStateOf(132.dp) }
    val bottom_bar_height by bottom_bar_height_state

    val hidden_seed_ids = open_layout?.hidden_ids.orEmpty()
    val hidden_id_set = remember(display_messages, hidden_group_revealed, hidden_seed_ids) {
        if (hidden_group_revealed) emptySet()
        else display_messages.asSequence().map { it.id }.filter { it in hidden_seed_ids }.toSet()
    }
    val hidden_run_sizes = remember(display_messages, hidden_id_set) {
        val sizes = mutableMapOf<Int, Int>()
        var run_start = -1
        display_messages.forEachIndexed { idx, msg ->
            if (msg.id in hidden_id_set) {
                if (run_start < 0) run_start = idx
                sizes[run_start] = (sizes[run_start] ?: 0) + 1
            } else {
                run_start = -1
            }
        }
        sizes.toMap()
    }

    val user_expanded_ids = remember(email_id) {
        mutableStateOf<Set<String>?>(null)
    }
    val current_expanded_ids = user_expanded_ids.value ?: open_layout?.expanded_ids.orEmpty()
    val ready_body_ids = remember(email_id) { mutableStateOf(emptySet<String>()) }
    var body_wait_expired by remember(email_id) { mutableStateOf(false) }
    LaunchedEffect(email_id, open_layout != null) {
        if (open_layout == null) return@LaunchedEffect
        kotlinx.coroutines.delay(thread_body_wait_ms)
        body_wait_expired = true
    }
    var thread_revealed by remember(email_id) {
        mutableStateOf(thread_revealed_cache.was_revealed(email_id))
    }
    LaunchedEffect(
        email_id,
        thread_complete,
        single_message_open,
        open_layout,
        hidden_id_set,
        ready_body_ids.value,
        body_wait_expired,
    ) {
        val layout = open_layout ?: return@LaunchedEffect
        if (thread_revealed) return@LaunchedEffect
        if (thread_reveal_ready(
                complete = thread_complete || single_message_open,
                expanded_ids = layout.expanded_ids,
                hidden_ids = hidden_id_set,
                ready_body_ids = ready_body_ids.value,
                body_wait_expired = body_wait_expired,
            )
        ) {
            thread_revealed = true
        }
    }
    LaunchedEffect(email_id, thread_revealed) {
        if (thread_revealed) thread_revealed_cache.mark_revealed(email_id)
    }
    val detail_phase = remember_detail_skeleton_phase(email_id, !thread_revealed)
    val detail_content_alpha_state = remember_detail_content_alpha(email_id, thread_revealed)

    fun show_toast(msg: String) {
        org.astermail.android.ui.common.app_toast.show(msg)
    }

    val reaction_hold_seconds = org.astermail.android.mail.resolve_undo_send_seconds(
        settings_state.preferences?.undo_send_enabled,
        settings_state.preferences?.undo_send_seconds,
    )

    fun announce_reaction(held: org.astermail.android.mail.HeldReaction, message_id: String, emoji: String) {
        if (held.delay_ms <= 0L) {
            show_toast(context.getString(R.string.reaction_sent))
            return
        }
        app_toast.show(
            TopToastState(
                message = context.getString(R.string.reaction_sent),
                undo_label = context.getString(R.string.undo),
                on_undo = {
                    mail_vm.undo_reaction(held = held, message_id = message_id, emoji = emoji) { error ->
                        if (error != null) show_toast(error)
                    }
                },
                duration_ms = held.delay_ms,
            ),
        )
    }

    var pending_post_quantum_reaction by remember { mutableStateOf<post_quantum_reaction?>(null) }

    fun dispatch_reaction(message_id: String, emoji: String, allow_non_post_quantum: Boolean = false) {
        val identity = reaction_identity_for(message_id)
        if (identity == null) {
            show_toast(context.getString(R.string.cannot_react_bcc))
            return
        }
        mail_vm.send_reaction(
            message_id = message_id,
            emoji = emoji,
            sender_email = identity.email,
            sender_alias_hash = identity.alias_hash,
            own_addresses = reaction_own_addresses,
            sender_resolved = true,
            hold_seconds = reaction_hold_seconds,
            require_encryption = require_encryption,
            allow_non_post_quantum = allow_non_post_quantum,
            on_post_quantum_missing = { coverage ->
                pending_post_quantum_reaction = post_quantum_reaction(message_id, emoji, coverage)
            },
            on_held = { held -> announce_reaction(held, message_id, emoji) },
        ) { error ->
            if (error != null) show_toast(error)
        }
    }

    pending_post_quantum_reaction?.let { pending ->
        org.astermail.android.ui.common.post_quantum_consent_dialog(
            missing = pending.coverage.missing,
            downgraded = pending.coverage.downgraded,
            on_cancel = { pending_post_quantum_reaction = null },
            on_send_anyway = {
                pending_post_quantum_reaction = null
                dispatch_reaction(pending.message_id, pending.emoji, allow_non_post_quantum = true)
            },
        )
    }

    val list_state = rememberLazyListState()
    warm_body_web_view(ready = thread_revealed) { list_state.isScrollInProgress }
    val tail_message = display_messages.lastOrNull()
    val tail_key = remember(display_ids, thread_send_aliases) {
        thread_item_keys(display_ids, thread_send_aliases).lastOrNull()
    }
    val tail_is_own = tail_message?.is_own_copy == true
    var user_dragged by remember(email_id) { mutableStateOf(false) }
    LaunchedEffect(email_id, list_state) {
        list_state.interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) user_dragged = true
        }
    }
    var followed_tail_key by remember(email_id) { mutableStateOf<String?>(null) }
    val pan_signal = remember { horizontal_pan_signal() }
    val card_reduce_motion = org.astermail.android.design.aster_reduce_motion()
    val card_placement_spec: androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset>? =
        if (card_reduce_motion) null else org.astermail.android.design.AsterSpring.fast_spatial()
    val body_holder = remember(email_id) { retained_body_holders.take(email_id) ?: body_view_holder() }
    val body_holder_context = LocalContext.current
    DisposableEffect(body_holder, body_holder_context) {
        val callbacks = object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(new_config: android.content.res.Configuration) {
                body_holder.evict_detached()
            }
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {
                body_holder.evict_detached()
            }
            override fun onTrimMemory(level: Int) {
                if (trim_level_evicts_bodies(level)) {
                    body_holder.evict_detached()
                    body_web_view_pool.release()
                    inline_images_memo.clear()
                }
            }
        }
        val app_context = body_holder_context.applicationContext
        app_context.registerComponentCallbacks(callbacks)
        onDispose {
            runCatching { app_context.unregisterComponentCallbacks(callbacks) }
            retained_body_holders.keep(email_id, body_holder, app_context)
        }
    }
    val show_topbar_subject_state = remember {
        derivedStateOf { list_state.firstVisibleItemIndex > 0 || list_state.firstVisibleItemScrollOffset > 80 }
    }

    LaunchedEffect(email_id, open_layout) {
        if (initial_scroll_done || open_layout == null) return@LaunchedEffect
        if (user_dragged) {
            followed_tail_key = tail_key
            initial_scroll_done = true
            return@LaunchedEffect
        }
        val anchor = thread_open_anchor_id(display_ids, own_sent_ids, email_id)
        val target = initial_thread_scroll_index(
            message_ids = display_ids,
            opened_id = anchor,
            header_item_count = 1,
        )
        followed_tail_key = tail_key
        initial_scroll_done = true
        if (target != null) list_state.scrollToItem(target)
    }
    LaunchedEffect(email_id, tail_key, tail_is_own, initial_scroll_done, user_dragged) {
        if (!initial_scroll_done) return@LaunchedEffect
        if (!should_follow_tail(user_dragged, tail_key, followed_tail_key, tail_is_own)) {
            if (!tail_is_own || user_dragged) followed_tail_key = tail_key
            return@LaunchedEffect
        }
        followed_tail_key = tail_key
        androidx.compose.runtime.withFrameNanos { }
        val index = list_state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tail_key }?.index
            ?: (list_state.layoutInfo.totalItemsCount - 1)
        if (index >= 0 && !user_dragged) {
            if (card_reduce_motion) list_state.scrollToItem(index) else list_state.animateScrollToItem(index)
        }
    }
    val search_controller = remember_detail_search_controller(email_id)

    val pending_anchor = remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
    val toggle_tick = remember { mutableStateOf(0) }
    fun anchor_toggle(id: String) {
        val info = list_state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
        pending_anchor.value = if (info != null) Triple(id, info.index, info.offset) else null
        toggle_tick.value += 1
    }
    LaunchedEffect(toggle_tick.value) {
        val anchor = pending_anchor.value ?: return@LaunchedEffect
        pending_anchor.value = null
        androidx.compose.runtime.withFrameNanos { }
        val info = list_state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == anchor.first }
        if (info != null) {
            val delta = (info.offset - anchor.third).toFloat()
            if (kotlin.math.abs(delta) > 1f) list_state.scrollBy(delta)
            return@LaunchedEffect
        }
        val index = anchor.second.coerceAtMost((list_state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        if (anchor.third < 0) {
            list_state.scrollToItem(index, -anchor.third)
        } else {
            list_state.scrollToItem(index)
            if (anchor.third > 0) list_state.scrollBy(-anchor.third.toFloat())
        }
    }
    val quote_density = LocalDensity.current.density
    DisposableEffect(body_holder, quote_density) {
        body_holder.on_quote_collapse = { _, shift_dp ->
            val shift_px = shift_dp * quote_density
            if (shift_px > 1f && !body_holder.is_released && list_state.canScrollBackward) {
                list_state.dispatchRawDelta(-shift_px)
            }
        }
        body_holder.on_quote_anchor = anchor@{ delta_px ->
            if (body_holder.is_released || list_state.isScrollInProgress) return@anchor false
            if ((delta_px > 0f && list_state.canScrollForward) || (delta_px < 0f && list_state.canScrollBackward)) {
                list_state.dispatchRawDelta(delta_px)
            }
            true
        }
        onDispose {
            body_holder.on_quote_collapse = null
            body_holder.on_quote_anchor = null
        }
    }

    batch_action_toast(mail_vm)

    LaunchedEffect(mail_vm) {
        mail_vm.toast_events.collect { evt ->
            org.astermail.android.ui.common.app_toast.show(
                org.astermail.android.ui.common.TopToastState(
                    message = evt.message,
                    undo_label = evt.undo_label,
                    on_undo = evt.on_undo,
                    duration_ms = evt.duration_ms,
                    on_timeout = evt.on_timeout,
                    key = evt.id,
                ),
            )
        }
    }

    val detail_flags = detail_screen_flags(
        show_action_sheet = show_action_sheet_state,
        show_topbar_menu = show_topbar_menu_state,
        show_message_details = show_message_details_state,
        show_raw_source_dialog = show_raw_source_dialog_state,
        show_encryption_info = show_encryption_info_state,
        pending_block_sender = pending_block_sender_state,
        pending_delete_permanent = pending_delete_permanent_state,
        profile_sender = profile_sender_state,
        profile_sender_trust = profile_sender_trust_state,
        show_snooze_sheet = show_snooze_sheet_state,
        show_folder_sheet = show_folder_sheet_state,
        show_label_sheet = show_label_sheet_state,
        show_category_sheet = show_category_sheet_state,
        action_target_id = action_target_id_state,
        action_menu_anchor = action_menu_anchor_state,
        left_after_unread = left_after_unread_state,
        is_spam_override = is_spam_override_state,
        reaction_picker_open = reaction_picker_open_state,
        bottom_bar_height = bottom_bar_height_state,
        pending_link = pending_link_state,
        lightbox_src = lightbox_src_state,
        preview_attachment = preview_attachment_state,
        preview_bytes = preview_bytes_state,
        options_attachment = options_attachment_state,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            detail_top_bar(
                flags = detail_flags,
                on_back = on_back,
                on_archive = on_archive,
                on_delete = on_delete,
                swipe_direction = swipe_direction,
                chained = chained,
                email_id = email_id,
                email = email,
                api_item = api_item,
                messages = messages,
                translated_thread_subject = translated_thread_subject,
                show_topbar_subject_state = show_topbar_subject_state,
                thread_settled = thread_settled,
                metadata_shows_transit = metadata_shows_transit,
                show_encryption_indicators = show_encryption_indicators,
                is_thread_encrypted = is_thread_encrypted,
                is_thread_pgp = is_thread_pgp,
                categories_enabled = categories_enabled,
                block_external_images = block_external_images,
                allow_external_ids = allow_external_ids,
                image_grant_allowed = image_grant_allowed,
                settings_state = settings_state,
                is_spam = is_spam,
                is_trashed = is_trashed,
                thread_trash_flags = thread_trash_flags,
                scope = scope,
                mail_vm = mail_vm,
                settings_vm = settings_vm,
                show_toast = { message -> show_toast(message) },
            )

            val subject_base = translated_thread_subject?.ifBlank { null }
                ?: email?.subject?.ifBlank { stringResource(R.string.no_subject) }
                ?: stringResource(R.string.no_subject)
            val subject_thread_count = if (thread_settled) messages.size else expected_message_count
            val subject_count_format = stringResource(R.string.inbox_subject_with_count, subject_base, subject_thread_count)
            val subject_text = if (subject_thread_count > 1) subject_count_format else subject_base
            val subject_copy_value = translated_thread_subject?.ifBlank { null } ?: email?.subject?.ifBlank { null }
            val skeleton_header = email?.let { seed ->
                val single = expected_message_count <= 1
                detail_skeleton_header(
                    subject = subject_text,
                    sender_name = if (single) displayed_sender_name(seed.display_sender_name, seed.sender_name) else null,
                    sender_email = if (single) displayed_sender_email(seed.display_sender_email, seed.sender_email) else null,
                )
            }
            if (search_controller != null) detail_search_effects(
                controller = search_controller,
                messages = display_messages,
                subject = subject_text,
                layout_ready = open_layout != null,
                thread_revealed = thread_revealed,
                hidden_ids = hidden_id_set,
                expanded_ids = current_expanded_ids,
                list_state = list_state,
                on_expand = { user_expanded_ids.value = it },
                on_reveal_hidden = { hidden_group_revealed = true },
            )

            if (email == null && load_requested && !thread_state.is_loading) {
                Box(
                    Modifier.fillMaxSize().detail_swipe_navigation(detail_swipe, on_next, on_previous),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.detail_swipe_offset(detail_swipe).padding(horizontal = AsterSpacing.lg),
                    ) {
                        Text(
                            text = thread_state.error
                                ?: stringResource(R.string.message_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text_muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        org.astermail.android.design.components.AsterDialogPrimaryButton(
                            label = stringResource(R.string.retry),
                            onClick = { mail_vm.load_thread(email_id) },
                        )
                    }
                }
                return@Column
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .chrome_surface(colors)
                    .clipToBounds()
                    .detail_swipe_navigation(detail_swipe, on_next, on_previous),
            ) {
            Box(modifier = Modifier.fillMaxSize().detail_swipe_offset(detail_swipe)) {
            CompositionLocalProvider(
                local_horizontal_pan_signal provides pan_signal,
                local_body_view_holder provides body_holder,
                org.astermail.android.ui.search.local_search_highlight provides search_controller?.session,
            ) {
            if (email != null) LazyColumn(
                state = list_state,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontal_scroll_indicator(pan_signal, bottom_inset = bottom_bar_height)
                    .detail_content_handoff { detail_content_alpha_state.value }
                    .clipToBounds()
                    .lazy_top_fade(list_state, 28.dp)
            ) {
                item(key = "subject_header") {
                    var subject_expanded by remember(subject_text) { mutableStateOf(false) }
                    var subject_truncated by remember(subject_text) { mutableStateOf(false) }
                    val applied_tag_tokens = remember(api_item, inbox_state_for_folder.items) {
                        org.astermail.android.labels.conversation_tag_tokens(
                            api_item?.as_conversation_member(),
                            inbox_state_for_folder.items.map { it.as_conversation_member() },
                        )
                    }
                    val settings_state_now by settings_vm.state.collectAsStateWithLifecycle()
                    val applied_tags = remember(applied_tag_tokens, settings_state_now.tags) {
                        applied_tag_tokens.mapNotNull { token ->
                            settings_state_now.tags.find { it.tag_token == token }
                        }.filter { it.encrypted_name.isNotBlank() }
                    }
                    val folder_chip = detail_folder_chip_for(
                        item = api_item,
                        folders = settings_state_now.labels,
                        is_spam = is_spam,
                        is_trashed = is_trashed,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = AsterSpacing.lg, end = AsterSpacing.xs)
                            .padding(top = AsterSpacing.sm, bottom = AsterSpacing.md),
                        verticalAlignment = Alignment.Top,
                    ) {
                        detail_subject_line(
                            subject = subject_text,
                            folder_chip = folder_chip,
                            tags = applied_tags,
                            max_lines = if (subject_expanded) Int.MAX_VALUE else 3,
                            on_overflow = { overflowed ->
                                if (!subject_expanded && overflowed) subject_truncated = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .subject_press_actions(
                                    expandable = subject_truncated,
                                    on_toggle_expand = { subject_expanded = !subject_expanded },
                                    copy_value = subject_copy_value,
                                ),
                        )
                        if (api_item?.raw_item?.metadata?.is_pinned == true) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        mail_vm.toggle_pin(email_id)
                                        show_toast(context.getString(R.string.unpinned))
                                    }
                                    .testTag("detail_pin_indicator"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = pin_icon_filled,
                                    contentDescription = stringResource(R.string.unpin),
                                    tint = colors.accent_blue,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        org.astermail.android.ui.common.star_toggle_icon(
                            is_starred = is_starred,
                            icon = if (is_starred) Icons.Filled.Star else TablerIcons.Star,
                            tint = if (is_starred) colors.accent_blue else colors.text_muted,
                            icon_size = 22.dp,
                            touch_size = 48.dp,
                            modifier = Modifier.testTag("detail_star"),
                            onClick = {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                mail_vm.toggle_star(email_id)
                                show_toast(
                                    if (!is_starred) {
                                        context.getString(R.string.starred)
                                    } else {
                                        context.getString(R.string.unstarred)
                                    },
                                )
                            },
                        )
                    }
                }

                val thread_items = if (open_layout == null) emptyList() else display_messages
                val thread_item_keys = thread_item_keys(thread_items.map { it.id }, thread_send_aliases)
                items(thread_items.size, key = { thread_item_keys[it] }, contentType = { "thread_message" }) { idx ->
                    val msg = thread_items[idx]
                    val is_pending_send = org.astermail.android.mail.is_optimistic_send_id(msg.id)
                    val is_last = idx == thread_items.size - 1
                    val is_expanded = thread_message_is_expanded(
                        message_id = msg.id,
                        is_last = is_last,
                        total_messages = thread_items.size,
                        expanded_ids = current_expanded_ids,
                        known_ids = open_layout?.known_ids.orEmpty(),
                    )

                    val is_hidden = msg.id in hidden_id_set
                    val is_after_indicator = hidden_id_set.isNotEmpty() &&
                        idx > 0 && thread_items[idx - 1].id in hidden_id_set

                    if (is_hidden) {
                        val run_size = hidden_run_sizes[idx]
                        if (run_size != null) {
                            hidden_group_indicator(
                                count = run_size,
                                on_reveal = { hidden_group_revealed = true },
                            )
                        }
                        return@items
                    }

                    val is_system_sender = is_aster_system_sender(msg)
                    val is_first_card = idx == 0 || is_after_indicator
                    val is_last_card = is_last

                    Box(modifier = Modifier.fillMaxWidth().animateItem(fadeInSpec = null, placementSpec = card_placement_spec, fadeOutSpec = null)) {
                    val body_kept = remember(msg.id) { BooleanArray(1) }
                    if (is_expanded) body_kept[0] = true
                    if (!is_expanded) {
                        collapsed_message(
                            msg = msg,
                            is_first_card = is_first_card,
                            is_last_card = is_last_card,
                            my_email = my_email,
                            my_profile_pic = my_profile_pic,
                            message_index = idx,
                            on_expand = {
                                anchor_toggle(msg.id)
                                user_expanded_ids.value = current_expanded_ids + msg.id
                            },
                        )
                    }
                    if (body_kept[0]) Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (is_expanded) Modifier else Modifier.kept_offscreen()),
                    ) {
                        order_details_card_for_message(msg = msg, subject = email?.subject.orEmpty())
                        expanded_message(
                            msg = msg,
                            is_last = is_last,
                            message_index = idx,
                            allow_auto_translate = !is_spam,
                            translation_subject = if (msg.id == subject_translation_owner) email?.subject else null,
                            on_subject_translated = { translated ->
                                if (msg.id == subject_translation_owner) translated_thread_subject = translated
                            },
                            thread_attachments = all_thread_attachments,
                            my_email = my_email,
                            my_profile_pic = my_profile_pic,
                            is_first_card = is_first_card,
                            is_last_card = is_last_card,
                            allow_external = message_loads_remote_content(
                                blocked = block_external_images,
                                granted = msg.id in allow_external_ids,
                                grant_allowed = image_grant_allowed,
                                is_system_sender = is_system_sender,
                            ),
                            blocked_for_traffic = blocked_for_traffic_only,
                            offer_load_once = image_grant_allowed,
                            offer_always_allow = offer_always_allow_external,
                            show_raw_headers = settings_state.preferences?.show_raw_headers == true,
                            on_retry_decrypt = { mail_vm.retry_decrypt_thread() },
                            retry_in_progress = decrypt_retry_active,
                            on_recover_data = if (
                                org.astermail.android.mail.message_locked_by_reset(
                                    msg.is_undecryptable && msg.is_sealed_to_missing_keys,
                                    locked_data_state.status,
                                )
                            ) {
                                { show_recover_data_dialog = true }
                            } else {
                                null
                            },
                            attachments_failed = msg.id in attachment_failure_ids,
                            attachments_loading = msg.id in attachment_loading_ids,
                            pending_attachments = pending_attachment_slots_by_id[msg.id].orEmpty(),
                            on_retry_attachments = { mail_vm.retry_thread_attachments() },
                            identity_changed = identity_changed_senders.contains(
                                org.astermail.android.mail.ratchet.PinIdentity.canonical_id(msg.sender_email),
                            ),
                            sender_unauthenticated = msg.id in unauthenticated_message_ids,
                            on_acknowledge_identity = {
                                mail_vm.acknowledge_identity_change(msg.sender_email)
                            },
                            on_load_external = {
                                allow_external_ids = allow_external_ids + msg.id
                            },
                            on_always_allow_external = {
                                allow_external_ids = allow_external_ids + msg.id
                                val base = settings_state.preferences
                                if (base != null) {
                                    settings_vm.save_preferences(
                                        base.copy(
                                            block_external_images = false,
                                            load_remote_images = "always",
                                        ),
                                    )
                                }
                            },
                            on_disable_low_network = {
                                allow_external_ids = allow_external_ids + msg.id
                                val base = settings_state.preferences
                                if (base != null) {
                                    settings_vm.save_preferences(base.copy(low_network_mode = false))
                                }
                            },
                            show_unsub = msg.id !in dismissed_unsub_ids &&
                                remember(msg.sender_email) { subscriptions_vm.sender_token(msg.sender_email) } !in unsubscribed_tokens,
                            on_dismiss_unsub = {
                                dismissed_unsub_ids = dismissed_unsub_ids + msg.id
                            },
                            on_body_ready = { ready_body_ids.value = ready_body_ids.value + msg.id },
                            on_track = { _, _, _ -> },
                            access_token = settings_vm.get_access_token(),
                            on_link_click = { url ->
                                if (!url.startsWith("aster:") || is_system_sender) pending_link = url
                            },
                            on_image_click = { src -> lightbox_src = src },
                            on_unsubscribe = { info ->
                                dismissed_unsub_ids = dismissed_unsub_ids + msg.id
                                val unsubscribe_sender = resolve_unsubscribe_sender(
                                    reaction_identity_for(msg.id),
                                    thread_state.messages.find { it.id == msg.id }?.raw_item?.routing_token,
                                )
                                show_toast(context.getString(R.string.unsubscribe_in_progress))
                                scope.launch {
                                    val outcome = execute_unsubscribe_checked(info, unsubscribe_sender) { request ->
                                        subscriptions_vm.proxy_unsubscribe_checked(request)
                                    }
                                    val record_unsubscribed = {
                                        subscriptions_vm.record_unsubscribed(
                                            msg.sender_email,
                                            msg.sender_name,
                                            info.unsubscribe_link,
                                            info.list_unsubscribe_header,
                                        )
                                    }
                                    if (outcome == UnsubscribeOutcome.unsubscribed) {
                                        record_unsubscribed()
                                        show_toast(context.getString(R.string.toast_unsubscribed))
                                        return@launch
                                    }
                                    if (outcome == UnsubscribeOutcome.rate_limited) {
                                        dismissed_unsub_ids = dismissed_unsub_ids - msg.id
                                        show_toast(context.getString(R.string.unsubscribe_rate_limited))
                                        return@launch
                                    }
                                    val manual_url = get_manual_unsubscribe_url(info)
                                        ?.takeIf { is_safe_unsubscribe_url(it) }
                                    if (manual_url == null) {
                                        dismissed_unsub_ids = dismissed_unsub_ids - msg.id
                                        show_toast(context.getString(R.string.could_not_unsubscribe))
                                        return@launch
                                    }
                                    if (is_web_unsubscribe_url(manual_url)) {
                                        val opened = try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(manual_url))
                                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                            true
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Throwable) {
                                            false
                                        }
                                        if (opened) {
                                            record_unsubscribed()
                                            show_toast(context.getString(R.string.unsubscribe_opened_in_browser))
                                        } else {
                                            dismissed_unsub_ids = dismissed_unsub_ids - msg.id
                                            show_toast(context.getString(R.string.could_not_unsubscribe))
                                        }
                                        return@launch
                                    }
                                    app_toast.show(
                                        TopToastState(
                                            message = context.getString(R.string.unsubscribe_manual_required),
                                            undo_label = context.getString(R.string.open_unsubscribe_page),
                                            duration_ms = 15000L,
                                            on_undo = {
                                                try {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(manual_url))
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                    )
                                                    record_unsubscribed()
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (_: Throwable) {
                                                    dismissed_unsub_ids = dismissed_unsub_ids - msg.id
                                                    show_toast(context.getString(R.string.could_not_unsubscribe))
                                                }
                                            },
                                        ),
                                    )
                                }
                            },
                            on_collapse = {
                                anchor_toggle(msg.id)
                                user_expanded_ids.value = current_expanded_ids - msg.id
                            },
                            on_sender_trust = { tapped_trust -> profile_sender_trust = tapped_trust },
                            on_sender_tap = { email, name ->
                                profile_sender = email to name
                            },
                            on_reply = { on_reply(msg.id, thread_ghost_email) },
                            on_reply_all = { on_reply_all(msg.id, thread_ghost_email) },
                            on_forward = { on_forward(msg.id, thread_ghost_email) },
                            on_more = { bounds ->
                                action_target_id = msg.id
                                action_menu_anchor = bounds.takeUnless { it == IntRect.Zero }
                                show_action_sheet = true
                            },
                            on_attachment_tap = { att ->
                                is_downloading_attachment = true
                                mail_vm.download_attachment(att) { result ->
                                    result.onSuccess { (resolved_att, bytes) ->
                                        preview_attachment = resolved_att.copy(is_unverified = att.is_unverified)
                                        preview_bytes = bytes
                                    }.onFailure { error ->
                                        show_toast(
                                            if (error is AttachmentIntegrityException) context.getString(R.string.attachment_content_mismatch)
                                            else if (error is AttachmentKeyUnavailableException) context.getString(R.string.attachment_locked)
                                            else context.getString(R.string.failed_to_load_preview),
                                        )
                                    }
                                    is_downloading_attachment = false
                                }
                            },
                            on_attachment_download = { att ->
                                show_toast(context.getString(R.string.downloading_file, att.filename))
                                with_attachment_bytes(att) { resolved_att, bytes ->
                                    request_storage_access {
                                        scope.launch {
                                            announce_saved(save_attachment_to_storage(context, resolved_att, bytes))
                                        }
                                    }
                                }
                            },
                            on_attachment_options = { att -> options_attachment = att },
                            reactions = if (reactions_enabled && !is_pending_send) message_reactions[msg.id].orEmpty() else emptyList(),
                            reaction_placeholders = if (reactions_enabled && !is_pending_send) reaction_placeholders[msg.id] ?: 0 else 0,
                            on_react = { emoji ->
                                if (is_pending_send) return@expanded_message
                                val blocked = reaction_restriction_for(msg)
                                if (blocked != null) {
                                    show_toast(
                                        context.getString(
                                            org.astermail.android.mail.reaction_restriction_string(blocked),
                                        ),
                                    )
                                    return@expanded_message
                                }
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                dispatch_reaction(msg.id, emoji)
                            },
                            on_unreact = { emoji ->
                                mail_vm.remove_reaction(message_id = msg.id, emoji = emoji) { error ->
                                    if (error != null) show_toast(error)
                                }
                            },
                            is_system = is_system_sender,
                            can_collapse = !is_pending_send && thread_items.size > 1,
                            is_pending_send = is_pending_send,
                            on_retry_pending_send = { mail_vm.retry_pending_send(msg.id) },
                        )
                    }
                    }
                }

                item(key = "detail_bottom_spacer", contentType = "detail_bottom_spacer") { Spacer(Modifier.height(bottom_bar_height + 16.dp)) }
            }
            }
            if (email != null) vertical_scroll_bar(
                state = list_state,
                modifier = Modifier.align(Alignment.TopEnd),
                bottom_inset = bottom_bar_height,
            )
            detail_skeleton_layer(
                thread_key = email_id,
                phase = detail_phase,
                message_count = expected_message_count,
                header = skeleton_header,
            )
            if (search_controller != null && email != null) {
                detail_search_overlay(search_controller, bottom_bar_height)
            }
            }

            }
        }


        if (email != null && messages.isNotEmpty()) {
            detail_bottom_bar(
                modifier = Modifier.align(Alignment.BottomCenter),
                flags = detail_flags,
                email_id = email_id,
                messages = messages,
                thread_state = thread_state,
                api_item = api_item,
                inbox_state_for_folder = inbox_state_for_folder,
                settings_state = settings_state,
                is_trashed = is_trashed,
                is_starred = is_starred,
                is_spam = is_spam,
                thread_trash_flags = thread_trash_flags,
                thread_ghost_email = thread_ghost_email,
                reaction_restriction_for = reaction_restriction_for,
                dispatch_reaction = { message_id, emoji -> dispatch_reaction(message_id, emoji) },
                show_toast = { message -> show_toast(message) },
                mail_vm = mail_vm,
                settings_vm = settings_vm,
                on_reply = on_reply,
                on_forward = on_forward,
                on_back = on_back,
                on_delete = on_delete,
                on_archive = on_archive,
            )
        }
    }

    detail_overlays(
        flags = detail_flags,
        email_id = email_id,
        email = email,
        messages = messages,
        api_item = api_item,
        inbox_state_for_folder = inbox_state_for_folder,
        settings_state = settings_state,
        is_starred = is_starred,
        is_spam = is_spam,
        is_trashed = is_trashed,
        thread_trash_flags = thread_trash_flags,
        message_is_trashed = message_is_trashed,
        thread_ghost_email = thread_ghost_email,
        reaction_own_addresses = reaction_own_addresses,
        is_thread_encrypted = is_thread_encrypted,
        is_thread_pgp = is_thread_pgp,
        is_thread_outside_pgp = is_thread_outside_pgp,
        scope = scope,
        request_storage_access = request_storage_access,
        save_as_pending = save_as_pending,
        save_as_launcher = save_as_launcher,
        show_toast = { message -> show_toast(message) },
        announce_saved = { saved -> announce_saved(saved) },
        with_attachment_bytes = { att, block -> with_attachment_bytes(att, block) },
        mail_vm = mail_vm,
        settings_vm = settings_vm,
        on_reply = on_reply,
        on_reply_all = on_reply_all,
        on_forward = on_forward,
        on_back = on_back,
        on_delete = on_delete,
        on_archive = on_archive,
        on_navigate = on_navigate,
    )
}

private class detail_screen_flags(
    val show_action_sheet: MutableState<Boolean>,
    val show_topbar_menu: MutableState<Boolean>,
    val show_message_details: MutableState<Boolean>,
    val show_raw_source_dialog: MutableState<Boolean>,
    val show_encryption_info: MutableState<Boolean>,
    val pending_block_sender: MutableState<String?>,
    val pending_delete_permanent: MutableState<List<String>?>,
    val profile_sender: MutableState<Pair<String, String>?>,
    val profile_sender_trust: MutableState<org.astermail.android.security.SenderTrust?>,
    val show_snooze_sheet: MutableState<Boolean>,
    val show_folder_sheet: MutableState<Boolean>,
    val show_label_sheet: MutableState<Boolean>,
    val show_category_sheet: MutableState<Boolean>,
    val action_target_id: MutableState<String?>,
    val action_menu_anchor: MutableState<IntRect?>,
    val left_after_unread: MutableState<Boolean>,
    val is_spam_override: MutableState<Boolean?>,
    val reaction_picker_open: MutableState<Boolean>,
    val bottom_bar_height: MutableState<Dp>,
    val pending_link: MutableState<String?>,
    val lightbox_src: MutableState<String?>,
    val preview_attachment: MutableState<MessageAttachment?>,
    val preview_bytes: MutableState<ByteArray?>,
    val options_attachment: MutableState<MessageAttachment?>,
)

@Composable
private fun detail_top_bar(
    flags: detail_screen_flags,
    on_back: () -> Unit,
    on_archive: () -> Unit,
    on_delete: () -> Unit,
    swipe_direction: Int,
    chained: Boolean,
    email_id: String,
    email: Email?,
    api_item: org.astermail.android.mail.InboxItem?,
    messages: List<ThreadMessage>,
    translated_thread_subject: String?,
    show_topbar_subject_state: androidx.compose.runtime.State<Boolean>,
    thread_settled: Boolean,
    metadata_shows_transit: Boolean,
    show_encryption_indicators: Boolean,
    is_thread_encrypted: Boolean,
    is_thread_pgp: Boolean,
    categories_enabled: Boolean,
    block_external_images: Boolean,
    allow_external_ids: Set<String>,
    image_grant_allowed: Boolean,
    settings_state: org.astermail.android.settings.SettingsUiState,
    is_spam: Boolean,
    is_trashed: Boolean,
    thread_trash_flags: List<Pair<String, Boolean>>,
    scope: kotlinx.coroutines.CoroutineScope,
    mail_vm: MailViewModel,
    settings_vm: SettingsViewModel,
    show_toast: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val show_topbar_subject by show_topbar_subject_state
    var show_topbar_menu by flags.show_topbar_menu
    var show_encryption_info by flags.show_encryption_info
    var left_after_unread by flags.left_after_unread
    var show_snooze_sheet by flags.show_snooze_sheet
    var show_folder_sheet by flags.show_folder_sheet
    var show_label_sheet by flags.show_label_sheet
    var show_category_sheet by flags.show_category_sheet
    var show_message_details by flags.show_message_details
    var show_raw_source_dialog by flags.show_raw_source_dialog
    var is_spam_override by flags.is_spam_override
    var pending_block_sender by flags.pending_block_sender
    var pending_delete_permanent by flags.pending_delete_permanent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .chrome_surface(colors)
            .padding(horizontal = AsterSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        org.astermail.android.design.components.AsterIconSlotButton(
            content_description = stringResource(R.string.back),
            onClick = on_back,
            modifier = Modifier.align(Alignment.CenterStart).testTag("back"),
        ) { tint, icon_modifier ->
            val morph = remember {
                androidx.compose.animation.core.Animatable(detail_back_icon_start_progress(swipe_direction, chained))
            }
            LaunchedEffect(Unit) {
                if (morph.value >= 1f) return@LaunchedEffect
                morph.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 320,
                        easing = AsterEasing.emphasized,
                    ),
                )
            }
            org.astermail.android.design.components.menu_back_morph_icon(
                progress = morph.value,
                tint = tint,
                modifier = icon_modifier.mirror_in_rtl(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 52.dp),
            contentAlignment = Alignment.Center,
        ) {
            val topbar_subject = translated_thread_subject?.ifBlank { null }
                ?: email?.subject?.ifBlank { stringResource(R.string.no_subject) }
                ?: stringResource(R.string.no_subject)
            androidx.compose.animation.AnimatedVisibility(
                visible = show_topbar_subject && email != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            ) {
                Text(
                    text = topbar_subject,
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            val badge_known = thread_settled || metadata_shows_transit
            androidx.compose.animation.AnimatedVisibility(
                visible = !show_topbar_subject && email != null && show_encryption_indicators,
                enter = androidx.compose.animation.EnterTransition.None,
                exit = fadeOut(),
            ) {
                val label = encryption_badge_label(is_thread_encrypted, is_thread_pgp)
                val tint = if (is_thread_encrypted) colors.accent_blue else colors.text_muted
                Row(
                    modifier = Modifier
                        .graphicsLayer { alpha = if (badge_known) 1f else 0f }
                        .clip(SquircleShape(999.dp))
                        .clickable(enabled = badge_known) { show_encryption_info = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("encryption_badge"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (is_thread_encrypted) TablerIcons.Lock else TablerIcons.LockOpen,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val is_archived = api_item?.is_archived == true
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
            AsterIconButton(
                icon = TablerIcons.DotsVertical,
                content_description = stringResource(R.string.more),
                onClick = { show_topbar_menu = true },
                modifier = Modifier.testTag("more"),
            )
            aster_menu(
                expanded = show_topbar_menu,
                on_dismiss = { show_topbar_menu = false },
                offset = DpOffset(0.dp, 8.dp),
                min_width = 260.dp,
            ) {
                val is_pinned = api_item?.raw_item?.metadata?.is_pinned == true
                detail_menu_action(
                    icon = if (is_archived) TablerIcons.Inbox else TablerIcons.Archive,
                    text = if (is_archived) stringResource(R.string.swipe_move_to_inbox) else stringResource(R.string.archive_action),
                    tint = colors.text_primary,
                    test_tag = "archive",
                ) {
                    show_topbar_menu = false
                    if (is_archived) {
                        mail_vm.unarchive(listOf(email_id))
                        on_archive()
                    } else {
                        mail_vm.archive(listOf(email_id))
                        on_archive()
                    }
                }
                detail_menu_action(
                    icon = TablerIcons.Mail,
                    text = stringResource(R.string.mark_as_unread),
                    tint = colors.text_primary,
                ) {
                    show_topbar_menu = false
                    if (!left_after_unread) {
                        left_after_unread = true
                        mail_vm.mark_unread(email_id)
                        show_toast(context.getString(R.string.marked_as_unread))
                        on_back()
                    }
                }
                detail_menu_action(
                    icon = if (is_pinned) pin_icon_filled else pin_icon,
                    text = if (is_pinned) stringResource(R.string.unpin) else stringResource(R.string.pin_to_top),
                    tint = colors.text_primary,
                ) {
                    show_topbar_menu = false
                    mail_vm.toggle_pin(email_id)
                }
                detail_menu_action(
                    icon = TablerIcons.Moon,
                    text = stringResource(R.string.snooze),
                    tint = colors.text_primary,
                ) {
                    show_topbar_menu = false
                    show_snooze_sheet = true
                }
                detail_menu_divider()
                detail_menu_action(
                    icon = TablerIcons.Folder,
                    text = stringResource(R.string.move_to_folder),
                    tint = colors.text_primary,
                ) {
                    show_topbar_menu = false
                    settings_vm.load_labels()
                    show_folder_sheet = true
                }
                detail_menu_action(
                    icon = TablerIcons.Tag,
                    text = stringResource(R.string.add_label),
                    tint = colors.text_primary,
                ) {
                    show_topbar_menu = false
                    settings_vm.load_tags()
                    show_label_sheet = true
                }
                if (can_move_to_category(api_item, categories_enabled)) {
                    detail_menu_action(
                        icon = TablerIcons.LayoutGrid,
                        text = stringResource(R.string.move_to_category),
                        tint = colors.text_primary,
                        test_tag = "move_to_category",
                    ) {
                        show_topbar_menu = false
                        show_category_sheet = true
                    }
                }
                detail_menu_action(
                    icon = TablerIcons.Printer,
                    text = stringResource(R.string.print),
                    tint = colors.text_primary,
                ) {
                    show_topbar_menu = false
                    val msg = messages.lastOrNull()
                    if (msg != null) {
                        val print_subject = email?.subject.orEmpty()
                        val print_allow_external = message_loads_remote_content(
                            blocked = block_external_images,
                            granted = msg.id in allow_external_ids,
                            grant_allowed = image_grant_allowed,
                            is_system_sender = is_aster_system_sender(msg),
                        )
                        val print_sanitize_options =
                            remote_content_sanitize_options(settings_state.preferences)
                        scope.launch {
                            print_email(
                                context = context,
                                msg = msg,
                                subject = print_subject,
                                allow_external = print_allow_external,
                                sanitize_options = print_sanitize_options,
                            ) {
                                show_toast(context.getString(R.string.print_failed))
                            }
                        }
                    } else {
                        show_toast(context.getString(R.string.nothing_to_print))
                    }
                }
                detail_menu_action(
                    icon = TablerIcons.InfoCircle,
                    text = stringResource(R.string.message_details),
                    tint = colors.text_primary,
                    test_tag = "message_details",
                ) {
                    show_topbar_menu = false
                    show_message_details = true
                }
                detail_menu_action(
                    icon = TablerIcons.Code,
                    text = stringResource(R.string.detail_view_raw_source),
                    tint = colors.text_primary,
                ) {
                    show_topbar_menu = false
                    show_raw_source_dialog = true
                }
                detail_menu_divider()
                detail_menu_action(
                    icon = spam_action_icon(is_spam),
                    text = if (is_spam) stringResource(R.string.swipe_not_spam) else stringResource(R.string.report_spam),
                    tint = if (is_spam) colors.accent_blue else colors.danger,
                ) {
                    show_topbar_menu = false
                    val spam_sender_hint =
                        listOfNotNull(messages.lastOrNull()?.sender_email)
                    if (is_spam) {
                        is_spam_override = false
                        mail_vm.unmark_spam(listOf(email_id), sender_emails_hint = spam_sender_hint)
                    } else {
                        is_spam_override = true
                        mail_vm.mark_spam(listOf(email_id), sender_emails_hint = spam_sender_hint)
                    }
                    on_back()
                }
                detail_menu_action(
                    icon = TablerIcons.Ban,
                    text = stringResource(R.string.block_sender),
                    tint = colors.danger,
                ) {
                    show_topbar_menu = false
                    val sender = messages.lastOrNull()?.sender_email
                    if (!sender.isNullOrBlank()) {
                        pending_block_sender = sender
                    }
                }
                detail_menu_action(
                    icon = if (is_trashed) TablerIcons.TrashOff else TablerIcons.Trash,
                    text = if (is_trashed) {
                        stringResource(R.string.swipe_delete_forever)
                    } else {
                        stringResource(R.string.delete_action)
                    },
                    tint = colors.danger,
                ) {
                    show_topbar_menu = false
                    if (is_trashed) {
                        pending_delete_permanent = org.astermail.android.mail.permanent_delete_targets(
                            email_id,
                            message_scope = false,
                            thread_trash_flags = thread_trash_flags,
                        )
                    } else {
                        mail_vm.trash(listOf(email_id))
                        on_delete()
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun detail_bottom_bar(
    modifier: Modifier,
    flags: detail_screen_flags,
    email_id: String,
    messages: List<ThreadMessage>,
    thread_state: org.astermail.android.mail.ThreadUiState,
    api_item: org.astermail.android.mail.InboxItem?,
    inbox_state_for_folder: org.astermail.android.mail.InboxUiState,
    settings_state: org.astermail.android.settings.SettingsUiState,
    is_trashed: Boolean,
    is_starred: Boolean,
    is_spam: Boolean,
    thread_trash_flags: List<Pair<String, Boolean>>,
    thread_ghost_email: String?,
    reaction_restriction_for: (ThreadMessage) -> org.astermail.android.mail.ReactionRestriction?,
    dispatch_reaction: (String, String) -> Unit,
    show_toast: (String) -> Unit,
    mail_vm: MailViewModel,
    settings_vm: SettingsViewModel,
    on_reply: (String, String?) -> Unit,
    on_forward: (String, String?) -> Unit,
    on_back: () -> Unit,
    on_delete: () -> Unit,
    on_archive: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val density = LocalDensity.current
    val context = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var bottom_bar_height by flags.bottom_bar_height
    var reaction_picker_open by flags.reaction_picker_open
    var left_after_unread by flags.left_after_unread
    var pending_delete_permanent by flags.pending_delete_permanent
    var show_folder_sheet by flags.show_folder_sheet
    var show_label_sheet by flags.show_label_sheet
    var show_snooze_sheet by flags.show_snooze_sheet
    var is_spam_override by flags.is_spam_override
    var action_target_id by flags.action_target_id
    var action_menu_anchor by flags.action_menu_anchor
    var show_action_sheet by flags.show_action_sheet
    val latest_msg = messages.last()
    val detail_prefs_state by settings_vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(detail_prefs_state.preferences?.toolbar_actions) {
        val raw = detail_prefs_state.preferences?.toolbar_actions
        if (raw != null) {
            cache_toolbar_actions(context, parse_toolbar_actions(raw))
        }
    }
    val detail_toolbar_slots = remember(detail_prefs_state.preferences?.toolbar_actions) {
        load_toolbar_actions(context)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass_bar(colors)
            .pointerInput(Unit) {}
            .onGloballyPositioned { coords ->
                val measured = with(density) { coords.size.height.toDp() }
                if (measured > 0.dp && measured != bottom_bar_height) {
                    bottom_bar_height = measured
                }
            }
            .navigationBarsPadding(),
    ) {
        if (!colors.is_glass) AsterDivider(modifier = Modifier.fillMaxWidth())
        val react_target = messages.lastOrNull { reaction_restriction_for(it) == null } ?: latest_msg
        val latest_restriction = reaction_restriction_for(react_target)
        LaunchedEffect(latest_restriction) {
            if (latest_restriction != null) reaction_picker_open = false
        }
        run {
            reaction_quick_picker(
                visible = reaction_picker_open && latest_restriction == null,
                skin_tone = settings_state.preferences?.emoji_skin_tone
                    ?: org.astermail.android.api.preferences.EMOJI_SKIN_TONE_DEFAULT,
                on_skin_tone = { settings_vm.set_emoji_skin_tone(it) },
                on_pick = { emoji ->
                    reaction_picker_open = false
                    val blocked = reaction_restriction_for(react_target)
                    if (blocked != null) {
                        show_toast(
                            context.getString(
                                org.astermail.android.mail.reaction_restriction_string(blocked),
                            ),
                        )
                        return@reaction_quick_picker
                    }
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    dispatch_reaction(react_target.id, emoji)
                },
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            reply_action_row(
                on_reply = { on_reply(latest_msg.id, thread_ghost_email) },
                on_forward = { on_forward(latest_msg.id, thread_ghost_email) },
                show_react = latest_restriction != org.astermail.android.mail.ReactionRestriction.disabled,
                react_enabled = latest_restriction == null,
                on_react = {
                    val blocked = reaction_restriction_for(react_target)
                    if (blocked != null) {
                        show_toast(
                            context.getString(
                                org.astermail.android.mail.reaction_restriction_string(blocked),
                            ),
                        )
                    } else {
                        reaction_picker_open = !reaction_picker_open
                    }
                },
            )
            thread_draft_slot(
                email_id = email_id,
                thread_token = thread_state.item?.thread_token,
                mail_vm = mail_vm,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.md)
                    .padding(top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                detail_toolbar_slots.forEach { slot_id ->
                    when (slot_id) {
                        "read" -> {
                            val read_state = api_item?.is_read == true
                            if (read_state) {
                                bottom_action(TablerIcons.Mail, stringResource(R.string.mark_as_unread), test_tag = "mark_read") {
                                    if (!left_after_unread) {
                                        left_after_unread = true
                                        mail_vm.mark_unread(email_id)
                                        show_toast(context.getString(R.string.marked_as_unread))
                                        on_back()
                                    }
                                }
                            } else {
                                bottom_action(TablerIcons.MailOpened, stringResource(R.string.mark_as_read), test_tag = "mark_read") {
                                    mail_vm.mark_read(email_id)
                                    show_toast(context.getString(R.string.marked_as_read))
                                }
                            }
                        }
                        "trash" -> {
                            if (is_trashed) {
                                bottom_action(TablerIcons.TrashOff, stringResource(R.string.swipe_delete_forever), test_tag = "delete") {
                                    pending_delete_permanent = org.astermail.android.mail.permanent_delete_targets(
                                        email_id,
                                        message_scope = false,
                                        thread_trash_flags = thread_trash_flags,
                                    )
                                }
                            } else {
                                bottom_action(TablerIcons.Trash, stringResource(if (inbox_state_for_folder.current_folder == "drafts") R.string.discard_draft else R.string.move_to_trash), test_tag = "delete") {
                                    mail_vm.trash(listOf(email_id))
                                    on_delete()
                                }
                            }
                        }
                        "archive" -> if (is_trashed) {
                            bottom_action(TablerIcons.Inbox, stringResource(R.string.swipe_restore), test_tag = "toolbar_archive") {
                                mail_vm.restore_trash(listOf(email_id))
                                on_delete()
                            }
                        } else {
                            val archived = api_item?.is_archived == true
                            bottom_action(
                                if (archived) TablerIcons.Inbox else TablerIcons.Archive,
                                if (archived) stringResource(R.string.swipe_restore) else stringResource(R.string.swipe_archive),
                                test_tag = "toolbar_archive",
                            ) {
                                if (archived) {
                                    mail_vm.unarchive(listOf(email_id))
                                    on_archive()
                                } else {
                                    mail_vm.archive(listOf(email_id))
                                    on_archive()
                                }
                            }
                        }
                        "folder" -> bottom_action(TablerIcons.Folder, stringResource(R.string.move_to_folder)) {
                            settings_vm.load_labels(force = settings_state.labels.isEmpty())
                            show_folder_sheet = true
                        }
                        "label" -> bottom_action(TablerIcons.Tag, stringResource(R.string.label)) {
                            settings_vm.load_tags(force = settings_state.tags.isEmpty())
                            show_label_sheet = true
                        }
                        "star" -> bottom_action(
                            if (is_starred) TablerIcons.StarOff else TablerIcons.Star,
                            if (is_starred) stringResource(R.string.unstar) else stringResource(R.string.star),
                            test_tag = "toolbar_star",
                        ) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            mail_vm.toggle_star(email_id)
                            show_toast(if (!is_starred) context.getString(R.string.starred) else context.getString(R.string.unstarred))
                        }
                        "snooze" -> bottom_action(TablerIcons.Clock, stringResource(R.string.snooze)) {
                            show_snooze_sheet = true
                        }
                        "spam" -> bottom_action(
                            spam_action_icon(is_spam),
                            if (is_spam) stringResource(R.string.swipe_not_spam) else stringResource(R.string.report_spam),
                            test_tag = "toolbar_spam",
                        ) {
                            val spam_sender_hint = listOfNotNull(messages.lastOrNull()?.sender_email)
                            if (is_spam) {
                                is_spam_override = false
                                mail_vm.unmark_spam(listOf(email_id), sender_emails_hint = spam_sender_hint)
                            } else {
                                is_spam_override = true
                                mail_vm.mark_spam(listOf(email_id), sender_emails_hint = spam_sender_hint)
                            }
                            on_back()
                        }
                        "reply" -> bottom_action(TablerIcons.ArrowBackUp.auto_mirrored(), stringResource(R.string.reply), test_tag = "toolbar_reply") {
                            on_reply(latest_msg.id, thread_ghost_email)
                        }
                        "forward" -> bottom_action(TablerIcons.MailForward, stringResource(R.string.forward), test_tag = "toolbar_forward") {
                            on_forward(latest_msg.id, thread_ghost_email)
                        }
                    }
                }
                bottom_action(TablerIcons.Dots, stringResource(R.string.more)) {
                    action_target_id = null
                    action_menu_anchor = null
                    show_action_sheet = true
                }
            }
        }
    }
}

@Composable
private fun detail_overlays(
    flags: detail_screen_flags,
    email_id: String,
    email: Email?,
    messages: List<ThreadMessage>,
    api_item: org.astermail.android.mail.InboxItem?,
    inbox_state_for_folder: org.astermail.android.mail.InboxUiState,
    settings_state: org.astermail.android.settings.SettingsUiState,
    is_starred: Boolean,
    is_spam: Boolean,
    is_trashed: Boolean,
    thread_trash_flags: List<Pair<String, Boolean>>,
    message_is_trashed: (String) -> Boolean,
    thread_ghost_email: String?,
    reaction_own_addresses: Set<String>,
    is_thread_encrypted: Boolean,
    is_thread_pgp: Boolean,
    is_thread_outside_pgp: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    request_storage_access: (() -> Unit) -> Unit,
    save_as_pending: Array<ByteArray?>,
    save_as_launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    show_toast: (String) -> Unit,
    announce_saved: (saved_attachment?) -> Unit,
    with_attachment_bytes: (MessageAttachment, (MessageAttachment, ByteArray) -> Unit) -> Unit,
    mail_vm: MailViewModel,
    settings_vm: SettingsViewModel,
    on_reply: (String, String?) -> Unit,
    on_reply_all: (String, String?) -> Unit,
    on_forward: (String, String?) -> Unit,
    on_back: () -> Unit,
    on_delete: () -> Unit,
    on_archive: () -> Unit,
    on_navigate: ((String) -> Unit)?,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var show_action_sheet by flags.show_action_sheet
    val action_target_id by flags.action_target_id
    val action_menu_anchor by flags.action_menu_anchor
    var left_after_unread by flags.left_after_unread
    var pending_delete_permanent by flags.pending_delete_permanent
    var is_spam_override by flags.is_spam_override
    var show_snooze_sheet by flags.show_snooze_sheet
    var show_label_sheet by flags.show_label_sheet
    var show_folder_sheet by flags.show_folder_sheet
    var show_category_sheet by flags.show_category_sheet
    var show_message_details by flags.show_message_details
    var show_raw_source_dialog by flags.show_raw_source_dialog
    var show_encryption_info by flags.show_encryption_info
    var preview_attachment by flags.preview_attachment
    var preview_bytes by flags.preview_bytes
    var lightbox_src by flags.lightbox_src
    var options_attachment by flags.options_attachment
    var pending_link by flags.pending_link
    var profile_sender by flags.profile_sender
    var profile_sender_trust by flags.profile_sender_trust
    var pending_block_sender by flags.pending_block_sender
    run {
        val target = action_target_id ?: messages.lastOrNull()?.id.orEmpty()
        val item_target = action_target_id ?: email_id
        val message_scope = action_target_id != null
        val target_trashed = message_is_trashed(item_target)
        val destructive_action = org.astermail.android.mail.destructive_action_for(
            is_trashed = target_trashed,
            is_draft = inbox_state_for_folder.current_folder == "drafts",
        )
        action_menu_sheet(
            is_draft = destructive_action == org.astermail.android.mail.DestructiveAction.discard_draft,
            is_trashed = target_trashed,
            on_restore = {
                show_action_sheet = false
                mail_vm.restore_trash(listOf(item_target))
                on_delete()
            },
            expanded = show_action_sheet,
            on_close = { show_action_sheet = false },
            on_reply = { show_action_sheet = false; on_reply(target, thread_ghost_email) },
            on_reply_all = { show_action_sheet = false; on_reply_all(target, thread_ghost_email) },
            on_forward = { show_action_sheet = false; on_forward(target, thread_ghost_email) },
            on_star = {
                show_action_sheet = false
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                mail_vm.toggle_star(item_target)
                show_toast(if (!is_starred) context.getString(R.string.starred) else context.getString(R.string.unstarred))
            },
            is_starred = is_starred,
            on_mark_unread = {
                show_action_sheet = false
                if (!left_after_unread) {
                    left_after_unread = true
                    mail_vm.mark_unread(item_target)
                    show_toast(context.getString(R.string.marked_as_unread))
                    on_back()
                }
            },
            on_archive = {
                show_action_sheet = false
                if (api_item?.is_archived == true) {
                    mail_vm.unarchive(listOf(item_target))
                    on_archive()
                } else {
                    mail_vm.archive(listOf(item_target), message_scope = message_scope)
                    on_archive()
                }
            },
            is_archived = api_item?.is_archived == true,
            on_trash = {
                show_action_sheet = false
                if (destructive_action == org.astermail.android.mail.DestructiveAction.delete_forever) {
                    pending_delete_permanent = org.astermail.android.mail.permanent_delete_targets(
                        item_target,
                        message_scope = message_scope,
                        thread_trash_flags = thread_trash_flags,
                    )
                } else {
                    mail_vm.trash(listOf(item_target), message_scope = message_scope)
                    on_delete()
                }
            },
            on_spam = {
                show_action_sheet = false
                val target_message = messages.firstOrNull { it.id == item_target } ?: messages.lastOrNull()
                val spam_sender_hint = listOfNotNull(target_message?.sender_email)
                if (is_spam) {
                    is_spam_override = false
                    mail_vm.unmark_spam(listOf(item_target), sender_emails_hint = spam_sender_hint)
                } else {
                    is_spam_override = true
                    mail_vm.mark_spam(listOf(item_target), sender_emails_hint = spam_sender_hint)
                }
                on_back()
            },
            is_spam = is_spam,
            on_snooze = {
                show_action_sheet = false
                show_snooze_sheet = true
            },
            on_label = {
                show_action_sheet = false
                settings_vm.load_tags(force = settings_vm.state.value.tags.isEmpty())
                show_label_sheet = true
            },
            on_customize_toolbar = {
                show_action_sheet = false
                on_navigate?.invoke("settings/customize_toolbar")
            },
            anchor = action_menu_anchor,
        )
    }

    val show_preview = preview_attachment != null && preview_bytes != null
    val preview_reduce_motion = aster_reduce_motion()
    AnimatedVisibility(
        visible = show_preview,
        enter = if (preview_reduce_motion) {
            fadeIn(animationSpec = tween(AsterDuration.instant))
        } else {
            fadeIn(
                animationSpec = tween(AsterDuration.dialog_enter, easing = AsterEasing.dialog_enter),
            ) + slideInVertically(
                animationSpec = tween(AsterDuration.dialog_enter, easing = AsterEasing.dialog_enter),
                initialOffsetY = { it / 6 },
            )
        },
        exit = if (preview_reduce_motion) {
            fadeOut(animationSpec = tween(AsterDuration.instant))
        } else {
            fadeOut(
                animationSpec = tween(AsterDuration.dialog_exit, easing = AsterEasing.dialog_exit),
            ) + slideOutVertically(
                animationSpec = tween(AsterDuration.dialog_exit, easing = AsterEasing.dialog_exit),
                targetOffsetY = { it / 6 },
            )
        },
    ) {
        val att = preview_attachment
        val byt = preview_bytes
        if (att != null && byt != null) {
            attachment_preview_dialog(
                attachment = att,
                bytes = byt,
                on_close = {
                    preview_attachment = null
                    preview_bytes = null
                },
                on_download = {
                    request_storage_access {
                        scope.launch {
                            val saved = save_attachment_to_storage(context, att, byt)
                            announce_saved(saved)
                            if (saved != null) {
                                preview_attachment = null
                                preview_bytes = null
                            }
                        }
                    }
                },
            )
        }
    }

    lightbox_src?.let { src ->
        email_image_lightbox(
            src = src,
            auth_header = settings_vm.get_access_token()?.let { "Bearer $it" },
            on_dismiss = { lightbox_src = null },
        )
    }

    options_attachment?.let { att ->
        attachment_options_sheet(
            attachment = att,
            on_close = { options_attachment = null },
            on_open = {
                with_attachment_bytes(att) { resolved_att, bytes ->
                    val opened = runCatching {
                        open_attachment_externally(context, resolved_att.filename, resolved_att.content_type, bytes)
                    }.getOrDefault(false)
                    if (!opened) show_toast(context.getString(R.string.no_app_to_open))
                }
            },
            on_open_downloads = {
                if (!open_downloads_folder(context)) show_toast(context.getString(R.string.no_app_to_open))
            },
            on_share = {
                with_attachment_bytes(att) { resolved_att, bytes ->
                    if (!share_attachment(context, resolved_att.filename, resolved_att.content_type, bytes)) {
                        show_toast(context.getString(R.string.no_app_to_open))
                    }
                }
            },
            on_save_as = {
                with_attachment_bytes(att) { resolved_att, bytes ->
                    save_as_pending[0] = bytes
                    val launched = runCatching {
                        save_as_launcher.launch(save_as_intent(resolved_att.filename, resolved_att.content_type))
                    }.isSuccess
                    if (!launched) {
                        save_as_pending[0] = null
                        show_toast(context.getString(R.string.no_app_to_open))
                    }
                }
            },
        )
    }

    val current_link = pending_link
    if (current_link != null && current_link.startsWith("aster:")) {
        val aster_path = current_link.removePrefix("aster:")
        LaunchedEffect(aster_path) {
            pending_link = null
            on_navigate?.invoke(aster_path)
        }
    }

    if (current_link != null && !current_link.startsWith("aster:")) {
        val link = current_link
        val open_external_link = {
            pending_link = null
            if (!is_safe_external_url(link)) {
                show_toast(context.getString(R.string.could_not_open_link))
            } else {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    show_toast(context.getString(R.string.could_not_open_link))
                }
            }
        }
        if (settings_state.preferences?.warn_suspicious_links == false) {
            LaunchedEffect(link) { open_external_link() }
        } else {
            org.astermail.android.design.components.AsterAlertDialog(
                on_dismiss = { pending_link = null },
                title = stringResource(R.string.open_external_link),
                message = stringResource(R.string.leaving_aster_warning),
                confirm_label = stringResource(R.string.open),
                cancel_label = stringResource(R.string.cancel),
                on_confirm = open_external_link,
                secondary_label = stringResource(R.string.copy_link),
                on_secondary = {
                    copy_external_link(
                        link = link,
                        write_clip = { label, text ->
                            write_to_clipboard(context, android.content.ClipData.newPlainText(label, text))
                        },
                        on_copied = { show_toast(context.getString(R.string.link_copied)) },
                        on_failed = { show_copy_failed_toast(context) },
                    )
                },
                extra_content = {
                    Text(
                        text = link,
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }

    val profile_target = profile_sender
    val profile_address = profile_target?.first?.trim()?.lowercase().orEmpty()
    LaunchedEffect(profile_address.isNotEmpty()) {
        if (profile_address.isNotEmpty() && settings_state.blocked_senders.isEmpty() &&
            !settings_state.blocked_senders_loading
        ) {
            settings_vm.load_blocked_senders()
        }
    }
    if (profile_target != null) {
        val profile_blocked = settings_state.blocked_senders.firstOrNull {
            !it.is_domain && it.address.trim().lowercase() == profile_address
        }
        sender_profile_sheet(
            sender_email = profile_target.first,
            sender_name = profile_target.second,
            trust = profile_sender_trust,
            is_own_address = profile_address in reaction_own_addresses,
            on_open_alias_settings = on_navigate?.let { navigate ->
                { address: String ->
                    val normalized = address.trim().lowercase()
                    val alias_id = settings_state.aliases.firstOrNull {
                        !it.decryption_failed && it.address.trim().lowercase() == normalized
                    }?.id
                    navigate(if (alias_id.isNullOrBlank()) "settings/aliases" else "alias:$alias_id")
                }
            },
            on_open_contact = on_navigate?.let { navigate -> { id: String -> navigate("contact:$id") } },
            is_blocked = profile_blocked != null,
            on_unblock = profile_blocked?.let { blocked -> { _: String -> settings_vm.unblock_sender(blocked.sender_token) } },
            on_close = {
                profile_sender = null
                profile_sender_trust = null
            },
            on_copy = { address ->
                val copied = write_to_clipboard(
                    context,
                    android.content.ClipData.newPlainText("email_address", address),
                )
                if (copied) {
                    show_toast(org.astermail.android.ui.common.copied_toast_text(context, address))
                } else {
                    show_copy_failed_toast(context)
                }
            },
            on_search_sender = { address ->
                org.astermail.android.ui.search.build_sender_mail_query(address)?.let { query ->
                    on_navigate?.invoke("search:$query")
                }
            },
            on_send_email = { address ->
                context.startActivity(
                    org.astermail.android.ComposeActivity.intent_for(context, prefill_to = address),
                )
            },
            on_block = { address -> pending_block_sender = address },
            on_result = { message -> show_toast(message) },
        )
    }

    val delete_permanent_ids = pending_delete_permanent
    if (delete_permanent_ids != null) {
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { pending_delete_permanent = null },
            title = stringResource(R.string.confirm_delete_permanent_title),
            message = if (delete_permanent_ids.size > 1) {
                stringResource(
                    R.string.confirm_delete_permanent_message_count,
                    pluralStringResource(
                        R.plurals.common_messages_count,
                        delete_permanent_ids.size,
                        delete_permanent_ids.size,
                    ),
                )
            } else {
                stringResource(R.string.confirm_delete_permanent_message)
            },
            confirm_label = stringResource(R.string.swipe_delete_forever),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                pending_delete_permanent = null
                mail_vm.delete_permanent_bulk(delete_permanent_ids)
                on_delete()
            },
        )
    }

    val block_target = pending_block_sender
    if (block_target != null) {
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { pending_block_sender = null },
            title = stringResource(R.string.block_sender),
            message = stringResource(R.string.block_sender_confirm_message),
            confirm_label = stringResource(R.string.block_sender),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                pending_block_sender = null
                settings_vm.block_sender(block_target) { ok ->
                    if (ok) {
                        show_toast(context.getString(R.string.sender_blocked_named, block_target))
                    } else {
                        show_toast(context.getString(R.string.failed_block_sender))
                    }
                }
                on_back()
            },
            extra_content = {
                Text(
                    text = block_target,
                    color = colors.accent_blue,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }

    if (show_encryption_info) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_encryption_info = false },
            title = encryption_badge_label(is_thread_encrypted, is_thread_pgp),
            body = { encryption_info_body(is_thread_encrypted, is_thread_pgp, is_thread_outside_pgp) },
            footer = {
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.done),
                    onClick = { show_encryption_info = false },
                )
            },
        )
    }

    if (show_snooze_sheet) {
        snooze_sheet(
            on_close = { show_snooze_sheet = false },
            on_pick = { iso, label ->
                show_snooze_sheet = false
                mail_vm.snooze_until(email_id, iso, label)
                on_back()
            },
        )
    }

    if (show_folder_sheet) {
        val source_folder = inbox_state_for_folder.current_folder
        val settings_state by settings_vm.state.collectAsStateWithLifecycle()
        val unnamed_folder_label = stringResource(R.string.unnamed_folder)
        val folder_decrypt_failed_label = stringResource(R.string.folder_decrypt_failed)
        val folder_nodes = org.astermail.android.folders.flatten_folder_tree(settings_state.labels)
        val folder_items = folder_nodes
            .map { node ->
                val label = node.label
                val readable = org.astermail.android.labels.readable_label_name(label)
                label.copy(encrypted_name = readable ?: folder_decrypt_failed_label)
            }
        label_picker_sheet(
            title = stringResource(R.string.move_to_folder),
            empty_message = stringResource(R.string.no_folders_yet_create),
            items = folder_items,
            depths = folder_nodes.associate { it.label.label_token to it.depth },
            on_close = { show_folder_sheet = false },
            on_pick = { picked ->
                val display = picked.encrypted_name?.takeIf { it.isNotBlank() }
                    ?: unnamed_folder_label
                mail_vm.move_to_folder_bulk(listOf(email_id), picked.label_token, display)
                show_folder_sheet = false
                on_back()
            },
            on_move_to_inbox = if (can_move_to_inbox(source_folder)) {
                {
                    mail_vm.move_to_inbox(listOf(email_id), source_folder)
                    show_folder_sheet = false
                    on_back()
                }
            } else {
                null
            },
        )
    }

    val category_item = api_item
    if (show_category_sheet && category_item != null) {
        val plan_vm_categories: org.astermail.android.billing.PlanLimitsViewModel = hiltViewModel()
        val plan_state_categories by plan_vm_categories.state.collectAsStateWithLifecycle()
        val custom_categories = settings_state.preferences?.custom_categories.orEmpty()
        val active_tabs = category_move_tabs(
            settings_state.preferences?.enabled_categories,
            custom_categories,
            plan_state_categories.limits?.limits?.get("max_custom_categories")?.limit ?: -1,
        )
        move_to_category_sheet(
            current_category = category_item.category,
            active_tabs = active_tabs,
            custom_categories = custom_categories,
            on_pick = { picked -> mail_vm.move_to_category(category_item.id, picked) },
            on_close = { show_category_sheet = false },
        )
    }

    if (show_label_sheet) {
        val settings_state by settings_vm.state.collectAsStateWithLifecycle()
        val label_item = api_item
        val label_target_id = label_item?.id ?: email_id
        val applied_tags = remember(label_item, inbox_state_for_folder.items) {
            org.astermail.android.labels.conversation_tag_tokens(
                label_item?.as_conversation_member(),
                inbox_state_for_folder.items.map { it.as_conversation_member() },
            ).toSet()
        }
        val tag_items = org.astermail.android.labels.tag_rows(settings_state.tags)
        val unknown_label = stringResource(R.string.label_decrypt_failed)
        tag_picker_sheet(
            title = stringResource(R.string.edit_labels),
            empty_message = stringResource(R.string.no_labels_yet_create),
            items = tag_items,
            on_close = { show_label_sheet = false },
            on_pick = { picked ->
                val display = org.astermail.android.labels.tag_display_name(picked, unknown_label)
                if (picked.tag_token in applied_tags) {
                    mail_vm.remove_tag_from_conversation(label_target_id, picked.tag_token, display)
                } else {
                    mail_vm.apply_tag_to_conversation(label_target_id, picked.tag_token, display)
                }
            },
            applied_tokens = applied_tags,
        )
    }

    if (show_message_details) {
        message_details_dialog(
            message = messages.lastOrNull(),
            subject = email?.subject.orEmpty(),
            on_close = { show_message_details = false },
        )
    }

    if (show_raw_source_dialog) {
        val msg = messages.lastOrNull()
        raw_source_dialog(
            message = msg,
            subject = email?.subject.orEmpty(),
            on_close = { show_raw_source_dialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun expanded_message(
    msg: ThreadMessage,
    is_last: Boolean,
    message_index: Int = 0,
    thread_attachments: List<MessageAttachment> = emptyList(),
    is_first_card: Boolean = true,
    is_last_card: Boolean = true,
    my_email: String = "",
    my_profile_pic: String? = null,
    allow_external: Boolean = false,
    blocked_for_traffic: Boolean = false,
    offer_always_allow: Boolean = true,
    offer_load_once: Boolean = true,
    on_load_external: () -> Unit = {},
    on_always_allow_external: () -> Unit = {},
    on_disable_low_network: () -> Unit = {},
    show_unsub: Boolean = true,
    on_dismiss_unsub: () -> Unit = {},
    on_unsubscribe: (UnsubscribeInfo) -> Unit = {},
    on_body_ready: () -> Unit = {},
    on_retry_decrypt: () -> Unit = {},
    retry_in_progress: Boolean = false,
    on_recover_data: (() -> Unit)? = null,
    on_track: (String, String, String?) -> Unit = { _, _, _ -> },
    access_token: String? = null,
    on_link_click: (String) -> Unit = {},
    on_image_click: (String) -> Unit = {},
    on_collapse: () -> Unit,
    on_sender_tap: (String, String) -> Unit = { _, _ -> },
    on_sender_trust: (org.astermail.android.security.SenderTrust?) -> Unit = {},
    on_reply: () -> Unit,
    on_reply_all: () -> Unit,
    on_forward: () -> Unit,
    on_more: (IntRect) -> Unit,
    on_attachment_tap: (MessageAttachment) -> Unit = {},
    on_attachment_download: (MessageAttachment) -> Unit = {},
    on_attachment_options: (MessageAttachment) -> Unit = {},
    attachments_failed: Boolean = false,
    attachments_loading: Boolean = false,
    pending_attachments: List<MessageAttachment> = emptyList(),
    on_retry_attachments: () -> Unit = {},
    reactions: List<DecryptedReaction> = emptyList(),
    reaction_placeholders: Int = 0,
    on_react: (String) -> Unit = {},
    on_unreact: (String) -> Unit = {},
    is_system: Boolean = false,
    can_collapse: Boolean = true,
    show_raw_headers: Boolean = false,
    show_header_reply: Boolean = true,
    identity_changed: Boolean = false,
    sender_unauthenticated: Boolean = false,
    on_acknowledge_identity: () -> Unit = {},
    is_pending_send: Boolean = false,
    on_retry_pending_send: () -> Unit = {},
    translation_enabled: Boolean = true,
    allow_auto_translate: Boolean = true,
    translation_subject: String? = null,
    on_subject_translated: (String?) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val banner_reduce_motion = aster_reduce_motion()
    val context = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val copy_email = { email: String ->
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        val copied = write_to_clipboard(
            context,
            android.content.ClipData.newPlainText("email_address", email),
        )
        show_copy_result_toast(context, email, copied)
    }
    val is_own_message = my_email.isNotEmpty() &&
        msg.sender_email.trim().lowercase() == my_email &&
        !msg.is_external
    val trust = remember_sender_trust(if (is_own_message) null else msg.trust_message)
    val marks_hidden = trust?.show_identity_marks == false
    val shows_official_badge = !is_own_message && is_official_sender(msg)
    val shows_verified_check = !shows_official_badge && trust?.show_verified_badge == true && msg.sender_verified_domain != null
    var show_details by remember { mutableStateOf(false) }
    var addresses_expanded by remember(msg.id) { mutableStateOf(false) }
    var sender_name_truncated by remember(msg.id) { mutableStateOf(false) }
    var show_sender_verified by remember(msg.id) { mutableStateOf(false) }
    if (show_sender_verified && shows_verified_check) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_sender_verified = false },
            title = stringResource(R.string.sender_verified_title),
            message = stringResource(R.string.sender_verified_message, msg.sender_verified_domain ?: ""),
            footer = {
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.done),
                    onClick = { show_sender_verified = false },
                )
            },
        )
    }
    val content_analysis_key = remember(msg.id, msg.body_html) { card_analysis_memo.key_of(msg.id, msg.body_html) }
    val content_analysis by androidx.compose.runtime.produceState(
        initialValue = card_analysis_memo.content_get(content_analysis_key) ?: MessageContentAnalysis(),
        content_analysis_key,
    ) {
        val cached = card_analysis_memo.content_get(content_analysis_key)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            analyze_message_content(msg.body_html)
        }
        card_analysis_memo.content_put(content_analysis_key, result)
        value = result
    }
    val tracker_report = content_analysis.tracker_report
    var show_tracker_details by remember(msg.id) { mutableStateOf(false) }
    if (show_tracker_details) {
        tracker_details_dialog(report = tracker_report, on_close = { show_tracker_details = false })
    }
    val chevron_rotation by animateFloatAsState(
        targetValue = if (show_details) 180f else 0f,
        animationSpec = tween(org.astermail.android.design.AsterMotionSpec.icon_ms, easing = AsterEasing.emphasized),
        label = "chevron",
    )
    val auth_status = remember(msg.id, msg.item_type, msg.spf_result, msg.dkim_result, msg.dmarc_result) {
        sender_auth_status(msg)
    }
    val auth_summary = remember(msg.id, msg.item_type, msg.spf_result, msg.dkim_result, msg.dmarc_result) {
        summarize_email_authentication(msg)
    }

    val unsub_header = remember(msg.raw_headers) {
        msg.raw_headers.firstOrNull { it.first.equals("list-unsubscribe", ignoreCase = true) }?.second
    }
    val unsub_post_header = remember(msg.raw_headers) {
        msg.raw_headers.firstOrNull { it.first.equals("list-unsubscribe-post", ignoreCase = true) }?.second
    }
    val unsub_key = remember(msg.id, msg.body_html, msg.body, unsub_header, unsub_post_header, msg.dkim_result) {
        unsub_info_memo.key_of(msg.id, msg.body_html, msg.body, unsub_header, unsub_post_header, msg.dkim_result)
    }
    val unsub_info by androidx.compose.runtime.produceState(
        initialValue = unsub_info_memo.get(unsub_key) ?: detect_unsubscribe_info(
            list_unsubscribe = unsub_header,
            list_unsubscribe_post = unsub_post_header,
            dkim_result = msg.dkim_result,
        ),
        unsub_key,
    ) {
        val cached = unsub_info_memo.get(unsub_key)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            detect_unsubscribe_info(
                html_content = msg.body_html,
                text_content = msg.body,
                list_unsubscribe = unsub_header,
                list_unsubscribe_post = unsub_post_header,
                dkim_result = msg.dkim_result,
            )
        }
        unsub_info_memo.put(unsub_key, result)
        value = result
    }
    val show_unsub_link = unsubscribe_link_visible(show_unsub, unsub_info)

    val card_color = inbox_card_read_color(colors)
    val card_shape = remember(is_first_card, is_last_card) {
        inbox_group_shape(is_first_card, is_last_card)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last_card) 0.dp else inbox_group_split,
            )
            .clip(card_shape)
            .acrylic_backdrop(colors)
            .background(card_color)
            .background(email_glass_backing(colors, white_page = false)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (can_collapse) Modifier.clickable(onClick = on_collapse) else Modifier)
                .padding(
                    start = inbox_card_content_padding,
                    end = AsterSpacing.sm,
                    top = AsterSpacing.md,
                    bottom = AsterSpacing.sm,
                )
                .testTag("message_header_$message_index"),
            verticalAlignment = Alignment.Top,
        ) {
            val shown_sender_name = org.astermail.android.security.trusted_sender_name(
                trust,
                displayed_sender_name(msg.display_sender_name, msg.sender_name),
                stringResource(R.string.unknown),
            )
            val shown_sender_email = displayed_sender_email(msg.display_sender_email, msg.sender_email)
            SenderAvatar(
                email = shown_sender_email,
                name = shown_sender_name,
                sender_authenticated = !marks_hidden &&
                    trust?.warning == null &&
                    system_avatar_authenticated(msg),
                profile_picture_url = if (is_own_message) my_profile_pic else null,
                plain = marks_hidden,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        on_sender_trust(trust)
                        on_sender_tap(shown_sender_email, shown_sender_name)
                    },
            )
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = shown_sender_name,
                                color = colors.text_primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = if (addresses_expanded) 3 else 1,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { if (!addresses_expanded) sender_name_truncated = it.hasVisualOverflow },
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .combinedClickable(
                                        hapticFeedbackEnabled = false,
                                        onClick = {
                                            if (sender_name_truncated || addresses_expanded) {
                                                addresses_expanded = !addresses_expanded
                                            } else if (can_collapse) {
                                                on_collapse()
                                            }
                                        },
                                        onLongClick = { copy_email(shown_sender_email) },
                                    ),
                            )
                            if (shows_official_badge) {
                                Spacer(Modifier.width(4.dp))
                                official_badge()
                            }
                            if (shows_verified_check) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = TablerIcons.CircleCheck,
                                    contentDescription = stringResource(R.string.sender_verified_badge),
                                    tint = colors.accent_blue,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable { show_sender_verified = true },
                                )
                            }
                        }
                        custom_forward_via_label(msg.display_sender_email, msg.sender_email)?.let { via_label ->
                            Text(
                                text = via_label,
                                color = colors.text_muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("message_forwarded_via_$message_index"),
                            )
                        }
                        email_auth_badge(
                            msg = msg,
                            unverified = trust?.auth_state == org.astermail.android.security.SenderAuthState.unverified,
                            unverified_domain = trust?.domain,
                        )
                        priority_badge(raw_headers = msg.raw_headers)
                    }
                    Spacer(Modifier.width(AsterSpacing.sm))
                    val header_yesterday_label = stringResource(R.string.yesterday)
                    val header_relative_time = remember(msg.timestamp, header_yesterday_label, AsterTimePreferences.generation) {
                        msg.timestamp.format_message_time(header_yesterday_label)
                    }
                    Text(
                        text = header_relative_time,
                        color = colors.text_muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = inbox_card_content_padding - AsterSpacing.sm),
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Spacer(Modifier.height(1.dp))
                        if (trust != null && trust.name.isNotEmpty() && trust.address.isNotEmpty()) {
                            Text(
                                text = isolated_slot_value(trust.address),
                                color = colors.text_muted,
                                fontSize = 12.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .testTag("message_sender_address_$message_index")
                                    .combinedClickable(
                                        hapticFeedbackEnabled = false,
                                        onClick = { show_details = !show_details },
                                        onLongClick = { copy_email(trust.address) },
                                    ),
                            )
                            Spacer(Modifier.height(1.dp))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.combinedClickable(
                                hapticFeedbackEnabled = false,
                                onClick = { show_details = !show_details },
                                onLongClick = {
                                    val recipient = msg.to_addresses.joinToString(", ").ifBlank { msg.to_label }
                                    copy_email(recipient)
                                },
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.to_label_prefix, msg.to_label),
                                color = colors.text_muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                imageVector = TablerIcons.ChevronDown,
                                contentDescription = if (show_details) {
                                    stringResource(R.string.detail_hide_details)
                                } else {
                                    stringResource(R.string.detail_show_details)
                                },
                                tint = colors.text_muted,
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(15.dp)
                                    .graphicsLayer(rotationZ = chevron_rotation),
                            )
                        }
                        val header_received_on = remember(msg) {
                            resolve_received_on_address(msg.raw_headers, msg.to_addresses + msg.cc_addresses, msg.sender_email)
                        }
                        val header_alias_label = alias_indicator_store.label_for(header_received_on)
                        if (header_alias_label != null) {
                            Spacer(Modifier.height(4.dp))
                            alias_chip(header_alias_label, modifier = Modifier.widthIn(max = 200.dp))
                        }
                        AnimatedVisibility(
                            visible = show_unsub_link,
                            enter = aster_expand_in(banner_reduce_motion),
                            exit = aster_collapse_out(banner_reduce_motion),
                        ) {
                            LaunchedEffect(msg.id) {
                                on_track(
                                    msg.sender_email,
                                    if (trust != null && trust.name.isEmpty()) "" else msg.sender_name,
                                    unsub_info.unsubscribe_link,
                                )
                            }
                            unsubscribe_link(on_unsubscribe = { on_unsubscribe(unsub_info) })
                        }
                    }
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (show_header_reply && !is_pending_send) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button, onClick = on_reply),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = TablerIcons.ArrowBackUp,
                                    contentDescription = stringResource(R.string.reply),
                                    tint = colors.text_secondary,
                                    modifier = Modifier.size(22.dp).mirror_in_rtl(),
                                )
                            }
                        }
                        val more_coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
                        if (!is_pending_send) Box(
                            modifier = Modifier
                                .size(40.dp)
                                .onGloballyPositioned { more_coordinates[0] = it }
                                .clip(CircleShape)
                                .clickable(role = Role.Button) {
                                    val coordinates = more_coordinates[0]
                                    on_more(
                                        if (coordinates != null && coordinates.isAttached) {
                                            coordinates.boundsInWindow().roundToIntRect()
                                        } else {
                                            IntRect.Zero
                                        },
                                    )
                                }
                                .testTag("message_more_$message_index"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = TablerIcons.DotsVertical,
                                contentDescription = stringResource(R.string.more_options),
                                tint = colors.text_secondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = show_details,
            enter = aster_expand_in(banner_reduce_motion),
            exit = aster_collapse_out(banner_reduce_motion),
        ) {
            val received_on = remember(msg) {
                resolve_received_on_address(msg.raw_headers, msg.to_addresses + msg.cc_addresses, msg.sender_email)
            }
            val details_sender_name = org.astermail.android.security.trusted_sender_name(
                trust,
                displayed_sender_name(msg.display_sender_name, msg.sender_name),
                stringResource(R.string.unknown),
            )
            val details_sender_email = displayed_sender_email(msg.display_sender_email, msg.sender_email)
            val details_name_hidden = trust != null && trust.name.isEmpty()
            val details_sender = if (
                details_name_hidden || details_sender_name.equals(details_sender_email, ignoreCase = true)
            ) {
                details_sender_email
            } else {
                "$details_sender_name <$details_sender_email>"
            }
            val details_reply_to = remember(msg.raw_headers, details_sender_email) {
                msg.raw_headers
                    .firstOrNull { it.first.equals("reply-to", ignoreCase = true) }
                    ?.second
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.contains(details_sender_email, ignoreCase = true) }
            }
            message_details_panel(
                sender = details_sender,
                forwarded_by = forwarding_address(msg.display_sender_email, msg.sender_email),
                reply_to = details_reply_to,
                is_encrypted = msg.is_e2e_encrypted,
                pgp_encrypted = msg.pgp_encrypted,
                pgp_label = msg.shows_pgp_label,
                pgp_signature = msg.pgp_signature,
                tracking_pixel_count = tracker_report.pixel_count,
                tracking_link_count = tracker_report.cleaned_link_count,
                date_text = msg.timestamp.format_full_datetime(),
                received_on = received_on,
                authentication = auth_summary?.let { summary ->
                    stringResource(
                        R.string.auth_summary_format,
                        auth_result_label(summary.checks[0]),
                        auth_result_label(summary.checks[1]),
                        auth_result_label(summary.checks[2]),
                    )
                },
                authentication_failed = auth_status == SenderAuthStatus.failed,
                on_show_trackers = if (tracker_report.total > 0) ({ show_tracker_details = true }) else null,
                raw_headers = msg.raw_headers,
                show_raw_headers = show_raw_headers,
                to_recipients = msg.to_addresses.ifEmpty {
                    listOfNotNull(msg.to_label.takeIf { it.isNotBlank() })
                },
                cc_recipients = msg.cc_addresses,
                bcc_recipients = msg.bcc_addresses,
            )
        }

        val external_counts = content_analysis.external_counts

        AsterExpandVisibility(visible = msg.send_status == "failed" || msg.send_status == "bounced") {
            send_failure_banner(reason = msg.send_error)
        }

        if (is_pending_send) {
            pending_send_status(
                send_status = msg.send_status,
                send_at_ms = msg.pending_send_at_ms,
                on_retry = on_retry_pending_send,
                modifier = Modifier
                    .padding(horizontal = AsterSpacing.md)
                    .padding(bottom = AsterSpacing.sm)
                    .testTag("pending_send_status"),
            )
        }

        val show_external_banner = external_counts.total > 0 && !allow_external

        AnimatedVisibility(
            visible = show_external_banner,
            enter = aster_expand_in(banner_reduce_motion),
            exit = aster_collapse_out(banner_reduce_motion),
        ) {
            if (blocked_for_traffic) {
                traffic_saver_banner(
                    counts = external_counts,
                    on_load_once = on_load_external,
                    on_disable_traffic_saving = on_disable_low_network,
                )
            } else {
                external_content_banner(
                    counts = external_counts,
                    on_allow_once = if (offer_load_once) on_load_external else null,
                    on_always_allow = if (offer_always_allow) on_always_allow_external else null,
                    on_show_trackers = if (tracker_report.total > 0) ({ show_tracker_details = true }) else null,
                )
            }
        }

        sender_trust_banner(trust)

        val phishing_key = remember(msg.id, msg.body_html, msg.body, msg.sender_email, is_system, auth_status) {
            card_analysis_memo.key_of(msg.id, msg.body_html) * 31L +
                listOf(msg.body, msg.sender_email, is_system, auth_status).hashCode().toLong()
        }
        val phishing_result by produceState(
            initialValue = if (is_system) null else card_analysis_memo.phishing_get(phishing_key),
            phishing_key,
        ) {
            if (is_system) {
                value = null
                return@produceState
            }
            val cached = card_analysis_memo.phishing_get(phishing_key)
            if (cached != null) {
                value = cached
                return@produceState
            }
            value = withContext(kotlinx.coroutines.Dispatchers.Default) {
                org.astermail.android.security.analyze_email(
                    html_content = msg.body_html.orEmpty(),
                    text_content = msg.body,
                    sender_name = msg.sender_name,
                    sender_email = msg.sender_email,
                    is_external = true,
                    spf_result = msg.spf_result,
                    dkim_result = msg.dkim_result,
                    dmarc_result = msg.dmarc_result,
                )
            }
            value?.let { card_analysis_memo.phishing_put(phishing_key, it) }
        }
        val phishing_snapshot = phishing_result
        AnimatedVisibility(
            visible = phishing_snapshot != null && phishing_snapshot.level != org.astermail.android.security.PhishingLevel.safe,
            enter = aster_expand_in(banner_reduce_motion),
            exit = aster_collapse_out(banner_reduce_motion),
        ) {
            if (phishing_snapshot != null) {
                phishing_banner(result = phishing_snapshot)
            }
        }
        AsterExpandVisibility(
            visible = (auth_status == SenderAuthStatus.failed || sender_unauthenticated) &&
                (phishing_snapshot == null || phishing_snapshot.level == org.astermail.android.security.PhishingLevel.safe),
        ) {
            sender_unverified_banner(
                sender = displayed_sender_email(msg.display_sender_email, msg.sender_email),
            )
        }
        AsterExpandVisibility(visible = identity_changed) {
            identity_changed_banner(
                sender = displayed_sender_email(msg.display_sender_email, msg.sender_email),
                on_acknowledge = on_acknowledge_identity,
            )
        }

        val inline_images_seed = remember(msg.id) { inline_images_memo.get(msg.id, msg.body_html.orEmpty()) }
        val inline_images_html = remember(msg.id) { arrayOf(if (inline_images_seed != null) msg.body_html else null) }
        val inline_images by produceState(
            initialValue = inline_images_seed ?: emptyMap(),
            msg.body_html,
            msg.attachments,
            thread_attachments,
        ) {
            val html_now = msg.body_html.orEmpty()
            val resolved = withContext(kotlinx.coroutines.Dispatchers.Default) {
                inline_image_sources(
                    html_now,
                    msg.attachments + thread_attachments,
                )
            }
            if (!keeps_inline_images(value, resolved, inline_images_html[0] == html_now)) value = resolved
            inline_images_html[0] = html_now
            inline_images_memo.put(msg.id, html_now, value)
        }

        val body_settings_state by shared_settings_view_model().state.collectAsStateWithLifecycle()
        val plain_text_mode = org.astermail.android.api.network.should_render_plain_text(
            html_rendering_mode = body_settings_state.preferences?.html_rendering_mode,
            low_network = org.astermail.android.network.low_network_active(),
        )
        val html_part_key = remember(msg.id, msg.body_html, msg.body) {
            unsub_info_memo.key_of(msg.id, msg.body_html, msg.body, null, null, null)
        }
        val html_part_quick = remember(html_part_key) {
            val html = msg.body_html
            when {
                html.isNullOrBlank() -> renderable_part_memo.Resolved(null)
                msg.body.isBlank() || msg.body == html -> renderable_part_memo.Resolved(html)
                else -> renderable_part_memo.get(html_part_key)
            }
        }
        val html_part_state = androidx.compose.runtime.produceState(
            initialValue = html_part_quick?.html ?: msg.body_html,
            html_part_key,
        ) {
            if (html_part_quick != null) {
                value = html_part_quick.html
                return@produceState
            }
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                renderable_html_part(msg.body_html, msg.body)
            }
            renderable_part_memo.put(html_part_key, renderable_part_memo.Resolved(result))
            value = result
        }
        val html_part = html_part_state.value
        val text_part_lazy = remember(msg.body_html, msg.body) {
            lazy(LazyThreadSafetyMode.NONE) {
                msg.body.ifBlank { org.astermail.android.mail.html_to_plain_text(msg.body_html.orEmpty()) }
            }
        }
        val text_part by text_part_lazy
        if (msg.is_body_pending) {
            var body_wait_expired by remember(msg.id, retry_in_progress) { mutableStateOf(false) }
            LaunchedEffect(msg.id, retry_in_progress) {
                kotlinx.coroutines.delay(BODY_PENDING_TIMEOUT_MS)
                if (org.astermail.android.api.network.low_network_state.extend_timeouts()) {
                    kotlinx.coroutines.delay(SLOW_LINK_BODY_PENDING_TIMEOUT_MS - BODY_PENDING_TIMEOUT_MS)
                }
                body_wait_expired = true
            }
            if (body_wait_expired) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AsterSpacing.lg)
                        .testTag("message_body"),
                ) {
                    Text(
                        text = stringResource(R.string.message_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text_muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    org.astermail.android.design.components.AsterDialogPrimaryButton(
                        label = stringResource(R.string.retry),
                        onClick = on_retry_decrypt,
                    )
                }
            } else {
                deferred_body_skeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(placeholder_body_height)
                        .clipToBounds()
                        .testTag("message_body"),
                )
            }
        } else if (!plain_text_mode && html_part != null && !msg.is_undecryptable) {
            email_html_view(
                html = html_part,
                message_key = msg.id,
                allow_external = allow_external,
                inline_images = inline_images,
                access_token = access_token,
                on_ready = on_body_ready,
                on_link_click = on_link_click,
                on_image_click = on_image_click,
                translation_enabled = translation_enabled && !is_pending_send,
                allow_auto_translate = allow_auto_translate &&
                    phishing_snapshot?.level == org.astermail.android.security.PhishingLevel.safe,
                subject = translation_subject,
                on_subject_translated = on_subject_translated,
                search_key = msg.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AsterSpacing.xs, bottom = if (is_last) 0.dp else AsterSpacing.sm)
                    .testTag("message_body"),
            )
        } else if (msg.is_undecryptable) {
            LaunchedEffect(Unit) { on_body_ready() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = AsterSpacing.md)
                    .acrylic(colors, RoundedCornerShape(AsterRadius.island), colors.bg_secondary)
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = TablerIcons.LockOff,
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(
                        if (on_recover_data != null) R.string.decrypt_locked_by_reset_title else R.string.decrypt_failed_title,
                    ),
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        if (on_recover_data != null) R.string.decrypt_locked_by_reset_body else R.string.decrypt_failed_body,
                    ),
                    color = colors.text_muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AsterSpacing.lg))
                if (on_recover_data != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.accent_blue)
                            .clickable(onClick = on_recover_data)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .testTag("recover_data_from_message"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = TablerIcons.LockOpen,
                            contentDescription = null,
                            tint = colors.on_accent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.recover_data_title),
                            color = colors.on_accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(AsterSpacing.sm))
                }
                val retry_is_secondary = on_recover_data != null
                val retry_content_color = if (retry_is_secondary) colors.accent_blue else colors.on_accent
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (retry_is_secondary) {
                                Color.Transparent
                            } else {
                                colors.accent_blue.copy(alpha = if (retry_in_progress) 0.5f else 1f)
                            },
                        )
                        .clickable(enabled = !retry_in_progress, onClick = on_retry_decrypt)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("retry_decrypt"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = TablerIcons.Refresh,
                        contentDescription = null,
                        tint = retry_content_color,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (retry_in_progress) R.string.decrypt_retrying else R.string.retry,
                        ),
                        color = retry_content_color,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (retry_in_progress) {
                        Spacer(Modifier.width(8.dp))
                        org.astermail.android.design.components.aster_spinner(
                            color = retry_content_color,
                            stroke_width = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        } else if (text_part.isBlank()) {
            LaunchedEffect(Unit) { on_body_ready() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = AsterSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_body),
                    color = colors.text_muted,
                    fontSize = 14.sp,
                )
            }
        } else {
            val e2e_no_key_text = stringResource(R.string.e2e_no_key_description)
            val no_body_text = stringResource(R.string.no_body)
            val plain_source = text_part.ifBlank {
                if (msg.is_encrypted) {
                    e2e_no_key_text
                } else {
                    no_body_text
                }
            }
            val plain_sync_html = remember(plain_source) {
                if (plain_source.length <= plain_text_sync_build_chars) {
                    org.astermail.android.mail.build_plain_text_html(plain_source)
                } else {
                    null
                }
            }
            val plain_async_html by produceState(initialValue = "", plain_source, plain_sync_html) {
                if (plain_sync_html != null) return@produceState
                value = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    org.astermail.android.mail.build_plain_text_html(plain_source)
                }
            }
            val plain_html = plain_sync_html ?: plain_async_html
            if (plain_html.isEmpty()) {
                deferred_body_skeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(placeholder_body_height)
                        .clipToBounds()
                        .testTag("message_body"),
                )
            } else email_html_view(
                html = plain_html,
                message_key = msg.id,
                allow_external = false,
                access_token = access_token,
                on_ready = on_body_ready,
                on_link_click = on_link_click,
                on_image_click = on_image_click,
                translation_enabled = translation_enabled && !is_pending_send,
                allow_auto_translate = allow_auto_translate &&
                    phishing_snapshot?.level == org.astermail.android.security.PhishingLevel.safe,
                subject = translation_subject,
                on_subject_translated = on_subject_translated,
                search_key = msg.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AsterSpacing.xs, bottom = if (is_last) 0.dp else AsterSpacing.sm)
                    .testTag("message_body"),
            )
        }

        val visible_attachments = msg.attachments.ifEmpty {
            if (attachments_loading && !attachments_failed) pending_attachments else emptyList()
        }
        if (visible_attachments.isNotEmpty()) {
            attachment_section(
                attachments = visible_attachments,
                on_tap = on_attachment_tap,
                on_download = on_attachment_download,
                on_options = on_attachment_options,
            )
        } else if (attachments_loading && !attachments_failed) {
            attachment_section_placeholder()
        } else if (attachments_failed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AsterSpacing.sm)
                    .testTag("attachments_failed"),
            ) {
                org.astermail.android.ui.settings.detail.load_failed_card(
                    message = null,
                    on_retry = on_retry_attachments,
                )
            }
        }

        if (!is_pending_send) {
            reaction_chip_row(
                reactions = reactions,
                placeholders = reaction_placeholders,
                my_email = my_email,
                on_react = on_react,
                on_unreact = on_unreact,
            )
        }

        Spacer(Modifier.height(AsterSpacing.md))
    }
}

@Composable
private fun reply_action_row(
    on_reply: () -> Unit,
    on_forward: () -> Unit,
    show_react: Boolean = false,
    react_enabled: Boolean = true,
    on_react: () -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val config = LocalConfiguration.current
    var label_size by remember(config, show_react) { mutableStateOf(REPLY_ACTION_LABEL_MAX) }
    val on_label_overflow: () -> Unit = {
        if (label_size.value > REPLY_ACTION_LABEL_MIN.value) {
            label_size = (label_size.value - 1f).sp
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reply_action_button(
            icon = TablerIcons.ArrowBackUp.auto_mirrored(),
            label = stringResource(R.string.reply),
            bg = colors.accent_blue,
            fg = colors.on_accent,
            label_size = label_size,
            on_label_overflow = on_label_overflow,
            on_click = on_reply,
            modifier = Modifier.weight(1f),
        )
        reply_action_button(
            icon = TablerIcons.MailForward,
            label = stringResource(R.string.forward),
            bg = field_surface_color(colors),
            fg = colors.text_primary,
            label_size = label_size,
            on_label_overflow = on_label_overflow,
            on_click = on_forward,
            modifier = Modifier.weight(1f),
        )
        if (show_react) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(SquircleShape(999.dp))
                    .background(if (react_enabled) field_surface_color(colors) else disabled_surface_color(colors))
                    .clickable(onClick = on_react)
                    .testTag("detail_react"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.MoodSmile,
                    contentDescription = stringResource(R.string.add_reaction),
                    tint = if (react_enabled) colors.text_secondary else colors.text_tertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun thread_draft_slot(
    email_id: String,
    thread_token: String?,
    mail_vm: MailViewModel,
) {
    val context = LocalContext.current
    val lifecycle_owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resolved_token by remember(email_id) {
        mutableStateOf(thread_token?.takeIf { it.isNotBlank() } ?: mail_vm.thread_token_for(email_id))
    }
    LaunchedEffect(email_id, thread_token) {
        val incoming = thread_token?.takeIf { it.isNotBlank() }
        if (incoming != null) resolved_token = incoming
    }
    val token = resolved_token
    if (token.isNullOrBlank()) return
    var thread_draft by remember(token) { mutableStateOf<org.astermail.android.mail.InboxItem?>(null) }
    var draft_probe_key by remember(token) { mutableStateOf(0) }
    DisposableEffect(lifecycle_owner, token) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) draft_probe_key++
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(token) {
        mail_vm.draft_changes.collect { draft_probe_key++ }
    }
    LaunchedEffect(token, draft_probe_key) {
        thread_draft = mail_vm.load_thread_draft(token)
    }
    val draft = thread_draft ?: return
    val no_subject = stringResource(R.string.no_subject)
    val summary = draft.subject.takeIf { it.isNotBlank() && it != no_subject } ?: draft.preview
    if (summary.isBlank()) return
    thread_draft_chip(
        summary = summary,
        on_edit = {
            context.startActivity(
                org.astermail.android.ComposeActivity.intent_for(
                    context,
                    mode = "draft",
                    draft_id = draft.id,
                ),
            )
        },
        on_delete = {
            mail_vm.delete_thread_draft(draft.id) { ok ->
                thread_draft = null
                if (!ok) draft_probe_key++
            }
        },
    )
}

@Composable
private fun thread_draft_chip(
    summary: String,
    on_edit: () -> Unit,
    on_delete: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val reduce_motion = aster_reduce_motion()
    var confirm_open by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        kotlinx.coroutines.delay(
            if (reduce_motion) org.astermail.android.design.AsterMotionSpec.reduced_fade_out_ms.toLong() else thread_draft_remove_ms,
        )
        on_delete()
    }

    AnimatedVisibility(
        visible = !removing,
        enter = fadeIn(animationSpec = tween(AsterDuration.instant)),
        exit = aster_collapse_out(reduce_motion),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md)
                .padding(bottom = AsterSpacing.sm)
                .acrylic(colors, SquircleShape(14.dp), colors.bg_secondary)
                .clickable(onClick = on_edit)
                .padding(horizontal = AsterSpacing.sm, vertical = 8.dp)
                .testTag("thread_draft_chip"),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons.Pencil,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.sender_draft),
                style = MaterialTheme.typography.labelMedium,
                color = colors.text_primary,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = colors.text_secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(SquircleShape(999.dp))
                    .clickable { confirm_open = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("thread_draft_delete"),
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accent_blue,
                )
            }
        }
    }

    if (confirm_open) {
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { confirm_open = false },
            title = stringResource(R.string.delete_draft_question),
            message = stringResource(R.string.delete_draft_confirm_description),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                confirm_open = false
                removing = true
            },
        )
    }
}

@Composable
private fun reaction_quick_picker(
    visible: Boolean,
    skin_tone: String,
    on_skin_tone: (String) -> Unit,
    on_pick: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val picker_reduce_motion = aster_reduce_motion()
    var picker_sheet_open by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = visible,
        enter = if (picker_reduce_motion) {
            fadeIn(animationSpec = tween(AsterDuration.instant))
        } else {
            fadeIn(
                animationSpec = tween(AsterDuration.menu_fade_enter, easing = AsterEasing.menu_enter),
            ) + expandVertically(
                animationSpec = tween(AsterDuration.menu_enter, easing = AsterEasing.menu_enter),
            )
        },
        exit = if (picker_reduce_motion) {
            fadeOut(animationSpec = tween(AsterDuration.instant))
        } else {
            fadeOut(
                animationSpec = tween(AsterDuration.menu_fade_exit, easing = AsterEasing.menu_exit),
            ) + shrinkVertically(
                animationSpec = tween(AsterDuration.menu_exit, easing = AsterEasing.menu_exit),
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            quick_reaction_emoji.forEach { base ->
                val emoji = remember(base, skin_tone) {
                    org.astermail.android.api.preferences.apply_emoji_skin_tone(base, skin_tone)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(SquircleShape(999.dp))
                        .background(colors.bg_tertiary)
                        .clickable { on_pick(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 18.sp)
                }
            }
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .clip(SquircleShape(999.dp))
                    .background(colors.bg_tertiary)
                    .clickable { picker_sheet_open = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Plus,
                    contentDescription = stringResource(R.string.add_reaction),
                    tint = colors.text_secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (picker_sheet_open) {
        reaction_picker_sheet(
            on_close = { picker_sheet_open = false },
            on_pick = { emoji ->
                picker_sheet_open = false
                on_pick(emoji)
            },
            skin_tone = skin_tone,
            on_skin_tone = on_skin_tone,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
private fun reaction_chip_row(
    reactions: List<DecryptedReaction>,
    placeholders: Int,
    my_email: String,
    on_react: (String) -> Unit,
    on_unreact: (String) -> Unit,
) {
    if (reactions.isEmpty() && placeholders <= 0) return
    val colors = AsterMaterial.colors
    val reduce_motion = aster_reduce_motion()
    var info_emoji by remember { mutableStateOf<String?>(null) }
    val chip_palette = remember(colors) {
        reaction_chip_palette(
            is_dark = colors.is_dark,
            accent = colors.accent_blue,
            surface = colors.bg_card,
            text_secondary = colors.text_secondary,
        )
    }
    val groups = remember(reactions, my_email) {
        group_reaction_chips(reactions, my_email)
    }
    val skeleton_count = if (reactions.isEmpty()) placeholders.coerceIn(1, 3) else 0
    val came_from_skeleton = remember { booleanArrayOf(false) }
    if (skeleton_count > 0) came_from_skeleton[0] = true
    val initial_chip_emojis = remember { groups.map { (emoji, _, _) -> emoji }.toSet() }
    val skeleton_shimmer = if (skeleton_count > 0) org.astermail.android.design.components.shimmer_state() else null
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .padding(top = AsterSpacing.sm, bottom = AsterSpacing.xs)
            .testTag("reaction_chip_row"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (skeleton_shimmer != null) {
            repeat(skeleton_count) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .width(52.dp)
                        .shimmer(skeleton_shimmer, SquircleShape(999.dp)),
                )
            }
        }
        groups.forEach { (emoji, count, mine) ->
            androidx.compose.runtime.key(emoji) {
                val appear = remember {
                    MutableTransitionState(emoji in initial_chip_emojis || came_from_skeleton[0]).apply { targetState = true }
                }
                val bg by org.astermail.android.design.aster_animate_color(
                    target = if (mine) chip_palette.own_fill else chip_palette.other_fill,
                    label = "reaction_chip_bg",
                )
                AnimatedVisibility(
                    visibleState = appear,
                    enter = org.astermail.android.design.aster_pop_in(reduce_motion),
                ) {
                    val shape = SquircleShape(999.dp)
                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(shape)
                            .background(bg)
                            .combinedClickable(
                                onClick = { if (mine) info_emoji = emoji else on_react(emoji) },
                                onLongClick = { info_emoji = emoji },
                            )
                            .padding(start = 8.dp, end = 11.dp)
                            .testTag("reaction_chip"),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = emoji, fontSize = 16.sp)
                        androidx.compose.animation.AnimatedContent(
                            targetState = count,
                            transitionSpec = {
                                if (reduce_motion) {
                                    org.astermail.android.design.aster_crossfade(true)
                                } else {
                                    (
                                        slideInVertically(tween(org.astermail.android.design.AsterMotionSpec.icon_ms, easing = AsterEasing.emphasized_decelerate)) { it / 2 } +
                                            fadeIn(tween(org.astermail.android.design.AsterMotionSpec.icon_ms, easing = AsterEasing.emphasized_decelerate))
                                        ) togetherWith
                                        (
                                            slideOutVertically(tween(org.astermail.android.design.AsterMotionSpec.fade_exit_ms, easing = AsterEasing.emphasized_accelerate)) { -it / 2 } +
                                                fadeOut(tween(org.astermail.android.design.AsterMotionSpec.fade_exit_ms, easing = AsterEasing.emphasized_accelerate))
                                            )
                                }
                            },
                            label = "reaction_chip_count",
                        ) { value ->
                            Text(
                                text = value.toString(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (mine) chip_palette.own_text else chip_palette.other_text,
                            )
                        }
                    }
                }
            }
        }
    }

    info_emoji?.let { emoji ->
        val reactors = reactions.filter { it.emoji == emoji }
        val you_label = stringResource(R.string.reaction_info_you, emoji)
        val others = reactors
            .filterNot { it.is_own || it.reactor_email.equals(my_email, ignoreCase = true) }
            .map { it.reactor_email }
            .distinct()
        val mine = reactors.any { it.is_own || it.reactor_email.equals(my_email, ignoreCase = true) }
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { info_emoji = null },
            title = stringResource(R.string.reaction_info_title),
            confirm_label = stringResource(R.string.ok),
            on_confirm = { info_emoji = null },
            extra_content = {
                Column(modifier = Modifier.fillMaxWidth().testTag("reaction_info_dialog")) {
                    if (mine) {
                        Text(text = you_label, color = colors.text_primary, fontSize = 15.sp)
                        Spacer(Modifier.height(AsterSpacing.xs))
                    }
                    others.forEach { email ->
                        Text(
                            text = stringResource(R.string.reaction_info_other, email, emoji),
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(AsterSpacing.xs))
                    }
                }
            },
        )
    }
}

private val REPLY_ACTION_LABEL_MAX = 14.sp
private val REPLY_ACTION_LABEL_MIN = 9.sp

@Composable
internal fun reply_action_button(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    label_size: TextUnit,
    on_label_overflow: () -> Unit,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(SquircleShape(999.dp))
            .background(bg)
            .clickable(onClick = on_click)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = fg,
            fontSize = label_size,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            onTextLayout = { result -> if (result.hasVisualOverflow) on_label_overflow() },
        )
    }
}

@Composable
internal fun compact_banner_action(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Text(
        text = label,
        color = if (primary) colors.on_accent else colors.accent_blue,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(AsterShapes.control)
            .background(if (primary) colors.accent_blue else tonal_surface_color(colors, colors.accent_blue))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
internal fun compact_banner_menu_action(
    content_description: String,
    items: @Composable androidx.compose.foundation.layout.ColumnScope.(close: () -> Unit) -> Unit,
) {
    val colors = AsterMaterial.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(AsterShapes.control)
                .background(tonal_surface_color(colors, colors.accent_blue))
                .clickable(role = Role.Button, onClick = { expanded = true })
                .semantics { contentDescription = content_description }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("compact_banner_more"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(16.dp),
            )
        }
        aster_menu(expanded = expanded, on_dismiss = { expanded = false }) {
            items { expanded = false }
        }
    }
}

@Composable
private fun compact_banner_icon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    on_icon_click: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.size(19.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .then(
                    if (on_icon_click != null) {
                        Modifier
                            .clip(SquircleShape(6.dp))
                            .clickable(onClick = on_icon_click)
                            .padding(2.dp)
                    } else {
                        Modifier
                    },
                )
                .size(15.dp),
        )
    }
}

private val compact_summary_policy = object : androidx.compose.ui.layout.MeasurePolicy {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurables: List<androidx.compose.ui.layout.Measurable>,
        constraints: androidx.compose.ui.unit.Constraints,
    ): androidx.compose.ui.layout.MeasureResult {
        val (first_icon, first_text, separator, second_icon, second_text) = measurables
        val icon_gap = 8.dp.roundToPx()
        val loose = androidx.compose.ui.unit.Constraints()
        val first_icon_placeable = first_icon.measure(loose)
        val second_icon_placeable = second_icon.measure(loose)
        val text_start = first_icon_placeable.width + icon_gap
        val one_line_width = text_start +
            first_text.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) +
            separator.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) +
            second_text.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
        if (one_line_width <= constraints.maxWidth) {
            val first = first_text.measure(loose)
            val sep = separator.measure(loose)
            val second = second_text.measure(loose)
            val height = maxOf(first_icon_placeable.height, first.height, sep.height, second.height)
            return layout(constraints.constrainWidth(one_line_width), height) {
                first_icon_placeable.placeRelative(0, (height - first_icon_placeable.height) / 2)
                first.placeRelative(text_start, (height - first.height) / 2)
                sep.placeRelative(text_start + first.width, (height - sep.height) / 2)
                second.placeRelative(text_start + first.width + sep.width, (height - second.height) / 2)
            }
        }
        val text_max = maxOf(0, constraints.maxWidth - text_start)
        val first = first_text.measure(androidx.compose.ui.unit.Constraints(maxWidth = text_max))
        val second = second_text.measure(androidx.compose.ui.unit.Constraints(maxWidth = text_max))
        val first_row = maxOf(first_icon_placeable.height, first.height)
        val second_row = maxOf(second_icon_placeable.height, second.height)
        val width = constraints.constrainWidth(text_start + maxOf(first.width, second.width))
        return layout(width, first_row + second_row) {
            first_icon_placeable.placeRelative(0, (first_row - first_icon_placeable.height) / 2)
            first.placeRelative(text_start, (first_row - first.height) / 2)
            second_icon_placeable.placeRelative(0, first_row + (second_row - second_icon_placeable.height) / 2)
            second.placeRelative(text_start, first_row + (second_row - second.height) / 2)
        }
    }

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
        height: Int,
    ): Int {
        val (first_icon, first_text, separator, _, second_text) = measurables
        return first_icon.maxIntrinsicWidth(height) + 8.dp.roundToPx() +
            first_text.maxIntrinsicWidth(height) + separator.maxIntrinsicWidth(height) + second_text.maxIntrinsicWidth(height)
    }

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
        height: Int,
    ): Int {
        val (first_icon, first_text, _, _, second_text) = measurables
        return first_icon.maxIntrinsicWidth(height) + 8.dp.roundToPx() +
            maxOf(first_text.maxIntrinsicWidth(height), second_text.maxIntrinsicWidth(height))
    }
}

@Composable
internal fun compact_banner_summary(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    icon_tint: Color,
    on_icon_click: (() -> Unit)?,
    label: String,
    second_icon: androidx.compose.ui.graphics.vector.ImageVector,
    second_icon_tint: Color,
    second_label: String,
    second_label_color: Color,
    second_label_modifier: Modifier,
) {
    val colors = AsterMaterial.colors
    Layout(
        content = {
            compact_banner_icon(icon = icon, tint = icon_tint, on_icon_click = on_icon_click)
            Text(
                text = label,
                color = colors.text_secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("compact_banner_label"),
            )
            Text(
                text = " · ",
                color = colors.text_secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
            compact_banner_icon(icon = second_icon, tint = second_icon_tint)
            Text(
                text = second_label,
                color = second_label_color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = second_label_modifier,
            )
        },
        measurePolicy = compact_summary_policy,
    )
}

internal fun compact_banner_summary_inline(
    available_width: Int,
    gap: Int,
    actions_width: Int,
    one_line_width: Int,
    two_row_width: Int,
): Boolean = minOf(one_line_width, two_row_width) + gap + actions_width <= available_width

@Composable
internal fun compact_banner_row(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    on_icon_click: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        compact_banner_icon(icon = icon, tint = tint, on_icon_click = on_icon_click)
        Spacer(Modifier.width(8.dp))
        content()
    }
}

internal fun compact_banner_actions_inline(
    available_width: Int,
    gap: Int,
    actions_width: Int,
    text_single_line_width: Int,
    min_text_width: Int,
    max_text_lines: Int,
    text_may_wrap: Boolean,
    text_lines_at: (Int) -> Int,
): Boolean {
    if (text_single_line_width + gap + actions_width <= available_width) return true
    if (!text_may_wrap) return false
    val text_width = available_width - gap - actions_width
    if (text_width < min_text_width) return false
    return text_lines_at(text_width) <= max_text_lines
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun compact_banner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on_icon_click: (() -> Unit)? = null,
    secondary_row: (@Composable () -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    val label_style = LocalTextStyle.current.merge(
        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
    val text_measurer = rememberTextMeasurer()
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = 3.dp)
            .acrylic(colors, SquircleShape(10.dp), colors.bg_secondary)
            .padding(start = AsterSpacing.md, end = AsterSpacing.sm),
        content = {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                if (summary != null) {
                    summary()
                } else if (label.isNotEmpty() || secondary_row == null) {
                    compact_banner_row(
                        icon = icon,
                        tint = if (on_icon_click != null) colors.accent_blue else colors.text_secondary,
                        on_icon_click = on_icon_click,
                    ) {
                        Text(
                            text = label,
                            color = colors.text_secondary,
                            style = label_style,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("compact_banner_label"),
                        )
                    }
                }
                if (summary == null) secondary_row?.invoke()
            }
            FlowRow(
                modifier = Modifier.testTag("compact_banner_actions"),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                actions()
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 6.dp.roundToPx()
        val text_measurable = measurables[0]
        val actions_measurable = measurables[1]
        val actions_width = actions_measurable.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
        val text_width = text_measurable.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
        val icon_column = 27.dp.roundToPx()
        val inline = if (summary != null) compact_banner_summary_inline(
            available_width = width,
            gap = gap,
            actions_width = actions_width,
            one_line_width = text_width,
            two_row_width = text_measurable.minIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity),
        ) else compact_banner_actions_inline(
            available_width = width,
            gap = gap,
            actions_width = actions_width,
            text_single_line_width = text_width,
            min_text_width = icon_column + 120.dp.roundToPx(),
            max_text_lines = 3,
            text_may_wrap = secondary_row == null && label.isNotEmpty(),
        ) { slot ->
            text_measurer.measure(
                text = label,
                style = label_style,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxOf(0, slot - icon_column)),
            ).lineCount
        }
        if (inline) {
            val actions_placeable = actions_measurable.measure(
                androidx.compose.ui.unit.Constraints(maxWidth = actions_width),
            )
            val text_slot = width - gap - actions_placeable.width
            val text_placeable = text_measurable.measure(
                androidx.compose.ui.unit.Constraints(minWidth = text_slot, maxWidth = text_slot),
            )
            val height = maxOf(text_placeable.height, actions_placeable.height)
            layout(width, height) {
                text_placeable.placeRelative(0, (height - text_placeable.height) / 2)
                actions_placeable.placeRelative(width - actions_placeable.width, (height - actions_placeable.height) / 2)
            }
        } else {
            val text_placeable = text_measurable.measure(
                androidx.compose.ui.unit.Constraints(minWidth = width, maxWidth = width),
            )
            val actions_placeable = actions_measurable.measure(
                androidx.compose.ui.unit.Constraints(maxWidth = width),
            )
            layout(width, text_placeable.height + actions_placeable.height) {
                text_placeable.placeRelative(0, 0)
                actions_placeable.placeRelative(width - actions_placeable.width, text_placeable.height)
            }
        }
    }
}

internal fun unsubscribe_link_visible(show_unsub: Boolean, info: UnsubscribeInfo): Boolean =
    show_unsub && info.has_unsubscribe

@Composable
internal fun unsubscribe_link(
    on_unsubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val label = stringResource(R.string.unsubscribe)
    Text(
        text = label,
        color = colors.accent_blue,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(SquircleShape(6.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = on_unsubscribe)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .semantics { contentDescription = label }
            .testTag("unsubscribe_link"),
    )
}

@Composable
private fun blocked_content_details_dialog(
    counts: ExternalContentCounts,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_close,
        title = stringResource(R.string.blocked_content_details_title),
        message = null,
        body = ({
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .vertical_scroll_with_indicator(),
            ) {
                Text(
                    text = stringResource(R.string.blocked_content_details_hint),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                counts.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (item.type) {
                                ExternalContentType.image -> stringResource(R.string.blocked_type_image)
                                ExternalContentType.tracker -> stringResource(R.string.blocked_type_tracker)
                                ExternalContentType.font -> stringResource(R.string.blocked_type_font)
                                ExternalContentType.stylesheet -> stringResource(R.string.blocked_type_stylesheet)
                            },
                            color = colors.text_muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(SquircleShape(4.dp))
                                .background(colors.bg_tertiary)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = external_display_url(item.url),
                            color = colors.text_secondary,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }),
        footer = {
            org.astermail.android.design.components.AsterDialogPrimaryButton(
                label = stringResource(R.string.close),
                onClick = on_close,
            )
        },
    )
}

@Composable
private fun tracker_details_section_label(text: String) {
    Text(
        text = text,
        color = AsterMaterial.colors.text_muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
    )
}

@Composable
internal fun tracker_details_dialog(
    report: EmailHtmlSanitizer.TrackerReport,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_close,
        title = stringResource(R.string.tracker_protection),
        message = null,
        body = ({
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .vertical_scroll_with_indicator(),
            ) {
                Text(
                    text = stringResource(R.string.tracker_details_hint),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
                if (report.pixel_domains.isNotEmpty()) {
                    Spacer(Modifier.height(AsterSpacing.md))
                    tracker_details_section_label(stringResource(R.string.spy_pixels_blocked))
                    Spacer(Modifier.height(4.dp))
                    report.pixel_domains.forEach { (domain, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = domain,
                                color = colors.text_secondary,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (count > 1) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "x$count",
                                    color = colors.text_muted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
                if (report.param_counts.isNotEmpty()) {
                    Spacer(Modifier.height(AsterSpacing.md))
                    tracker_details_section_label(stringResource(R.string.links_cleaned))
                    Spacer(Modifier.height(4.dp))
                    report.param_counts.forEach { (param, count) ->
                        Text(
                            text = pluralStringResource(R.plurals.param_removed_from_links, count, param, count),
                            color = colors.text_secondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }),
        footer = {
            org.astermail.android.design.components.AsterDialogPrimaryButton(
                label = stringResource(R.string.close),
                onClick = on_close,
            )
        },
    )
}

@Composable
internal fun external_content_banner(
    counts: ExternalContentCounts,
    on_allow_once: (() -> Unit)?,
    on_always_allow: (() -> Unit)?,
    on_show_trackers: (() -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    val summary_parts = mutableListOf<String>()
    if (counts.image_count > 0) {
        val n = counts.image_count
        summary_parts.add(pluralStringResource(R.plurals.n_images, n, n))
    }
    if (counts.font_count > 0) {
        val n = counts.font_count
        summary_parts.add(pluralStringResource(R.plurals.n_fonts, n, n))
    }
    if (counts.css_count > 0) {
        val n = counts.css_count
        summary_parts.add(pluralStringResource(R.plurals.n_stylesheets, n, n))
    }
    val label = when {
        summary_parts.isNotEmpty() -> summary_parts.joinToString(", ")
        counts.tracker_count > 0 -> ""
        else -> stringResource(R.string.detail_external_images_blocked)
    }
    var show_details by remember { mutableStateOf(false) }
    if (show_details) {
        blocked_content_details_dialog(counts = counts, on_close = { show_details = false })
    }
    val open_details: (() -> Unit)? = if (counts.items.isNotEmpty()) ({ show_details = true }) else null
    val open_trackers = on_show_trackers ?: open_details
    val tracker_label = pluralStringResource(R.plurals.n_tracking_pixels, counts.tracker_count, counts.tracker_count)
    val tracker_link_modifier = Modifier
        .clip(SquircleShape(6.dp))
        .then(
            if (open_trackers != null) {
                Modifier.clickable(role = Role.Button, onClick = open_trackers)
            } else {
                Modifier
            },
        )
        .testTag("banner_trackers")
        .padding(vertical = 2.dp)
    val tracker_color = if (open_trackers != null) colors.accent_blue else colors.text_secondary
    compact_banner(
        icon = TablerIcons.PhotoOff,
        label = label,
        on_icon_click = open_details,
        summary = if (counts.tracker_count > 0 && label.isNotEmpty()) ({
            compact_banner_summary(
                icon = TablerIcons.PhotoOff,
                icon_tint = if (open_details != null) colors.accent_blue else colors.text_secondary,
                on_icon_click = open_details,
                label = label,
                second_icon = TablerIcons.ShieldCheck,
                second_icon_tint = colors.success,
                second_label = tracker_label,
                second_label_color = tracker_color,
                second_label_modifier = tracker_link_modifier,
            )
        }) else null,
        secondary_row = if (counts.tracker_count > 0) ({
            compact_banner_row(
                icon = TablerIcons.ShieldCheck,
                tint = colors.success,
                modifier = tracker_link_modifier,
            ) {
                Text(
                    text = tracker_label,
                    color = tracker_color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }) else null,
    ) {
        if (on_allow_once != null) {
            compact_banner_action(
                label = stringResource(R.string.detail_external_load),
                primary = true,
                onClick = on_allow_once,
            )
        }
        if (on_always_allow != null) {
            compact_banner_menu_action(
                content_description = stringResource(R.string.more_options),
            ) { close ->
                aster_menu_item(
                    label = stringResource(R.string.detail_external_always_allow),
                    on_click = {
                        close()
                        on_always_allow()
                    },
                    test_tag = "compact_banner_always_allow",
                )
            }
        }
    }
}

@Composable
internal fun traffic_saver_banner(
    counts: ExternalContentCounts,
    on_load_once: () -> Unit,
    on_disable_traffic_saving: () -> Unit,
) {
    val summary_parts = mutableListOf<String>()
    if (counts.image_count > 0) {
        val n = counts.image_count
        summary_parts.add(pluralStringResource(R.plurals.n_images, n, n))
    }
    if (counts.font_count > 0) {
        val n = counts.font_count
        summary_parts.add(pluralStringResource(R.plurals.n_fonts, n, n))
    }
    val label = if (summary_parts.isNotEmpty()) summary_parts.joinToString(", ")
        else stringResource(R.string.detail_external_images_traffic_blocked)
    compact_banner(icon = TablerIcons.PhotoOff, label = label) {
        compact_banner_action(
            label = stringResource(R.string.detail_external_allow_once),
            primary = false,
            onClick = on_load_once,
        )
        compact_banner_action(
            label = stringResource(R.string.detail_disable_traffic_saving),
            primary = true,
            onClick = on_disable_traffic_saving,
        )
    }
}

@Composable
private fun raw_source_dialog(
    message: ThreadMessage?,
    subject: String,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val headers = remember(message) {
        if (message == null) return@remember ""
        buildString {
            append("From: ").append(message.sender_name).append(" <").append(message.sender_email).append(">\n")
            append("To: ").append(message.to_label).append("\n")
            if (subject.isNotBlank()) append("Subject: ").append(subject).append("\n")
            append("Date: ").append(java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US).format(java.util.Date(message.timestamp))).append("\n")
            append("Message-Id: ").append(message.id).append("\n")
            append("X-Encrypted: ").append(if (message.is_e2e_encrypted) "end-to-end" else "in-transit").append("\n")
            if (message.trackers_blocked > 0) append("X-Aster-Trackers-Blocked: ").append(message.trackers_blocked).append("\n")
        }
    }
    val body_text = remember(message) {
        message?.body_html?.takeIf { it.isNotBlank() } ?: message?.body.orEmpty()
    }
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_close,
        title = stringResource(R.string.detail_raw_source_title),
        message = if (message == null) stringResource(R.string.detail_raw_source_unavailable) else null,
        body = if (message == null) null else ({
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .vertical_scroll_with_indicator(),
            ) {
                Text(
                    text = stringResource(R.string.detail_raw_source_headers),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = headers,
                    color = colors.text_primary,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.detail_raw_source_body),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body_text,
                    color = colors.text_primary,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }),
        footer = {
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.detail_raw_source_close),
                onClick = on_close,
            )
            if (message != null) {
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.detail_raw_source_copy),
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("raw_source", headers + "\n" + body_text)
                        clip.description.extras = android.os.PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                        if (write_to_clipboard(context, clip)) {
                            app_toast.show(context.getString(R.string.detail_raw_source_copied))
                        } else {
                            show_copy_failed_toast(context)
                        }
                    },
                )
            }
        },
    )
}

@Composable
private fun message_detail_row(label: String, value: String) {
    val colors = AsterMaterial.colors
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            color = colors.text_muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = colors.text_primary,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun message_details_dialog(
    message: ThreadMessage?,
    subject: String,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val headers_text = remember(message) {
        message?.raw_headers?.takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { "${it.first}: ${it.second}" }
            .orEmpty()
    }
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_close,
        title = stringResource(R.string.message_details),
        message = if (message == null) stringResource(R.string.detail_raw_source_unavailable) else null,
        body = if (message == null) null else ({
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .vertical_scroll_with_indicator(),
            ) {
                val from_email = displayed_sender_email(message.display_sender_email, message.sender_email)
                val from_name = org.astermail.android.security.trusted_sender_name(
                    message.trust_message,
                    displayed_sender_name(message.display_sender_name, message.sender_name),
                    stringResource(R.string.unknown),
                )
                message_detail_row(
                    stringResource(R.string.from_label),
                    if (from_name.equals(from_email, ignoreCase = true)) from_email else "$from_name <$from_email>",
                )
                forwarding_address(message.display_sender_email, message.sender_email)?.let {
                    message_detail_row(stringResource(R.string.forwarded_by_label), it)
                }
                if (message.to_label.isNotBlank()) {
                    message_detail_row(stringResource(R.string.to_label), message.to_label)
                }
                if (message.cc_addresses.isNotEmpty()) {
                    message_detail_row(stringResource(R.string.cc), message.cc_addresses.joinToString(", "))
                }
                if (message.bcc_addresses.isNotEmpty()) {
                    message_detail_row(stringResource(R.string.bcc), message.bcc_addresses.joinToString(", "))
                }
                resolve_received_on_address(message.raw_headers, message.to_addresses + message.cc_addresses, message.sender_email)?.let {
                    message_detail_row(stringResource(R.string.received_on_label), it)
                }
                message_detail_row(
                    stringResource(R.string.date),
                    java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US)
                        .format(java.util.Date(message.timestamp)),
                )
                if (subject.isNotBlank()) {
                    message_detail_row(stringResource(R.string.subject_label), subject)
                }
                message_detail_row(
                    stringResource(R.string.message_id_label),
                    "<${message.id}@astermail.org>",
                )
                message_detail_row(
                    stringResource(R.string.encryption),
                    if (message.is_e2e_encrypted && message.shows_pgp_label) {
                        stringResource(R.string.encrypted_pgp)
                    } else if (message.is_e2e_encrypted) {
                        stringResource(R.string.encrypted_e2e)
                    } else {
                        stringResource(R.string.encrypted_in_transit)
                    },
                )
                if (message.pgp_encrypted) {
                    message_detail_row(
                        stringResource(R.string.pgp_signature_label),
                        pgp_signature_label(message.pgp_signature),
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.message_headers),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = headers_text.ifBlank { stringResource(R.string.no_raw_headers) },
                    color = colors.text_primary,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }),
        footer = {
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.detail_raw_source_close),
                onClick = on_close,
            )
            if (message != null && headers_text.isNotBlank()) {
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.copy_headers),
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("headers", headers_text)
                        clip.description.extras = android.os.PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                        if (write_to_clipboard(context, clip)) {
                            app_toast.show(context.getString(R.string.headers_copied))
                        } else {
                            show_copy_failed_toast(context)
                        }
                    },
                )
            }
        },
    )
}

@Composable
internal fun message_details_panel(
    sender: String,
    reply_to: String?,
    date_text: String,
    is_encrypted: Boolean,
    tracking_pixel_count: Int,
    tracking_link_count: Int,
    received_on: String?,
    authentication: String?,
    authentication_failed: Boolean,
    on_show_trackers: (() -> Unit)?,
    raw_headers: List<Pair<String, String>> = emptyList(),
    show_raw_headers: Boolean = false,
    pgp_encrypted: Boolean = false,
    pgp_label: Boolean = pgp_encrypted,
    pgp_signature: org.astermail.android.crypto.PgpSignatureStatus =
        org.astermail.android.crypto.PgpSignatureStatus.NONE,
    to_recipients: List<String> = emptyList(),
    cc_recipients: List<String> = emptyList(),
    bcc_recipients: List<String> = emptyList(),
    forwarded_by: String? = null,
) {
    val colors = AsterMaterial.colors
    val panel_context = LocalContext.current
    val panel_haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val panel_haptics_enabled = org.astermail.android.ui.theme.local_accessibility.current.haptic_enabled
    val copy_address = { value: String ->
        val address = copyable_email_address(value)
        if (panel_haptics_enabled) {
            panel_haptics.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
            )
        }
        val copied = write_to_clipboard(
            panel_context,
            android.content.ClipData.newPlainText("email_address", address),
        )
        show_copy_result_toast(panel_context, address, copied)
    }
    var show_security by remember { mutableStateOf(false) }
    val raw_headers_text = remember(raw_headers) {
        raw_headers.joinToString("\n") { "${it.first}: ${it.second}" }
    }
    val encryption_value = if (is_encrypted && pgp_label) {
        stringResource(R.string.encrypted_pgp)
    } else if (is_encrypted) {
        stringResource(R.string.encrypted_e2e)
    } else {
        stringResource(R.string.encrypted_in_transit)
    }
    val encryption_tint = if (is_encrypted) colors.accent_blue else colors.text_muted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .padding(bottom = AsterSpacing.sm)
            .acrylic(colors, SquircleShape(14.dp), colors.bg_secondary)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
    ) {
        detail_meta_row(
            label = stringResource(R.string.from),
            value = sender,
            on_long_click = { copy_address(sender) },
        )
        if (forwarded_by != null) {
            detail_meta_row(
                label = stringResource(R.string.forwarded_by_label),
                value = forwarded_by,
                on_long_click = { copy_address(forwarded_by) },
            )
        }
        if (reply_to != null) {
            detail_meta_row(
                label = stringResource(R.string.reply_to_label),
                value = reply_to,
                on_long_click = { copy_address(reply_to) },
            )
        }
        listOf(
            Triple("to", R.string.to, to_recipients),
            Triple("cc", R.string.cc, cc_recipients),
            Triple("bcc", R.string.bcc, bcc_recipients),
        ).forEach { (field, label_res, recipients) ->
            if (recipients.isNotEmpty()) {
                val joined = recipients.joinToString(", ")
                detail_meta_row(
                    label = stringResource(label_res),
                    value = joined,
                    on_long_click = { copy_address(joined) },
                    modifier = Modifier.testTag("details_$field"),
                    value_max_height = 180.dp,
                )
            }
        }
        detail_meta_row(label = stringResource(R.string.date), value = date_text)
        detail_meta_row(
            label = stringResource(R.string.encryption),
            value = encryption_value,
            icon = TablerIcons.Lock,
            value_tint = encryption_tint,
        )
        if (pgp_encrypted) {
            detail_meta_row(
                label = stringResource(R.string.pgp_signature_label),
                value = pgp_signature_label(pgp_signature),
                icon = TablerIcons.ShieldLock,
                value_tint = pgp_signature_tint(pgp_signature),
                modifier = Modifier.testTag("pgp_signature_row"),
            )
        }
        Text(
            text = stringResource(R.string.view_security_details),
            color = colors.accent_blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(top = 2.dp)
                .clip(SquircleShape(8.dp))
                .clickable { show_security = true }
                .padding(vertical = 5.dp),
        )
        if (show_raw_headers) {
            Spacer(Modifier.height(AsterSpacing.sm))
            Text(
                text = stringResource(R.string.message_headers),
                color = colors.text_muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = raw_headers_text.ifBlank { stringResource(R.string.no_raw_headers) },
                color = colors.text_primary,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .vertical_scroll_with_indicator(),
            )
        }
    }
    if (show_security) {
        security_details_dialog(
            is_encrypted = is_encrypted,
            pgp_encrypted = pgp_encrypted,
            pgp_label = pgp_label,
            pgp_signature = pgp_signature,
            tracking_pixel_count = tracking_pixel_count,
            tracking_link_count = tracking_link_count,
            received_on = received_on,
            authentication = authentication,
            authentication_failed = authentication_failed,
            on_show_trackers = on_show_trackers,
            on_close = { show_security = false },
        )
    }
}

@Composable
private fun security_details_dialog(
    is_encrypted: Boolean,
    pgp_encrypted: Boolean,
    pgp_label: Boolean,
    pgp_signature: org.astermail.android.crypto.PgpSignatureStatus,
    tracking_pixel_count: Int,
    tracking_link_count: Int,
    received_on: String?,
    authentication: String?,
    authentication_failed: Boolean,
    on_show_trackers: (() -> Unit)?,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val tracker_count = tracking_pixel_count + tracking_link_count
    val tracker_breakdown = listOfNotNull(
        if (tracking_pixel_count > 0) {
            pluralStringResource(R.plurals.n_tracking_pixels, tracking_pixel_count, tracking_pixel_count)
        } else {
            null
        },
        if (tracking_link_count > 0) {
            pluralStringResource(R.plurals.n_tracking_links, tracking_link_count, tracking_link_count)
        } else {
            null
        },
    ).joinToString(" · ")
    val dialog_context = LocalContext.current
    val dialog_haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val dialog_haptics_enabled = org.astermail.android.ui.theme.local_accessibility.current.haptic_enabled
    val copy_dialog_address = { value: String ->
        val address = copyable_email_address(value)
        if (dialog_haptics_enabled) {
            dialog_haptics.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
            )
        }
        val copied = write_to_clipboard(
            dialog_context,
            android.content.ClipData.newPlainText("email_address", address),
        )
        show_copy_result_toast(dialog_context, address, copied)
    }
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_close,
        title = stringResource(R.string.security_details_title),
        message = null,
        body = ({
            Column(modifier = Modifier.fillMaxWidth()) {
                detail_meta_row(
                    label = stringResource(R.string.encryption),
                    value = if (is_encrypted && pgp_label) {
                        stringResource(R.string.encrypted_pgp)
                    } else if (is_encrypted) {
                        stringResource(R.string.encrypted_e2e)
                    } else {
                        stringResource(R.string.encrypted_in_transit)
                    },
                    icon = TablerIcons.Lock,
                    value_tint = if (is_encrypted) colors.accent_blue else colors.text_muted,
                )
                if (pgp_encrypted) {
                    detail_meta_row(
                        label = stringResource(R.string.pgp_signature_label),
                        value = pgp_signature_label(pgp_signature),
                        icon = TablerIcons.ShieldLock,
                        value_tint = pgp_signature_tint(pgp_signature),
                    )
                }
                detail_meta_row(
                    label = stringResource(R.string.tracker_protection),
                    value = if (tracker_count > 0) {
                        pluralStringResource(R.plurals.trackers_blocked_count, tracker_count, tracker_count) +
                            "\n" + tracker_breakdown
                    } else {
                        stringResource(R.string.no_trackers)
                    },
                    icon = TablerIcons.ShieldLock,
                    value_tint = if (tracker_count > 0) colors.warning else colors.success,
                    modifier = Modifier.testTag("tracker_badge"),
                    on_click = if (on_show_trackers != null) ({
                        on_close()
                        on_show_trackers()
                    }) else null,
                )
                if (received_on != null) {
                    detail_meta_row(
                        label = stringResource(R.string.received_on_label),
                        value = received_on,
                        on_long_click = { copy_dialog_address(received_on) },
                    )
                }
                if (authentication != null) {
                    detail_meta_row(
                        label = stringResource(R.string.sender_authentication),
                        value = authentication,
                        value_tint = if (authentication_failed) colors.danger else null,
                    )
                }
            }
        }),
        footer = {
            org.astermail.android.design.components.AsterDialogPrimaryButton(
                label = stringResource(R.string.close),
                onClick = on_close,
            )
        },
    )
}

@Composable
private fun pgp_signature_tint(
    status: org.astermail.android.crypto.PgpSignatureStatus,
): androidx.compose.ui.graphics.Color {
    val colors = AsterMaterial.colors
    return when (status) {
        org.astermail.android.crypto.PgpSignatureStatus.VALID -> colors.success
        org.astermail.android.crypto.PgpSignatureStatus.INVALID -> colors.danger
        org.astermail.android.crypto.PgpSignatureStatus.UNVERIFIED -> colors.accent_blue
        org.astermail.android.crypto.PgpSignatureStatus.NONE -> colors.text_muted
    }
}

@Composable
private fun detail_meta_row(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    value_tint: androidx.compose.ui.graphics.Color? = null,
    on_click: (() -> Unit)? = null,
    on_long_click: (() -> Unit)? = null,
    value_max_height: androidx.compose.ui.unit.Dp? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (on_click != null || on_long_click != null) {
                    Modifier.combinedClickable(
                        hapticFeedbackEnabled = false,
                        onClick = on_click ?: {},
                        onLongClick = on_long_click,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = value_tint ?: colors.text_muted,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    color = colors.text_primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = value_tint ?: colors.text_secondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (value_max_height != null) {
                            Modifier
                                .heightIn(max = value_max_height)
                                .vertical_scroll_with_indicator()
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        if (on_click != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(15.dp).mirror_in_rtl(),
            )
        }
    }
}

@Composable
private fun collapsed_message(
    msg: ThreadMessage,
    is_first_card: Boolean = true,
    is_last_card: Boolean = true,
    my_email: String = "",
    my_profile_pic: String? = null,
    message_index: Int = 0,
    on_expand: () -> Unit,
) {
    val colors = AsterMaterial.colors

    val is_undecryptable = msg.is_undecryptable || (msg.sender_email.isBlank() && msg.body.isBlank())
    val is_own_message = my_email.isNotEmpty() &&
        msg.sender_email.trim().lowercase() == my_email &&
        !msg.is_external
    val trust = remember_sender_trust(if (is_own_message || is_undecryptable) null else msg.trust_message)
    val marks_hidden = trust?.show_identity_marks == false
    val shown_sender_name = org.astermail.android.security.trusted_sender_name(
        trust,
        displayed_sender_name(msg.display_sender_name, msg.sender_name),
        stringResource(R.string.unknown),
    )
    val card_color = inbox_card_read_color(colors)

    val card_shape = remember(is_first_card, is_last_card) {
        inbox_group_shape(is_first_card, is_last_card)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last_card) 0.dp else inbox_group_split,
            )
            .clip(card_shape)
            .acrylic_backdrop(colors)
            .background(card_color)
            .background(email_glass_backing(colors, white_page = false)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = on_expand)
                .padding(
                    horizontal = inbox_card_content_padding,
                    vertical = AsterSpacing.md,
                )
                .testTag("message_header_$message_index"),
            verticalAlignment = Alignment.Top,
        ) {
            if (is_undecryptable) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .acrylic(colors, CircleShape, colors.bg_card),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.Lock,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                SenderAvatar(
                    email = displayed_sender_email(msg.display_sender_email, msg.sender_email),
                    name = shown_sender_name,
                    size = 40.dp,
                    sender_authenticated = !marks_hidden &&
                        trust?.warning == null &&
                        system_avatar_authenticated(msg),
                    profile_picture_url = if (is_own_message) my_profile_pic else null,
                    plain = marks_hidden,
                )
            }
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (is_undecryptable) stringResource(R.string.encrypted) else shown_sender_name,
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    val row_yesterday_label = stringResource(R.string.yesterday)
                    val row_relative_time = remember(msg.timestamp, row_yesterday_label, AsterTimePreferences.generation) {
                        msg.timestamp.format_message_time(row_yesterday_label)
                    }
                    Text(
                        text = row_relative_time,
                        color = colors.text_muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (is_undecryptable) stringResource(R.string.e2e_encrypted_message)
                        else clean_preview_text(msg.preview, msg.body).ifBlank { stringResource(R.string.no_body) },
                    color = colors.text_muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun clean_preview_text(preview: String, body: String): String {
    val source = preview.ifBlank {
        body.lineSequence().map { it.trim() }.firstOrNull { line ->
            line.isNotBlank() && line.any { it.isLetterOrDigit() }
        }.orEmpty()
    }
    val collapsed = source
        .replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), "")
        .replace(Regex("[*_~`#=\\-]{2,}"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return collapsed
}

@Composable
private fun hidden_group_indicator(
    count: Int,
    on_reveal: () -> Unit,
) {
    val colors = AsterMaterial.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_reveal)
            .padding(vertical = AsterSpacing.md),
        contentAlignment = Alignment.CenterStart,
    ) {
        AsterDivider(modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier
                .padding(start = AsterSpacing.md)
                .height(40.dp)
                .acrylic(colors, CircleShape, field_surface_color(colors))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = count.toString(),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = colors.text_secondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun action_chip(
    label: String,
    icon: ImageVector,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    is_primary: Boolean = false,
    mirror_icon: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val bg = if (is_primary) colors.accent_blue else colors.bg_secondary
    val text_color = if (is_primary) colors.on_accent else colors.text_primary
    val icon_color = if (is_primary) colors.on_accent else colors.text_secondary

    Row(
        modifier = modifier
            .height(36.dp)
            .clip(SquircleShape(18.dp))
            .background(bg)
            .clickable(onClick = on_click),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = icon_color,
            modifier = Modifier
                .size(16.dp)
                .then(
                    if (mirror_icon) Modifier.graphicsLayer(scaleX = -1f) else Modifier,
                ),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = text_color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private object action_menu_position_provider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

private val action_menu_shadow_gutter = 22.dp
private val action_menu_anchor_gap = 4.dp
private val action_menu_screen_margin = 12.dp

private class action_menu_placement {
    var upward by mutableStateOf(false)
}

@Composable
internal fun action_menu_sheet(
    expanded: Boolean,
    on_close: () -> Unit,
    on_reply: () -> Unit,
    on_reply_all: () -> Unit,
    on_forward: () -> Unit,
    on_star: () -> Unit,
    is_starred: Boolean,
    on_mark_unread: () -> Unit,
    on_archive: () -> Unit,
    is_archived: Boolean = false,
    on_trash: () -> Unit,
    on_spam: () -> Unit,
    is_spam: Boolean = false,
    on_snooze: () -> Unit = {},
    on_label: () -> Unit = {},
    on_customize_toolbar: () -> Unit = {},
    anchor: IntRect? = null,
    is_draft: Boolean = false,
    is_trashed: Boolean = false,
    on_restore: () -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val parent_view = LocalView.current
    val placement = remember { action_menu_placement() }
    val visible_state = remember { MutableTransitionState(false) }
    visible_state.targetState = expanded
    if (!visible_state.currentState && !visible_state.targetState) return

    val scrim_interaction = remember { MutableInteractionSource() }
    val menu_reduce_motion = aster_reduce_motion()
    val menu_pop_fade_enter = if (menu_reduce_motion) AsterDuration.instant else AsterDuration.menu_fade_enter
    val menu_pop_enter = if (menu_reduce_motion) AsterDuration.instant else AsterDuration.menu_enter
    val menu_pop_fade_exit = if (menu_reduce_motion) AsterDuration.instant else AsterDuration.menu_fade_exit
    val menu_pop_exit = if (menu_reduce_motion) AsterDuration.instant else AsterDuration.menu_exit
    Popup(
        popupPositionProvider = action_menu_position_provider,
        onDismissRequest = on_close,
        properties = PopupProperties(focusable = true),
    ) {
        val popup_view = LocalView.current
        val layout_direction = LocalLayoutDirection.current
        val bars = WindowInsets.systemBars
        val origin = when {
            anchor == null -> TransformOrigin(1f, 1f)
            layout_direction == LayoutDirection.Rtl -> TransformOrigin(0f, if (placement.upward) 1f else 0f)
            else -> TransformOrigin(1f, if (placement.upward) 1f else 0f)
        }
        val menu: @Composable (Modifier) -> Unit = { menu_modifier ->
            AnimatedVisibility(
                visibleState = visible_state,
                modifier = menu_modifier,
                enter = fadeIn(
                        animationSpec = tween(
                            menu_pop_fade_enter,
                            easing = AsterEasing.menu_enter,
                        ),
                    ) +
                        scaleIn(
                            animationSpec = tween(
                                menu_pop_enter,
                                easing = AsterEasing.menu_enter,
                            ),
                            initialScale = org.astermail.android.design.AsterScale.menu_enter_from,
                            transformOrigin = origin,
                        ),
                    exit = fadeOut(animationSpec = tween(menu_pop_fade_exit)) +
                        scaleOut(
                            animationSpec = tween(
                                menu_pop_exit,
                                easing = AsterEasing.menu_exit,
                            ),
                            targetScale = org.astermail.android.design.AsterScale.menu_exit_to,
                            transformOrigin = origin,
                        ),
                ) {
                    aster_menu_surface(
                        modifier = Modifier.testTag("action_menu"),
                        min_width = 240.dp,
                        max_width = 320.dp,
                        max_height = 460.dp,
                    ) {
                        aster_menu_item(stringResource(R.string.reply), on_reply, icon = TablerIcons.ArrowBackUp.auto_mirrored())
                        aster_menu_item(stringResource(R.string.reply_all), on_reply_all, icon = TablerIcons.ArrowsLeft)
                        aster_menu_item(stringResource(R.string.forward), on_forward, icon = TablerIcons.MailForward)
                        aster_menu_item(
                            if (is_starred) stringResource(R.string.unstar) else stringResource(R.string.star),
                            on_star,
                            icon = TablerIcons.Star,
                        )
                        aster_menu_item(stringResource(R.string.mark_as_unread), on_mark_unread, icon = TablerIcons.Mail)
                        aster_menu_item(stringResource(R.string.label), on_label, icon = TablerIcons.Tag)
                        aster_menu_item(stringResource(R.string.snooze), on_snooze, icon = TablerIcons.Moon)
                        if (is_trashed) {
                            aster_menu_item(stringResource(R.string.swipe_restore), on_restore, icon = TablerIcons.Inbox)
                        } else {
                            aster_menu_item(
                                if (is_archived) stringResource(R.string.swipe_move_to_inbox) else stringResource(R.string.swipe_archive),
                                on_archive,
                                icon = if (is_archived) TablerIcons.Inbox else TablerIcons.Archive,
                            )
                        }
                        if (is_spam) {
                            aster_menu_item(
                                stringResource(R.string.swipe_not_spam),
                                on_spam,
                                icon = spam_action_icon(is_spam = true),
                                tint = colors.accent_blue,
                            )
                        } else {
                            aster_menu_item(
                                stringResource(R.string.report_spam),
                                on_spam,
                                icon = spam_action_icon(is_spam = false),
                                destructive = true,
                            )
                        }
                        aster_menu_item(
                            stringResource(
                                when (org.astermail.android.mail.destructive_action_for(is_trashed, is_draft)) {
                                    org.astermail.android.mail.DestructiveAction.delete_forever -> R.string.swipe_delete_forever
                                    org.astermail.android.mail.DestructiveAction.discard_draft -> R.string.discard_draft
                                    org.astermail.android.mail.DestructiveAction.move_to_trash -> R.string.move_to_trash
                                },
                            ),
                            on_trash,
                            icon = if (is_trashed) TablerIcons.TrashOff else TablerIcons.Trash,
                            destructive = true,
                        )
                        aster_menu_item(
                            stringResource(R.string.customize_toolbar),
                            on_customize_toolbar,
                            icon = TablerIcons.Adjustments,
                            tint = colors.text_secondary,
                        )
                    }
                }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = scrim_interaction,
                        indication = null,
                        onClick = on_close,
                    ),
            )
            if (anchor == null) {
                menu(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = action_menu_shadow_gutter, bottom = action_menu_shadow_gutter),
                )
            } else {
                Layout(
                    content = { menu(Modifier) },
                    modifier = Modifier.fillMaxSize(),
                ) { measurables, constraints ->
                    val margin = action_menu_screen_margin.roundToPx()
                    val gap = action_menu_anchor_gap.roundToPx()
                    val top_limit = bars.getTop(this) + margin
                    val bottom_limit = constraints.maxHeight - bars.getBottom(this) - margin
                    val placeable = measurables.first().measure(
                        Constraints(
                            maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0),
                            maxHeight = (bottom_limit - top_limit).coerceAtLeast(0),
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val parent_screen = IntArray(2).also { parent_view.getLocationOnScreen(it) }
                        val parent_window = IntArray(2).also { parent_view.getLocationInWindow(it) }
                        val popup_screen = IntArray(2).also { popup_view.getLocationOnScreen(it) }
                        val dx = parent_screen[0] - parent_window[0] - popup_screen[0]
                        val dy = parent_screen[1] - parent_window[1] - popup_screen[1]
                        val left = anchor.left + dx
                        val right = anchor.right + dx
                        val top = anchor.top + dy
                        val bottom = anchor.bottom + dy
                        val width = constraints.maxWidth
                        val raw_x = if (layout_direction == LayoutDirection.Rtl) left else right - placeable.width
                        val x = raw_x.coerceIn(margin, (width - placeable.width - margin).coerceAtLeast(margin))
                        val below = bottom + gap
                        val above = top - gap - placeable.height
                        val upward = below + placeable.height > bottom_limit && above >= top_limit
                        if (placement.upward != upward) placement.upward = upward
                        val y = if (upward) {
                            above
                        } else {
                            below.coerceAtMost((bottom_limit - placeable.height).coerceAtLeast(top_limit))
                        }
                        placeable.place(x, y)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun snooze_sheet(
    on_close: () -> Unit,
    on_pick: (iso: String, label: String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    val later_today = stringResource(R.string.snooze_later_today)
    val tomorrow_morning = stringResource(R.string.snooze_tomorrow_morning)
    val this_weekend_label = stringResource(R.string.snooze_this_weekend)
    val next_week_label = stringResource(R.string.snooze_next_week)
    val next_month_label = stringResource(R.string.snooze_next_month)
    val options = remember(
        later_today,
        tomorrow_morning,
        this_weekend_label,
        next_week_label,
        next_month_label,
    ) {
        snooze_options(
            later_today,
            tomorrow_morning,
            this_weekend_label,
            next_week_label,
            next_month_label,
        )
    }
    aster_modal_sheet(
        shape = org.astermail.android.ui.common.aster_sheet_shape,
        on_dismiss_request = on_close,
        sheet_state = state,
        container_color = sheet_container_color(colors),
        drag_handle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.snooze_until),
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    start = AsterSpacing.xl,
                    end = AsterSpacing.xl,
                    top = AsterSpacing.xs,
                    bottom = AsterSpacing.sm,
                ),
            )
            options.forEach { (label, iso) ->
                snooze_row(
                    label = label,
                    detail = snooze_detail_label(iso),
                    tint = colors.text_primary,
                    detail_tint = colors.text_secondary,
                ) { on_pick(iso, label) }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@Composable
private fun snooze_row(
    label: String,
    detail: String,
    tint: Color,
    detail_tint: Color,
    on_click: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.xl, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            color = detail_tint,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun snooze_detail_label(iso: String): String {
    val millis = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
        ?: return ""
    return millis.format_full_datetime()
}

internal const val PICKER_TREE_INDENT_DP = 18

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun label_picker_sheet(
    title: String,
    empty_message: String,
    items: List<org.astermail.android.api.labels.LabelItem>,
    on_close: () -> Unit,
    on_pick: (org.astermail.android.api.labels.LabelItem) -> Unit,
    applied_tokens: Set<String> = emptySet(),
    on_move_to_inbox: (() -> Unit)? = null,
    depths: Map<String, Int> = emptyMap(),
    system_targets: List<String> = emptyList(),
    on_system_target: (String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    aster_modal_sheet(
        shape = org.astermail.android.ui.common.aster_sheet_shape,
        on_dismiss_request = on_close,
        sheet_state = state,
        container_color = sheet_container_color(colors),
        drag_handle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    start = AsterSpacing.xl,
                    end = AsterSpacing.xl,
                    top = AsterSpacing.xs,
                    bottom = AsterSpacing.sm,
                ),
            )
            if (on_move_to_inbox != null) {
                sheet_row(
                    label = stringResource(R.string.folder_inbox),
                    tint = colors.text_primary,
                    icon = TablerIcons.Inbox,
                    on_click = on_move_to_inbox,
                )
            }
            system_targets.forEach { target ->
                val (label_res, icon) = when (target) {
                    "archive" -> R.string.folder_archive to TablerIcons.Archive
                    "spam" -> R.string.folder_spam to spam_action_icon(is_spam = false)
                    "trash" -> R.string.folder_trash to TablerIcons.Trash
                    "star" -> R.string.folder_starred to TablerIcons.Star
                    else -> return@forEach
                }
                Box(modifier = Modifier.testTag("move_sheet_$target")) {
                    sheet_row(
                        label = stringResource(label_res),
                        tint = colors.text_primary,
                        icon = icon,
                        on_click = { on_system_target(target) },
                    )
                }
            }
            if (on_move_to_inbox != null || system_targets.isNotEmpty()) {
                AsterDivider(modifier = Modifier.padding(horizontal = AsterSpacing.xl))
            }
            if (items.isEmpty()) {
                Text(
                    text = empty_message,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = AsterSpacing.xl, vertical = AsterSpacing.md),
                )
            } else {
                items.forEach { item ->
                    val display = item.encrypted_name?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.unnamed_folder)
                    val applied = item.label_token in applied_tokens
                    val depth = (depths[item.label_token] ?: 0).coerceAtLeast(0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { on_pick(item) }
                            .padding(horizontal = AsterSpacing.xl, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (depth > 0) Spacer(Modifier.width((depth * PICKER_TREE_INDENT_DP).dp))
                        Icon(
                            imageVector = TablerIcons.Folder,
                            contentDescription = null,
                            tint = item.encrypted_color
                                ?.takeIf { it.startsWith("#") }
                                ?.let { org.astermail.android.design.parse_hex_color_safe(it) }
                                ?: colors.text_primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(AsterSpacing.md))
                        Text(
                            text = display,
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (applied) {
                            Icon(
                                imageVector = TablerIcons.Check,
                                contentDescription = null,
                                tint = colors.accent_blue,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun tag_picker_sheet(
    title: String,
    empty_message: String,
    items: List<org.astermail.android.api.tags.TagItem>,
    on_close: () -> Unit,
    on_pick: (org.astermail.android.api.tags.TagItem) -> Unit,
    applied_tokens: Set<String> = emptySet(),
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    aster_modal_sheet(
        shape = org.astermail.android.ui.common.aster_sheet_shape,
        on_dismiss_request = on_close,
        sheet_state = state,
        container_color = sheet_container_color(colors),
        drag_handle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    start = AsterSpacing.xl,
                    end = AsterSpacing.xl,
                    top = AsterSpacing.xs,
                    bottom = AsterSpacing.sm,
                ),
            )
            if (items.isEmpty()) {
                Text(
                    text = empty_message,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = AsterSpacing.xl, vertical = AsterSpacing.md),
                )
            } else {
                val unknown_tag_label = stringResource(R.string.label_decrypt_failed)
                val tag_nodes = remember(items) { org.astermail.android.labels.flatten_tag_tree(items) }
                tag_nodes.forEach { node ->
                    val item = node.tag
                    val display = org.astermail.android.labels.tag_display_name(item, unknown_tag_label)
                    val tag_color = try {
                        item.encrypted_color?.let { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) }
                    } catch (_: Throwable) { null }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { on_pick(item) }
                            .padding(horizontal = AsterSpacing.xl, vertical = AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (node.depth > 0) {
                            Spacer(Modifier.width((node.depth * PICKER_TREE_INDENT_DP).dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(tag_color ?: Color.Transparent, shape = CircleShape),
                        )
                        Spacer(Modifier.width(AsterSpacing.md))
                        Text(
                            text = display,
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.tag_token in applied_tokens) {
                            Icon(
                                imageVector = TablerIcons.Check,
                                contentDescription = null,
                                tint = colors.accent_blue,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

internal const val SNOOZE_MORNING_HOUR = 9

internal const val SNOOZE_LATER_TODAY_OFFSET_HOURS = 4L

internal fun snooze_targets(
    now: java.time.ZonedDateTime,
): List<java.time.ZonedDateTime> {
    val later_today = now.plusHours(SNOOZE_LATER_TODAY_OFFSET_HOURS)
    val tomorrow = at_snooze_morning(now.plusDays(1))
    val days_until_saturday = ((java.time.DayOfWeek.SATURDAY.value -
        now.dayOfWeek.value + 7) % 7).let { if (it == 0) 7 else it }
    val this_weekend = at_snooze_morning(now.plusDays(days_until_saturday.toLong()))
    val next_week = at_snooze_morning(now.plusDays(7))
    val next_month = at_snooze_morning(now.plusMonths(1))
    return listOf(later_today, tomorrow, this_weekend, next_week, next_month)
}

private fun at_snooze_morning(
    value: java.time.ZonedDateTime,
): java.time.ZonedDateTime {
    return value.toLocalDate()
        .atTime(SNOOZE_MORNING_HOUR, 0)
        .atZone(value.zone)
}

internal fun snooze_options(
    later_today_label: String,
    tomorrow_morning_label: String,
    this_weekend_label: String,
    next_week_label: String,
    next_month_label: String,
    now: java.time.ZonedDateTime = java.time.ZonedDateTime.now(
        AsterTimePreferences.account_zone_id(),
    ),
): List<Pair<String, String>> {
    val labels = listOf(
        later_today_label,
        tomorrow_morning_label,
        this_weekend_label,
        next_week_label,
        next_month_label,
    )
    val fmt = java.time.format.DateTimeFormatter.ISO_INSTANT
    val targets = snooze_targets(now)
    val paired = labels.zip(targets) { label, target ->
        label to fmt.format(target.toInstant())
    }
    val later_today_is_today = targets[0].toLocalDate() == now.toLocalDate()
    return if (later_today_is_today) paired else paired.drop(1)
}

private suspend fun print_email(
    context: android.content.Context,
    msg: ThreadMessage,
    subject: String,
    allow_external: Boolean,
    sanitize_options: EmailHtmlSanitizer.SanitizeOptions,
    on_failure: () -> Unit,
) {
    val labels = email_print_labels(
        from = context.getString(R.string.from_label),
        to = context.getString(R.string.to_label),
        cc = context.getString(R.string.cc),
        date = context.getString(R.string.date),
        image_blocked = context.getString(R.string.image_blocked),
        tracking_pixel_blocked = context.getString(R.string.tracking_pixel_blocked),
    )
    val job_name = print_job_name(subject, context.getString(R.string.aster_email))
    val failure_message = context.getString(R.string.print_failed)
    val html = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        runCatching {
            val body = build_email_print_body(
                msg,
                allow_external,
                sanitize_options,
                BlockedImageLabels(labels.image_blocked, labels.tracking_pixel_blocked),
            )
            build_email_print_html(msg, subject, msg.timestamp.format_full_datetime(), labels, body)
        }.getOrNull()
    }
    if (html == null) {
        on_failure()
        return
    }
    print_email_document(
        context = context,
        job_name = job_name,
        html = html,
        allow_network = allow_external,
        failure_message = failure_message,
        on_failure = on_failure,
    )
}

@Composable
internal fun sheet_row(
    label: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    on_click: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.xl, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(AsterSpacing.md))
        }
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun text_link_button(
    label: String,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .clip(SquircleShape(8.dp))
            .clickable(onClick = on_click)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = colors.accent_blue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun info_banner(
    icon: ImageVector,
    label: String,
    button_label: String,
    on_action: () -> Unit,
    on_dismiss: (() -> Unit)? = null,
    secondary: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(SquircleShape(8.dp))
                .clickable(onClick = on_action)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                text = button_label,
                color = colors.accent_blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (on_dismiss != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(SquircleShape(8.dp))
                    .clickable(onClick = on_dismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = colors.text_muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private val safe_external_schemes = setOf("http", "https", "mailto", "tel")
private val safe_unsubscribe_schemes = setOf("https", "mailto")

internal fun copy_external_link(
    link: String,
    write_clip: (label: String, text: String) -> Boolean,
    on_copied: () -> Unit,
    on_failed: () -> Unit,
) {
    if (write_clip("link", link)) on_copied() else on_failed()
}

private fun is_safe_external_url(url: String): Boolean {
    val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull() ?: return false
    return scheme in safe_external_schemes
}

private fun is_safe_unsubscribe_url(url: String): Boolean {
    val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull() ?: return false
    return scheme in safe_unsubscribe_schemes
}

private const val max_body_layout_px = 200000

private inline fun <T> as_io_failure(block: () -> T): T =
    try {
        block()
    } catch (e: java.io.IOException) {
        throw e
    } catch (e: Throwable) {
        throw java.io.IOException(e)
    }
private const val remeasure_viewport_slack_px = 4
private const val vertical_overflow_slack_dp = 8f

internal object web_view_support {
    @Volatile
    private var known_available = false

    fun is_available(ctx: android.content.Context): Boolean {
        if (known_available) return true
        val available = runCatching {
            android.webkit.WebSettings.getDefaultUserAgent(ctx.applicationContext)
        }.isSuccess
        if (available) known_available = true
        return available
    }
}

private object contain_body_bring_into_view : androidx.compose.ui.node.ModifierNodeElement<contained_bring_into_view_node>() {
    override fun create(): contained_bring_into_view_node = contained_bring_into_view_node()

    override fun update(node: contained_bring_into_view_node) = Unit

    override fun hashCode(): Int = 0x6272

    override fun equals(other: Any?): Boolean = other === this
}

private class contained_bring_into_view_node :
    Modifier.Node(),
    androidx.compose.ui.relocation.BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: androidx.compose.ui.layout.LayoutCoordinates,
        boundsProvider: () -> androidx.compose.ui.geometry.Rect?,
    ) = Unit
}

internal class mail_body_web_view(
    ctx: android.content.Context,
) : android.webkit.WebView(ctx) {

    override fun requestRectangleOnScreen(rectangle: android.graphics.Rect?, immediate: Boolean): Boolean = false

    var selection_active: Boolean = false
        private set

    var selection_gesture: Boolean = false
        private set

    fun end_selection_gesture() {
        selection_gesture = false
    }

    var on_width_change: (() -> Unit)? = null

    var on_horizontal_scroll: ((Int, Int, Int) -> Unit)? = null

    fun horizontal_scroll_range(): Int = computeHorizontalScrollRange()

    fun horizontal_scroll_extent(): Int = computeHorizontalScrollExtent()

    fun report_horizontal_scroll() {
        on_horizontal_scroll?.invoke(
            computeHorizontalScrollOffset(),
            computeHorizontalScrollExtent(),
            computeHorizontalScrollRange(),
        )
    }

    private var pinching: Boolean = false

    private fun vertical_slack_px(): Int =
        (vertical_overflow_slack_dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    fun content_overflows_vertically(): Boolean =
        body_overflows_vertically(computeVerticalScrollRange(), height, vertical_slack_px())

    fun bottom_edge_visible(): Boolean {
        if (height <= 0 || !isShown) return false
        val visible = android.graphics.Rect()
        if (!getGlobalVisibleRect(visible)) return false
        val location = IntArray(2)
        getLocationInWindow(location)
        return visible.bottom >= location[1] + height - vertical_slack_px()
    }

    fun keeps_vertical_drag(drag_dy: Float): Boolean =
        body_keeps_vertical_drag(
            overflows = content_overflows_vertically(),
            drag_dy = drag_dy,
            can_scroll_down = canScrollVertically(1),
            can_scroll_up = canScrollVertically(-1),
            bottom_visible = bottom_edge_visible(),
        )

    private fun vertical_scroll_allowed(): Boolean =
        pinching || height >= max_body_layout_px - remeasure_viewport_slack_px || content_overflows_vertically()

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> pinching = false
            android.view.MotionEvent.ACTION_POINTER_DOWN -> pinching = true
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
            event.actionMasked == android.view.MotionEvent.ACTION_CANCEL
        ) {
            pinching = false
            if (scrollY != 0 && !vertical_scroll_allowed()) scrollTo(scrollX, 0)
        }
        return handled
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (l != oldl) report_horizontal_scroll()
        if (t != 0 && !vertical_scroll_allowed()) scrollTo(l, 0)
    }

    var on_content_height: ((Int) -> Unit)? = null

    var destroyed: Boolean = false
        private set

    private var reported_content_height = -1

    fun forget_content_height() {
        reported_content_height = -1
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (destroyed) return
        val content = contentHeight
        if (content > 0 && content != reported_content_height) {
            reported_content_height = content
            on_content_height?.invoke(content)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (destroyed) return
        if (body_width_changed(oldw, w) || (oldw == 0 && w > 0)) on_width_change?.invoke()
        if (h != oldh && h > 0) on_height_change?.invoke()
    }

    var on_height_change: (() -> Unit)? = null

    fun release_body() {
        if (destroyed) return
        destroyed = true
        on_content_height = null
        on_width_change = null
        on_height_change = null
        on_horizontal_scroll = null
        stopLoading()
        loadUrl("about:blank")
        removeAllViews()
        destroy()
    }

    private fun wrap(
        callback: android.view.ActionMode.Callback,
    ): android.view.ActionMode.Callback = object : android.view.ActionMode.Callback2() {
        override fun onGetContentRect(
            mode: android.view.ActionMode,
            view: android.view.View?,
            out_rect: android.graphics.Rect,
        ) {
            if (callback is android.view.ActionMode.Callback2) {
                callback.onGetContentRect(mode, view, out_rect)
            } else {
                super.onGetContentRect(mode, view, out_rect)
            }
        }

        override fun onCreateActionMode(
            mode: android.view.ActionMode,
            menu: android.view.Menu,
        ): Boolean {
            selection_active = true
            selection_gesture = true
            parent?.requestDisallowInterceptTouchEvent(true)
            return callback.onCreateActionMode(mode, menu)
        }

        override fun onPrepareActionMode(
            mode: android.view.ActionMode,
            menu: android.view.Menu,
        ): Boolean = callback.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(
            mode: android.view.ActionMode,
            item: android.view.MenuItem,
        ): Boolean = callback.onActionItemClicked(mode, item)

        override fun onDestroyActionMode(mode: android.view.ActionMode) {
            selection_active = false
            selection_gesture = false
            parent?.requestDisallowInterceptTouchEvent(false)
            callback.onDestroyActionMode(mode)
        }
    }

    override fun startActionMode(
        callback: android.view.ActionMode.Callback?,
    ): android.view.ActionMode? =
        if (callback == null) super.startActionMode(null) else super.startActionMode(wrap(callback))

    override fun startActionMode(
        callback: android.view.ActionMode.Callback?,
        type: Int,
    ): android.view.ActionMode? =
        if (callback == null) {
            super.startActionMode(null, type)
        } else {
            super.startActionMode(wrap(callback), type)
        }
}

internal fun configure_mail_body_web_view(
    web: android.webkit.WebView,
    text_zoom: Int,
    allow_external: Boolean,
) {
    web.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    web.settings.javaScriptEnabled = false
    web.settings.loadWithOverviewMode = false
    web.settings.useWideViewPort = true
    web.settings.textZoom = text_zoom
    web.settings.builtInZoomControls = false
    web.settings.displayZoomControls = false
    web.settings.setSupportZoom(false)
    web.settings.domStorageEnabled = false
    web.settings.loadsImagesAutomatically = true
    web.settings.blockNetworkImage = !allow_external
    web.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    web.settings.allowFileAccess = false
    web.settings.allowContentAccess = false
    @Suppress("DEPRECATION")
    web.settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    web.settings.allowUniversalAccessFromFileURLs = false
    web.settings.javaScriptCanOpenWindowsAutomatically = false
    web.settings.setGeolocationEnabled(false)
    @Suppress("DEPRECATION")
    web.settings.saveFormData = false
    @Suppress("DEPRECATION")
    web.settings.savePassword = false
    web.isVerticalScrollBarEnabled = false
    web.isHorizontalScrollBarEnabled = false
    web.overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
    web.isNestedScrollingEnabled = false
}

private object body_height_cache {
    private const val max_entries = 128
    private val store = object : java.util.LinkedHashMap<Long, Float>(max_entries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Float>?): Boolean = size > max_entries
    }
    @Synchronized fun get(key: Long): Float? = store[key]
    @Synchronized fun put(key: Long, value: Float) { store[key] = value }
}

private object card_analysis_memo {
    private const val capacity = 96
    private val content = object : LinkedHashMap<Long, MessageContentAnalysis>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, MessageContentAnalysis>?): Boolean =
            size > capacity
    }
    private val phishing = object : LinkedHashMap<Long, org.astermail.android.security.PhishingResult>(32, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, org.astermail.android.security.PhishingResult>?,
        ): Boolean = size > capacity
    }

    fun key_of(id: String, html: String?): Long =
        (id.hashCode().toLong() shl 32) xor ((html?.length ?: -1).toLong() * 31L + (html?.hashCode() ?: 0).toLong())

    @Synchronized
    fun content_get(key: Long): MessageContentAnalysis? = content[key]

    @Synchronized
    fun content_put(key: Long, value: MessageContentAnalysis) {
        content[key] = value
    }

    @Synchronized
    fun phishing_get(key: Long): org.astermail.android.security.PhishingResult? = phishing[key]

    @Synchronized
    fun phishing_put(key: Long, value: org.astermail.android.security.PhishingResult) {
        phishing[key] = value
    }
}

private object unsub_info_memo {
    private const val capacity = 96
    private val store = object : LinkedHashMap<Long, UnsubscribeInfo>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, UnsubscribeInfo>?): Boolean =
            size > capacity
    }

    fun key_of(id: String, html: String?, text: String?, header: String?, post: String?, dkim: String?): Long {
        var h = id.hashCode().toLong()
        for (part in arrayOf(html, text, header, post, dkim)) {
            h = h * 1_000_003L + (part?.length ?: -1).toLong()
            h = h * 31L + (part?.hashCode() ?: 0).toLong()
        }
        return h
    }

    @Synchronized
    fun get(key: Long): UnsubscribeInfo? = store[key]

    @Synchronized
    fun put(key: Long, value: UnsubscribeInfo) {
        store[key] = value
    }
}

private object renderable_part_memo {
    class Resolved(val html: String?)

    private const val capacity = 96
    private val store = object : LinkedHashMap<Long, Resolved>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Resolved>?): Boolean = size > capacity
    }

    @Synchronized
    fun get(key: Long): Resolved? = store[key]

    @Synchronized
    fun put(key: Long, value: Resolved) {
        store[key] = value
    }
}

private object thread_revealed_cache {
    private const val max_entries = 64
    private val store = object : java.util.LinkedHashMap<String, Boolean>(max_entries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > max_entries
    }
    @Synchronized fun was_revealed(key: String): Boolean = store[key] == true
    @Synchronized fun mark_revealed(key: String) { store[key] = true }
}

private object html_cache {
    private const val max_entries = 32
    private val store = object : java.util.LinkedHashMap<Long, String>(max_entries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean = size > max_entries
    }
    @Synchronized fun get(key: Long): String? = store[key]
    @Synchronized fun put(key: Long, value: String) { store[key] = value }
    fun height_key(html_hash: Int, allow_external: Boolean, screen_w: Int, text_zoom: Int): Long {
        var h = html_hash.toLong() and 0xFFFFFFFFL
        h = h * 31L + (if (allow_external) 1L else 0L)
        h = h * 31L + screen_w.toLong()
        h = h * 31L + text_zoom.toLong()
        return h
    }
    fun key(html_hash: Int, allow_external: Boolean, bg_hex: String, screen_w: Int = 0, force_dark: Boolean = false, translate: Boolean = false): Long {
        var h = html_hash.toLong() and 0xFFFFFFFFL
        h = h * 31L + (if (allow_external) 1L else 0L)
        h = h * 31L + bg_hex.hashCode().toLong()
        h = h * 31L + org.astermail.android.BuildConfig.VERSION_CODE.toLong()
        h = h * 31L + screen_w.toLong()
        h = h * 31L + (if (force_dark) 1L else 0L)
        h = h * 31L + (if (translate) 1L else 0L)
        return h
    }
}

@Volatile
private var email_image_client_instance: okhttp3.OkHttpClient? = null

internal const val EMAIL_FONT_PATH = "/__aster_font/opendyslexic.otf"

internal const val EMAIL_USER_FONT_PREFIX = "/__aster_email_font/"

private fun is_zoomable_image_src(src: String): Boolean {
    if (src.isBlank() || src.length > 8192) return false
    if (src.startsWith("data:image/", ignoreCase = true)) return true
    if (src.startsWith(INLINE_IMAGE_URL_PREFIX)) return true
    return try {
        val parsed = android.net.Uri.parse(src)
        parsed.scheme.equals("https", ignoreCase = true) && parsed.host == "app.astermail.org"
    } catch (_: Throwable) {
        false
    }
}

private fun email_user_font_response(
    context: android.content.Context,
    path: String,
): android.webkit.WebResourceResponse? = try {
    val name = path.removePrefix(EMAIL_USER_FONT_PREFIX).removeSuffix(".ttf")
    val parts = name.split("__")
    val resource = if (parts.size == 2) {
        org.astermail.android.design.email_font_resource_for(parts[0], parts[1])
    } else {
        null
    }
    if (resource == null) {
        null
    } else {
        android.webkit.WebResourceResponse(
            "font/ttf",
            null,
            200,
            "OK",
            mapOf("Cache-Control" to "max-age=86400"),
            context.resources.openRawResource(resource),
        )
    }
} catch (_: Throwable) {
    null
}

@Suppress("ResourceType")
private fun email_font_response(context: android.content.Context): android.webkit.WebResourceResponse? = try {
    android.webkit.WebResourceResponse(
        "font/otf",
        null,
        200,
        "OK",
        mapOf("Cache-Control" to "max-age=86400"),
        context.resources.openRawResource(R.font.opendyslexic_regular),
    )
} catch (_: Throwable) {
    null
}

private fun email_image_client(context: android.content.Context): okhttp3.OkHttpClient {
    email_image_client_instance?.let { return it }
    return synchronized(mail_detail_image_lock) {
        email_image_client_instance ?: okhttp3.OkHttpClient.Builder()
            .dns(org.astermail.android.api.DualStackDns)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
            .apply {
                dispatcher.maxRequests = 64
                dispatcher.maxRequestsPerHost = 16
            }
            .also { email_image_client_instance = it }
    }
}

private val mail_detail_image_lock = Any()

internal fun evict_email_image_cache() {
    synchronized(mail_detail_image_lock) {
        runCatching { email_image_client_instance?.cache?.evictAll() }
    }
}

private object translation_offer_memo {
    private const val dismissed_marker = "-"
    private val entries = object : java.util.LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 128
    }

    @Synchronized
    fun language(key: String): String? = entries[key]?.takeIf { it != dismissed_marker }

    @Synchronized
    fun is_dismissed(key: String): Boolean = entries[key] == dismissed_marker

    @Synchronized
    fun remember_language(key: String, language: String) {
        if (entries[key] != dismissed_marker) entries[key] = language
    }

    @Synchronized
    fun dismiss(key: String) {
        entries[key] = dismissed_marker
    }
}

private sealed interface TranslationBannerState {
    object Hidden : TranslationBannerState
    data class Offer(val language: String, val needs_download: Boolean) : TranslationBannerState
    object Translating : TranslationBannerState
    data class Translated(val language: String) : TranslationBannerState
    data class ShowingOriginal(val language: String) : TranslationBannerState
    object Failed : TranslationBannerState
    object WifiRequired : TranslationBannerState
    object WebViewOutdated : TranslationBannerState
}

@Composable
private fun translation_banner(
    state: TranslationBannerState,
    target_language: String,
    on_translate: (String) -> Unit,
    on_show_original: () -> Unit,
    on_show_translation: () -> Unit,
    on_dismiss: () -> Unit,
    on_update_webview: () -> Unit,
) {
    val colors = AsterMaterial.colors
    if (state is TranslationBannerState.Hidden) return
    val translate_icon: @Composable () -> Unit = {
        compact_notice_icon(TablerIcons.Language, colors.text_muted)
    }
    when (state) {
        is TranslationBannerState.Offer -> {
            val name = org.astermail.android.translation.language_display_name(state.language)
            val target = org.astermail.android.translation.language_display_name(target_language)
            compact_notice_bar(
                modifier = Modifier.testTag("translation_offer"),
                leading = translate_icon,
                on_close = on_dismiss,
            ) {
                translation_bar_line(
                    label = name,
                    action = stringResource(R.string.translation_bar_translate_to, target),
                    on_action = { on_translate(state.language) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        is TranslationBannerState.Translating -> {
            compact_notice_bar(
                leading = {
                    org.astermail.android.design.components.aster_spinner(
                        color = colors.accent_blue,
                        stroke_width = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                },
            ) {
                Text(
                    text = stringResource(R.string.translation_translating),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        is TranslationBannerState.Translated -> {
            val name = org.astermail.android.translation.language_display_name(state.language)
            compact_notice_bar(leading = translate_icon) {
                translation_bar_line(
                    label = stringResource(R.string.translation_bar_translated_from, name),
                    action = stringResource(R.string.translation_show_original),
                    on_action = on_show_original,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        is TranslationBannerState.ShowingOriginal -> {
            val name = org.astermail.android.translation.language_display_name(state.language)
            compact_notice_bar(leading = translate_icon) {
                translation_bar_line(
                    label = name,
                    action = stringResource(R.string.translation_show_translation),
                    on_action = on_show_translation,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        is TranslationBannerState.WifiRequired -> {
            compact_notice_bar(leading = translate_icon, on_close = on_dismiss) {
                Text(
                    text = stringResource(R.string.translation_wifi_required),
                    color = colors.text_secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        is TranslationBannerState.WebViewOutdated -> {
            compact_notice_bar(leading = translate_icon, on_close = on_dismiss) {
                translation_bar_line(
                    label = stringResource(
                        R.string.translation_webview_outdated,
                        org.astermail.android.translation.TranslationRuntime.webview_name(LocalContext.current),
                    ),
                    action = stringResource(R.string.translation_webview_update),
                    on_action = on_update_webview,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        is TranslationBannerState.Failed -> {
            compact_notice_bar(leading = translate_icon, on_close = on_dismiss) {
                Text(
                    text = stringResource(R.string.translation_failed),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun translation_bar_line(
    label: String,
    action: String,
    on_action: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = " \u00B7 ",
            color = colors.text_tertiary,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Text(
            text = action,
            color = colors.accent_blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clip(AsterShapes.island)
                .clickable(onClick = on_action)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

private fun reveal_search_hit(
    web_ref: Array<android.webkit.WebView?>,
    fraction: Float,
): Float? {
    val web = web_ref[0] ?: return null
    if (web.height <= 0) return null
    @Suppress("DEPRECATION")
    val px_per_css = web.scale.takeIf { it > 0f } ?: web.resources.displayMetrics.density
    val content_px = (web.contentHeight * px_per_css).coerceAtLeast(web.height.toFloat())
    val top_px = fraction.coerceIn(0f, 1f) * content_px
    val inner_top = web.scrollY.toFloat()
    if (content_px > web.height + 1 && (top_px < inner_top || top_px > inner_top + web.height * 0.8f)) {
        val max_inner = (content_px - web.height).coerceAtLeast(0f)
        val target = (top_px - web.height * 0.3f).coerceIn(0f, max_inner)
        web.scrollTo(web.scrollX, target.toInt())
    }
    val location = IntArray(2)
    web.getLocationInWindow(location)
    return location[1] + top_px - web.scrollY
}

@Composable
internal fun email_html_view(
    html: String,
    modifier: Modifier = Modifier,
    message_key: String? = null,
    allow_external: Boolean = false,
    inline_images: Map<String, String> = emptyMap(),
    access_token: String? = null,
    force_light: Boolean = false,
    on_ready: () -> Unit = {},
    on_link_click: (String) -> Unit = {},
    on_image_click: (String) -> Unit = {},
    translation_enabled: Boolean = true,
    allow_auto_translate: Boolean = true,
    subject: String? = null,
    on_subject_translated: (String?) -> Unit = {},
    search_key: String? = null,
) {
    val webview_usable = org.astermail.android.ui.common.remember_webview_usable()
    LaunchedEffect(webview_usable) {
        if (!webview_usable) on_ready()
    }
    val colors = AsterMaterial.colors
    val settings_vm: SettingsViewModel = shared_settings_view_model()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val theme_dark = !force_light && email_theme_is_dark(colors)
    val force_dark_emails = forces_dark_emails(settings_state.preferences?.force_dark_emails == true, theme_dark)
    val is_dark = theme_dark
    val body_surface = if (colors.is_glass) colors.thread_content_bg else colors.bg_primary
    val bg_hex = when {
        force_light -> "#FFFFFF"
        colors.is_glass -> "transparent"
        else -> String.format(java.util.Locale.US, "#%06X", body_surface.toArgb() and 0xFFFFFF)
    }
    val fg_hex = when {
        force_light -> "#111827"
        else -> String.format(java.util.Locale.US, "#%06X", colors.text_primary.toArgb() and 0xFFFFFF)
    }
    val link_hex = String.format(java.util.Locale.US, "#%06X", colors.accent_blue.toArgb() and 0xFFFFFF)

    val pan_signal = local_horizontal_pan_signal.current
    val screen_width_dp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val text_zoom = when (settings_state.preferences?.font_size_scale) {
        "small" -> 85
        "large" -> 120
        "extra_large" -> 140
        else -> 100
    }
    val forwarded_label = stringResource(R.string.forwarded_message_label)
    val hide_quote_label_text = stringResource(R.string.hide_quoted_text)
    val show_quote_label_text = stringResource(R.string.show_quoted_text)
    val blocked_image_labels = BlockedImageLabels(
        image = stringResource(R.string.image_blocked),
        tracking_pixel = stringResource(R.string.tracking_pixel_blocked),
    )
    val image_failed_label = stringResource(R.string.image_failed_placeholder)
    val sanitize_options = remote_content_sanitize_options(settings_state.preferences)
    val dyslexia_font = settings_state.preferences?.dyslexia_font == true
    val underline_links = settings_state.preferences?.underline_links == true
    val email_font_id = org.astermail.android.design.resolve_email_font_id(
        settings_state.preferences?.email_font_choice,
        settings_state.preferences?.font_choice,
    )

    val translate_mode = if (org.astermail.android.translation.translation_supported && translation_enabled) {
        settings_state.preferences?.translate_incoming ?: "off"
    } else {
        "off"
    }
    val translate_langs = settings_state.preferences?.translate_languages ?: emptyList()
    val translate_never_langs = settings_state.preferences?.translate_never_languages ?: emptyList()
    val ui_language = org.astermail.android.translation.normalize_language_code(
        java.util.Locale.getDefault().language,
    )
    val translate_target = org.astermail.android.translation.normalize_language_code(
        translate_langs.firstOrNull(),
    ) ?: ui_language ?: "en"
    val translate_accepted = remember(translate_langs, translate_never_langs, ui_language) {
        (translate_langs + translate_never_langs + listOfNotNull(ui_language))
            .mapNotNull { org.astermail.android.translation.normalize_language_code(it) }
            .distinct()
            .joinToString(",")
    }
    val translate_context = LocalContext.current
    val translate_active_early = translate_mode != "off"
    val web_ref = remember { arrayOfNulls<android.webkit.WebView>(1) }
    val translation_memo_key = message_key ?: html.hashCode().toString()
    var translation_state by remember(html) {
        val remembered_language = if (translate_mode != "off" && translate_mode != "always") translation_offer_memo.language(translation_memo_key) else null
        mutableStateOf<TranslationBannerState>(
            if (remembered_language != null && remembered_language != translate_target) {
                TranslationBannerState.Offer(
                    remembered_language,
                    !TranslationDownloadPolicy.route_consent_granted(translate_context, remembered_language, translate_target),
                )
            } else {
                TranslationBannerState.Hidden
            },
        )
    }
    val main_handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val translate_mode_ref = remember { arrayOf("off") }
    val translate_target_ref = remember { arrayOf("en") }
    val set_translation_state = remember { arrayOfNulls<(TranslationBannerState) -> Unit>(1) }
    translate_mode_ref[0] = translate_mode
    translate_target_ref[0] = translate_target
    set_translation_state[0] = { translation_state = it }

    val translation_engine_ref = remember {
        arrayOfNulls<org.astermail.android.translation.translation_session>(1)
    }
    val source_body_ref = remember(html) { arrayOfNulls<String>(1) }
    var source_body_version by remember(html) { mutableStateOf(0) }
    var translated_body by remember(html) { mutableStateOf<String?>(null) }
    val translation_scope = androidx.compose.runtime.rememberCoroutineScope()
    val translation_from_ref = remember { arrayOfNulls<String>(1) }
    val translation_subject_ref = remember { arrayOfNulls<String>(1) }
    val translation_subject_sent = remember(html) { booleanArrayOf(false) }
    val translation_source_ref = remember(html) { arrayOfNulls<String>(1) }
    val kept_translation = remember(html) {
        arrayOfNulls<org.astermail.android.translation.remembered_translation>(1)
    }
    val translation_watchdog = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val current_translation_state = remember { arrayOfNulls<TranslationBannerState>(1) }
    val on_subject_translated_ref = remember { arrayOfNulls<(String?) -> Unit>(1) }
    val allow_auto_translate_ref = remember { booleanArrayOf(true) }
    translation_subject_ref[0] = subject?.takeIf { it.isNotBlank() }
    on_subject_translated_ref[0] = on_subject_translated
    allow_auto_translate_ref[0] = allow_auto_translate
    current_translation_state[0] = translation_state

    fun translation_memory_key(source: String): String =
        org.astermail.android.translation.TranslationMemory.key(
            source.hashCode() * 31 + (translation_subject_ref[0]?.hashCode() ?: 0),
            translate_target_ref[0],
        )

    fun stop_translation_watchdog() {
        translation_watchdog[0]?.cancel()
        translation_watchdog[0] = null
    }

    fun arm_translation_watchdog() {
        translation_watchdog[0]?.cancel()
        translation_watchdog[0] = translation_scope.launch {
            delay(org.astermail.android.translation.TRANSLATION_INACTIVITY_TIMEOUT_MS)
            if (current_translation_state[0] is TranslationBannerState.Translating) {
                set_translation_state[0]?.invoke(TranslationBannerState.Failed)
            }
        }
    }

    fun run_translation(from: String) {
        val source = source_body_ref[0] ?: return
        translation_from_ref[0] = from
        translation_scope.launch {
            val segments = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                extract_translatable_segments(source)
            }
            if (source_body_ref[0] !== source) return@launch
            val engine = translation_engine_ref[0] ?: return@launch
            val subject_text = translation_subject_ref[0]
            if (segments.isEmpty() && subject_text == null) {
                set_translation_state[0]?.invoke(TranslationBannerState.Hidden)
                return@launch
            }
            translation_subject_sent[0] = subject_text != null
            translation_source_ref[0] = source
            set_translation_state[0]?.invoke(TranslationBannerState.Translating)
            arm_translation_watchdog()
            engine.translate(
                if (subject_text != null) segments + subject_text else segments,
                from,
                translate_target_ref[0],
            )
        }
    }

    fun start_translation(from: String) {
        val to = translate_target_ref[0]
        when {
            !org.astermail.android.translation.TranslationRuntime.webview_supported(translate_context) ->
                set_translation_state[0]?.invoke(TranslationBannerState.WebViewOutdated)
            TranslationDownloadPolicy.route_download_blocked(translate_context, from, to) ->
                set_translation_state[0]?.invoke(TranslationBannerState.WifiRequired)
            else -> run_translation(from)
        }
    }

    fun show_original() {
        val kept = kept_translation[0]
        translated_body = null
        on_subject_translated_ref[0]?.invoke(null)
        set_translation_state[0]?.invoke(
            if (kept != null) TranslationBannerState.ShowingOriginal(kept.from) else TranslationBannerState.Hidden,
        )
    }

    fun show_translation() {
        val kept = kept_translation[0] ?: return
        translated_body = kept.body
        on_subject_translated_ref[0]?.invoke(kept.subject)
        set_translation_state[0]?.invoke(TranslationBannerState.Translated(kept.from))
    }

    fun handle_translation_detect(json: String) {
        val language = Regex("\"language\"\\s*:\\s*\"([a-z]{2})\"").find(json)?.groupValues?.get(1)
        val detected = json.contains("\"detected\":true") || json.contains("\"detected\": true")
        if (!detected || language == null) return
        if (language == translate_target_ref[0]) return
        if (translation_offer_memo.is_dismissed(translation_memo_key)) return
        translation_offer_memo.remember_language(translation_memo_key, language)
        if (translated_body != null || kept_translation[0] != null) return
        val mode = translate_mode_ref[0]
        if (mode == "off") return
        val granted = TranslationDownloadPolicy.route_consent_granted(
            translate_context,
            language,
            translate_target_ref[0],
        )
        if (!org.astermail.android.translation.TranslationRuntime.webview_supported(translate_context)) {
            set_translation_state[0]?.invoke(TranslationBannerState.WebViewOutdated)
        } else if (mode == "always" && granted && allow_auto_translate_ref[0]) {
            start_translation(language)
        } else {
            set_translation_state[0]?.invoke(TranslationBannerState.Offer(language, !granted))
        }
    }

    fun handle_translation_status(json: String) {
        val state = Regex("\"state\"\\s*:\\s*\"([a-z_]+)\"").find(json)?.groupValues?.get(1)
        val apply_state = set_translation_state[0]
        when (state) {
            "translating", "progress" -> {
                if (current_translation_state[0] !is TranslationBannerState.Translating) {
                    apply_state?.invoke(TranslationBannerState.Translating)
                }
                arm_translation_watchdog()
            }
            "translated", "empty" -> stop_translation_watchdog()
            "error" -> {
                stop_translation_watchdog()
                if (current_translation_state[0] !is TranslationBannerState.Translating) return
                val failed_from = translation_from_ref[0]
                val blocked = failed_from != null &&
                    TranslationDownloadPolicy.route_download_blocked(translate_context, failed_from, translate_target_ref[0])
                apply_state?.invoke(if (blocked) TranslationBannerState.WifiRequired else TranslationBannerState.Failed)
            }
            else -> Unit
        }
    }

    fun handle_translation_result(json: String) {
        stop_translation_watchdog()
        val all_segments = org.astermail.android.translation.translated_segments_of(json) ?: return
        val source = source_body_ref[0] ?: return
        if (translation_source_ref[0] !== source) return
        val from = translation_from_ref[0] ?: return
        val split = org.astermail.android.translation.split_subject_segment(all_segments, translation_subject_sent[0])
        translation_scope.launch {
            val applied = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                apply_translated_segments(source, split.body)
            }
            if (source_body_ref[0] !== source) return@launch
            val remembered = org.astermail.android.translation.remembered_translation(from, applied, split.subject)
            kept_translation[0] = remembered
            org.astermail.android.translation.TranslationMemory.put(translation_memory_key(source), remembered)
            translated_body = applied
            on_subject_translated_ref[0]?.invoke(split.subject)
            set_translation_state[0]?.invoke(TranslationBannerState.Translated(from))
            if (org.astermail.android.translation.translation_quality_limited(from)) {
                org.astermail.android.ui.common.app_toast.show(
                    translate_context.getString(R.string.translation_limited_quality),
                )
            }
        }
    }

    fun restore_remembered_translation(source: String): Boolean {
        val remembered = org.astermail.android.translation.TranslationMemory.get(translation_memory_key(source))
            ?: return false
        kept_translation[0] = remembered
        translated_body = remembered.body
        on_subject_translated_ref[0]?.invoke(remembered.subject)
        set_translation_state[0]?.invoke(TranslationBannerState.Translated(remembered.from))
        return true
    }

    DisposableEffect(translate_active_early) {
        if (translate_active_early && translation_engine_ref[0] == null) {
            translation_engine_ref[0] = runCatching {
                org.astermail.android.translation.TranslationEngineHost.shared.open(
                    translate_context,
                    on_detect = { json -> handle_translation_detect(json) },
                    on_status = { json -> handle_translation_status(json) },
                    on_result = { json -> handle_translation_result(json) },
                )
            }.getOrNull()
        }
        onDispose {
            stop_translation_watchdog()
            translation_engine_ref[0]?.close()
            translation_engine_ref[0] = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { on_subject_translated_ref[0]?.invoke(null) }
    }

    val inline_sig = remember(inline_images) { inline_images.keys.sorted().joinToString(",").hashCode() }
    val inline_refs = remember(html) { inline_cid_references(html) }
    val inline_complete = remember(inline_refs, inline_images) { inline_cids_resolved(inline_refs, inline_images) }
    var inline_wait_over by remember(html) { mutableStateOf(inline_refs.isEmpty()) }
    LaunchedEffect(html) {
        if (inline_wait_over) return@LaunchedEffect
        delay(INLINE_IMAGE_WAIT_MS)
        inline_wait_over = true
    }
    val inline_ready = inline_complete || inline_wait_over
    val html_hash = remember(html, inline_sig) { html.hashCode() * 31 + inline_sig }
    val base_height_key = remember(html_hash, allow_external, screen_width_dp, text_zoom, dyslexia_font, email_font_id, underline_links, is_dark, force_dark_emails) {
        body_height_key(
            html_cache.height_key(html_hash, allow_external, screen_width_dp, text_zoom),
            dyslexia_font = dyslexia_font,
            email_font_id = email_font_id,
            underline_links = underline_links,
            is_dark = is_dark,
            force_dark = force_dark_emails,
        )
    }
    val estimated_height = remember(html_hash, screen_width_dp) {
        if (html.isEmpty()) placeholder_body_height else estimated_body_height_dp(html, screen_width_dp).dp
    }
    val body_holder = local_body_view_holder.current
    val measure = remember(message_key, body_holder) {
        message_key?.let { key -> body_holder?.measure_for(key) } ?: body_measure()
    }
    remember(measure, base_height_key) {
        if (measure.height_key != base_height_key) {
            val previous_dp = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
                measure.view_dp.takeIf { it >= 1f } ?: measure.reserve_dp.takeIf { it > 1f }
            }
            measure.begin_key(
                base_height_key,
                seed_body_height(
                    body_height_cache.get(base_height_key),
                    previous_dp ?: estimated_height.value,
                ),
                android.os.SystemClock.uptimeMillis(),
            )
        }
        base_height_key
    }
    val alive = remember { booleanArrayOf(true) }
    DisposableEffect(Unit) {
        alive[0] = true
        onDispose { alive[0] = false }
    }
    val visual_ready = remember(measure) { mutableStateOf(false) }
    val load_tick = remember(measure) { mutableStateOf(0) }
    val layout_tick = remember(measure) { mutableStateOf(0) }
    val renderer_gone = remember { mutableStateOf(false) }
    var web_generation by remember { mutableStateOf(0) }
    val layout_density = LocalDensity.current.density
    val max_body_height_dp = remember(layout_density) { (max_body_layout_px / layout_density.coerceAtLeast(1f)).toInt() }
    val last_window_y = remember { floatArrayOf(Float.NaN) }
    val reload_policy = remember(measure) { body_reload_policy() }
    val web_view_context = androidx.compose.ui.platform.LocalContext.current
    val web_view_missing = remember(base_height_key) { !web_view_support.is_available(web_view_context) }
    val renderer_exhausted = remember(base_height_key) { mutableStateOf(web_view_missing) }
    val cacheable_load = remember(measure) { booleanArrayOf(true) }
    val white_page_ref = remember { booleanArrayOf(false) }
    var long_pressed_link by remember { mutableStateOf<String?>(null) }

    fun build_html(body: String): String = build_email_html(
        body = body,
        is_dark = is_dark,
        fg_hex = fg_hex,
        link_hex = link_hex,
        forwarded_label = forwarded_label,
        image_failed_label = image_failed_label,
        force_dark_emails = force_dark_emails,
        dyslexia_font = dyslexia_font,
        translate_mode = translate_mode,
        email_font_id = email_font_id,
        text_zoom = text_zoom,
        underline_links = underline_links,
        hide_quote_label = hide_quote_label_text,
        show_quote_label = show_quote_label_text,
    )

    val translate_active = translate_active_early
    val allow_external_ref = remember { booleanArrayOf(false) }
    allow_external_ref[0] = allow_external
    val cache_key = remember(html_hash, allow_external, bg_hex, screen_width_dp, force_dark_emails, translate_active, dyslexia_font, email_font_id, text_zoom, sanitize_options, underline_links) { ((((html_cache.key(html_hash, allow_external, bg_hex, screen_width_dp, force_dark_emails, translate_active) * 31L + (if (dyslexia_font) 1L else 0L)) * 31L + email_font_id.hashCode().toLong()) * 31L + text_zoom.toLong()) * 31L + sanitize_options.hashCode().toLong()) * 31L + (if (underline_links) 1L else 0L) }
    var prebuilt_html by remember(html_hash, allow_external, translate_active, dyslexia_font, email_font_id, text_zoom, sanitize_options, underline_links) { mutableStateOf<String?>(if (html.isEmpty()) null else html_cache.get(cache_key)) }
    val wants_white_page = remember(prebuilt_html) { prebuilt_html?.contains("data-white=\"1\"") == true }

    val search_session = if (search_key != null) org.astermail.android.ui.search.local_search_highlight.current else null
    val search_terms = search_session?.terms?.body.orEmpty()
    val search_reduce_motion = org.astermail.android.design.aster_reduce_motion()
    val search_accent = colors.accent_blue.toArgb()
    var search_marked by remember { mutableStateOf<Pair<String, org.astermail.android.ui.search.marked_search_body>?>(null) }
    LaunchedEffect(prebuilt_html, search_terms, is_dark, search_accent, search_reduce_motion) {
        val base = prebuilt_html
        if (base == null || search_terms.isEmpty() || search_session?.dismissed == true) {
            search_marked = null
            if (base != null && search_key != null) search_session?.report_count(search_key, 0)
            return@LaunchedEffect
        }
        val marked = withContext(Dispatchers.Default) {
            runCatching {
                org.astermail.android.ui.search.mark_search_hits(base, search_terms, search_accent, is_dark, search_reduce_motion)
            }.getOrNull()
        } ?: org.astermail.android.ui.search.marked_search_body(base, 0)
        search_marked = base to marked
        if (search_key != null) search_session?.report_count(search_key, marked.hits)
    }
    val search_current_marked = search_marked?.takeIf { it.first == prebuilt_html }?.second
    val shown_html: String? = when {
        search_terms.isEmpty() || search_session?.dismissed == true -> prebuilt_html
        search_current_marked != null -> search_current_marked.html
        else -> null
    }

    fun degrade_to_plain_text() {
        renderer_exhausted.value = true
        measure.probing = false
        measure.measured = true
    }

    val stale_content_dp = remember(measure) { floatArrayOf(0f) }

    fun read_body(web: mail_body_web_view, generation: Int) {
        if (!alive[0] || web.destroyed || renderer_exhausted.value) return
        try {
            val density = web.resources.displayMetrics.density.coerceAtLeast(0.1f)
            if (!body_width_measurable(web.width, density)) return
            @Suppress("DEPRECATION")
            val px_per_css = web.scale.takeIf { it > 0f } ?: density
            val content_dp = kotlin.math.round(web.contentHeight * px_per_css / density).toInt()
            val view_dp = kotlin.math.round(web.height / density).toInt()
            val probe_dp = MEASURE_PROBE_HEIGHT.value.toInt()
            val accepted = measure.accepts(generation) && measure.painted
            val judged = judge_body_reading(accepted, content_dp, view_dp, probe_dp, measure.view_is_known_height)
            val left_height = stale_content_dp[0]
            val verdict = if (judged == body_reading_verdict.grow && left_height >= 1f &&
                kotlin.math.abs(content_dp - left_height) <= BODY_HEIGHT_ROUNDING_CSS
            ) {
                body_reading_verdict.stale
            } else {
                judged
            }
            if (accepted && verdict != body_reading_verdict.stale) stale_content_dp[0] = 0f
            when (verdict) {
                body_reading_verdict.stale -> Unit
                body_reading_verdict.probe -> if (!measure.probing) {
                    web.forget_content_height()
                    measure.probing = true
                }
                body_reading_verdict.grow, body_reading_verdict.settle -> {
                    val exact = verdict == body_reading_verdict.grow || view_dp <= probe_dp + BODY_HEIGHT_SLACK_CSS
                    val height = if (exact) {
                        body_reading_height_dp(content_dp, max_body_height_dp)
                    } else {
                        measure.view_dp.coerceAtLeast(1f)
                    }
                    val reading_exact = body_reading_exact(verdict, view_dp, probe_dp)
                    measure.view_dp = height
                    measure.view_is_known_height = true
                    measure.last_exact = reading_exact
                    if (reading_exact) measure.exact_dp = height
                    measure.probing = false
                    measure.measured = true
                    measure.quote.on_exact_height(height)
                    if (cacheable_load[0] && !measure.quote.is_expanded && !measure.quote.has_open &&
                        body_height_cacheable(accepted, measure.painted, verdict, height) &&
                        body_height_trustworthy(reading_exact, visual_ready.value, height, measure.exact_dp)
                    ) {
                        body_height_cache.put(measure.height_key, height)
                    }
                }
            }
        } catch (_: Throwable) {
            degrade_to_plain_text()
        }
    }

    fun request_read(web: mail_body_web_view) {
        if (!alive[0] || web.destroyed) return
        val generation = measure.load_generation
        try {
            web.postVisualStateCallback(
                System.nanoTime(),
                object : android.webkit.WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (measure.accepts(generation)) read_body(web, generation)
                    }
                },
            )
        } catch (_: Throwable) {
            read_body(web, generation)
        }
    }

    fun load_body(web: mail_body_web_view, built: String, notify: Boolean = true, keep_quotes: Boolean = false) {
        if (web.destroyed) return
        measure.begin_load(android.os.SystemClock.uptimeMillis(), keep_height = measure.measured)
        if (!keep_quotes) measure.quote.reset()
        web.forget_content_height()
        measure.loaded_built = built
        measure.loaded_external = allow_external_ref[0]
        visual_ready.value = false
        if (notify) load_tick.value++
        val rendered = if (keep_quotes) with_open_quotes(built, measure.quote.open_quotes) else built
        web.loadDataWithBaseURL("https://mail-content.invalid/", rendered, "text/html", "UTF-8", null)
    }

    val pending_quote_collapse = remember(measure) { arrayOfNulls<pending_collapse>(1) }
    val quote_anchor_y = remember(measure) { floatArrayOf(Float.NaN) }
    val quote_anchor_until = remember(measure) { longArrayOf(0L) }
    val quote_anchor_generation = remember(measure) { intArrayOf(-1) }

    fun apply_pending_collapse() {
        val pending = pending_quote_collapse[0] ?: return
        pending_quote_collapse[0] = null
        if (!alive[0] || pending.generation != measure.load_generation) return
        val collapsed = pending.collapsed_dp
        if (collapsed != null && collapsed > 0f) {
            measure.view_dp = collapsed
            measure.view_is_known_height = true
        } else {
            measure.view_is_known_height = false
        }
        if (pending.shift_dp > 0f) {
            val key = message_key
            if (key != null) body_holder?.on_quote_collapse?.invoke(key, pending.shift_dp)
        }
    }

    LaunchedEffect(measure, base_height_key, measure.measured, measure.probing, visual_ready.value) {
        if (measure.settled) {
            on_ready()
            return@LaunchedEffect
        }
        if (!measure.measured || measure.probing) return@LaunchedEffect
        val wait_ms = body_settle_wait_ms(measure.trusted, visual_ready.value)
        if (wait_ms > 0L) delay(wait_ms)
        if (!measure.measured || measure.probing) return@LaunchedEffect
        measure.settled = true
        on_ready()
    }

    val proxy_base = REMOTE_IMAGE_PROXY_BASE

    fun proxy_html(raw: String): String {
        val cid_normalized = resolve_inline_cids(raw, inline_images)
        if (!allow_external) {
            val imgs_blocked = EmailHtmlSanitizer.replace_blocked_images(cid_normalized, blocked_image_labels)
            return EmailHtmlSanitizer.neutralize_blocked_backgrounds(imgs_blocked)
        }
        return proxy_external_urls(cid_normalized, proxy_base)
    }

    LaunchedEffect(html, inline_sig, inline_ready, allow_external, bg_hex, force_dark_emails, translate_active, dyslexia_font, email_font_id, text_zoom, sanitize_options, underline_links, translated_body) {
        if (html.isEmpty()) {
            prebuilt_html = null
            return@LaunchedEffect
        }
        if (!inline_ready && prebuilt_html == null) return@LaunchedEffect
        val override = translated_body
        if (override != null) {
            prebuilt_html = withContext(Dispatchers.Default) {
                runCatching { build_html(override) }
                    .getOrElse { plain_text_fallback_document(html, bg_hex, fg_hex) }
            }
            return@LaunchedEffect
        }
        val cached = html_cache.get(cache_key)
        if (cached != null) {
            prebuilt_html = cached
            if (needs_cached_translation_source(source_body_ref[0], translate_active)) {
                source_body_ref[0] = withContext(Dispatchers.Default) {
                    runCatching { proxy_html(sanitized_html_memo.sanitize(html, sanitize_options)) }.getOrNull()
                }
                source_body_version++
            }
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val sanitized = sanitized_html_memo.sanitize(html, sanitize_options)
                val source = proxy_html(sanitized)
                source_body_ref[0] = source
                build_html(source)
            }.getOrElse {
                runCatching { build_html(plain_text_fallback_body(html)) }
                    .getOrElse { plain_text_fallback_document(html, bg_hex, fg_hex) }
            }
        }
        html_cache.put(cache_key, result)
        prebuilt_html = result
    }

    LaunchedEffect(measure, measure.painted, measure.measured, measure.probing, web_generation, load_tick.value) {
        if (prebuilt_html == null || renderer_exhausted.value) return@LaunchedEffect
        if (measure.measured && !measure.probing) return@LaunchedEffect
        if (!measure.painted) {
            delay(BODY_PAINT_FALLBACK_MS)
            val web = web_ref[0] as? mail_body_web_view ?: return@LaunchedEffect
            if (!web.destroyed && web.contentHeight > 0) measure.painted = true
            return@LaunchedEffect
        }
        repeat(BODY_READ_FALLBACK_ATTEMPTS) {
            delay(BODY_READ_FALLBACK_STEP_MS)
            if (measure.measured && !measure.probing) return@LaunchedEffect
            val web = web_ref[0] as? mail_body_web_view ?: return@repeat
            read_body(web, measure.load_generation)
        }
        if (measure.measured && !measure.probing) return@LaunchedEffect
        val web = web_ref[0] as? mail_body_web_view
        val content_dp = try {
            if (web == null || web.destroyed) 0 else {
                val density = web.resources.displayMetrics.density.coerceAtLeast(0.1f)
                @Suppress("DEPRECATION")
                val px_per_css = web.scale.takeIf { it > 0f } ?: density
                kotlin.math.round(web.contentHeight * px_per_css / density).toInt()
            }
        } catch (_: Throwable) { 0 }
        val height = if (content_dp > 0) {
            body_reading_height_dp(content_dp, max_body_height_dp)
        } else {
            measure.view_dp.takeIf { it > 0f } ?: measure.reserve_dp
        }
        measure.view_dp = height
        measure.view_is_known_height = true
        measure.probing = false
        measure.measured = true
        measure.quote.on_exact_height(height)
    }

    LaunchedEffect(base_height_key, web_view_missing) {
        if (!web_view_missing) return@LaunchedEffect
        measure.painted = true
        measure.probing = false
        measure.measured = true
    }

    LaunchedEffect(renderer_gone.value) {
        if (!renderer_gone.value) return@LaunchedEffect
        renderer_gone.value = false
        measure.discard_view = true
        web_ref[0] = null
        when (reload_policy.on_renderer_gone()) {
            renderer_gone_action.stop -> {
                white_page_ref[0] = false
                degrade_to_plain_text()
                measure.painted = true
                return@LaunchedEffect
            }
            renderer_gone_action.plain_text_fallback -> {
                prebuilt_html = runCatching { build_html(plain_text_fallback_body(html)) }
                    .getOrElse { plain_text_fallback_document(html, bg_hex, fg_hex) }
            }
            renderer_gone_action.regenerate -> Unit
        }
        visual_ready.value = false
        measure.loaded_built = null
        web_generation++
    }

    LaunchedEffect(load_tick.value, web_generation) {
        if (load_tick.value == 0) return@LaunchedEffect
        reload_policy.begin_load()
        while (reload_policy.reloads_remaining()) {
            delay(2200)
            val web = web_ref[0] as? mail_body_web_view ?: return@LaunchedEffect
            if (web.destroyed) return@LaunchedEffect
            if (!reload_policy.should_reload(measure.painted, visual_ready.value, web.contentHeight)) return@LaunchedEffect
            val built = measure.loaded_built ?: return@LaunchedEffect
            load_body(web, built, notify = false)
        }
        delay(2600)
        val regen_web = web_ref[0] as? mail_body_web_view ?: return@LaunchedEffect
        if (regen_web.destroyed) return@LaunchedEffect
        if (reload_policy.should_regenerate(measure.painted, visual_ready.value, regen_web.contentHeight)) {
            measure.discard_view = true
            visual_ready.value = false
            measure.loaded_built = null
            web_ref[0] = null
            web_generation++
        }
    }

    if (search_key != null && search_session != null) {
        val search_reveal_body = search_current_marked
        val search_ready = visual_ready.value && measure.measured && !measure.probing &&
            (search_reveal_body?.hits ?: 0) > 0 && measure.loaded_built == search_reveal_body?.html
        DisposableEffect(search_key, search_session, search_ready, search_reveal_body) {
            if (search_ready && search_reveal_body != null) {
                search_session.register(search_key) { local ->
                    reveal_search_hit(web_ref, search_reveal_body.fraction_of(local))
                }
            }
            onDispose { search_session.unregister(search_key) }
        }
    }

    val translation_consent_version by TranslationDownloadPolicy.consent_version.collectAsStateWithLifecycle()
    LaunchedEffect(translation_consent_version) {
        val offer = translation_state as? TranslationBannerState.Offer ?: return@LaunchedEffect
        if (!offer.needs_download) return@LaunchedEffect
        if (TranslationDownloadPolicy.route_consent_granted(translate_context, offer.language, translate_target_ref[0])) {
            translation_state = TranslationBannerState.Offer(offer.language, false)
        }
    }

    LaunchedEffect(measure.measured, translate_mode, translate_accepted, prebuilt_html, source_body_version) {
        if (!measure.measured || translate_mode == "off") return@LaunchedEffect
        if (translated_body != null || kept_translation[0] != null) return@LaunchedEffect
        delay(150)
        val engine = translation_engine_ref[0] ?: return@LaunchedEffect
        val source = source_body_ref[0] ?: return@LaunchedEffect
        if (restore_remembered_translation(source)) return@LaunchedEffect
        val text = withContext(Dispatchers.Default) {
            runCatching { org.astermail.android.mail.html_to_plain_text(source) }.getOrDefault("")
        }
        if (text.isBlank()) return@LaunchedEffect
        engine.detect(org.astermail.android.translation.detection_text_of(text), translate_accepted)
    }

    val webview_client = remember(measure, body_holder) {
        object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                val scheme = request.url?.scheme?.lowercase() ?: return true
                when (scheme) {
                    "asterimg" -> {
                        val raw = url.substringAfter("asterimg:", "")
                        val decoded = try {
                            java.net.URLDecoder.decode(raw, "UTF-8")
                        } catch (_: Throwable) { "" }
                        if (is_zoomable_image_src(decoded)) on_image_click(decoded)
                        return true
                    }
                    "http", "https", "mailto", "tel", "sms", "aster" -> {
                        on_link_click(url)
                        return true
                    }
                    QUOTE_HIDE_SCHEME -> {
                        val target = view as? mail_body_web_view ?: return true
                        val built = measure.loaded_built ?: return true
                        if (target.destroyed) return true
                        val link = parse_quote_link(request.url?.schemeSpecificPart) ?: return true
                        main_handler.post {
                            if (!alive[0] || target.destroyed || measure.loaded_built != built) return@post
                            val quote = measure.quote
                            val changed = when {
                                link.action == QUOTE_OPEN_ACTION -> quote.set_open(link.index ?: return@post, true)
                                link.index != null -> quote.set_open(link.index, false)
                                else -> quote.close_all()
                            }
                            if (!changed) return@post
                            if (link.action != QUOTE_COVER_ACTION && !last_window_y[0].isNaN()) {
                                quote_anchor_y[0] = last_window_y[0]
                                quote_anchor_until[0] = android.os.SystemClock.uptimeMillis() + QUOTE_ANCHOR_WINDOW_MS
                            } else {
                                quote_anchor_y[0] = Float.NaN
                            }
                            if (link.action == QUOTE_OPEN_ACTION) {
                                pending_quote_collapse[0] = null
                                load_body(target, built, keep_quotes = true)
                                quote_anchor_generation[0] = measure.load_generation
                                return@post
                            }
                            val all_closed = !quote.has_open
                            val shift = if (all_closed) quote.on_collapse_requested() else 0f
                            val collapsed = if (all_closed) quote.collapsed_height_dp else null
                            stale_content_dp[0] = if (collapsed != null) measure.view_dp else 0f
                            load_body(target, built, keep_quotes = true)
                            quote_anchor_generation[0] = measure.load_generation
                            pending_quote_collapse[0] = pending_collapse(
                                generation = measure.load_generation,
                                collapsed_dp = collapsed,
                                shift_dp = if (link.action == QUOTE_COVER_ACTION) shift else 0f,
                            )
                        }
                        return true
                    }
                    "about" -> return false
                    else -> return true
                }
            }

            override fun onRenderProcessGone(
                view: android.webkit.WebView?,
                detail: android.webkit.RenderProcessGoneDetail?,
            ): Boolean {
                measure.discard_view = true
                renderer_gone.value = true
                return true
            }

            override fun onPageCommitVisible(view: android.webkit.WebView?, url: String?) {
                if (url == "about:blank") return
                view?.setBackgroundColor(
                    if (white_page_ref[0]) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT,
                )
                apply_pending_collapse()
                measure.painted = true
                (view as? mail_body_web_view)?.let { request_read(it) }
            }

            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                view?.setBackgroundColor(
                    if (white_page_ref[0]) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT,
                )
                if (url == "about:blank") return
                apply_pending_collapse()
                measure.painted = true
                val body = view as? mail_body_web_view ?: return
                if (body.destroyed) return
                val generation = measure.load_generation
                try {
                    body.postVisualStateCallback(
                        System.nanoTime(),
                        object : android.webkit.WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                if (!measure.accepts(generation)) return
                                visual_ready.value = true
                                read_body(body, generation)
                                if (!body.destroyed && measure.accepts(generation) && needs_confirm_probe(
                                        settled = measure.settled,
                                        trusted = measure.trusted,
                                        measured = measure.measured,
                                        probing = measure.probing,
                                        last_exact = measure.last_exact,
                                    )
                                ) {
                                    body.forget_content_height()
                                    measure.view_is_known_height = false
                                    measure.probing = true
                                }
                            }
                        },
                    )
                } catch (_: Throwable) {
                    read_body(body, generation)
                }
            }

            override fun shouldInterceptRequest(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
            ): android.webkit.WebResourceResponse? {
                val req_uri = request?.url ?: return null
                val url = req_uri.toString()
                if (req_uri.host == org.astermail.android.translation.TranslationAssets.CONTENT_HOST) {
                    if (InlineImageStore.key_for_path(req_uri.path) != null) {
                        return inline_image_response(req_uri.path)
                    }
                    val user_font_path = req_uri.path
                    if (user_font_path != null && user_font_path.startsWith(EMAIL_USER_FONT_PREFIX)) {
                        val user_font_ctx = view?.context
                        if (user_font_ctx != null) {
                            val user_font_response = email_user_font_response(user_font_ctx, user_font_path)
                            if (user_font_response != null) return user_font_response
                        }
                        return null
                    }
                    if (req_uri.path == EMAIL_FONT_PATH) {
                        val font_ctx = view?.context
                        if (font_ctx != null) {
                            val font_response = email_font_response(font_ctx)
                            if (font_response != null) return font_response
                        }
                        return null
                    }
                    return null
                }
                if (request.isForMainFrame) return null
                val request_action = mail_body_request_action(
                    scheme = req_uri.scheme,
                    host = req_uri.host,
                    path = req_uri.path,
                    proxied_url = try { req_uri.getQueryParameter("url") } catch (_: Throwable) { null },
                    allow_external = allow_external_ref[0],
                    port = req_uri.port,
                    user_info = req_uri.encodedUserInfo,
                )
                if (request_action == MailBodyRequestAction.LOCAL) return null
                if (request_action == MailBodyRequestAction.BLOCK) {
                    return android.webkit.WebResourceResponse(
                        "text/plain",
                        null,
                        403,
                        "Forbidden",
                        mapOf("Cache-Control" to "no-store"),
                        java.io.ByteArrayInputStream(ByteArray(0)),
                    )
                }
                fun image_response(
                    content_type: String,
                    stream: java.io.InputStream,
                ): android.webkit.WebResourceResponse =
                    android.webkit.WebResourceResponse(
                        content_type,
                        null,
                        200,
                        "OK",
                        mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"),
                        stream,
                    )
                fun transparent_pixel(): android.webkit.WebResourceResponse = failed_image_response()
                val cache_ctx = view?.context
                if (cache_ctx != null) {
                    remote_image_cache.lookup(cache_ctx, url)?.let { hit ->
                        return image_response(hit.content_type, java.io.ByteArrayInputStream(hit.bytes))
                    }
                }
                val current_token = settings_vm.get_access_token()
                if (current_token.isNullOrBlank()) return transparent_pixel()
                val ctx = cache_ctx ?: return transparent_pixel()
                val client = email_image_client(ctx)
                return try {
                    fun fetch_once(target: String, bearer: String): okhttp3.Response {
                        val req = okhttp3.Request.Builder()
                            .url(target)
                            .header("Authorization", "Bearer $bearer")
                            .build()
                        return client.newCall(req).execute()
                    }
                    fun fetch(bearer: String): okhttp3.Response {
                        var target = url
                        var attempt = fetch_once(target, bearer)
                        if (attempt.isRedirect) {
                            val next = proxy_redirect_target(target, attempt.header("Location"))
                            if (next != null) {
                                attempt.close()
                                target = next
                                attempt = fetch_once(target, bearer)
                            }
                        }
                        if (proxy_status_is_retryable(attempt.code)) {
                            attempt.close()
                            Thread.sleep(600L)
                            attempt = fetch_once(target, bearer)
                        }
                        return attempt
                    }
                    var resp = fetch(current_token)
                    if (resp.code == 401) {
                        resp.close()
                        val refreshed = settings_vm.refresh_access_token_blocking(current_token)
                            ?: settings_vm.get_access_token()?.takeIf { it != current_token }
                        if (refreshed.isNullOrBlank()) return transparent_pixel()
                        resp = fetch(refreshed)
                    }
                    if (!resp.isSuccessful) {
                        resp.close()
                        return transparent_pixel()
                    }
                    val body = resp.body
                    if (body == null) { resp.close(); return transparent_pixel() }
                    val content_type = resp.header("Content-Type")?.substringBefore(';')?.trim()
                        ?.takeIf { it.isNotBlank() } ?: "image/jpeg"
                    val source = remote_image_cache.caching_stream(
                        ctx,
                        url,
                        resp.code,
                        resp.header("Content-Type"),
                        body.byteStream(),
                    )
                    val stream = object : java.io.FilterInputStream(source) {
                        override fun read(): Int = as_io_failure { super.read() }
                        override fun read(b: ByteArray, off: Int, len: Int): Int = as_io_failure { super.read(b, off, len) }
                        override fun skip(n: Long): Long = as_io_failure { super.skip(n) }
                        override fun available(): Int = as_io_failure { super.available() }
                        override fun close() {
                            try { super.close() } finally { resp.close() }
                        }
                    }
                    image_response(content_type, stream)
                } catch (_: Throwable) { transparent_pixel() }
            }
        }
    }

    Column(modifier = modifier) {
      var last_visible_translation by remember(html) { mutableStateOf(translation_state) }
      if (translation_state !is TranslationBannerState.Hidden) last_visible_translation = translation_state
      androidx.compose.animation.AnimatedVisibility(
        visible = translation_state !is TranslationBannerState.Hidden,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
      ) {
        translation_banner(
          state = last_visible_translation,
          target_language = translate_target,
          on_translate = { lang ->
              val needs_download = (translation_state as? TranslationBannerState.Offer)?.needs_download == true
              TranslationDownloadPolicy.grant_route_consent(translate_context, lang, translate_target)
              val will_download = needs_download &&
                  org.astermail.android.translation.TranslationRuntime.webview_supported(translate_context) &&
                  !TranslationDownloadPolicy.route_download_blocked(translate_context, lang, translate_target)
              if (will_download) {
                  org.astermail.android.ui.common.app_toast.show(
                      translate_context.getString(
                          R.string.translation_bar_downloading,
                          org.astermail.android.translation.language_display_name(lang),
                      ),
                  )
              }
              start_translation(lang)
          },
          on_show_original = { show_original() },
          on_show_translation = { show_translation() },
          on_dismiss = {
              translation_offer_memo.dismiss(translation_memo_key)
              translation_state = TranslationBannerState.Hidden
          },
          on_update_webview = { org.astermail.android.translation.TranslationRuntime.open_webview_update(translate_context) },
        )
      }
      val view_dp = measure.view_dp
      val probing_now = measure.probing
      val measured_now = measure.measured
      val settled_now = measure.settled
      var skeleton_due by remember(measure) { mutableStateOf(!measure.settled) }
      LaunchedEffect(measure, settled_now, base_height_key) {
          if (settled_now) {
              skeleton_due = false
              return@LaunchedEffect
          }
          val waited = android.os.SystemClock.uptimeMillis() - measure.started_ms
          delay((BODY_SKELETON_DELAY_MS - waited).coerceAtLeast(0L))
          skeleton_due = show_body_skeleton(measure.settled, android.os.SystemClock.uptimeMillis() - measure.started_ms)
      }
      val hold_dp = when {
          renderer_exhausted.value -> 0f
          !measured_now -> maxOf(measure.reserve_dp, view_dp)
          probing_now -> view_dp
          else -> 0f
      }
      val web_height = if (probing_now || view_dp < 1f) MEASURE_PROBE_HEIGHT else view_dp.dp
      Box(
          modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = hold_dp.coerceAtLeast(0f).dp)
              .clipToBounds(),
          contentAlignment = Alignment.TopStart,
      ) {
        if (white_page_ref[0] && measured_now) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(androidx.compose.ui.graphics.Color.White),
            )
        }
        if (renderer_exhausted.value) {
            val fallback_text = remember(html) {
                runCatching { org.astermail.android.mail.html_to_plain_text(html) }.getOrDefault("")
            }
            val fallback_color = remember(fg_hex) {
                runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(fg_hex)) }
                    .getOrDefault(androidx.compose.ui.graphics.Color.Unspecified)
            }
            androidx.compose.foundation.text.selection.SelectionContainer(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = fallback_text,
                    color = fallback_color,
                    fontSize = (15f * text_zoom / 100f).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        } else if (!webview_usable) {
            org.astermail.android.ui.common.webview_unavailable_notice()
        } else androidx.compose.runtime.key(web_generation) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                val reused = message_key?.let { key -> body_holder?.take_view(key, measure) }
                val created = reused ?: (body_web_view_pool.take(ctx) ?: mail_body_web_view(ctx)).also {
                    measure.loaded_built = null
                    measure.discard_view = false
                }
                created.apply {
                    configure_mail_body_web_view(this, text_zoom, allow_external)
                    on_width_change = {
                        if (alive[0] && !destroyed) {
                            layout_tick.value++
                            measure.view_is_known_height = false
                            measure.quote.on_layout_changed()
                            forget_content_height()
                            request_read(this)
                        }
                    }
                    on_height_change = {
                        if (alive[0] && !destroyed) request_read(this)
                    }
                    on_content_height = { _ ->
                        if (alive[0] && !destroyed) read_body(this, measure.load_generation)
                    }
                    on_horizontal_scroll = { offset, extent, range -> pan_signal?.report(offset, extent, range) }
                    var touch_down_x = 0f
                    var touch_down_y = 0f
                    var multi_touch = false
                    var pan_claimed = false
                    val tap_slop_px = android.view.ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
                    setOnTouchListener { v, ev ->
                        val body = v as? mail_body_web_view
                        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                            body?.end_selection_gesture()
                        }
                        if (body?.selection_gesture == true) {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            return@setOnTouchListener false
                        }
                        when (ev.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                touch_down_x = ev.x
                                touch_down_y = ev.y
                                multi_touch = false
                                pan_claimed = false
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                                multi_touch = true
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                if (ev.pointerCount > 1) {
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                } else {
                                    val dx = Math.abs(ev.x - touch_down_x)
                                    val dy = Math.abs(ev.y - touch_down_y)
                                    if (!pan_claimed && dx > dy && dx > 8f) {
                                        pan_claimed = body_claims_horizontal_pan(
                                            drag_dx = ev.x - touch_down_x,
                                            can_scroll_forward = v.canScrollHorizontally(1),
                                            can_scroll_back = v.canScrollHorizontally(-1),
                                        )
                                    }
                                    if (pan_claimed) {
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    } else if (dy >= dx && dy > 8f && body?.keeps_vertical_drag(ev.y - touch_down_y) == true) {
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    } else {
                                        v.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                            }
                        }
                        false
                    }
                    setOnLongClickListener {
                        val hit = hitTestResult
                        fun is_copyable_link(href: String?): Boolean =
                            href != null && (
                                href.startsWith("http://") ||
                                    href.startsWith("https://") ||
                                    href.startsWith("mailto:", ignoreCase = true)
                                )
                        when (hit.type) {
                            android.webkit.WebView.HitTestResult.EMAIL_TYPE -> {
                                val address = hit.extra
                                if (!address.isNullOrBlank()) {
                                    long_pressed_link = "mailto:$address"
                                    true
                                } else {
                                    false
                                }
                            }
                            android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                val href = hit.extra
                                if (is_copyable_link(href)) {
                                    long_pressed_link = href
                                    true
                                } else {
                                    false
                                }
                            }
                            android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                val handler = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
                                    val href = msg.data.getString("url")
                                    if (is_copyable_link(href)) long_pressed_link = href
                                    true
                                }
                                requestFocusNodeHref(handler.obtainMessage())
                                true
                            }
                            else -> false
                        }
                    }
                    webViewClient = webview_client
                    web_ref[0] = this
                    if (reused != null) {
                        forget_content_height()
                        if (progress >= 100 && contentHeight > 0) measure.painted = true
                    }
                    message_key?.let { key -> body_holder?.keep_view(key, measure, this) }
                }
            },
            update = { web_view ->
                layout_tick.value
                web_ref[0] = web_view
                val built = shown_html ?: return@AndroidView
                web_view.settings.textZoom = text_zoom
                web_view.settings.loadsImagesAutomatically = true
                web_view.settings.blockNetworkImage = false
                web_view.settings.mixedContentMode =
                    android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (web_view.destroyed) return@AndroidView
                if (measure.loaded_built == built && measure.loaded_external == allow_external) return@AndroidView
                if (!body_width_measurable(web_view.width, web_view.resources.displayMetrics.density)) return@AndroidView
                val previous = measure.loaded_built
                if (previous != null && previous != built) {
                    measure.view_is_known_height = false
                    measure.quote.reset()
                }
                white_page_ref[0] = wants_white_page
                web_view.setBackgroundColor(
                    if (wants_white_page) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT,
                )
                cacheable_load[0] = translated_body == null
                load_body(web_view, built)
            },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .height(web_height)
                .clipToBounds()
                .then(contain_body_bring_into_view)
                .onGloballyPositioned { coords ->
                    if (!coords.isAttached) return@onGloballyPositioned
                    val window_y = coords.positionInWindow().y
                    val anchor_y = quote_anchor_y[0]
                    if (!anchor_y.isNaN()) {
                        if (android.os.SystemClock.uptimeMillis() > quote_anchor_until[0] ||
                            quote_anchor_generation[0] != measure.load_generation
                        ) {
                            quote_anchor_y[0] = Float.NaN
                        } else if (measure.painted && !measure.probing) {
                            val correction = quote_anchor_correction(anchor_y, window_y)
                            if (correction != 0f && body_holder?.on_quote_anchor?.invoke(correction) != true) {
                                quote_anchor_y[0] = Float.NaN
                            }
                        }
                    }
                    val previous_y = last_window_y[0]
                    if (previous_y.isNaN() || kotlin.math.abs(window_y - previous_y) >= layout_density * BODY_REPAINT_STEP_DP) {
                        last_window_y[0] = window_y
                        web_ref[0]?.postInvalidateOnAnimation()
                    }
                },
            onRelease = { web_view ->
                if (web_ref[0] === web_view) web_ref[0] = null
                val key = message_key
                if (body_holder == null || key == null || !body_holder.park(key, web_view)) {
                    release_body_view_later(web_view)
                }
            },
        )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = !renderer_exhausted.value && skeleton_due && !settled_now,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = androidx.compose.animation.ExitTransition.None,
            modifier = Modifier
                .matchParentSize()
                .align(Alignment.TopStart),
        ) {
            email_body_skeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (colors.is_translucent) Color.Transparent else inbox_card_read_color(colors)),
            )
        }
      }
    }

    long_pressed_link?.let { link ->
        link_options_sheet(url = link, on_close = { long_pressed_link = null })
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun link_options_sheet(
    url: String,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val address = remember(url) {
        if (url.startsWith("mailto:", ignoreCase = true)) {
            android.net.Uri.decode(url.substring("mailto:".length).substringBefore('?')).trim()
        } else {
            null
        }
    }
    val shown = address ?: url
    val copied_label = stringResource(if (address != null) R.string.email_copied else R.string.link_copied)
    val state = rememberModalBottomSheetState()
    aster_modal_sheet(
        shape = org.astermail.android.ui.common.aster_sheet_shape,
        on_dismiss_request = on_close,
        sheet_state = state,
        container_color = sheet_container_color(colors),
        drag_handle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text(
                text = shown,
                color = colors.text_secondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = AsterSpacing.xl, vertical = AsterSpacing.xs),
            )
            AsterDivider()
            val copy_label = stringResource(if (address != null) R.string.copy_address else R.string.copy_link)
            sheet_row(copy_label, colors.text_primary) {
                val clip = android.content.ClipData.newPlainText(if (address != null) "address" else "link", shown)
                if (write_to_clipboard(context, clip)) {
                    app_toast.show(copied_label)
                } else {
                    show_copy_failed_toast(context)
                }
                on_close()
            }
            if (address == null) {
                sheet_row(stringResource(R.string.share_link), colors.text_primary) {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, url)
                    }
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
                    on_close()
                }
            }
            Spacer(Modifier.height(AsterSpacing.sm))
        }
    }
}

@Composable
private fun attachment_section_placeholder() {
    val state = org.astermail.android.design.components.shimmer_state()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .testTag("attachments_loading")
            .skeleton_reveal(),
    ) {
        Spacer(Modifier.height(AsterSpacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .height(14.dp)
                .shimmer(state, shape),
        )
        Spacer(Modifier.height(AsterSpacing.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shimmer(state, AsterShapes.island_lg),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun attachment_section(
    attachments: List<MessageAttachment>,
    on_tap: (MessageAttachment) -> Unit,
    on_download: (MessageAttachment) -> Unit,
    on_options: (MessageAttachment) -> Unit = {},
) {
    val colors = AsterMaterial.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md),
    ) {
        Spacer(Modifier.height(AsterSpacing.sm))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = TablerIcons.Paperclip,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = pluralStringResource(R.plurals.attachments_count, attachments.size, attachments.size),
                color = colors.text_secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            val total_bytes = remember(attachments) { attachments.sumOf { it.size_bytes.coerceAtLeast(0L) } }
            if (total_bytes > 0) {
                val size_ctx = LocalContext.current
                Text(
                    text = android.text.format.Formatter.formatShortFileSize(size_ctx, total_bytes),
                    color = colors.text_muted,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("attachments_total_size"),
                )
            }
            val any_unverified = remember(attachments) { attachments.any { it.is_unverified } }
            if (any_unverified) {
                Icon(
                    imageVector = TablerIcons.AlertTriangle,
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.attachment_unverified),
                    color = colors.warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("attachments_unverified"),
                )
            }
        }

        Spacer(Modifier.height(AsterSpacing.sm))

        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = attachment_grid_columns(maxWidth.value)
            Column(modifier = Modifier.fillMaxWidth()) {
                attachments.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { att ->
                            attachment_chip(
                                attachment = att,
                                on_tap = { on_tap(att) },
                                on_download = { on_download(att) },
                                on_long_press = { on_options(att) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

internal fun attachment_grid_columns(width_dp: Float): Int = if (width_dp >= 560f) 2 else 1

@Composable
private fun attachment_chip(
    attachment: MessageAttachment,
    on_tap: () -> Unit,
    on_download: () -> Unit,
    modifier: Modifier = Modifier,
    on_long_press: () -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val type_color = attachment_type_color(attachment.content_type)
    val pending = attachment.is_pending_slot()

    Row(
        modifier = modifier
            .acrylic(colors, AsterShapes.island_lg, field_surface_color(colors))
            .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(enabled = !pending, onClick = on_tap, onLongClick = on_long_press),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = attachment_icon(attachment.content_type),
                contentDescription = null,
                tint = if (pending) colors.text_muted else type_color,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (pending && attachment.filename.isBlank()) {
                    val shimmer = org.astermail.android.design.components.shimmer_state()
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .fillMaxWidth(0.6f)
                            .height(12.dp)
                            .shimmer(shimmer, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                    )
                } else Text(
                    text = display_filename(attachment.filename),
                    color = colors.text_primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    style = androidx.compose.material3.LocalTextStyle.current.copy(
                        textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                    ),
                )
                if (attachment.size_bytes > 0) {
                    val size_ctx = LocalContext.current
                    Text(
                        text = android.text.format.Formatter.formatShortFileSize(size_ctx, attachment.size_bytes),
                        color = colors.text_muted,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(enabled = !pending, onClick = on_download),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TablerIcons.Download,
                contentDescription = stringResource(R.string.download),
                tint = if (pending) colors.text_muted.copy(alpha = 0.4f) else colors.text_secondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun attachment_icon(content_type: String): ImageVector {
    return when {
        content_type.startsWith("image/") -> TablerIcons.Photo
        content_type.startsWith("video/") -> TablerIcons.Video
        content_type.startsWith("audio/") -> TablerIcons.Music
        content_type.contains("pdf") -> TablerIcons.FileText
        content_type.contains("zip") || content_type.contains("gzip") ||
            content_type.contains("tar") || content_type.contains("rar") ||
            content_type.contains("7z") -> TablerIcons.FileZip
        content_type.contains("html") || content_type.contains("xml") ||
            content_type.contains("json") || content_type.contains("javascript") -> TablerIcons.Code
        content_type.startsWith("text/") || content_type.contains("document") ||
            content_type.contains("msword") || content_type.contains("spreadsheet") ||
            content_type.contains("presentation") -> TablerIcons.FileText
        else -> TablerIcons.File
    }
}

private fun attachment_type_color(content_type: String): Color {
    return when {
        content_type == "application/pdf" -> Color(0xFFEA4335)
        content_type.startsWith("image/") -> Color(0xFFA855F7)
        content_type.startsWith("video/") -> Color(0xFFEC4899)
        content_type.startsWith("audio/") -> Color(0xFF0EA5E9)
        content_type.contains("spreadsheet") || content_type.contains("excel") ||
            content_type == "text/csv" -> Color(0xFF34A853)
        content_type.contains("presentation") || content_type.contains("powerpoint") -> Color(0xFFF97316)
        content_type.contains("word") || content_type.contains("document") -> Color(0xFF4285F4)
        content_type.contains("zip") || content_type.contains("gzip") ||
            content_type.contains("tar") -> Color(0xFF8B5CF6)
        content_type.startsWith("text/") -> Color(0xFF3B82F6)
        else -> Color(0xFF6B7280)
    }
}

private fun attachment_type_label(content_type: String, filename: String): String {
    val known = mapOf(
        "application/pdf" to "PDF",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "DOCX",
        "application/msword" to "DOC",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "XLSX",
        "application/vnd.ms-excel" to "XLS",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "PPTX",
        "application/vnd.ms-powerpoint" to "PPT",
        "application/json" to "JSON",
        "application/xml" to "XML",
    )
    known[content_type]?.let { return it }
    if (content_type.startsWith("text/")) return "TXT"
    if (content_type.contains("zip") || content_type.contains("compressed")) return "ZIP"
    val ext = filename.substringAfterLast('.', "")
    return if (ext.isNotBlank()) ext.uppercase() else "FILE"
}

internal fun format_file_size(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}

private val BIDI_CONTROL_CHARACTERS =
    Regex("[\\u202a-\\u202e\\u2066-\\u2069\\u200e\\u200f\\u061c]")

internal fun strip_bidi_controls(raw: String): String = BIDI_CONTROL_CHARACTERS.replace(raw, "")

internal fun display_filename(raw: String): String = strip_bidi_controls(raw)

internal fun sanitize_filename(raw: String): String {
    val without_bidi = strip_bidi_controls(raw)
    val base = without_bidi.substringAfterLast('/').substringAfterLast('\\')
    val cleaned = base.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f\\x7f-\\x9f\\u2028\\u2029]"), "_").trim().trimStart('.')
    return fit_attachment_name(cleaned.ifBlank { "attachment" })
}

private fun open_attachment_externally(
    context: android.content.Context,
    filename: String,
    content_type: String,
    bytes: ByteArray,
): Boolean {
    val mime = safe_view_mime(filename, content_type)
    val safe_name = sanitize_filename(filename)
    val target = org.astermail.android.share.write_opened_attachment(context.cacheDir, safe_name, bytes)
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        target,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        clipData = android.content.ClipData.newRawUri(safe_name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        runCatching { target.parentFile?.deleteRecursively() }
        throw e
    }
    return true
}

private fun safe_view_mime(filename: String, declared: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    val resolved = if (ext.isNotBlank()) {
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    } else {
        null
    }
    val mime = resolved ?: declared.ifBlank { "application/octet-stream" }
    val blocked = setOf(
        "application/vnd.android.package-archive",
        "application/x-msdownload",
        "application/x-executable",
        "application/x-elf",
        "application/x-sh",
    )
    return if (mime.lowercase() in blocked) "application/octet-stream" else mime
}

private data class saved_attachment(val uri: android.net.Uri, val mime: String)

private data class post_quantum_reaction(
    val message_id: String,
    val emoji: String,
    val coverage: org.astermail.android.mail.ratchet.PostQuantumCoverage,
)

private suspend fun save_attachment_to_storage(
    context: android.content.Context,
    attachment: MessageAttachment,
    bytes: ByteArray,
): saved_attachment? = withContext(Dispatchers.IO) {
    try {
        val safe_name = sanitize_filename(attachment.filename)
        val mime = safe_view_mime(safe_name, attachment.content_type)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe_name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
                show_download_notification(context, safe_name, uri, mime)
                saved_attachment(uri, mime)
            } else {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = write_download_file(dir, safe_name, bytes) ?: return@withContext null
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
            show_download_notification(context, file.name, uri, mime)
            saved_attachment(uri, mime)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}

private fun open_saved_attachment(context: android.content.Context, saved: saved_attachment): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(saved.uri, saved.mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun open_downloads_folder(context: android.content.Context): Boolean {
    val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun share_attachment(
    context: android.content.Context,
    filename: String,
    content_type: String,
    bytes: ByteArray,
): Boolean {
    val safe_name = sanitize_filename(filename)
    val mime = safe_view_mime(safe_name, content_type)
    val uri = runCatching {
        val target = write_share_file(context.cacheDir, safe_name, bytes)
        androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
    }.getOrNull() ?: return false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, null).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(chooser) }.isSuccess
}

private fun save_as_intent(filename: String, content_type: String): Intent {
    val safe_name = sanitize_filename(filename)
    return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = safe_view_mime(safe_name, content_type)
        putExtra(Intent.EXTRA_TITLE, safe_name)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun attachment_options_sheet(
    attachment: MessageAttachment,
    on_close: () -> Unit,
    on_open: () -> Unit,
    on_open_downloads: () -> Unit,
    on_share: () -> Unit,
    on_save_as: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    aster_modal_sheet(
        shape = org.astermail.android.ui.common.aster_sheet_shape,
        on_dismiss_request = on_close,
        sheet_state = state,
        container_color = sheet_container_color(colors),
        drag_handle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text(
                text = display_filename(attachment.filename),
                color = colors.text_secondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.LocalTextStyle.current.copy(
                    textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                ),
                modifier = Modifier.padding(horizontal = AsterSpacing.xl, vertical = AsterSpacing.xs),
            )
            AsterDivider()
            sheet_row(stringResource(R.string.open), colors.text_primary, TablerIcons.ExternalLink) {
                on_close()
                on_open()
            }
            sheet_row(stringResource(R.string.open_downloads_folder), colors.text_primary, TablerIcons.Folder) {
                on_close()
                on_open_downloads()
            }
            sheet_row(stringResource(R.string.share_attachment), colors.text_primary, TablerIcons.Share) {
                on_close()
                on_share()
            }
            sheet_row(stringResource(R.string.save_as), colors.text_primary, TablerIcons.Download) {
                on_close()
                on_save_as()
            }
            Spacer(Modifier.height(AsterSpacing.sm))
        }
    }
}

private fun show_download_notification(
    context: android.content.Context,
    filename: String,
    uri: android.net.Uri,
    mime: String,
) {
    try {
        val channel_id = "downloads"
        val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channel_id,
                context.getString(R.string.notif_channel_downloads_name),
                android.app.NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }
        val view_intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val open_intent = if (view_intent.resolveActivity(context.packageManager) != null) {
            view_intent
        } else {
            android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val pending = android.app.PendingIntent.getActivity(
            context, filename.hashCode(), open_intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public_version = androidx.core.app.NotificationCompat.Builder(context, channel_id)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.download_complete))
            .build()
        val app_lock_configured = runCatching {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                org.astermail.android.notifications.MailPollingWorker.MailRepositoryEntryPoint::class.java,
            ).app_lock_store().hides_notification_content()
        }.getOrDefault(true)
        val content_hidden = org.astermail.android.security.LockdownStore.is_enabled(context) || app_lock_configured
        val notification = androidx.core.app.NotificationCompat.Builder(context, channel_id)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.download_complete))
            .setContentText(if (content_hidden) null else filename)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public_version)
            .build()
        nm.notify(filename.hashCode(), notification)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
    }
}

@Composable
private fun attachment_preview_dialog(
    attachment: MessageAttachment,
    bytes: ByteArray,
    on_close: () -> Unit,
    on_download: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val ct = attachment.content_type.lowercase()
    val context = LocalContext.current
    val open_externally = {
        try {
            val opened = open_attachment_externally(
                context,
                attachment.filename,
                attachment.content_type,
                bytes,
            )
            if (!opened) {
                app_toast.show(context.getString(R.string.no_app_to_open))
            }
        } catch (_: Throwable) {
            app_toast.show(context.getString(R.string.no_app_to_open))
        }
    }
    val share = {
        if (!share_attachment(context, attachment.filename, attachment.content_type, bytes)) {
            app_toast.show(context.getString(R.string.no_app_to_open))
        }
    }

    BackHandler { on_close() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .systemBarsPadding()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                attachment_viewer_button(TablerIcons.X, stringResource(R.string.close), "attachment_viewer_close", on_close)
                Text(
                    text = display_filename(attachment.filename),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                attachment_viewer_button(
                    TablerIcons.Share,
                    stringResource(R.string.share_attachment),
                    "attachment_viewer_share",
                    share,
                )
                attachment_viewer_button(
                    TablerIcons.Download,
                    stringResource(R.string.download),
                    "attachment_viewer_download",
                    on_download,
                )
                attachment_viewer_button(
                    TablerIcons.ExternalLink,
                    stringResource(R.string.open_with),
                    "attachment_viewer_open",
                    open_externally,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    ct.startsWith("image/") -> image_attachment_viewer(
                        bytes = bytes,
                        description = display_filename(attachment.filename),
                    ) { message ->
                        attachment_fallback_panel(attachment, ct, bytes, on_download, message)
                    }
                    is_text_attachment(ct, attachment.filename) -> text_attachment_viewer(bytes)
                    is_pdf_attachment(ct, attachment.filename) -> pdf_attachment_viewer(
                        bytes = bytes,
                        filename = attachment.filename,
                    ) { message ->
                        attachment_fallback_panel(attachment, ct, bytes, on_download, message)
                    }
                    else -> attachment_fallback_panel(attachment, ct, bytes, on_download)
                }
            }
        }
    }
}

@Composable
internal fun attachment_viewer_button(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    test_tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .testTag(test_tag)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun attachment_fallback_panel(
    attachment: MessageAttachment,
    ct: String,
    bytes: ByteArray,
    on_download: () -> Unit,
    message: String? = null,
) {
    val context = LocalContext.current
    val type_color = attachment_type_color(ct)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = attachment_icon(ct),
            contentDescription = null,
            tint = type_color,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = display_filename(attachment.filename),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
            style = androidx.compose.material3.LocalTextStyle.current.copy(
                textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${attachment_type_label(ct, attachment.filename)} - ${format_file_size(bytes.size.toLong())}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
        )
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            pdf_fallback_message(message)
        }
        Spacer(Modifier.height(32.dp))
        val lightbox_button_fill = lerp(Color.Black, Color.White, 0.16f)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(AsterShapes.control)
                    .background(lightbox_button_fill)
                    .clickable(onClick = on_download)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.download), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .clip(AsterShapes.control)
                    .background(lightbox_button_fill)
                    .clickable {
                        try {
                            val opened = open_attachment_externally(
                                context,
                                attachment.filename,
                                attachment.content_type,
                                bytes,
                            )
                            if (!opened) {
                                app_toast.show(context.getString(R.string.no_app_to_open))
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            app_toast.show(context.getString(R.string.no_app_to_open))
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.open_with), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun Modifier.kept_offscreen(): Modifier =
    this
        .clearAndSetSemantics {}
        .layout { _, _ -> layout(0, 0) {} }

@Composable
private fun androidx.compose.foundation.layout.RowScope.bottom_action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    test_tag: String? = null,
    onClick: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(SquircleShape(18.dp))
            .clickable(onClick = onClick)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        org.astermail.android.design.AsterIconSwap(target_state = icon, label = "bottom_action_icon") { shown_icon ->
            Icon(
                imageVector = shown_icon,
                contentDescription = label,
                tint = colors.text_primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun detail_menu_action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    test_tag: String? = null,
    onClick: () -> Unit,
) {
    aster_menu_item(
        label = text,
        icon = icon,
        tint = tint,
        test_tag = test_tag,
        on_click = onClick,
    )
}

@Composable
private fun phishing_banner(result: org.astermail.android.security.PhishingResult) {
    val colors = AsterMaterial.colors
    val is_dangerous = result.level == org.astermail.android.security.PhishingLevel.dangerous
    val bg = if (is_dangerous) colors.danger else tonal_surface_color(colors, colors.warning)
    val tint = if (is_dangerous) Color.White else colors.warning
    val text_color = if (is_dangerous) Color.White else colors.text_primary
    val sub_text_color = if (is_dangerous) Color.White.copy(alpha = 0.85f) else colors.text_secondary
    val title = if (is_dangerous) stringResource(R.string.phishing_dangerous_title) else stringResource(R.string.phishing_warning_title)
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.xs)
            .clip(AsterShapes.island)
            .background(bg)
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = TablerIcons.AlertCircle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = text_color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(if (expanded) R.string.phishing_hide_details else R.string.phishing_show_details),
                color = text_color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.phishing_privacy_note),
                color = sub_text_color,
                fontSize = 12.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.phishing_signals_heading),
                color = text_color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            for (signal in result.signals) {
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(text = "•", color = text_color, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(signal.description_res, *signal.description_args.toTypedArray()),
                        color = sub_text_color,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun detail_menu_divider() {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(Dp.Hairline)
            .background(colors.border_secondary),
    )
}

@Composable
internal fun encryption_badge_label(is_encrypted: Boolean, is_pgp: Boolean = false): String =
    if (is_encrypted && is_pgp)
        stringResource(R.string.encrypted_pgp)
    else if (is_encrypted)
        stringResource(R.string.end_to_end_encrypted)
    else
        stringResource(R.string.protected_in_transit)

@Composable
internal fun pgp_signature_label(status: org.astermail.android.crypto.PgpSignatureStatus): String =
    when (status) {
        org.astermail.android.crypto.PgpSignatureStatus.NONE ->
            stringResource(R.string.pgp_signature_none)
        org.astermail.android.crypto.PgpSignatureStatus.UNVERIFIED ->
            stringResource(R.string.pgp_signature_unverified)
        org.astermail.android.crypto.PgpSignatureStatus.VALID ->
            stringResource(R.string.pgp_signature_valid)
        org.astermail.android.crypto.PgpSignatureStatus.INVALID ->
            stringResource(R.string.pgp_signature_invalid)
    }

@Suppress("DEPRECATION")
internal fun trim_level_evicts_bodies(level: Int): Boolean =
    level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
        level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
        level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND

internal fun metadata_shows_transit_only(item: org.astermail.android.api.mail.MailItem): Boolean =
    item.is_external && item.has_recipient_key != true && item.item_type != "sent" && item.item_type != "scheduled" &&
        item.item_type != "draft"

internal fun thread_is_end_to_end_encrypted(messages: List<ThreadMessage>): Boolean =
    messages.isNotEmpty() && messages.all { it.is_e2e_encrypted }

internal fun thread_is_pgp_encrypted(messages: List<ThreadMessage>): Boolean =
    messages.isNotEmpty() &&
        messages.any { it.shows_pgp_label } &&
        messages.all { it.is_e2e_encrypted }

internal fun thread_reaches_outside_with_pgp(messages: List<ThreadMessage>): Boolean =
    thread_is_pgp_encrypted(messages) &&
        messages.any { it.reaches_outside_with_pgp } &&
        messages.none { it.pgp_encrypted }

@Composable
internal fun encryption_info_body(is_encrypted: Boolean, is_pgp: Boolean = false, is_outside: Boolean = false) {
    val colors = AsterMaterial.colors
    Text(
        text = if (is_encrypted && is_pgp && is_outside)
            stringResource(R.string.recipient_published_key_description)
        else if (is_encrypted && is_pgp)
            stringResource(R.string.pgp_recipient_description)
        else if (is_encrypted)
            stringResource(R.string.e2e_recipient_description)
        else
            stringResource(R.string.transit_recipient_description),
        color = colors.text_secondary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun auth_result_label(result: org.astermail.android.security.EmailAuthCheckResult): String =
    when (result.status) {
        org.astermail.android.security.EmailAuthStatus.pass -> stringResource(R.string.auth_result_pass)
        org.astermail.android.security.EmailAuthStatus.fail -> stringResource(R.string.auth_result_fail)
        org.astermail.android.security.EmailAuthStatus.other -> result.value
        else -> stringResource(R.string.auth_result_missing)
    }

@Composable
private fun identity_changed_banner(sender: String, on_acknowledge: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .padding(bottom = AsterSpacing.sm)
            .clip(AsterShapes.island)
            .background(tonal_surface_color(colors, colors.danger))
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = TablerIcons.ShieldLock,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Column {
            Text(
                text = stringResource(R.string.identity_changed_title),
                color = colors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.identity_changed_description, sender),
                color = colors.text_secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.identity_changed_dismiss),
                color = colors.danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(SquircleShape(10.dp))
                    .clickable { on_acknowledge() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun send_failure_banner(reason: String?) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .padding(bottom = AsterSpacing.sm)
            .clip(AsterShapes.island)
            .background(tonal_surface_color(colors, colors.danger))
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = TablerIcons.AlertTriangle,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Column {
            Text(
                text = stringResource(R.string.send_failed_title),
                color = colors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.send_failed_help),
                color = colors.text_secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            val detail = org.astermail.android.send_failure_detail_res(reason)?.let { stringResource(it) }
                ?: reason?.trim().orEmpty()
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    color = colors.text_secondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun sender_unverified_banner(sender: String) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .padding(bottom = AsterSpacing.sm)
            .clip(AsterShapes.island)
            .background(tonal_surface_color(colors, colors.danger))
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = TablerIcons.AlertTriangle,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Column {
            Text(
                text = stringResource(R.string.sender_unverified_title),
                color = colors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.sender_unverified_description, sender),
                color = colors.text_secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun pending_send_status(
    send_status: String?,
    send_at_ms: Long?,
    on_retry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val counts_down = pending_send_counts_down(send_status)
    var now_ms by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(counts_down, send_at_ms) {
        now_ms = System.currentTimeMillis()
        while (counts_down && send_at_ms != null && now_ms < send_at_ms) {
            kotlinx.coroutines.delay((send_at_ms - now_ms).rem(1000L).takeIf { it > 0 } ?: 1000L)
            now_ms = System.currentTimeMillis()
        }
    }
    val phase = pending_send_display_phase(send_status, send_at_ms, now_ms)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            Crossfade(targetState = phase.first, label = "pending_send_icon") { state ->
                when (state) {
                    pending_send_phase_sent -> Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(14.dp),
                    )
                    pending_send_phase_sending -> org.astermail.android.design.components.aster_spinner(
                        color = colors.text_muted,
                        stroke_width = 1.5.dp,
                        modifier = Modifier.size(12.dp),
                    )
                    pending_send_phase_failed -> Icon(
                        imageVector = TablerIcons.AlertTriangle,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(14.dp),
                    )
                    pending_send_phase_waiting -> Icon(
                        imageVector = TablerIcons.CloudOff,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(14.dp),
                    )
                    else -> Icon(
                        imageVector = TablerIcons.Clock,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (phase.first) {
                pending_send_phase_sent -> stringResource(R.string.sent)
                pending_send_phase_sending -> stringResource(R.string.sending)
                pending_send_phase_failed -> stringResource(R.string.pending_send_failed)
                pending_send_phase_waiting -> stringResource(R.string.pending_send_waiting)
                else -> stringResource(R.string.sending_in_countdown, phase.second)
            },
            color = when (phase.first) {
                pending_send_phase_sent -> colors.accent_blue
                pending_send_phase_failed -> colors.danger
                else -> colors.text_muted
            },
            fontSize = 12.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (phase.first == pending_send_phase_failed) {
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = on_retry,
                modifier = Modifier.testTag("pending_send_retry"),
            ) {
                Text(
                    text = stringResource(R.string.retry),
                    color = colors.accent_blue,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
