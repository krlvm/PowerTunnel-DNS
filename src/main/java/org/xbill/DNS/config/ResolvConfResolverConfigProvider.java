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

// SPDX-License-Identifier: BSD-3-Clause
package org.xbill.DNS.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.StringTokenizer;
import org.xbill.DNS.SimpleResolver;

public class ResolvConfResolverConfigProvider extends BaseResolverConfigProvider {
  private int ndots = 1;

  @Override
  public void initialize() {
    reset();
    // first try the default unix config path
    if (!tryParseResolveConf("/etc/resolv.conf")) {
      // then fallback to netware
      tryParseResolveConf("sys:/etc/resolv.cfg");
    }
  }

  private boolean tryParseResolveConf(String path) {
    Path p = Paths.get(path);
    if (Files.exists(p)) {
      try (InputStream in = Files.newInputStream(p)) {
        parseResolvConf(in);
        return true;
      } catch (IOException e) {
        // ignore
      }
    }

    return false;
  }

  protected void parseResolvConf(InputStream in) throws IOException {
    try (InputStreamReader isr = new InputStreamReader(in);
        BufferedReader br = new BufferedReader(isr)) {
      String line;
      while ((line = br.readLine()) != null) {
        StringTokenizer st = new StringTokenizer(line);
        if (!st.hasMoreTokens()) {
          continue;
        }

        switch (st.nextToken()) {
          case "nameserver":
            addNameserver(new InetSocketAddress(st.nextToken(), SimpleResolver.DEFAULT_PORT));
            break;

          case "domain":
            // man resolv.conf:
            // The domain and search keywords are mutually exclusive. If more than one instance of
            // these keywords is present, the last instance wins.
            searchlist.clear();
            if (!st.hasMoreTokens()) {
              continue;
            }

            addSearchPath(st.nextToken());
            break;

          case "search":
            // man resolv.conf:
            // The domain and search keywords are mutually exclusive. If more than one instance of
            // these keywords is present, the last instance wins.
            searchlist.clear();
            while (st.hasMoreTokens()) {
              addSearchPath(st.nextToken());
            }

          case "options":
            while (st.hasMoreTokens()) {
              String token = st.nextToken();
              if (token.startsWith("ndots:")) {
                ndots = parseNdots(token.substring(6));
              }
            }
            break;
        }
      }
    }

    // man resolv.conf:
    // The search keyword of a system's resolv.conf file can be overridden on a per-process basis by
    // setting the environment variable LOCALDOMAIN to a space-separated list of search domains.
    String localdomain = System.getenv("LOCALDOMAIN");
    if (localdomain != null && !localdomain.isEmpty()) {
      searchlist.clear();
      parseSearchPathList(localdomain, " ");
    }

    // man resolv.conf:
    // The options keyword of a system's resolv.conf file can be amended on a per-process basis by
    // setting the environment variable RES_OPTIONS to a space-separated list of resolver options as
    // explained above under options.
    String resOptions = System.getenv("RES_OPTIONS");
    if (resOptions != null && !resOptions.isEmpty()) {
      StringTokenizer st = new StringTokenizer(resOptions, " ");
      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        if (token.startsWith("ndots:")) {
          ndots = parseNdots(token.substring(6));
        }
      }
    }
  }

  @Override
  public int ndots() {
    return ndots;
  }

  @Override
  public boolean isEnabled() {
    return !System.getProperty("os.name").contains("Windows")
        && !System.getProperty("java.specification.vendor").toLowerCase().contains("android");
  }
}