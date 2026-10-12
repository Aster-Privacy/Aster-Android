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

package org.astermail.android.share

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaRowOwnerInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.contentResolver
    private val own_rows = mutableListOf<Uri>()
    private val shell_rows = mutableListOf<Uri>()

    @Before
    fun setup() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
    }

    @After
    fun cleanup() {
        own_rows.forEach { runCatching { resolver.delete(it, null, null) } }
        shell_rows.forEach { shell("content delete --uri $it") }
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().decodeToString() }
    }

    private fun insert_own_row(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "aster_owner_probe_${System.nanoTime()}.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        assertNotNull(uri)
        own_rows += uri!!
        resolver.openOutputStream(uri)!!.use { it.write("plaintext".toByteArray()) }
        return uri
    }

    private fun insert_shell_row(): Uri {
        val name = "shell_owner_probe_${System.nanoTime()}.txt"
        shell(
            "content insert --uri content://media/external/downloads " +
                "--bind _display_name:s:$name --bind mime_type:s:text/plain",
        )
        val listing = shell(
            "content query --uri content://media/external/downloads --projection _id --where _display_name='$name'",
        )
        val id = Regex("_id=(\\d+)").find(listing)?.groupValues?.get(1)?.toLong()
        assertNotNull(listing, id)
        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id!!)
        shell_rows += uri
        return uri
    }

    @Test
    fun a_row_the_app_wrote_is_readable_without_a_grant_and_counts_as_owned() {
        val uri = insert_own_row()
        assertEquals("plaintext", resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
        assertTrue(app_owns_media_row(resolver, uri, context.packageName))
    }

    @Test
    fun a_row_the_app_wrote_counts_as_owned_when_the_uri_hides_every_row() {
        val uri = insert_own_row()
        val hidden = uri.buildUpon().appendQueryParameter("limit", "0").build()
        assertTrue(app_owns_media_row(resolver, hidden, context.packageName))
    }

    @Test
    fun a_row_the_app_wrote_counts_as_owned_through_the_files_collection() {
        val uri = insert_own_row()
        val files = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), ContentUris.parseId(uri))
        assertTrue(app_owns_media_row(resolver, files, context.packageName))
    }

    @Test
    fun the_whole_downloads_collection_counts_as_owned() {
        insert_own_row()
        assertTrue(app_owns_media_row(resolver, MediaStore.Downloads.EXTERNAL_CONTENT_URI, context.packageName))
    }

    @Test
    fun a_row_from_another_app_without_a_grant_counts_as_owned() {
        val uri = insert_shell_row()
        assertTrue(app_owns_media_row(resolver, uri, context.packageName))
    }

    @Test
    fun a_row_from_another_app_with_a_grant_is_not_owned() {
        val uri = insert_shell_row()
        shell(
            "am start -a android.intent.action.SEND -t text/plain -d $uri " +
                "--eu android.intent.extra.STREAM $uri --grant-read-uri-permission " +
                "-n ${context.packageName}/org.astermail.android.MainActivity",
        )
        val deadline = System.currentTimeMillis() + 15_000L
        var visible = false
        while (!visible && System.currentTimeMillis() < deadline) {
            visible = runCatching {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 }
            }.getOrNull() == true
            if (!visible) Thread.sleep(250)
        }
        assertTrue("the granted row never became visible", visible)
        assertFalse(app_owns_media_row(resolver, uri, context.packageName))
    }

    @Test
    fun another_provider_is_never_owned() {
        assertFalse(
            app_owns_media_row(
                resolver,
                Uri.parse("content://com.example.gallery.fileprovider/shared/1"),
                context.packageName,
            ),
        )
    }
}
