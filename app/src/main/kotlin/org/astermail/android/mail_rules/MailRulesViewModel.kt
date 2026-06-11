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

package org.astermail.android.mail_rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.api.mail_rules.Action
import org.astermail.android.api.mail_rules.Condition
import org.astermail.android.api.mail_rules.CreateRuleRequest
import org.astermail.android.api.mail_rules.MailRule
import org.astermail.android.api.mail_rules.MailRulesApi
import org.astermail.android.api.mail_rules.MatchMode
import org.astermail.android.api.mail_rules.UpdateRuleRequest

data class MailRulesUiState(
    val rules: List<MailRule> = emptyList(),
    val is_loading: Boolean = false,
    val is_refreshing: Boolean = false,
    val error: String? = null,
    val last_saved_id: String? = null,
)

@HiltViewModel
class MailRulesViewModel @Inject constructor(
    private val api: MailRulesApi,
) : ViewModel() {

    private val _state = MutableStateFlow(MailRulesUiState())
    val state: StateFlow<MailRulesUiState> = _state.asStateFlow()

    fun load(force_refresh: Boolean = false) {
        viewModelScope.launch {
            if (force_refresh) {
                _state.value = _state.value.copy(is_refreshing = true, error = null)
            } else if (_state.value.rules.isEmpty()) {
                _state.value = _state.value.copy(is_loading = true, error = null)
            }
            try {
                val response = api.list()
                _state.value = _state.value.copy(
                    rules = response.rules,
                    is_loading = false,
                    is_refreshing = false,
                    error = null,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    is_refreshing = false,
                    error = t.message ?: "load failed",
                )
            }
        }
    }

    fun create_rule(
        name: String,
        color: String,
        enabled: Boolean,
        match_mode: MatchMode,
        conditions: List<Condition>,
        actions: List<Action>,
        run_on_existing: Boolean = false,
        on_done: (String?) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val response = api.create(
                    CreateRuleRequest(
                        name = name,
                        color = color,
                        enabled = enabled,
                        match_mode = match_mode,
                        conditions = conditions,
                        actions = actions,
                        run_on_existing = run_on_existing,
                    ),
                )
                load()
                _state.value = _state.value.copy(last_saved_id = response.id)
                on_done(response.id)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "create failed")
                on_done(null)
            }
        }
    }

    fun update_rule(
        rule_id: String,
        name: String? = null,
        color: String? = null,
        enabled: Boolean? = null,
        match_mode: MatchMode? = null,
        conditions: List<Condition>? = null,
        actions: List<Action>? = null,
        on_done: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val updated = api.update(
                    rule_id,
                    UpdateRuleRequest(
                        name = name,
                        color = color,
                        enabled = enabled,
                        match_mode = match_mode,
                        conditions = conditions,
                        actions = actions,
                    ),
                )
                _state.value = _state.value.copy(
                    rules = _state.value.rules.map { if (it.id == rule_id) updated else it },
                )
                on_done(true)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "update failed")
                on_done(false)
            }
        }
    }

    fun delete_rule(rule_id: String) {
        val original = _state.value.rules
        _state.value = _state.value.copy(rules = original.filter { it.id != rule_id })
        viewModelScope.launch {
            try {
                api.delete(rule_id)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(rules = original, error = t.message ?: "delete failed")
            }
        }
    }

    fun toggle_enabled(rule_id: String) {
        val rule = _state.value.rules.firstOrNull { it.id == rule_id } ?: return
        update_rule(rule_id = rule_id, enabled = !rule.enabled)
    }

    fun duplicate_rule(rule_id: String) {
        val rule = _state.value.rules.firstOrNull { it.id == rule_id } ?: return
        create_rule(
            name = rule.name + " (copy)",
            color = rule.color,
            enabled = rule.enabled,
            match_mode = rule.match_mode,
            conditions = rule.conditions,
            actions = rule.actions,
        )
    }

    fun run_on_existing(rule_id: String) {
        viewModelScope.launch {
            try {
                api.run_on_existing(rule_id)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "run failed")
            }
        }
    }

    fun reorder(from_index: Int, to_index: Int) {
        val current = _state.value.rules.toMutableList()
        if (from_index !in current.indices || to_index !in current.indices) return
        val item = current.removeAt(from_index)
        current.add(to_index, item)
        val original = _state.value.rules
        _state.value = _state.value.copy(rules = current)
        viewModelScope.launch {
            try {
                api.reorder(current.map { it.id })
            } catch (t: Throwable) {
                _state.value = _state.value.copy(rules = original, error = t.message ?: "reorder failed")
            }
        }
    }

    fun clear_error() {
        _state.value = _state.value.copy(error = null)
    }
}
