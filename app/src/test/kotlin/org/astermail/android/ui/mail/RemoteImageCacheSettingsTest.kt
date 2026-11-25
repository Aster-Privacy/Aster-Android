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

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RemoteImageCacheSettingsTest {
    private lateinit var context: Application

    @Before
    fun set_up() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun reset() {
        remote_image_cache.set_keep_images(context, true)
        remote_image_cache.set_prefetch_on_arrival(context, false)
    }

    @Test
    fun defaults_keep_images_on_and_prefetch_off() {
        val settings = remote_image_cache.current_settings(context)
        assertTrue(settings.keep_images)
        assertFalse(settings.prefetch_on_arrival)
    }

    @Test
    fun turning_keep_off_clears_and_bypasses_the_cache() {
        val url = "https://cdn.example.com/a.png"
        val scope = File(context.noBackupFilesDir, "remote_image_cache/scope")
        scope.mkdirs()
        val entry = File(scope, "entry.img").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        remote_image_cache.set_keep_images(context, false)
        assertFalse(entry.exists())
        assertFalse(remote_image_cache.is_enabled(context))
        assertNull(remote_image_cache.lookup(context, url))
        val source = ByteArrayInputStream(ByteArray(10))
        assertSame(source, remote_image_cache.caching_stream(context, url, 200, "image/png", source))
        assertFalse(remote_image_cache.remember(context, "account", url, 200, "image/png", ByteArray(10)))
    }

    @Test
    fun settings_are_device_local_preferences() {
        remote_image_cache.set_prefetch_on_arrival(context, true)
        val prefs = context.getSharedPreferences("remote_image_cache_settings", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("prefetch_on_arrival", false))
    }
}
