/*
 * This file is part of PowerTunnel-DNS.
 *
 * PowerTunnel-DNS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PowerTunnel-DNS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PowerTunnel-DNS.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.krlvm.powertunnel.plugins.dns;

public enum DNSPreset {

    CUSTOM(null),

    GOOGLE("8.8.8.8"),
    GOOGLE_DOH("https://8.8.8.8/dns-query"),

    CLOUDFLARE("1.1.1.1"),
    CLOUDFLARE_DOH("https://1.1.1.1/dns-query");



    private String address;

    DNSPreset(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }
}
