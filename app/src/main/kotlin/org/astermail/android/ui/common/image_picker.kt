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
package org.astermail.android.ui.common

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import org.astermail.android.R

internal const val image_pick_system_action = "android.provider.action.PICK_IMAGES"
internal const val image_pick_system_max_extra = "android.provider.extra.PICK_IMAGES_MAX"
internal const val image_pick_androidx_action = "androidx.activity.result.contract.action.PICK_IMAGES"
internal const val image_pick_androidx_max_extra = "androidx.activity.result.contract.extra.PICK_IMAGES_MAX"
internal const val image_pick_gms_action = "com.google.android.gms.provider.action.PICK_IMAGES"
internal const val image_pick_gms_max_extra = "com.google.android.gms.provider.extra.PICK_IMAGES_MAX"
private const val image_pick_mime_type = "image/*"
internal const val visual_media_any_type = "*/*"
internal val visual_media_mime_types = arrayOf("image/*", "video/*")

internal enum class image_pick_source {
    system_picker,
    androidx_picker,
    gms_picker,
    open_document,
    get_content,
}

internal data class image_pick_spec(
    val action: String,
    val openable: Boolean,
    val max_items_extra: String?,
    val allow_multiple: Boolean,
)

internal data class image_pick_types(
    val type: String?,
    val extra_mime_types: List<String>?,
)

internal fun image_pick_types_for(spec: image_pick_spec, include_videos: Boolean): image_pick_types = when {
    !include_videos -> image_pick_types(image_pick_mime_type, null)
    spec.openable -> image_pick_types(visual_media_any_type, visual_media_mime_types.toList())
    else -> image_pick_types(null, null)
}

internal fun is_system_photo_picker_available(sdk_int: Int, r_extension_version: Int): Boolean =
    sdk_int >= 33 || (sdk_int >= 30 && r_extension_version >= 2)

internal fun image_pick_sources(
    sdk_int: Int,
    r_extension_version: Int,
    has_androidx_picker: Boolean,
    has_gms_picker: Boolean,
): List<image_pick_source> = buildList {
    if (is_system_photo_picker_available(sdk_int, r_extension_version)) add(image_pick_source.system_picker)
    if (has_androidx_picker) add(image_pick_source.androidx_picker)
    if (has_gms_picker) add(image_pick_source.gms_picker)
    add(image_pick_source.open_document)
    add(image_pick_source.get_content)
}

internal fun image_pick_spec_for(source: image_pick_source, multiple: Boolean): image_pick_spec = when (source) {
    image_pick_source.system_picker -> image_pick_spec(
        action = image_pick_system_action,
        openable = false,
        max_items_extra = image_pick_system_max_extra.takeIf { multiple },
        allow_multiple = false,
    )
    image_pick_source.androidx_picker -> image_pick_spec(
        action = image_pick_androidx_action,
        openable = false,
        max_items_extra = image_pick_androidx_max_extra.takeIf { multiple },
        allow_multiple = false,
    )
    image_pick_source.gms_picker -> image_pick_spec(
        action = image_pick_gms_action,
        openable = false,
        max_items_extra = image_pick_gms_max_extra.takeIf { multiple },
        allow_multiple = false,
    )
    image_pick_source.open_document -> image_pick_spec(
        action = Intent.ACTION_OPEN_DOCUMENT,
        openable = true,
        max_items_extra = null,
        allow_multiple = multiple,
    )
    image_pick_source.get_content -> image_pick_spec(
        action = Intent.ACTION_GET_CONTENT,
        openable = true,
        max_items_extra = null,
        allow_multiple = multiple,
    )
}

class image_picker_launcher internal constructor(private val on_launch: () -> Unit) {
    fun launch() = on_launch()
}

@Composable
fun remember_image_picker(on_picked: (Uri) -> Unit): image_picker_launcher =
    remember_image_picker_launcher(multiple = false) { uris -> uris.firstOrNull()?.let(on_picked) }

@Composable
fun remember_multiple_image_picker(
    include_videos: Boolean = false,
    on_picked: (List<Uri>) -> Unit,
): image_picker_launcher =
    remember_image_picker_launcher(multiple = true, include_videos = include_videos) { uris ->
        if (uris.isNotEmpty()) on_picked(uris)
    }

@Composable
private fun remember_image_picker_launcher(
    multiple: Boolean,
    include_videos: Boolean = false,
    on_result: (List<Uri>) -> Unit,
): image_picker_launcher {
    val context = LocalContext.current
    val latest_on_result by rememberUpdatedState(on_result)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        latest_on_result(picked_image_uris(result))
    }
    return remember(context, launcher, multiple, include_videos) {
        image_picker_launcher { launch_image_picker(context, launcher, multiple, include_videos) }
    }
}

private fun launch_image_picker(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
    multiple: Boolean,
    include_videos: Boolean,
) {
    val sdk_int = Build.VERSION.SDK_INT
    val r_extension = r_extension_version()
    val androidx_picker = resolve_system_activity(context, image_pick_androidx_action)
    val gms_picker = resolve_system_activity(context, image_pick_gms_action)
    val sources = image_pick_sources(sdk_int, r_extension, androidx_picker != null, gms_picker != null)
    val max_items = if (is_system_photo_picker_available(sdk_int, r_extension)) {
        system_picker_max_items()
    } else {
        Int.MAX_VALUE
    }
    for (source in sources) {
        val component = when (source) {
            image_pick_source.androidx_picker -> androidx_picker
            image_pick_source.gms_picker -> gms_picker
            else -> null
        }
        val intent = image_pick_intent(image_pick_spec_for(source, multiple), component, max_items, include_videos)
        val launched = try {
            launcher.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (launched) return
    }
    Toast.makeText(context, context.getString(R.string.image_picker_unavailable), Toast.LENGTH_LONG).show()
}

private fun image_pick_intent(
    spec: image_pick_spec,
    component: ResolveInfo?,
    max_items: Int,
    include_videos: Boolean,
): Intent {
    val types = image_pick_types_for(spec, include_videos)
    val intent = Intent(spec.action)
    types.type?.let { intent.setType(it) }
    types.extra_mime_types?.let { intent.putExtra(Intent.EXTRA_MIME_TYPES, it.toTypedArray()) }
    component?.activityInfo?.let { info -> intent.setClassName(info.applicationInfo.packageName, info.name) }
    if (spec.openable) intent.addCategory(Intent.CATEGORY_OPENABLE)
    spec.max_items_extra?.let { extra -> intent.putExtra(extra, max_items) }
    if (spec.allow_multiple) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    return intent
}

private fun picked_image_uris(result: ActivityResult): List<Uri> {
    if (result.resultCode != Activity.RESULT_OK) return emptyList()
    val data = result.data ?: return emptyList()
    val clip = data.clipData
    val from_clip = if (clip == null) emptyList() else (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    return (listOfNotNull(data.data) + from_clip).distinct()
}

private fun r_extension_version(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) }.getOrDefault(0)
    } else {
        0
    }

@SuppressLint("NewApi")
private fun system_picker_max_items(): Int = runCatching { MediaStore.getPickImagesMaxLimit() }.getOrDefault(Int.MAX_VALUE)

@Suppress("DEPRECATION")
private fun resolve_system_activity(context: Context, action: String): ResolveInfo? = runCatching {
    context.packageManager.resolveActivity(
        Intent(action),
        PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY,
    )
}.getOrNull()
