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

package org.astermail.android.crypto.storage_rotation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.astermail.android.auth.STORAGE_REKEY_PENDING

val DEFAULT_RESEAL_STEPS: List<ResealStep> by lazy {
    listOf(
        ratchet_states_step,
        aliases_step,
        domain_addresses_step,
        deleted_aliases_step,
        ghost_aliases_step,
        alias_directories_step,
        deleted_alias_directories_step,
        alias_items_step,
        contacts_step,
        contact_groups_step,
        contact_field_definitions_step,
        contact_extras_step,
        blocked_senders_step,
        allowed_senders_step,
        recent_recipients_step,
        profile_notes_step,
        external_accounts_step,
        signatures_step,
        templates_step,
        sync_sources_step,
        mail_metadata_step,
        escrow_step,
    )
}

enum class ResealRunStatus { NOT_NEEDED, DONE, INTERRUPTED, SUPERSEDED, FAILED }

data class ResealRunResult(
    val status: ResealRunStatus,
    val counts: StepCounts,
    val reason: String? = null,
    val incomplete: List<String> = emptyList(),
)

fun needs_storage_reseal(view: VaultKeyView?): Boolean =
    view != null && view.data_kek_random && view.storage_rekey_state == STORAGE_REKEY_PENDING

suspend fun run_storage_reseal(
    view: VaultKeyView,
    api: ResealApi,
    store: ProgressStore,
    finish: suspend (String) -> StorageRekeyFinish,
    still_current: (suspend () -> Boolean)? = null,
    sleep: suspend (Long) -> Unit = { delay(it) },
    is_cancelled: () -> Boolean = { false },
    steps: List<ResealStep> = DEFAULT_RESEAL_STEPS,
    extra_old_keys: List<ByteArray> = emptyList(),
): ResealRunResult {
    val counts = StepCounts()
    val data_kek = view.data_kek
    if (!needs_storage_reseal(view) || data_kek.isNullOrEmpty()) {
        return ResealRunResult(ResealRunStatus.NOT_NEEDED, counts)
    }
    val keys = build_reseal_keys(view, extra_old_keys)
        ?: return ResealRunResult(ResealRunStatus.FAILED, counts, "keys_unavailable")
    try {
        val stored = runCatching { store.load() }.getOrNull()
        val progress = if (stored != null && stored.key_id == keys.key_id) stored else ResealProgress(keys.key_id)
        val ctx = ResealContext(
            keys = keys,
            api = api,
            progress = progress,
            persist = { store.save(progress) },
            sleep = sleep,
            is_cancelled = is_cancelled,
            still_current = still_current,
            counts = counts,
        )
        val incomplete_steps = ArrayList<String>()
        ctx.persist()
        for (step in steps) {
            if (ctx.is_cancelled()) {
                return ResealRunResult(ResealRunStatus.INTERRUPTED, counts, "cancelled")
            }
            when (step.run(ctx)) {
                StepResult.DONE -> Unit
                StepResult.INCOMPLETE -> incomplete_steps.add(step.name)
                StepResult.INTERRUPTED -> return ResealRunResult(ResealRunStatus.INTERRUPTED, counts, step.name)
            }
        }
        if (progress.hash_moves.isNotEmpty()) {
            return ResealRunResult(ResealRunStatus.INTERRUPTED, counts, "hash_moves")
        }
        if (incomplete_steps.isNotEmpty()) {
            return ResealRunResult(ResealRunStatus.INTERRUPTED, counts, "incomplete", incomplete_steps)
        }
        val finished = finish(data_kek)
        if (finished == StorageRekeyFinish.FAILED) {
            return ResealRunResult(ResealRunStatus.FAILED, counts, "finish")
        }
        store.clear()
        val status = if (finished == StorageRekeyFinish.SUPERSEDED) ResealRunStatus.SUPERSEDED else ResealRunStatus.DONE
        return ResealRunResult(status, counts)
    } catch (interrupted: ResealInterrupted) {
        return ResealRunResult(ResealRunStatus.INTERRUPTED, counts, interrupted.message)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (caught: Throwable) {
        return ResealRunResult(ResealRunStatus.FAILED, counts, caught::class.simpleName ?: "error")
    } finally {
        keys.dispose()
    }
}
