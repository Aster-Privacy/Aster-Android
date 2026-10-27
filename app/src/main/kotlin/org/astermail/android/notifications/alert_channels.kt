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

package org.astermail.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import org.astermail.android.R

val ALERT_VIBRATION_PATTERN = longArrayOf(0L, 250L, 200L, 250L)

val ASTER_SOUND_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

fun aster_new_mail_sound_uri(context: Context): Uri =
    Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath("raw")
        .appendPath(context.resources.getResourceEntryName(R.raw.aster_new_mail))
        .build()

fun is_user_chosen_sound(sound: Uri?): Boolean =
    sound != null && sound != Settings.System.DEFAULT_NOTIFICATION_URI

fun create_alert_channel(
    manager: NotificationManager,
    channel: NotificationChannel,
    vibrate: Boolean,
    replaced_ids: List<String>,
) {
    if (manager.getNotificationChannel(channel.id) == null) {
        val previous = replaced_ids
            .filter { it != channel.id }
            .firstNotNullOfOrNull { runCatching { manager.getNotificationChannel(it) }.getOrNull() }
        if (previous != null) {
            inherit_user_choices(channel, previous)
        }
    }
    channel.enableVibration(vibrate)
    channel.vibrationPattern = if (vibrate) ALERT_VIBRATION_PATTERN else null
    manager.createNotificationChannel(channel)
    for (id in replaced_ids) {
        if (id != channel.id) {
            runCatching { manager.deleteNotificationChannel(id) }
        }
    }
}

private fun inherit_user_choices(channel: NotificationChannel, previous: NotificationChannel) {
    if (previous.importance != NotificationManager.IMPORTANCE_UNSPECIFIED) {
        channel.importance = previous.importance
    }
    val previous_sound = previous.sound
    if (channel.sound != null && is_user_chosen_sound(previous_sound)) {
        channel.setSound(previous_sound, previous.audioAttributes)
    }
}
