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

package org.astermail.android.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val target_package = "org.astermail.android"
private const val launch_timeout_ms = 15_000L
private const val settle_timeout_ms = 4_000L
private const val probe_timeout_ms = 2_500L

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baseline_profile_rule = BaselineProfileRule()

    @Test
    fun startup() = baseline_profile_rule.collect(
        packageName = target_package,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        await_first_content()
    }

    @Test
    fun inbox_scroll() = baseline_profile_rule.collect(
        packageName = target_package,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        await_first_content()
        scroll_mail_list()
    }

    @Test
    fun thread_open_and_back() = baseline_profile_rule.collect(
        packageName = target_package,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        await_first_content()
        open_first_thread()
        go_back()
    }

    @Test
    fun drawer_and_settings() = baseline_profile_rule.collect(
        packageName = target_package,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        await_first_content()
        open_navigation_drawer()
        go_back()
        open_settings()
        go_back()
    }
}

private fun MacrobenchmarkScope.await_first_content() {
    device.wait(Until.hasObject(By.pkg(target_package).depth(0)), launch_timeout_ms)
    device.waitForIdle(settle_timeout_ms)
}

private fun MacrobenchmarkScope.find_scroll_container(): UiObject2? =
    device.wait(Until.findObject(By.scrollable(true)), settle_timeout_ms)

private fun MacrobenchmarkScope.scroll_mail_list() {
    repeat(3) {
        fling_mail_list(Direction.DOWN)
    }
    fling_mail_list(Direction.UP)
}

private fun MacrobenchmarkScope.fling_mail_list(direction: Direction) {
    val list = find_scroll_container() ?: return
    runCatching {
        list.setGestureMargin(list.visibleBounds.width() / 5)
        list.fling(direction)
    }
    device.waitForIdle(settle_timeout_ms)
}

private fun MacrobenchmarkScope.open_first_thread() {
    val row = runCatching {
        find_scroll_container()?.findObjects(By.clickable(true))?.firstOrNull()
    }.getOrNull() ?: return
    runCatching { row.click() }
    device.waitForIdle(settle_timeout_ms)
}

private fun MacrobenchmarkScope.open_navigation_drawer() {
    if (tap_tag("open_drawer")) return
    device.swipe(1, device.displayHeight / 2, device.displayWidth * 2 / 3, device.displayHeight / 2, 12)
    device.waitForIdle(settle_timeout_ms)
}

private fun MacrobenchmarkScope.open_settings() {
    tap_tag("open_settings")
}

private fun MacrobenchmarkScope.tap_tag(tag: String): Boolean {
    val target = device.wait(Until.findObject(By.res(tag)), probe_timeout_ms) ?: return false
    val tapped = runCatching { target.click() }.isSuccess
    device.waitForIdle(settle_timeout_ms)
    return tapped
}

private fun MacrobenchmarkScope.go_back() {
    device.pressBack()
    device.waitForIdle(settle_timeout_ms)
}
