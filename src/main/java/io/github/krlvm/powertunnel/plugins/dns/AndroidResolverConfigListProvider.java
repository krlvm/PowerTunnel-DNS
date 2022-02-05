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

import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.config.BaseResolverConfigProvider;
import org.xbill.DNS.config.InitializationException;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class AndroidResolverConfigListProvider extends BaseResolverConfigProvider {

    private final List<String> dnsList;
    private final String domainsSearchPath;

    public AndroidResolverConfigListProvider(List<String> dnsList, String domainsSearchPath) {
        this.dnsList = dnsList;
        this.domainsSearchPath = domainsSearchPath;
    }

    @Override
    public void initialize() throws InitializationException {
        if (dnsList != null) {
            for (String address : dnsList) {
                addNameserver(new InetSocketAddress(address, SimpleResolver.DEFAULT_PORT));
            }
        }
        if (domainsSearchPath != null) {
            parseSearchPathList(domainsSearchPath, ",");
        }
    }

    @Override
    public boolean isEnabled() {
        return System.getProperty("java.vendor").contains("Android");
    }
}
