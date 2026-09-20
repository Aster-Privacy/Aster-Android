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

package org.astermail.android.ui.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.mail.EmailHtmlSanitizer
import org.astermail.android.ui.mail.REMOTE_IMAGE_PROXY_BASE
import org.astermail.android.ui.mail.build_email_html
import org.astermail.android.ui.mail.configure_mail_body_web_view
import org.astermail.android.ui.mail.proxy_external_urls
import org.astermail.android.ui.mail.resolve_inline_cids

private const val quoted_preview_min_height_dp = 64

private const val quoted_preview_max_height_dp = 320

private const val quoted_preview_initial_height_dp = 160

private val quoted_preview_measure_delays_ms = longArrayOf(0L, 120L, 400L, 900L)

internal fun build_quoted_preview_body(
    raw_html: String,
    allow_external: Boolean,
    image_blocked_label: String,
    sanitize_options: EmailHtmlSanitizer.SanitizeOptions,
): String {
    val sanitized = EmailHtmlSanitizer.sanitize(raw_html, sanitize_options)
    val cid_resolved = resolve_inline_cids(sanitized, emptyMap())
    if (allow_external) return proxy_external_urls(cid_resolved, REMOTE_IMAGE_PROXY_BASE)
    return EmailHtmlSanitizer.neutralize_blocked_backgrounds(
        EmailHtmlSanitizer.replace_blocked_images(cid_resolved, image_blocked_label),
    )
}

@Composable
internal fun quoted_html_preview(html: String, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val settings_vm = shared_settings_view_model()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val preferences = settings_state.preferences
    val remote_images_always = preferences?.load_remote_images == "always" ||
        preferences?.block_external_content == false
    val privacy_blocks_external = (preferences?.block_external_images ?: true) && !remote_images_always
    val allow_external = !privacy_blocks_external && !org.astermail.android.network.low_network_active()
    val tracking_protection_on = preferences?.block_external_content != false
    val sanitize_options = EmailHtmlSanitizer.SanitizeOptions(
        clean_tracking_links = tracking_protection_on && preferences?.block_tracking_links != false,
        remove_tracking_pixels = tracking_protection_on && preferences?.block_tracking_pixels != false,
        block_remote_fonts = preferences?.block_remote_fonts != false,
        block_remote_css = preferences?.block_remote_css != false,
    )
    val text_zoom = when (preferences?.font_size_scale) {
        "small" -> 85
        "large" -> 120
        "extra_large" -> 140
        else -> 100
    }
    val is_dark = if (colors.is_glass) {
        colors.is_dark
    } else {
        colors.bg_primary.luminance() < colors.text_primary.luminance()
    }
    val fg_hex = String.format(java.util.Locale.US, "#%06X", colors.text_secondary.toArgb() and 0xFFFFFF)
    val link_hex = String.format(java.util.Locale.US, "#%06X", colors.accent_blue.toArgb() and 0xFFFFFF)
    val forwarded_label = stringResource(R.string.forwarded_message_label)
    val image_blocked_label = stringResource(R.string.image_blocked_placeholder)
    val image_failed_label = stringResource(R.string.image_failed_placeholder)
    val dyslexia_font = preferences?.dyslexia_font == true
    val underline_links = preferences?.underline_links == true
    val email_font_id = org.astermail.android.design.resolve_email_font_id(
        preferences?.email_font_choice,
        preferences?.font_choice,
    )
    val force_dark_emails = is_dark && preferences?.force_dark_emails == true
    val screen_height_dp = LocalConfiguration.current.screenHeightDp
    val max_height_dp = minOf(quoted_preview_max_height_dp, (screen_height_dp * 0.45f).toInt())
        .coerceAtLeast(quoted_preview_min_height_dp)

    val document by produceState<String?>(
        initialValue = null,
        html,
        allow_external,
        sanitize_options,
        is_dark,
        fg_hex,
        link_hex,
        force_dark_emails,
        dyslexia_font,
        email_font_id,
        text_zoom,
        underline_links,
    ) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                build_email_html(
                    body = build_quoted_preview_body(
                        raw_html = html,
                        allow_external = allow_external,
                        image_blocked_label = image_blocked_label,
                        sanitize_options = sanitize_options,
                    ),
                    is_dark = is_dark,
                    fg_hex = fg_hex,
                    link_hex = link_hex,
                    forwarded_label = forwarded_label,
                    image_failed_label = image_failed_label,
                    force_dark_emails = force_dark_emails,
                    dyslexia_font = dyslexia_font,
                    translate_mode = "off",
                    email_font_id = email_font_id,
                    text_zoom = text_zoom,
                    underline_links = underline_links,
                    scrollable = true,
                )
            }.getOrNull()
        }
    }

    var measured_height_dp by remember(html) { mutableStateOf(quoted_preview_initial_height_dp) }
    val target_height_dp = measured_height_dp.coerceIn(quoted_preview_min_height_dp, max_height_dp)
    val reduce_motion = aster_reduce_motion()
    val animated_height by animateDpAsState(
        targetValue = target_height_dp.dp,
        animationSpec = tween(
            durationMillis = if (reduce_motion) 0 else AsterDuration.medium_1,
            easing = AsterEasing.standard_in_out,
        ),
        label = "quoted_preview_height",
    )

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                configure_mail_body_web_view(this, text_zoom, allow_external)
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                setOnTouchListener { view, _ ->
                    val scrollable = view.canScrollVertically(1) || view.canScrollVertically(-1)
                    view.parent?.requestDisallowInterceptTouchEvent(scrollable)
                    false
                }
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean = true

                    override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                        quoted_preview_measure_delays_ms.forEach { delay_ms ->
                            view.postDelayed({
                                val content_height = view.contentHeight
                                if (content_height > 0) measured_height_dp = content_height
                            }, delay_ms)
                        }
                    }

                    override fun onRenderProcessGone(
                        view: android.webkit.WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?,
                    ): Boolean {
                        runCatching {
                            (view?.parent as? android.view.ViewGroup)?.removeView(view)
                            view?.destroy()
                        }
                        return true
                    }
                }
            }
        },
        update = { web_view ->
            val doc = document ?: return@AndroidView
            web_view.settings.textZoom = text_zoom
            web_view.settings.blockNetworkImage = !allow_external
            web_view.setBackgroundColor(
                if (doc.contains("data-white=\"1\"")) {
                    android.graphics.Color.WHITE
                } else {
                    android.graphics.Color.TRANSPARENT
                },
            )
            if (web_view.tag != doc) {
                web_view.tag = doc
                web_view.loadDataWithBaseURL(null, doc, "text/html", "utf-8", null)
            }
        },
        onRelease = { web_view ->
            runCatching {
                web_view.stopLoading()
                web_view.destroy()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(if (reduce_motion) target_height_dp.dp else animated_height),
    )
}
