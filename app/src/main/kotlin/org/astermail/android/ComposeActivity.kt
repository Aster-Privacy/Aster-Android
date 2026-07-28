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

package org.astermail.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import compose.icons.TablerIcons
import compose.icons.tablericons.Lock
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.astermail.android.design.AsterMaterial
import org.astermail.android.security.AppLockViewModel
import org.astermail.android.security.LockdownStore
import org.astermail.android.ui.common.aster_theme_root
import org.astermail.android.ui.compose.ComposeScreen

@AndroidEntryPoint
class ComposeActivity : androidx.fragment.app.FragmentActivity() {

    companion object {
        private const val extra_reply_to = "reply_to"
        private const val extra_mode = "mode"
        private const val extra_draft_id = "draft_id"
        private const val extra_prefill_to = "prefill_to"
        private const val extra_thread_ghost = "thread_ghost"

        fun intent_for(
            context: Context,
            reply_to: String? = null,
            mode: String? = null,
            draft_id: String? = null,
            prefill_to: String? = null,
            thread_ghost_email: String? = null,
        ): Intent {
            val document_key = when {
                !draft_id.isNullOrBlank() -> "draft/${Uri.encode(draft_id)}"
                !reply_to.isNullOrBlank() -> "${Uri.encode(mode.orEmpty().ifBlank { "reply" })}/${Uri.encode(reply_to)}"
                !prefill_to.isNullOrBlank() -> "new/${Uri.encode(prefill_to)}"
                else -> "new"
            }
            return Intent(context, ComposeActivity::class.java).apply {
                data = Uri.parse("aster-compose://$document_key")
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                putExtra(extra_reply_to, reply_to)
                putExtra(extra_mode, mode)
                putExtra(extra_draft_id, draft_id)
                putExtra(extra_prefill_to, prefill_to)
                putExtra(extra_thread_ghost, thread_ghost_email)
            }
        }
    }

    @javax.inject.Inject
    lateinit var app_lock_store: org.astermail.android.security.AppLockStore

    private val lockdown_listener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            runOnUiThread { enforce_secure_flag() }
        }

    override fun onCreate(saved_instance_state: Bundle?) {
        super.onCreate(saved_instance_state)
        enforce_secure_flag()
        LockdownStore.register_listener(applicationContext, lockdown_listener)
        lifecycleScope.launch {
            app_lock_store.config_version.collect { enforce_secure_flag() }
        }
        val reply_to = intent?.getStringExtra(extra_reply_to)?.takeIf { it.isNotBlank() }
        val mode = intent?.getStringExtra(extra_mode)?.takeIf { it.isNotBlank() }
        val draft_id = intent?.getStringExtra(extra_draft_id)?.takeIf { it.isNotBlank() }
        val prefill_to = intent?.getStringExtra(extra_prefill_to)?.takeIf { it.isNotBlank() }
        val thread_ghost_email = intent?.getStringExtra(extra_thread_ghost)?.takeIf { it.isNotBlank() }
        enableEdgeToEdge()
        setContent {
            aster_theme_root {
                val lock_vm: AppLockViewModel = hiltViewModel()
                val is_locked by lock_vm.store.is_locked.collectAsStateWithLifecycle()
                ComposeScreen(
                    on_back = { finish() },
                    on_sent = { finish() },
                    reply_to = reply_to,
                    mode = mode,
                    draft_id = draft_id,
                    prefill_to = prefill_to,
                    thread_ghost_email = thread_ghost_email,
                )
                if (is_locked) {
                    compose_locked_overlay()
                }
            }
        }
    }

    override fun onDestroy() {
        LockdownStore.unregister_listener(applicationContext, lockdown_listener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        enforce_secure_flag()
    }

    override fun onPause() {
        enforce_secure_flag()
        super.onPause()
    }

    private fun enforce_secure_flag() {
        val app_lock_configured = runCatching { app_lock_store.is_configured() }.getOrDefault(true)
        if (LockdownStore.is_enabled(applicationContext) || app_lock_configured) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
private fun compose_locked_overlay() {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = TablerIcons.Lock,
                contentDescription = null,
                tint = colors.text_secondary,
            )
            Text(
                text = stringResource(R.string.compose_locked_title),
                color = colors.text_primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.compose_locked_message),
                color = colors.text_secondary,
                fontSize = 14.sp,
            )
        }
    }
}
