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

import org.astermail.android.api.subscriptions.ProxyUnsubscribeRequest

enum class UnsubscribeOutcome {
    unsubscribed,
    manual_required,
}

fun build_proxy_unsubscribe_request(info: UnsubscribeInfo): ProxyUnsubscribeRequest? {
    return when {
        info.method == "one-click" && info.unsubscribe_link != null -> ProxyUnsubscribeRequest(
            method = "one-click",
            url = info.unsubscribe_link,
            list_unsubscribe_post = info.list_unsubscribe_post,
        )
        info.method == "link" && info.unsubscribe_link != null -> ProxyUnsubscribeRequest(
            method = "link",
            url = info.unsubscribe_link,
        )
        info.method == "mailto" && info.unsubscribe_mailto != null -> ProxyUnsubscribeRequest(
            method = "mailto",
            mailto_address = info.unsubscribe_mailto,
        )
        else -> null
    }
}

suspend fun execute_unsubscribe(
    info: UnsubscribeInfo,
    send: suspend (ProxyUnsubscribeRequest) -> Boolean,
): UnsubscribeOutcome {
    val request = build_proxy_unsubscribe_request(info) ?: return UnsubscribeOutcome.manual_required
    val succeeded = try {
        send(request)
    } catch (t: kotlinx.coroutines.CancellationException) {
        throw t
    } catch (_: Throwable) {
        false
    }
    return if (succeeded) UnsubscribeOutcome.unsubscribed else UnsubscribeOutcome.manual_required
}
