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

package org.xbill.DNS.config;

import java.net.InetSocketAddress;
import java.util.List;

import org.xbill.DNS.SimpleResolver;

public class AndroidResolverConfigProvider extends BaseResolverConfigProvider {

  public static List<String> dnsServers = null;
  public static String domainsSearchPath = null;

  @Override
  public void initialize() throws InitializationException {
    if (dnsServers != null) {
      for (String address : dnsServers) {
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