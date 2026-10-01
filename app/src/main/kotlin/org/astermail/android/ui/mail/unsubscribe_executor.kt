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

fun build_proxy_unsubscribe_requests(info: UnsubscribeInfo): List<ProxyUnsubscribeRequest> {
    val requests = mutableListOf<ProxyUnsubscribeRequest>()
    if (info.method == "one-click" && info.unsubscribe_link != null) {
        requests += ProxyUnsubscribeRequest(
            method = "one-click",
            url = info.unsubscribe_link,
            list_unsubscribe_post = ONE_CLICK_POST_VALUE,
        )
    }
    if (info.unsubscribe_mailto != null) {
        requests += ProxyUnsubscribeRequest(
            method = "mailto",
            mailto_address = info.unsubscribe_mailto,
        )
    }
    return requests
}

suspend fun execute_unsubscribe(
    info: UnsubscribeInfo,
    send: suspend (ProxyUnsubscribeRequest) -> Boolean,
): UnsubscribeOutcome {
    for (request in build_proxy_unsubscribe_requests(info)) {
        val succeeded = try {
            send(request)
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (_: Throwable) {
            false
        }
        if (succeeded) return UnsubscribeOutcome.unsubscribed
    }
    return UnsubscribeOutcome.manual_required
}
