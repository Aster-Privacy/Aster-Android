//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// AGPL-3.0-or-later
//

package org.astermail.android.ui.mail

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.mail.MailViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun pending_send_preview_screen(
    on_back: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val mail_vm: MailViewModel = hiltViewModel()
    val pending by mail_vm.pending_undo_send.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        if (pending == null) on_back()
    }

    val p = pending ?: return
    var now_ms by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.started_at_ms) {
        while (true) {
            now_ms = System.currentTimeMillis()
            val remaining = (p.started_at_ms + p.duration_ms) - now_ms
            if (remaining <= 0) break
            delay(((remaining - 1) % 1000 + 1))
        }
    }
    val remaining_ms = (p.started_at_ms + p.duration_ms) - now_ms
    val seconds_left = (((remaining_ms + 999) / 1000).toInt()).coerceAtLeast(0)

    val from_email = p.sender_email ?: ""
    val from_name = p.sender_display_name?.takeIf { it.isNotBlank() } ?: from_email
    val to_line = p.to.joinToString(", ")
    val cc_line = p.cc.joinToString(", ")
    val bcc_line = p.bcc.joinToString(", ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { on_back() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = colors.text_primary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = p.subject.ifBlank { stringResource_no_subject() },
                color = colors.text_primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SenderAvatar(email = from_email, name = from_name, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = from_name,
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (to_line.isNotBlank()) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.to_recipients_inline, to_line),
                            color = colors.text_muted,
                            fontSize = 13.sp,
                        )
                    }
                    if (cc_line.isNotBlank()) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.cc_recipients_inline, cc_line),
                            color = colors.text_muted,
                            fontSize = 13.sp,
                        )
                    }
                    if (bcc_line.isNotBlank()) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.bcc_recipients_inline, bcc_line),
                            color = colors.text_muted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = false
                            settings.defaultTextEncodingName = "UTF-8"
                            settings.blockNetworkLoads = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { wv ->
                        wv.loadDataWithBaseURL(
                            null,
                            wrap_body_html(p.body_html),
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    },
                    onRelease = { wv ->
                        runCatching {
                            wv.stopLoading()
                            wv.loadUrl("about:blank")
                            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                            wv.destroy()
                        }
                    },
                )
            }

            if (p.attachment_names.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    p.attachment_names.forEach { fname ->
                        Box(
                            modifier = Modifier
                                .clip(SquircleShape(18.dp))
                                .background(colors.dropdown_bg)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(text = fname, color = colors.text_primary, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.dropdown_bg)
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(
                    R.string.sending_in_countdown,
                    seconds_left,
                ),
                color = colors.text_primary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.undo),
                color = colors.accent_blue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(SquircleShape(8.dp))
                    .clickable {
                        p.undo()
                        on_back()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun stringResource_no_subject(): String =
    androidx.compose.ui.res.stringResource(R.string.no_subject)

private fun wrap_body_html(body: String): String {
    return """
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: -apple-system, Roboto, sans-serif; font-size: 15px; color: #111; margin: 0; padding: 0; word-wrap: break-word; }
          img { max-width: 100%; height: auto; }
          a { color: #2563eb; }
        </style>
        </head><body>$body</body></html>
    """.trimIndent()
}
