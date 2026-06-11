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

package org.astermail.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.plugins.auth.providers.BearerTokens
import javax.inject.Singleton
import org.astermail.android.api.ApiClient
import org.astermail.android.api.BuildConfig
import org.astermail.android.api.TokenProvider
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.AuthApiImpl
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.BillingApiImpl
import org.astermail.android.api.external_accounts.ExternalAccountsApi
import org.astermail.android.api.external_accounts.ExternalAccountsApiImpl
import org.astermail.android.api.imports.ImportApi
import org.astermail.android.api.imports.ImportApiImpl
import org.astermail.android.api.contacts.ContactsApi
import org.astermail.android.api.contacts.ContactsApiImpl
import org.astermail.android.api.csrf.CsrfApi
import org.astermail.android.api.csrf.CsrfApiImpl
import org.astermail.android.api.autoforward.AutoForwardApi
import org.astermail.android.api.autoforward.AutoForwardApiImpl
import org.astermail.android.api.mail_rules.MailRulesApi
import org.astermail.android.api.mail_rules.MailRulesApiImpl
import org.astermail.android.api.developer.DeveloperApi
import org.astermail.android.api.developer.DeveloperApiImpl
import org.astermail.android.api.recovery.RecoveryApi
import org.astermail.android.api.recovery.RecoveryApiImpl
import org.astermail.android.api.ghost.GhostAliasApi
import org.astermail.android.api.ghost.GhostAliasApiImpl
import org.astermail.android.api.snooze.SnoozeApi
import org.astermail.android.api.snooze.SnoozeApiImpl
import org.astermail.android.api.labels.LabelsApi
import org.astermail.android.api.labels.LabelsApiImpl
import org.astermail.android.api.tags.TagsApi
import org.astermail.android.api.tags.TagsApiImpl
import org.astermail.android.api.templates.TemplatesApi
import org.astermail.android.api.templates.TemplatesApiImpl
import org.astermail.android.api.totp.TotpApi
import org.astermail.android.api.totp.TotpApiImpl
import org.astermail.android.api.vacation.VacationReplyApi
import org.astermail.android.api.vacation.VacationReplyApiImpl
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.keys.KeysApiImpl
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.RatchetApiImpl
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailApiImpl
import org.astermail.android.api.preferences.PreferencesApi
import org.astermail.android.api.preferences.PreferencesApiImpl
import org.astermail.android.api.settings.SettingsApi
import org.astermail.android.api.settings.SettingsApiImpl
import org.astermail.android.api.send.SendApi
import org.astermail.android.api.send.SendApiImpl
import org.astermail.android.api.signatures.SignaturesApi
import org.astermail.android.api.signatures.SignaturesApiImpl
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.api.subscriptions.SubscriptionsApiImpl
import org.astermail.android.api.user.UserApi
import org.astermail.android.api.user.UserApiImpl
import org.astermail.android.storage.TokenStore

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provide_token_provider(
        token_store: TokenStore,
        auth_api: dagger.Lazy<AuthApi>,
    ): TokenProvider = object : TokenProvider {
        override suspend fun load(): BearerTokens? {
            val access = token_store.access_token ?: return null
            val refresh = token_store.refresh_token ?: access
            return BearerTokens(access, refresh)
        }

        override suspend fun refresh(): BearerTokens? {
            return try {
                val response = auth_api.get().refresh()
                val existing_refresh = token_store.refresh_token ?: response.access_token
                token_store.save(response.access_token, existing_refresh)
                BearerTokens(response.access_token, existing_refresh)
            } catch (t: Throwable) {
                val is_definitive_auth_failure = t is org.astermail.android.api.ApiError.UnauthorizedError ||
                    t is org.astermail.android.api.ApiError.ForbiddenError
                if (is_definitive_auth_failure) {
                    null
                } else {
                    val access = token_store.access_token
                    val refresh = token_store.refresh_token ?: access
                    if (access != null && refresh != null) BearerTokens(access, refresh) else null
                }
            }
        }

        override suspend fun clear() {
            token_store.clear()
        }
    }

    @Provides
    @Singleton
    fun provide_api_client(
        token_provider: TokenProvider,
        token_store: TokenStore,
    ): ApiClient = ApiClient(
        base_url = BuildConfig.API_BASE_URL,
        token_provider = token_provider,
        on_csrf_changed = { token -> token_store.save_csrf(token) },
        initial_csrf = token_store.csrf_token,
    )

    @Provides
    @Singleton
    fun provide_auth_api(client: ApiClient): AuthApi = AuthApiImpl(client)

    @Provides
    @Singleton
    fun provide_user_api(client: ApiClient): UserApi = UserApiImpl(client)

    @Provides
    @Singleton
    fun provide_keys_api(client: ApiClient): KeysApi = KeysApiImpl(client)

    @Provides
    @Singleton
    fun provide_ratchet_api(client: ApiClient): RatchetApi = RatchetApiImpl(client)

    @Provides
    @Singleton
    fun provide_csrf_api(client: ApiClient): CsrfApi = CsrfApiImpl(client)

    @Provides
    @Singleton
    fun provide_mail_api(client: ApiClient): MailApi = MailApiImpl(client)

    @Provides
    @Singleton
    fun provide_settings_api(client: ApiClient): SettingsApi = SettingsApiImpl(client)

    @Provides
    @Singleton
    fun provide_contacts_api(client: ApiClient): ContactsApi = ContactsApiImpl(client)

    @Provides
    @Singleton
    fun provide_labels_api(client: ApiClient): LabelsApi = LabelsApiImpl(client)

    @Provides
    @Singleton
    fun provide_tags_api(client: ApiClient): TagsApi = TagsApiImpl(client)

    @Provides
    @Singleton
    fun provide_preferences_api(client: ApiClient): PreferencesApi = PreferencesApiImpl(client)

    @Provides
    @Singleton
    fun provide_signatures_api(client: ApiClient): SignaturesApi = SignaturesApiImpl(client)

    @Provides
    @Singleton
    fun provide_ghost_alias_api(client: ApiClient): GhostAliasApi = GhostAliasApiImpl(client)

    @Provides
    @Singleton
    fun provide_snooze_api(client: ApiClient): SnoozeApi = SnoozeApiImpl(client)

    @Provides
    @Singleton
    fun provide_auto_forward_api(client: ApiClient): AutoForwardApi = AutoForwardApiImpl(client)

    @Provides
    @Singleton
    fun provide_mail_rules_api(client: ApiClient): MailRulesApi = MailRulesApiImpl(client)

    @Provides
    @Singleton
    fun provide_developer_api(client: ApiClient): DeveloperApi = DeveloperApiImpl(client)

    @Provides
    @Singleton
    fun provide_recovery_api(client: ApiClient): RecoveryApi = RecoveryApiImpl(client)

    @Provides
    @Singleton
    fun provide_templates_api(client: ApiClient): TemplatesApi = TemplatesApiImpl(client)

    @Provides
    @Singleton
    fun provide_totp_api(client: ApiClient): TotpApi = TotpApiImpl(client)

    @Provides
    @Singleton
    fun provide_vacation_reply_api(client: ApiClient): VacationReplyApi =
        VacationReplyApiImpl(client)

    @Provides
    @Singleton
    fun provide_subscriptions_api(client: ApiClient): SubscriptionsApi =
        SubscriptionsApiImpl(client)

    @Provides
    @Singleton
    fun provide_billing_api(client: ApiClient): BillingApi = BillingApiImpl(client)

    @Provides
    @Singleton
    fun provide_import_api(client: ApiClient): ImportApi = ImportApiImpl(client)

    @Provides
    @Singleton
    fun provide_external_accounts_api(client: ApiClient): ExternalAccountsApi = ExternalAccountsApiImpl(client)

    @Provides
    @Singleton
    fun provide_send_api(client: ApiClient): SendApi = SendApiImpl(client)
}
