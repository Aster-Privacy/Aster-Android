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

package org.astermail.android.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugManifestExportTest {

    private fun manifest_text(relative: String): String {
        var candidate = File(relative)
        if (!candidate.exists()) candidate = File("app/$relative")
        assertTrue("cannot find $relative", candidate.exists())
        return candidate.readText()
    }

    private fun component_blocks(manifest: String, tag: String): List<String> =
        Regex("<$tag\\b[\\s\\S]*?(?:/>|</$tag>)").findAll(manifest).map { it.value }.toList()

    @Test
    fun every_exported_debug_component_requires_a_permission() {
        val manifest = manifest_text("src/debug/AndroidManifest.xml")
        val offenders = mutableListOf<String>()

        for (tag in listOf("receiver", "activity", "service", "provider")) {
            for (block in component_blocks(manifest, tag)) {
                if (!block.contains("android:exported=\"true\"")) continue
                if (block.contains("android:permission=")) continue
                offenders += Regex("android:name=\"([^\"]+)\"").find(block)?.groupValues?.get(1) ?: tag
            }
        }

        assertTrue("exported with no permission: $offenders", offenders.isEmpty())
    }

    @Test
    fun the_debug_notification_receiver_is_reachable_only_by_the_shell() {
        val manifest = manifest_text("src/debug/AndroidManifest.xml")
        val block = component_blocks(manifest, "receiver")
            .first { it.contains("DebugNotificationReceiver") }

        assertTrue(block.contains("android:permission=\"android.permission.DUMP\""))
    }

    @Test
    fun the_release_manifest_never_declares_the_debug_receiver() {
        val manifest = manifest_text("src/main/AndroidManifest.xml")

        assertTrue(!manifest.contains("DebugNotificationReceiver"))
    }
}
