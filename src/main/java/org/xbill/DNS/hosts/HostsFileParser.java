// Modified in order to avoid using java.nio.file
// APIs which are not available on Android SDK < 26

// SPDX-License-Identifier: BSD-3-Clause
package org.xbill.DNS.hosts;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.xbill.DNS.Address;
import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.nio.FilesHelper;

/**
 * Parses and caches the system's local hosts database, otherwise known as {@code /etc/hosts}. The
 * cache is cleared when the file is modified.
 *
 * @since 3.4
 */
public final class HostsFileParser {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HostsFileParser.class);

    private static final int MAX_FULL_CACHE_FILE_SIZE_BYTES = 16384;

    private final Map<String, InetAddress> hostsCache = new HashMap<>();
    private final File path;
    private final boolean clearCacheOnChange;
    private Instant lastFileReadTime = Instant.MIN;
    private boolean isEntireFileParsed;

    /**
     * Creates a new instance based on the current OS's default. Unix and alike (or rather everything
     * else than Windows) use {@code /etc/hosts}, while on Windows {@code
     * %SystemRoot%\System32\drivers\etc\hosts} is used. The cache is cleared when the file has
     * changed.
     */
    public HostsFileParser() {
        this(
                System.getProperty("os.name").contains("Windows")
                        ? new File(System.getenv("SystemRoot"), "\\System32\\drivers\\etc\\hosts")
                        : new File("/etc/hosts"),
                true);
    }

    /**
     * Creates an instance with a custom hosts database path. The cache is cleared when the file has
     * changed.
     *
     * @param path The path to the hosts database.
     */
    public HostsFileParser(File path) {
        this(path, true);
    }

    /**
     * Creates an instance with a custom hosts database path.
     *
     * @param path The path to the hosts database.
     * @param clearCacheOnChange set to true to clear the cache when the hosts file changes.
     */
    public HostsFileParser(File path, boolean clearCacheOnChange) {
        this.path = Objects.requireNonNull(path, "path is required");
        this.clearCacheOnChange = clearCacheOnChange;
        if (path.isDirectory()) {
            throw new IllegalArgumentException("path must be a file");
        }
    }

    /**
     * Performs on-demand parsing and caching of the local hosts database.
     *
     * @param name the hostname to search for.
     * @param type Record type to search for, see {@link org.xbill.DNS.Type}.
     * @return The first address found for the requested hostname.
     * @throws IOException When the parsing fails.
     * @throws IllegalArgumentException when {@code type} is not {@link org.xbill.DNS.Type#A} or{@link
     *     org.xbill.DNS.Type#AAAA}.
     */
    public synchronized Optional<InetAddress> getAddressForHost(Name name, int type)
            throws IOException {
        Objects.requireNonNull(name, "name is required");
        if (type != Type.A && type != Type.AAAA) {
            throw new IllegalArgumentException("type can only be A or AAAA");
        }

        validateCache();

        InetAddress cachedAddress = hostsCache.get(key(name, type));
        if (cachedAddress != null) {
            return Optional.of(cachedAddress);
        }

        if (isEntireFileParsed || !path.exists()) {
            return Optional.empty();
        }

        if (path.length() <= MAX_FULL_CACHE_FILE_SIZE_BYTES) {
            parseEntireHostsFile();
        } else {
            searchHostsFileForEntry(name, type);
        }

        return Optional.ofNullable(hostsCache.get(key(name, type)));
    }

    private void parseEntireHostsFile() throws IOException {
        String line;
        int lineNumber = 0;
        try (BufferedReader hostsReader = FilesHelper.newBufferedReader(path, StandardCharsets.UTF_8)) {
            while ((line = hostsReader.readLine()) != null) {
                LineData lineData = parseLine(++lineNumber, line);
                if (lineData != null) {
                    for (Name lineName : lineData.names) {
                        InetAddress lineAddress =
                                InetAddress.getByAddress(lineName.toString(true), lineData.address);
                        hostsCache.putIfAbsent(key(lineName, lineData.type), lineAddress);
                    }
                }
            }
        }

        isEntireFileParsed = true;
    }

    private void searchHostsFileForEntry(Name name, int type) throws IOException {
        String line;
        int lineNumber = 0;
        try (BufferedReader hostsReader = FilesHelper.newBufferedReader(path, StandardCharsets.UTF_8)) {
            while ((line = hostsReader.readLine()) != null) {
                LineData lineData = parseLine(++lineNumber, line);
                if (lineData != null) {
                    for (Name lineName : lineData.names) {
                        boolean isSearchedEntry = lineName.equals(name);
                        if (isSearchedEntry && type == lineData.type) {
                            InetAddress lineAddress =
                                    InetAddress.getByAddress(lineName.toString(true), lineData.address);
                            hostsCache.putIfAbsent(key(lineName, lineData.type), lineAddress);
                            return;
                        }
                    }
                }
            }
        }
    }

    private static final class LineData {
        final int type;
        final byte[] address;
        final Iterable<? extends Name> names;

        LineData(int type, byte[] address, Iterable<? extends Name> names) {
            this.type = type;
            this.address = address;
            this.names = names;
        }
    }

    private LineData parseLine(int lineNumber, String line) {
        String[] lineTokens = getLineTokens(line);
        if (lineTokens.length < 2) {
            return null;
        }

        int lineAddressType = Type.A;
        byte[] lineAddressBytes = Address.toByteArray(lineTokens[0], Address.IPv4);
        if (lineAddressBytes == null) {
            lineAddressBytes = Address.toByteArray(lineTokens[0], Address.IPv6);
            lineAddressType = Type.AAAA;
        }

        if (lineAddressBytes == null) {
            log.warn("Could not decode address {}, {}#L{}", lineTokens[0], path, lineNumber);
            return null;
        }

        Iterable<? extends Name> lineNames =
                Arrays.stream(lineTokens)
                        .skip(1)
                        .map(lineTokenName -> safeName(lineTokenName, lineNumber))
                        .filter(Objects::nonNull)
                        ::iterator;
        return new LineData(lineAddressType, lineAddressBytes, lineNames);
    }

    private Name safeName(String name, int lineNumber) {
        try {
            return Name.fromString(name, Name.root);
        } catch (TextParseException e) {
            log.warn("Could not decode name {}, {}#L{}, skipping", name, path, lineNumber);
            return null;
        }
    }

    private String[] getLineTokens(String line) {
        // everything after a # until the end of the line is a comment
        int commentStart = line.indexOf('#');
        if (commentStart == -1) {
            commentStart = line.length();
        }

        return line.substring(0, commentStart).trim().split("\\s+");
    }

    private void validateCache() throws IOException {
        if (clearCacheOnChange) {
            // A filewatcher / inotify etc. would be nicer, but doesn't work. c.f. the write up at
            // https://blog.arkey.fr/2019/09/13/watchservice-and-bind-mount/
            Instant fileTime =
                    path.exists() ? Instant.ofEpochMilli(path.lastModified()) : Instant.MAX;
            if (fileTime.isAfter(lastFileReadTime)) {
                // skip logging noise when the cache is empty anyway
                if (!hostsCache.isEmpty()) {
                    log.info("Local hosts database has changed at {}, clearing cache", fileTime);
                    hostsCache.clear();
                }

                isEntireFileParsed = false;
                lastFileReadTime = fileTime;
            }
        }
    }

    private String key(Name name, int type) {
        return name.toString() + '\t' + type;
    }

    // for unit testing only
    int cacheSize() {
        return hostsCache.size();
    }
}