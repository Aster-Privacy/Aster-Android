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

package org.astermail.android.imports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class oauth_authorize_url_test {
    @Test
    fun accepts_each_provider_host() {
        assertTrue(is_expected_authorize_url("google", "https://accounts.google.com/o/oauth2/v2/auth?client_id=a"))
        assertTrue(is_expected_authorize_url("microsoft", "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?x=1"))
        assertTrue(is_expected_authorize_url("yahoo", "https://api.login.yahoo.com/oauth2/request_auth?x=1"))
        assertTrue(is_expected_authorize_url("Yahoo", "https://API.LOGIN.YAHOO.COM:443/oauth2/request_auth"))
    }

    @Test
    fun rejects_other_schemes() {
        assertFalse(is_expected_authorize_url("google", "http://accounts.google.com/o/oauth2/v2/auth"))
        assertFalse(is_expected_authorize_url("google", "intent://accounts.google.com/#Intent;end"))
        assertFalse(is_expected_authorize_url("google", "javascript:alert(1)"))
        assertFalse(is_expected_authorize_url("google", "file:///sdcard/x.html"))
    }

    @Test
    fun rejects_other_hosts() {
        assertFalse(is_expected_authorize_url("google", "https://example.com/o/oauth2/v2/auth"))
        assertFalse(is_expected_authorize_url("google", "https://accounts.google.com.example.com/auth"))
        assertFalse(is_expected_authorize_url("google", "https://accounts.google.com@example.com/auth"))
        assertFalse(is_expected_authorize_url("google", "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"))
        assertFalse(is_expected_authorize_url("unknown", "https://accounts.google.com/o/oauth2/v2/auth"))
    }

    @Test
    fun rejects_odd_ports_user_info_and_blank_values() {
        assertFalse(is_expected_authorize_url("google", "https://accounts.google.com:8443/auth"))
        assertFalse(is_expected_authorize_url("google", "https://user@accounts.google.com/auth"))
        assertFalse(is_expected_authorize_url("google", ""))
        assertFalse(is_expected_authorize_url("google", null))
        assertFalse(is_expected_authorize_url("google", "not a url"))
    }
}
