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

package org.astermail.android.billing

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

object PlayReview {
    private const val TAG = "PlayReview"

    fun request(
        activity: Activity,
        manager: ReviewManager = ReviewManagerFactory.create(activity),
        on_complete: () -> Unit = {},
    ) {
        try {
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (request.isSuccessful) {
                    try {
                        manager.launchReviewFlow(activity, request.result)
                            .addOnCompleteListener { on_complete() }
                    } catch (t: Throwable) {
                        Log.w(TAG, "launchReviewFlow failed", t)
                        on_complete()
                    }
                } else {
                    Log.w(TAG, "requestReviewFlow failed", request.exception)
                    on_complete()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "in-app review unavailable", t)
            on_complete()
        }
    }
}
