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

package org.astermail.android.api.network

import java.util.concurrent.atomic.AtomicLong

object TransientFailureSignal {

    private val count = AtomicLong(0L)

    fun epoch(): Long = count.get()

    fun note() {
        count.incrementAndGet()
    }

    fun note_status(status: Int) {
        if (is_transient_status(status)) note()
    }

    fun is_transient_status(status: Int): Boolean =
        status == 401 || status == 403 || status == 408 || status == 425 || status == 429 || status >= 500
}
