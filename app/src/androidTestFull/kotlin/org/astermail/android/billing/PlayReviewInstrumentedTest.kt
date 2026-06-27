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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.testing.FakeReviewManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlayReviewInstrumentedTest {

    @Test
    fun review_flow_completes_with_fake_manager() {
        val scenario = ActivityScenario.launch(ReviewTestActivity::class.java)
        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            PlayReview.request(activity, FakeReviewManager(activity)) { latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        scenario.close()
    }

    @Test
    fun completes_without_crash_when_play_unavailable() {
        val unavailable = object : ReviewManager {
            override fun requestReviewFlow(): Task<ReviewInfo> =
                Tasks.forException(RuntimeException("play store unavailable"))

            override fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo): Task<Void> =
                Tasks.forResult(null)
        }
        val scenario = ActivityScenario.launch(ReviewTestActivity::class.java)
        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            PlayReview.request(activity, unavailable) { latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        scenario.close()
    }
}
