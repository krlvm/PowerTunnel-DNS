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

public interface Base64Provider {
    String encodeURL(byte[] bytes);

    static Base64Provider getProvider() {
        try {
            Class.forName("java.util.Base64");
            return new Java8Provider();
        } catch (ClassNotFoundException ex) {
            return new AndroidProvider();
        }
    }

    class Java8Provider implements Base64Provider {
        @Override
        public String encodeURL(byte[] bytes) {
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
    }

    class AndroidProvider implements Base64Provider {
        @Override
        public String encodeURL(byte[] bytes) {
            return new String(android.util.Base64.encode(bytes,
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP
            ));
        }
    }
}
