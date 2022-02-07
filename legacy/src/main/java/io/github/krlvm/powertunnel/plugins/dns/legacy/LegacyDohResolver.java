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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;

/**
 * Very basic implementation of
 * DNS over HTTPS (DoH) Resolver
 * for dnsjava v2.x
 *
 * Parts of code were derived from
 * DohResolver of dnsjava v3.x
 */
public class LegacyDohResolver implements Resolver {

    private final Logger log = LoggerFactory.getLogger(LegacyDohResolver.class);

    private final String host;
    private final Base64Provider base64;

    private int timeout;
    private TSIG tsig;

    public LegacyDohResolver(String host, Base64Provider base64) {
        this.host = host;
        this.base64 = base64;

        this.setTimeout(5);
    }

    @Override
    public void setPort(int port) {
        // Not implemented, port is part of the URI
    }

    @Override
    public void setTCP(boolean flag) {
        // Not implemented, HTTP is always TCP
    }

    @Override
    public void setIgnoreTruncation(boolean flag) {
        // Not implemented, protocol uses TCP and doesn't have truncation
    }

    @Override
    public void setEDNS(int level) {

    }

    @Override
    public void setEDNS(int level, int payloadSize, int flags, List options) {

    }

    @Override
    public void setTSIGKey(TSIG key) {
        this.tsig = key;
    }

    @Override
    public void setTimeout(int secs, int msecs) {
        this.timeout = (secs * 1000) + msecs;
    }

    @Override
    public void setTimeout(int secs) {
        setTimeout(secs, 0);
    }

    @Override
    public Message send(Message query) throws IOException {
        query = ((Message) query.clone());
        if (tsig != null) {
            tsig.apply(query, null);
        }

        final String encoded = base64.encodeURL(query.toWire());

        HttpURLConnection con = null;
        try {
            final URL url = new URL(host + "?dns=" + encoded);
            con = (HttpURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Java client");
            con.setRequestProperty("content-type", "application/dns-message");
            con.setConnectTimeout(timeout);
            //con.setDoOutput(true);

            final int rc = con.getResponseCode();
            final byte[] buf = new byte[65535];
            con.getInputStream().read(buf);

            final Message response;
            if (rc >= 200 && rc < 300) {
                response = new Message(buf);
                verifyTSIG(query, response, buf, tsig);
            } else {
                response = new Message(0);
                response.getHeader().setRcode(Rcode.SERVFAIL);
            }
            return response;
        } catch (SocketTimeoutException ex) {
            throw new IOException(
                    "Query "
                            + query.getHeader().getID()
                            + " for "
                            + query.getQuestion().getName()
                            + "/"
                            + Type.string(query.getQuestion().getType())
                            + " timed out",
            ex);
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    @Override
    public Object sendAsync(Message query, ResolverListener listener) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private void verifyTSIG(Message query, Message response, byte[] b, TSIG tsig) {
        if (tsig == null) {
            return;
        }

        int error = tsig.verify(response, b, query.getTSIG());
        log.debug(
                "TSIG verify for query {}, {}/{}: {}",
                query.getHeader().getID(),
                query.getQuestion().getName(),
                Type.string(query.getQuestion().getType()),
                Rcode.TSIGstring(error));
    }
}