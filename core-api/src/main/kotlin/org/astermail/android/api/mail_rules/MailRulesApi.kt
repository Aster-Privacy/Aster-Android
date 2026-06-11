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

package org.astermail.android.api.mail_rules

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.ExperimentalSerializationApi
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
enum class AddressOp {
    @SerialName("is") IS,
    @SerialName("contains") CONTAINS,
    @SerialName("is_not") IS_NOT,
    @SerialName("matches_domain") MATCHES_DOMAIN,
    @SerialName("matches_regex") MATCHES_REGEX,
}

@Serializable
enum class TextOp {
    @SerialName("contains") CONTAINS,
    @SerialName("does_not_contain") DOES_NOT_CONTAIN,
    @SerialName("is") IS,
    @SerialName("starts_with") STARTS_WITH,
    @SerialName("ends_with") ENDS_WITH,
    @SerialName("is_empty") IS_EMPTY,
    @SerialName("matches_regex") MATCHES_REGEX,
}

@Serializable
enum class NumericOp {
    @SerialName("greater_than") GREATER_THAN,
    @SerialName("less_than") LESS_THAN,
    @SerialName("equals") EQUALS,
}

@Serializable
enum class DateOp {
    @SerialName("older_than_days") OLDER_THAN_DAYS,
    @SerialName("newer_than_days") NEWER_THAN_DAYS,
}

@Serializable
enum class AttachmentNameOp {
    @SerialName("contains") CONTAINS,
    @SerialName("ends_with") ENDS_WITH,
    @SerialName("matches_regex") MATCHES_REGEX,
}

@Serializable
enum class AuthResult {
    @SerialName("pass") PASS,
    @SerialName("fail") FAIL,
    @SerialName("none") NONE,
    @SerialName("missing") MISSING,
}

@Serializable
enum class ReadState {
    @SerialName("read") READ,
    @SerialName("unread") UNREAD,
}

@Serializable
enum class MatchMode {
    @SerialName("all") ALL,
    @SerialName("any") ANY,
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("field")
sealed class Condition {
    @Serializable
    @SerialName("from")
    data class From(val op: AddressOp, val value: String, val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("reply_to")
    data class ReplyTo(val op: AddressOp, val value: String, val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("to")
    data class To(val op: AddressOp, val value: String, val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("cc")
    data class Cc(val op: AddressOp, val value: String, val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("bcc")
    data class Bcc(val op: AddressOp, val value: String, val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("any_recipient")
    data class AnyRecipient(val op: AddressOp, val value: String, val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("subject")
    data class Subject(val op: TextOp, val value: String = "", val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("body")
    data class Body(val op: TextOp, val value: String = "", val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("header")
    data class Header(val name: String, val op: TextOp, val value: String = "", val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("list_id")
    data class ListId(val op: TextOp, val value: String = "", val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("attachment_name")
    data class AttachmentName(val op: AttachmentNameOp, val value: String, val case_sensitive: Boolean? = null) : Condition()

    @Serializable
    @SerialName("has_attachment")
    data class HasAttachment(val `is`: Boolean) : Condition()

    @Serializable
    @SerialName("is_reply")
    data class IsReply(val `is`: Boolean) : Condition()

    @Serializable
    @SerialName("is_forward")
    data class IsForward(val `is`: Boolean) : Condition()

    @Serializable
    @SerialName("is_auto_submitted")
    data class IsAutoSubmitted(val `is`: Boolean) : Condition()

    @Serializable
    @SerialName("has_calendar_invite")
    data class HasCalendarInvite(val `is`: Boolean) : Condition()

    @Serializable
    @SerialName("has_list_id")
    data class HasListId(val `is`: Boolean) : Condition()

    @Serializable
    @SerialName("attachment_size")
    data class AttachmentSize(val op: NumericOp, val value: Long) : Condition()

    @Serializable
    @SerialName("total_size")
    data class TotalSize(val op: NumericOp, val value: Long) : Condition()

    @Serializable
    @SerialName("recipient_count")
    data class RecipientCount(val op: NumericOp, val value: Long) : Condition()

    @Serializable
    @SerialName("spam_score")
    data class SpamScore(val op: NumericOp, val value: Double) : Condition()

    @Serializable
    @SerialName("date_received")
    data class DateReceived(val op: DateOp, val value: Long) : Condition()

    @Serializable
    @SerialName("dkim_result")
    data class DkimResult(val value: AuthResult) : Condition()

    @Serializable
    @SerialName("spf_result")
    data class SpfResult(val value: AuthResult) : Condition()

    @Serializable
    @SerialName("dmarc_result")
    data class DmarcResult(val value: AuthResult) : Condition()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class Action {
    @Serializable
    @SerialName("move_to")
    data class MoveTo(val folder_token: String) : Action()

    @Serializable
    @SerialName("apply_labels")
    data class ApplyLabels(val label_tokens: List<String>) : Action()

    @Serializable
    @SerialName("mark_as")
    data class MarkAs(val value: ReadState) : Action()

    @Serializable
    @SerialName("star")
    data object Star : Action()

    @Serializable
    @SerialName("skip_inbox")
    data object SkipInbox : Action()

    @Serializable
    @SerialName("delete")
    data object Delete : Action()

    @Serializable
    @SerialName("forward")
    data class Forward(val to: String) : Action()

    @Serializable
    @SerialName("auto_reply")
    data class AutoReply(val template_id: String) : Action()

    @Serializable
    @SerialName("pin")
    data object Pin : Action()

    @Serializable
    @SerialName("snooze")
    data class Snooze(val until_iso8601: String) : Action()

    @Serializable
    @SerialName("categorize")
    data class Categorize(val category: String) : Action()

    @Serializable
    @SerialName("notify")
    data class Notify(val enabled: Boolean) : Action()
}

@Serializable
data class MailRule(
    val id: String,
    val name: String,
    val color: String = "#6366F1",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val match_mode: MatchMode = MatchMode.ALL,
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    val applied_count: Long = 0,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class MailRulesListResponse(val rules: List<MailRule> = emptyList())

@Serializable
data class CreateRuleRequest(
    val name: String,
    val color: String? = null,
    val enabled: Boolean? = null,
    val match_mode: MatchMode? = null,
    val conditions: List<Condition>,
    val actions: List<Action>,
    val run_on_existing: Boolean? = null,
)

@Serializable
data class CreateRuleResponse(
    val id: String,
    val success: Boolean = true,
)

@Serializable
data class UpdateRuleRequest(
    val name: String? = null,
    val color: String? = null,
    val enabled: Boolean? = null,
    val match_mode: MatchMode? = null,
    val conditions: List<Condition>? = null,
    val actions: List<Action>? = null,
)

@Serializable
data class ReorderRequest(val order: List<String>)

interface MailRulesApi {
    suspend fun list(): MailRulesListResponse
    suspend fun create(request: CreateRuleRequest): CreateRuleResponse
    suspend fun update(rule_id: String, request: UpdateRuleRequest): MailRule
    suspend fun delete(rule_id: String)
    suspend fun reorder(ordered_ids: List<String>)
    suspend fun run_on_existing(rule_id: String)
}

class MailRulesApiImpl(private val client: ApiClient) : MailRulesApi {
    private val base = "/api/mail/v1/mail-rules"

    override suspend fun list(): MailRulesListResponse {
        val response = client.http.get("${client.base_url}$base")
        return decode_or_throw(response)
    }

    override suspend fun create(request: CreateRuleRequest): CreateRuleResponse {
        val csrf = client.fetch_csrf_if_needed()
        val response = client.http.post("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            csrf?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update(rule_id: String, request: UpdateRuleRequest): MailRule {
        val csrf = client.fetch_csrf_if_needed()
        val response = client.http.patch("${client.base_url}$base/$rule_id") {
            contentType(ContentType.Application.Json)
            csrf?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun delete(rule_id: String) {
        val csrf = client.fetch_csrf_if_needed()
        val response = client.http.delete("${client.base_url}$base/$rule_id") {
            csrf?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun reorder(ordered_ids: List<String>) {
        val csrf = client.fetch_csrf_if_needed()
        val response = client.http.post("${client.base_url}$base/reorder") {
            contentType(ContentType.Application.Json)
            csrf?.let { header("X-CSRF-Token", it) }
            setBody(ReorderRequest(order = ordered_ids))
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun run_on_existing(rule_id: String) {
        val csrf = client.fetch_csrf_if_needed()
        val response = client.http.post("${client.base_url}$base/$rule_id/run-on-existing") {
            csrf?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        return try {
            response.body()
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
