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
package org.astermail.android.settings.domains

enum class DnsHostStyle { at, blank, fqdn }

enum class DnsProvider(
    val label: String?,
    val host_style: DnsHostStyle,
    val dashboard_template: String?,
) {
    cloudflare("Cloudflare", DnsHostStyle.at, "https://dash.cloudflare.com/?to=/:account/{domain}/dns"),
    godaddy("GoDaddy", DnsHostStyle.at, "https://dcc.godaddy.com/control/{domain}/dns"),
    namecheap("Namecheap", DnsHostStyle.at, "https://ap.www.namecheap.com/domains/domaincontrolpanel/{domain}/advancedns"),
    google("Google Domains / Squarespace", DnsHostStyle.at, "https://account.squarespace.com/domains"),
    route53("Amazon Route 53", DnsHostStyle.fqdn, "https://console.aws.amazon.com/route53/v2/hostedzones"),
    hover("Hover", DnsHostStyle.at, "https://www.hover.com/control_panel/domain/{domain}/dns"),
    porkbun("Porkbun", DnsHostStyle.blank, "https://porkbun.com/account/dns/{domain}"),
    namesilo("NameSilo", DnsHostStyle.blank, "https://www.namesilo.com/account_domains.php"),
    gandi("Gandi", DnsHostStyle.at, "https://admin.gandi.net/domain/{domain}/records"),
    ovh("OVHcloud", DnsHostStyle.blank, "https://www.ovh.com/manager/#/web/domain/{domain}/zone"),
    digitalocean("DigitalOcean", DnsHostStyle.at, "https://cloud.digitalocean.com/networking/domains/{domain}"),
    hetzner("Hetzner", DnsHostStyle.at, "https://dns.hetzner.com/"),
    vercel("Vercel", DnsHostStyle.blank, "https://vercel.com/dashboard/domains"),
    other(null, DnsHostStyle.at, null);

    companion object {
        fun from_id(id: String?): DnsProvider = entries.firstOrNull { it.name == id } ?: other
    }
}

private val safe_domain = Regex("^[a-z0-9.-]{1,253}$")

fun dns_provider_dashboard_url(provider: DnsProvider, domain: String): String? {
    val template = provider.dashboard_template ?: return null
    val zone = domain.trim().lowercase().trimEnd('.')
    if (!safe_domain.matches(zone)) return null
    return template.replace("{domain}", zone)
}

fun format_host_for_provider(host: String, domain: String, provider: DnsProvider): String {
    val zone = domain.trim().lowercase().trimEnd('.')
    if (provider == DnsProvider.other) {
        val raw = host.trim()
        return raw.ifEmpty { "@" }
    }
    val relative = normalize_dns_host(host, zone)
    return when (provider.host_style) {
        DnsHostStyle.fqdn -> if (relative == "@") zone else "$relative.$zone"
        DnsHostStyle.blank -> if (relative == "@") "" else relative
        DnsHostStyle.at -> relative
    }
}
