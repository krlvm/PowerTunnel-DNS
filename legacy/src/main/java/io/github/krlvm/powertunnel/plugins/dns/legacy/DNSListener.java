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

package io.github.krlvm.powertunnel.plugins.dns.legacy;

import io.github.krlvm.powertunnel.sdk.proxy.DNSRequest;
import io.github.krlvm.powertunnel.sdk.proxy.DNSResolver;
import io.github.krlvm.powertunnel.sdk.proxy.ProxyAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;

public class DNSListener extends ProxyAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DNSListener.class);

    private final DNSResolver resolver;

    public DNSListener(DNSResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean onResolutionRequest(@NotNull DNSRequest request) {
        if(request.getResponse() != null) return true;
        try {
            request.setResponse(resolver.resolve(request.getHost(), request.getPort()));
            return true;
        } catch (UnknownHostException ex) {
            LOGGER.debug("Failed to resolve hostname '{}': {}", request.getHost(), ex.getMessage(), ex);
            return false;
        }
    }
}
