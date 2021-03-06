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

import io.github.krlvm.powertunnel.sdk.configuration.Configuration;
import io.github.krlvm.powertunnel.sdk.plugin.PowerTunnelPlugin;
import io.github.krlvm.powertunnel.sdk.proxy.ProxyServer;
import io.github.krlvm.powertunnel.sdk.types.PowerTunnelPlatform;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.*;
import org.xbill.DNS.config.AndroidResolverConfigProvider;
import org.xbill.DNS.dnssec.ValidatingResolver;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class DNSPlugin extends PowerTunnelPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(DNSPlugin.class);

    @Override
    public void onProxyInitialization(@NotNull ProxyServer proxy) {
        if(!validateAndroidVersion()) return;
        final Configuration configuration = readConfiguration();

        String dns;
        final DNSPreset preset;
        try {
            preset = DNSPreset.valueOf(configuration.get("dns_preset", "custom").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DNSParseException("Invalid DNS Preset");
        }

        if(preset == DNSPreset.CUSTOM) {
            dns = configuration.get("dns", "");
        } else {
            dns = preset.getAddress();
        }

        final boolean doh = dns.startsWith("https://")
                || (configuration.getBoolean("allow_insecure", false) && dns.startsWith("http://"));
        if (dns.startsWith("http://") && !doh) {
            throw new DNSParseException("Please, enable insecure DNS requests to use Plain-HTTP DNS Resolver");
        }

        final boolean sec = configuration.getBoolean("dnssec", false);
        final boolean ignoreHosts = configuration.getBoolean("ignore_system_hosts", false);
        System.setProperty("dnsjava.lookup.use_hosts_file", String.valueOf(!ignoreHosts));

        if (dns.endsWith("/")) {
            dns = dns.substring(0, dns.length() - 1);
        }

        if (getServer().getPlatform() == PowerTunnelPlatform.ANDROID) {
            AndroidResolverConfigProvider.dnsServers = proxy.getDNSServers();
            AndroidResolverConfigProvider.domainsSearchPath = proxy.getDNSDomainsSearchPath();
        }

        Resolver resolver = null;
        if(doh) {
            resolver = new DohResolver(dns);
        } else {
            if(!dns.isEmpty()) {
                if (!DNSParser.isIPv4(dns) && !DNSParser.isIPv4WithPort(dns) && !DNSParser.isIPv6(dns) && !DNSParser.isIPv6WithPort(dns)) {
                    throw new DNSParseException("Invalid DNS address");
                }
                String address = dns;
                int port = -1;
                if (DNSParser.hasPort(address)) {
                    Object[] split = DNSParser.splitAddress(address);
                    if (split != null) {
                        address = ((String) split[0]);
                        port = ((int) split[1]);
                    }
                }
                try {
                    resolver = new SimpleResolver(address);
                    if (port != -1) resolver.setPort(port);
                } catch (UnknownHostException ex) {
                    throw new RuntimeException("Failed to initialize specified DNS Resolver: " + ex.getMessage(), ex);
                }
            }
            if(sec) {
                if (resolver == null) {
                    try {
                        resolver = new SimpleResolver();
                    } catch (UnknownHostException ex) {
                        throw new RuntimeException("Failed to initialize default DNS Resolver: " + ex.getMessage(), ex);
                    }
                }
                resolver = new ValidatingResolver(resolver);
            }
        }

        if(resolver == null) return;
        LOGGER.info("DNS Resolver: '{}' [dnsOverHttps={}, dnsSec={}, ignoreHosts={}]", dns, doh, sec, ignoreHosts);

        final Resolver pResolver = resolver;
        registerProxyListener(new DNSListener((host, port) -> {
            final Lookup lookup;
            try {
                lookup = new Lookup(host, Type.A);
            } catch (TextParseException ex) {
                throw new UnknownHostException();
            }
            lookup.setResolver(pResolver);
            final Record[] records = lookup.run();
            if (lookup.getResult() == Lookup.SUCCESSFUL) {
                return new InetSocketAddress(((ARecord) records[0]).getAddress(), port);
            } else {
                throw new UnknownHostException();
            }
        }));
    }

    private static boolean validateAndroidVersion() {
        try {
            // dnsjava uses APIs that are not available on old Android versions
            Class.forName("java.time.Duration");
            return true;
        } catch (ClassNotFoundException ex) {
            LOGGER.error("DNS Plugin is not supported on your Android version");
            return false;
        }
    }
}
