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


package org.astermail.android.auth

import java.security.SecureRandom
import org.astermail.android.storage.fake_prefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryBackupPlanTest {

    private val email = "user@astermail.org"

    private fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun vault(format_v2: Boolean): JSONObject {
        val vault = JSONObject().put("identity_key", "id")
        if (format_v2) vault.put(RecoveryBackupVaultFields.FORMAT_V2, true)
        return vault
    }

    private fun opened(user_id: String, format_v2: Boolean, vault: JSONObject) =
        OpenedVaultBackup(vault.toString().toByteArray(Charsets.UTF_8), 0L, user_id, format_v2)

    private fun seals(plan: RecoveryBackupPlan, vault: JSONObject, floors: RecoveryBackupFloors) {
        val binding = floors.next_binding(plan.user_id, email, RecoveryBackupVaultFields.generation(vault))
        val sealed = runCatching {
            seal_vault_backup(vault.toString().toByteArray(Charsets.UTF_8), random(32), binding, plan.format_v2)
        }
        assertTrue("plan ${plan.user_id}/${plan.format_v2} failed to seal", sealed.isSuccess)
    }

    @Test
    fun a_vault_on_the_new_format_without_a_user_id_stops_at_the_code_step() {
        val floors = RecoveryBackupFloors(fake_prefs())
        val vault = vault(format_v2 = true)

        assertNull(plan_recovery_backup(opened("", false, vault), vault, floors, email))
        assertNull(plan_recovery_backup(opened("  ", false, vault), vault, floors, email))
    }

    @Test
    fun an_account_marked_for_the_new_format_without_a_user_id_stops_at_the_code_step() {
        val floors = RecoveryBackupFloors(fake_prefs())
        val vault = vault(format_v2 = false)
        floors.mark_v2(null, email)

        assertNull(plan_recovery_backup(opened("", false, vault), vault, floors, email))
    }

    @Test
    fun an_old_format_backup_without_a_user_id_keeps_the_first_format_and_seals() {
        val floors = RecoveryBackupFloors(fake_prefs())
        val vault = vault(format_v2 = false)

        val plan = plan_recovery_backup(opened("", false, vault), vault, floors, email)
        assertNotNull(plan)
        assertFalse(plan!!.format_v2)
        seals(plan, vault, floors)
    }

    @Test
    fun a_new_format_backup_keeps_its_user_and_seals() {
        val floors = RecoveryBackupFloors(fake_prefs())
        val vault = vault(format_v2 = true)

        val plan = plan_recovery_backup(opened("user-1", true, vault), vault, floors, email)
        assertNotNull(plan)
        assertTrue(plan!!.format_v2)
        assertEquals("user-1", plan.user_id)
        seals(plan, vault, floors)
    }

    @Test
    fun every_plan_the_code_step_accepts_seals_at_the_last_step() {
        for (user_id in listOf("", " ", "user-1")) {
            for (backup_v2 in listOf(false, true)) {
                for (vault_v2 in listOf(false, true)) {
                    for (marked in listOf(false, true)) {
                        val floors = RecoveryBackupFloors(fake_prefs())
                        if (marked) floors.mark_v2(null, email)
                        val vault = vault(vault_v2)
                        val plan = plan_recovery_backup(opened(user_id, backup_v2, vault), vault, floors, email)
                            ?: continue
                        seals(plan, vault, floors)
                    }
                }
            }
        }
    }
}
